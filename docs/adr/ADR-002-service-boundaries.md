# ADR-002: Service Boundaries

Status: Accepted

## Decision

NEXA will separate network discovery, trust evaluation, security events, persistence, and notification adapters by responsibility.

## Context

The project needs to evolve without coupling the scanner to mobile delivery or UI concerns.

## Consequences

Modules may communicate through explicit domain contracts instead of reaching into each other's implementation details.

## Rejected alternative

A single script containing scanning, cryptography, persistence, and notification logic is explicitly rejected.
