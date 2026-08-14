# Privacy and Data Classification

## Data classes

### Public
Repository documentation and non-sensitive architecture information.

### Operational
Device names, network metadata, timestamps, event summaries.

### Sensitive
Credentials, tokens, trust material, private keys, infrastructure secrets.

## Principles

- collect only data required for the feature
- avoid exporting raw network data unnecessarily
- protect sensitive values at rest and in transit
- document retention where persistence is introduced
- support deletion or cleanup procedures where appropriate

The project should not silently upload raw network traffic or unrelated personal content to cloud services.
