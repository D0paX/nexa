"""
Domain models for correlated devices.

These models represent stable devices derived from ephemeral observations.
"""

from dataclasses import dataclass, field
from datetime import datetime
from ipaddress import IPv4Address
from typing import FrozenSet
from uuid import UUID

from nexa.domain.correlation import ObservationConflict, PresenceState
from nexa.domain.scope import NetworkScope


@dataclass(frozen=True)
class ScanContext:
    """
    Context binding an observation run to a specific network scope.
    """

    scan_id: UUID
    started_at: datetime
    network_scope: NetworkScope


@dataclass(frozen=True)
class DeviceRecord:
    """
    A correlated representation of a device observed on the network.
    This is an observational correlation record, not a trusted identity.
    """

    device_id: UUID
    network_scope: NetworkScope
    mac_addresses: FrozenSet[str]
    ipv4_addresses: FrozenSet[IPv4Address]
    first_observed_at: datetime
    last_observed_at: datetime
    presence_state: PresenceState
    conflicts: FrozenSet[ObservationConflict] = field(default_factory=frozenset)
