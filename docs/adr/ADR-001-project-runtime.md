# ADR-001: Project Runtime

Status: Accepted

## Decision

NEXA uses Python 3.12 as the initial Python runtime.

## Context

The development environment currently provides Python 3.12 for this project. Reproducibility is more important than chasing the newest interpreter.

## Consequences

- project configuration must declare Python 3.12
- dependencies must be validated against Python 3.12
- CI should test the declared runtime
- runtime upgrades require deliberate validation

## Future change

A later runtime upgrade requires compatibility testing and an ADR update if it materially changes support policy.
