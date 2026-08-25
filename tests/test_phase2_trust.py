"""Integration tests for Phase 2 Cryptographic Trust."""

import uuid
from typing import Any, Tuple

import pytest
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from nexa.crypto.primitives import (
    base64url_encode,
    canonicalize,
    generate_key_pair,
    get_fingerprint,
    sign,
)
from nexa.domain.enrollment import EnrollmentService
from nexa.domain.nonce_cache import NonceCache
from nexa.domain.protocol import (
    AuthenticatedDeviceResponse,
    AuthenticatedKeyRotationRequest,
    ChallengePurpose,
    DeviceResponseEnvelope,
    KeyRotationRequestEnvelope,
)
from nexa.domain.rate_limit import RateLimiter
from nexa.domain.trust import (
    CredentialState,
    TrustAuditEventType,
    TrustedDeviceIdentity,
    TrustState,
)
from nexa.domain.trust_manager import TrustManager
from nexa.domain.verifier import IdentityVerifier, VerificationError
from nexa.persistence.sqlite_trust import SqliteTrustRepository


@pytest.fixture
def trust_repo(tmp_path: Any) -> SqliteTrustRepository:
    db_path = tmp_path / "trust.db"
    return SqliteTrustRepository(str(db_path))


@pytest.fixture
def verifier_keypair() -> Tuple[bytes, bytes]:
    priv, pub = generate_key_pair()
    return priv, pub


@pytest.fixture
def identity_verifier(
    trust_repo: SqliteTrustRepository, verifier_keypair: Tuple[bytes, bytes]
) -> IdentityVerifier:
    verifier_priv_bytes, verifier_pub_bytes = verifier_keypair
    verifier_fingerprint = get_fingerprint(verifier_pub_bytes)

    rate_limiter = RateLimiter(max_enrollment_budget=5)
    nonce_cache = NonceCache()

    return IdentityVerifier(
        trust_repo=trust_repo,
        rate_limiter=rate_limiter,
        nonce_cache=nonce_cache,
        verifier_private_key_bytes=verifier_priv_bytes,
        verifier_fingerprint=verifier_fingerprint,
    )


@pytest.fixture
def enrollment_service(
    trust_repo: SqliteTrustRepository, identity_verifier: IdentityVerifier
) -> EnrollmentService:
    return EnrollmentService(trust_repo, identity_verifier)


@pytest.fixture
def trust_manager(trust_repo: SqliteTrustRepository) -> TrustManager:
    return TrustManager(trust_repo)


def helper_enroll_device(
    enrollment_service: EnrollmentService,
    identity_verifier: IdentityVerifier,
    scope_id: str,
    device_id: str,
    dev_priv: bytes,
    dev_pub: bytes,
) -> TrustedDeviceIdentity:
    """Helper to request enrollment (enters PENDING_ENROLLMENT)."""
    chal = enrollment_service.initiate_enrollment(scope_id, device_id)
    dev_priv_key = Ed25519PrivateKey.from_private_bytes(
        dev_priv
    )  # unused but valid conceptually
    _ = dev_priv_key
    resp_payload = DeviceResponseEnvelope(
        protocol_version="1.0",
        message_type="identity_response",
        challenge_id=chal.payload.challenge_id,
        credential_version=1,
        device_fingerprint=get_fingerprint(dev_pub),
        verifier_identity=identity_verifier.verifier_fingerprint,
        purpose=ChallengePurpose.ENROLLMENT,
        nonce=chal.payload.nonce,
    )
    sig = sign(dev_priv, canonicalize(resp_payload.to_dict()))
    resp = AuthenticatedDeviceResponse(payload=resp_payload, signature=sig)

    return enrollment_service.submit_enrollment_request(
        scope_id, device_id, chal, resp, dev_pub
    )


def test_device_self_supplied_key_cannot_become_trusted_automatically(
    trust_repo: SqliteTrustRepository,
    identity_verifier: IdentityVerifier,
    enrollment_service: EnrollmentService,
) -> None:
    """
    1. Device self-supplied key cannot become TRUSTED automatically.
    2. Enters PENDING_ENROLLMENT only.
    """
    dev_priv, dev_pub = generate_key_pair()
    device_id = str(uuid.uuid4())
    identity = helper_enroll_device(
        enrollment_service, identity_verifier, "scope_A", device_id, dev_priv, dev_pub
    )

    assert identity.state == TrustState.PENDING_ENROLLMENT

    # Try verifying response, should fail because identity is not TRUSTED
    chal = identity_verifier.issue_challenge(
        "scope_A", device_id, ChallengePurpose.VERIFICATION, get_fingerprint(dev_pub)
    )
    payload = DeviceResponseEnvelope(
        protocol_version="1.0",
        message_type="identity_response",
        challenge_id=chal.payload.challenge_id,
        credential_version=1,
        device_fingerprint=get_fingerprint(dev_pub),
        verifier_identity=identity_verifier.verifier_fingerprint,
        purpose=ChallengePurpose.VERIFICATION,
        nonce=chal.payload.nonce,
    )
    sig = sign(dev_priv, canonicalize(payload.to_dict()))
    resp = AuthenticatedDeviceResponse(payload=payload, signature=sig)

    with pytest.raises(VerificationError, match="not trusted"):
        identity_verifier.verify_response("scope_A", chal, resp)


def test_correct_operator_fingerprint_produces_trusted(
    trust_repo: SqliteTrustRepository,
    identity_verifier: IdentityVerifier,
    enrollment_service: EnrollmentService,
) -> None:
    """
    3. Correct operator fingerprint approval produces TRUSTED.
    9. Audit event generated.
    """
    dev_priv, dev_pub = generate_key_pair()
    device_id = str(uuid.uuid4())
    identity = helper_enroll_device(
        enrollment_service, identity_verifier, "scope_A", device_id, dev_priv, dev_pub
    )

    trusted_identity = enrollment_service.approve_enrollment(
        str(identity.identity_id), get_fingerprint(dev_pub)
    )

    assert trusted_identity.state == TrustState.TRUSTED

    audits = trust_repo.get_audit_events(str(identity.identity_id))
    assert any(a.event_type == TrustAuditEventType.ENROLLMENT_APPROVED for a in audits)

    # Verification should now pass
    chal = identity_verifier.issue_challenge(
        "scope_A", device_id, ChallengePurpose.VERIFICATION, get_fingerprint(dev_pub)
    )
    payload = DeviceResponseEnvelope(
        protocol_version="1.0",
        message_type="identity_response",
        challenge_id=chal.payload.challenge_id,
        credential_version=1,
        device_fingerprint=get_fingerprint(dev_pub),
        verifier_identity=identity_verifier.verifier_fingerprint,
        purpose=ChallengePurpose.VERIFICATION,
        nonce=chal.payload.nonce,
    )
    sig = sign(dev_priv, canonicalize(payload.to_dict()))

    identity_verifier.verify_response(
        "scope_A", chal, AuthenticatedDeviceResponse(payload, sig)
    )


def test_wrong_operator_fingerprint_is_rejected(
    trust_repo: SqliteTrustRepository,
    identity_verifier: IdentityVerifier,
    enrollment_service: EnrollmentService,
) -> None:
    """4. Wrong operator fingerprint is rejected."""
    dev_priv, dev_pub = generate_key_pair()
    device_id = str(uuid.uuid4())
    identity = helper_enroll_device(
        enrollment_service, identity_verifier, "scope_A", device_id, dev_priv, dev_pub
    )

    with pytest.raises(VerificationError, match="Operator fingerprint does not match"):
        enrollment_service.approve_enrollment(
            str(identity.identity_id), "wrong_fingerprint_here"
        )


def test_modified_public_key_after_approval_is_rejected(
    trust_repo: SqliteTrustRepository,
    identity_verifier: IdentityVerifier,
    enrollment_service: EnrollmentService,
) -> None:
    """5. Modified public key after approval is rejected."""
    dev_priv, dev_pub = generate_key_pair()
    device_id = str(uuid.uuid4())
    identity = helper_enroll_device(
        enrollment_service, identity_verifier, "scope_A", device_id, dev_priv, dev_pub
    )
    enrollment_service.approve_enrollment(
        str(identity.identity_id), get_fingerprint(dev_pub)
    )

    # Attacker uses different key to forge proof
    atk_priv, atk_pub = generate_key_pair()
    chal = identity_verifier.issue_challenge(
        "scope_A", device_id, ChallengePurpose.VERIFICATION, get_fingerprint(dev_pub)
    )
    payload = DeviceResponseEnvelope(
        protocol_version="1.0",
        message_type="identity_response",
        challenge_id=chal.payload.challenge_id,
        credential_version=1,
        device_fingerprint=get_fingerprint(atk_pub),
        verifier_identity=identity_verifier.verifier_fingerprint,
        purpose=ChallengePurpose.VERIFICATION,
        nonce=chal.payload.nonce,
    )
    # Signs with attacker key, but tries to pass it off
    sig = sign(atk_priv, canonicalize(payload.to_dict()))
    with pytest.raises(VerificationError, match="Unknown credential fingerprint"):
        identity_verifier.verify_response(
            "scope_A", chal, AuthenticatedDeviceResponse(payload, sig)
        )


def test_duplicate_enrollment_rejected(
    trust_repo: SqliteTrustRepository,
    identity_verifier: IdentityVerifier,
    enrollment_service: EnrollmentService,
) -> None:
    """7. Duplicate enrollment attempts are rejected."""
    dev_priv, dev_pub = generate_key_pair()
    device_id = str(uuid.uuid4())
    helper_enroll_device(
        enrollment_service, identity_verifier, "scope_A", device_id, dev_priv, dev_pub
    )

    with pytest.raises(
        VerificationError, match="Credential fingerprint already enrolled"
    ):
        helper_enroll_device(
            enrollment_service,
            identity_verifier,
            "scope_B",
            device_id,
            dev_priv,
            dev_pub,
        )


def test_revoked_credential_cannot_be_re_enrolled(
    trust_repo: SqliteTrustRepository,
    identity_verifier: IdentityVerifier,
    enrollment_service: EnrollmentService,
    trust_manager: TrustManager,
) -> None:
    """8. Revoked credential cannot be re-enrolled."""
    dev_priv, dev_pub = generate_key_pair()
    device_id = str(uuid.uuid4())
    identity = helper_enroll_device(
        enrollment_service, identity_verifier, "scope_A", device_id, dev_priv, dev_pub
    )
    enrollment_service.approve_enrollment(
        str(identity.identity_id), get_fingerprint(dev_pub)
    )

    trust_manager.revoke_identity(str(identity.identity_id), "compromised")

    # Try enrolling again with same key
    with pytest.raises(
        VerificationError, match="Credential fingerprint already enrolled"
    ):
        helper_enroll_device(
            enrollment_service,
            identity_verifier,
            "scope_B",
            device_id,
            dev_priv,
            dev_pub,
        )


def test_anomaly_detection_concurrent_proofs(
    trust_repo: SqliteTrustRepository,
    identity_verifier: IdentityVerifier,
    enrollment_service: EnrollmentService,
) -> None:
    """Verify concurrent proofs from different scopes emit anomaly."""
    dev_priv, dev_pub = generate_key_pair()
    device_id = str(uuid.uuid4())

    identity = helper_enroll_device(
        enrollment_service, identity_verifier, "scope_A", device_id, dev_priv, dev_pub
    )
    enrollment_service.approve_enrollment(
        str(identity.identity_id), get_fingerprint(dev_pub)
    )

    # Proof 1 from Scope A
    chal1 = identity_verifier.issue_challenge(
        "scope_A", device_id, ChallengePurpose.VERIFICATION, get_fingerprint(dev_pub)
    )
    payload1 = DeviceResponseEnvelope(
        protocol_version="1.0",
        message_type="identity_response",
        challenge_id=chal1.payload.challenge_id,
        credential_version=1,
        device_fingerprint=get_fingerprint(dev_pub),
        verifier_identity=identity_verifier.verifier_fingerprint,
        purpose=ChallengePurpose.VERIFICATION,
        nonce=chal1.payload.nonce,
    )
    sig1 = sign(dev_priv, canonicalize(payload1.to_dict()))
    identity_verifier.verify_response(
        "scope_A", chal1, AuthenticatedDeviceResponse(payload1, sig1)
    )

    # Proof 2 from Scope B (simulating another endpoint using the same key)
    device_id_2 = str(uuid.uuid4())
    chal2 = identity_verifier.issue_challenge(
        "scope_B", device_id_2, ChallengePurpose.VERIFICATION, get_fingerprint(dev_pub)
    )
    payload2 = DeviceResponseEnvelope(
        protocol_version="1.0",
        message_type="identity_response",
        challenge_id=chal2.payload.challenge_id,
        credential_version=1,
        device_fingerprint=get_fingerprint(dev_pub),
        verifier_identity=identity_verifier.verifier_fingerprint,
        purpose=ChallengePurpose.VERIFICATION,
        nonce=chal2.payload.nonce,
    )
    sig2 = sign(dev_priv, canonicalize(payload2.to_dict()))
    identity_verifier.verify_response(
        "scope_B", chal2, AuthenticatedDeviceResponse(payload2, sig2)
    )

    # Check audits for anomaly
    audits = trust_repo.get_audit_events(str(identity.identity_id))
    anomaly_audits = [
        a
        for a in audits
        if a.event_type == TrustAuditEventType.IDENTITY_CONCURRENCY_ANOMALY
    ]
    assert len(anomaly_audits) == 1


def helper_setup_trusted_device(
    enrollment_service: EnrollmentService,
    identity_verifier: IdentityVerifier,
) -> Tuple[str, bytes, bytes, str]:
    """Helper to create a TRUSTED device identity."""
    dev_priv, dev_pub = generate_key_pair()
    device_id = str(uuid.uuid4())
    identity = helper_enroll_device(
        enrollment_service, identity_verifier, "scope_A", device_id, dev_priv, dev_pub
    )
    trusted_identity = enrollment_service.approve_enrollment(
        str(identity.identity_id), get_fingerprint(dev_pub)
    )
    return str(trusted_identity.identity_id), dev_priv, dev_pub, device_id


def test_rotation_valid_old_key_signature_succeeds(
    trust_repo: SqliteTrustRepository,
    identity_verifier: IdentityVerifier,
    enrollment_service: EnrollmentService,
    trust_manager: TrustManager,
) -> None:
    """1, 10, 11, 12, 13. Valid rotation succeeds and updates states."""
    identity_id, old_priv, old_pub, _ = helper_setup_trusted_device(
        enrollment_service, identity_verifier
    )
    old_fingerprint = get_fingerprint(old_pub)

    # 1. Generate new key
    new_priv, new_pub = generate_key_pair()
    new_fingerprint = get_fingerprint(new_pub)

    # 2. Create rotation request
    payload = KeyRotationRequestEnvelope(
        protocol_version="1.0",
        message_type="key_rotation_request",
        purpose=ChallengePurpose.KEY_ROTATION,
        identity_id=identity_id,
        old_credential_fingerprint=old_fingerprint,
        new_public_key_b64=base64url_encode(new_pub),
        rotation_version=2,
    )
    sig = sign(old_priv, canonicalize(payload.to_dict()))
    req = AuthenticatedKeyRotationRequest(payload, sig)

    # 3. Rotate
    trust_manager.verify_and_rotate_credential(req)

    # 4. Verify states
    identity = trust_repo.get_identity(identity_id)
    assert identity is not None
    assert identity.state == TrustState.TRUSTED

    old_cred = trust_repo.get_credential_by_fingerprint(old_fingerprint)
    assert old_cred is not None
    assert old_cred.state == CredentialState.SUPERSEDED

    new_cred = trust_repo.get_credential_by_fingerprint(new_fingerprint)
    assert new_cred is not None
    assert new_cred.state == CredentialState.ACTIVE
    assert new_cred.version == 2

    # 5. Verify audit event
    audits = trust_repo.get_audit_events(identity_id)
    assert any(a.event_type == TrustAuditEventType.KEY_ROTATED for a in audits)


def test_rotation_new_key_only_rejected(
    trust_repo: SqliteTrustRepository,
    identity_verifier: IdentityVerifier,
    enrollment_service: EnrollmentService,
    trust_manager: TrustManager,
) -> None:
    """2. New-key-only self-signature is rejected."""
    identity_id, old_priv, old_pub, _ = helper_setup_trusted_device(
        enrollment_service, identity_verifier
    )
    old_fingerprint = get_fingerprint(old_pub)

    new_priv, new_pub = generate_key_pair()

    payload = KeyRotationRequestEnvelope(
        protocol_version="1.0",
        message_type="key_rotation_request",
        purpose=ChallengePurpose.KEY_ROTATION,
        identity_id=identity_id,
        old_credential_fingerprint=old_fingerprint,
        new_public_key_b64=base64url_encode(new_pub),
        rotation_version=2,
    )
    # Signing with NEW key instead of OLD key
    sig = sign(new_priv, canonicalize(payload.to_dict()))
    req = AuthenticatedKeyRotationRequest(payload, sig)

    with pytest.raises(ValueError, match="Invalid signature"):
        trust_manager.verify_and_rotate_credential(req)


def test_rotation_wrong_old_key_rejected(
    trust_repo: SqliteTrustRepository,
    identity_verifier: IdentityVerifier,
    enrollment_service: EnrollmentService,
    trust_manager: TrustManager,
) -> None:
    """3. Wrong old key is rejected."""
    identity_id, _, old_pub, _ = helper_setup_trusted_device(
        enrollment_service, identity_verifier
    )
    old_fingerprint = get_fingerprint(old_pub)

    wrong_priv, _ = generate_key_pair()
    new_priv, new_pub = generate_key_pair()

    payload = KeyRotationRequestEnvelope(
        protocol_version="1.0",
        message_type="key_rotation_request",
        purpose=ChallengePurpose.KEY_ROTATION,
        identity_id=identity_id,
        old_credential_fingerprint=old_fingerprint,
        new_public_key_b64=base64url_encode(new_pub),
        rotation_version=2,
    )
    # Signing with completely unrelated key
    sig = sign(wrong_priv, canonicalize(payload.to_dict()))
    req = AuthenticatedKeyRotationRequest(payload, sig)

    with pytest.raises(ValueError, match="Invalid signature"):
        trust_manager.verify_and_rotate_credential(req)


def test_rotation_revoked_credential_rejected(
    trust_repo: SqliteTrustRepository,
    identity_verifier: IdentityVerifier,
    enrollment_service: EnrollmentService,
    trust_manager: TrustManager,
) -> None:
    """4. Revoked credential cannot rotate."""
    identity_id, old_priv, old_pub, _ = helper_setup_trusted_device(
        enrollment_service, identity_verifier
    )
    old_fingerprint = get_fingerprint(old_pub)

    trust_manager.revoke_identity(identity_id, "compromised")

    new_priv, new_pub = generate_key_pair()
    payload = KeyRotationRequestEnvelope(
        protocol_version="1.0",
        message_type="key_rotation_request",
        purpose=ChallengePurpose.KEY_ROTATION,
        identity_id=identity_id,
        old_credential_fingerprint=old_fingerprint,
        new_public_key_b64=base64url_encode(new_pub),
        rotation_version=2,
    )
    sig = sign(old_priv, canonicalize(payload.to_dict()))
    req = AuthenticatedKeyRotationRequest(payload, sig)

    with pytest.raises(
        ValueError, match="Cannot rotate credential for a revoked identity"
    ):
        trust_manager.verify_and_rotate_credential(req)


def test_rotation_superseded_credential_rejected(
    trust_repo: SqliteTrustRepository,
    identity_verifier: IdentityVerifier,
    enrollment_service: EnrollmentService,
    trust_manager: TrustManager,
) -> None:
    """5. SUPERSEDED credential cannot rotate."""
    identity_id, old_priv, old_pub, _ = helper_setup_trusted_device(
        enrollment_service, identity_verifier
    )
    old_fingerprint = get_fingerprint(old_pub)

    # First rotation
    new_priv, new_pub = generate_key_pair()
    payload1 = KeyRotationRequestEnvelope(
        protocol_version="1.0",
        message_type="key_rotation_request",
        purpose=ChallengePurpose.KEY_ROTATION,
        identity_id=identity_id,
        old_credential_fingerprint=old_fingerprint,
        new_public_key_b64=base64url_encode(new_pub),
        rotation_version=2,
    )
    sig1 = sign(old_priv, canonicalize(payload1.to_dict()))
    trust_manager.verify_and_rotate_credential(
        AuthenticatedKeyRotationRequest(payload1, sig1)
    )

    # Now attempt to rotate AGAIN using the SUPERSEDED old_priv
    newest_priv, newest_pub = generate_key_pair()
    payload2 = KeyRotationRequestEnvelope(
        protocol_version="1.0",
        message_type="key_rotation_request",
        purpose=ChallengePurpose.KEY_ROTATION,
        identity_id=identity_id,
        old_credential_fingerprint=old_fingerprint,
        new_public_key_b64=base64url_encode(newest_pub),
        rotation_version=3,
    )
    sig2 = sign(old_priv, canonicalize(payload2.to_dict()))

    with pytest.raises(ValueError, match="Old credential fingerprint mismatch"):
        trust_manager.verify_and_rotate_credential(
            AuthenticatedKeyRotationRequest(payload2, sig2)
        )


def test_rotation_version_must_be_strictly_greater(
    trust_repo: SqliteTrustRepository,
    identity_verifier: IdentityVerifier,
    enrollment_service: EnrollmentService,
    trust_manager: TrustManager,
) -> None:
    """7, 8, 9. Rotation version must be strictly greater (no rollback/downgrade)."""
    identity_id, old_priv, old_pub, _ = helper_setup_trusted_device(
        enrollment_service, identity_verifier
    )
    old_fingerprint = get_fingerprint(old_pub)

    new_priv, new_pub = generate_key_pair()

    # Attempt with version=1 (same as current)
    payload_dup = KeyRotationRequestEnvelope(
        protocol_version="1.0",
        message_type="key_rotation_request",
        purpose=ChallengePurpose.KEY_ROTATION,
        identity_id=identity_id,
        old_credential_fingerprint=old_fingerprint,
        new_public_key_b64=base64url_encode(new_pub),
        rotation_version=1,
    )
    sig_dup = sign(old_priv, canonicalize(payload_dup.to_dict()))
    with pytest.raises(ValueError, match="must be strictly greater"):
        trust_manager.verify_and_rotate_credential(
            AuthenticatedKeyRotationRequest(payload_dup, sig_dup)
        )

    # Attempt with version=0 (downgrade)
    payload_down = KeyRotationRequestEnvelope(
        protocol_version="1.0",
        message_type="key_rotation_request",
        purpose=ChallengePurpose.KEY_ROTATION,
        identity_id=identity_id,
        old_credential_fingerprint=old_fingerprint,
        new_public_key_b64=base64url_encode(new_pub),
        rotation_version=0,
    )
    sig_down = sign(old_priv, canonicalize(payload_down.to_dict()))
    with pytest.raises(ValueError, match="must be strictly greater"):
        trust_manager.verify_and_rotate_credential(
            AuthenticatedKeyRotationRequest(payload_down, sig_down)
        )
