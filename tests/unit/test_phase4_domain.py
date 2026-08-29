import uuid
from datetime import datetime, timezone
from ipaddress import IPv4Address
from unittest.mock import MagicMock

import pytest

from nexa.domain.actions import (
    ActionCapability,
    ActionExecution,
    ActionRequest,
    ExecutionState,
    TargetSnapshot,
)
from nexa.domain.correlation import PresenceState
from nexa.domain.device import DeviceRecord
from nexa.domain.trust import TrustedDeviceIdentity, TrustState


@pytest.fixture
def sample_snapshot() -> TargetSnapshot:
    identity = TrustedDeviceIdentity(
        identity_id=uuid.uuid4(),
        state=TrustState.TRUSTED,
    )
    device = DeviceRecord(
        device_id=uuid.uuid4(),
        network_scope=MagicMock(network_address="192.168.1.0", name="GUEST"),
        mac_addresses=frozenset(["00:11:22:33:44:55"]),
        ipv4_addresses=frozenset([IPv4Address("192.168.1.100")]),
        first_observed_at=datetime.now(timezone.utc),
        last_observed_at=datetime.now(timezone.utc),
        presence_state=PresenceState.PRESENT,
    )
    return TargetSnapshot(
        action_id=uuid.uuid4(),
        trusted_identity=identity,
        device_record=device,
        network_scope=MagicMock(network_address="192.168.1.0", name="GUEST"),
        ip_address="192.168.1.100",
        mac_address="00:11:22:33:44:55",
        observation_timestamp=datetime.now(timezone.utc),
        cryptographic_freshness=datetime.now(timezone.utc),
        authorization_context={},
    )


@pytest.fixture
def sample_execution(sample_snapshot: TargetSnapshot) -> ActionExecution:
    request = ActionRequest(
        action_id=sample_snapshot.action_id,
        capability=ActionCapability.QUARANTINE_DEVICE,
        identity_id=sample_snapshot.trusted_identity.identity_id,
        requesting_actor="admin",
        authorization_context={},
        target_snapshot=sample_snapshot,
    )
    return ActionExecution(
        action_id=request.action_id,
        request=request,
        state=ExecutionState.REQUESTED,
    )


def test_legal_state_transitions(sample_execution: ActionExecution) -> None:
    # REQUESTED -> AUTHORIZED -> EXECUTING -> SUCCEEDED
    # -> ROLLBACK_REQUESTED -> ROLLED_BACK
    exe = sample_execution.transition_to(ExecutionState.AUTHORIZED)
    assert exe.state == ExecutionState.AUTHORIZED

    exe = exe.transition_to(ExecutionState.EXECUTING)
    assert exe.state == ExecutionState.EXECUTING

    exe = exe.transition_to(ExecutionState.SUCCEEDED)
    assert exe.state == ExecutionState.SUCCEEDED
    assert exe.completed_at is not None

    exe = exe.transition_to(ExecutionState.ROLLBACK_REQUESTED)
    assert exe.state == ExecutionState.ROLLBACK_REQUESTED

    exe = exe.transition_to(ExecutionState.ROLLED_BACK)
    assert exe.state == ExecutionState.ROLLED_BACK


def test_illegal_state_transition(sample_execution: ActionExecution) -> None:
    # Cannot go REQUESTED -> EXECUTING
    with pytest.raises(
        ValueError, match="Illegal state transition from REQUESTED to EXECUTING"
    ):
        sample_execution.transition_to(ExecutionState.EXECUTING)

    # Cannot go SUCCEEDED -> EXECUTING
    exe = (
        sample_execution.transition_to(ExecutionState.AUTHORIZED)
        .transition_to(ExecutionState.EXECUTING)
        .transition_to(ExecutionState.SUCCEEDED)
    )
    with pytest.raises(
        ValueError, match="Illegal state transition from SUCCEEDED to EXECUTING"
    ):
        exe.transition_to(ExecutionState.EXECUTING)
