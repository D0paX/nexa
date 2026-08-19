"""
Unit tests for the Phase 1D correlation engine and domain models.
"""

import uuid
from datetime import datetime, timezone
from ipaddress import IPv4Address
from typing import Any

import pytest

from nexa.domain.correlation import (
    ConflictClassification,
    PresenceState,
)
from nexa.domain.device import DeviceRecord, ScanContext
from nexa.domain.observation import DeviceObservation
from nexa.domain.scope import NetworkScope
from nexa.network.correlator import ObservationCorrelator


@pytest.fixture
def mock_scope() -> NetworkScope:
    return NetworkScope(
        network_address=IPv4Address("192.168.1.0"),
        broadcast_address=IPv4Address("192.168.1.255"),
        prefix_length=24,
        host_count=256,
        first_usable_host=IPv4Address("192.168.1.1"),
        last_usable_host=IPv4Address("192.168.1.254"),
        gateway=IPv4Address("192.168.1.1"),
        interface_name="eth0",
    )


@pytest.fixture
def alternate_scope() -> NetworkScope:
    return NetworkScope(
        network_address=IPv4Address("10.0.0.0"),
        broadcast_address=IPv4Address("10.0.0.255"),
        prefix_length=24,
        host_count=256,
        first_usable_host=IPv4Address("10.0.0.1"),
        last_usable_host=IPv4Address("10.0.0.254"),
        gateway=IPv4Address("10.0.0.1"),
        interface_name="eth1",
    )


@pytest.fixture
def id_factory() -> Any:
    """A deterministic ID factory for testing."""

    class IDFactory:
        def __init__(self) -> None:
            self.counter = 0

        def __call__(self) -> uuid.UUID:
            self.counter += 1
            return uuid.UUID(int=self.counter)

    return IDFactory()


@pytest.fixture
def correlator(id_factory: Any) -> ObservationCorrelator:
    return ObservationCorrelator(id_factory=id_factory)


def test_single_observation(
    correlator: ObservationCorrelator, mock_scope: NetworkScope
) -> None:
    t1 = datetime(2026, 8, 18, 12, 0, 0, tzinfo=timezone.utc)
    scan_ctx = ScanContext(uuid.uuid4(), t1, mock_scope)

    obs = DeviceObservation(
        mac_address="AA:BB:CC:DD:EE:FF",
        ipv4_address=IPv4Address("192.168.1.42"),
        observed_at=t1,
        interface_name="eth0",
    )

    records = correlator.correlate(scan_ctx, [obs])
    assert len(records) == 1
    record = records[0]

    assert record.device_id == uuid.UUID(int=1)
    assert record.mac_addresses == frozenset(["aa:bb:cc:dd:ee:ff"])
    assert record.ipv4_addresses == frozenset([IPv4Address("192.168.1.42")])
    assert record.presence_state == PresenceState.PRESENT
    assert not record.conflicts


def test_repeated_observation(
    correlator: ObservationCorrelator, mock_scope: NetworkScope
) -> None:
    t1 = datetime(2026, 8, 18, 12, 0, 0, tzinfo=timezone.utc)
    scan_ctx1 = ScanContext(uuid.uuid4(), t1, mock_scope)
    obs1 = DeviceObservation(
        mac_address="AA:BB:CC:DD:EE:FF",
        ipv4_address=IPv4Address("192.168.1.42"),
        observed_at=t1,
        interface_name="eth0",
    )
    correlator.correlate(scan_ctx1, [obs1])

    t2 = datetime(2026, 8, 18, 12, 5, 0, tzinfo=timezone.utc)
    scan_ctx2 = ScanContext(uuid.uuid4(), t2, mock_scope)
    obs2 = DeviceObservation(
        mac_address="AA:BB:CC:DD:EE:FF",
        ipv4_address=IPv4Address("192.168.1.42"),
        observed_at=t2,
        interface_name="eth0",
    )
    records = correlator.correlate(scan_ctx2, [obs2])

    assert len(records) == 1
    record = records[0]
    # UUID should be stable
    assert record.device_id == uuid.UUID(int=1)
    assert record.first_observed_at == t1
    assert record.last_observed_at == t2
    assert record.presence_state == PresenceState.PRESENT


def test_same_mac_different_ip_within_scope(
    correlator: ObservationCorrelator, mock_scope: NetworkScope
) -> None:
    t1 = datetime(2026, 8, 18, 12, 0, 0, tzinfo=timezone.utc)
    scan_ctx = ScanContext(uuid.uuid4(), t1, mock_scope)

    obs1 = DeviceObservation(
        mac_address="AA:BB:CC:DD:EE:FF",
        ipv4_address=IPv4Address("192.168.1.42"),
        observed_at=t1,
        interface_name="eth0",
    )
    obs2 = DeviceObservation(
        mac_address="AA:BB:CC:DD:EE:FF",
        ipv4_address=IPv4Address("192.168.1.43"),
        observed_at=t1,
        interface_name="eth0",
    )

    records = correlator.correlate(scan_ctx, [obs1, obs2])
    assert len(records) == 1
    record = records[0]
    assert len(record.ipv4_addresses) == 2
    assert IPv4Address("192.168.1.42") in record.ipv4_addresses
    assert IPv4Address("192.168.1.43") in record.ipv4_addresses


def test_same_mac_across_scopes(
    correlator: ObservationCorrelator,
    mock_scope: NetworkScope,
    alternate_scope: NetworkScope,
) -> None:
    t1 = datetime(2026, 8, 18, 12, 0, 0, tzinfo=timezone.utc)
    ctx1 = ScanContext(uuid.uuid4(), t1, mock_scope)
    obs1 = DeviceObservation(
        mac_address="AA:BB:CC:DD:EE:FF",
        ipv4_address=IPv4Address("192.168.1.42"),
        observed_at=t1,
        interface_name="eth0",
    )
    r1 = correlator.correlate(ctx1, [obs1])

    t2 = datetime(2026, 8, 18, 12, 1, 0, tzinfo=timezone.utc)
    ctx2 = ScanContext(uuid.uuid4(), t2, alternate_scope)
    obs2 = DeviceObservation(
        mac_address="AA:BB:CC:DD:EE:FF",
        ipv4_address=IPv4Address("10.0.0.42"),
        observed_at=t2,
        interface_name="eth1",
    )
    r2 = correlator.correlate(ctx2, [obs2])

    assert len(r1) == 1
    assert len(r2) == 1
    assert r1[0].device_id != r2[0].device_id
    assert r1[0].network_scope != r2[0].network_scope


def test_different_macs_same_ip_conflict(
    correlator: ObservationCorrelator, mock_scope: NetworkScope
) -> None:
    t1 = datetime(2026, 8, 18, 12, 0, 0, tzinfo=timezone.utc)
    scan_ctx = ScanContext(uuid.uuid4(), t1, mock_scope)

    obs1 = DeviceObservation(
        mac_address="AA:BB:CC:DD:EE:FF",
        ipv4_address=IPv4Address("192.168.1.42"),
        observed_at=t1,
        interface_name="eth0",
    )
    obs2 = DeviceObservation(
        mac_address="11:22:33:44:55:66",
        ipv4_address=IPv4Address("192.168.1.42"),
        observed_at=t1,
        interface_name="eth0",
    )

    records = correlator.correlate(scan_ctx, [obs1, obs2])
    assert len(records) == 2

    for record in records:
        assert len(record.conflicts) == 1
        conflict = list(record.conflicts)[0]
        assert conflict.classification == ConflictClassification.IP_COLLISION
        assert conflict.involved_macs == frozenset(
            ["aa:bb:cc:dd:ee:ff", "11:22:33:44:55:66"]
        )


def test_unseen_transition(
    correlator: ObservationCorrelator, mock_scope: NetworkScope
) -> None:
    t1 = datetime(2026, 8, 18, 12, 0, 0, tzinfo=timezone.utc)
    ctx1 = ScanContext(uuid.uuid4(), t1, mock_scope)
    obs = DeviceObservation(
        mac_address="AA:BB:CC:DD:EE:FF",
        ipv4_address=IPv4Address("192.168.1.42"),
        observed_at=t1,
        interface_name="eth0",
    )
    records1 = correlator.correlate(ctx1, [obs])
    assert records1[0].presence_state == PresenceState.PRESENT

    t2 = datetime(2026, 8, 18, 12, 5, 0, tzinfo=timezone.utc)
    ctx2 = ScanContext(uuid.uuid4(), t2, mock_scope)
    # Empty scan results in the device becoming UNSEEN
    records2 = correlator.correlate(ctx2, [])
    assert len(records2) == 1
    assert records2[0].presence_state == PresenceState.UNSEEN


def test_order_independent_correlation(
    correlator: ObservationCorrelator, mock_scope: NetworkScope, id_factory: Any
) -> None:
    t1 = datetime(2026, 8, 18, 12, 0, 0, tzinfo=timezone.utc)
    scan_ctx = ScanContext(uuid.uuid4(), t1, mock_scope)

    obs1 = DeviceObservation(
        mac_address="BB:BB:BB:BB:BB:BB",
        ipv4_address=IPv4Address("192.168.1.50"),
        observed_at=t1,
        interface_name="eth0",
    )
    obs2 = DeviceObservation(
        mac_address="AA:AA:AA:AA:AA:AA",
        ipv4_address=IPv4Address("192.168.1.50"),
        observed_at=t1,
        interface_name="eth0",
    )

    correlator1 = ObservationCorrelator(id_factory=id_factory)
    res1 = correlator1.correlate(scan_ctx, [obs1, obs2])

    # Reset ID factory
    id_factory.counter = 0

    correlator2 = ObservationCorrelator(id_factory=id_factory)
    res2 = correlator2.correlate(scan_ctx, [obs2, obs1])

    res1.sort(key=lambda x: x.device_id)
    res2.sort(key=lambda x: x.device_id)

    assert res1 == res2


def test_invalid_observations(
    correlator: ObservationCorrelator, mock_scope: NetworkScope
) -> None:
    t1 = datetime(2026, 8, 18, 12, 0, 0, tzinfo=timezone.utc)
    scan_ctx = ScanContext(uuid.uuid4(), t1, mock_scope)

    # IP out of scope
    obs_invalid = DeviceObservation(
        mac_address="AA:BB:CC:DD:EE:FF",
        ipv4_address=IPv4Address("10.0.0.42"),
        observed_at=t1,
        interface_name="eth0",
    )
    records = correlator.correlate(scan_ctx, [obs_invalid])
    assert len(records) == 0


def test_security_invariant_no_trust_state() -> None:
    """Verify that DeviceRecord does not contain any trust state."""
    assert not hasattr(DeviceRecord, "trusted")
    assert not hasattr(DeviceRecord, "authenticated")
    assert not hasattr(DeviceRecord, "enrolled")
    assert not hasattr(DeviceRecord, "cryptographically_verified")


def test_initial_records_hydration(mock_scope: NetworkScope) -> None:
    """Verify that correlator state is correctly initialized from prior records."""
    known_id = uuid.uuid4()
    t1 = datetime(2026, 8, 18, 12, 0, 0, tzinfo=timezone.utc)

    # Create a persisted Phase 1D-compatible DeviceRecord
    record = DeviceRecord(
        device_id=known_id,
        network_scope=mock_scope,
        mac_addresses=frozenset(["aa:bb:cc:dd:ee:ff"]),
        ipv4_addresses=frozenset([IPv4Address("192.168.1.42")]),
        first_observed_at=t1,
        last_observed_at=t1,
        presence_state=PresenceState.UNSEEN,
        conflicts=frozenset(),
    )

    # Hydrate correlator (reconstructing the in-memory dictionary natively)
    correlator = ObservationCorrelator(initial_records=[record])

    # Ensure the exact same UUID and scope-bound state survives
    scan_ctx = ScanContext(uuid.uuid4(), t1, mock_scope)
    # Re-correlating with an empty list should simply return the hydrated record
    records = correlator.correlate(scan_ctx, [])

    assert len(records) == 1
    hydrated_record = records[0]
    assert hydrated_record.device_id == known_id
    assert hydrated_record.network_scope == mock_scope
    assert hydrated_record.presence_state == PresenceState.UNSEEN
    assert hydrated_record.mac_addresses == frozenset(["aa:bb:cc:dd:ee:ff"])
