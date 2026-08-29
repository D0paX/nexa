"""SQLite implementation of the ActionRepository for Phase 4."""

import json
import logging
import sqlite3
import uuid
from contextlib import contextmanager
from ipaddress import IPv4Address, IPv4Network
from typing import Iterator, List, Optional

from nexa.crypto.primitives import from_rfc3339, to_rfc3339
from nexa.domain.actions import (
    ActionCapability,
    ActionExecution,
    ActionRepository,
    ActionRequest,
    EnforcementBinding,
    EnforcementPlan,
    ExecutionState,
    QuarantinePolicy,
    TargetSnapshot,
)
from nexa.domain.correlation import PresenceState
from nexa.domain.device import DeviceRecord
from nexa.domain.scope import NetworkScope
from nexa.domain.trust import TrustedDeviceIdentity, TrustState

logger = logging.getLogger(__name__)

SCHEMA_V1 = """
CREATE TABLE IF NOT EXISTS action_executions (
    action_id TEXT PRIMARY KEY,
    capability TEXT NOT NULL,
    identity_id TEXT NOT NULL,
    state TEXT NOT NULL,
    requesting_actor TEXT NOT NULL,
    operator_id TEXT,
    rollback_of TEXT,
    requested_at TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    completed_at TEXT,
    target_snapshot_json TEXT NOT NULL,
    authorization_context_json TEXT NOT NULL,
    audit_references_json TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_actions_identity ON action_executions(identity_id);
CREATE INDEX IF NOT EXISTS idx_actions_state ON action_executions(state);
"""

SCHEMA_V2 = """
ALTER TABLE action_executions ADD COLUMN enforcement_plan_json TEXT;
"""


class SqliteActionRepository(ActionRepository):
    """
    SQLite persistence adapter for Phase 4 actions.
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
                logger.info("Initializing SQLite action database with schema v1")
                cursor.executescript(SCHEMA_V1)
                cursor.execute("PRAGMA user_version = 1")
                user_version = 1

            if user_version == 1:
                logger.info("Migrating SQLite action database to schema v2")
                cursor.executescript(SCHEMA_V2)
                cursor.execute("PRAGMA user_version = 2")
                user_version = 2

            if user_version > 2:
                raise RuntimeError(
                    f"Action Database version {user_version} is newer "
                    "than application supports."
                )

    def _serialize_target_snapshot(self, snapshot: TargetSnapshot) -> str:
        """Serialize TargetSnapshot to JSON for persistence."""
        net = snapshot.network_scope
        return json.dumps(
            {
                "action_id": str(snapshot.action_id),
                "trusted_identity": {
                    "identity_id": str(snapshot.trusted_identity.identity_id),
                    "state": snapshot.trusted_identity.state.value,
                    "created_at": to_rfc3339(snapshot.trusted_identity.created_at),
                    "updated_at": to_rfc3339(snapshot.trusted_identity.updated_at),
                },
                "device_record": {
                    "device_id": str(snapshot.device_record.device_id),
                    "mac_addresses": list(snapshot.device_record.mac_addresses),
                    "ipv4_addresses": [
                        str(ip) for ip in snapshot.device_record.ipv4_addresses
                    ],
                    "first_observed_at": to_rfc3339(
                        snapshot.device_record.first_observed_at
                    ),
                    "last_observed_at": to_rfc3339(
                        snapshot.device_record.last_observed_at
                    ),
                    "presence_state": snapshot.device_record.presence_state.value,
                },
                "network_scope": {
                    "network": f"{net.network_address}/{net.prefix_length}",
                    "interface_name": net.interface_name,
                    "gateway": str(net.gateway) if net.gateway else None,
                },
                "ip_address": snapshot.ip_address,
                "mac_address": snapshot.mac_address,
                "observation_timestamp": to_rfc3339(snapshot.observation_timestamp),
                "cryptographic_freshness": to_rfc3339(snapshot.cryptographic_freshness),
                "authorization_context": snapshot.authorization_context,
            }
        )

    def _serialize_enforcement_plan(
        self, plan: Optional[EnforcementPlan]
    ) -> Optional[str]:
        if not plan:
            return None
        bindings = []
        for b in plan.enforcement_bindings:
            bindings.append(
                {
                    "identity_id": str(b.identity_id),
                    "scope_id": str(b.scope_id),
                    "ip_address": b.ip_address,
                    "mac_address": b.mac_address,
                }
            )

        qp = None
        if plan.quarantine_policy:
            qp = {
                "permit_dns": plan.quarantine_policy.permit_dns,
                "permit_dhcp": plan.quarantine_policy.permit_dhcp,
                "permit_gateway": plan.quarantine_policy.permit_gateway,
                "permit_verifier_ips": plan.quarantine_policy.permit_verifier_ips,
            }

        return json.dumps(
            {
                "action_id": str(plan.action_id),
                "capability": plan.capability.value,
                "enforcement_bindings": bindings,
                "quarantine_policy": qp,
            }
        )

    def _deserialize_target_snapshot(self, data_str: str) -> TargetSnapshot:
        """Deserialize TargetSnapshot from JSON."""
        data = json.loads(data_str)

        id_data = data["trusted_identity"]
        trusted_identity = TrustedDeviceIdentity(
            identity_id=uuid.UUID(id_data["identity_id"]),
            state=TrustState(id_data["state"]),
            created_at=from_rfc3339(id_data["created_at"]),
            updated_at=from_rfc3339(id_data["updated_at"]),
        )

        ns_data = data["network_scope"]
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

        dr_data = data["device_record"]
        device_record = DeviceRecord(
            device_id=uuid.UUID(dr_data["device_id"]),
            network_scope=network_scope,
            mac_addresses=frozenset(dr_data["mac_addresses"]),
            ipv4_addresses=frozenset(
                IPv4Address(ip) for ip in dr_data["ipv4_addresses"]
            ),
            first_observed_at=from_rfc3339(dr_data["first_observed_at"]),
            last_observed_at=from_rfc3339(dr_data["last_observed_at"]),
            presence_state=PresenceState(dr_data["presence_state"]),
            conflicts=frozenset(),
        )

        return TargetSnapshot(
            action_id=uuid.UUID(data["action_id"]),
            trusted_identity=trusted_identity,
            device_record=device_record,
            network_scope=network_scope,
            ip_address=data["ip_address"],
            mac_address=data["mac_address"],
            observation_timestamp=from_rfc3339(data["observation_timestamp"]),
            cryptographic_freshness=from_rfc3339(data["cryptographic_freshness"]),
            authorization_context=data["authorization_context"],
        )

    def _deserialize_enforcement_plan(
        self, data_str: Optional[str]
    ) -> Optional[EnforcementPlan]:
        if not data_str:
            return None
        data = json.loads(data_str)
        bindings = []
        for b in data.get("enforcement_bindings", []):
            bindings.append(
                EnforcementBinding(
                    identity_id=uuid.UUID(b["identity_id"]),
                    scope_id=str(b.get("scope_id", b.get("action_id"))),
                    ip_address=b["ip_address"],
                    mac_address=b["mac_address"],
                )
            )

        qp = None
        qp_data = data.get("quarantine_policy")
        if qp_data:
            qp = QuarantinePolicy(
                permit_dns=qp_data.get("permit_dns", True),
                permit_dhcp=qp_data.get("permit_dhcp", True),
                permit_gateway=qp_data.get("permit_gateway", True),
                permit_verifier_ips=qp_data.get("permit_verifier_ips", []),
            )

        return EnforcementPlan(
            action_id=uuid.UUID(data["action_id"]),
            capability=ActionCapability(data["capability"]),
            enforcement_bindings=bindings,
            quarantine_policy=qp,
        )

    def save_execution(self, execution: ActionExecution) -> None:
        """Persist or update an ActionExecution record."""
        with self._connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                """
                INSERT INTO action_executions (
                    action_id, capability, identity_id, state, requesting_actor,
                    operator_id, rollback_of, requested_at, created_at,
                    updated_at, completed_at, target_snapshot_json,
                    authorization_context_json, audit_references_json,
                    enforcement_plan_json
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(action_id) DO UPDATE SET
                    state=excluded.state,
                    operator_id=excluded.operator_id,
                    updated_at=excluded.updated_at,
                    completed_at=excluded.completed_at,
                    audit_references_json=excluded.audit_references_json,
                    enforcement_plan_json=excluded.enforcement_plan_json
                """,
                (
                    str(execution.action_id),
                    execution.request.capability.value,
                    str(execution.request.identity_id),
                    execution.state.value,
                    execution.request.requesting_actor,
                    execution.operator_id,
                    str(execution.rollback_of) if execution.rollback_of else None,
                    to_rfc3339(execution.request.requested_at),
                    to_rfc3339(execution.created_at),
                    to_rfc3339(execution.updated_at),
                    to_rfc3339(execution.completed_at)
                    if execution.completed_at
                    else None,
                    self._serialize_target_snapshot(execution.request.target_snapshot),
                    json.dumps(execution.request.authorization_context),
                    json.dumps(execution.audit_references),
                    self._serialize_enforcement_plan(execution.enforcement_plan),
                ),
            )

    def _row_to_execution(self, row: sqlite3.Row) -> ActionExecution:
        target_snapshot = self._deserialize_target_snapshot(row["target_snapshot_json"])
        request = ActionRequest(
            action_id=uuid.UUID(row["action_id"]),
            capability=ActionCapability(row["capability"]),
            identity_id=uuid.UUID(row["identity_id"]),
            requesting_actor=row["requesting_actor"],
            authorization_context=json.loads(row["authorization_context_json"]),
            target_snapshot=target_snapshot,
            requested_at=from_rfc3339(row["requested_at"]),
            operator_id=row["operator_id"],
        )

        return ActionExecution(
            action_id=uuid.UUID(row["action_id"]),
            request=request,
            state=ExecutionState(row["state"]),
            created_at=from_rfc3339(row["created_at"]),
            updated_at=from_rfc3339(row["updated_at"]),
            completed_at=from_rfc3339(row["completed_at"])
            if row["completed_at"]
            else None,
            operator_id=row["operator_id"],
            rollback_of=uuid.UUID(row["rollback_of"]) if row["rollback_of"] else None,
            audit_references=json.loads(row["audit_references_json"]),
            enforcement_plan=self._deserialize_enforcement_plan(
                row["enforcement_plan_json"]
            )
            if "enforcement_plan_json" in row.keys()
            else None,
        )

    def get_execution(self, action_id: uuid.UUID) -> Optional[ActionExecution]:
        """Retrieve an execution by action_id."""
        with self._connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT * FROM action_executions WHERE action_id = ?", (str(action_id),)
            )
            row = cursor.fetchone()
            if not row:
                return None
            return self._row_to_execution(row)

    def get_executions_by_identity(
        self, identity_id: uuid.UUID
    ) -> List[ActionExecution]:
        """Retrieve executions associated with a given identity."""
        with self._connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT * FROM action_executions WHERE identity_id = ? "
                "ORDER BY created_at DESC",
                (str(identity_id),),
            )
            return [self._row_to_execution(row) for row in cursor.fetchall()]

    def get_active_executions(self) -> List[ActionExecution]:
        """Retrieve all executions currently in EXECUTING or RECONCILING state."""
        with self._connection() as conn:
            cursor = conn.cursor()
            cursor.execute(
                "SELECT * FROM action_executions WHERE state IN (?, ?)",
                (ExecutionState.EXECUTING.value, ExecutionState.RECONCILING.value),
            )
            return [self._row_to_execution(row) for row in cursor.fetchall()]
