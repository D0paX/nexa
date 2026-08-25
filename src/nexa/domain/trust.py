"""Cryptographic Identity and Trust Domain Model."""

import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Any, Optional


class TrustState(Enum):
    """The overarching trust relationship for a logical identity."""

    UNKNOWN = "UNKNOWN"
    VERIFIED_UNTRUSTED = "VERIFIED_UNTRUSTED"
    PENDING_ENROLLMENT = "PENDING_ENROLLMENT"
    TRUSTED = "TRUSTED"
    REVOKED = "REVOKED"


class CredentialState(Enum):
    """The lifecycle state of a specific cryptographic credential."""

    ACTIVE = "ACTIVE"
    SUPERSEDED = "SUPERSEDED"
    REVOKED = "REVOKED"


class TrustAuditEventType(Enum):
    """Security-sensitive auditable events for cryptographic trust."""

    ENROLLMENT_REQUESTED = "ENROLLMENT_REQUESTED"
    ENROLLMENT_APPROVED = "ENROLLMENT_APPROVED"
    VERIFICATION_SUCCEEDED = "VERIFICATION_SUCCEEDED"
    VERIFICATION_FAILED = "VERIFICATION_FAILED"
    KEY_ROTATED = "KEY_ROTATED"
    CREDENTIAL_REVOKED = "CREDENTIAL_REVOKED"
    IDENTITY_REVOKED = "IDENTITY_REVOKED"
    IDENTITY_CONCURRENCY_ANOMALY = "IDENTITY_CONCURRENCY_ANOMALY"


@dataclass(frozen=True)
class TrustedDeviceIdentity:
    """The stable, logical representation of a known trusted device."""

    identity_id: uuid.UUID
    state: TrustState
    created_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))
    updated_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))


@dataclass(frozen=True)
class Credential:
    """A specific asymmetric public key currently representing the device."""

    identity_id: uuid.UUID
    public_key_bytes: bytes
    fingerprint_sha256: str
    version: int
    state: CredentialState
    created_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))
    updated_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))


@dataclass(frozen=True)
class TrustAuditEvent:
    """Immutable audit log event for security actions."""

    event_id: uuid.UUID
    identity_id: Optional[uuid.UUID]
    event_type: TrustAuditEventType
    timestamp: datetime = field(default_factory=lambda: datetime.now(timezone.utc))
    details: dict[str, Any] = field(default_factory=dict)
