"""Action Domain Model (Phase 4)."""

import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Any, Optional, Protocol

from nexa.domain.device import DeviceRecord
from nexa.domain.scope import NetworkScope
from nexa.domain.trust import TrustedDeviceIdentity


class ActionCapability(Enum):
    """The supported enforcement actions."""

    QUARANTINE_DEVICE = "QUARANTINE_DEVICE"
    RELEASE_QUARANTINE = "RELEASE_QUARANTINE"
    REQUIRE_REVERIFICATION = "REQUIRE_REVERIFICATION"


class EnforcementMode(Enum):
    """The execution mode for the enforcement action."""

    AUDIT_ONLY = "AUDIT_ONLY"
    ENFORCEMENT_ENABLED = "ENFORCEMENT_ENABLED"


class NormalizedState(Enum):
    """The normalized status of a firewall target binding."""

    PRESENT = "PRESENT"
    ABSENT = "ABSENT"
    UNKNOWN = "UNKNOWN"
    ADAPTER_UNAVAILABLE = "ADAPTER_UNAVAILABLE"


class ExecutionState(Enum):
    """The lifecycle state of an action execution."""

    REQUESTED = "REQUESTED"
    AUTHORIZED = "AUTHORIZED"
    DENIED = "DENIED"
    EXECUTING = "EXECUTING"
    RECONCILING = "RECONCILING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    ROLLBACK_REQUESTED = "ROLLBACK_REQUESTED"
    ROLLED_BACK = "ROLLED_BACK"
    ROLLBACK_FAILED = "ROLLBACK_FAILED"


class OperatorApprovalMode(Enum):
    """How the operator interacts with an action."""

    AUTOMATIC = "AUTOMATIC"
    OPERATOR_REQUIRED = "OPERATOR_REQUIRED"
    OPERATOR_ONLY = "OPERATOR_ONLY"


@dataclass(frozen=True)
class TargetSnapshot:
    """A point-in-time snapshot of the target state immediately prior to enforcement."""

    action_id: uuid.UUID
    trusted_identity: TrustedDeviceIdentity
    device_record: DeviceRecord
    network_scope: NetworkScope
    ip_address: str
    mac_address: str
    observation_timestamp: datetime
    cryptographic_freshness: datetime
    authorization_context: dict[str, Any]


@dataclass(frozen=True)
class ActionRequest:
    """A request to execute a specific capability on a target."""

    action_id: uuid.UUID
    capability: ActionCapability
    identity_id: uuid.UUID
    requesting_actor: str
    authorization_context: dict[str, Any]
    target_snapshot: TargetSnapshot
    requested_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))
    operator_id: Optional[str] = None


@dataclass(frozen=True)
class ActionExecution:
    """The tracked state of an enforcement action."""

    action_id: uuid.UUID
    request: ActionRequest
    state: ExecutionState
    created_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))
    updated_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))
    completed_at: Optional[datetime] = None
    operator_id: Optional[str] = None
    rollback_of: Optional[uuid.UUID] = None
    audit_references: list[str] = field(default_factory=list)
    enforcement_plan: Optional["EnforcementPlan"] = None

    def transition_to(self, new_state: ExecutionState) -> "ActionExecution":
        """Safely transition to a new state if legal."""
        valid_transitions = {
            ExecutionState.REQUESTED: {
                ExecutionState.AUTHORIZED,
                ExecutionState.DENIED,
            },
            ExecutionState.AUTHORIZED: {ExecutionState.EXECUTING},
            ExecutionState.DENIED: set(),
            ExecutionState.EXECUTING: {
                ExecutionState.SUCCEEDED,
                ExecutionState.FAILED,
                ExecutionState.RECONCILING,
            },
            ExecutionState.RECONCILING: {
                ExecutionState.SUCCEEDED,
                ExecutionState.FAILED,
            },
            ExecutionState.SUCCEEDED: {ExecutionState.ROLLBACK_REQUESTED},
            ExecutionState.FAILED: {ExecutionState.ROLLBACK_REQUESTED},
            ExecutionState.ROLLBACK_REQUESTED: {
                ExecutionState.ROLLED_BACK,
                ExecutionState.ROLLBACK_FAILED,
            },
            ExecutionState.ROLLED_BACK: set(),
            ExecutionState.ROLLBACK_FAILED: set(),
        }

        if new_state not in valid_transitions[self.state]:
            raise ValueError(
                f"Illegal state transition from {self.state.name} to {new_state.name}"
            )

        from dataclasses import replace

        return replace(
            self,
            state=new_state,
            updated_at=datetime.now(timezone.utc),
            completed_at=datetime.now(timezone.utc)
            if new_state
            in {
                ExecutionState.SUCCEEDED,
                ExecutionState.FAILED,
                ExecutionState.DENIED,
                ExecutionState.ROLLED_BACK,
                ExecutionState.ROLLBACK_FAILED,
            }
            else self.completed_at,
        )


@dataclass(frozen=True)
class EnforcementBinding:
    """
    Explicitly binds a firewall state to an identity and scope to prevent
    stale-IP inheritance.
    """

    identity_id: uuid.UUID
    scope_id: str
    ip_address: str
    mac_address: str


@dataclass(frozen=True)
class EnforcementOwnership:
    """Currently active ownership of the managed firewall target."""

    identity_id: uuid.UUID
    scope_id: str
    ip_address: str
    mac_address: str
    enforcement_binding_id: str
    state: NormalizedState
    created_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))


class EnforcementStateRepository(Protocol):
    """Protocol for authoritative EnforcementOwnership state."""

    def acquire_target_lock(self, scope_id: str, ip_address: str) -> bool:
        """Acquire a durable transactional serialization lock for the target."""
        ...

    def release_target_lock(self, scope_id: str, ip_address: str) -> None:
        """Release the target serialization lock."""
        ...

    def save_ownership(self, ownership: EnforcementOwnership) -> None:
        """
        Persist or update active ownership, enforcing uniqueness on
        (scope_id, ip_address).
        """
        ...

    def get_ownership(
        self, scope_id: str, ip_address: str
    ) -> Optional[EnforcementOwnership]:
        """Retrieve authoritative ownership for a target."""
        ...

    def remove_ownership(self, scope_id: str, ip_address: str) -> None:
        """Remove active ownership."""
        ...


@dataclass(frozen=True)
class QuarantinePolicy:
    """Logical representation of a quarantine permit/deny policy."""

    permit_dns: bool = True
    permit_dhcp: bool = True
    permit_gateway: bool = True
    permit_verifier_ips: list[str] = field(default_factory=list)
    gateway_ip: Optional[str] = None


@dataclass(frozen=True)
class EnforcementPlan:
    """A structurally validated execution plan ready for translation by the adapter."""

    action_id: uuid.UUID
    capability: ActionCapability
    enforcement_bindings: list[EnforcementBinding]
    quarantine_policy: Optional[QuarantinePolicy] = None


class ActionRepository(Protocol):
    """Protocol for persisting ActionExecution and tracking state."""

    def save_execution(self, execution: ActionExecution) -> None:
        """Persist or update an ActionExecution record."""
        ...

    def get_execution(self, action_id: uuid.UUID) -> Optional[ActionExecution]:
        """Retrieve an execution by action_id."""
        ...

    def get_executions_by_identity(
        self, identity_id: uuid.UUID
    ) -> list[ActionExecution]:
        """Retrieve executions associated with a given identity."""
        ...

    def get_active_executions(self) -> list[ActionExecution]:
        """Retrieve all executions currently in EXECUTING or RECONCILING state."""
        ...


class EnforcementAdapter(Protocol):
    """Protocol for the underlying OS/network enforcement mechanism."""

    def apply(self, plan: EnforcementPlan) -> None:
        """Apply an enforcement plan, throwing an exception on failure."""
        ...

    def release(self, plan: EnforcementPlan) -> None:
        """Revert the state changes applied by a plan."""
        ...

    def inspect(self) -> dict[str, NormalizedState]:
        """
        Inspect actual OS state and return normalized managed target bindings.
        Keys are usually a composite string of the TargetBinding elements.
        """
        ...
