from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any, Dict, Optional
from uuid import UUID, uuid4


class Severity(Enum):
    INFO = "INFO"
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


@dataclass
class SecurityEvent:
    event_id: UUID = field(default_factory=uuid4)
    event_class: str = field(default="")
    timestamp: datetime = field(default_factory=datetime.utcnow)
    severity: Severity = field(default=Severity.INFO)
    identity_id: Optional[UUID] = None
    device_id: Optional[UUID] = None
    network_scope: str = field(default="GLOBAL")
    context: Dict[str, Any] = field(default_factory=dict)


@dataclass
class AggregatedSecurityEvent:
    event_id: UUID = field(default_factory=uuid4)
    event_class: str = field(default="")
    time_range_start: datetime = field(default_factory=datetime.utcnow)
    time_range_end: datetime = field(default_factory=datetime.utcnow)
    severity: Severity = field(default=Severity.INFO)
    identity_ids: list[str] = field(default_factory=list)
    device_ids: list[str] = field(default_factory=list)
    network_scope: str = field(default="GLOBAL")
    count: int = 1
    aggregation_reason: str = "COMPACTION"
