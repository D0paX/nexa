import uuid
from typing import Any
from unittest.mock import MagicMock

import pytest

from nexa.actions.executor import ActionExecutor
from nexa.domain.actions import (
    ActionCapability,
    ActionRequest,
    EnforcementMode,
    EnforcementOwnership,
    ExecutionState,
    NormalizedState,
)
from nexa.domain.trust import TrustState


@pytest.fixture
def mock_repo() -> Any:
    repo = MagicMock()
    repo.get_execution.return_value = None
    return repo


@pytest.fixture
def mock_enforcement_state_repo() -> Any:
    repo = MagicMock()
    repo.acquire_target_lock.return_value = True
    repo.get_ownership.return_value = None
    return repo


@pytest.fixture
def mock_adapter() -> Any:
    adapter = MagicMock()
    adapter.inspect.return_value = {}
    return adapter


@pytest.fixture
def executor(
    mock_repo: Any, mock_enforcement_state_repo: Any, mock_adapter: Any
) -> Any:
    circuit_breaker = MagicMock()
    circuit_breaker.is_paused.return_value = False
    trust_manager = MagicMock()
    trust_manager.trust_repo.get_identity.return_value = MagicMock(
        state=TrustState.TRUSTED
    )

    return ActionExecutor(
        repository=mock_repo,
        enforcement_state_repo=mock_enforcement_state_repo,
        adapter=mock_adapter,
        circuit_breaker=circuit_breaker,
        trust_manager=trust_manager,
        event_aggregator=MagicMock(),
        enforcement_mode=EnforcementMode.ENFORCEMENT_ENABLED,
    )


def test_stale_ip_release_is_rejected(
    executor: Any, mock_enforcement_state_repo: Any, mock_adapter: Any
) -> None:
    """
    Device A -> IP X -> Quarantine
    A leaves.
    Device B -> IP X
    RELEASE(A) must NOT release B.
    """
    identity_a = uuid.uuid4()
    scope = "GUEST"
    ip = "192.168.1.100"
    mac_a = "AA:AA:AA:AA:AA:AA"

    # Simulate B currently owns the IP
    identity_b = uuid.uuid4()
    mac_b = "BB:BB:BB:BB:BB:BB"
    binding_id_b = f"{identity_b}:{scope}:{ip}:{mac_b}"

    mock_enforcement_state_repo.get_ownership.return_value = EnforcementOwnership(
        identity_id=identity_b,
        scope_id=scope,
        ip_address=ip,
        mac_address=mac_b,
        enforcement_binding_id=binding_id_b,
        state=NormalizedState.PRESENT,
    )

    # Create request to release A
    request = MagicMock(spec=ActionRequest)
    request.action_id = uuid.uuid4()
    request.capability = ActionCapability.RELEASE_QUARANTINE
    request.identity_id = identity_a
    request.operator_id = None

    snapshot = MagicMock()
    snapshot.network_scope.interface_name = scope
    snapshot.ip_address = ip
    snapshot.mac_address = mac_a
    request.target_snapshot = snapshot

    # Execution should fail closed and not call release
    execution = executor.execute_action(request)

    assert execution.state == ExecutionState.FAILED
    mock_adapter.release.assert_not_called()
    mock_enforcement_state_repo.remove_ownership.assert_not_called()


def test_cross_device_isolation(
    executor: Any, mock_enforcement_state_repo: Any, mock_adapter: Any
) -> None:
    """
    A -> IP X -> Quarantine
    B -> IP Y -> Quarantine
    RELEASE(A) should only release A.
    """
    identity_a = uuid.uuid4()
    scope = "GUEST"
    ip_a = "192.168.1.100"
    mac_a = "AA:AA:AA:AA:AA:AA"
    binding_id_a = f"{identity_a}:{scope}:{ip_a}:{mac_a}"

    # Simulate A currently owns IP X
    mock_enforcement_state_repo.get_ownership.return_value = EnforcementOwnership(
        identity_id=identity_a,
        scope_id=scope,
        ip_address=ip_a,
        mac_address=mac_a,
        enforcement_binding_id=binding_id_a,
        state=NormalizedState.PRESENT,
    )

    request = MagicMock(spec=ActionRequest)
    request.action_id = uuid.uuid4()
    request.capability = ActionCapability.RELEASE_QUARANTINE
    request.identity_id = identity_a
    request.operator_id = None

    snapshot = MagicMock()
    snapshot.network_scope.interface_name = scope
    snapshot.ip_address = ip_a
    snapshot.mac_address = mac_a
    request.target_snapshot = snapshot

    execution = executor.execute_action(request)

    assert execution.state == ExecutionState.SUCCEEDED
    mock_adapter.release.assert_called_once()
    mock_enforcement_state_repo.remove_ownership.assert_called_once_with(scope, ip_a)


def test_duplicate_ownership_claim_rejected(
    executor: Any, mock_enforcement_state_repo: Any, mock_adapter: Any
) -> None:
    """
    If Action A and Action B concurrently try to claim the same target,
    but A has the ownership, B must fail.
    """
    identity_a = uuid.uuid4()
    scope = "GUEST"
    ip = "192.168.1.100"
    mac_a = "AA:AA:AA:AA:AA:AA"
    binding_id_a = f"{identity_a}:{scope}:{ip}:{mac_a}"

    # Simulate A already owns it
    mock_enforcement_state_repo.get_ownership.return_value = EnforcementOwnership(
        identity_id=identity_a,
        scope_id=scope,
        ip_address=ip,
        mac_address=mac_a,
        enforcement_binding_id=binding_id_a,
        state=NormalizedState.PRESENT,
    )

    # B tries to quarantine on same IP
    identity_b = uuid.uuid4()
    request = MagicMock(spec=ActionRequest)
    request.action_id = uuid.uuid4()
    request.capability = ActionCapability.QUARANTINE_DEVICE
    request.identity_id = identity_b
    request.operator_id = None

    snapshot = MagicMock()
    snapshot.network_scope.interface_name = scope
    snapshot.ip_address = ip
    snapshot.mac_address = "BB:BB:BB:BB:BB:BB"
    request.target_snapshot = snapshot

    execution = executor.execute_action(request)

    # Should fail due to conflict
    assert execution.state == ExecutionState.FAILED
    mock_adapter.apply.assert_not_called()
