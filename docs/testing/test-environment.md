# Test Environment

## Principles

Real network behavior should be tested in controlled environments.

Tests that need an actual LAN should be explicit and opt-in rather than silently touching an uncontrolled network.

## Environments

- unit: isolated
- integration: controlled dependencies
- network integration: authorized test network
- end-to-end: controlled NEXA stack and authorized Android device

Test credentials must never be production credentials.
