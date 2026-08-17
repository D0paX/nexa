# Phase 1 — Network Visibility

Status: Accepted

## Objective

Build reliable local-network interface detection, subnet discovery, ARP-based device observation, normalization, persistence, and device lifecycle tracking. This phase establishes the core observational capabilities of NEXA.

## Scope

The complete Phase 1 structure is as follows:

* **1A Environment & Network Interface Discovery** (Currently active)
* **1B Subnet & Gateway Discovery** (Planned)
* **1C ARP Observation Engine** (Planned)
* **1D Device Observation Model** (Planned)
* **1E Device Lifecycle & Persistence** (Planned)
* **1F Reliability & Testing** (Planned)
* **1G Phase 1 Exit / Verification** (Planned)

## Non-goals

* Packet injection
* Network state changes or MITM behavior
* Cryptographic identity verification (Phase 2)
* Threat detection (Phase 3)
* Notification handling (Phase 4)

## Dependencies

* Linux `iproute2` package for network interface observation (Phase 1A).
* Future phases may require specific packet inspection libraries (e.g., Scapy) explicitly authorized via ADR.

## Architecture

NEXA strictly separates low-level network adapter discovery from the core domain objects. Observational data is normalized into a `NetworkContext` domain model, preventing operating-system specifics or CLI command artifacts from polluting the business logic.

## Security considerations

All network observation must be passive. Subprocess executions targeting Linux `iproute2` must guarantee shell safety (e.g., `shell=False` in Python) to prevent command injection. Untrusted operating-system data must be explicitly validated during discovery before populating domain models.

## Deliverables

* `NetworkContext` domain model
* Linux-specific `iproute2` network discovery adapter
* Subnet boundary calculation logic
* Passive ARP observation engine
* Device presence tracking and history

## Test strategy

* Comprehensive unit tests mocking shell boundaries and system environments.
* Integration tests conditionally enabled on Linux systems via explicit environment gate (`NEXA_RUN_NETWORK_INTEGRATION=1`).

## Observability

Discovery operations should emit structured logs containing normalized context (interface names, derived networks) but explicitly omit sensitive operator tokens, credentials, or arbitrary raw shell outputs unless tightly controlled.

## Acceptance criteria

* All sub-phases (1A-1G) complete.
* Observational operations work safely in the targeted Linux environment.
* No destructive packet injection or spoofing capabilities exist.

## Phase exit gate

Phase 1 cannot exit until security review, test passage, and explicit roadmap alignment verify that observational boundaries are respected and that the Phase 1 Exit Checklist is fulfilled.

## Risks

* **System Parsing Fragility:** Varying Linux distributions may output different metadata in `iproute2`. Structured JSON output (`ip -j`) is prioritized.
* **Complex Interface Configurations:** Virtual adapters, VPNs, and Docker bridges can complicate interface selection. The primary default gateway route will be the definitive selection mechanism.

## Follow-up work

Transitioning the normalized device state to Phase 2 (Device Identity & Trust) where deterministic trust anchors are established.
