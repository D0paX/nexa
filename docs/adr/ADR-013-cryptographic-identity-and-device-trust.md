# ADR 013: Cryptographic Identity and Device Trust (Phase 2)

## Status

Accepted

## Context

Phase 1 established passive network observation (`DeviceRecord`) limited to observational L2/L3 heuristics. Phase 2 introduces an active verification overlay to establish cryptographic trust, requiring rigorous protocol boundaries to prevent spoofing, tracking, and DoS attacks. The logical identity mechanism must be stable across credential rotations and bounded strictly to explicit operator authorization.

## Decisions

### 1. Active Verification Boundary
Phase 2 introduces active verification. NEXA transmits bounded TCP/UDP challenges to established IPs. This explicitly separates active cryptographic trust from Phase 1 passive observation.

### 2. Logical Identity & Credential Separation
* **`TrustedDeviceIdentity`**: The stable, logical representation of an explicitly trusted endpoint.
* **`Credential`**: The current asymmetric public key in use.
* **`CredentialVersion`**: The monotonic version history of credentials bound to the logical identity.
A key rotation represents a new credential version bound to the same `TrustedDeviceIdentity`.

### 3. Trust States vs Credential States
* **TrustState**: `UNKNOWN` → `VERIFIED_UNTRUSTED` → `PENDING_ENROLLMENT` → `TRUSTED` (with a manual terminal path to `REVOKED`).
* **CredentialState**: `ACTIVE` (currently signing), `SUPERSEDED` (legitimately rotated), `REVOKED` (compromised or disabled).

### 4. Explicit Cryptographic Suite & Encoding
To eliminate all ambiguity across disparate device ecosystems, the cryptographic suite and byte encodings are strictly defined:
* **Algorithm**: Ed25519.
* **Canonicalization**: RFC 8785 JSON Canonicalization Scheme (JCS) producing UTF-8 bytes.
* **Encoding**: Nonce, Public Key, and Signatures are encoded using **base64url without padding**.
* **Fingerprint**: SHA-256 of the exact public key bytes, represented as hexadecimal.
* **Timestamps**: UTC RFC 3339.

### 5. Authenticated Bidirectional Challenge Protocol
To prevent a device from being exploited as an unauthenticated signing oracle, the protocol is explicitly bidirectional:
1. **NEXA → Device (Authenticated Challenge)**: NEXA signs the challenge envelope using its private key.
2. **Device Authentication**: The device verifies the signature, expiration, and ensures the `verifier_identity` matches its exclusively configured host.
3. **Device → NEXA (Authenticated Response)**: The device generates and transmits a canonical response:
```json
{
  "protocol_version": "1.0",
  "message_type": "identity_response",
  "challenge_id": "<uuid>",
  "credential_version": "<integer>",
  "fingerprint": "<sha256_hex_fingerprint>",
  "signature": "<base64url_unpadded_signature>"
}
```
**Signature Semantics**: The signature covers the RFC 8785 canonical bytes combining the exact `challenge_id`, `protocol_version`, `verifier_identity`, `purpose`, `nonce`, and `credential_version`. The verifier strictly rejects responses not matching an outstanding challenge.

### 6. Ephemeral Nonce Cache
Challenge nonces are **ephemeral protocol state**, distinctly separated from durable trust state. Nonces reside entirely in an in-memory bounded cache (not SQLite). Entries are single-use, naturally expire based on the verifier clock (`expires_at`), and respect a deterministic maximum outstanding bound to prevent memory exhaustion.

### 7. Verifier Bootstrap & Privacy
* **Bootstrap**: "Trust on First Use" (TOFU) is prohibited. The device learns the legitimate `verifier_identity` strictly through an out-of-band operator action (pre-provisioning, physical pairing code, or explicit fingerprint validation).
* **Privacy**: The device maintains one credential pair per NEXA verifier. It explicitly drops unauthenticated challenges or challenges from unknown verifiers to eliminate cross-network passive tracking. The same verifier can securely recognize the device across varying `NetworkScopes`.

### 8. Identity Concurrency & Relays
Verification proves private key possession (`verify_signature()`); it **does not prove** L2 physical proximity. 
* **Relays**: A live relay can forward a fresh challenge to a legitimate device.
* **Anomalies**: If NEXA concurrently validates the same `ACTIVE` credential from contradictory MACs, IPs, or scopes, it emits an `IDENTITY_CONCURRENCY_ANOMALY`. 

### 9. Separated Resource Budgets & Limits
Active verification introduces DoS surfaces. Strict bounds are enforced:
* **Enrollment Traffic**: Processed under a strictly separated handling budget to prevent saturation of routine active verification logic.
* **General Limits**: Max 1 verification/device/30s, max 50 concurrent verifications/scope, max 500 global concurrent challenges, max payload sizes (4 KB), timeout bounds (3s), and exponential failure backoff.

### 10. Trust Audit Events
A robust security audit trail is established in the `TrustRepository` (isolated from `DeviceRepository`), distinctly logging: `ENROLLMENT_REQUESTED`, `ENROLLMENT_APPROVED`, `VERIFICATION_SUCCEEDED`, `VERIFICATION_FAILED`, `KEY_ROTATED`, `CREDENTIAL_REVOKED`, `IDENTITY_REVOKED`, and `IDENTITY_CONCURRENCY_ANOMALY`.

## Consequences

* **Phase 1 Independence**: Untrusted endpoints ignore challenges; Phase 1 passive lifecycles remain structurally untouched.
* **Robust Security Posture**: Bidirectional authentication prevents signing oracles; strict encodings prevent malleability; resource limits prevent DoS.
* **Implementation Complexity**: Requires managing bidirectional Ed25519 signatures, RFC 8785 canonicalization, strict resource limiters (with separated enrollment budgets), discrete audit logs, and in-memory TTL caching routines.
