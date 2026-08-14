# NEXA Master Roadmap

**Status: LOCKED**

This is the committed roadmap baseline. Work should follow the phases in order unless an explicit roadmap amendment is approved through governance.

## Phase 0 — Foundation & Governance
Establish repository structure, requirements, architecture, security boundaries, testing strategy, release rules, and AI-agent governance.

## Phase 1 — Network Visibility
Build reliable local-network interface detection, subnet discovery, ARP-based device observation, normalization, persistence, and device lifecycle tracking.

## Phase 2 — Device Identity & Trust
Define enrollment, identity, credentials, verification, rotation, revocation, and the cryptographic trust model.

## Phase 3 — Security Event Engine
Turn observations and trust decisions into deterministic, persistent security events with clear lifecycle semantics.

## Phase 4 — Notification Infrastructure
Deliver security events through a notification service with FCM as an adapter, including delivery tracking, retries, deduplication, and token lifecycle handling.

## Phase 5 — Android Security Client
Build the Android application for authenticated alert handling, notification presentation, audible alarm behavior, acknowledgement, device views, and secure configuration.

## Phase 6 — Reliability & Security Hardening
Harden failure handling, secrets, permissions, dependency supply chain, observability, recovery, and security testing.

## Phase 7 — Advanced Detection
Add explainable device fingerprinting and behavioral analysis while keeping deterministic controls as the security authority.

## Phase 8 — Defensive Controls
Introduce carefully bounded administrative mitigation features for authorized networks. Detection, explanation, operator confirmation, and safe rollback take priority over automation.

## Phase 9 — Release & Portfolio Showcase
Prepare public documentation, reproducible installation, demos, architecture diagrams, release notes, and a portfolio-quality end-to-end demonstration.

## Scope decision

A web dashboard is not part of the current committed roadmap. A future dashboard may be proposed through the feature and roadmap amendment process.

## Phase gate rule

A phase does not close merely because code exists. It closes when:

- acceptance criteria pass
- tests pass
- security review requirements are satisfied
- documentation is synchronized
- operational behavior is understood
- the phase exit checklist is approved
- outstanding risks are either resolved or formally accepted
