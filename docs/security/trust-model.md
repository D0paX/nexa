# Device Trust Model

## Trust states

At minimum:

- Unknown
- Enrolled
- Trusted
- Verification Failed
- Revoked

## Trust principles

- observation is not trust
- identity is not merely a network address
- credentials must have lifecycle management
- verification decisions must be explainable
- revocation must take effect deterministically
- trust failures must not silently degrade into trust

## Enrollment

Enrollment is an explicit administrative action.

## Revocation

Revocation invalidates a device's trust relationship without destroying the historical audit record.
