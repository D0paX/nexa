# System Architecture Overview

## Current target architecture

```text
Local Network
      |
      v
NEXA Agent
  |   |   |
  |   |   +--> Device Observation
  |   +------> Trust Verification
  +----------> Event Generation
      |
      v
Persistence
      |
      v
Notification Service
      |
      v
FCM
      |
      v
NEXA Android Client
```

## Boundary rule

The detection engine must not depend directly on Android implementation details.

The Android application must not be responsible for deciding whether a device is trusted.

FCM is a transport adapter, not the trust authority.
