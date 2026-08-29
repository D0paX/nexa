import json
import logging
import sqlite3
from contextlib import contextmanager
from typing import Iterator, List, Optional
from uuid import UUID

from nexa.crypto.primitives import from_rfc3339, to_rfc3339
from nexa.domain.alerts import (
    Alert,
    AlertState,
    Notification,
    NotificationDeliveryState,
)
from nexa.domain.events import SecurityEvent, Severity

logger = logging.getLogger(__name__)

SCHEMA_V1 = """
CREATE TABLE IF NOT EXISTS security_event_outbox (
    event_id TEXT PRIMARY KEY,
    event_class TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    severity TEXT NOT NULL,
    identity_id TEXT,
    device_id TEXT,
    network_scope TEXT NOT NULL,
    context TEXT NOT NULL,
    processed INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS alerts (
    alert_id TEXT PRIMARY KEY,
    aggregation_key TEXT NOT NULL,
    state TEXT NOT NULL,
    severity TEXT NOT NULL,
    event_class TEXT NOT NULL,
    identity_id TEXT,
    device_id TEXT,
    network_scope TEXT NOT NULL,
    event_count INTEGER NOT NULL DEFAULT 1,
    first_seen TEXT NOT NULL,
    last_seen TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_alerts_agg_state ON alerts(aggregation_key, state);

CREATE TABLE IF NOT EXISTS notification_queue (
    notification_id TEXT PRIMARY KEY,
    alert_id TEXT NOT NULL,
    state TEXT NOT NULL,
    payload TEXT NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY(alert_id) REFERENCES alerts(alert_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_notif_state ON notification_queue(state);

CREATE TABLE IF NOT EXISTS alert_audit_log (
    audit_id TEXT PRIMARY KEY,
    alert_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    details TEXT NOT NULL,
    FOREIGN KEY(alert_id) REFERENCES alerts(alert_id) ON DELETE CASCADE
);
"""

schema_version: int = 1


class SqliteAlertRepository:
    def __init__(self, db_path: str):
        self.db_path = db_path
        self._shared_conn: sqlite3.Connection | None = None
        self._initialize_db()

    def set_shared_connection(self, conn: sqlite3.Connection | None) -> None:
        self._shared_conn = conn

    @contextmanager
    def _connection(self) -> Iterator[sqlite3.Connection]:
        if self._shared_conn:
            yield self._shared_conn
        else:
            conn = sqlite3.connect(self.db_path, timeout=10.0)
            conn.row_factory = sqlite3.Row
            conn.execute("PRAGMA journal_mode=WAL")
            conn.execute("PRAGMA foreign_keys=ON")
            try:
                with conn:
                    yield conn
            finally:
                conn.close()

    def _initialize_db(self) -> None:
        with self._connection() as conn:
            cursor = conn.cursor()
            cursor.execute("PRAGMA user_version")

            # Use user_version = 2 for alerts assuming it's in the same DB
            # Or we can just run it
            cursor.executescript(SCHEMA_V1)

    def append_outbox_event(self, event: SecurityEvent) -> None:
        with self._connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                INSERT OR IGNORE INTO security_event_outbox (
                    event_id, event_class, timestamp, severity,
                    identity_id, device_id, network_scope, context, processed
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                (
                    str(event.event_id),
                    event.event_class,
                    to_rfc3339(event.timestamp),
                    event.severity.value,
                    str(event.identity_id) if event.identity_id else None,
                    str(event.device_id) if event.device_id else None,
                    event.network_scope,
                    json.dumps(event.context),
                ),
            )

    def get_unprocessed_outbox_events(self, limit: int = 1000) -> List[SecurityEvent]:
        with self._connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                SELECT * FROM security_event_outbox
                WHERE processed = 0
                ORDER BY timestamp ASC LIMIT ?
                """,
                (limit,),
            )
            rows = cursor.fetchall()
            return [
                SecurityEvent(
                    event_id=UUID(row["event_id"]),
                    event_class=row["event_class"],
                    timestamp=from_rfc3339(row["timestamp"]),
                    severity=Severity(row["severity"]),
                    identity_id=UUID(row["identity_id"])
                    if row["identity_id"]
                    else None,
                    device_id=UUID(row["device_id"]) if row["device_id"] else None,
                    network_scope=row["network_scope"],
                    context=json.loads(row["context"]),
                )
                for row in rows
            ]

    def mark_outbox_events_processed(self, event_ids: List[UUID]) -> None:
        if not event_ids:
            return
        with self._connection() as conn:
            cursor = conn.cursor()
            query = (
                "UPDATE security_event_outbox SET processed = 1 "
                f"WHERE event_id IN ({','.join(['?'] * len(event_ids))})"
            )
            cursor.execute(query, [str(eid) for eid in event_ids])

    def get_unprocessed_outbox_count(self) -> int:
        with self._connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT COUNT(*) as c FROM security_event_outbox WHERE processed = 0"
            )
            row = cursor.fetchone()
            return row["c"] if row else 0

    def get_active_alert_by_key(self, aggregation_key: str) -> Optional[Alert]:
        with self._connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                SELECT * FROM alerts
                WHERE aggregation_key = ? AND state IN (?, ?)
                LIMIT 1
                """,
                (aggregation_key, AlertState.NEW.value, AlertState.ACKNOWLEDGED.value),
            )
            row = cursor.fetchone()
            if not row:
                return None
            return self._row_to_alert(row)

    def save_alert(self, alert: Alert) -> None:
        with self._connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                INSERT INTO alerts (
                    alert_id, aggregation_key, state, severity, event_class,
                    identity_id, device_id, network_scope, event_count,
                    first_seen, last_seen
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(alert_id) DO UPDATE SET
                    state=excluded.state,
                    event_count=excluded.event_count,
                    last_seen=excluded.last_seen
                """,
                (
                    str(alert.alert_id),
                    alert.aggregation_key,
                    alert.state.value,
                    alert.severity.value,
                    alert.event_class,
                    str(alert.identity_id) if alert.identity_id else None,
                    str(alert.device_id) if alert.device_id else None,
                    alert.network_scope,
                    alert.event_count,
                    to_rfc3339(alert.first_seen),
                    to_rfc3339(alert.last_seen),
                ),
            )

    def _row_to_alert(self, row: sqlite3.Row) -> Alert:
        return Alert(
            alert_id=UUID(row["alert_id"]),
            aggregation_key=row["aggregation_key"],
            state=AlertState(row["state"]),
            severity=Severity(row["severity"]),
            event_class=row["event_class"],
            identity_id=UUID(row["identity_id"]) if row["identity_id"] else None,
            device_id=UUID(row["device_id"]) if row["device_id"] else None,
            network_scope=row["network_scope"],
            event_count=row["event_count"],
            first_seen=from_rfc3339(row["first_seen"]),
            last_seen=from_rfc3339(row["last_seen"]),
        )

    def enqueue_notification(self, notification: Notification) -> None:
        with self._connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                INSERT INTO notification_queue (
                    notification_id, alert_id, state, payload, retry_count,
                    next_retry_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    str(notification.notification_id),
                    str(notification.alert_id),
                    notification.state.value,
                    json.dumps(notification.payload),
                    notification.retry_count,
                    to_rfc3339(notification.next_retry_at)
                    if notification.next_retry_at
                    else None,
                    to_rfc3339(notification.created_at),
                    to_rfc3339(notification.updated_at),
                ),
            )

    def get_pending_notifications(self, limit: int = 50) -> List[Notification]:
        with self._connection() as conn:
            cursor = conn.cursor()
            # In SQLite datetime strings: A < B works if RFC3339 formatted
            from datetime import datetime

            now_rfc = to_rfc3339(datetime.utcnow())
            cursor.execute(
                """
                SELECT * FROM notification_queue
                WHERE state IN (?, ?)
                AND (next_retry_at IS NULL OR next_retry_at <= ?)
                ORDER BY created_at ASC LIMIT ?
                """,
                (
                    NotificationDeliveryState.QUEUED.value,
                    NotificationDeliveryState.RETRYING.value,
                    now_rfc,
                    limit,
                ),
            )
            rows = cursor.fetchall()
            return [self._row_to_notification(row) for row in rows]

    def update_notification(self, notification: Notification) -> None:
        with self._connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                UPDATE notification_queue SET
                    state = ?,
                    retry_count = ?,
                    next_retry_at = ?,
                    updated_at = ?
                WHERE notification_id = ?
                """,
                (
                    notification.state.value,
                    notification.retry_count,
                    to_rfc3339(notification.next_retry_at)
                    if notification.next_retry_at
                    else None,
                    to_rfc3339(notification.updated_at),
                    str(notification.notification_id),
                ),
            )

    def _row_to_notification(self, row: sqlite3.Row) -> Notification:
        return Notification(
            notification_id=UUID(row["notification_id"]),
            alert_id=UUID(row["alert_id"]),
            state=NotificationDeliveryState(row["state"]),
            payload=json.loads(row["payload"]),
            retry_count=row["retry_count"],
            next_retry_at=from_rfc3339(row["next_retry_at"])
            if row["next_retry_at"]
            else None,
            created_at=from_rfc3339(row["created_at"]),
            updated_at=from_rfc3339(row["updated_at"]),
        )
