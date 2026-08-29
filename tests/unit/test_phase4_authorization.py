import uuid
from datetime import datetime, timedelta, timezone
from ipaddress import IPv4Address
from unittest.mock import MagicMock

import pytest

from nexa.actions.authorization import AuthorizationEngine, AuthorizationException
from nexa.domain.actions import (
    ActionCapability,
    ActionRequest,
    OperatorApprovalMode,
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
def authorization_engine() -> AuthorizationEngine:
    return AuthorizationEngine(freshness_max_age_seconds=300)


def test_authorization_passes_automatic(
    authorization_engine: AuthorizationEngine, sample_snapshot: TargetSnapshot
) -> None:
    request = ActionRequest(
        action_id=sample_snapshot.action_id,
        capability=ActionCapability.QUARANTINE_DEVICE,
        identity_id=sample_snapshot.trusted_identity.identity_id,
        requesting_actor="system",
        authorization_context={},
        target_snapshot=sample_snapshot,
    )

    assert authorization_engine.evaluate(
        request, TrustState.TRUSTED, OperatorApprovalMode.AUTOMATIC
    )


def test_authorization_fails_if_revoked(
    authorization_engine: AuthorizationEngine, sample_snapshot: TargetSnapshot
) -> None:
    request = ActionRequest(
        action_id=sample_snapshot.action_id,
        capability=ActionCapability.QUARANTINE_DEVICE,
        identity_id=sample_snapshot.trusted_identity.identity_id,
        requesting_actor="system",
        authorization_context={},
        target_snapshot=sample_snapshot,
    )

    with pytest.raises(AuthorizationException, match="Identity is revoked"):
        authorization_engine.evaluate(
            request, TrustState.REVOKED, OperatorApprovalMode.AUTOMATIC
        )


def test_authorization_fails_if_stale_target(
    authorization_engine: AuthorizationEngine, sample_snapshot: TargetSnapshot
) -> None:
    import dataclasses

    stale_snapshot = dataclasses.replace(
        sample_snapshot,
        observation_timestamp=datetime.now(timezone.utc) - timedelta(hours=1),
    )

    request = ActionRequest(
        action_id=stale_snapshot.action_id,
        capability=ActionCapability.QUARANTINE_DEVICE,
        identity_id=stale_snapshot.trusted_identity.identity_id,
        requesting_actor="system",
        authorization_context={},
        target_snapshot=stale_snapshot,
    )

    with pytest.raises(AuthorizationException, match="TargetSnapshot is stale"):
        authorization_engine.evaluate(
            request, TrustState.TRUSTED, OperatorApprovalMode.AUTOMATIC
        )


def test_authorization_requires_operator_when_mode_is_strict(
    authorization_engine: AuthorizationEngine, sample_snapshot: TargetSnapshot
) -> None:
    operator_id = "admin@local"
    timestamp_str = datetime.now(timezone.utc).isoformat()

    context = {
        "approval": {
            "action_id": str(sample_snapshot.action_id),
            "identity_id": str(sample_snapshot.trusted_identity.identity_id),
            "capability": ActionCapability.QUARANTINE_DEVICE.value,
            "operator_id": operator_id,
            "timestamp": timestamp_str,
        }
    }

    request = ActionRequest(
        action_id=sample_snapshot.action_id,
        capability=ActionCapability.QUARANTINE_DEVICE,
        identity_id=sample_snapshot.trusted_identity.identity_id,
        requesting_actor="system",
        authorization_context=context,
        target_snapshot=sample_snapshot,
        operator_id=operator_id,
    )

    # Should pass
    assert authorization_engine.evaluate(
        request, TrustState.TRUSTED, OperatorApprovalMode.OPERATOR_REQUIRED
    )

    # Should fail if approval context is missing
    request_no_approval = ActionRequest(
        action_id=sample_snapshot.action_id,
        capability=ActionCapability.QUARANTINE_DEVICE,
        identity_id=sample_snapshot.trusted_identity.identity_id,
        requesting_actor="system",
        authorization_context={},
        target_snapshot=sample_snapshot,
        operator_id=operator_id,
    )
    with pytest.raises(AuthorizationException, match="Missing operator approval block"):
        authorization_engine.evaluate(
            request_no_approval,
            TrustState.TRUSTED,
            OperatorApprovalMode.OPERATOR_REQUIRED,
        )

    # Should fail if operator_id is missing from request
    request_no_operator = ActionRequest(
        action_id=sample_snapshot.action_id,
        capability=ActionCapability.QUARANTINE_DEVICE,
        identity_id=sample_snapshot.trusted_identity.identity_id,
        requesting_actor="system",
        authorization_context=context,
        target_snapshot=sample_snapshot,
    )
    with pytest.raises(AuthorizationException, match="operator_id is missing"):
        authorization_engine.evaluate(
            request_no_operator,
            TrustState.TRUSTED,
            OperatorApprovalMode.OPERATOR_REQUIRED,
        )

    # Should fail if binding is mismatched (wrong capability)
    bad_context = {
        "approval": {
            "action_id": str(sample_snapshot.action_id),
            "identity_id": str(sample_snapshot.trusted_identity.identity_id),
            "capability": ActionCapability.RELEASE_QUARANTINE.value,
            "operator_id": operator_id,
            "timestamp": timestamp_str,
        }
    }
    request_bad_capability = ActionRequest(
        action_id=sample_snapshot.action_id,
        capability=ActionCapability.QUARANTINE_DEVICE,
        identity_id=sample_snapshot.trusted_identity.identity_id,
        requesting_actor="system",
        authorization_context=bad_context,
        target_snapshot=sample_snapshot,
        operator_id=operator_id,
    )
    with pytest.raises(
        AuthorizationException, match="Approval capability does not match request"
    ):
        authorization_engine.evaluate(
            request_bad_capability,
            TrustState.TRUSTED,
            OperatorApprovalMode.OPERATOR_REQUIRED,
        )
