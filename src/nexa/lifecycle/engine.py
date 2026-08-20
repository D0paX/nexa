"""
Lifecycle engine orchestrating persistence and correlation boundaries.
"""

import hashlib
import logging
from collections import deque
from datetime import timedelta
from typing import Dict, List
from uuid import UUID

from nexa.domain.correlation import PresenceState
from nexa.domain.device import DeviceRecord, ScanContext
from nexa.domain.lifecycle import (
    DeviceRepository,
    LifecycleEvent,
    LifecycleEventType,
    ScanTransactionEnvelope,
)
from nexa.domain.observation import DeviceObservation
from nexa.network.correlator import ObservationCorrelator

logger = logging.getLogger(__name__)


class PersistenceFailure(Exception):
    """Raised when persistence enters degraded mode and hits maximum queue bounds."""

    pass


class LifecycleEngine:
    """
    Manages the lifecycle of devices, evaluates state transitions,
    and handles degraded persistence fallbacks.
    """

    def __init__(self, repository: DeviceRepository, max_queue_size: int = 500) -> None:
        self.repository = repository
        self.max_queue_size = max_queue_size

        self.degraded: bool = False
        self._pending_queue: deque[ScanTransactionEnvelope] = deque()

        self._correlators: Dict[str, ObservationCorrelator] = {}
        # Stores previous state for transition detection
        self._previous_states: Dict[str, Dict[UUID, DeviceRecord]] = {}

    def get_canonical_scope_key(self, context: ScanContext) -> str:
        """
        Generates a deterministic SHA-256 scope key from the canonical Network CIDR.
        """
        cidr = (
            f"{context.network_scope.network_address}/"
            f"{context.network_scope.prefix_length}"
        )
        return hashlib.sha256(cidr.encode("utf-8")).hexdigest()

    def process_scan(
        self, context: ScanContext, observations: List[DeviceObservation]
    ) -> List[DeviceRecord]:
        """
        Processes a single scan batch, applies correlation, evaluates transitions,
        and saves transaction envelopes.
        """
        scope_key = self.get_canonical_scope_key(context)
        now = context.started_at

        # Initialize correlator from persistence if this is the
        # first time for this scope
        if scope_key not in self._correlators:
            records = self.repository.get_records_by_scope(scope_key)
            self._correlators[scope_key] = ObservationCorrelator(
                initial_records=records
            )
            self._previous_states[scope_key] = {r.device_id: r for r in records}

        correlator = self._correlators[scope_key]
        previous_state = self._previous_states[scope_key]

        # 1. Correlate
        current_records = correlator.correlate(context, observations)
        current_state = {r.device_id: r for r in current_records}

        # 2. Evaluate Transitions
        events: List[LifecycleEvent] = []
        conflicts = []

        for record in current_records:
            prev = previous_state.get(record.device_id)

            if prev is None:
                # Completely new record
                events.append(
                    LifecycleEvent(
                        device_id=record.device_id,
                        event_type=LifecycleEventType.FIRST_SEEN,
                        timestamp=now,
                        description="Device observed for the first time.",
                    )
                )
                events.append(
                    LifecycleEvent(
                        device_id=record.device_id,
                        event_type=LifecycleEventType.BECAME_PRESENT,
                        timestamp=now,
                        description="Device became present.",
                    )
                )
            else:
                # Existing record, check transitions
                if (
                    prev.presence_state == PresenceState.UNSEEN
                    and record.presence_state == PresenceState.PRESENT
                ):
                    events.append(
                        LifecycleEvent(
                            device_id=record.device_id,
                            event_type=LifecycleEventType.BECAME_PRESENT,
                            timestamp=now,
                            description="Device became present again.",
                        )
                    )
                elif (
                    prev.presence_state == PresenceState.PRESENT
                    and record.presence_state == PresenceState.UNSEEN
                ):
                    events.append(
                        LifecycleEvent(
                            device_id=record.device_id,
                            event_type=LifecycleEventType.BECAME_UNSEEN,
                            timestamp=now,
                            description="Device became unseen.",
                        )
                    )

                # Check for new conflicts
                new_conflicts = record.conflicts - prev.conflicts
                for c in new_conflicts:
                    events.append(
                        LifecycleEvent(
                            device_id=record.device_id,
                            event_type=LifecycleEventType.CONFLICT_DETECTED,
                            timestamp=c.observed_at,
                            description=f"Conflict detected: {c.classification.value}",
                        )
                    )
                    conflicts.append(c)

            # Note: For newly created records, all their conflicts are effectively new
            if prev is None:
                for c in record.conflicts:
                    events.append(
                        LifecycleEvent(
                            device_id=record.device_id,
                            event_type=LifecycleEventType.CONFLICT_DETECTED,
                            timestamp=c.observed_at,
                            description=f"Conflict detected: {c.classification.value}",
                        )
                    )
                    conflicts.append(c)

        # Update previous state for next time
        self._previous_states[scope_key] = current_state

        envelope = ScanTransactionEnvelope(
            scope_key=scope_key,
            records=current_records,
            conflicts=conflicts,
            events=events,
            timestamp=now,
        )

        # 3. Handle Persistence & Degraded Mode
        self._commit_envelope(envelope)

        return current_records

    def _commit_envelope(self, envelope: ScanTransactionEnvelope) -> None:
        """
        Attempts to save to persistence. If failed, manages the queue.
        If healthy, tries to flush the queue first.
        """
        # If we have a queue, try to flush it before committing the new one
        if self._pending_queue:
            self.retry_pending_queue()

        # If still degraded, just enqueue
        if self.degraded:
            self._enqueue(envelope)
            return

        try:
            self.repository.save_scan_transaction(envelope)

            # Apply the 30-day retention policy
            threshold = envelope.timestamp - timedelta(days=30)
            self.repository.prune_stale_records(threshold)
        except Exception as e:
            logger.error(f"Persistence failure, entering degraded mode: {e}")
            self.degraded = True
            self._enqueue(envelope)

    def _enqueue(self, envelope: ScanTransactionEnvelope) -> None:
        if len(self._pending_queue) >= self.max_queue_size:
            logger.error("Persistence queue bound reached. Fatal degraded state.")
            raise PersistenceFailure("Maximum pending persistence queue bound reached.")
        self._pending_queue.append(envelope)

    def retry_pending_queue(self) -> None:
        """
        Attempts to drain the pending persistence queue.
        """
        if not self._pending_queue:
            self.degraded = False
            return

        logger.info(f"Retrying {len(self._pending_queue)} pending transactions...")

        while self._pending_queue:
            envelope = self._pending_queue[0]
            try:
                self.repository.save_scan_transaction(envelope)
                self._pending_queue.popleft()
            except Exception as e:
                logger.warning(f"Persistence retry failed: {e}")
                self.degraded = True
                return

        self.degraded = False
        logger.info("Persistence healthy.")
