"""
Unit tests for network discovery infrastructure adapter.
"""

import json
import subprocess
from ipaddress import IPv4Address, IPv4Network
from typing import Any, Callable
from unittest.mock import MagicMock, patch

import pytest

from nexa.domain.network import InvalidNetworkContextError, NetworkContext
from nexa.network.discovery import (
    AmbiguousInterfaceError,
    LinuxNetworkEnvironment,
    NetworkDiscoveryError,
    NoUsableInterfaceError,
)

# --- Test Data ---

MOCK_ROUTE_DEFAULT: list[dict[str, Any]] = [
    {"dst": "default", "gateway": "192.168.1.1", "dev": "eth0"}
]

MOCK_ADDR_ETH0: list[dict[str, Any]] = [
    {
        "ifname": "eth0",
        "addr_info": [{"family": "inet", "local": "192.168.1.20", "prefixlen": 24}],
    }
]

MOCK_ADDR_MULTIPLE: list[dict[str, Any]] = [
    {
        "ifname": "lo",
        "addr_info": [{"family": "inet", "local": "127.0.0.1", "prefixlen": 8}],
    },
    {
        "ifname": "eth0",
        "addr_info": [{"family": "inet", "local": "192.168.1.20", "prefixlen": 24}],
    },
    {
        "ifname": "wlan0",
        "addr_info": [{"family": "inet", "local": "10.0.0.5", "prefixlen": 16}],
    },
]


@pytest.fixture
def adapter() -> LinuxNetworkEnvironment:
    return LinuxNetworkEnvironment(timeout=1.5)


def mock_subprocess_run(
    route_output: list[dict[str, Any]],
    addr_output: list[dict[str, Any]],
    returncode: int = 0,
) -> Callable[..., MagicMock]:
    def side_effect(args: list[str], **kwargs: Any) -> MagicMock:
        # Verify shell-less execution and bounded timeout
        assert kwargs.get("shell") is False
        assert isinstance(kwargs.get("timeout"), float)
        assert kwargs.get("capture_output") is True
        assert kwargs.get("text") is True

        # Verify literal arguments
        valid_args = (
            ["ip", "-j", "-4", "route", "show", "default"],
            ["ip", "-j", "-4", "addr", "show"],
        )
        assert args in valid_args, f"Unexpected command args: {args}"

        mock_result = MagicMock()
        mock_result.returncode = returncode
        if "route" in args:
            mock_result.stdout = json.dumps(route_output)
        elif "addr" in args:
            mock_result.stdout = json.dumps(addr_output)
        else:
            mock_result.stdout = "[]"

        if returncode != 0:
            mock_result.stderr = "Simulated error"
            raise subprocess.CalledProcessError(
                returncode, args, output=mock_result.stdout, stderr=mock_result.stderr
            )

        return mock_result

    return side_effect


def test_valid_interface_with_route(adapter: LinuxNetworkEnvironment) -> None:
    with patch("subprocess.run") as mock_run:
        mock_run.side_effect = mock_subprocess_run(MOCK_ROUTE_DEFAULT, MOCK_ADDR_ETH0)
        ctx = adapter.discover()

        assert ctx.interface_name == "eth0"
        assert ctx.ipv4_address == IPv4Address("192.168.1.20")
        assert ctx.network == IPv4Network("192.168.1.0/24")
        assert ctx.gateway == IPv4Address("192.168.1.1")


def test_multiple_interfaces_with_default_route_selection(
    adapter: LinuxNetworkEnvironment,
) -> None:
    with patch("subprocess.run") as mock_run:
        # Default route says wlan0
        route = [{"dst": "default", "gateway": "10.0.0.1", "dev": "wlan0"}]
        mock_run.side_effect = mock_subprocess_run(route, MOCK_ADDR_MULTIPLE)
        ctx = adapter.discover()

        assert ctx.interface_name == "wlan0"
        assert ctx.ipv4_address == IPv4Address("10.0.0.5")
        assert ctx.network == IPv4Network("10.0.0.0/16")
        assert ctx.gateway == IPv4Address("10.0.0.1")


def test_no_default_route_one_usable_interface(
    adapter: LinuxNetworkEnvironment,
) -> None:
    with patch("subprocess.run") as mock_run:
        mock_run.side_effect = mock_subprocess_run([], MOCK_ADDR_ETH0)
        ctx = adapter.discover()

        assert ctx.interface_name == "eth0"
        assert ctx.ipv4_address == IPv4Address("192.168.1.20")
        assert ctx.gateway is None


def test_no_default_route_multiple_usable_interfaces(
    adapter: LinuxNetworkEnvironment,
) -> None:
    with patch("subprocess.run") as mock_run:
        mock_run.side_effect = mock_subprocess_run([], MOCK_ADDR_MULTIPLE)
        with pytest.raises(AmbiguousInterfaceError):
            adapter.discover()


def test_no_usable_interface(adapter: LinuxNetworkEnvironment) -> None:
    with patch("subprocess.run") as mock_run:
        # Only loopback
        addrs = [
            {
                "ifname": "lo",
                "addr_info": [{"family": "inet", "local": "127.0.0.1", "prefixlen": 8}],
            }
        ]
        mock_run.side_effect = mock_subprocess_run([], addrs)
        with pytest.raises(NoUsableInterfaceError):
            adapter.discover()


def test_no_ipv4_address(adapter: LinuxNetworkEnvironment) -> None:
    with patch("subprocess.run") as mock_run:
        # Interface exists but no inet address
        addrs = [
            {
                "ifname": "eth0",
                "addr_info": [{"family": "inet6", "local": "fe80::1", "prefixlen": 64}],
            }
        ]
        mock_run.side_effect = mock_subprocess_run(MOCK_ROUTE_DEFAULT, addrs)
        with pytest.raises(NoUsableInterfaceError):
            adapter.discover()


def test_malformed_json(adapter: LinuxNetworkEnvironment) -> None:
    with patch("subprocess.run") as mock_run:
        mock_result = MagicMock()
        mock_result.stdout = "This is not JSON"
        mock_result.returncode = 0
        mock_run.return_value = mock_result

        with pytest.raises(NetworkDiscoveryError, match="Failed to parse JSON"):
            adapter.discover()


def test_malformed_ipv4(adapter: LinuxNetworkEnvironment) -> None:
    with patch("subprocess.run") as mock_run:
        addrs = [
            {
                "ifname": "eth0",
                "addr_info": [
                    {"family": "inet", "local": "999.999.999.999", "prefixlen": 24}
                ],
            }
        ]
        mock_run.side_effect = mock_subprocess_run(MOCK_ROUTE_DEFAULT, addrs)
        with pytest.raises(NetworkDiscoveryError, match="Malformed IP/prefix"):
            adapter.discover()


def test_missing_gateway(adapter: LinuxNetworkEnvironment) -> None:
    with patch("subprocess.run") as mock_run:
        route = [{"dst": "default", "dev": "eth0"}]  # Missing 'gateway'
        mock_run.side_effect = mock_subprocess_run(route, MOCK_ADDR_ETH0)
        ctx = adapter.discover()
        assert ctx.gateway is None


def test_invalid_gateway(adapter: LinuxNetworkEnvironment) -> None:
    with patch("subprocess.run") as mock_run:
        route = [{"dst": "default", "gateway": "not-an-ip", "dev": "eth0"}]
        mock_run.side_effect = mock_subprocess_run(route, MOCK_ADDR_ETH0)
        ctx = adapter.discover()
        assert ctx.gateway is None  # Should fallback to None gracefully


def test_command_not_found(adapter: LinuxNetworkEnvironment) -> None:
    with patch("subprocess.run", side_effect=FileNotFoundError):
        with pytest.raises(NetworkDiscoveryError, match="command was not found"):
            adapter.discover()


def test_command_timeout(adapter: LinuxNetworkEnvironment) -> None:
    with patch(
        "subprocess.run", side_effect=subprocess.TimeoutExpired(cmd="ip", timeout=1.5)
    ):
        with pytest.raises(NetworkDiscoveryError, match="timed out"):
            adapter.discover()


def test_nonzero_subprocess_exit(adapter: LinuxNetworkEnvironment) -> None:
    with patch("subprocess.run") as mock_run:
        mock_run.side_effect = mock_subprocess_run([], [], returncode=1)
        with pytest.raises(NetworkDiscoveryError, match="failed with exit code 1"):
            adapter.discover()


# --- Domain Model specific tests ---


def test_domain_model_rejects_empty_interface() -> None:
    with pytest.raises(
        InvalidNetworkContextError, match="Interface name cannot be empty"
    ):
        NetworkContext(
            interface_name=" ",
            ipv4_address=IPv4Address("192.168.1.20"),
            network=IPv4Network("192.168.1.0/24"),
            gateway=IPv4Address("192.168.1.1"),
        )


def test_domain_model_rejects_ip_outside_network() -> None:
    with pytest.raises(
        InvalidNetworkContextError, match="not within the network block"
    ):
        NetworkContext(
            interface_name="eth0",
            ipv4_address=IPv4Address("10.0.0.5"),
            network=IPv4Network("192.168.1.0/24"),
            gateway=IPv4Address("192.168.1.1"),
        )


def test_domain_model_rejects_loopback() -> None:
    with pytest.raises(InvalidNetworkContextError, match="Loopback addresses"):
        NetworkContext(
            interface_name="lo",
            ipv4_address=IPv4Address("127.0.0.1"),
            network=IPv4Network("127.0.0.0/8"),
            gateway=None,
        )


def test_domain_model_rejects_gateway_outside_network() -> None:
    with pytest.raises(
        InvalidNetworkContextError, match="Gateway 10.0.0.1 is not within"
    ):
        NetworkContext(
            interface_name="eth0",
            ipv4_address=IPv4Address("192.168.1.20"),
            network=IPv4Network("192.168.1.0/24"),
            gateway=IPv4Address("10.0.0.1"),
        )
