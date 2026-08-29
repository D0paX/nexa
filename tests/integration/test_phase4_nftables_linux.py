"""
Integration tests for native Linux nftables enforcement.
"""

import os
import subprocess
import sys
import uuid
from typing import Generator

import pytest

from nexa.actions.adapter import NftablesAdapter
from nexa.domain.actions import (
    ActionCapability,
    EnforcementBinding,
    EnforcementPlan,
    NormalizedState,
    QuarantinePolicy,
)

RUN_INTEGRATION = os.environ.get("NEXA_RUN_NETWORK_INTEGRATION") == "1"
IS_LINUX = sys.platform.startswith("linux")

pytestmark = pytest.mark.skipif(
    not (RUN_INTEGRATION and IS_LINUX),
    reason=(
        "Linux network integration tests require "
        "NEXA_RUN_NETWORK_INTEGRATION=1 and a Linux host."
    ),
)

# Test IPs for the namespace
TARGET_A_IP = "192.0.2.10"
TARGET_B_IP = "192.0.2.11"
GATEWAY_IP = "192.0.2.1"
DNS_IP = "192.0.2.53"
NS_NAME = "nexa-p4-test"


@pytest.fixture(scope="module")
def network_namespace() -> Generator[str, None, None]:
    """Sets up an isolated network namespace with test interfaces."""
    # Cleanup any existing
    subprocess.run(["ip", "netns", "del", NS_NAME], stderr=subprocess.DEVNULL)

    # Create namespace
    subprocess.run(["ip", "netns", "add", NS_NAME], check=True)

    # We use a dummy interface to act as the targets and gateway inside the namespace
    # so we can route and ping them.
    # Actually, a simpler way is to use lo, but pinging local IPs
    # usually goes through `lo`. nftables hooks on `lo` still work,
    # but `prerouting`/`postrouting` might behave differently.
    # Let's use dummy interfaces.
    try:
        subprocess.run(
            [
                "ip",
                "netns",
                "exec",
                NS_NAME,
                "ip",
                "link",
                "add",
                "dummy0",
                "type",
                "dummy",
            ],
            check=True,
        )
        subprocess.run(
            ["ip", "netns", "exec", NS_NAME, "ip", "link", "set", "dummy0", "up"],
            check=True,
        )

        # Add IPs to dummy0
        for ip in [TARGET_A_IP, TARGET_B_IP, GATEWAY_IP, DNS_IP]:
            subprocess.run(
                [
                    "ip",
                    "netns",
                    "exec",
                    NS_NAME,
                    "ip",
                    "addr",
                    "add",
                    f"{ip}/24",
                    "dev",
                    "dummy0",
                ],
                check=True,
            )

        subprocess.run(
            ["ip", "netns", "exec", NS_NAME, "ip", "link", "set", "lo", "up"],
            check=True,
        )

        # The tests will run inside this namespace using `ip netns exec nexa-p4-test`
        yield NS_NAME

    finally:
        subprocess.run(["ip", "netns", "del", NS_NAME], check=False)
        # Flush the global nexa table on host just in case, but adapter runs in ns


def _ping(ns: str, src: str, dst: str) -> bool:
    """Returns True if ping succeeds, False otherwise."""
    # -I specifies source IP
    cmd = ["ip", "netns", "exec", ns, "ping", "-c", "1", "-W", "1", "-I", src, dst]
    result = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return result.returncode == 0


def _nc_udp(ns: str, src: str, dst: str, port: int) -> bool:
    """Returns True if UDP packet can be sent without error (doesn't verify receipt)."""
    # Just an outbound check. Since ping tests ICMP block, this is extra.
    # Actually, if we use netcat to send a packet, it might still exit 0.
    return True


@pytest.fixture
def adapter(network_namespace: str) -> NftablesAdapter:
    """Returns an adapter that wraps its commands in the namespace."""

    class NamespacedAdapter(NftablesAdapter):
        def _execute_nft(self, args: list[str]) -> str:
            ns_cmd = ["ip", "netns", "exec", network_namespace, "nft"] + args
            result = subprocess.run(
                ns_cmd,
                check=True,
                shell=False,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                timeout=5.0,
            )
            return result.stdout

    # Cleanup any table inside the namespace first
    subprocess.run(
        [
            "ip",
            "netns",
            "exec",
            network_namespace,
            "nft",
            "delete",
            "table",
            "inet",
            "nexa",
        ],
        stderr=subprocess.DEVNULL,
    )
    return NamespacedAdapter()


def test_nftables_native_enforcement(
    adapter: NftablesAdapter, network_namespace: str
) -> None:
    # 1. NORMAL - verify allowed traffic
    assert _ping(network_namespace, TARGET_A_IP, GATEWAY_IP), (
        "Initial A->Gateway ping failed"
    )
    assert _ping(network_namespace, TARGET_A_IP, TARGET_B_IP), (
        "Initial A->B ping failed"
    )
    assert _ping(network_namespace, TARGET_B_IP, GATEWAY_IP), (
        "Initial B->Gateway ping failed"
    )

    # 2. QUARANTINE_DEVICE A
    plan = EnforcementPlan(
        action_id=uuid.uuid4(),
        capability=ActionCapability.QUARANTINE_DEVICE,
        enforcement_bindings=[
            EnforcementBinding(
                identity_id=uuid.uuid4(),
                scope_id="dummy0",
                ip_address=TARGET_A_IP,
                mac_address="00:11:22:33:44:55",
            )
        ],
        quarantine_policy=QuarantinePolicy(
            permit_dns=True,
            permit_dhcp=True,
            permit_gateway=True,
            permit_verifier_ips=[],
            gateway_ip=GATEWAY_IP,
        ),
    )

    adapter.apply(plan)

    # Verify inspect reports present
    state = adapter.inspect()
    assert len(state) == 1
    assert list(state.values())[0] == NormalizedState.PRESENT

    # Verify explicitly denied traffic is actually blocked (A -> B)
    # Target B is NOT the gateway, nor DNS, nor DHCP
    assert not _ping(network_namespace, TARGET_A_IP, TARGET_B_IP), (
        "A->B should be BLOCKED"
    )

    # Verify explicitly permitted traffic (A -> Gateway)
    assert _ping(network_namespace, TARGET_A_IP, GATEWAY_IP), (
        "A->Gateway should be ALLOWED"
    )

    # 3. NON-QUARANTINED TRAFFIC TEST (B -> Gateway, B -> A)
    # B is unrestricted
    assert _ping(network_namespace, TARGET_B_IP, GATEWAY_IP), (
        "B->Gateway should be unaffected"
    )
    # Note: B pinging A might fail if A's quarantine blocks incoming ICMP!
    # A is quarantined. Traffic to A is `daddr TARGET_A`. `quarantine_eval` will drop it
    # unless it's permitted. A is completely isolated.
    assert not _ping(network_namespace, TARGET_B_IP, TARGET_A_IP), (
        "B->A should be BLOCKED by A's quarantine"
    )

    # 4. RELEASE_QUARANTINE A
    release_plan = EnforcementPlan(
        action_id=uuid.uuid4(),
        capability=ActionCapability.RELEASE_QUARANTINE,
        enforcement_bindings=plan.enforcement_bindings,
        quarantine_policy=None,
    )

    adapter.apply(release_plan)

    # Verify managed target is removed
    state = adapter.inspect()
    assert len(state) == 0

    # Verify previously denied traffic is restored
    assert _ping(network_namespace, TARGET_A_IP, TARGET_B_IP), "A->B should be RESTORED"
    assert _ping(network_namespace, TARGET_B_IP, TARGET_A_IP), "B->A should be RESTORED"
