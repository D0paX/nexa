"""
Unit tests for the Phase 1B NetworkScope domain model.
"""

from ipaddress import IPv4Address, IPv4Network

import pytest

from nexa.domain.network import NetworkContext
from nexa.domain.scope import InvalidNetworkScopeError, NetworkScope


def create_context(ip: str, cidr: str, gw: str | None = None) -> NetworkContext:
    """Helper to create a NetworkContext."""
    return NetworkContext(
        interface_name="eth0",
        ipv4_address=IPv4Address(ip),
        network=IPv4Network(cidr, strict=False),
        gateway=IPv4Address(gw) if gw else None,
    )


def test_valid_scope_from_context_24() -> None:
    """Test a typical /24 network."""
    ctx = create_context("192.168.1.50", "192.168.1.0/24", "192.168.1.1")
    scope = NetworkScope.from_context(ctx)

    assert scope.network_address == IPv4Address("192.168.1.0")
    assert scope.broadcast_address == IPv4Address("192.168.1.255")
    assert scope.prefix_length == 24
    assert scope.host_count == 254
    assert scope.first_usable_host == IPv4Address("192.168.1.1")
    assert scope.last_usable_host == IPv4Address("192.168.1.254")
    assert scope.gateway == IPv4Address("192.168.1.1")
    assert scope.interface_name == "eth0"


def test_valid_scope_from_context_16() -> None:
    """Test a /16 network (maximum allowed bounds)."""
    ctx = create_context("10.0.5.10", "10.0.0.0/16", None)
    scope = NetworkScope.from_context(ctx)

    assert scope.prefix_length == 16
    assert scope.host_count == 65534
    assert scope.first_usable_host == IPv4Address("10.0.0.1")
    assert scope.last_usable_host == IPv4Address("10.0.255.254")
    assert scope.gateway is None


def test_valid_scope_from_context_31() -> None:
    """Test a /31 point-to-point network."""
    # RFC 3021 specifies /31 has 2 usable hosts, no separate broadcast/network addresses
    ctx = create_context("172.16.0.0", "172.16.0.0/31", "172.16.0.1")
    scope = NetworkScope.from_context(ctx)

    assert scope.prefix_length == 31
    assert scope.host_count == 2
    assert scope.first_usable_host == IPv4Address("172.16.0.0")
    assert scope.last_usable_host == IPv4Address("172.16.0.1")


def test_valid_scope_from_context_32() -> None:
    """Test a /32 single host network."""
    ctx = create_context("10.9.8.7", "10.9.8.7/32", "10.9.8.7")
    scope = NetworkScope.from_context(ctx)

    assert scope.prefix_length == 32
    assert scope.host_count == 1
    assert scope.first_usable_host == IPv4Address("10.9.8.7")
    assert scope.last_usable_host == IPv4Address("10.9.8.7")


def test_invalid_scope_too_large() -> None:
    """Test that networks larger than /16 are rejected."""
    ctx = create_context("10.0.0.5", "10.0.0.0/8")
    with pytest.raises(InvalidNetworkScopeError, match="is too large"):
        NetworkScope.from_context(ctx)


def test_invalid_scope_zero_network() -> None:
    """Test that the 0.0.0.0 network is rejected."""
    # Although this typically wouldn't pass NetworkContext if IP is 0.0.0.0,
    # testing the Scope safety net specifically.
    with pytest.raises(
        InvalidNetworkScopeError, match="not a valid observation network"
    ):
        NetworkScope(
            network_address=IPv4Address("0.0.0.0"),
            broadcast_address=IPv4Address("0.0.0.255"),
            prefix_length=24,
            host_count=254,
            first_usable_host=IPv4Address("0.0.0.1"),
            last_usable_host=IPv4Address("0.0.0.254"),
            gateway=None,
            interface_name="eth0",
        )
