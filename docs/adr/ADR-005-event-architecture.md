# ADR-005: Event Architecture

Status: Accepted

## Decision

NEXA will represent meaningful security state changes as explicit domain events.

## Consequences

Events can be persisted, audited, notified, replayed for testing, and consumed by future integrations without coupling the detector to a specific transport.
