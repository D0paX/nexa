# Logging

## Goals

Logs should answer:

- what happened
- when
- where
- why the system took the action
- what correlation or event identifier is relevant

## Rules

- use structured logs
- never log secrets
- never log private keys
- redact tokens
- use severity levels consistently
- preserve useful context without dumping raw sensitive payloads

Security events and application diagnostics should be distinguishable.
