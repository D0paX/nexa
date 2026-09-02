package com.example.nexa.ui.notifications

import com.example.nexa.ui.alerts.AlertLifecycle
import com.example.nexa.ui.alerts.AlertSeverity
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.DeliveryAttempt
import com.example.nexa.ui.common.DeliveryState
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.NexaQuery
import com.example.nexa.ui.common.facetMatches
import com.example.nexa.ui.common.matches
import com.example.nexa.ui.common.nexaQuery
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.enforcement.ExecutionState

/**
 * The operator-facing model of Phase 3 notification delivery.
 *
 * This screen answers one question: did NEXA manage to deliver the message?
 * It does not answer what the message was about — that belongs to the alert,
 * the action or the identity the message concerned, each of which has its own
 * authoritative surface.
 *
 * The structure enforces the distinction rather than relying on wording:
 *
 *   [NotificationDeliverySummary]  holds delivery concepts and nothing else
 *   [NotificationSource]           holds a read-only snapshot of the subject
 *
 * They are siblings on [NotificationRecord], never nested and never merged.
 * No delivery field is derived from the source, and no source field is
 * derived from delivery — which is what makes it structurally impossible for
 * a failed notification to render as a failed alert.
 */

// ============================================================
// CHANNEL
// ============================================================

/**
 * The transport a notification went out on.
 *
 * Phase 3 declares an FCM integration boundary, so push is the channel the
 * backend actually has. Others are not modelled because inventing them would
 * put destinations in the interface that nothing can deliver to.
 */
enum class NotificationChannel {
    Push,

    /** The record does not name a channel NEXA recognises. */
    Unknown
}

val NotificationChannel.label: String
    get() = when (this) {
        NotificationChannel.Push -> "Push (FCM)"
        NotificationChannel.Unknown -> "Unknown channel"
    }

// ============================================================
// DELIVERY — transport concepts only
// ============================================================

/**
 * Everything known about one delivery, and nothing about what it was about.
 *
 * There is deliberately no alert lifecycle, no execution state and no trust
 * standing on this type. A screen holding only this object cannot make a
 * claim about a security incident, because it has not been given one.
 *
 * [nextRetryLabel] is present only when the backend supplied retry timing.
 * NEXA does not run a client-side countdown and then present it as though it
 * knew the schedule.
 */
data class NotificationDeliverySummary(
    val deliveryId: String,
    val state: DeliveryState,
    val channel: NotificationChannel,
    val attemptCount: Int,
    val maxAttempts: Int?,
    val createdLabel: String,
    val lastAttemptLabel: String,
    val nextRetryLabel: String? = null,
    /**
     * True when this record exists because a push arrived on this device.
     *
     * Arrival here is not the Phase 3 delivery model reporting success: the
     * backend confirms delivery, and a message reaching one handset is a
     * different fact from that confirmation. Records created this way carry
     * an unavailable delivery state and say why.
     */
    val receivedOnThisDevice: Boolean = false,
    /** Why the last attempt failed, when the record says. Never a generic apology. */
    val failureReason: String? = null,
    val attempts: List<DeliveryAttempt> = emptyList(),
    /** Deterministic recency key. */
    val ageMinutes: Int
) {
    val isFailure: Boolean get() = state.isFailure
    val isRetrying: Boolean get() = state == DeliveryState.Retrying
    val isExhausted: Boolean get() = state == DeliveryState.Exhausted
    val hasRetrySchedule: Boolean get() = nextRetryLabel != null
}

// ============================================================
// SOURCE — a read-only snapshot of the subject
// ============================================================

/** The kind of thing a notification was about, for filtering and labelling. */
enum class NotificationSourceType { Alert, Action, Trust, SecurityEvent, Unknown }

/**
 * What the message was about.
 *
 * Snapshots only. Nothing here is authoritative and nothing here is editable
 * from this screen: the alert's real state lives in Alerts, the execution's
 * in the action flow, the identity's in Identity. These fields exist so an
 * operator reading a delivery record knows what failed to arrive — and so the
 * interface can say, in as many words, that the incident is unaffected.
 */
sealed interface NotificationSource {

    /**
     * [lifecycle] is null when the alert's own state has not been read.
     *
     * A push carries identifiers and hints, never authoritative state, so a
     * record created from one knows the alert exists and knows nothing about
     * where it stands. Null says exactly that, and the interface says it too
     * rather than picking a plausible-looking lifecycle.
     */
    data class Alert(
        val alertId: String,
        val title: String,
        val severity: AlertSeverity,
        val lifecycle: AlertLifecycle?
    ) : NotificationSource

    /** [executionState] is null when the execution's state has not been read. */
    data class Action(
        val actionId: String,
        val actionCode: String,
        val executionState: ExecutionState?,
        val executionMode: ExecutionMode
    ) : NotificationSource

    /** [trust] is null when the identity's standing has not been read. */
    data class Trust(
        val identityId: String,
        val label: String,
        val trust: TrustState?
    ) : NotificationSource

    data class SecurityEvent(
        val eventId: String,
        val summary: String
    ) : NotificationSource

    /** NEXA cannot resolve what this notification was about. */
    data object Unknown : NotificationSource
}

val NotificationSource.type: NotificationSourceType
    get() = when (this) {
        is NotificationSource.Alert -> NotificationSourceType.Alert
        is NotificationSource.Action -> NotificationSourceType.Action
        is NotificationSource.Trust -> NotificationSourceType.Trust
        is NotificationSource.SecurityEvent -> NotificationSourceType.SecurityEvent
        NotificationSource.Unknown -> NotificationSourceType.Unknown
    }

/** The identifier an operator would quote. Stable, and the future deep-link key. */
val NotificationSource.identifier: String?
    get() = when (this) {
        is NotificationSource.Alert -> alertId
        is NotificationSource.Action -> actionId
        is NotificationSource.Trust -> identityId
        is NotificationSource.SecurityEvent -> eventId
        NotificationSource.Unknown -> null
    }

// ============================================================
// TARGET
// ============================================================

/**
 * The network context the message concerned.
 *
 * An address is observation context and never an identity — the same rule the
 * rest of the product holds to, restated here because a notification about a
 * device is the easiest place to forget it.
 */
sealed interface NotificationTarget {
    data class Device(
        val deviceId: String,
        val label: String,
        val mac: String,
        val ip: String?,
        val scope: String,
        val observationFreshness: DataFreshness
    ) : NotificationTarget

    data class Identity(
        val identityId: String,
        val label: String,
        val scope: String?
    ) : NotificationTarget

    /**
     * A device NEXA has an address for and nothing else.
     *
     * What a push leaves behind. Carrying only the MAC is deliberate: the
     * label, scope and observation freshness are unknown, and inventing
     * plausible values would be a target reconstructed from a message rather
     * than read from the system.
     */
    data class UnresolvedDevice(val mac: String) : NotificationTarget

    /** An identity NEXA has an identifier for and nothing else. */
    data class UnresolvedIdentity(val identityId: String) : NotificationTarget

    data object None : NotificationTarget
}

val NotificationTarget.displayLabel: String?
    get() = when (this) {
        is NotificationTarget.Device -> label
        is NotificationTarget.Identity -> label
        is NotificationTarget.UnresolvedDevice -> "Unresolved device"
        is NotificationTarget.UnresolvedIdentity -> "Unresolved identity"
        NotificationTarget.None -> null
    }

val NotificationTarget.scopeOrNull: String?
    get() = when (this) {
        is NotificationTarget.Device -> scope
        is NotificationTarget.Identity -> scope
        // An unresolved reference has no scope, and guessing one would put a
        // record in a network segment nothing confirmed it belongs to.
        is NotificationTarget.UnresolvedDevice -> null
        is NotificationTarget.UnresolvedIdentity -> null
        NotificationTarget.None -> null
    }

// ============================================================
// RECORD
// ============================================================

/**
 * One notification and what became of it.
 *
 * [id] is the delivery identifier assigned by the backend — the routing key,
 * never a display label.
 */
data class NotificationRecord(
    val id: String,
    val delivery: NotificationDeliverySummary,
    val source: NotificationSource,
    val target: NotificationTarget = NotificationTarget.None,
    /** What the message said, as sent. */
    val subject: String
) {
    val sourceType: NotificationSourceType get() = source.type
}

// ============================================================
// ATTENTION
// ============================================================

/**
 * After this long, a delivery failure is history rather than an open
 * operational problem. Failures older than this are ranked below current
 * in-flight deliveries so a week-old failure cannot sit above a delivery
 * failing right now.
 */
const val STALE_ATTENTION_MINUTES = 7 * 24 * 60

/**
 * How loudly a delivery record is asking for an operator. Lower sorts first.
 *
 * Ranked purely on delivery. The severity of the incident the message was
 * about deliberately plays no part: a critical alert is critical because of
 * the alert, and letting it promote its notification would quietly turn this
 * screen into a second, worse alert queue.
 */
fun deliveryAttentionRank(record: NotificationRecord): Int {
    val base = when (record.delivery.state) {
        // Terminal and nobody was told.
        DeliveryState.Exhausted -> 0
        DeliveryState.Failed -> 10
        DeliveryState.Retrying -> 20
        DeliveryState.Pending -> 30
        DeliveryState.Sent -> 40
        DeliveryState.Unavailable -> 50
        DeliveryState.Delivered -> 60
    }
    val agedOut = record.delivery.ageMinutes > STALE_ATTENTION_MINUTES
    return if (agedOut && base <= 10) base + STALE_ATTENTION_DEMOTION else base
}

private const val STALE_ATTENTION_DEMOTION = 25

// ============================================================
// QUERY / FILTER / SORT
// ============================================================

enum class NotificationTimeRange(val label: String, val minutes: Int?) {
    LastHour("Last hour", 60),
    Last24Hours("Last 24 hours", 60 * 24),
    Last7Days("Last 7 days", 60 * 24 * 7),
    All("All records", null)
}

/**
 * Delivery filters.
 *
 * There is no channel filter: Phase 3 declares one channel, and offering a
 * control that can only ever select everything would suggest the model holds
 * destinations it does not.
 */
data class NotificationFilters(
    val states: Set<DeliveryState> = emptySet(),
    val sourceTypes: Set<NotificationSourceType> = emptySet(),
    val scopes: Set<String> = emptySet(),
    val timeRange: NotificationTimeRange = NotificationTimeRange.All
) {
    val isActive: Boolean
        get() = states.isNotEmpty() || sourceTypes.isNotEmpty() || scopes.isNotEmpty() ||
            timeRange != NotificationTimeRange.All

    val activeCount: Int
        get() = states.size + sourceTypes.size + scopes.size +
            (if (timeRange != NotificationTimeRange.All) 1 else 0)
}

enum class NotificationQuickFilter(val label: String) {
    All("All"),
    Failed("Failed"),
    Retrying("Retrying"),
    Pending("Pending"),
    Delivered("Delivered")
}

/** Which delivery states a quick filter selects. Empty means "do not narrow". */
val NotificationQuickFilter.states: Set<DeliveryState>
    get() = when (this) {
        NotificationQuickFilter.All -> emptySet()
        // Exhausted is a failure that has stopped trying; an operator looking
        // for failures wants both.
        NotificationQuickFilter.Failed -> setOf(DeliveryState.Failed, DeliveryState.Exhausted)
        NotificationQuickFilter.Retrying -> setOf(DeliveryState.Retrying)
        NotificationQuickFilter.Pending -> setOf(DeliveryState.Pending, DeliveryState.Sent)
        NotificationQuickFilter.Delivered -> setOf(DeliveryState.Delivered)
    }

fun NotificationFilters.withQuickFilter(quick: NotificationQuickFilter): NotificationFilters =
    copy(states = quick.states)

fun NotificationFilters.activeQuickFilter(): NotificationQuickFilter? = when {
    states.isEmpty() -> NotificationQuickFilter.All
    else -> NotificationQuickFilter.entries.firstOrNull {
        it != NotificationQuickFilter.All && it.states == states
    }
}

enum class NotificationSort(val label: String) {
    Attention("Needs attention"),
    Newest("Newest first"),
    Oldest("Oldest first")
}

/**
 * The text a delivery record is searchable by.
 *
 * Every searchable field is one intended for operator use. The model carries
 * no token, credential or payload to search — the push registration token a
 * real delivery record would reference is not represented here at all, so
 * there is nothing to accidentally expose through a query.
 *
 * The delivery state is searchable by name, which is a text match on a
 * displayed value. It is not a substitute for the delivery-state facet, and
 * it cannot alter what happened to any message.
 */
fun notificationSearchFields(record: NotificationRecord): List<String?> = listOf(
    record.id,
    record.delivery.deliveryId,
    record.subject,
    record.delivery.state.name,
    record.sourceType.name,
    record.source.identifier
) + record.source.searchFields() + record.target.searchFields()

private fun NotificationSource.searchFields(): List<String?> = when (this) {
    is NotificationSource.Alert -> listOf(title)
    is NotificationSource.Action -> listOf(actionCode)
    is NotificationSource.Trust -> listOf(label)
    is NotificationSource.SecurityEvent -> listOf(summary)
    NotificationSource.Unknown -> emptyList()
}

private fun NotificationTarget.searchFields(): List<String?> = when (this) {
    is NotificationTarget.Device -> listOf(deviceId, label, mac, ip, scope)
    is NotificationTarget.Identity -> listOf(identityId, label, scope)
    is NotificationTarget.UnresolvedDevice -> listOf(mac)
    is NotificationTarget.UnresolvedIdentity -> listOf(identityId)
    NotificationTarget.None -> emptyList()
}

fun List<NotificationRecord>.applyQuery(query: NexaQuery): List<NotificationRecord> =
    if (!query.isActive) this else filter { query.matches(notificationSearchFields(it)) }

/**
 * Convenience overload: normalizes then matches.
 *
 * The normalized form is what matching actually uses, so a caller that has a
 * raw string goes through the same door as everything else rather than
 * inventing its own trimming.
 */
fun List<NotificationRecord>.applyQuery(query: String): List<NotificationRecord> = applyQuery(nexaQuery(query))

/**
 * AND between facets, OR within each.
 *
 * Delivery state is a transport fact and stays one. It is never folded into
 * alert severity: a delivered notification about a critical incident and a
 * failed notification about an informational one are both perfectly ordinary,
 * and a filter that conflated them would hide that.
 */
fun List<NotificationRecord>.applyFilters(filters: NotificationFilters): List<NotificationRecord> =
    filter { record ->
        filters.states.facetMatches(record.delivery.state) &&
            filters.sourceTypes.facetMatches(record.sourceType) &&
            filters.scopes.facetMatches(record.target.scopeOrNull) &&
            (filters.timeRange.minutes == null || record.delivery.ageMinutes <= filters.timeRange.minutes)
    }

/**
 * Deterministic ordering.
 *
 * Recency breaks ties within a rank, and the delivery id breaks ties within a
 * minute, so the list never reorders itself between two identical loads.
 */
fun List<NotificationRecord>.applySort(sort: NotificationSort): List<NotificationRecord> =
    when (sort) {
        NotificationSort.Attention -> sortedWith(
            compareBy({ deliveryAttentionRank(it) }, { it.delivery.ageMinutes }, { it.id })
        )
        NotificationSort.Newest -> sortedWith(compareBy({ it.delivery.ageMinutes }, { it.id }))
        NotificationSort.Oldest -> sortedWith(compareBy({ -it.delivery.ageMinutes }, { it.id }))
    }

fun List<NotificationRecord>.resolve(
    query: String,
    filters: NotificationFilters,
    sort: NotificationSort
): List<NotificationRecord> =
    applyQuery(nexaQuery(query)).applyFilters(filters).applySort(sort)

// ============================================================
// SUMMARY
// ============================================================

/** Delivery posture. Counts transport states only — never incident severity. */
data class NotificationSummaryCounts(
    val total: Int,
    val pending: Int,
    val retrying: Int,
    val failed: Int,
    val exhausted: Int,
    val delivered: Int
) {
    val needsAttention: Int get() = failed + exhausted
}

fun summarize(records: List<NotificationRecord>): NotificationSummaryCounts =
    NotificationSummaryCounts(
        total = records.size,
        pending = records.count {
            it.delivery.state == DeliveryState.Pending || it.delivery.state == DeliveryState.Sent
        },
        retrying = records.count { it.delivery.state == DeliveryState.Retrying },
        failed = records.count { it.delivery.state == DeliveryState.Failed },
        exhausted = records.count { it.delivery.state == DeliveryState.Exhausted },
        delivered = records.count { it.delivery.state == DeliveryState.Delivered }
    )

// ============================================================
// COVERAGE
// ============================================================

/** Whether the delivery records shown are the whole picture. */
sealed interface NotificationCoverage {
    data object Complete : NotificationCoverage
    data class Partial(val reason: String) : NotificationCoverage

    val isComplete: Boolean get() = this is Complete
}

// ============================================================
// SCREEN STATE
// ============================================================

const val NOTIFICATION_PAGE_SIZE = 25

sealed interface NotificationCenterUiState {
    data object Loading : NotificationCenterUiState

    data class Content(
        val all: List<NotificationRecord>,
        val visible: List<NotificationRecord>,
        val page: List<NotificationRecord>,
        val summary: NotificationSummaryCounts,
        val query: String,
        val filters: NotificationFilters,
        val sort: NotificationSort,
        val freshness: DataFreshness,
        val coverage: NotificationCoverage,
        val hasMore: Boolean
    ) : NotificationCenterUiState

    data object Offline : NotificationCenterUiState

    /** Delivery visibility could not be retrieved. Not "nothing was sent". */
    data object Unavailable : NotificationCenterUiState

    data class Error(val message: String) : NotificationCenterUiState
}

/** Builds a [NotificationCenterUiState.Content] with derived fields consistent. */
fun notificationContent(
    all: List<NotificationRecord>,
    query: String = "",
    filters: NotificationFilters = NotificationFilters(),
    sort: NotificationSort = NotificationSort.Attention,
    freshness: DataFreshness = DataFreshness.Live,
    coverage: NotificationCoverage = NotificationCoverage.Complete,
    pageLimit: Int = NOTIFICATION_PAGE_SIZE
): NotificationCenterUiState.Content {
    val visible = all.resolve(query, filters, sort)
    return NotificationCenterUiState.Content(
        all = all,
        visible = visible,
        page = visible.take(pageLimit),
        // Over the visible set, so the header's count and its breakdown always
        // describe the same records.
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

/** One field of a delivery record. */
data class NotificationField(
    val label: String,
    val value: String,
    val technical: Boolean = false
)

/** Where a delivery record can take the operator. Never into an execution. */
sealed interface NotificationLink {
    data class Alert(val alertId: String) : NotificationLink
    data class Device(val mac: String) : NotificationLink
    data class Identity(val identityId: String) : NotificationLink
}

data class NotificationDetailData(
    val record: NotificationRecord,
    val deliveryFields: List<NotificationField>,
    val sourceFields: List<NotificationField>,
    val attempts: List<DeliveryAttempt>,
    val links: List<NotificationLink>,
    val freshness: DataFreshness
)

sealed interface NotificationDetailUiState {
    data object Loading : NotificationDetailUiState
    data class Content(val data: NotificationDetailData) : NotificationDetailUiState

    /** The delivery record could not be resolved. Nothing is assumed about it. */
    data object Unavailable : NotificationDetailUiState
    data class Error(val message: String) : NotificationDetailUiState
}
