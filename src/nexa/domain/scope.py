"""
Domain model representing the canonical, safe observation scope for NEXA.

This module consumes the os-level NetworkContext and mathematically derives
the boundaries that Phase 1C and future phases will observe, ensuring
that the observation target is well-bounded and safe.
"""

from dataclasses import dataclass
from ipaddress import IPv4Address

from nexa.domain.network import NetworkContext, NetworkDomainError


class InvalidNetworkScopeError(NetworkDomainError):
    """Raised when a NetworkScope violates safety bounds or logical consistency."""


@dataclass(frozen=True)
class NetworkScope:
    """
    Represents the safe, bounded observation target for NEXA.

    This is an immutable domain object that defines exactly what addresses
    NEXA is permitted to passively monitor or observe. It decouples the
    observation engines from the OS discovery mechanics.
    """

    network_address: IPv4Address
    broadcast_address: IPv4Address
    prefix_length: int
    host_count: int
    first_usable_host: IPv4Address | None
    last_usable_host: IPv4Address | None
    gateway: IPv4Address | None
    interface_name: str

    def __post_init__(self) -> None:
        """Validate the scope size and consistency."""
        # 1. Reject extremely large networks (e.g., /8) to prevent runaway observation.
        #    A limit of /16 (65,534 hosts) is the maximum safe bound for
        #    local LAN observation.
        if self.prefix_length < 16:
            raise InvalidNetworkScopeError(
                f"Network prefix /{self.prefix_length} is too large. "
                "NEXA enforces a maximum observation scope of /16 to prevent "
                "accidental network flooding."
            )

        # 2. Reject 0.0.0.0 networks
        if self.network_address == IPv4Address("0.0.0.0"):
            raise InvalidNetworkScopeError(
                "0.0.0.0 is not a valid observation network."
            )

    @classmethod
    def from_context(cls, context: NetworkContext) -> "NetworkScope":
        """
        Deterministically derive the NetworkScope from a validated NetworkContext.

        Args:
            context: The OS-level NetworkContext discovered in Phase 1A.

        Returns:
            NetworkScope: The canonical bounds for observation.
        """
        net = context.network

        # Calculate usable hosts correctly supporting /32 and /31 edge cases
        # Python's IPv4Network.hosts() handles these according to RFC 3021
        hosts_list = list(net.hosts())

        host_count = len(hosts_list)
        first_host = hosts_list[0] if host_count > 0 else None
        last_host = hosts_list[-1] if host_count > 0 else None

        return cls(
            network_address=net.network_address,
            broadcast_address=net.broadcast_address,
            prefix_length=net.prefixlen,
            host_count=host_count,
            first_usable_host=first_host,
            last_usable_host=last_host,
            gateway=context.gateway,
            interface_name=context.interface_name,
        )
