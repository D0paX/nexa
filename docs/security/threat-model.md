# Threat Model

## Assets

- device trust credentials
- cryptographic keys
- configuration secrets
- FCM credentials and registration tokens
- device inventory
- security event history
- administrative actions

## Threat actors

- unauthorized local network client
- malicious local device
- compromised trusted device
- attacker spoofing observed identifiers
- compromised host or credential
- malicious or vulnerable dependency

## Threat categories

Evaluate:

- spoofing
- tampering
- repudiation
- information disclosure
- denial of service
- elevation of privilege

## Key assumptions

- the operator controls or is authorized to administer the monitored network
- the Linux VM host is part of the trusted computing base
- physical compromise of the host is outside the initial threat model
- the router firmware is not modified by NEXA

Threat-model changes require updates to this document and related ADRs where architecture changes.
