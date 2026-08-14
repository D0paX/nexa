# Security Boundaries

## Trusted components

- protected NEXA configuration
- protected credentials
- authorized administrative actions
- cryptographic verification logic

## Semi-trusted inputs

- ARP observations
- hostnames
- IP addresses
- MAC addresses
- device-reported metadata
- network timing characteristics

## Untrusted inputs

Any network-originated data must be treated as untrusted until validated.

## Boundary rule

No untrusted observation may directly grant trust or privileged capability.
