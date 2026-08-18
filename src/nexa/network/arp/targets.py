"""
Target generation for the ARP observation engine.

Provides deterministic, bounded generation of IPv4 targets for ARP discovery.
"""

from ipaddress import IPv4Address
from typing import Iterator

from nexa.domain.scope import NetworkScope
from nexa.network.arp.errors import ARPTargetOutOfBoundsError


class ARPTargetGenerator:
    """
    Deterministically generates ARP targets bounded by a NetworkScope.
    """

    def __init__(self, scope: NetworkScope) -> None:
        """
        Initialize the target generator.

        Args:
            scope: The validated network scope defining the bounds.
        """
        self._scope = scope

    def generate(self) -> Iterator[IPv4Address]:
        """
        Generate all valid IPv4 targets within the scope.

        Yields:
            IPv4Address: The next observation target.

        Raises:
            ARPTargetOutOfBoundsError: If target generation falls outside safe bounds.
        """
        if self._scope.host_count == 0:
            return

        if (
            self._scope.first_usable_host is None
            or self._scope.last_usable_host is None
        ):
            return

        first_int = int(self._scope.first_usable_host)
        last_int = int(self._scope.last_usable_host)

        for ip_int in range(first_int, last_int + 1):
            ip_addr = IPv4Address(ip_int)

            # Failsafe checks
            if ip_addr.is_loopback:
                continue
            if ip_addr.is_multicast:
                continue

            # Extra paranoia check to ensure we haven't somehow exceeded scope
            if (
                ip_addr < self._scope.network_address
                or ip_addr > self._scope.broadcast_address
            ):
                raise ARPTargetOutOfBoundsError(
                    f"Generated target {ip_addr} violates scope "
                    f"{self._scope.network_address}/{self._scope.prefix_length}"
                )

            yield ip_addr
