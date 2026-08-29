import sqlite3
from typing import Any, Optional

from nexa.persistence.sqlite_alerts import SqliteAlertRepository
from nexa.persistence.sqlite_repository import SqliteDeviceRepository
from nexa.persistence.sqlite_trust import SqliteTrustRepository


class TransactionCoordinator:
    """
    Coordinates transactions across Phase 1 (Device), Phase 2 (Trust),
    and Phase 3 (Alert) SQLite repositories.

    If they share a database file, they can share the exact same sqlite3.Connection
    and participate in a single atomic transaction.
    """

    def __init__(
        self,
        db_path: str,
        device_repo: SqliteDeviceRepository,
        trust_repo: SqliteTrustRepository,
        alert_repo: SqliteAlertRepository,
    ):
        self.db_path = db_path
        self.device_repo = device_repo
        self.trust_repo = trust_repo
        self.alert_repo = alert_repo
        self._conn: Optional[sqlite3.Connection] = None

    def __enter__(self) -> "TransactionCoordinator":
        """Starts a shared transaction."""
        self._conn = sqlite3.connect(self.db_path, timeout=10.0)
        self._conn.row_factory = sqlite3.Row
        self._conn.execute("PRAGMA journal_mode=WAL")
        self._conn.execute("PRAGMA foreign_keys=ON")

        # Inject the connection into repositories
        self.device_repo.set_shared_connection(self._conn)
        self.trust_repo.set_shared_connection(self._conn)
        self.alert_repo.set_shared_connection(self._conn)

        # Explicitly begin a transaction
        self._conn.execute("BEGIN IMMEDIATE")
        return self

    def __exit__(self, exc_type: Any, exc_val: Any, exc_tb: Any) -> None:
        """Commits or rolls back the shared transaction."""
        try:
            if self._conn:
                if exc_type is None:
                    self._conn.commit()
                else:
                    self._conn.rollback()
        finally:
            self.device_repo.set_shared_connection(None)
            self.trust_repo.set_shared_connection(None)
            self.alert_repo.set_shared_connection(None)

            if self._conn:
                self._conn.close()
            self._conn = None
