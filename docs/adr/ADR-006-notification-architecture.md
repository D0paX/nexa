# ADR-006: Notification Architecture

Status: Accepted

## Decision

Firebase Cloud Messaging is an adapter behind a notification service boundary.

## Consequences

The security event engine remains independent of FCM. Additional channels can be added later without rewriting event-generation logic.
