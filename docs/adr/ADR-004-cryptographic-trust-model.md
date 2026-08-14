# ADR-004: Cryptographic Trust Model

Status: Proposed

## Decision target

NEXA will use established cryptographic primitives through maintained libraries. NEXA will define its device trust protocol rather than invent new cryptographic primitives.

## Candidate approaches

- HMAC-based challenge/response
- public-key signatures such as Ed25519
- device-bound credential enrollment

## Acceptance criteria

The selected approach must address:

- authenticity
- tamper resistance
- replay resistance where applicable
- credential rotation
- revocation
- secret storage
- recovery
- testability

The final decision requires a dedicated security review before Phase 2 implementation.
