# ADR-012: Phase 1F Reliability & Testing Design

## Status

Accepted

## Context

Phase 1 established the core capabilities of NEXA (Network Visibility): environment discovery (1A), scope boundaries (1B), ARP observation (1C), correlation (1D), and persistence (1E). Before proceeding to Phase 2 (Identity & Trust), we must ensure that the foundational observational architecture is robust, deterministic, and gracefully degrades under failure.

Phase 1F is a dedicated reliability and testing phase. It focuses entirely on validating the failure boundaries, performance limits, and security constraints defined in previous ADRs without expanding the product footprint.

## Decisions

### 1. Deterministic Domain Testing
We will rely on deterministic unit tests for the domain space. We will strictly validate invariants such as the `/16` maximum scope size, `initial_records` restart continuity, and opaque `device_id` uniqueness. 

*Property-based testing is not required for Phase 1F. Deterministic boundary and invariant tests are sufficient for the current architecture. Hypothesis may be evaluated later if the state/input space becomes materially more complex.*

### 2. Exception Contracts
Existing public exception types (e.g., `NetworkDiscoveryError`) must be preserved and actively asserted against failures (e.g., malformed `ip` output or timeouts). Tests must not rely on generic `ValueError` exceptions if a domain-specific error was defined for that failure.

### 3. Subprocess Security Validation
Phase 1A executes system subprocesses. Testing must explicitly verify that `subprocess.run(...)` is invoked with `shell=False`, a defined timeout, literal argument lists, and informational-only commands. The test must accurately reflect the existing implementation (using `subprocess.run`) rather than mocking theoretical `subprocess.Popen` pathways merely for test convenience.

### 4. ARP Retry and Batching Semantics
Phase 1C targets must strictly adhere to the batch constraint (default 64) and the global timeout (60 seconds). Retries must be strictly limited to the mathematical difference of `(current batch targets) - (successfully answered targets)`. We must deterministically verify that already-responsive hosts are not re-queried during retry cycles. Malformed responses must be safely ignored without raising fatal exceptions.

### 5. Scope Isolation and Deduplication
Phase 1D correlation must deduplicate strictly on the `(IP, MAC)` tuple within a given `NetworkScope`. Identical MAC addresses seen across different network scopes must remain un-correlated to prevent false cross-network identity merging. IP collisions must be mapped to deterministic `ObservationConflict` events.

### 6. Causal Persistence and Recovery
Phase 1E must gracefully degrade during simulated I/O errors (e.g., lock busy timeouts or transaction rollback). Failed persistence events will enter an ordered Pending Persistence Queue (FIFO). Queue saturation (500 items max) must be tested to ensure the system rejects further history without unbounded memory growth. Recovery must replay transactions causally. 30-day pruning must be tested via cascade constraint execution.

### 7. Security and Privacy Boundaries
Tests must capture and verify logger emissions to prove that raw device observation data (MAC/IP mappings) are not inappropriately leaked into standard application logs. Sensitive device details must not leak through unhandled error paths.

## Consequences

- The test suite will be expanded significantly across all components (`tests/unit` and `tests/integration`).
- Code coverage will naturally increase without arbitrary coverage-percentage gates.
- Future phases (Phase 2) can rely on a mathematically bounded, secure observation engine that fails securely under load.

## Acceptance Criteria
- All identified 1A-1E reliability gaps have deterministic tests.
- Ruff, mypy, format, and pytest pass.
- Persistence failure/recovery paths, ARP timeout/retry invariants, scope bounds, and security invariants are all verified.
- No new unapproved dependencies are introduced.
