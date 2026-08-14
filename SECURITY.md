# Security Policy

## Scope

NEXA is intended for defensive security monitoring of networks the operator owns or is explicitly authorized to administer.

## Reporting vulnerabilities

Do not publish sensitive vulnerability details in a public issue before coordinated review.

For a private disclosure, use the repository's configured private security reporting mechanism when available. Include:

- affected component
- affected version or commit
- reproduction steps
- security impact
- mitigations or workarounds
- relevant logs with secrets removed

## Security expectations

NEXA treats the following as security-sensitive:

- device credentials
- cryptographic keys
- FCM credentials and registration tokens
- database credentials
- configuration secrets
- trust decisions
- security event records

Secrets must never be committed to source control, test fixtures, screenshots, logs, or sample configuration.

See:

- `docs/security/threat-model.md`
- `docs/security/trust-model.md`
- `docs/security/cryptography.md`
- `docs/security/secrets-management.md`
- `docs/security/vulnerability-management.md`
