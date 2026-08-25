"""
Cryptographic module for Phase 2 identity and trust.

Provides Ed25519 signatures, RFC 8785 canonicalization, and strict byte encodings.
"""

from nexa.crypto.primitives import (
    base64url_decode,
    base64url_encode,
    canonicalize,
    from_rfc3339,
    generate_key_pair,
    get_fingerprint,
    sign,
    to_rfc3339,
    verify_signature,
)

__all__ = [
    "base64url_decode",
    "base64url_encode",
    "canonicalize",
    "from_rfc3339",
    "generate_key_pair",
    "get_fingerprint",
    "sign",
    "to_rfc3339",
    "verify_signature",
]
