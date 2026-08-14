# NEXA

**Network Security & Device Trust**

NEXA is a security-focused home network platform designed to discover devices, evaluate device trust, generate security events, and deliver alerts to an authorized operator.

## Project status

NEXA is in **Phase 0: Foundation & Governance**.

The project roadmap is intentionally locked. Changes to scope, architecture, security model, or phase ordering must follow the documented governance and ADR process.

## Core principles

- Security before convenience.
- Deterministic controls before AI-assisted classification.
- Established cryptographic primitives before custom cryptography.
- Small, explicit modules before accidental monoliths.
- Evidence before trust.
- Tests before completion.
- Documentation before architectural drift.
- Human ownership of architecture; AI agents assist implementation.
- Detect first. Explain second. Act last.
- Product UI must remain professional, restrained, accessible, and free of cyberpunk styling.
- No emojis in product UI, source-code output, logs, documentation-as-product-content, or notification copy.

## Current committed scope

The current roadmap covers:

1. Foundation & Governance
2. Network Visibility
3. Device Identity & Trust
4. Security Event Engine
5. Notification Infrastructure
6. Android Security Client
7. Reliability & Security Hardening
8. Advanced Detection
9. Defensive Controls
10. Release & Portfolio Showcase

The web dashboard is **not part of the committed architecture**. A future dashboard may be added through the formal change process.

## Repository

The repository root is the project root. No additional nested `nexa/` root directory should be created.

## Documentation

See `docs/README.md` for the documentation system and `docs/roadmap/master-roadmap.md` for the locked roadmap.
