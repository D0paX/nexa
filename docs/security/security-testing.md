# Security Testing

Security testing is a required part of feature completion for security-sensitive changes.

## Required categories

- input validation
- authentication/authorization behavior
- credential handling
- replay/tampering tests
- malformed data tests
- privilege-boundary tests
- dependency/security checks
- secrets scanning

## High-risk changes

Cryptography, trust decisions, packet manipulation, active mitigation, and administrative command execution require explicit security review before acceptance.
