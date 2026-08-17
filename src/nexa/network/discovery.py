"""
Linux-specific network discovery infrastructure adapter.

This module interacts safely with the Linux `iproute2` utilities to extract
network configuration state without modifying the system or requiring elevated
privileges.
"""

import json
import subprocess
from ipaddress import IPv4Address, IPv4Network
from typing import Any, Optional

from nexa.domain.network import NetworkContext, NetworkDomainError


class NetworkDiscoveryError(Exception):
    """Base exception for infrastructure network discovery failures."""


class NoUsableInterfaceError(NetworkDiscoveryError):
    """Raised when no suitable network interface can be found."""


class AmbiguousInterfaceError(NetworkDiscoveryError):
    """Raised when interface selection cannot be determined automatically."""


class LinuxNetworkEnvironment:
    """
    Adapter for discovering the Linux network environment via `iproute2`.
    """

    def __init__(self, timeout: float = 2.0) -> None:
        """
        Initialize the discovery adapter.

        Args:
            timeout: Subprocess execution timeout in seconds.
        """
        self.timeout = timeout

    def discover(self) -> NetworkContext:
        """
        Discover the active network context.

        Returns:
            NetworkContext: The normalized domain model representing the current LAN.

        Raises:
            NetworkDiscoveryError: If discovery fails due to system errors.
        """
        routes = self._run_ip_command(["ip", "-j", "-4", "route", "show", "default"])
        addrs = self._run_ip_command(["ip", "-j", "-4", "addr", "show"])

        # Determine the primary interface and optional gateway
        selected_iface, gateway = self._select_interface(routes, addrs)

        # Find the IPv4 configuration for the selected interface
        ip_addr, network = self._extract_ipv4_config(addrs, selected_iface)

        try:
            return NetworkContext(
                interface_name=selected_iface,
                ipv4_address=ip_addr,
                network=network,
                gateway=gateway,
            )
        except NetworkDomainError as e:
            raise NetworkDiscoveryError(f"Invalid discovered network state: {e}") from e

    def _run_ip_command(self, args: list[str]) -> list[dict[str, Any]]:
        """
        Safely execute an `ip` command and parse its JSON output.
        """
        try:
            result = subprocess.run(
                args,
                capture_output=True,
                text=True,
                check=True,
                timeout=self.timeout,
                shell=False,
            )
        except FileNotFoundError as e:
            raise NetworkDiscoveryError(
                "The 'ip' command was not found. Are you on Linux with iproute2?"
            ) from e
        except subprocess.TimeoutExpired as e:
            raise NetworkDiscoveryError(
                f"Command timed out after {self.timeout}s: {args}"
            ) from e
        except subprocess.CalledProcessError as e:
            raise NetworkDiscoveryError(
                f"Command failed with exit code {e.returncode}: {e.stderr.strip()}"
            ) from e

        try:
            parsed = json.loads(result.stdout)
            if not isinstance(parsed, list):
                raise NetworkDiscoveryError(f"Expected a JSON list, got {type(parsed)}")
            return parsed
        except json.JSONDecodeError as e:
            if not result.stdout.strip():
                return []  # Sometimes 'ip' returns nothing (e.g., no default route)
            raise NetworkDiscoveryError(
                "Failed to parse JSON output from ip command."
            ) from e

    def _select_interface(
        self, routes: list[dict[str, Any]], addrs: list[dict[str, Any]]
    ) -> tuple[str, Optional[IPv4Address]]:
        """
        Determine the best interface to use based on the default route
        or available configs.
        Returns (interface_name, optional_gateway_ip).
        """
        # 1. Primary logic: use the default IPv4 route
        if routes:
            # Pick the first default route
            default_route = routes[0]
            dev = default_route.get("dev")
            gw = default_route.get("gateway")
            if not dev:
                raise NetworkDiscoveryError("Default route missing 'dev' field.")

            try:
                gateway_ip = IPv4Address(gw) if gw else None
            except ValueError:
                gateway_ip = None

            return dev, gateway_ip

        # 2. Fallback logic: no default route. Check if there is exactly
        # one usable interface.
        candidates = []
        for iface in addrs:
            ifname = iface.get("ifname")
            if not ifname or ifname == "lo":
                continue

            # Check if it has an IPv4 address
            addr_info = iface.get("addr_info", [])
            if any(info.get("family") == "inet" for info in addr_info):
                candidates.append(ifname)

        if not candidates:
            raise NoUsableInterfaceError(
                "No default route and no usable IPv4 interfaces found."
            )

        if len(candidates) > 1:
            raise AmbiguousInterfaceError(
                "No default route found, and multiple candidate interfaces "
                f"exist: {candidates}. "
                "Cannot automatically select a primary interface safely."
            )

        return candidates[0], None

    def _extract_ipv4_config(
        self, addrs: list[dict[str, Any]], target_iface: str
    ) -> tuple[IPv4Address, IPv4Network]:
        """
        Find the IPv4 address and subnet for the given interface.
        """
        for iface in addrs:
            if iface.get("ifname") == target_iface:
                for info in iface.get("addr_info", []):
                    if info.get("family") == "inet":
                        local_ip_str = info.get("local")
                        prefixlen = info.get("prefixlen")

                        if not local_ip_str or prefixlen is None:
                            continue

                        try:
                            ip_addr = IPv4Address(local_ip_str)
                            # Create the network representation (e.g., 192.168.1.0/24)
                            # strict=False allows passing host IPs and masking them down
                            network = IPv4Network(
                                f"{local_ip_str}/{prefixlen}", strict=False
                            )
                            return ip_addr, network
                        except ValueError as e:
                            raise NetworkDiscoveryError(
                                f"Malformed IP/prefix data on {target_iface}: {e}"
                            ) from e

        raise NoUsableInterfaceError(
            f"Interface '{target_iface}' has no valid IPv4 address."
        )
