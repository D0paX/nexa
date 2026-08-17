"""
Integration tests for Linux network discovery.

These tests execute the actual iproute2 commands against the host system.
They are disabled by default to prevent CI failures in non-Linux or
unpredictable network environments.
"""

import os
import sys
from ipaddress import IPv4Address, IPv4Network

import pytest

from nexa.domain.network import NetworkContext
from nexa.network.discovery import LinuxNetworkEnvironment

# Gate the integration test behind an explicit environment variable
# and ensure it's actually running on Linux.
RUN_INTEGRATION = os.environ.get("NEXA_RUN_NETWORK_INTEGRATION") == "1"
IS_LINUX = sys.platform.startswith("linux")

pytestmark = pytest.mark.skipif(
    not (RUN_INTEGRATION and IS_LINUX),
    reason=(
        "Linux network integration tests require "
        "NEXA_RUN_NETWORK_INTEGRATION=1 and a Linux host."
    ),
)


def test_real_linux_network_discovery() -> None:
    """
    Test that the actual Linux host has a discoverable network environment.
    This will fail if the host lacks `iproute2` or has no active IPv4 network.
    """
    adapter = LinuxNetworkEnvironment(timeout=5.0)

    # Execute the actual system discovery
    context = adapter.discover()

    # Validate that we got a real, logically sound domain model back
    assert isinstance(context, NetworkContext)

    assert isinstance(context.interface_name, str)
    assert len(context.interface_name) > 0
    assert context.interface_name != "lo"  # Should not select loopback

    assert isinstance(context.ipv4_address, IPv4Address)
    assert not context.ipv4_address.is_loopback

    assert isinstance(context.network, IPv4Network)
    assert context.ipv4_address in context.network

    if context.gateway is not None:
        assert isinstance(context.gateway, IPv4Address)
        assert context.gateway in context.network
