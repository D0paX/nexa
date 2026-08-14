# Credential Lifecycle

Every device credential should have an explicit lifecycle:

```text
Provision
 -> Enroll
 -> Activate
 -> Verify
 -> Rotate
 -> Revoke
 -> Retire
```

## Requirements

- unique device identity
- explicit enrollment
- secure storage
- rotation procedure
- revocation procedure
- recovery procedure
- auditability

Credential lifecycle behavior must be tested independently from network scanning.
