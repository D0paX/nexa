# Supply Chain Security

## Dependency policy

- prefer maintained packages
- minimize dependency count
- pin or constrain versions intentionally
- review transitive dependencies for security-sensitive components
- use lock files where supported
- monitor known vulnerabilities
- record reasons for unusual dependencies

## Build integrity

CI should verify dependency installation and reproducible checks for the supported environment where practical.
