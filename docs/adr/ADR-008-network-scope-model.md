# ADR-008: Network Scope Model and Safety Bounds

## Status

Accepted

## Context

Phase 1B requires taking the OS-derived network information (`NetworkContext` from Phase 1A) and normalizing it into a canonical observation target (`NetworkScope`). 

NEXA must guarantee that its observation mechanisms (like the future Phase 1C ARP engine) operate within safe, deterministic bounds. We cannot blindly trust OS-reported network masks (e.g., a misconfigured `/0` or `/8` could cause the application to attempt scanning millions of addresses, causing resource exhaustion or triggering IDS/IPS alerts). 

We need a dedicated, immutable domain model to represent the network scope, independent of how it was discovered, which enforces maximum safe sizes and consistency rules before any active or passive observation takes place.

## Decision

We will introduce a `NetworkScope` domain model that mathematically derives the canonical observation boundaries using Python's standard `ipaddress` module.

**Key constraints and derivations:**
- **Maximum Scope Size:** The model will strictly reject prefixes smaller than `/16` (i.e., more than 65,534 hosts) to prevent accidental runaway observation.
- **Minimum Scope Size:** The model must handle up to `/31` gracefully (though typical LANs are `/24`).
- **Consistency Validation:** It will ensure the local interface IP and gateway (if present) are mathematically within the calculated network bounds.
- **Immutability:** The object will be a frozen `@dataclass` containing exactly: `network_address`, `broadcast_address`, `prefix_length`, `host_count`, `first_usable_host`, `last_usable_host`, `gateway`, and `interface_name`.
- **Pure Python:** The model will have zero dependencies on OS tools (no `iproute2`), no `scapy`, and no network I/O.

## Alternatives Considered

1. **Pass `NetworkContext` directly to Phase 1C**
   - *Rejected.* `NetworkContext` represents what the OS *believes* the configuration is. It lacks explicit bounding, maximum-size safety rails, and convenience fields (like `first_usable_host`) needed by observers.
2. **Dynamic bounds checking inside the ARP engine**
   - *Rejected.* Safety bounds should be enforced universally at the domain layer, not duplicated in individual observation engines.
3. **Configurable maximum prefix length**
   - *Rejected for now.* A hardcoded limit of `/16` is sufficient for initial deployment. If a user genuinely needs to observe a `/8` LAN, they have a highly unusual environment that can be addressed via configuration in a future phase.

## Consequences

### Security Implications
- Prevents "scope explosion" by capping observation at a `/16`.
- Ensures mathematical consistency, preventing spoofed or malformed gateways from causing out-of-bounds traffic generation in future phases.

### Operational Implications
- Rejects environments with intentionally massive subnets (e.g., `/8`), requiring future configuration overrides if those are valid targets.
- Provides a clean, well-defined interface for Phase 1C, isolating network logic from OS discovery.

### Relationship to Phase 1A
`NetworkScope` is strictly downstream of Phase 1A's `NetworkContext`. Phase 1A discovers what is configured; Phase 1B determines if it's safe and mathematically models it.

### Relationship to Phase 1C
`NetworkScope` is the sole input contract for Phase 1C. The ARP engine will iterate over or monitor the boundaries defined by `first_usable_host` and `last_usable_host` without knowing how they were derived.
