"""
Device Enrollment Flow for Phase 2 Verification.

Coordinates the explicit operator-driven enrollment of a device into the trust system.
"""

import logging
import uuid
from datetime import datetime, timezone

from cryptography.exceptions import InvalidSignature

from nexa.crypto.primitives import canonicalize, get_fingerprint, verify_signature
from nexa.domain.protocol import (
    AuthenticatedChallenge,
    AuthenticatedDeviceResponse,
    ChallengePurpose,
)
from nexa.domain.trust import (
    Credential,
    CredentialState,
    TrustAuditEvent,
    TrustAuditEventType,
    TrustedDeviceIdentity,
    TrustState,
)
from nexa.domain.trust_lifecycle import TrustRepository
from nexa.domain.verifier import IdentityVerifier, VerificationError

logger = logging.getLogger(__name__)


class EnrollmentService:
    """
    Coordinates the multi-step operator-driven enrollment process.
    """

    def __init__(self, trust_repo: TrustRepository, verifier: IdentityVerifier):
        self.trust_repo = trust_repo
        self.verifier = verifier

    def initiate_enrollment(
        self, scope_id: str, device_id: str
    ) -> AuthenticatedChallenge:
        """
        Step 1: Operator initiates enrollment.
        Issues an enrollment challenge to the device.
        """
        # We don't have a TrustedDeviceIdentity yet, or it's in UNKNOWN state.
        # We issue a challenge with target_fingerprint=None
        return self.verifier.issue_challenge(
            scope_id=scope_id,
            device_id=device_id,
            purpose=ChallengePurpose.ENROLLMENT,
            target_fingerprint=None,
        )

    def submit_enrollment_request(
        self,
        scope_id: str,
        device_id: str,
        challenge: AuthenticatedChallenge,
        response: AuthenticatedDeviceResponse,
        public_key_bytes: bytes,
    ) -> TrustedDeviceIdentity:
        """
        Step 2: Device provides response with its public key.
        NEXA verifies the signature, and creates the identity in PENDING_ENROLLMENT.
        """
        # Validate that the response fingerprint matches the provided public key
        computed_fingerprint = get_fingerprint(public_key_bytes)
        if response.payload.device_fingerprint != computed_fingerprint:
            raise VerificationError("Device fingerprint does not match public key.")

        # 1. Enforce bounds on challenge matching
        if response.payload.challenge_id != challenge.payload.challenge_id:
            raise VerificationError("Challenge ID mismatch.")
        if response.payload.nonce != challenge.payload.nonce:
            raise VerificationError("Nonce mismatch.")
        if response.payload.purpose != ChallengePurpose.ENROLLMENT:
            raise VerificationError("Purpose must be ENROLLMENT.")
        if response.payload.verifier_identity != self.verifier.verifier_fingerprint:
            raise VerificationError("Verifier identity mismatch.")

        # 2. Check nonce cache
        if not self.verifier.nonce_cache.consume_nonce(response.payload.nonce):
            raise VerificationError("Nonce is invalid, expired, or already used.")

        # 3. Verify Signature with the provided key
        try:
            canonical_response = canonicalize(response.payload.to_dict())
            verify_signature(
                public_key_bytes,
                response.signature,
                canonical_response,
            )
        except InvalidSignature:
            # We must release the rate limiter slot because verify_response is bypassed
            self.verifier.rate_limiter.release_enrollment()
            raise VerificationError(
                "Invalid device signature during enrollment."
            ) from None

        # Release the slot after successful crypto validation
        self.verifier.rate_limiter.release_enrollment()

        # Check if credential already exists
        existing_cred = self.trust_repo.get_credential_by_fingerprint(
            computed_fingerprint
        )
        if existing_cred:
            raise VerificationError("Credential fingerprint already enrolled.")

        # 4. Create the identity and credential
        now = datetime.now(timezone.utc)
        identity_id = uuid.uuid4()

        identity = TrustedDeviceIdentity(
            identity_id=identity_id,
            state=TrustState.PENDING_ENROLLMENT,
            created_at=now,
            updated_at=now,
        )

        credential = Credential(
            identity_id=identity_id,
            public_key_bytes=public_key_bytes,
            fingerprint_sha256=computed_fingerprint,
            version=1,
            state=CredentialState.ACTIVE,
            created_at=now,
            updated_at=now,
        )

        self.trust_repo.save_identity(identity)
        self.trust_repo.save_credential(credential)

        # Audit
        audit = TrustAuditEvent(
            event_id=uuid.uuid4(),
            identity_id=identity_id,
            event_type=TrustAuditEventType.ENROLLMENT_REQUESTED,
            timestamp=now,
            details={
                "reason": "explicit_enrollment",
                "challenge_id": challenge.payload.challenge_id,
                "credential_version": 1,
            },
        )
        self.trust_repo.append_audit_event(audit)
        self.trust_repo.link_device_to_identity(device_id, str(identity_id))

        return identity

    def approve_enrollment(
        self, identity_id: str, expected_fingerprint: str
    ) -> TrustedDeviceIdentity:
        """
        Step 3: Operator explicitly approves enrollment using out-of-band fingerprint.
        NEXA transitions the identity to TRUSTED.
        """
        identity = self.trust_repo.get_identity(identity_id)
        if not identity:
            raise ValueError(f"Identity {identity_id} not found.")

        if identity.state != TrustState.PENDING_ENROLLMENT:
            raise VerificationError("Identity is not pending enrollment.")

        credential = self.trust_repo.get_active_credential_for_identity(identity_id)
        if not credential:
            raise VerificationError("No active credential found for identity.")

        if credential.fingerprint_sha256 != expected_fingerprint:
            raise VerificationError(
                "Operator fingerprint does not match device credential."
            )

        # Transition to TRUSTED
        from nexa.domain.trust_lifecycle import TrustLifecycle

        new_identity = TrustLifecycle.transition_trust_state(
            identity, TrustState.TRUSTED
        )
        self.trust_repo.save_identity(new_identity)

        now = datetime.now(timezone.utc)
        audit = TrustAuditEvent(
            event_id=uuid.uuid4(),
            identity_id=uuid.UUID(identity_id),
            event_type=TrustAuditEventType.ENROLLMENT_APPROVED,
            timestamp=now,
            details={
                "reason": "operator_approval",
                "verified_fingerprint": expected_fingerprint,
            },
        )
        self.trust_repo.append_audit_event(audit)

        return new_identity
