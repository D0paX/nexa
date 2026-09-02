package com.example.nexa.ui.alerts

import com.example.nexa.ui.common.ActivityEntry
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.DeliveryAttempt
import com.example.nexa.ui.common.DeliveryState
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.devices.Presence

/**
 * The operator-facing model of Phase 3 alerts.
 *
 * The distinction this model exists to protect:
 *
 *   [AlertLifecycle]  — what the operator or system has done with the alert
 *   [DeliveryState]   — what happened to the notification about the alert
 *
 * These are separate fields on separate axes and neither is ever derived
 * from the other. A notification that failed does not make the alert
 * "failed"; a notification that was delivered does not resolve anything.
 * Severity is a third, independent axis: how serious the event is, not what
 * has been done about it.
 */

// ============================================================
// SEVERITY — how serious the event is
// ============================================================

enum class AlertSeverity { Critical, Danger, Warning, Information }

// ============================================================
// LIFECYCLE — what has been done about the alert
// ============================================================

enum class AlertLifecycle {
    /** Raised, nobody has taken it. */
    New,

    /** An operator has seen it. Explicitly NOT resolved. */
    Acknowledged,

    /** The incident is closed. */
    Resolved,

    /** Deliberately set aside without being resolved. */
    Ignored;

    /** Whether the alert is still part of the active incident load. */
    val isOpen: Boolean get() = this == New || this == Acknowledged
}

// ============================================================
// NOTIFICATION DELIVERY — a separate lifecycle entirely
//
// DeliveryState and DeliveryAttempt now live in com.example.nexa.ui.common,
// shared with the notification center. One vocabulary, so "failed" cannot
// come to mean two things in two places.
// ============================================================

/**
 * The notification picture for one alert.
 *
 * Deliberately a separate type from the alert itself so that no screen can
 * accidentally render a delivery state where a lifecycle state belongs.
 */
data class DeliverySummary(
    val state: DeliveryState,
    val lastAttemptLabel: String,
    val attempts: List<DeliveryAttempt> = emptyList(),
    val detail: String? = null
)

// ============================================================
// TARGET — an alert does not always point at a device
// ============================================================

/** The cryptographic identity an alert's target carries, when it has one. */
data class AlertIdentityRef(
    val identityId: String,
    val trust: TrustState
)

/** Observed device context an alert refers to. */
data class AlertDeviceRef(
    val deviceId: String,
    val label: String,
    val mac: String,
    val ip: String?,
    val scope: String,
    val presence: Presence,
    val recordFreshness: DataFreshness,
    val lastObservedLabel: String
)

/**
 * What the alert is about.
 *
 * [DeviceTarget] carries an identity only when one actually exists — an
 * observed device is not assumed to have a cryptographic identity.
 */
sealed interface AlertTarget {
    data class DeviceTarget(
        val device: AlertDeviceRef,
        val identity: AlertIdentityRef?
    ) : AlertTarget

    /** The alert concerns a network scope rather than one device. */
    data class ScopeTarget(val scope: String) : AlertTarget

    /** NEXA cannot resolve what this alert refers to. */
    data object Unknown : AlertTarget
}

val AlertTarget.deviceRef: AlertDeviceRef?
    get() = (this as? AlertTarget.DeviceTarget)?.device

val AlertTarget.identityRef: AlertIdentityRef?
    get() = (this as? AlertTarget.DeviceTarget)?.identity

/** True when the observation behind the target is not current. */
val AlertTarget.isStale: Boolean
    get() = deviceRef?.recordFreshness?.let { it !is DataFreshness.Live } ?: false

// ============================================================
// ALERT
// ============================================================

data class AlertListItem(
    val id: String,
    val title: String,
    val severity: AlertSeverity,
    val lifecycle: AlertLifecycle,
    val delivery: DeliveryState,
    val target: AlertTarget,
    val createdLabel: String,
    val updatedLabel: String,
    /** Age in minutes — the deterministic recency key for ordering. */
    val ageMinutes: Int
)

data class AlertDetailData(
    val alert: AlertListItem,
    val description: String,
    val delivery: DeliverySummary,
    val timeline: List<ActivityEntry>,
    val actions: List<AlertAction>,
    val freshness: DataFreshness
)

// ============================================================
// ACTIONS
// ============================================================

enum class AlertActionKind {
    /** Phase 3 lifecycle: the operator has seen it. Does not resolve it. */
    Acknowledge,

    /** Phase 3 lifecycle: the incident is closed. */
    Resolve,

    /** Phase 3 lifecycle: set aside without resolving. */
    Ignore,

    /** Phase 4 enforcement — always routed through the confirmation flow. */
    QuarantineTarget,

    /** Phase 2 trust operation — also routed through the confirmation flow. */
    RequireReverification,

    /** Navigation only. */
    ViewDevice,
    ViewIdentity
}

/**
 * An action offered for an alert.
 *
 * [enforcement] marks the actions that must leave the client through the
 * Phase 4 confirmation path; [actionCode] is what that path receives.
 * Lifecycle actions carry no action code because they are Phase 3
 * operations on the alert, not enforcement requests.
 */
data class AlertAction(
    val kind: AlertActionKind,
    val label: String,
    val actionCode: String? = null,
    val enabled: Boolean = true,
    val disabledReason: String? = null,
    val enforcement: Boolean = false,
    val destructive: Boolean = false
)

/**
 * Which actions an alert's current state supports.
 *
 * Acknowledgement is offered only while the alert is new, resolution only
 * once it has been acknowledged — so the interface cannot suggest that
 * acknowledging closed the incident. A resolved or ignored alert offers no
 * enforcement response at all: responding to a closed incident should start
 * from the device, not from history.
 */
fun availableAlertActions(alert: AlertListItem): List<AlertAction> {
    val actions = mutableListOf<AlertAction>()

    when (alert.lifecycle) {
        AlertLifecycle.New -> {
            actions += AlertAction(AlertActionKind.Acknowledge, "Acknowledge")
            actions += AlertAction(AlertActionKind.Ignore, "Ignore")
        }
        AlertLifecycle.Acknowledged -> {
            actions += AlertAction(AlertActionKind.Resolve, "Resolve")
            actions += AlertAction(AlertActionKind.Ignore, "Ignore")
        }
        AlertLifecycle.Resolved, AlertLifecycle.Ignored -> Unit
    }

    val device = alert.target.deviceRef
    if (alert.lifecycle.isOpen && device != null) {
        actions += AlertAction(
            kind = AlertActionKind.QuarantineTarget,
            label = "Quarantine Target",
            actionCode = "QUARANTINE_DEVICE",
            enforcement = true,
            destructive = true
        )

        // Reverification requires an identity to reverify.
        val identity = alert.target.identityRef
        if (identity != null && identity.trust != TrustState.Revoked) {
            actions += AlertAction(
                kind = AlertActionKind.RequireReverification,
                label = "Require Reverification",
                actionCode = "REQUIRE_REVERIFICATION",
                enforcement = true
            )
        }
    }

    if (device != null) {
        actions += AlertAction(AlertActionKind.ViewDevice, "View Observed Device")
    }
    if (alert.target.identityRef != null) {
        actions += AlertAction(AlertActionKind.ViewIdentity, "View Identity")
    }

    return actions
}

// ============================================================
// QUERY / FILTER / SORT
// ============================================================

data class AlertFilters(
    val severity: Set<AlertSeverity> = emptySet(),
    val lifecycle: Set<AlertLifecycle> = emptySet(),
    val delivery: Set<DeliveryState> = emptySet(),
    val scopes: Set<String> = emptySet(),
    val onlyDeliveryFailures: Boolean = false
) {
    val isActive: Boolean
        get() = severity.isNotEmpty() || lifecycle.isNotEmpty() || delivery.isNotEmpty() ||
            scopes.isNotEmpty() || onlyDeliveryFailures

    val activeCount: Int
        get() = severity.size + lifecycle.size + delivery.size + scopes.size +
            (if (onlyDeliveryFailures) 1 else 0)
}

enum class AlertSort { Attention, Newest, Severity }

/** Which slice of the incident load is being viewed. */
enum class AlertScopeView { Open, History, All }

fun List<AlertListItem>.applyQuery(query: String): List<AlertListItem> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { alert ->
        alert.id.contains(q, ignoreCase = true) ||
            alert.title.contains(q, ignoreCase = true) ||
            (alert.target.deviceRef?.label?.contains(q, ignoreCase = true) == true) ||
            (alert.target.deviceRef?.mac?.contains(q, ignoreCase = true) == true) ||
            (alert.target.deviceRef?.ip?.contains(q, ignoreCase = true) == true) ||
            (alert.target.deviceRef?.scope?.contains(q, ignoreCase = true) == true) ||
            (alert.target.identityRef?.identityId?.contains(q, ignoreCase = true) == true)
    }
}

fun List<AlertListItem>.applyFilters(filters: AlertFilters): List<AlertListItem> = filter { alert ->
    (filters.severity.isEmpty() || alert.severity in filters.severity) &&
        (filters.lifecycle.isEmpty() || alert.lifecycle in filters.lifecycle) &&
        (filters.delivery.isEmpty() || alert.delivery in filters.delivery) &&
        (filters.scopes.isEmpty() || alert.target.deviceRef?.scope in filters.scopes) &&
        (!filters.onlyDeliveryFailures || alert.delivery.isFailure)
}

fun List<AlertListItem>.applyView(view: AlertScopeView): List<AlertListItem> = when (view) {
    AlertScopeView.Open -> filter { it.lifecycle.isOpen }
    AlertScopeView.History -> filter { !it.lifecycle.isOpen }
    AlertScopeView.All -> this
}

/**
 * How loudly an alert is asking for an operator. Lower sorts first.
 *
 * Openness dominates severity: a resolved critical is history, and history
 * never outranks a live incident. Within the open set, severity orders the
 * work, and unacknowledged outranks acknowledged at the same severity
 * because it has not been picked up yet.
 *
 * Recency is applied as a tie-break rather than folded into the rank, so an
 * old critical never outranks a newer one purely by being critical.
 */
fun attentionRank(alert: AlertListItem): Int {
    if (!alert.lifecycle.isOpen) {
        return if (alert.lifecycle == AlertLifecycle.Resolved) 90 else 95
    }
    val severityRank = when (alert.severity) {
        AlertSeverity.Critical -> 0
        AlertSeverity.Danger -> 10
        AlertSeverity.Warning -> 20
        AlertSeverity.Information -> 30
    }
    val lifecycleRank = if (alert.lifecycle == AlertLifecycle.New) 0 else 5
    return severityRank + lifecycleRank
}

private fun severityOrder(severity: AlertSeverity): Int = when (severity) {
    AlertSeverity.Critical -> 0
    AlertSeverity.Danger -> 1
    AlertSeverity.Warning -> 2
    AlertSeverity.Information -> 3
}

fun List<AlertListItem>.applySort(sort: AlertSort): List<AlertListItem> = when (sort) {
    // Rank first, then newest within the same rank.
    AlertSort.Attention -> sortedWith(compareBy({ attentionRank(it) }, { it.ageMinutes }, { it.id }))
    AlertSort.Newest -> sortedWith(compareBy({ it.ageMinutes }, { it.id }))
    AlertSort.Severity -> sortedWith(compareBy({ severityOrder(it.severity) }, { it.ageMinutes }, { it.id }))
}

fun List<AlertListItem>.resolve(
    query: String,
    filters: AlertFilters,
    sort: AlertSort,
    view: AlertScopeView
): List<AlertListItem> = applyView(view).applyQuery(query).applyFilters(filters).applySort(sort)

// ============================================================
// SUMMARY
// ============================================================

/**
 * Aggregate counts.
 *
 * [deliveryFailures] is counted separately from anything about the alerts
 * themselves — a delivery problem is an operational fact about
 * notifications, not a property of the incident load.
 */
data class AlertSummaryCounts(
    val open: Int,
    val critical: Int,
    val unacknowledged: Int,
    val deliveryFailures: Int
)

fun summarize(alerts: List<AlertListItem>): AlertSummaryCounts = AlertSummaryCounts(
    open = alerts.count { it.lifecycle.isOpen },
    critical = alerts.count { it.lifecycle.isOpen && it.severity == AlertSeverity.Critical },
    unacknowledged = alerts.count { it.lifecycle == AlertLifecycle.New },
    deliveryFailures = alerts.count { it.delivery.isFailure }
)

// ============================================================
// SCREEN STATE
// ============================================================

sealed interface AlertsUiState {
    data object Loading : AlertsUiState

    data class Content(
        val all: List<AlertListItem>,
        val visible: List<AlertListItem>,
        val summary: AlertSummaryCounts,
        val query: String,
        val filters: AlertFilters,
        val sort: AlertSort,
        val view: AlertScopeView,
        val freshness: DataFreshness,
        val degraded: Boolean,
        /** Cached alert state, no connection. Distinct from merely stale. */
        val offline: Boolean = false
    ) : AlertsUiState

    data object Offline : AlertsUiState

    /** Alert state could not be read — not the same as there being none. */
    data object Unavailable : AlertsUiState

    data class Error(val message: String) : AlertsUiState
}

sealed interface AlertDetailUiState {
    data object Loading : AlertDetailUiState
    data class Content(val data: AlertDetailData) : AlertDetailUiState
    data object Unavailable : AlertDetailUiState
    data class Error(val message: String) : AlertDetailUiState
}
