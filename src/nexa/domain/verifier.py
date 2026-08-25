"""
Core Identity Verification Service for Phase 2.

Handles challenge issuance, response verification, rate limiting, and replay protection.
"""

import logging
import secrets
import threading
import uuid
from datetime import datetime, timedelta, timezone

from nexa.crypto.primitives import canonicalize, sign, verify_signature
from nexa.domain.nonce_cache import NonceCache
from nexa.domain.protocol import (
    AuthenticatedChallenge,
    AuthenticatedDeviceResponse,
    ChallengeEnvelope,
    ChallengePurpose,
)
from nexa.domain.rate_limit import RateLimiter
from nexa.domain.trust import (
    CredentialState,
    TrustAuditEvent,
    TrustAuditEventType,
    TrustState,
)
from nexa.domain.trust_lifecycle import TrustRepository

logger = logging.getLogger(__name__)


class VerificationError(Exception):
    """Base exception for verification failures."""

    pass


class IdentityVerifier:
    """
    Coordinates active verification of device identities.
    """

    def __init__(
        self,
        trust_repo: TrustRepository,
        rate_limiter: RateLimiter,
        nonce_cache: NonceCache,
        verifier_private_key_bytes: bytes,
        verifier_fingerprint: str,
        challenge_ttl_seconds: int = 60,
    ):
        self.trust_repo = trust_repo
        self.rate_limiter = rate_limiter
        self.nonce_cache = nonce_cache
        self.verifier_private_key_bytes = verifier_private_key_bytes
        self.verifier_fingerprint = verifier_fingerprint
        self.challenge_ttl = timedelta(seconds=challenge_ttl_seconds)

        self._recent_proofs: dict[str, tuple[str, datetime]] = {}
        self._proof_lock = threading.Lock()

    def issue_challenge(
        self,
        scope_id: str,
        device_id: str,
        purpose: ChallengePurpose,
        target_fingerprint: str | None = None,
    ) -> AuthenticatedChallenge:
        """
        Issues an authenticated challenge for a device.
        Enforces resource bounds and rate limits.
        """
        # Enforce rate limits
        if purpose == ChallengePurpose.ENROLLMENT:
            if not self.rate_limiter.try_acquire_enrollment():
                raise VerificationError("Enrollment rate limit exceeded.")
        else:
            if not self.rate_limiter.try_acquire_verification(scope_id, device_id):
                raise VerificationError("Verification rate limit exceeded.")

        try:
            challenge_id = str(uuid.uuid4())
            nonce = secrets.token_hex(32)
            now = datetime.now(timezone.utc)
            expires_at = now + self.challenge_ttl

            # Cache the nonce
            if not self.nonce_cache.add_nonce(nonce, expires_at):
                raise VerificationError("Global active challenges limit reached.")

            envelope = ChallengeEnvelope(
                protocol_version="1.0",
                message_type="challenge",
                verifier_identity=self.verifier_fingerprint,
                device_identity=target_fingerprint,
                purpose=purpose,
                challenge_id=challenge_id,
                nonce=nonce,
                issued_at=now,
                expires_at=expires_at,
            )

            # Sign the canonical envelope
            canonical_bytes = canonicalize(envelope.to_dict())
            signature = sign(self.verifier_private_key_bytes, canonical_bytes)

            return AuthenticatedChallenge(payload=envelope, signature=signature)

        except Exception:
            # Release limits on failure
            if purpose == ChallengePurpose.ENROLLMENT:
                self.rate_limiter.release_enrollment()
            else:
                self.rate_limiter.release_verification(scope_id)
            raise

    def verify_response(
        self,
        scope_id: str,
        challenge: AuthenticatedChallenge,
        response: AuthenticatedDeviceResponse,
    ) -> None:
        """
        Verifies a device's response against an outstanding challenge.
        Releases the rate limiter slot when done.
        """
        purpose = challenge.payload.purpose
        try:
            # 1. Match challenge bounds
            if response.payload.challenge_id != challenge.payload.challenge_id:
                raise VerificationError("Challenge ID mismatch.")
            if response.payload.nonce != challenge.payload.nonce:
                raise VerificationError("Nonce mismatch.")
            if response.payload.purpose != challenge.payload.purpose:
                raise VerificationError("Purpose mismatch.")
            if response.payload.verifier_identity != self.verifier_fingerprint:
                raise VerificationError("Verifier identity mismatch.")

            # 2. Check nonce cache to prevent replay
            if not self.nonce_cache.consume_nonce(response.payload.nonce):
                raise VerificationError("Nonce is invalid, expired, or already used.")

            # 3. Retrieve device credential
            fingerprint = response.payload.device_fingerprint
            credential = self.trust_repo.get_credential_by_fingerprint(fingerprint)
            if not credential:
                raise VerificationError(
                    f"Unknown credential fingerprint: {fingerprint}"
                )

            if credential.state != CredentialState.ACTIVE:
                raise VerificationError(
                    f"Credential is not active: {credential.state.value}"
                )

            if credential.version != response.payload.credential_version:
                raise VerificationError("Credential version mismatch.")

            # 4. Verify Identity State
            identity = self.trust_repo.get_identity(str(credential.identity_id))
            if not identity:
                raise VerificationError("Orphaned credential, no identity found.")

            if identity.state != TrustState.TRUSTED:
                raise VerificationError(
                    f"Identity is not trusted (State: {identity.state.value})."
                )

            # 5. Verify Cryptographic Signature
            canonical_response = canonicalize(response.payload.to_dict())
            is_valid = verify_signature(
                credential.public_key_bytes,
                response.signature,
                canonical_response,
            )
            if not is_valid:
                raise VerificationError("Invalid device signature.")

            # 6. Anomaly Detection for Concurrent Proofs
            now = datetime.now(timezone.utc)
            with self._proof_lock:
                last_proof = self._recent_proofs.get(fingerprint)
                if last_proof:
                    last_scope, last_time = last_proof
                    if (
                        last_scope != scope_id
                        and (now - last_time).total_seconds() < 30
                    ):
                        # Concurrent proof from different scope! Emit anomaly.
                        audit = TrustAuditEvent(
                            event_id=uuid.uuid4(),
                            identity_id=identity.identity_id,
                            event_type=TrustAuditEventType.IDENTITY_CONCURRENCY_ANOMALY,
                            timestamp=now,
                            details={
                                "reason": "concurrent_conflicting_proofs",
                                "scope_a": last_scope,
                                "scope_b": scope_id,
                            },
                        )
                        self.trust_repo.append_audit_event(audit)
                        logger.warning(
                            f"Concurrency Anomaly detected for {fingerprint}"
                        )
                self._recent_proofs[fingerprint] = (scope_id, now)

        finally:
            if purpose == ChallengePurpose.ENROLLMENT:
                self.rate_limiter.release_enrollment()
            else:
                self.rate_limiter.release_verification(scope_id)
