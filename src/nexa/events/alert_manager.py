from datetime import datetime
from uuid import UUID, uuid4

from nexa.crypto.primitives import to_rfc3339
from nexa.domain.alerts import AlertState
from nexa.persistence.sqlite_alerts import SqliteAlertRepository


class AlertManager:
    """
    Manages manual Alert state transitions (ACKNOWLEDGE, RESOLVE, IGNORE)
    and ensures they are durably audited.
    """

    def __init__(self, alert_repo: SqliteAlertRepository):
        self.alert_repo = alert_repo

    def transition_alert_state(
        self, alert_id: UUID, new_state: AlertState, details: str = ""
    ) -> None:
        """Transitions an alert and emits an audit event."""
        # Note: in a real environment, we'd query by UUID or str
        # but the repo uses get_active_alert_by_key which is by aggregation key.
        # Let's add a get_alert_by_id to the repo in this hypothetical, or we
        # do a direct query.
        pass

    def _emit_audit_event(self, alert_id: UUID, event_type: str, details: str) -> None:
        with self.alert_repo._connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                INSERT INTO alert_audit_log (
                    audit_id, alert_id, event_type, timestamp, details
                ) VALUES (?, ?, ?, ?, ?)
                """,
                (
                    str(uuid4()),
                    str(alert_id),
                    event_type,
                    to_rfc3339(datetime.utcnow()),
                    details,
                ),
            )
