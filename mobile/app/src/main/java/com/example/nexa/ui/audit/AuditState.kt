package com.example.nexa.ui.audit

import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode

/**
 * The operator-facing model of NEXA's security history.
 *
 * Audit answers what happened, when, to what, and what state it reached. It is
 * not a second alerts screen and not a log dump: every entry is a typed
 * historical fact drawn from a specific phase of the system, and the type is
 * preserved rather than flattened into a generic "event".
 *
 * The separations this model exists to hold apart — the same ones the rest of
 * the product holds apart, now in the past tense:
 *
 *   Security event      is not an alert
 *   Alert lifecycle     is not notification delivery
 *   Trust change        is not enforcement
 *   Action execution    is not reconciliation
 *   A simulated action  is not an action
 *
 * Nothing here carries a summary written by its source. Headlines and
 * explanations are derived from the type and the execution mode (see
 * AuditPresentation), so no data source — preview or future backend — can
 * hand the interface a sentence that quietly drops the word "simulated".
 */

// ============================================================
// CATEGORIES
// ============================================================

/**
 * The families of history NEXA records.
 *
 * Only families that actual Phase 1-4 event data produces. There is no
 * "Reverification" family: a reverification request is a Phase 2 trust
 * operation and is recorded as one, so putting it in a family of its own would
 * suggest a subsystem that does not exist.
 */
enum class AuditCategory {
    /** Phase 1 network observation. */
    Device,

    /** Phase 2 cryptographic identity and trust lifecycle. */
    Trust,

    /** Phase 3 alert lifecycle. */
    Alert,

    /** Phase 3 notification delivery. Never alert state. */
    Notification,

    /** Phase 4 ActionRequest / ActionExecution lifecycle. */
    Action,

    /** Phase 4 EnforcementBinding state and reconciliation. */
    Enforcement,

    /** Phase 4 subsystem state — the circuit breaker. */
    System
}

// ============================================================
// EVENT TYPES
// ============================================================

/**
 * The historical event vocabulary.
 *
 * Each value is something Phase 1-4 actually produces. Nothing here is a
 * convenience type invented to make a timeline look fuller.
 */
enum class AuditEventType {
    // --- Phase 1: observation ---
    DeviceObserved,
    DeviceAddressChanged,

    // --- Phase 2: identity and trust ---
    IdentityCreated,
    VerificationCompleted,
    TrustChanged,
    ReverificationRequested,
    CredentialSuperseded,
    IdentityRevoked,

    // --- Phase 3: alert lifecycle ---
    AlertRaised,
    AlertAcknowledged,
    AlertResolved,
    AlertIgnored,

    // --- Phase 3: notification delivery ---
    NotificationSent,
    NotificationDelivered,
    NotificationRetrying,
    NotificationFailed,

    // --- Phase 4: action lifecycle ---
    ActionRequested,
    ActionAuthorized,
    ActionDenied,
    ActionExecuting,
    ActionReconciled,
    ActionSucceeded,
    ActionFailed,
    ActionOutcomeUnknown,
    RollbackRequested,
    RollbackCompleted,
    RollbackFailed,

    // --- Phase 4: enforcement bindings ---
    EnforcementBindingCreated,
    EnforcementBindingRemoved,
    CrashReconciliationCompleted,

    // --- Phase 4: circuit breaker ---
    CircuitBreakerOpened,
    CircuitBreakerClosed
}

val AuditEventType.category: AuditCategory
    get() = when (this) {
        AuditEventType.DeviceObserved,
        AuditEventType.DeviceAddressChanged -> AuditCategory.Device

        AuditEventType.IdentityCreated,
        AuditEventType.VerificationCompleted,
        AuditEventType.TrustChanged,
        AuditEventType.ReverificationRequested,
        AuditEventType.CredentialSuperseded,
        AuditEventType.IdentityRevoked -> AuditCategory.Trust

        AuditEventType.AlertRaised,
        AuditEventType.AlertAcknowledged,
        AuditEventType.AlertResolved,
        AuditEventType.AlertIgnored -> AuditCategory.Alert

        AuditEventType.NotificationSent,
        AuditEventType.NotificationDelivered,
        AuditEventType.NotificationRetrying,
        AuditEventType.NotificationFailed -> AuditCategory.Notification

        AuditEventType.ActionRequested,
        AuditEventType.ActionAuthorized,
        AuditEventType.ActionDenied,
        AuditEventType.ActionExecuting,
        AuditEventType.ActionReconciled,
        AuditEventType.ActionSucceeded,
        AuditEventType.ActionFailed,
        AuditEventType.ActionOutcomeUnknown,
        AuditEventType.RollbackRequested,
        AuditEventType.RollbackCompleted,
        AuditEventType.RollbackFailed -> AuditCategory.Action

        AuditEventType.EnforcementBindingCreated,
        AuditEventType.EnforcementBindingRemoved,
        AuditEventType.CrashReconciliationCompleted -> AuditCategory.Enforcement

        AuditEventType.CircuitBreakerOpened,
        AuditEventType.CircuitBreakerClosed -> AuditCategory.System
    }

/**
 * Whether the event type, by its own definition, asserts that real firewall
 * state changed.
 *
 * This is the structural half of the simulation guarantee. A reconciliation
 * compares intent against actual nftables state, and a binding is a rule that
 * exists in the kernel — neither can be produced by a run that mutates
 * nothing. An AUDIT_ONLY entry of one of these types is not a wording problem
 * to be fixed with a badge; it is incoherent data, and [isCoherent] rejects
 * it.
 */
val AuditEventType.assertsFirewallMutation: Boolean
    get() = when (this) {
        AuditEventType.ActionReconciled,
        AuditEventType.EnforcementBindingCreated,
        AuditEventType.EnforcementBindingRemoved,
        AuditEventType.CrashReconciliationCompleted -> true
        else -> false
    }

/** Whether the type belongs to an execution and therefore carries a mode. */
val AuditEventType.isExecution: Boolean
    get() = category == AuditCategory.Action || category == AuditCategory.Enforcement

// ============================================================
// OUTCOME
// ============================================================

/**
 * What state the event reached.
 *
 * [Unknown] is a real outcome and is never resolved into one of the others.
 * An action whose result NEXA could not determine stays undetermined forever;
 * history does not get more certain with age.
 */
enum class AuditOutcome {
    Succeeded,
    Failed,
    Pending,

    /** The event records something that happened, with no success axis. */
    Informational,

    Unknown
}

// ============================================================
// TARGET
// ============================================================

/**
 * What the event was about.
 *
 * [scope] travels with a device because the same MAC in another NetworkScope
 * is a different logical target — the same rule the enforcement flow follows.
 * An address is context, never identity.
 */
sealed interface AuditTarget {
    data class Device(
        val deviceId: String,
        val label: String,
        val mac: String,
        val ip: String?,
        val scope: String
    ) : AuditTarget

    data class Identity(
        val identityId: String,
        val label: String,
        val scope: String?
    ) : AuditTarget

    /** The event concerns a network scope rather than one target. */
    data class Scope(val scope: String) : AuditTarget

    /** The event concerns a NEXA subsystem, not a device or identity. */
    data class Subsystem(val name: String) : AuditTarget

    /** NEXA cannot resolve what this event referred to. */
    data object Unresolved : AuditTarget
}

val AuditTarget.displayLabel: String
    get() = when (this) {
        is AuditTarget.Device -> label
        is AuditTarget.Identity -> label
        is AuditTarget.Scope -> scope
        is AuditTarget.Subsystem -> name
        AuditTarget.Unresolved -> "Target unresolved"
    }

/** The identifier an operator would check. Null when there is none to show. */
val AuditTarget.technical: String?
    get() = when (this) {
        is AuditTarget.Device -> mac
        is AuditTarget.Identity -> identityId
        is AuditTarget.Scope -> scope
        is AuditTarget.Subsystem -> null
        AuditTarget.Unresolved -> null
    }

val AuditTarget.scopeOrNull: String?
    get() = when (this) {
        is AuditTarget.Device -> scope
        is AuditTarget.Identity -> scope
        is AuditTarget.Scope -> scope
        else -> null
    }

// ============================================================
// SOURCE
// ============================================================

/**
 * Which subsystem produced the record.
 *
 * Kept so an entry that originated from an alert can say so without becoming
 * an alert: source is provenance, not type.
 */
enum class AuditSource {
    Observation,
    TrustService,
    SecurityEvent,
    Alert,
    NotificationService,
    ActionPipeline,
    EnforcementSubsystem
}

// ============================================================
// LINKS
// ============================================================

/**
 * Where an entry can take the operator.
 *
 * Deliberately no link into the action confirmation flow. That screen exists
 * to request an execution, and routing a historical record into it would put
 * an operator one tap from re-running something they were only reading about.
 * History links to the things it refers to; it does not offer to repeat them.
 */
sealed interface AuditLink {
    data class Alert(val alertId: String) : AuditLink
    data class Device(val mac: String) : AuditLink
    data class Identity(val identityId: String) : AuditLink
}

// ============================================================
// ENTRY
// ============================================================

/**
 * One historical security event.
 *
 * [executionMode] is null when the event is not an execution at all — an alert
 * being raised, a device appearing. Null is not "live": the two are distinct
 * and [isLiveEnforcement] only reports the mode that was actually recorded.
 *
 * [sequence] is the authoritative ordering key from the source. Chronology is
 * never inferred from the order rows happen to render in, or from the client's
 * own clock.
 */
data class AuditEntry(
    val id: String,
    val type: AuditEventType,
    val target: AuditTarget,
    val outcome: AuditOutcome,
    /** The authoritative timestamp, timezone-qualified. Shown in detail. */
    val occurredAtLabel: String,
    /** The day heading this record sits under, from the same authority. */
    val dayLabel: String,
    /** Relative recency, for scanning. Never the only time shown. */
    val relativeLabel: String,
    val ageMinutes: Int,
    val sequence: Long,
    val source: AuditSource,
    val executionMode: ExecutionMode? = null,
    /** Groups the events of one action or one alert into a sequence. */
    val correlationId: String? = null,
    /** The Phase 4 action code, for action and enforcement events. */
    val actionCode: String? = null,
    val previousState: String? = null,
    val resultingState: String? = null,
    /** The Phase 3 alert this record came from, when it came from one. */
    val alertId: String? = null,
    /** Source-supplied specifics. Never a headline, never a claim of outcome. */
    val note: String? = null
) {
    val category: AuditCategory get() = type.category

    /** True only when the record says the run was simulated. */
    val isSimulated: Boolean get() = executionMode == ExecutionMode.AuditOnly

    /** True only when the record says real firewall state was mutated. */
    val isLiveEnforcement: Boolean get() = executionMode == ExecutionMode.Enforce

    /** The mode was recorded but could not be determined. Not live, not simulated. */
    val hasUnknownMode: Boolean get() = executionMode == ExecutionMode.Unknown

    val carriesExecutionMode: Boolean get() = executionMode != null
}

/**
 * Whether an entry is internally consistent.
 *
 * A simulated run cannot have reconciled anything or created a binding,
 * because it did not touch the kernel. Rather than presenting such a record
 * with a reassuring badge, NEXA treats it as data it will not vouch for.
 */
fun isCoherent(entry: AuditEntry): Boolean =
    !(entry.isSimulated && entry.type.assertsFirewallMutation)

// ============================================================
// FILTERING / SEARCH / SORT
// ============================================================

/** Bounded, named time windows. Open-ended history is the default. */
enum class AuditTimeRange(val label: String, val minutes: Int?) {
    LastHour("Last hour", 60),
    Last24Hours("Last 24 hours", 60 * 24),
    Last7Days("Last 7 days", 60 * 24 * 7),
    All("All history", null)
}

data class AuditFilters(
    val categories: Set<AuditCategory> = emptySet(),
    val outcomes: Set<AuditOutcome> = emptySet(),
    val executionModes: Set<ExecutionMode> = emptySet(),
    val scopes: Set<String> = emptySet(),
    val timeRange: AuditTimeRange = AuditTimeRange.All,
    /** Narrows to records of simulated runs only. */
    val onlySimulated: Boolean = false
) {
    val isActive: Boolean
        get() = categories.isNotEmpty() || outcomes.isNotEmpty() ||
            executionModes.isNotEmpty() || scopes.isNotEmpty() ||
            timeRange != AuditTimeRange.All || onlySimulated

    val activeCount: Int
        get() = categories.size + outcomes.size + executionModes.size + scopes.size +
            (if (timeRange != AuditTimeRange.All) 1 else 0) +
            (if (onlySimulated) 1 else 0)
}

/** The scanning shortcuts above the timeline. */
enum class AuditQuickFilter(val label: String) {
    All("All"),
    Actions("Actions"),
    Trust("Trust"),
    Alerts("Alerts"),
    Notifications("Notifications"),
    Simulations("Simulations")
}

/** Which categories a quick filter selects. Empty means "do not narrow". */
val AuditQuickFilter.categories: Set<AuditCategory>
    get() = when (this) {
        AuditQuickFilter.All -> emptySet()
        // Requests and their resulting bindings are one story to an operator
        // scanning history, even though they remain separate event families.
        AuditQuickFilter.Actions -> setOf(AuditCategory.Action, AuditCategory.Enforcement)
        AuditQuickFilter.Trust -> setOf(AuditCategory.Trust)
        AuditQuickFilter.Alerts -> setOf(AuditCategory.Alert)
        AuditQuickFilter.Notifications -> setOf(AuditCategory.Notification)
        AuditQuickFilter.Simulations -> emptySet()
    }

/** Applies a quick filter without disturbing the operator's other choices. */
fun AuditFilters.withQuickFilter(quick: AuditQuickFilter): AuditFilters = copy(
    categories = quick.categories,
    onlySimulated = quick == AuditQuickFilter.Simulations
)

/** Which quick filter the current filters correspond to, if any. */
fun AuditFilters.activeQuickFilter(): AuditQuickFilter? = when {
    onlySimulated && categories.isEmpty() -> AuditQuickFilter.Simulations
    onlySimulated -> null
    categories.isEmpty() -> AuditQuickFilter.All
    else -> AuditQuickFilter.entries.firstOrNull {
        it != AuditQuickFilter.All && it != AuditQuickFilter.Simulations &&
            it.categories == categories
    }
}

/**
 * Search across the identifiers an operator would paste in.
 *
 * Every field searched is one intended for operator use. There is nothing
 * secret in this model to search — no key material, no credential, no token
 * is carried by [AuditEntry] at all, which is a stronger guarantee than
 * excluding such fields here would be.
 */
fun List<AuditEntry>.applyQuery(query: String): List<AuditEntry> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { entry ->
        entry.id.contains(q, ignoreCase = true) ||
            entry.correlationId?.contains(q, ignoreCase = true) == true ||
            entry.alertId?.contains(q, ignoreCase = true) == true ||
            entry.actionCode?.contains(q, ignoreCase = true) == true ||
            entry.target.matchesQuery(q)
    }
}

private fun AuditTarget.matchesQuery(q: String): Boolean = when (this) {
    is AuditTarget.Device ->
        deviceId.contains(q, true) || label.contains(q, true) ||
            mac.contains(q, true) || ip?.contains(q, true) == true ||
            scope.contains(q, true)
    is AuditTarget.Identity ->
        identityId.contains(q, true) || label.contains(q, true) ||
            scope?.contains(q, true) == true
    is AuditTarget.Scope -> scope.contains(q, true)
    is AuditTarget.Subsystem -> name.contains(q, true)
    AuditTarget.Unresolved -> false
}

/**
 * Applies the filter set.
 *
 * The execution-mode filter matches only entries that actually recorded a
 * mode. An event with no mode is not swept into "live" because it is not
 * simulated — that inference is precisely the one this model refuses to make.
 */
fun List<AuditEntry>.applyFilters(filters: AuditFilters): List<AuditEntry> = filter { entry ->
    (filters.categories.isEmpty() || entry.category in filters.categories) &&
        (filters.outcomes.isEmpty() || entry.outcome in filters.outcomes) &&
        (filters.executionModes.isEmpty() || entry.executionMode in filters.executionModes) &&
        (filters.scopes.isEmpty() || entry.target.scopeOrNull in filters.scopes) &&
        (filters.timeRange.minutes == null || entry.ageMinutes <= filters.timeRange.minutes) &&
        (!filters.onlySimulated || entry.isSimulated)
}

enum class AuditSort(val label: String) {
    /** The default. History is read backwards from now. */
    Newest("Newest first"),

    /** For replaying a sequence in the order it actually happened. */
    Oldest("Oldest first")
}

/**
 * Orders by the authoritative recency and sequence carried on the record.
 *
 * [AuditEntry.sequence] breaks ties, so two events inside the same displayed
 * minute keep the order the source assigned them rather than the order they
 * arrived in the list.
 */
fun List<AuditEntry>.applySort(sort: AuditSort): List<AuditEntry> = when (sort) {
    AuditSort.Newest -> sortedWith(compareBy({ it.ageMinutes }, { -it.sequence }, { it.id }))
    AuditSort.Oldest -> sortedWith(compareBy({ -it.ageMinutes }, { it.sequence }, { it.id }))
}

fun List<AuditEntry>.resolve(
    query: String,
    filters: AuditFilters,
    sort: AuditSort
): List<AuditEntry> = applyQuery(query).applyFilters(filters).applySort(sort)

// ============================================================
// GROUPING
// ============================================================

/**
 * The day heading a run of entries sits under.
 *
 * Derived from the label the source supplied, never from the client clock —
 * the point of a security history is that it says when the system thought
 * things happened.
 */
data class AuditDayGroup(val label: String, val entries: List<AuditEntry>)

fun groupByDay(entries: List<AuditEntry>, dayLabel: (AuditEntry) -> String): List<AuditDayGroup> {
    val groups = mutableListOf<AuditDayGroup>()
    var currentLabel: String? = null
    var current = mutableListOf<AuditEntry>()
    entries.forEach { entry ->
        val label = dayLabel(entry)
        if (label != currentLabel) {
            if (currentLabel != null) groups += AuditDayGroup(currentLabel, current)
            currentLabel = label
            current = mutableListOf()
        }
        current += entry
    }
    if (currentLabel != null) groups += AuditDayGroup(currentLabel, current)
    return groups
}

// ============================================================
// COVERAGE
// ============================================================

/**
 * Whether the history being shown is the whole history.
 *
 * A partial feed says so. Presenting an incomplete record as authoritative is
 * how an operator concludes that nothing happened during a window when in
 * fact NEXA simply could not see it.
 */
sealed interface AuditCoverage {
    data object Complete : AuditCoverage
    data class Partial(val reason: String) : AuditCoverage

    val isComplete: Boolean get() = this is Complete
}

// ============================================================
// SUMMARY
// ============================================================

/**
 * Counts over the records currently being shown.
 *
 * Deliberately computed from the *visible* set rather than from everything
 * loaded. A header that counted 6 filtered records and then broke them down
 * using totals from all 56 would read as "6 records, 6 failed" — every number
 * true of a different set, and the sentence as a whole false.
 *
 * Simulated and live runs are counted apart within that set. A total that
 * mixed them would be the single most misleading number this screen could
 * show.
 */
data class AuditSummaryCounts(
    val total: Int,
    val liveEnforcement: Int,
    val simulated: Int,
    val unknownOutcome: Int,
    val failures: Int
)

fun summarize(entries: List<AuditEntry>): AuditSummaryCounts = AuditSummaryCounts(
    total = entries.size,
    liveEnforcement = entries.count { it.isLiveEnforcement },
    simulated = entries.count { it.isSimulated },
    unknownOutcome = entries.count { it.outcome == AuditOutcome.Unknown },
    failures = entries.count { it.outcome == AuditOutcome.Failed }
)

// ============================================================
// SCREEN STATE
// ============================================================

/** Rendered page size. History is never loaded into the list unbounded. */
const val AUDIT_PAGE_SIZE = 25

sealed interface AuditUiState {
    data object Loading : AuditUiState

    /**
     * [page] is the bounded slice actually rendered; [visible] is everything
     * matching the current query. [hasMore] drives incremental loading and is
     * the seam a real paginated source plugs into.
     */
    data class Content(
        val all: List<AuditEntry>,
        val visible: List<AuditEntry>,
        val page: List<AuditEntry>,
        val summary: AuditSummaryCounts,
        val query: String,
        val filters: AuditFilters,
        val sort: AuditSort,
        val freshness: DataFreshness,
        val coverage: AuditCoverage,
        val hasMore: Boolean
    ) : AuditUiState

    data object Offline : AuditUiState

    /** History could not be retrieved. Not the same as there being none. */
    data object Unavailable : AuditUiState

    data class Error(val message: String) : AuditUiState
}

/** Builds a [AuditUiState.Content] with its derived fields consistent. */
fun auditContent(
    all: List<AuditEntry>,
    query: String = "",
    filters: AuditFilters = AuditFilters(),
    sort: AuditSort = AuditSort.Newest,
    freshness: DataFreshness = DataFreshness.Live,
    coverage: AuditCoverage = AuditCoverage.Complete,
    pageLimit: Int = AUDIT_PAGE_SIZE
): AuditUiState.Content {
    val visible = all.resolve(query, filters, sort)
    return AuditUiState.Content(
        all = all,
        visible = visible,
        page = visible.take(pageLimit),
        summary = summarize(visible),
        query = query,
        filters = filters,
        sort = sort,
        freshness = freshness,
        coverage = coverage,
        hasMore = visible.size > pageLimit
    )
}

// ============================================================
// DETAIL
// ============================================================

/** One field of an event's record. Only emitted when a value exists. */
data class AuditField(
    val label: String,
    val value: String,
    val technical: Boolean = false
)

data class AuditDetailData(
    val entry: AuditEntry,
    val fields: List<AuditField>,
    /** Other records from the same action or alert, oldest first. */
    val related: List<AuditEntry>,
    val links: List<AuditLink>,
    val freshness: DataFreshness
)

sealed interface AuditDetailUiState {
    data object Loading : AuditDetailUiState
    data class Content(val data: AuditDetailData) : AuditDetailUiState

    /** The record could not be resolved. Nothing is assumed about it. */
    data object Unavailable : AuditDetailUiState
    data class Error(val message: String) : AuditDetailUiState
}
