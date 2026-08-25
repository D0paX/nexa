"""
Cryptographic protocol envelopes for Phase 2 Verification.

Defines the bidirectional authenticated challenge-response protocol.
"""

from dataclasses import dataclass
from datetime import datetime
from enum import Enum
from typing import Any, Dict


class ChallengePurpose(Enum):
    """Reason for issuing the challenge."""

    ENROLLMENT = "enrollment"
    VERIFICATION = "verification"
    KEY_ROTATION = "key_rotation"
    REVOCATION = "revocation"


@dataclass(frozen=True)
class ChallengeEnvelope:
    """
    The canonical payload of a challenge.
    Must be signed by the NEXA Verifier.
    """

    protocol_version: str
    message_type: str  # Always 'challenge'
    verifier_identity: str  # The SHA-256 fingerprint of the NEXA verifier
    device_identity: (
        str | None
    )  # Target device fingerprint, or None for initial enrollment
    purpose: ChallengePurpose
    challenge_id: str
    nonce: str
    issued_at: datetime
    expires_at: datetime

    def to_dict(self) -> Dict[str, Any]:
        """Convert to dict for JCS canonicalization."""
        return {
            "protocol_version": self.protocol_version,
            "message_type": self.message_type,
            "verifier_identity": self.verifier_identity,
            "device_identity": self.device_identity,
            "purpose": self.purpose.value,
            "challenge_id": self.challenge_id,
            "nonce": self.nonce,
            "issued_at": self.issued_at.isoformat(),
            "expires_at": self.expires_at.isoformat(),
        }


@dataclass(frozen=True)
class AuthenticatedChallenge:
    """
    A challenge enveloped with the Verifier's signature.
    """

    payload: ChallengeEnvelope
    signature: str  # Base64url encoded signature of the JCS canonical payload


@dataclass(frozen=True)
class DeviceResponseEnvelope:
    """
    The canonical payload of a device response.
    Must be signed by the Device.
    """

    protocol_version: str
    message_type: str  # Always 'identity_response'
    challenge_id: str
    credential_version: int
    device_fingerprint: str
    # The device explicitly binds its response to the verifier and purpose
    verifier_identity: str
    purpose: ChallengePurpose
    nonce: str

    def to_dict(self) -> Dict[str, Any]:
        """Convert to dict for JCS canonicalization."""
        return {
            "protocol_version": self.protocol_version,
            "message_type": self.message_type,
            "challenge_id": self.challenge_id,
            "credential_version": self.credential_version,
            "device_fingerprint": self.device_fingerprint,
            "verifier_identity": self.verifier_identity,
            "purpose": self.purpose.value,
            "nonce": self.nonce,
        }


@dataclass(frozen=True)
class AuthenticatedDeviceResponse:
    """
    A response enveloped with the Device's signature.
    """

    payload: DeviceResponseEnvelope
    signature: str  # Base64url encoded signature of the JCS canonical payload


@dataclass(frozen=True)
class KeyRotationRequestEnvelope:
    """
    The canonical payload of a key rotation request.
    Must be signed by the OLD active credential.
    """

    protocol_version: str
    message_type: str  # Always 'key_rotation_request'
    purpose: ChallengePurpose  # Always ChallengePurpose.KEY_ROTATION
    identity_id: str
    old_credential_fingerprint: str
    new_public_key_b64: str  # Base64url encoded raw public key
    rotation_version: int

    def to_dict(self) -> Dict[str, Any]:
        """Convert to dict for JCS canonicalization."""
        return {
            "protocol_version": self.protocol_version,
            "message_type": self.message_type,
            "purpose": self.purpose.value,
            "identity_id": self.identity_id,
            "old_credential_fingerprint": self.old_credential_fingerprint,
            "new_public_key": self.new_public_key_b64,
            "rotation_version": self.rotation_version,
        }


@dataclass(frozen=True)
class AuthenticatedKeyRotationRequest:
    """
    A rotation request enveloped with the Device's old active signature.
    """

    payload: KeyRotationRequestEnvelope
    signature: str  # Base64url encoded signature of the JCS canonical payload
