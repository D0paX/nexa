# Cryptography Policy

## Rules

- Use well-established primitives and maintained libraries.
- Do not implement cryptographic primitives manually.
- Separate protocol design from primitive implementation.
- Use cryptographically secure randomness.
- Protect keys at rest.
- Minimize credential lifetime where practical.
- Support rotation and revocation where the protocol requires them.
- Never log secrets, private keys, shared secrets, or bearer tokens.

## Verification

Cryptographic code requires deterministic tests, negative tests, and security review before acceptance.

## Candidate primitive families

The project may evaluate HMAC and Ed25519-style public-key signatures where appropriate. The final protocol is an architectural/security decision, not an implementation detail.
