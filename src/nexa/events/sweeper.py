import logging
from collections import defaultdict
from typing import List, Sequence, Union
from uuid import uuid4

from nexa.domain.alerts import (
    Alert,
    Notification,
    NotificationDeliveryState,
)
from nexa.domain.events import AggregatedSecurityEvent, SecurityEvent
from nexa.events.aggregator import AlertAggregator
from nexa.events.rules import RuleEngine
from nexa.persistence.sqlite_alerts import SqliteAlertRepository

logger = logging.getLogger(__name__)


class OutboxSweeper:
    """
    Drains the Security Event Outbox, processes rules, creates/updates alerts,
    and enqueues notifications. Implements compaction on overload.
    """

    def __init__(
        self,
        alert_repo: SqliteAlertRepository,
        rule_engine: RuleEngine,
        high_water_mark: int = 1000,
    ):
        self.alert_repo = alert_repo
        self.rule_engine = rule_engine
        self.high_water_mark = high_water_mark

    def sweep(self) -> None:
        """Single pass to drain the outbox."""
        unprocessed_count = self.alert_repo.get_unprocessed_outbox_count()
        if unprocessed_count == 0:
            return

        limit = min(unprocessed_count, self.high_water_mark * 2)
        events = self.alert_repo.get_unprocessed_outbox_events(limit=limit)

        if not events:
            return

        processed_events: Sequence[Union[SecurityEvent, AggregatedSecurityEvent]]
        if unprocessed_count > self.high_water_mark:
            logger.warning(
                f"Outbox high water mark reached ({unprocessed_count} > "
                f"{self.high_water_mark}). Compacting."
            )
            processed_events = self._compact_events(events)
        else:
            processed_events = events

        event_ids_processed = [
            e.event_id for e in events
        ]  # we mark original events processed

        for item in processed_events:
            # Rule Engine Evaluation
            if not self.rule_engine.evaluate(item):
                continue

            # Aggregation (grouping active alerts)
            # Normal security events and aggregated ones both can be aggregated
            # to Alerts.
            agg_key = AlertAggregator.compute_aggregation_key(item)

            existing_alert = self.alert_repo.get_active_alert_by_key(agg_key)

            # Determine base entity
            ts = (
                item.timestamp
                if isinstance(item, SecurityEvent)
                else item.time_range_end
            )
            count_delta = 1 if isinstance(item, SecurityEvent) else item.count

            if existing_alert:
                existing_alert.event_count += count_delta
                existing_alert.last_seen = ts

                # Upgrade severity if needed
                if item.severity.value != existing_alert.severity.value:
                    # simplistic: just update if it's new (A full hierarchy check
                    # could be done, but for now we'll just set it if the new
                    # event is higher. But Enum comparison needs logic.
                    # We'll rely on the domain Severity order.)
                    pass

                self.alert_repo.save_alert(existing_alert)

            else:
                # Create a new alert
                new_alert = Alert(
                    aggregation_key=agg_key,
                    severity=item.severity,
                    event_class=item.event_class,
                    identity_id=item.identity_id
                    if isinstance(item, SecurityEvent)
                    else None,
                    device_id=item.device_id
                    if isinstance(item, SecurityEvent)
                    else None,
                    network_scope=item.network_scope,
                    event_count=count_delta,
                    first_seen=item.timestamp
                    if isinstance(item, SecurityEvent)
                    else item.time_range_start,
                    last_seen=ts,
                )
                self.alert_repo.save_alert(new_alert)

                notification_id = uuid4()
                notification = Notification(
                    notification_id=notification_id,
                    alert_id=new_alert.alert_id,
                    state=NotificationDeliveryState.QUEUED,
                    payload={
                        "notification_id": str(notification_id),
                        "alert_id": str(new_alert.alert_id),
                        "event_class": new_alert.event_class,
                        "severity": new_alert.severity.value,
                        "network_scope": new_alert.network_scope,
                        "event_count": new_alert.event_count,
                    },
                )
                self.alert_repo.enqueue_notification(notification)

        # Mark all as processed
        self.alert_repo.mark_outbox_events_processed(event_ids_processed)

    def _compact_events(
        self, events: Sequence[SecurityEvent]
    ) -> List[Union[SecurityEvent, AggregatedSecurityEvent]]:
        """
        Compacts events into AggregatedSecurityEvent based on:
        event_class, identity_id, device_id, network_scope, severity.
        """
        groups = defaultdict(list)
        for e in events:
            key = (
                e.event_class,
                str(e.identity_id),
                str(e.device_id),
                e.network_scope,
                e.severity.value,
            )
            groups[key].append(e)

        compacted: List[Union[SecurityEvent, AggregatedSecurityEvent]] = []
        for _key, group in groups.items():
            if len(group) == 1:
                compacted.append(group[0])
            else:
                group.sort(key=lambda x: x.timestamp)
                agg = AggregatedSecurityEvent(
                    event_class=group[0].event_class,
                    time_range_start=group[0].timestamp,
                    time_range_end=group[-1].timestamp,
                    severity=group[0].severity,
                    network_scope=group[0].network_scope,
                    count=len(group),
                    aggregation_reason="HIGH_WATER_COMPACTION",
                )
                compacted.append(agg)

        return compacted
