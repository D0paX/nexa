"""
Ephemeral Nonce Cache for Phase 2 Verification.

Challenge nonces are ephemeral protocol state, not durable trust state.
"""

import threading
from datetime import datetime, timezone
from typing import Dict


class NonceCache:
    """
    In-memory bounded cache for active challenges.
    Provides single-use lookup and deterministic eviction.
    """

    def __init__(self, max_entries: int = 500):
        self._max_entries = max_entries
        self._cache: Dict[str, datetime] = {}
        self._lock = threading.Lock()

    def add_nonce(self, nonce: str, expires_at: datetime) -> bool:
        """
        Add a nonce with its expiration time.
        Returns False if the cache is full (max global concurrent challenges hit).
        """
        with self._lock:
            self._evict_expired()
            if len(self._cache) >= self._max_entries:
                return False
            self._cache[nonce] = expires_at
            return True

    def consume_nonce(self, nonce: str) -> bool:
        """
        Consume a single-use nonce.
        Returns True if the nonce was found and not expired.
        """
        with self._lock:
            expires_at = self._cache.pop(nonce, None)
            if expires_at is None:
                return False

            # Check if expired
            now = datetime.now(timezone.utc)
            if now > expires_at:
                return False

            return True

    def _evict_expired(self) -> None:
        """Deterministically evict expired nonces."""
        now = datetime.now(timezone.utc)
        expired_keys = [
            nonce for nonce, expires_at in self._cache.items() if now > expires_at
        ]
        for key in expired_keys:
            self._cache.pop(key, None)
