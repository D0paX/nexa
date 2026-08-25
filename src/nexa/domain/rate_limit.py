"""
Rate Limiting and Resource Bounds for Active Verification.
"""

import threading
from collections import defaultdict
from datetime import datetime, timedelta, timezone


class RateLimiter:
    """
    Enforces active verification and enrollment bounds.
    """

    def __init__(
        self,
        max_concurrent_global: int = 500,
        max_concurrent_per_scope: int = 50,
        verification_interval_seconds: int = 30,
        max_enrollment_budget: int = 50,
    ):
        self._max_global = max_concurrent_global
        self._max_per_scope = max_concurrent_per_scope
        self._interval = timedelta(seconds=verification_interval_seconds)
        self._max_enrollment = max_enrollment_budget

        self._inflight_global = 0
        self._inflight_enrollment = 0
        self._inflight_per_scope: defaultdict[str, int] = defaultdict(int)

        self._last_verification_attempt: dict[str, datetime] = {}

        self._lock = threading.Lock()

    def try_acquire_verification(self, scope_id: str, device_id: str) -> bool:
        """
        Attempt to acquire a verification slot for a device in a scope.
        Enforces per-device interval, per-scope concurrency, and global concurrency.
        """
        with self._lock:
            now = datetime.now(timezone.utc)

            # 1. Per-device interval limit
            last_attempt = self._last_verification_attempt.get(device_id)
            if last_attempt and now - last_attempt < self._interval:
                return False

            # 2. Global concurrency limit
            if self._inflight_global >= self._max_global:
                return False

            # 3. Per-scope concurrency limit
            if self._inflight_per_scope[scope_id] >= self._max_per_scope:
                return False

            # Acquire
            self._inflight_global += 1
            self._inflight_per_scope[scope_id] += 1
            self._last_verification_attempt[device_id] = now
            return True

    def release_verification(self, scope_id: str) -> None:
        """Release an inflight verification slot."""
        with self._lock:
            if self._inflight_global > 0:
                self._inflight_global -= 1
            if self._inflight_per_scope[scope_id] > 0:
                self._inflight_per_scope[scope_id] -= 1

    def try_acquire_enrollment(self) -> bool:
        """
        Attempt to acquire an enrollment slot from the separated budget.
        """
        with self._lock:
            if self._inflight_enrollment >= self._max_enrollment:
                return False
            self._inflight_enrollment += 1
            return True

    def release_enrollment(self) -> None:
        """Release an inflight enrollment slot."""
        with self._lock:
            if self._inflight_enrollment > 0:
                self._inflight_enrollment -= 1
