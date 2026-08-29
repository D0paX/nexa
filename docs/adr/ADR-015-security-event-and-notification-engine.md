# ADR 015: Security Event and Notification Engine (Phase 3)

## Status

Accepted

## Context

Phase 1 established passive device observability and lifecycle tracking. Phase 2 established cryptographic identity and explicit device trust. Phase 3 introduces the capability to process anomalies and transitions from these preceding phases, evaluate them against security rules, aggregate them to prevent noise, and alert operators via Firebase Cloud Messaging (FCM). 

## Decisions

### 1. Durability Guarantees and Compaction
The system prevents unintentional event loss via a **Transactional Outbox**. During normal operation, raw `SecurityEvent`s are preserved. However, during an extreme network event storm, the system may intentionally perform **Overload Compaction**. 
- **Normal:** Raw events preserved.
- **High-Water (e.g., > 1000 events/min):** Compaction engages. Raw events are aggregated into `AggregatedSecurityEvent`s, preserving `event_class`, `time_range`, affected identity/device references, `NetworkScope`, `count`, `severity`, and `aggregation reason`.
- **Critical Saturation:** Maximize compaction; unique/high-severity events are prioritized over granular timestamps but never silently discarded.

### 2. Transactional Outbox Boundary
Phase 1/2 state changes and `SecurityEvent` persistence occur atomically. An **Application Transaction Coordinator** ensures that the domain state change (in `DeviceRepository` or `TrustRepository`) and the event outbox entry (in `AlertRepository` or shared schema) commit synchronously within the same SQLite transaction.

### 3. State Separation
We strictly separate the **Alert Lifecycle** (operator workflow) from the **Notification Delivery Lifecycle** (network transport).

**Alert States:**
* `NEW`: Unread anomaly.
* `ACKNOWLEDGED`: Operator is investigating.
* `RESOLVED`: Operator confirmed and closed.
* `IGNORED`: Operator dismissed as benign.

**Notification Delivery States:**
* `QUEUED`: Accepted into durable notification queue.
* `IN_FLIGHT`: HTTPS payload pending to FCM.
* `ACCEPTED`: FCM returned HTTP 200 OK (accepted for delivery, NOT confirmed delivered to client).
* `FAILED`: Terminal delivery rejection (e.g., 400 Bad Request).
* `RETRYING`: Transient error (e.g., 503 Server Error), exponential backoff active.
* `EXHAUSTED`: Maximum retry bounds exceeded.

### 4. Alert Correlation and Rule Evaluation
Processing is explicitly decoupled into three pipeline stages:
1. **Normalization**: `LifecycleEvent`s and `TrustAuditEvent`s are mapped to a uniform `SecurityEvent`.
2. **Rule Evaluation**: Deterministic checks evaluate whether the event merits an alert.
3. **Aggregation / Deduplication**: Events are grouped deterministically. Matches within a rolling time window increment the `event_count` of an existing `Alert` rather than creating duplicates.

### 5. FCM Semantics and Idempotency
Firebase HTTP v1 does not provide robust exactly-once primitives, and `collapse_key` is strictly for message replacement, not reliable deduplication. Therefore, NEXA provides **at-least-once** delivery attempts. NEXA generates a unique `notification_id` embedded in the payload. The client application is responsible for **idempotent consumption** using this `notification_id`. 

### 6. FCM Payload Safety
The `NotificationPayload` strictly contains the minimum required context: `notification_id`, alert reference, severity, and a human-safe summary. It explicitly excludes private keys, credentials, raw cryptographic material, unnecessary MAC/IP info, and PII.

### 7. Failure Semantics
* **Crash during Phase 1/2:** Transaction rolls back; no `SecurityEvent` generated.
* **Crash before aggregation:** Sweeper resumes processing un-acked `SecurityEvent`s.
* **FCM Outage/503:** Notification transitions to `RETRYING`; alert remains correct.
* **Duplicate Dispatch:** Client relies on `notification_id` for idempotency.

## Consequences

* **Resilience:** Events are preserved across arbitrary process restarts and prolonged network partitions.
* **Clarity:** Operators manage alerts, while the system manages notifications.
* **Complexity:** Requires implementing application-level transactions and outbox sweepers.
