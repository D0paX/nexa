from datetime import datetime, timezone
from ipaddress import IPv4Address, IPv4Network
from typing import Any
from unittest.mock import Mock
from uuid import uuid4

import pytest

from nexa.domain.correlation import PresenceState
from nexa.domain.device import ScanContext
from nexa.domain.lifecycle import LifecycleEventType, ScanTransactionEnvelope
from nexa.domain.network import NetworkContext
from nexa.domain.observation import DeviceObservation
from nexa.domain.scope import NetworkScope
from nexa.lifecycle.engine import LifecycleEngine, PersistenceFailure


@pytest.fixture
def mock_repo() -> Any:
    repo = Mock()
    repo.get_records_by_scope.return_value = []
    return repo


@pytest.fixture
def test_scope() -> Any:
    network = IPv4Network("192.168.1.0/24")
    ctx = NetworkContext(
        interface_name="eth0",
        network=network,
        ipv4_address=IPv4Address(network.network_address + 2),
        gateway=IPv4Address(network.network_address + 1),
    )
    return NetworkScope.from_context(ctx)


@pytest.fixture
def scan_context(test_scope: Any) -> Any:
    return ScanContext(
        scan_id=uuid4(), started_at=datetime.now(timezone.utc), network_scope=test_scope
    )


def test_first_seen_event(mock_repo: Any, scan_context: Any) -> None:
    engine = LifecycleEngine(mock_repo)

    obs = DeviceObservation(
        mac_address="aa:bb:cc:dd:ee:ff",
        ipv4_address=IPv4Address("192.168.1.50"),
        observed_at=datetime.now(timezone.utc),
        interface_name="eth0",
    )

    records = engine.process_scan(scan_context, [obs])
    assert len(records) == 1

    # Check that repo.save_scan_transaction was called
    mock_repo.save_scan_transaction.assert_called_once()
    envelope: ScanTransactionEnvelope = mock_repo.save_scan_transaction.call_args[0][0]

    assert len(envelope.records) == 1
    assert envelope.records[0].presence_state == PresenceState.PRESENT

    # We should have FIRST_SEEN and BECAME_PRESENT
    event_types = {e.event_type for e in envelope.events}
    assert LifecycleEventType.FIRST_SEEN in event_types
    assert LifecycleEventType.BECAME_PRESENT in event_types


def test_became_unseen_event(mock_repo: Any, scan_context: Any) -> None:
    engine = LifecycleEngine(mock_repo)

    obs = DeviceObservation(
        mac_address="aa:bb:cc:dd:ee:ff",
        ipv4_address=IPv4Address("192.168.1.50"),
        observed_at=datetime.now(timezone.utc),
        interface_name="eth0",
    )

    # Pass 1: Device is present
    engine.process_scan(scan_context, [obs])

    # Pass 2: Device is missing from scan
    mock_repo.save_scan_transaction.reset_mock()
    records_pass2 = engine.process_scan(scan_context, [])

    assert len(records_pass2) == 1
    assert records_pass2[0].presence_state == PresenceState.UNSEEN

    mock_repo.save_scan_transaction.assert_called_once()
    envelope = mock_repo.save_scan_transaction.call_args[0][0]
    assert len(envelope.events) == 1
    assert envelope.events[0].event_type == LifecycleEventType.BECAME_UNSEEN


def test_degraded_persistence_queue(mock_repo: Any, scan_context: Any) -> None:
    engine = LifecycleEngine(mock_repo, max_queue_size=2)

    obs = DeviceObservation(
        mac_address="aa:bb:cc:dd:ee:ff",
        ipv4_address=IPv4Address("192.168.1.50"),
        observed_at=datetime.now(timezone.utc),
        interface_name="eth0",
    )

    # Make the repo fail
    mock_repo.save_scan_transaction.side_effect = Exception("Disk full")

    # Process scan 1
    records = engine.process_scan(scan_context, [obs])
    assert len(records) == 1
    assert engine.degraded is True
    assert len(engine._pending_queue) == 1

    # Process scan 2
    engine.process_scan(scan_context, [])
    assert engine.degraded is True
    assert len(engine._pending_queue) == 2

    # Process scan 3 -> Should exceed bound
    with pytest.raises(PersistenceFailure, match="Maximum pending"):
        engine.process_scan(scan_context, [])


def test_degraded_persistence_recovery(mock_repo: Any, scan_context: Any) -> None:
    engine = LifecycleEngine(mock_repo, max_queue_size=5)

    obs = DeviceObservation(
        mac_address="aa:bb:cc:dd:ee:ff",
        ipv4_address=IPv4Address("192.168.1.50"),
        observed_at=datetime.now(timezone.utc),
        interface_name="eth0",
    )

    # Fail first
    mock_repo.save_scan_transaction.side_effect = Exception("Disk full")
    engine.process_scan(scan_context, [obs])

    assert engine.degraded is True
    assert len(engine._pending_queue) == 1

    # Recover
    mock_repo.save_scan_transaction.side_effect = None
    engine.process_scan(scan_context, [])

    assert engine.degraded is False
    assert len(engine._pending_queue) == 0
    # The repo should have been called twice successfully (once for queue retry,
    # once for new envelope)
    # Wait, save_scan_transaction is called 1 time during the failed process_scan,
    # then during the second process_scan, it tries to drain the queue (1 call)
    # and then saves the new envelope (1 call).
    assert mock_repo.save_scan_transaction.call_count == 3
