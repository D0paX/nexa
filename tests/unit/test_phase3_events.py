from typing import Any
from uuid import uuid4

import pytest

from nexa.domain.events import AggregatedSecurityEvent, SecurityEvent, Severity
from nexa.events.aggregator import AlertAggregator
from nexa.events.rules import RuleEngine
from nexa.events.sweeper import OutboxSweeper
from nexa.persistence.sqlite_alerts import SqliteAlertRepository


@pytest.fixture
def sweeper(tmp_path: Any) -> Any:
    db_path = str(tmp_path / "nexa.db")
    repo = SqliteAlertRepository(db_path)
    rules = RuleEngine()
    return OutboxSweeper(repo, rules, high_water_mark=1000)


def test_aggregation_semantics(sweeper: Any) -> None:
    # 1000 identical events
    identity_id = uuid4()
    device_id = uuid4()
    events = []

    for _ in range(1000):
        evt = SecurityEvent(
            event_id=uuid4(),
            event_class="TEST_ALERT",
            severity=Severity.HIGH,
            identity_id=identity_id,
            device_id=device_id,
            network_scope="GLOBAL",
        )
        sweeper.alert_repo.append_outbox_event(evt)
        events.append(evt)

    assert sweeper.alert_repo.get_unprocessed_outbox_count() == 1000

    # Sweep
    sweeper.sweep()

    # Assert 1000 identical events -> one Alert with count=1000
    assert sweeper.alert_repo.get_unprocessed_outbox_count() == 0

    # Since they are exactly the same, the aggregator assigns them the same hash
    # limit is min(1000, 2000). Is >1000 or >=1000?
    # Our code says `if unprocessed_count > self.high_water_mark:`. It's exactly 1000,
    # so NO compaction, just normal evaluation.
    # 1000 loop iterations updating the same alert.

    agg_key = AlertAggregator.compute_aggregation_key(events[0])
    alert = sweeper.alert_repo.get_active_alert_by_key(agg_key)

    assert alert is not None
    assert alert.event_count == 1000
    assert alert.severity == Severity.HIGH


def test_compaction_preserves_metadata(sweeper: Any) -> None:
    sweeper.high_water_mark = 5  # Lower threshold for testing
    identity_id = uuid4()

    # Generate 10 identical events
    for _ in range(10):
        evt = SecurityEvent(
            event_id=uuid4(),
            event_class="COMPACT_ME",
            severity=Severity.CRITICAL,
            identity_id=identity_id,
            network_scope="LOCAL",
        )
        sweeper.alert_repo.append_outbox_event(evt)

    assert sweeper.alert_repo.get_unprocessed_outbox_count() == 10

    events = sweeper.alert_repo.get_unprocessed_outbox_events(limit=20)
    compacted = sweeper._compact_events(events)

    # Should compact 10 into 1 AggregatedSecurityEvent
    assert len(compacted) == 1

    agg = compacted[0]
    assert isinstance(agg, AggregatedSecurityEvent)
    assert agg.event_class == "COMPACT_ME"
    assert agg.severity == Severity.CRITICAL
    assert agg.network_scope == "LOCAL"
    assert agg.count == 10
    assert agg.aggregation_reason == "HIGH_WATER_COMPACTION"
