# Configuration

Configuration must be explicit, validated, and environment-aware.

## Rules

- no secrets in source
- validate startup configuration
- fail clearly on invalid required configuration
- document defaults
- distinguish development and deployment configuration
- avoid hidden configuration precedence

Every new setting should document its purpose, type, default, sensitivity, and restart requirements.
