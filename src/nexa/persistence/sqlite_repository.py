"""
SQLite implementation of the DeviceRepository.
"""

import json
import logging
import sqlite3
from contextlib import contextmanager
from datetime import datetime
from ipaddress import IPv4Address, IPv4Network
from typing import Iterator, List
from uuid import UUID

from nexa.domain.correlation import (
    ConflictClassification,
    ObservationConflict,
    PresenceState,
)
from nexa.domain.device import DeviceRecord
from nexa.domain.lifecycle import (
    DeviceRepository,
    ScanTransactionEnvelope,
)
from nexa.domain.scope import NetworkScope

logger = logging.getLogger(__name__)

SCHEMA_V1 = """
CREATE TABLE IF NOT EXISTS devices (
    device_id TEXT PRIMARY KEY,
    scope_key TEXT NOT NULL,
    network_scope TEXT NOT NULL,
    first_observed_at TEXT NOT NULL,
    last_observed_at TEXT NOT NULL,
    presence_state TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS device_macs (
    device_id TEXT NOT NULL,
    mac_address TEXT NOT NULL,
    FOREIGN KEY(device_id) REFERENCES devices(device_id) ON DELETE CASCADE,
    PRIMARY KEY (device_id, mac_address)
);

CREATE TABLE IF NOT EXISTS device_ips (
    device_id TEXT NOT NULL,
    ip_address TEXT NOT NULL,
    FOREIGN KEY(device_id) REFERENCES devices(device_id) ON DELETE CASCADE,
    PRIMARY KEY (device_id, ip_address)
);

CREATE TABLE IF NOT EXISTS device_conflicts (
    device_id TEXT NOT NULL,
    classification TEXT NOT NULL,
    description TEXT NOT NULL,
    involved_macs TEXT NOT NULL,
    observed_at TEXT NOT NULL,
    FOREIGN KEY(device_id) REFERENCES devices(device_id) ON DELETE CASCADE,
    PRIMARY KEY (device_id, classification, observed_at)
);

CREATE TABLE IF NOT EXISTS lifecycle_events (
    device_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    description TEXT NOT NULL,
    FOREIGN KEY(device_id) REFERENCES devices(device_id) ON DELETE CASCADE,
    PRIMARY KEY (device_id, event_type, timestamp)
);
"""


class SqliteDeviceRepository(DeviceRepository):
    """
    SQLite persistence adapter for DeviceRecord and LifecycleEvent.
    """

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
            conn = sqlite3.connect(
                self.db_path,
                timeout=10.0,
            )
            conn.row_factory = sqlite3.Row
            conn.execute("PRAGMA journal_mode=WAL")
            conn.execute("PRAGMA foreign_keys=ON")
            try:
                with conn:
                    yield conn
            finally:
                conn.close()

    def _initialize_db(self) -> None:
        """Initializes schema and runs migrations."""
        with self._connection() as conn:
            cursor = conn.cursor()
            cursor.execute("PRAGMA user_version")
            row = cursor.fetchone()
            user_version = row[0] if row else 0

            if user_version == 0:
                logger.info("Initializing SQLite database with schema v1")
                cursor.executescript(SCHEMA_V1)
                cursor.execute("PRAGMA user_version = 1")
            elif user_version > 1:
                raise RuntimeError(
                    f"Database version {user_version} is newer "
                    "than application supports."
                )
            # If user_version == 1, we are up to date

    def save_scan_transaction(self, envelope: ScanTransactionEnvelope) -> None:
        """
        Persists a complete scan transaction envelope atomically.
        """
        with self._connection() as conn:
            try:
                cursor = conn.cursor()

                for record in envelope.records:
                    # Upsert device record
                    # We store the serialized network_scope to allow hydrating the
                    # domain object properly e.g.,
                    # NetworkScope(network=IPv4Network('192.168.1.0/24'),
                    # interface_name='eth0', gateway=...)
                    # However, NetworkScope requires actual objects. For simplicity,
                    # we can store a JSON representation.
                    network_scope_json = json.dumps(
                        {
                            "network": (
                                f"{record.network_scope.network_address}/"
                                f"{record.network_scope.prefix_length}"
                            ),
                            "interface_name": record.network_scope.interface_name,
                            "gateway": str(record.network_scope.gateway)
                            if record.network_scope.gateway
                            else None,
                        }
                    )

                    cursor.execute(
                        """
                        INSERT INTO devices (
                            device_id, scope_key, network_scope, first_observed_at,
                            last_observed_at, presence_state
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT(device_id) DO UPDATE SET
                            last_observed_at=excluded.last_observed_at,
                            presence_state=excluded.presence_state
                        """,
                        (
                            str(record.device_id),
                            envelope.scope_key,
                            network_scope_json,
                            record.first_observed_at.isoformat(),
                            record.last_observed_at.isoformat(),
                            record.presence_state.value,
                        ),
                    )

                    # Replace MACs
                    cursor.execute(
                        "DELETE FROM device_macs WHERE device_id = ?",
                        (str(record.device_id),),
                    )
                    for mac in record.mac_addresses:
                        cursor.execute(
                            "INSERT INTO device_macs (device_id, mac_address) "
                            "VALUES (?, ?)",
                            (str(record.device_id), mac),
                        )

                    # Replace IPs
                    cursor.execute(
                        "DELETE FROM device_ips WHERE device_id = ?",
                        (str(record.device_id),),
                    )
                    for ip in record.ipv4_addresses:
                        cursor.execute(
                            "INSERT INTO device_ips (device_id, ip_address) "
                            "VALUES (?, ?)",
                            (str(record.device_id), str(ip)),
                        )

                    # Replace conflicts
                    cursor.execute(
                        "DELETE FROM device_conflicts WHERE device_id = ?",
                        (str(record.device_id),),
                    )
                    for conflict in record.conflicts:
                        cursor.execute(
                            """
                            INSERT INTO device_conflicts (
                            device_id, classification, description,
                            involved_macs, observed_at
                        )
                            VALUES (?, ?, ?, ?, ?)
                            """,
                            (
                                str(record.device_id),
                                conflict.classification.value,
                                conflict.description,
                                json.dumps(list(conflict.involved_macs)),
                                conflict.observed_at.isoformat(),
                            ),
                        )

                for event in envelope.events:
                    cursor.execute(
                        """
                        INSERT OR IGNORE INTO lifecycle_events (
                            device_id, event_type, timestamp, description
                        )
                        VALUES (?, ?, ?, ?)
                        """,
                        (
                            str(event.device_id),
                            event.event_type.value,
                            event.timestamp.isoformat(),
                            event.description,
                        ),
                    )

            except sqlite3.Error as e:
                logger.error(f"Persistence transaction failed: {e}")
                raise

    def get_record_by_id(self, device_id: str) -> DeviceRecord | None:
        """
        Retrieves a specific DeviceRecord by its UUID.
        """
        with self._connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT * FROM devices WHERE device_id = ?", (str(device_id),)
            )
            d_row = cursor.fetchone()

            if not d_row:
                return None

            # Fetch MACs
            cursor.execute(
                "SELECT mac_address FROM device_macs WHERE device_id = ?",
                (str(device_id),),
            )
            macs = frozenset(row["mac_address"] for row in cursor.fetchall())

            # Fetch IPs
            cursor.execute(
                "SELECT ip_address FROM device_ips WHERE device_id = ?",
                (str(device_id),),
            )
            ips = frozenset(IPv4Address(row["ip_address"]) for row in cursor.fetchall())

            # Fetch Conflicts
            cursor.execute(
                "SELECT * FROM device_conflicts WHERE device_id = ?", (str(device_id),)
            )
            conflicts_set = set()
            for c_row in cursor.fetchall():
                conflicts_set.add(
                    ObservationConflict(
                        classification=ConflictClassification(c_row["classification"]),
                        description=c_row["description"],
                        involved_macs=frozenset(json.loads(c_row["involved_macs"])),
                        observed_at=datetime.fromisoformat(c_row["observed_at"]),
                    )
                )

            # Deserialize NetworkScope
            ns_data = json.loads(d_row["network_scope"])
            gw = IPv4Address(ns_data["gateway"]) if ns_data.get("gateway") else None
            net = IPv4Network(ns_data["network"])
            hosts_list = list(net.hosts())
            host_count = len(hosts_list)

            network_scope = NetworkScope(
                network_address=net.network_address,
                broadcast_address=net.broadcast_address,
                prefix_length=net.prefixlen,
                host_count=host_count,
                first_usable_host=hosts_list[0] if host_count > 0 else None,
                last_usable_host=hosts_list[-1] if host_count > 0 else None,
                interface_name=ns_data["interface_name"],
                gateway=gw,
            )

            return DeviceRecord(
                device_id=UUID(str(device_id)),
                network_scope=network_scope,
                mac_addresses=macs,
                ipv4_addresses=ips,
                first_observed_at=datetime.fromisoformat(d_row["first_observed_at"]),
                last_observed_at=datetime.fromisoformat(d_row["last_observed_at"]),
                presence_state=PresenceState(d_row["presence_state"]),
                conflicts=frozenset(conflicts_set),
            )

    def get_records_by_scope(self, scope_key: str) -> List[DeviceRecord]:
        """
        Hydrates all active records for a specific scope.
        """
        with self._connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM devices WHERE scope_key = ?", (scope_key,))
            device_rows = cursor.fetchall()

            records = []
            for d_row in device_rows:
                device_id = str(d_row["device_id"])

                # Fetch MACs
                cursor.execute(
                    "SELECT mac_address FROM device_macs WHERE device_id = ?",
                    (device_id,),
                )
                macs = frozenset(row["mac_address"] for row in cursor.fetchall())

                # Fetch IPs
                cursor.execute(
                    "SELECT ip_address FROM device_ips WHERE device_id = ?",
                    (device_id,),
                )
                ips = frozenset(
                    IPv4Address(row["ip_address"]) for row in cursor.fetchall()
                )

                # Fetch Conflicts
                cursor.execute(
                    "SELECT * FROM device_conflicts WHERE device_id = ?", (device_id,)
                )
                conflicts_set = set()
                for c_row in cursor.fetchall():
                    conflicts_set.add(
                        ObservationConflict(
                            classification=ConflictClassification(
                                c_row["classification"]
                            ),
                            description=c_row["description"],
                            involved_macs=frozenset(json.loads(c_row["involved_macs"])),
                            observed_at=datetime.fromisoformat(c_row["observed_at"]),
                        )
                    )

                # Deserialize NetworkScope
                ns_data = json.loads(d_row["network_scope"])
                gw = IPv4Address(ns_data["gateway"]) if ns_data.get("gateway") else None
                net = IPv4Network(ns_data["network"])
                hosts_list = list(net.hosts())
                host_count = len(hosts_list)

                network_scope = NetworkScope(
                    network_address=net.network_address,
                    broadcast_address=net.broadcast_address,
                    prefix_length=net.prefixlen,
                    host_count=host_count,
                    first_usable_host=hosts_list[0] if host_count > 0 else None,
                    last_usable_host=hosts_list[-1] if host_count > 0 else None,
                    interface_name=ns_data["interface_name"],
                    gateway=gw,
                )

                record = DeviceRecord(
                    device_id=UUID(device_id),
                    network_scope=network_scope,
                    mac_addresses=macs,
                    ipv4_addresses=ips,
                    first_observed_at=datetime.fromisoformat(
                        d_row["first_observed_at"]
                    ),
                    last_observed_at=datetime.fromisoformat(d_row["last_observed_at"]),
                    presence_state=PresenceState(d_row["presence_state"]),
                    conflicts=frozenset(conflicts_set),
                )
                records.append(record)

            return records

    def prune_stale_records(self, threshold: datetime) -> int:
        """
        Executes an atomic cascading delete for all UNSEEN records
        where last_observed_at < threshold.
        Returns the number of deleted records.
        """
        with self._connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "DELETE FROM devices WHERE presence_state = ? AND last_observed_at < ?",
                (PresenceState.UNSEEN.value, threshold.isoformat()),
            )
            return int(cursor.rowcount)
