import uuid
from datetime import datetime, timedelta, timezone
from ipaddress import IPv4Address
from typing import Any
from unittest.mock import MagicMock

import pytest

from nexa.actions.snapshot import SnapshotException, build_target_snapshot
from nexa.domain.correlation import PresenceState
from nexa.domain.device import DeviceRecord
from nexa.domain.trust import TrustedDeviceIdentity, TrustState


@pytest.fixture
def trust_repo() -> Any:
    return MagicMock()


@pytest.fixture
def device_repo() -> Any:
    return MagicMock()


@pytest.fixture
def setup_mocks(trust_repo: Any, device_repo: Any) -> Any:
    identity_id = uuid.uuid4()
    device_id = uuid.uuid4()

    identity = TrustedDeviceIdentity(
        identity_id=identity_id,
        state=TrustState.TRUSTED,
    )
    trust_repo.get_identity.return_value = identity
    trust_repo.get_device_ids_for_identity.return_value = [str(device_id)]

    device = DeviceRecord(
        device_id=device_id,
        network_scope=MagicMock(network_address="192.168.1.0", name="GUEST"),
        mac_addresses=frozenset(["00:11:22:33:44:55"]),
        ipv4_addresses=frozenset([IPv4Address("192.168.1.100")]),
        first_observed_at=datetime.now(timezone.utc),
        last_observed_at=datetime.now(timezone.utc),
        presence_state=PresenceState.PRESENT,
    )
    device_repo.get_record_by_id.return_value = device

    return identity_id, device_id, identity, device


def test_build_target_snapshot_success(
    trust_repo: Any, device_repo: Any, setup_mocks: Any
) -> None:
    identity_id, device_id, _, _ = setup_mocks

    snapshot = build_target_snapshot(
        action_id=uuid.uuid4(),
        identity_id=identity_id,
        trust_repo=trust_repo,
        device_repo=device_repo,
        authorization_context={},
    )

    assert snapshot.ip_address == "192.168.1.100"
    assert snapshot.mac_address == "00:11:22:33:44:55"
    assert snapshot.network_scope.network_address == "192.168.1.0"


def test_snapshot_fails_on_zero_device_matches(
    trust_repo: Any, device_repo: Any, setup_mocks: Any
) -> None:
    identity_id, _, _, _ = setup_mocks
    trust_repo.get_device_ids_for_identity.return_value = []

    with pytest.raises(SnapshotException, match="No devices linked to identity"):
        build_target_snapshot(uuid.uuid4(), identity_id, trust_repo, device_repo, {})


def test_snapshot_fails_on_multiple_plausible_matches(
    trust_repo: Any, device_repo: Any, setup_mocks: Any
) -> None:
    identity_id, device_id, _, device = setup_mocks
    device_id_2 = uuid.uuid4()
    trust_repo.get_device_ids_for_identity.return_value = [
        str(device_id),
        str(device_id_2),
    ]

    # Both are plausible
    device_repo.get_record_by_id.side_effect = lambda did: device

    with pytest.raises(SnapshotException, match="Multiple plausible target records"):
        build_target_snapshot(uuid.uuid4(), identity_id, trust_repo, device_repo, {})


def test_snapshot_fails_on_stale_observation(
    trust_repo: Any, device_repo: Any, setup_mocks: Any
) -> None:
    identity_id, device_id, _, device = setup_mocks

    # Set observation to 1 hour ago
    import dataclasses

    stale_device = dataclasses.replace(
        device, last_observed_at=datetime.now(timezone.utc) - timedelta(hours=1)
    )
    device_repo.get_record_by_id.return_value = stale_device

    with pytest.raises(
        SnapshotException, match="0 matching valid/fresh device records found."
    ):
        build_target_snapshot(uuid.uuid4(), identity_id, trust_repo, device_repo, {})


def test_snapshot_fails_on_expired_freshness(
    trust_repo: Any, device_repo: Any, setup_mocks: Any
) -> None:
    identity_id, device_id, identity, _ = setup_mocks

    # Set freshness to 1 hour ago
    import dataclasses

    stale_identity = dataclasses.replace(
        identity, updated_at=datetime.now(timezone.utc) - timedelta(hours=1)
    )
    trust_repo.get_identity.return_value = stale_identity

    with pytest.raises(SnapshotException, match="cryptographic freshness expired."):
        build_target_snapshot(uuid.uuid4(), identity_id, trust_repo, device_repo, {})


def test_snapshot_fails_on_multiple_ips(
    trust_repo: Any, device_repo: Any, setup_mocks: Any
) -> None:
    identity_id, device_id, _, device = setup_mocks

    # Device has multiple IPs
    import dataclasses

    ambiguous_device = dataclasses.replace(
        device,
        ipv4_addresses=frozenset(
            [IPv4Address("192.168.1.100"), IPv4Address("192.168.1.101")]
        ),
    )
    device_repo.get_record_by_id.return_value = ambiguous_device

    with pytest.raises(
        SnapshotException, match="Multiple IPs associated with target device"
    ):
        build_target_snapshot(uuid.uuid4(), identity_id, trust_repo, device_repo, {})


def test_snapshot_fails_on_identity_not_found(
    trust_repo: Any, device_repo: Any
) -> None:
    trust_repo.get_identity.return_value = None
    with pytest.raises(SnapshotException, match="not found"):
        build_target_snapshot(uuid.uuid4(), uuid.uuid4(), trust_repo, device_repo, {})
