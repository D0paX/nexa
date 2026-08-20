"""
Integration tests for Linux ARP discovery.

These tests execute real Scapy Layer-2 interactions on the host system.
They are disabled by default to prevent CI failures.
"""

import os
import sys

import pytest

from nexa.domain.observation import DeviceObservation
from nexa.domain.scope import NetworkScope
from nexa.network.arp.observer import ARPObserver
from nexa.network.discovery import LinuxNetworkEnvironment

RUN_INTEGRATION = os.environ.get("NEXA_RUN_NETWORK_INTEGRATION") == "1"
IS_LINUX = sys.platform.startswith("linux")

pytestmark = pytest.mark.skipif(
    not (RUN_INTEGRATION and IS_LINUX),
    reason=(
        "Linux network integration tests require "
        "NEXA_RUN_NETWORK_INTEGRATION=1 and a Linux host."
    ),
)


def test_real_linux_arp_discovery() -> None:
    """
    Test that the ARP observer can successfully interact with the local network.

    To keep the test bounded, we will explicitly scan only the gateway and the
    host's own IP (a /31 or /32 equivalent) rather than an entire /24 subnet.
    """
    # 1. Discover the real network environment to get the interface and gateway
    adapter = LinuxNetworkEnvironment(timeout=5.0)
    context = adapter.discover()

    target_ip = context.gateway if context.gateway else context.ipv4_address

    # 2. Artificially create a /32 scope targeting just the gateway/host
    scope = NetworkScope(
        network_address=target_ip,
        broadcast_address=target_ip,
        prefix_length=32,
        host_count=1,
        first_usable_host=target_ip,
        last_usable_host=target_ip,
        gateway=context.gateway,
        interface_name=context.interface_name,
    )

    observer = ARPObserver(batch_size=64, global_timeout_seconds=5.0)

    # 3. Execute observation
    try:
        observations = observer.observe(scope)
    except Exception as e:
        # We might fail due to lack of CAP_NET_RAW if not run with sudo/caps
        # For the integration test, we assume the environment is properly configured.
        pytest.fail(f"ARP observation failed unexpectedly: {e}")

    # We should have at most 1 valid observation, or 0 if it doesn't respond
    # The primary goal is that the Scapy transport executes without crashing
    for obs in observations:
        assert isinstance(obs, DeviceObservation)
        assert obs.interface_name == context.interface_name
        assert obs.ipv4_address == target_ip
