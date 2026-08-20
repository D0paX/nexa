"""
Domain models for device observation correlation.

These models represent the presence state and conflicts arising
from correlating network observations.
"""

from dataclasses import dataclass
from datetime import datetime
from enum import Enum
from typing import FrozenSet


class PresenceState(Enum):
    """
    The current presence state of a correlated device.

    PRESENT: The device was observed during the current ScanContext.
    UNSEEN: The device exists in memory but was not observed in the current ScanContext.
    """

    PRESENT = "present"
    UNSEEN = "unseen"


class ConflictClassification(Enum):
    """
    Strongly typed classification of observation conflicts.
    """

    IP_COLLISION = "ip_collision"


@dataclass(frozen=True)
class ObservationConflict:
    """
    Represents a conflict detected during correlation (e.g. multiple MACs for one IP).
    """

    classification: ConflictClassification
    description: str
    involved_macs: FrozenSet[str]
    observed_at: datetime
