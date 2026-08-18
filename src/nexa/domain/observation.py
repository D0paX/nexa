"""
Domain models for NEXA device observation.

These models represent the pure domain concepts for tracking observed network devices,
decoupled from any specific transport or collection mechanism.
"""

import re
from dataclasses import dataclass
from datetime import datetime
from ipaddress import IPv4Address


class ObservationDomainError(Exception):
    """Base exception for all observation domain errors."""


class InvalidObservationError(ObservationDomainError):
    """Raised when an invalid device observation is constructed."""


@dataclass(frozen=True)
class DeviceObservation:
    """
    Represents an individual device observed on the network.

    This is an immutable domain object. MAC addresses are strictly normalized
    to their lower-case, colon-separated canonical form (e.g., aa:bb:cc:dd:ee:ff).
    """

    ipv4_address: IPv4Address
    mac_address: str
    observed_at: datetime
    interface_name: str
    source: str = "arp_discovery"

    def __post_init__(self) -> None:
        """Validate and normalize observation data."""
        if not self.interface_name or not self.interface_name.strip():
            raise InvalidObservationError("Interface name cannot be empty.")

        if not self.mac_address or not self.mac_address.strip():
            raise InvalidObservationError("MAC address cannot be empty.")

        # Normalize MAC address format (remove hyphens/dots, make lowercase)
        normalized_mac = self.mac_address.lower().strip()
        normalized_mac = normalized_mac.replace("-", ":").replace(".", "")

        # If it's a continuous string of 12 hex chars, add colons
        if len(normalized_mac) == 12 and ":" not in normalized_mac:
            normalized_mac = ":".join(
                normalized_mac[i : i + 2] for i in range(0, 12, 2)
            )

        # Validate against canonical MAC format (aa:bb:cc:dd:ee:ff)
        mac_pattern = re.compile(r"^([0-9a-f]{2}:){5}[0-9a-f]{2}$")
        if not mac_pattern.match(normalized_mac):
            raise InvalidObservationError(
                f"Invalid MAC address format: {self.mac_address}"
            )

        # We must use object.__setattr__ because the dataclass is frozen
        object.__setattr__(self, "mac_address", normalized_mac)
