# Component Architecture

The initial architecture should separate responsibilities into explicit modules.

## Core areas

- network discovery
- observation normalization
- device registry
- identity and trust
- event engine
- persistence
- notification service
- configuration
- logging/observability

## Dependency direction

Low-level network observation should not import notification or UI concerns.

Security decisions should operate on explicit domain models rather than raw framework objects.

Adapters should wrap external systems such as FCM, databases, and platform APIs.
