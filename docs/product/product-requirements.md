# Product Requirements

## Primary user outcome

The operator should receive a timely, trustworthy indication when a device appears on the monitored network that NEXA cannot establish as trusted.

## Functional requirements

- discover devices visible on the monitored network
- normalize device observations
- retain device history
- support explicit device enrollment
- verify device trust
- generate security events
- notify the authorized operator
- present alert state in the Android client
- support acknowledgement and operational recovery

## Quality requirements

- correctness over cleverness
- deterministic security decisions
- auditable event history
- safe failure behavior
- reproducible development environment
- testable interfaces
- minimal secret exposure

## Future extensibility

The design must allow future additions without requiring a rewrite of the core detection and event pipeline.
