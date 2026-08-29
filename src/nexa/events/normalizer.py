from typing import Optional
from uuid import UUID

from nexa.domain.events import SecurityEvent, Severity
from nexa.domain.lifecycle import LifecycleEvent, LifecycleEventType
from nexa.domain.trust import TrustAuditEvent, TrustAuditEventType


def normalize_lifecycle_event(
    event: LifecycleEvent, network_scope: str = "GLOBAL"
) -> SecurityEvent:
    severity = Severity.INFO
    if event.event_type == LifecycleEventType.CONFLICT_DETECTED:
        severity = Severity.HIGH
    elif event.event_type == LifecycleEventType.FIRST_SEEN:
        severity = Severity.LOW

    return SecurityEvent(
        event_class=f"LIFECYCLE_{event.event_type.value.upper()}",
        timestamp=event.timestamp,
        severity=severity,
        device_id=event.device_id,
        network_scope=network_scope,
        context={"description": event.description},
    )


def normalize_trust_event(
    event: TrustAuditEvent,
    network_scope: str = "GLOBAL",
    device_id: Optional[UUID] = None,
) -> SecurityEvent:
    severity = Severity.INFO

    # Map high/critical trust events
    if event.event_type in (
        TrustAuditEventType.VERIFICATION_FAILED,
        TrustAuditEventType.IDENTITY_CONCURRENCY_ANOMALY,
    ):
        severity = Severity.HIGH
    elif event.event_type in (
        TrustAuditEventType.CREDENTIAL_REVOKED,
        TrustAuditEventType.IDENTITY_REVOKED,
    ):
        severity = Severity.MEDIUM

    return SecurityEvent(
        event_class=f"TRUST_{event.event_type.value.upper()}",
        timestamp=event.timestamp,
        severity=severity,
        identity_id=event.identity_id,
        device_id=device_id,
        network_scope=network_scope,
        context=event.details,
    )
