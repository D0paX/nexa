# Secrets Management

## Secrets include

- database credentials
- FCM service credentials
- device trust secrets
- signing keys
- tokens
- CI credentials

## Rules

- use environment variables or a dedicated secret store for deployment secrets
- never commit secrets
- never put secrets in documentation examples
- redact secrets from logs
- use separate credentials per environment
- rotate leaked credentials immediately

## Local development

Provide safe example configuration files containing placeholders only.
