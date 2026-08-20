# ADR 011: Device Lifecycle and Persistence Model (Phase 1E)

## Status

Accepted

## Context

Phase 1D implemented in-memory device correlation, transforming normalized `DeviceObservation` domain data into `DeviceRecord`s. To provide network visibility over time, NEXA requires a persistence mechanism and a defined lifecycle for these devices.

Phase 1E introduces durable persistence for `DeviceRecord`s and tracks historical state transitions (`LifecycleEvent`s) across application sessions. We must define the storage backend, repository boundary, lifecycle semantics, transaction scope, degraded persistence handling, and pruning strategy while strictly adhering to Phase 1 boundaries: no cryptographic trust, no arbitrary enrollment concepts, and no network state mutation.

## Terminology

* **Raw ARP packets**: Raw network material. Never persisted.
* **DeviceObservation**: Normalized domain observation. Ephemeral.
* **DeviceRecord**: Correlated durable state.
* **LifecycleEvent**: Historical state transition.

## Decisions

### 1. Persistence Backend: SQLite
NEXA will use standard local SQLite (`sqlite3`) as the persistence backend. As a single-process local daemon, SQLite provides file-based, zero-configuration durable storage with ACID guarantees. No external dependencies or ORMs are introduced.

### 2. Repository Abstraction and Restart Recovery
A strict `DeviceRepository` interface abstraction decouples the domain layer from `sqlite3`. The domain and lifecycle layers never interact with SQL. Restart recovery hydrates domain state strictly through this explicit application/domain interface. The repository never manipulates private `ObservationCorrelator` internals. Persisted opaque `device_id`s are reused across restarts without deriving them from MACs.

**Architectural Note (Phase 1E Correlator Extension)**: To support restart recovery, previously persisted `DeviceRecord` objects cross the application/domain boundary to hydrate the `ObservationCorrelator` via the explicit optional `initial_records` constructor input. This extension is backwards compatible because `initial_records` is optional. The SQLite backend remains safely behind `DeviceRepository`, and the correlator remains completely free from persistence knowledge.

### 3. Database Path
The database location is controlled via a deterministic configuration boundary: the `NEXA_DATA_DIR` environment variable. 
* The default development path is `~/.nexa/data/nexa.db`. 
* Production installations can explicitly configure paths such as `/var/lib/nexa/nexa.db`.
Hardcoded `.data` directories are rejected.

### 4. Scope Identity via Canonical Network CIDR
Scope isolation is preserved using a deterministic SHA-256 scope key derived from the canonical serialization of the `NetworkScope` domain object. 
* **Input**: The canonical Network CIDR (e.g., `192.168.1.0/24`) in UTF-8.
* **Generation**: `SHA-256("192.168.1.0/24")`.
Contextual properties like `interface_name` and `gateway` are intentionally excluded from the identity derivation to ensure a scope identity remains stable even if local interface naming changes.

### 5. Typed Lifecycle Events and Semantics
Lifecycle transitions use a strongly-typed classification:
* `FIRST_SEEN`
* `BECAME_PRESENT`
* `BECAME_UNSEEN`
* `CONFLICT_DETECTED`

**Semantics**:
* `PRESENT` is established exclusively by active observation.
* `UNSEEN` is established only when a lifecycle evaluation for the current `ScanContext` determines that an existing device was not observed in the batch.
* The mere passage of time or an application restart **does not** mark devices `UNSEEN`. The next successful lifecycle evaluation determines the next state.

### 6. Transaction Boundaries and Concurrency
Updates generated from a single scan batch (correlation, device state changes, conflicts, lifecycle events) must execute within a **single SQLite transaction**. Partial persistence is forbidden. SQLite will be configured in WAL mode with a busy timeout, adhering to NEXA's single-process writer model.

### 7. Persistence Failure Semantics
NEXA explicitly models persistence failure instead of silent data loss or unbounded divergence.
* If a repository transaction fails, it undergoes a `ROLLBACK`.
* The system enters a `PERSISTENCE_DEGRADED` state.
* The entire, complete scan transaction envelope is placed into an ordered **Pending Persistence Queue**. Strict causal ordering is preserved; multiple scans are not coalesced.
* In-memory observation continues.
* When persistence recovers, NEXA replays the transaction envelopes in their original order. Upon successful replay, the state returns to `PERSISTENCE_HEALTHY`.
* If the bounded queue reaches its maximum transaction count limit (e.g., 500 scan transactions), NEXA emits a high-severity persistence failure and silently stops accepting additional durable-history claims to prevent unbounded memory growth.

### 8. Retention and Pruning Policy
NEXA enforces an explicit 30-day operational retention policy (not a security guarantee):
* `PRESENT` devices are retained.
* `UNSEEN` devices are eligible for pruning exactly 30 days since their `last_observed_at`.
* `LifecycleEvent`s are retained for 30 days.
* Conflicts are retained as long as their associated device remains retained.

Pruning executes as a single, atomic SQLite transaction utilizing foreign-key constraints (cascade deletes) to guarantee no orphaned MAC rows, IP rows, conflict rows, or lifecycle event rows remain.

### 9. Security and Privacy
The persistence layer relies exclusively on parameterized SQL (`?`). The local database is treated as integrity-sensitive application state. No raw network packets, user credentials, or cryptographic secrets are persisted. 

### 10. Migrations
Migrations are handled internally using `PRAGMA user_version`. Schema version 1 defines the initial structure. Migrations execute deterministically from `N` to `N+1`. Invalid versions halt startup to protect data integrity.

## Consequences
- Operational stability is heavily prioritized via the `PERSISTENCE_DEGRADED` mode and transaction replay capabilities.
- Device identity (`device_id`) remains robustly isolated from MAC spoofing over time.
- Memory and disk footprint are formally bounded by the retention cutoff and the pending queue limit.
- Opaque correlation boundaries established in Phase 1D correctly survive reboots.
