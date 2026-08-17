"""
Domain models for NEXA network observation.

These models represent the core network concepts independent of any underlying
operating system or observation mechanism.
"""

from dataclasses import dataclass
from ipaddress import IPv4Address, IPv4Network


class NetworkDomainError(Exception):
    """Base exception for all network domain errors."""


class InvalidNetworkContextError(NetworkDomainError):
    """Raised when an invalid network context is constructed."""


@dataclass(frozen=True)
class NetworkContext:
    """
    Represents the validated local network environment where NEXA is operating.

    This is an immutable domain object that decouples the application from the
    underlying operating system's discovery mechanics (e.g., iproute2).
    """

    interface_name: str
    ipv4_address: IPv4Address
    network: IPv4Network
    gateway: IPv4Address | None

    def __post_init__(self) -> None:
        """Validate logical consistency of the network context."""
        if not self.interface_name or not self.interface_name.strip():
            raise InvalidNetworkContextError("Interface name cannot be empty.")

        # Ensure the IP address actually belongs to the provided network block
        if self.ipv4_address not in self.network:
            raise InvalidNetworkContextError(
                f"IP address {self.ipv4_address} is not within "
                f"the network block {self.network}."
            )

        # Loopback interfaces are not valid targets for NEXA's external observation
        if self.ipv4_address.is_loopback:
            raise InvalidNetworkContextError(
                f"Loopback addresses ({self.ipv4_address}) are not "
                "valid for external network contexts."
            )

        # If a gateway is provided, it should generally be in the same network
        # (Though point-to-point links can violate this, NEXA targets local LANs)
        if self.gateway is not None:
            if self.gateway not in self.network:
                raise InvalidNetworkContextError(
                    f"Gateway {self.gateway} is not within "
                    f"the local network block {self.network}."
                )
