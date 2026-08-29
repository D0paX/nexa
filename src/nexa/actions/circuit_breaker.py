"""Circuit breaker for Phase 4 enforcement actions."""

import logging
from dataclasses import dataclass
from datetime import datetime, timezone

logger = logging.getLogger(__name__)


@dataclass
class CircuitBreakerState:
    """Current state of the enforcement circuit breaker."""

    paused: bool = False
    reason: str = ""
    last_tripped_at: datetime | None = None
    manual_override: bool = False

    # Simple counters for tracking (in memory for this implementation)
    # In a fully distributed system, these might be backed by Redis or DB.
    recent_failures: int = 0
    recent_rollbacks: int = 0
    queued_actions: int = 0


class EnforcementCircuitBreaker:
    """
    Monitors enforcement health and globally pauses execution if
    thresholds are exceeded.
    """

    def __init__(
        self,
        max_failure_threshold: int = 5,
        max_queue_depth: int = 1000,
        max_rollback_failures: int = 2,
    ):
        self.max_failure_threshold = max_failure_threshold
        self.max_queue_depth = max_queue_depth
        self.max_rollback_failures = max_rollback_failures
        self._state = CircuitBreakerState()
        self._last_reset = datetime.now(timezone.utc)

    def is_paused(self) -> bool:
        """Check if enforcement is currently paused globally."""
        return self._state.paused

    def get_state(self) -> CircuitBreakerState:
        return self._state

    def _check_thresholds(self) -> None:
        if self._state.paused:
            return

        if self._state.recent_failures >= self.max_failure_threshold:
            self._trip("Action failure threshold exceeded.")

        elif self._state.queued_actions >= self.max_queue_depth:
            self._trip("Action queue saturation.")

        elif self._state.recent_rollbacks >= self.max_rollback_failures:
            self._trip("Rollback failure threshold exceeded.")

    def _trip(self, reason: str) -> None:
        logger.critical(f"ENFORCEMENT_PAUSED: {reason}")
        self._state.paused = True
        self._state.reason = reason
        self._state.last_tripped_at = datetime.now(timezone.utc)

    def record_action_failure(self) -> None:
        self._state.recent_failures += 1
        self._check_thresholds()

    def record_rollback_failure(self) -> None:
        self._state.recent_rollbacks += 1
        self._check_thresholds()

    def record_queue_depth(self, depth: int) -> None:
        self._state.queued_actions = depth
        self._check_thresholds()

    def record_action_success(self) -> None:
        """Decrease failure counters on success to prevent accidental tripping."""
        self._state.recent_failures = max(0, self._state.recent_failures - 1)

    def resume_enforcement(self) -> None:
        """Explicitly clear the circuit breaker. Requires operator action."""
        logger.info("ENFORCEMENT_RESUMED: Circuit breaker manually reset by operator.")
        self._state.paused = False
        self._state.reason = ""
        self._state.recent_failures = 0
        self._state.recent_rollbacks = 0
        self._state.queued_actions = 0
        self._last_reset = datetime.now(timezone.utc)
