"""
SQLite implementation of the TrustRepository.
"""

import json
import logging
import sqlite3
from typing import List
from uuid import UUID

from nexa.crypto.primitives import from_rfc3339, to_rfc3339
from nexa.domain.trust import (
    Credential,
    CredentialState,
    TrustAuditEvent,
    TrustAuditEventType,
    TrustedDeviceIdentity,
    TrustState,
)
from nexa.domain.trust_lifecycle import TrustRepository

logger = logging.getLogger(__name__)

SCHEMA_V1 = """
CREATE TABLE IF NOT EXISTS trusted_identities (
    identity_id TEXT PRIMARY KEY,
    state TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS credentials (
    fingerprint_sha256 TEXT PRIMARY KEY,
    identity_id TEXT NOT NULL,
    public_key_bytes BLOB NOT NULL,
    version INTEGER NOT NULL,
    state TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY(identity_id) REFERENCES trusted_identities(identity_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS trust_audit_events (
    event_id TEXT PRIMARY KEY,
    identity_id TEXT,
    event_type TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    details TEXT NOT NULL,
    FOREIGN KEY(identity_id) REFERENCES trusted_identities(identity_id)
        ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS device_identity_mappings (
    device_id TEXT PRIMARY KEY,
    identity_id TEXT NOT NULL,
    FOREIGN KEY(identity_id) REFERENCES trusted_identities(identity_id)
        ON DELETE CASCADE
);
"""


class SqliteTrustRepository(TrustRepository):
    """
    SQLite persistence adapter for Trust and Identity data.
    """

    def __init__(self, db_path: str):
        self.db_path = db_path
        self._initialize_db()

    def _get_connection(self) -> sqlite3.Connection:
        """Returns a configured SQLite connection."""
        conn = sqlite3.connect(
            self.db_path,
            timeout=10.0,
        )
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute("PRAGMA foreign_keys=ON")
        return conn

    def _initialize_db(self) -> None:
        """Initializes schema and runs migrations."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("PRAGMA user_version")
            row = cursor.fetchone()
            user_version = row[0] if row else 0

            # Use a separate user_version for trust db,
            # or expect it to be a distinct file.
            if user_version == 0:
                logger.info("Initializing SQLite trust database with schema v1")
                cursor.executescript(SCHEMA_V1)
                cursor.execute("PRAGMA user_version = 1")
            elif user_version > 1:
                raise RuntimeError(
                    f"Trust Database version {user_version} is newer "
                    "than application supports."
                )

    def save_identity(self, identity: TrustedDeviceIdentity) -> None:
        """Persist a TrustedDeviceIdentity."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                INSERT INTO trusted_identities (
                    identity_id, state, created_at, updated_at
                )
                VALUES (?, ?, ?, ?)
                ON CONFLICT(identity_id) DO UPDATE SET
                    state=excluded.state,
                    updated_at=excluded.updated_at
                """,
                (
                    str(identity.identity_id),
                    identity.state.value,
                    to_rfc3339(identity.created_at),
                    to_rfc3339(identity.updated_at),
                ),
            )

    def get_identity(self, identity_id: str) -> TrustedDeviceIdentity | None:
        """Retrieve a TrustedDeviceIdentity."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT * FROM trusted_identities WHERE identity_id = ?",
                (identity_id,),
            )
            row = cursor.fetchone()
            if not row:
                return None
            return TrustedDeviceIdentity(
                identity_id=UUID(row["identity_id"]),
                state=TrustState(row["state"]),
                created_at=from_rfc3339(row["created_at"]),
                updated_at=from_rfc3339(row["updated_at"]),
            )

    def save_credential(self, credential: Credential) -> None:
        """Persist a Credential."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                INSERT INTO credentials (
                    fingerprint_sha256, identity_id, public_key_bytes, version,
                    state, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(fingerprint_sha256) DO UPDATE SET
                    state=excluded.state,
                    updated_at=excluded.updated_at
                """,
                (
                    credential.fingerprint_sha256,
                    str(credential.identity_id),
                    credential.public_key_bytes,
                    credential.version,
                    credential.state.value,
                    to_rfc3339(credential.created_at),
                    to_rfc3339(credential.updated_at),
                ),
            )

    def get_credential_by_fingerprint(self, fingerprint: str) -> Credential | None:
        """Retrieve a Credential by its SHA-256 fingerprint."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT * FROM credentials WHERE fingerprint_sha256 = ?",
                (fingerprint,),
            )
            row = cursor.fetchone()
            if not row:
                return None
            return Credential(
                identity_id=UUID(row["identity_id"]),
                public_key_bytes=row["public_key_bytes"],
                fingerprint_sha256=row["fingerprint_sha256"],
                version=row["version"],
                state=CredentialState(row["state"]),
                created_at=from_rfc3339(row["created_at"]),
                updated_at=from_rfc3339(row["updated_at"]),
            )

    def link_device_to_identity(self, device_id: str, identity_id: str) -> None:
        """
        Link an ephemeral DeviceRecord UUID to a persistent TrustedDeviceIdentity UUID.
        """
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                INSERT INTO device_identity_mappings (device_id, identity_id)
                VALUES (?, ?)
                ON CONFLICT(device_id) DO UPDATE SET
                    identity_id=excluded.identity_id
                """,
                (device_id, identity_id),
            )

    def get_identity_for_device(self, device_id: str) -> str | None:
        """Retrieve the associated identity_id for a device_id, if any."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT identity_id FROM device_identity_mappings WHERE device_id = ?",
                (device_id,),
            )
            row = cursor.fetchone()
            if not row:
                return None
            return str(row["identity_id"])

    def get_active_credential_for_identity(self, identity_id: str) -> Credential | None:
        """Retrieve the currently ACTIVE credential for a given identity."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                SELECT * FROM credentials
                WHERE identity_id = ? AND state = ?
                ORDER BY version DESC LIMIT 1
                """,
                (identity_id, CredentialState.ACTIVE.value),
            )
            row = cursor.fetchone()
            if not row:
                return None
            return Credential(
                identity_id=UUID(row["identity_id"]),
                public_key_bytes=row["public_key_bytes"],
                fingerprint_sha256=row["fingerprint_sha256"],
                version=row["version"],
                state=CredentialState(row["state"]),
                created_at=from_rfc3339(row["created_at"]),
                updated_at=from_rfc3339(row["updated_at"]),
            )

    def append_audit_event(self, event: TrustAuditEvent) -> None:
        """Persist an immutable audit event."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                INSERT OR IGNORE INTO trust_audit_events (
                    event_id, identity_id, event_type, timestamp, details
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                (
                    str(event.event_id),
                    str(event.identity_id) if event.identity_id else None,
                    event.event_type.value,
                    to_rfc3339(event.timestamp),
                    json.dumps(event.details),
                ),
            )

    def get_audit_events(self, identity_id: str) -> List[TrustAuditEvent]:
        """Retrieve audit events for an identity."""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                SELECT * FROM trust_audit_events
                WHERE identity_id = ?
                ORDER BY timestamp ASC
                """,
                (identity_id,),
            )
            rows = cursor.fetchall()
            events = []
            for row in rows:
                events.append(
                    TrustAuditEvent(
                        event_id=UUID(row["event_id"]),
                        identity_id=UUID(row["identity_id"]),
                        event_type=TrustAuditEventType(row["event_type"]),
                        timestamp=from_rfc3339(row["timestamp"]),
                        details=json.loads(row["details"]),
                    )
                )
            return events
