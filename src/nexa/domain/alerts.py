from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any, Dict, Optional
from uuid import UUID, uuid4

from nexa.domain.events import Severity


class AlertState(Enum):
    NEW = "NEW"
    ACKNOWLEDGED = "ACKNOWLEDGED"
    RESOLVED = "RESOLVED"
    IGNORED = "IGNORED"


class NotificationDeliveryState(Enum):
    QUEUED = "QUEUED"
    IN_FLIGHT = "IN_FLIGHT"
    ACCEPTED = "ACCEPTED"
    FAILED = "FAILED"
    RETRYING = "RETRYING"
    EXHAUSTED = "EXHAUSTED"


@dataclass
class Alert:
    alert_id: UUID = field(default_factory=uuid4)
    aggregation_key: str = field(default="")
    state: AlertState = field(default=AlertState.NEW)
    severity: Severity = field(default=Severity.INFO)
    event_class: str = field(default="")
    identity_id: Optional[UUID] = None
    device_id: Optional[UUID] = None
    network_scope: str = field(default="GLOBAL")
    event_count: int = 1
    first_seen: datetime = field(default_factory=datetime.utcnow)
    last_seen: datetime = field(default_factory=datetime.utcnow)


@dataclass
class Notification:
    notification_id: UUID = field(default_factory=uuid4)
    alert_id: UUID = field(default_factory=uuid4)
    state: NotificationDeliveryState = field(default=NotificationDeliveryState.QUEUED)
    payload: Dict[str, Any] = field(default_factory=dict)
    retry_count: int = 0
    next_retry_at: Optional[datetime] = None
    created_at: datetime = field(default_factory=datetime.utcnow)
    updated_at: datetime = field(default_factory=datetime.utcnow)
