import uuid
from datetime import datetime, timezone
from ipaddress import IPv4Address
from typing import Any
from unittest.mock import MagicMock

import pytest

from nexa.actions.executor import ActionExecutor, ExecutorException
from nexa.domain.actions import (
    ActionCapability,
    ActionExecution,
    ActionRequest,
    EnforcementMode,
    EnforcementOwnership,
    ExecutionState,
    NormalizedState,
    TargetSnapshot,
)
from nexa.domain.correlation import PresenceState
from nexa.domain.device import DeviceRecord
from nexa.domain.trust import TrustedDeviceIdentity, TrustState


@pytest.fixture
def mock_repo() -> Any:
    repo = MagicMock()
    repo.get_execution.return_value = None
    repo.get_active_executions.return_value = []
    return repo


@pytest.fixture
def mock_adapter() -> Any:
    adapter = MagicMock()
    adapter.inspect.return_value = {}
    return adapter


@pytest.fixture
def mock_circuit_breaker() -> Any:
    cb = MagicMock()
    cb.is_paused.return_value = False
    return cb


@pytest.fixture
def mock_trust_manager() -> Any:
    tm = MagicMock()
    tm.trust_repo = MagicMock()
    identity = TrustedDeviceIdentity(
        identity_id=uuid.uuid4(),
        state=TrustState.TRUSTED,
    )
    tm.trust_repo.get_identity.return_value = identity
    return tm


@pytest.fixture
def mock_enforcement_state_repo() -> Any:
    repo = MagicMock()
    repo.acquire_target_lock.return_value = True
    repo.get_ownership.return_value = None
    return repo


@pytest.fixture
def mock_event_aggregator() -> Any:
    return MagicMock()


@pytest.fixture
def executor(
    mock_repo: Any,
    mock_enforcement_state_repo: Any,
    mock_adapter: Any,
    mock_circuit_breaker: Any,
    mock_trust_manager: Any,
    mock_event_aggregator: Any,
) -> Any:
    return ActionExecutor(
        repository=mock_repo,
        enforcement_state_repo=mock_enforcement_state_repo,
        adapter=mock_adapter,
        circuit_breaker=mock_circuit_breaker,
        trust_manager=mock_trust_manager,
        event_aggregator=mock_event_aggregator,
        enforcement_mode=EnforcementMode.AUDIT_ONLY,
    )


@pytest.fixture
def sample_request() -> Any:
    identity_id = uuid.uuid4()
    snapshot = TargetSnapshot(
        action_id=uuid.uuid4(),
        trusted_identity=TrustedDeviceIdentity(
            identity_id=identity_id,
            state=TrustState.TRUSTED,
        ),
        device_record=DeviceRecord(
            device_id=uuid.uuid4(),
            network_scope=MagicMock(network_address="192.168.1.0", name="GUEST"),
            mac_addresses=frozenset(["00:11:22:33:44:55"]),
            ipv4_addresses=frozenset([IPv4Address("192.168.1.100")]),
            first_observed_at=datetime.now(timezone.utc),
            last_observed_at=datetime.now(timezone.utc),
            presence_state=PresenceState.PRESENT,
        ),
        network_scope=MagicMock(network_address="192.168.1.0", name="GUEST"),
        ip_address="192.168.1.100",
        mac_address="00:11:22:33:44:55",
        observation_timestamp=datetime.now(timezone.utc),
        cryptographic_freshness=datetime.now(timezone.utc),
        authorization_context={},
    )

    return ActionRequest(
        action_id=snapshot.action_id,
        capability=ActionCapability.QUARANTINE_DEVICE,
        identity_id=identity_id,
        requesting_actor="system",
        authorization_context={},
        target_snapshot=snapshot,
    )


def test_execute_action_audit_only(
    executor: Any, sample_request: Any, mock_adapter: Any
) -> None:
    execution = executor.execute_action(sample_request)

    assert execution.state == ExecutionState.SUCCEEDED
    assert "execution_mode: AUDIT_ONLY" in execution.audit_references
    mock_adapter.apply.assert_not_called()


def test_execute_action_enforcement_mode(
    executor: Any, sample_request: Any, mock_adapter: Any
) -> None:
    executor.enforcement_mode = EnforcementMode.ENFORCEMENT_ENABLED

    execution = executor.execute_action(sample_request)

    assert execution.state == ExecutionState.SUCCEEDED
    mock_adapter.apply.assert_called_once()


def test_circuit_breaker_halts_execution(
    executor: Any, sample_request: Any, mock_circuit_breaker: Any
) -> None:
    mock_circuit_breaker.is_paused.return_value = True

    with pytest.raises(ExecutorException, match="Enforcement is paused"):
        executor.execute_action(sample_request)


def test_execute_action_idempotency_succeeded(
    executor: Any, sample_request: Any, mock_repo: Any, mock_adapter: Any
) -> None:
    existing = ActionExecution(
        action_id=sample_request.action_id,
        request=sample_request,
        state=ExecutionState.SUCCEEDED,
    )
    mock_repo.get_execution.return_value = existing

    execution = executor.execute_action(sample_request)
    assert execution.state == ExecutionState.SUCCEEDED
    # Should not re-run
    mock_adapter.apply.assert_not_called()


def test_execute_action_idempotency_target_bound(
    executor: Any, sample_request: Any, mock_repo: Any, mock_adapter: Any
) -> None:
    executor.enforcement_mode = EnforcementMode.ENFORCEMENT_ENABLED

    # Simulate that the adapter inspect says the device is already quarantined
    identity_id = sample_request.identity_id
    scope_id = sample_request.target_snapshot.network_scope.interface_name
    ip = sample_request.target_snapshot.ip_address
    mac = sample_request.target_snapshot.mac_address
    key = f"{identity_id}:{scope_id}:{ip}:{mac}"

    mock_enforcement_state_repo = executor.enforcement_state_repo
    mock_enforcement_state_repo.get_ownership.return_value = EnforcementOwnership(
        identity_id=identity_id,
        scope_id=scope_id,
        ip_address=ip,
        mac_address=mac,
        enforcement_binding_id=key,
        state=NormalizedState.PRESENT,
    )

    execution = executor.execute_action(sample_request)
    assert execution.state == ExecutionState.SUCCEEDED
    # The adapter should apply idempotently
    mock_adapter.apply.assert_called_once()


def test_revocation_race_aborts_action(
    executor: Any, sample_request: Any, mock_trust_manager: Any, mock_adapter: Any
) -> None:
    executor.enforcement_mode = EnforcementMode.ENFORCEMENT_ENABLED

    # Simulate identity revoked just before applying
    identity = TrustedDeviceIdentity(
        identity_id=sample_request.identity_id,
        state=TrustState.REVOKED,
    )
    mock_trust_manager.trust_repo.get_identity.return_value = identity

    execution = executor.execute_action(sample_request)
    assert execution.state == ExecutionState.FAILED
    mock_adapter.apply.assert_not_called()


def test_rollback_dispatches_event_on_failure(
    executor: Any,
    sample_request: Any,
    mock_repo: Any,
    mock_adapter: Any,
    mock_event_aggregator: Any,
) -> None:
    executor.enforcement_mode = EnforcementMode.ENFORCEMENT_ENABLED

    existing = ActionExecution(
        action_id=sample_request.action_id,
        request=sample_request,
        state=ExecutionState.SUCCEEDED,
    )
    mock_repo.get_execution.return_value = existing

    # Mock ownership to pass validation
    from nexa.domain.actions import EnforcementOwnership, NormalizedState

    mock_enforcement_state_repo = executor.enforcement_state_repo
    mock_enforcement_state_repo.get_ownership.return_value = EnforcementOwnership(
        identity_id=sample_request.identity_id,
        scope_id=sample_request.target_snapshot.network_scope.interface_name,
        ip_address=sample_request.target_snapshot.ip_address,
        mac_address=sample_request.target_snapshot.mac_address,
        enforcement_binding_id=f"{sample_request.identity_id}:{sample_request.target_snapshot.network_scope.interface_name}:{sample_request.target_snapshot.ip_address}:{sample_request.target_snapshot.mac_address}",
        state=NormalizedState.PRESENT,
    )

    mock_adapter.release.side_effect = Exception("Adapter offline")

    execution = executor.rollback_action(sample_request.action_id)
    assert execution.state == ExecutionState.ROLLBACK_FAILED

    # Should dispatch a Phase 3 SecurityEvent
    mock_event_aggregator.dispatch.assert_called_once()
    event = mock_event_aggregator.dispatch.call_args[0][0]
    assert "Phase 4 Enforcement Rollback Failed" in event.context["description"]
    assert "Adapter offline" in event.context["description"]


# Crash Reconciliation Tests
def test_reconcile_intent_persisted_mutation_absent(
    executor: Any,
    mock_enforcement_state_repo: Any,
    mock_adapter: Any,
    sample_request: Any,
) -> None:
    # A. intent persisted, mutation absent
    execution = ActionExecution(
        action_id=sample_request.action_id,
        request=sample_request,
        state=ExecutionState.EXECUTING,
    )

    db = {execution.action_id: execution}

    def mock_save(ex: Any) -> None:
        db[ex.action_id] = ex

    def mock_get(aid: Any) -> Any:
        return db.get(aid)

    executor.repository.save_execution.side_effect = mock_save
    executor.repository.get_execution.side_effect = mock_get
    executor.repository.get_active_executions.return_value = [execution]

    mock_adapter.inspect.return_value = {}  # No state
    mock_enforcement_state_repo.get_ownership.return_value = None

    executor.reconcile_crashes()

    saved = executor.repository.get_execution(execution.action_id)
    assert saved.state == ExecutionState.FAILED


def test_reconcile_mutation_occurred_persistence_missing(
    executor: Any,
    mock_enforcement_state_repo: Any,
    mock_adapter: Any,
    sample_request: Any,
) -> None:
    # B. mutation occurred, persistence missing
    execution = ActionExecution(
        action_id=sample_request.action_id,
        request=sample_request,
        state=ExecutionState.EXECUTING,
    )

    db = {execution.action_id: execution}

    def mock_save(ex: Any) -> None:
        db[ex.action_id] = ex

    def mock_get(aid: Any) -> Any:
        return db.get(aid)

    executor.repository.save_execution.side_effect = mock_save
    executor.repository.get_execution.side_effect = mock_get
    executor.repository.get_active_executions.return_value = [execution]

    binding_id = (
        f"{sample_request.identity_id}:"
        f"{sample_request.target_snapshot.network_scope.interface_name}:"
        f"{sample_request.target_snapshot.ip_address}:"
        f"{sample_request.target_snapshot.mac_address}"
    )
    mock_adapter.inspect.return_value = {binding_id: NormalizedState.PRESENT}
    mock_enforcement_state_repo.get_ownership.return_value = None

    executor.reconcile_crashes()

    saved = executor.repository.get_execution(execution.action_id)
    assert saved.state == ExecutionState.SUCCEEDED
    mock_enforcement_state_repo.save_ownership.assert_called_once()


def test_reconcile_malformed_ambiguous_state(
    executor: Any,
    mock_enforcement_state_repo: Any,
    mock_adapter: Any,
    sample_request: Any,
) -> None:
    # D. malformed/ambiguous state
    execution = ActionExecution(
        action_id=sample_request.action_id,
        request=sample_request,
        state=ExecutionState.EXECUTING,
    )

    db = {execution.action_id: execution}

    def mock_save(ex: Any) -> None:
        db[ex.action_id] = ex

    def mock_get(aid: Any) -> Any:
        return db.get(aid)

    executor.repository.save_execution.side_effect = mock_save
    executor.repository.get_execution.side_effect = mock_get
    executor.repository.get_active_executions.return_value = [execution]

    binding_id = (
        f"{sample_request.identity_id}:"
        f"{sample_request.target_snapshot.network_scope.interface_name}:"
        f"{sample_request.target_snapshot.ip_address}:"
        f"{sample_request.target_snapshot.mac_address}"
    )
    mock_adapter.inspect.return_value = {binding_id: NormalizedState.PRESENT}

    from nexa.domain.actions import EnforcementOwnership

    mock_enforcement_state_repo.get_ownership.return_value = EnforcementOwnership(
        identity_id=sample_request.identity_id,
        scope_id=sample_request.target_snapshot.network_scope.interface_name,
        ip_address=sample_request.target_snapshot.ip_address,
        mac_address=sample_request.target_snapshot.mac_address,
        enforcement_binding_id="different_binding_id",  # Mismatch
        state=NormalizedState.PRESENT,
    )

    executor.reconcile_crashes()

    saved = executor.repository.get_execution(execution.action_id)
    assert saved.state == ExecutionState.FAILED
