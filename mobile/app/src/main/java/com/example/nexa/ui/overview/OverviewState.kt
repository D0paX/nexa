package com.example.nexa.ui.overview

import com.example.nexa.theme.NexaStatus

/**
 * The operator-facing state of the NEXA Security Command Center.
 *
 * These are UI types, deliberately domain-neutral: nothing here imports or
 * mirrors a backend model. When Phase 1-4 state is wired in, it is mapped
 * into these shapes at the edge, so the screen never depends on the
 * enforcement domain's internal structure.
 */

// ============================================================
// POSTURE
// ============================================================

/**
 * The single answer to "is the system healthy?".
 *
 * Never read off one counter — see [derivePosture] for the precedence rules
 * that produce it.
 */
enum class SecurityPosture {
    /** Enforcement is available and nothing is outstanding. */
    Secure,

    /** Enforcement is available and currently constraining at least one target. */
    Enforcing,

    /** NEXA is operating with reduced capability; results may be incomplete. */
    Degraded,

    /** Enforcement is globally halted — circuit breaker open. */
    Paused,

    /** Something needs an operator now. */
    Critical,

    /** NEXA cannot currently establish the state of the system. */
    Unknown
}

/** How current the displayed picture is. */
sealed interface DataFreshness {
    /** Confirmed just now. */
    data object Live : DataFreshness

    /** Real, but old enough that it should not be trusted for a decision. */
    data class Stale(val lastUpdatedLabel: String) : DataFreshness

    /** NEXA does not know how current this is. */
    data object Unknown : DataFreshness
}

// ============================================================
// ENFORCEMENT
// ============================================================

/** Phase 4 execution mode, surfaced so simulation is never mistaken for enforcement. */
enum class ExecutionMode { Enforce, AuditOnly }

/** Phase 4 enforcement circuit breaker. */
enum class CircuitBreakerState { Closed, Open, HalfOpen }

/**
 * What enforcement is actually doing right now.
 *
 * Counters are separate on purpose: "quarantined" is a steady-state fact,
 * while "failed" and "reconciliation" are conditions that need a human.
 */
data class EnforcementState(
    val enabled: Boolean,
    val circuitBreaker: CircuitBreakerState,
    val executionMode: ExecutionMode,
    val quarantinedDevices: Int,
    val pendingActions: Int,
    val failedActions: Int,
    val reconciliationIssues: Int
) {
    /** Enforcement can only actually run when enabled and not broken open. */
    val isOperational: Boolean
        get() = enabled && circuitBreaker != CircuitBreakerState.Open
}

// ============================================================
// SUMMARIES
// ============================================================

data class DeviceSummary(
    val active: Int,
    val online: Int,
    val offline: Int,
    val untrusted: Int,
    val quarantined: Int
)

data class AlertSummary(
    val total: Int,
    val critical: Int,
    val warning: Int,
    val unacknowledged: Int
)

// ============================================================
// ATTENTION
// ============================================================

/** Where an attention item sends the operator when tapped. */
sealed interface AttentionTarget {
    data class Alert(val id: String) : AttentionTarget
    data class Device(val mac: String) : AttentionTarget
    data object None : AttentionTarget
}

/**
 * One thing that may require operator action.
 *
 * [priority] is the sort key — lower sorts first. Informational events never
 * become attention items; only conditions a person may need to resolve.
 */
data class AttentionItem(
    val id: String,
    val title: String,
    val detail: String,
    val status: NexaStatus,
    val target: AttentionTarget,
    val priority: Int
)

// ============================================================
// ACTIVITY
// ============================================================

/** The kinds of security history the command center reports. */
enum class ActivityKind {
    AlertRaised,
    AlertAcknowledged,
    DeviceAppeared,
    TrustChanged,
    ReverificationRequired,
    EnforcementStarted,
    EnforcementCompleted,
    ReleaseCompleted,
    ActionFailed
}

/**
 * A single security event in recent history: what happened, to whom, when,
 * and what state it ended in.
 */
data class ActivityEntry(
    val id: String,
    val kind: ActivityKind,
    val title: String,
    val target: String,
    val timeAgo: String,
    val status: NexaStatus
)

// ============================================================
// SCREEN STATE
// ============================================================

/** Everything the command center renders when it has a picture to show. */
data class OverviewData(
    val posture: SecurityPosture,
    val postureDetail: String,
    val enforcement: EnforcementState,
    val attention: List<AttentionItem>,
    val devices: DeviceSummary,
    val alerts: AlertSummary,
    val activity: List<ActivityEntry>,
    val freshness: DataFreshness
)

/**
 * The states the screen can be in.
 *
 * [Offline] and [Unavailable] are distinct from an empty [Content]: absence
 * of data and absence of visibility are different security facts, and an
 * operator must never read a lost backend as "nothing is happening".
 */
sealed interface OverviewUiState {
    data object Loading : OverviewUiState

    data class Content(val data: OverviewData) : OverviewUiState

    /** The device has no connection and no usable cached picture. */
    data object Offline : OverviewUiState

    /** NEXA is reachable but cannot report system state. */
    data object Unavailable : OverviewUiState

    data class Error(val message: String) : OverviewUiState
}

// ============================================================
// DERIVATION — pure, testable, and the only place posture is decided
// ============================================================

/**
 * Resolves overall posture from the parts.
 *
 * Precedence is deliberate and ordered by how badly the operator needs to
 * know:
 *
 *  1. Unknown  — we cannot see the system, so we claim nothing.
 *  2. Critical — something needs a person now.
 *  3. Paused   — the circuit breaker has halted enforcement globally.
 *  4. Degraded — we are running, but not at full capability.
 *  5. Enforcing— healthy, and actively constraining something.
 *  6. Secure   — healthy, nothing outstanding.
 *
 * Paused deliberately outranks Enforcing: reporting ENFORCING while the
 * breaker is open would tell an operator the network is being protected
 * when it is not.
 */
fun derivePosture(
    enforcement: EnforcementState,
    alerts: AlertSummary,
    freshness: DataFreshness
): SecurityPosture = when {
    freshness == DataFreshness.Unknown -> SecurityPosture.Unknown

    alerts.critical > 0 || enforcement.failedActions > 0 -> SecurityPosture.Critical

    enforcement.circuitBreaker == CircuitBreakerState.Open -> SecurityPosture.Paused

    !enforcement.enabled ||
        enforcement.reconciliationIssues > 0 ||
        enforcement.circuitBreaker == CircuitBreakerState.HalfOpen -> SecurityPosture.Degraded

    enforcement.quarantinedDevices > 0 -> SecurityPosture.Enforcing

    else -> SecurityPosture.Secure
}

/**
 * Builds the prioritized attention list.
 *
 * Only conditions a person may need to resolve appear here — a quarantined
 * device that is behaving as intended is steady state, not an action item.
 * Results are sorted by [AttentionItem.priority].
 */
fun buildAttentionItems(
    enforcement: EnforcementState,
    alerts: AlertSummary,
    criticalAlerts: List<ActivityEntry> = emptyList()
): List<AttentionItem> {
    val items = mutableListOf<AttentionItem>()

    if (enforcement.failedActions > 0) {
        items += AttentionItem(
            id = "enforcement-failed",
            title = "Enforcement action failed",
            detail = "${enforcement.failedActions} action(s) did not complete. Target state is not confirmed.",
            status = NexaStatus.Critical,
            target = AttentionTarget.None,
            priority = 0
        )
    }

    criticalAlerts.take(2).forEachIndexed { index, entry ->
        items += AttentionItem(
            id = "alert-${entry.id}",
            title = entry.title,
            detail = entry.target,
            status = NexaStatus.Critical,
            target = AttentionTarget.Alert(entry.id),
            priority = 1 + index
        )
    }

    if (enforcement.circuitBreaker == CircuitBreakerState.Open) {
        items += AttentionItem(
            id = "circuit-breaker",
            title = "Enforcement paused",
            detail = "The circuit breaker is open. No enforcement action will execute until it closes.",
            status = NexaStatus.Paused,
            target = AttentionTarget.None,
            priority = 3
        )
    }

    if (enforcement.reconciliationIssues > 0) {
        items += AttentionItem(
            id = "reconciliation",
            title = "Reconciliation required",
            detail = "${enforcement.reconciliationIssues} binding(s) could not be reconciled after restart.",
            status = NexaStatus.Degraded,
            target = AttentionTarget.None,
            priority = 4
        )
    }

    if (enforcement.pendingActions > 0) {
        items += AttentionItem(
            id = "pending",
            title = "Action pending",
            detail = "${enforcement.pendingActions} action(s) awaiting execution.",
            status = NexaStatus.Information,
            target = AttentionTarget.None,
            priority = 5
        )
    }

    if (alerts.unacknowledged > 0 && alerts.critical == 0) {
        items += AttentionItem(
            id = "unacknowledged",
            title = "Unacknowledged alerts",
            detail = "${alerts.unacknowledged} alert(s) have not been acknowledged.",
            status = NexaStatus.Warning,
            target = AttentionTarget.None,
            priority = 6
        )
    }

    return items.sortedBy { it.priority }
}

/**
 * The sentence under the posture word.
 *
 * Wording is deliberately precise: ENFORCING states what NEXA is doing, not
 * that every target is safe, and Unknown never implies health.
 */
fun postureDetail(posture: SecurityPosture, enforcement: EnforcementState): String = when (posture) {
    SecurityPosture.Secure ->
        "Enforcement is available. No outstanding conditions."
    SecurityPosture.Enforcing ->
        "Enforcement is active on ${enforcement.quarantinedDevices} device(s). Other targets are unaffected."
    SecurityPosture.Degraded ->
        "NEXA is running with reduced capability. Enforcement results may be incomplete."
    SecurityPosture.Paused ->
        "Enforcement is halted by the circuit breaker. Existing rules remain in place."
    SecurityPosture.Critical ->
        "One or more conditions require operator attention now."
    SecurityPosture.Unknown ->
        "NEXA cannot confirm the current state of the system."
}
