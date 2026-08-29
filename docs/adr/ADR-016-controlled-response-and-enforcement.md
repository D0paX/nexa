# ADR-016: Controlled Response & Enforcement Architecture

## Status
Proposed (Revised for Final Security Architecture)

## Context
Phase 4 translates alerts and operator decisions into controlled, authorized network state changes. This revision finalizes the architectural details concerning operator authorization, privileged boundary confinement, adapter inspection, and strict state reconciliation.

## Decisions

### Target Snapshot
Target resolution immediately before enforcement uses a `TargetSnapshot` containing the `TrustedDeviceIdentity`, current `DeviceRecord`, `NetworkScope`, observed IP/MAC, cryptographic freshness, authorization context, and `action_id`. MAC or IP is never treated as cryptographic identity.

### Target Race Mitigation
We implement pre-flight revalidation immediately before enforcement. The residual race (IP changing between final validation and OS application) is mitigated by freshness bounds and fail-safe firewall structures applying only to managed sets.

### Quarantine Semantics & Policy Ownership
`QUARANTINE_DEVICE` explicitly permits verifier, DNS, DHCP, and gateway communication while denying all else. This logical `QuarantinePolicy` is owned outside the adapter. The adapter merely translates an already-authorized `EnforcementPlan` into structural rules; it does not independently decide traffic permits.

### Nftables State Model
Phase 4 manages a dedicated `table inet nexa` with predefined chains and managed sets (e.g., `set quarantine_targets`). Actions atomically manage entries in these sets. The table is never entirely flushed for idempotency to protect cross-device state isolation.

### Operator Authorization
Phase 4 consumes an already-authenticated operator context from a future Management Plane. Operator approval uses `operator_id` as the persistent reference and is uniquely bound by the authorization persistence model. It binds the `action_id`, `TrustedDeviceIdentity`, capability, policy, `operator_id`, and timestamp immutably in the persistence layer. We do not introduce a separate operator PKI in Phase 4. Operator authorization remains strictly separated from Phase 2 device credentials.

### Privileged Nftables Boundary
The privileged boundary must be strictly confined:
- `shell=False` is mandatory.
- Arguments must be literal executable paths and parameters.
- Bounded timeouts must be enforced.
- No shell interpolation, no arbitrary `nft` syntax, no include directives, and no flush ruleset commands are permitted.
Only NEXA-generated, validated `EnforcementPlan`s may reach the privileged adapter.

### Adapter Inspection Contract
The adapter abstraction conceptually supports `apply()`, `release()`, and `inspect()`. The `inspect()` method parses the current OS nftables state and returns it to the domain as a normalized representation. The domain never receives raw subprocess output.

### Crash Reconciliation Rules
When recovering a crashed process, `EXECUTING` states transition to `RECONCILING`. The system uses the adapter's `inspect()` contract to read actual managed state:
- If action intended `QUARANTINED` and state is `QUARANTINED` -> `SUCCEEDED`.
- If action intended `QUARANTINED` but state is `NORMAL` -> `FAILED` (or retried safely).
- If action intended `NORMAL` but state is `QUARANTINED` -> `ROLLED_BACK`.
The system will never blindly repeat potentially destructive operations.

### Security Principal Separation
Phase 4 strictly separates logical entities:
`Device cryptographic identity ≠ Operator identity ≠ Authorization decision ≠ Action execution ≠ Firewall state`.
No layer may silently substitute one for another.

### Phase 2 Reverification Boundary
Phase 4 interacts with Phase 2 through a narrow interface conceptually like `ReverificationRequest`. Phase 4 requests this, and Phase 2 manages its own trust/session lifecycle boundary to invalidate the current verification session. Phase 4 never modifies `TrustState`, `CredentialState`, or enrollments directly.

### Safety Modes
Phase 4 introduces explicit execution modes: `AUDIT_ONLY` and `ENFORCEMENT_ENABLED`. `AUDIT_ONLY` evaluates authorization, constructs plans, and simulates execution without mutating the firewall. New installations strictly default to `AUDIT_ONLY`. Global enforcement disable always overrides scope-level enablement.

### Enforcement Circuit Breaker
A global safety circuit breaker triggers `ENFORCEMENT_PAUSED` if action failure rates, queue saturation, or rollback failures exceed thresholds. Global OFF overrides scope policies and requires manual operator review to clear.

## Consequences
- Eliminates target mapping race conditions.
- Confines privileged operations to tightly controlled paths preventing command injection.
- Defines clear boundaries between authorization, trust, operator intent, and execution state.
