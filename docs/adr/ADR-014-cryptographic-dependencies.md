# ADR 014: Cryptographic Dependencies

## Status

Accepted

## Context

Phase 2 requires Ed25519 cryptographic primitives, SHA-256 fingerprinting, and RFC 8785 JSON Canonicalization Scheme (JCS). Python's standard library provides `hashlib` for SHA-256 but lacks native support for Ed25519 and RFC 8785 JCS. We must introduce explicit dependencies to satisfy the strict cryptographic suite defined in ADR 013.

## Decisions

### 1. `cryptography` for Ed25519
We will adopt the `cryptography` package (e.g., `cryptography>=42.0.0`). 
**Rationale**: `cryptography` is the industry-standard, broadly audited Python cryptographic library. It explicitly supports Ed25519 key generation, signing, and verification natively through OpenSSL bindings, avoiding the risks of rolling our own pure-Python ECC implementation or relying on less-maintained packages.

### 2. `jcs` for RFC 8785
We will adopt the `jcs` package (e.g., `jcs>=0.2.1`).
**Rationale**: Implementing RFC 8785 correctly from scratch is error-prone due to strict float serialization (ES6 Number.toString() semantics) and UTF-16 surrogate pairing rules. The `jcs` package is a dedicated, compliant RFC 8785 implementation in Python that ensures cross-platform canonicalization consistency.

## Consequences

* **Increased Footprint**: Adds native binary dependencies (`cryptography`) to the project, which may impact build times and cross-compilation for embedded environments if any.
* **Security Confidence**: Reduces risk of canonicalization bugs or side-channel cryptographic attacks compared to bespoke implementations.
