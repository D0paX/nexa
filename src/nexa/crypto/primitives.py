"""Cryptographic primitives for Phase 2 identity and trust."""

import base64
import hashlib
from datetime import datetime, timezone
from typing import Any

import jcs  # type: ignore
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ed25519


def base64url_encode(data: bytes) -> str:
    """Encode bytes to base64url without padding."""
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def base64url_decode(data: str) -> bytes:
    """Decode base64url without padding to bytes."""
    padding = "=" * (4 - (len(data) % 4))
    if padding == "====":
        padding = ""
    return base64.urlsafe_b64decode(data + padding)


def generate_key_pair() -> tuple[bytes, bytes]:
    """
    Generate an Ed25519 key pair.

    Returns:
        tuple[bytes, bytes]: (private_key_bytes, public_key_bytes) in raw format.
    """
    private_key = ed25519.Ed25519PrivateKey.generate()
    public_key = private_key.public_key()

    private_bytes = private_key.private_bytes(
        encoding=serialization.Encoding.Raw,
        format=serialization.PrivateFormat.Raw,
        encryption_algorithm=serialization.NoEncryption(),
    )
    public_bytes = public_key.public_bytes(
        encoding=serialization.Encoding.Raw,
        format=serialization.PublicFormat.Raw,
    )

    return private_bytes, public_bytes


def get_fingerprint(public_key_bytes: bytes) -> str:
    """Calculate the SHA-256 hex fingerprint of raw public key bytes."""
    return hashlib.sha256(public_key_bytes).hexdigest()


def canonicalize(data: dict[str, Any]) -> bytes:
    """
    Canonicalize a dictionary to RFC 8785 JSON Canonicalization Scheme (JCS) bytes.
    """
    canonical_bytes: bytes = jcs.canonicalize(data)
    return canonical_bytes


def sign(private_key_bytes: bytes, data: bytes) -> str:
    """
    Sign arbitrary bytes using Ed25519 and return base64url unpadded signature.
    """
    private_key = ed25519.Ed25519PrivateKey.from_private_bytes(private_key_bytes)
    signature = private_key.sign(data)
    return base64url_encode(signature)


def verify_signature(
    public_key_bytes: bytes, signature_b64url: str, data: bytes
) -> bool:
    """
    Verify an Ed25519 signature in base64url format against arbitrary bytes.
    """
    try:
        public_key = ed25519.Ed25519PublicKey.from_public_bytes(public_key_bytes)
        signature = base64url_decode(signature_b64url)
        public_key.verify(signature, data)
        return True
    except (InvalidSignature, ValueError):
        return False


def to_rfc3339(dt: datetime) -> str:
    """Convert a UTC datetime to RFC 3339 format (e.g., 2026-08-22T12:00:00Z)."""
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def from_rfc3339(dt_str: str) -> datetime:
    """Parse an RFC 3339 string to a UTC datetime."""
    if dt_str.endswith("Z"):
        dt_str = dt_str[:-1] + "+00:00"
    return datetime.fromisoformat(dt_str)
