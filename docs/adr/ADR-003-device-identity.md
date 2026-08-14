# ADR-003: Device Identity

Status: Accepted for design direction

## Decision

A MAC address, IP address, hostname, or vendor label is not treated as sufficient cryptographic identity.

## Context

Network identifiers are observable and may be altered or duplicated.

## Consequences

NEXA will require a separate trust/enrollment mechanism and will preserve raw observations separately from the resulting identity decision.

The exact credential mechanism is defined in the trust-model ADR to be accepted during Phase 2 design.
