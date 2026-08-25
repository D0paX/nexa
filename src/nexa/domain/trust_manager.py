"""
Trust Management Service.

Handles identity revocation, credential superseding, and manual lifecycle actions.
"""

import logging
import uuid
from datetime import datetime, timezone

from nexa.domain.protocol import AuthenticatedKeyRotationRequest, ChallengePurpose
from nexa.domain.trust import (
    CredentialState,
    TrustAuditEvent,
    TrustAuditEventType,
    TrustState,
)
from nexa.domain.trust_lifecycle import TrustLifecycle, TrustRepository

logger = logging.getLogger(__name__)


class TrustManager:
    def __init__(self, trust_repo: TrustRepository):
        self.trust_repo = trust_repo

    def revoke_identity(self, identity_id: str, reason: str) -> None:
        """
        Operator revokes a TrustedDeviceIdentity and all its credentials.
        """
        identity = self.trust_repo.get_identity(identity_id)
        if not identity:
            raise ValueError(f"Identity {identity_id} not found.")

        # Transition identity state
        new_identity = TrustLifecycle.transition_trust_state(
            identity, TrustState.REVOKED
        )
        self.trust_repo.save_identity(new_identity)

        # Transition active credentials
        active_cred = self.trust_repo.get_active_credential_for_identity(identity_id)
        if active_cred:
            revoked_cred = TrustLifecycle.transition_credential_state(
                active_cred, CredentialState.REVOKED
            )
            self.trust_repo.save_credential(revoked_cred)

        # Audit
        now = datetime.now(timezone.utc)
        audit = TrustAuditEvent(
            event_id=uuid.uuid4(),
            identity_id=uuid.UUID(identity_id),
            event_type=TrustAuditEventType.IDENTITY_REVOKED,
            timestamp=now,
            details={"reason": reason},
        )
        self.trust_repo.append_audit_event(audit)
        logger.warning(f"Identity {identity_id} revoked. Reason: {reason}")

    def verify_and_rotate_credential(
        self, request: "AuthenticatedKeyRotationRequest"
    ) -> None:
        """
        Cryptographically verify and rotate the active credential for an identity.
        """
        payload = request.payload

        if payload.message_type != "key_rotation_request":
            raise ValueError("Invalid message type.")
        if payload.purpose != ChallengePurpose.KEY_ROTATION:
            raise ValueError("Invalid challenge purpose.")

        identity_id = payload.identity_id
        identity = self.trust_repo.get_identity(identity_id)
        if not identity:
            raise ValueError(f"Identity {identity_id} not found.")

        if identity.state == TrustState.REVOKED:
            raise ValueError("Cannot rotate credential for a revoked identity.")

        if identity.state != TrustState.TRUSTED:
            raise ValueError("Identity must be TRUSTED to rotate credential.")

        active_cred = self.trust_repo.get_active_credential_for_identity(identity_id)
        if not active_cred:
            raise ValueError("No active credential to rotate.")

        if active_cred.fingerprint_sha256 != payload.old_credential_fingerprint:
            raise ValueError("Old credential fingerprint mismatch.")

        if payload.rotation_version <= active_cred.version:
            raise ValueError(
                f"Rotation version {payload.rotation_version} must be strictly "
                f"greater than {active_cred.version}."
            )

        # Verify signature using OLD ACTIVE credential
        from nexa.crypto.primitives import (
            base64url_decode,
            canonicalize,
            get_fingerprint,
            verify_signature,
        )

        canonical_req = canonicalize(payload.to_dict())
        is_valid = verify_signature(
            active_cred.public_key_bytes, request.signature, canonical_req
        )
        if not is_valid:
            raise ValueError("Invalid signature on key rotation request.")

        try:
            new_public_key_bytes = base64url_decode(payload.new_public_key_b64)
        except Exception:
            raise ValueError("Malformed new public key encoding.") from None

        new_fingerprint = get_fingerprint(new_public_key_bytes)

        # Supersede old credential
        superseded_cred = TrustLifecycle.transition_credential_state(
            active_cred, CredentialState.SUPERSEDED
        )
        self.trust_repo.save_credential(superseded_cred)

        # Create new credential
        from nexa.domain.trust import Credential

        now = datetime.now(timezone.utc)

        new_cred = Credential(
            identity_id=uuid.UUID(identity_id),
            public_key_bytes=new_public_key_bytes,
            fingerprint_sha256=new_fingerprint,
            version=payload.rotation_version,
            state=CredentialState.ACTIVE,
            created_at=now,
            updated_at=now,
        )
        self.trust_repo.save_credential(new_cred)

        # Audit
        audit = TrustAuditEvent(
            event_id=uuid.uuid4(),
            identity_id=uuid.UUID(identity_id),
            event_type=TrustAuditEventType.KEY_ROTATED,
            timestamp=now,
            details={
                "old_fingerprint": active_cred.fingerprint_sha256,
                "old_version": active_cred.version,
                "new_fingerprint": new_fingerprint,
                "new_version": new_cred.version,
            },
        )
        self.trust_repo.append_audit_event(audit)
