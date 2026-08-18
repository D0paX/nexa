"""
Unit tests for the ARP Observation Engine.
"""

from datetime import datetime, timezone
from ipaddress import IPv4Address, IPv4Network
from unittest.mock import Mock

import pytest

from nexa.domain.network import NetworkContext
from nexa.domain.observation import DeviceObservation, InvalidObservationError
from nexa.domain.scope import NetworkScope
from nexa.network.arp.errors import (
    GlobalTimeoutExceededError,
    NexaPrivilegeError,
)
from nexa.network.arp.observer import ARPObserver
from nexa.network.arp.targets import ARPTargetGenerator


def create_test_scope(network_str: str) -> NetworkScope:
    """Helper to create a NetworkScope."""
    net = IPv4Network(network_str)
    # Using 10.0.0.254 or similar for IP and Gateway
    ip = next(net.hosts()) if net.prefixlen < 31 else net.network_address
    ctx = NetworkContext(
        interface_name="eth0", ipv4_address=ip, network=net, gateway=ip
    )
    return NetworkScope.from_context(ctx)


def test_mac_normalization() -> None:
    """Test that DeviceObservation correctly normalizes different MAC inputs."""
    ip = IPv4Address("192.168.1.10")
    now = datetime.now(timezone.utc)

    # Standard format
    obs1 = DeviceObservation(ip, "aa:bb:cc:dd:ee:ff", now, "eth0")
    assert obs1.mac_address == "aa:bb:cc:dd:ee:ff"

    # Uppercase with hyphens
    obs2 = DeviceObservation(ip, "AA-BB-CC-DD-EE-FF", now, "eth0")
    assert obs2.mac_address == "aa:bb:cc:dd:ee:ff"

    # Cisco style dots
    obs3 = DeviceObservation(ip, "aabb.ccdd.eeff", now, "eth0")
    assert obs3.mac_address == "aa:bb:cc:dd:ee:ff"

    # Plain hex string
    obs4 = DeviceObservation(ip, "aabbccddeeff", now, "eth0")
    assert obs4.mac_address == "aa:bb:cc:dd:ee:ff"


def test_invalid_mac_normalization() -> None:
    """Test that invalid MACs raise InvalidObservationError."""
    ip = IPv4Address("192.168.1.10")
    now = datetime.now(timezone.utc)

    with pytest.raises(InvalidObservationError):
        DeviceObservation(ip, "not-a-mac", now, "eth0")

    with pytest.raises(InvalidObservationError):
        DeviceObservation(ip, "aa:bb:cc:dd:ee:ff:11", now, "eth0")


def test_arp_target_generator_24() -> None:
    """Test target generation for a /24 network."""
    scope = create_test_scope("192.168.1.0/24")
    generator = ARPTargetGenerator(scope)
    targets = list(generator.generate())

    assert len(targets) == 254
    assert targets[0] == IPv4Address("192.168.1.1")
    assert targets[-1] == IPv4Address("192.168.1.254")
    # Network and broadcast excluded
    assert IPv4Address("192.168.1.0") not in targets
    assert IPv4Address("192.168.1.255") not in targets


def test_arp_target_generator_31() -> None:
    """Test target generation for a /31 point-to-point network."""
    scope = create_test_scope("192.168.1.0/31")
    generator = ARPTargetGenerator(scope)
    targets = list(generator.generate())

    assert len(targets) == 2
    assert targets[0] == IPv4Address("192.168.1.0")
    assert targets[1] == IPv4Address("192.168.1.1")


def test_arp_target_generator_32() -> None:
    """Test target generation for a /32 host route."""
    scope = create_test_scope("192.168.1.10/32")
    generator = ARPTargetGenerator(scope)
    targets = list(generator.generate())

    assert len(targets) == 1
    assert targets[0] == IPv4Address("192.168.1.10")


def test_observer_batching_and_retry() -> None:
    """Test that the observer batches requests and retries only unanswered."""
    scope = create_test_scope("192.168.1.0/24")
    mock_transport = Mock()

    # Batch 1 (targets 1-64)
    # First attempt: returns response for .1, leaves .2 unanswered
    # Second attempt (retry for .2): returns response for .2
    # Batch 2 (targets 65-128)... etc. We just mock the calls.

    def side_effect(
        targets: list[IPv4Address], interface_name: str, timeout: float
    ) -> list[dict[str, str]]:
        responses = []
        for t in targets:
            if str(t) == "192.168.1.1":
                responses.append({"ip": "192.168.1.1", "mac": "aa:bb:cc:dd:ee:01"})
            # Let .2 fail the first time, succeed the second time
            # (when it's alone or small)
            if str(t) == "192.168.1.2" and len(targets) < 64:
                responses.append({"ip": "192.168.1.2", "mac": "aa:bb:cc:dd:ee:02"})
        return responses

    mock_transport.send_arp_requests.side_effect = side_effect

    observer = ARPObserver(
        transport=mock_transport, batch_size=64, inter_batch_delay=0.0
    )

    observations = observer.observe(scope)

    # We should have found .1 and .2
    assert len(observations) == 2

    # Verify send_arp_requests was called correctly
    # 254 hosts / 64 = 4 batches (64, 64, 64, 62)
    # Plus retries for each batch
    assert mock_transport.send_arp_requests.call_count == 8

    # Check the first call (initial batch 1)
    call1_args = mock_transport.send_arp_requests.call_args_list[0][0][0]
    assert len(call1_args) == 64
    assert call1_args[0] == IPv4Address("192.168.1.1")

    # Check the second call (retry for batch 1)
    # It should only contain the 63 unanswered targets (since .1 answered)
    call2_args = mock_transport.send_arp_requests.call_args_list[1][0][0]
    assert len(call2_args) == 63
    assert IPv4Address("192.168.1.1") not in call2_args


def test_observer_deduplication() -> None:
    """Test IP+MAC deduplication semantics."""
    scope = create_test_scope("10.0.0.0/24")
    mock_transport = Mock()

    # Same IP returns two different MACs -> Both should be kept
    # Same IP returns same MAC twice -> Should be deduplicated
    mock_transport.send_arp_requests.return_value = [
        {"ip": "10.0.0.5", "mac": "aa:bb:cc:dd:ee:11"},
        {"ip": "10.0.0.5", "mac": "aa:bb:cc:dd:ee:22"},  # Different MAC, same IP
        {"ip": "10.0.0.5", "mac": "AA:BB:CC:DD:EE:11"},  # Same MAC (case diff), same IP
    ]

    observer = ARPObserver(transport=mock_transport, inter_batch_delay=0.0)
    obs = observer.observe(scope)

    # Should result in exactly 2 observations
    assert len(obs) == 2
    macs = {o.mac_address for o in obs}
    assert "aa:bb:cc:dd:ee:11" in macs
    assert "aa:bb:cc:dd:ee:22" in macs


def test_global_timeout() -> None:
    """Test that the global timeout aborts the scan cleanly."""
    scope = create_test_scope("10.0.0.0/16")
    mock_transport = Mock()
    mock_transport.send_arp_requests.return_value = []

    # Set a very low global timeout
    observer = ARPObserver(
        transport=mock_transport,
        batch_size=64,
        global_timeout_seconds=0.1,  # 100ms
        inter_batch_delay=0.15,  # 150ms delay per batch
    )

    with pytest.raises(GlobalTimeoutExceededError):
        observer.observe(scope)


def test_privilege_error_propagation() -> None:
    """Test that NexaPrivilegeError propagates correctly from the transport."""
    scope = create_test_scope("10.0.0.0/24")
    mock_transport = Mock()
    mock_transport.send_arp_requests.side_effect = NexaPrivilegeError("No cap")

    observer = ARPObserver(transport=mock_transport)

    with pytest.raises(NexaPrivilegeError):
        observer.observe(scope)
