"""State machine and lifecycle boundaries for cryptographic trust."""

from datetime import datetime, timezone
from typing import List, Protocol

from nexa.domain.trust import (
    Credential,
    CredentialState,
    TrustAuditEvent,
    TrustedDeviceIdentity,
    TrustState,
)


class TrustTransitionError(Exception):
    """Raised when an invalid state transition is attempted."""


class TrustLifecycle:
    """Explicit state machine for identity and credential lifecycle."""

    @staticmethod
    def transition_trust_state(
        identity: TrustedDeviceIdentity, new_state: TrustState
    ) -> TrustedDeviceIdentity:
        """
        Transition the TrustState of a logical identity.

        Allowed transitions:
        TrustState.UNKNOWN: {
            TrustState.VERIFIED_UNTRUSTED,
            TrustState.PENDING_ENROLLMENT
        },
        TrustState.VERIFIED_UNTRUSTED: {TrustState.PENDING_ENROLLMENT},
        TrustState.PENDING_ENROLLMENT: {TrustState.TRUSTED, TrustState.REVOKED},
        TrustState.TRUSTED: {TrustState.REVOKED},
        """
        if identity.state == new_state:
            return identity

        if identity.state == TrustState.REVOKED:
            raise TrustTransitionError("REVOKED is a terminal state.")

        valid = False
        if (
            identity.state == TrustState.UNKNOWN
            and new_state == TrustState.VERIFIED_UNTRUSTED
        ):
            valid = True
        elif (
            identity.state == TrustState.VERIFIED_UNTRUSTED
            and new_state == TrustState.PENDING_ENROLLMENT
        ):
            valid = True
        elif (
            identity.state == TrustState.UNKNOWN
            and new_state == TrustState.PENDING_ENROLLMENT
        ):
            valid = True
        elif (
            identity.state == TrustState.PENDING_ENROLLMENT
            and new_state == TrustState.TRUSTED
        ):
            valid = True
        elif (
            identity.state == TrustState.PENDING_ENROLLMENT
            and new_state == TrustState.REVOKED
        ):
            valid = True
        elif identity.state == TrustState.TRUSTED and new_state == TrustState.REVOKED:
            valid = True

        if not valid:
            raise TrustTransitionError(
                f"Invalid trust transition: {identity.state.value} -> {new_state.value}"
            )

        return TrustedDeviceIdentity(
            identity_id=identity.identity_id,
            state=new_state,
            created_at=identity.created_at,
            updated_at=datetime.now(timezone.utc),
        )

    @staticmethod
    def transition_credential_state(
        credential: Credential, new_state: CredentialState
    ) -> Credential:
        """
        Transition the CredentialState.

        Allowed:
        - ACTIVE -> SUPERSEDED
        - ACTIVE -> REVOKED
        - SUPERSEDED -> REVOKED
        """
        if credential.state == new_state:
            return credential

        if credential.state == CredentialState.REVOKED:
            raise TrustTransitionError("REVOKED is a terminal credential state.")

        valid = False
        if (
            credential.state == CredentialState.ACTIVE
            and new_state == CredentialState.SUPERSEDED
        ):
            valid = True
        elif (
            credential.state == CredentialState.ACTIVE
            and new_state == CredentialState.REVOKED
        ):
            valid = True
        elif (
            credential.state == CredentialState.SUPERSEDED
            and new_state == CredentialState.REVOKED
        ):
            valid = True

        if not valid:
            raise TrustTransitionError(
                f"Invalid credential transition: "
                f"{credential.state.value} -> {new_state.value}"
            )

        return Credential(
            identity_id=credential.identity_id,
            public_key_bytes=credential.public_key_bytes,
            fingerprint_sha256=credential.fingerprint_sha256,
            version=credential.version,
            state=new_state,
            created_at=credential.created_at,
            updated_at=datetime.now(timezone.utc),
        )


class TrustRepository(Protocol):
    """
    Abstract persistence boundary for Trust and Identity data.
    Separated from DeviceRepository.
    """

    def save_identity(self, identity: TrustedDeviceIdentity) -> None:
        """Persist a TrustedDeviceIdentity."""
        ...

    def get_identity(self, identity_id: str) -> TrustedDeviceIdentity | None:
        """Retrieve a TrustedDeviceIdentity."""
        ...

    def save_credential(self, credential: Credential) -> None:
        """Persist a Credential."""
        ...

    def get_credential_by_fingerprint(self, fingerprint: str) -> Credential | None:
        """Retrieve a Credential by its SHA-256 fingerprint."""
        ...

    def link_device_to_identity(self, device_id: str, identity_id: str) -> None:
        """
        Link an ephemeral DeviceRecord UUID to a persistent TrustedDeviceIdentity UUID.
        """
        ...

    def get_identity_for_device(self, device_id: str) -> str | None:
        """Retrieve the associated identity_id for a device_id, if any."""
        ...

    def get_active_credential_for_identity(self, identity_id: str) -> Credential | None:
        """Retrieve the currently ACTIVE credential for a given identity."""
        ...

    def append_audit_event(self, event: TrustAuditEvent) -> None:
        """Persist an immutable audit event."""
        ...

    def get_audit_events(self, identity_id: str) -> List[TrustAuditEvent]:
        """Retrieve audit events for an identity."""
        ...
