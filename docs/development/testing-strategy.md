# Testing Strategy

## Layers

1. unit tests
2. component tests
3. integration tests
4. security tests
5. end-to-end tests

## Test principles

- test behavior, not implementation accidents
- include negative paths
- test failure modes
- keep deterministic tests fast
- isolate network-dependent tests
- provide safe test doubles for external services

Security-critical logic requires both positive and negative verification.
