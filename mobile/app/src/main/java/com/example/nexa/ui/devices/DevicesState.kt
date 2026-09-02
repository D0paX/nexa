package com.example.nexa.ui.devices

import com.example.nexa.ui.common.ActivityEntry
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.NexaQuery
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.common.facetMatches
import com.example.nexa.ui.common.matches
import com.example.nexa.ui.common.nexaQuery
import com.example.nexa.ui.common.availabilityOf
import com.example.nexa.ui.enforcement.unreadableStateReason

/**
 * The operator-facing model of the device inventory.
 *
 * The distinctions Phase 1-4 draws are preserved as separate fields, never
 * collapsed into one "status":
 *
 *   [Presence]         — Phase 1 network observation. Says nothing about trust.
 *   [TrustState]      — Phase 2 cryptographic identity. Says nothing about authorization.
 *   [DeviceEnforcement]— Phase 4 execution state. Says nothing about what the firewall
 *                        does in every context.
 *
 * A device that is present is not therefore trusted; a device that is
 * trusted is not therefore authorized for any given action.
 */

// ============================================================
// DOMAIN-NEUTRAL DEVICE STATE
// ============================================================

/** Phase 1: has NEXA observed this device on the network recently? */
enum class Presence { Present, Absent, Unknown }

// Trust standing (Phase 2) uses the shared TrustState vocabulary — see
// com.example.nexa.ui.common.TrustState.

/** Phase 4: what enforcement is doing about this device. */
enum class DeviceEnforcement {
    Normal,
    Quarantined,
    Reconciling,
    Failed,
    Paused,
    Unknown
}

data class DeviceAlerts(val total: Int, val critical: Int, val warning: Int) {
    val hasAny: Boolean get() = total > 0
}

/**
 * A device as it appears in the inventory.
 *
 * [id] is the stable device identity, deliberately separate from [ip]: Phase 4
 * protects against stale-IP reassignment, so an address is never treated as
 * an identity. [scope] travels with the device because the same MAC in two
 * NetworkScopes is not the same logical target.
 */
data class DeviceListItem(
    val id: String,
    val label: String,
    val mac: String,
    val ip: String?,
    val scope: String,
    val presence: Presence,
    val trust: TrustState,
    val identityId: String?,
    val enforcement: DeviceEnforcement,
    val alerts: DeviceAlerts,
    val lastSeenLabel: String,
    val freshness: DataFreshness
)

// ============================================================
// QUERY / FILTER / SORT
// ============================================================

data class DeviceFilters(
    val presence: Set<Presence> = emptySet(),
    val trust: Set<TrustState> = emptySet(),
    val enforcement: Set<DeviceEnforcement> = emptySet(),
    val scopes: Set<String> = emptySet(),
    val onlyWithAlerts: Boolean = false
) {
    val isActive: Boolean
        get() = presence.isNotEmpty() || trust.isNotEmpty() ||
            enforcement.isNotEmpty() || scopes.isNotEmpty() || onlyWithAlerts

    val activeCount: Int
        get() = presence.size + trust.size + enforcement.size + scopes.size + (if (onlyWithAlerts) 1 else 0)
}

enum class DeviceSort(val label: String) {
    /** Devices needing an operator first. The default. */
    Attention("Needs attention"),
    Name("Name"),
    Presence("Presence")
}

/**
 * The text a device is searchable by.
 *
 * Operator-facing identifiers only. Nothing in [DeviceListItem] is secret —
 * there is no credential, key or token on the model to exclude — and the list
 * is written out explicitly rather than derived from the data class so that
 * adding a field to the model can never silently make it searchable.
 */
fun deviceSearchFields(device: DeviceListItem): List<String?> = listOf(
    device.label,
    device.mac,
    device.ip,
    device.scope,
    device.identityId,
    device.id
)

fun List<DeviceListItem>.applyQuery(query: NexaQuery): List<DeviceListItem> =
    if (!query.isActive) this else filter { query.matches(deviceSearchFields(it)) }

/**
 * Convenience overload: normalizes then matches.
 *
 * The normalized form is what matching actually uses, so a caller that has a
 * raw string goes through the same door as everything else rather than
 * inventing its own trimming.
 */
fun List<DeviceListItem>.applyQuery(query: String): List<DeviceListItem> = applyQuery(nexaQuery(query))

/**
 * AND between facets, OR within each — the shared rule, see [facetMatches].
 *
 * A device excluded here is not changed by being excluded. Its trust, its
 * enforcement state and what an operator may do to it are exactly what they
 * were before the filter was applied.
 */
fun List<DeviceListItem>.applyFilters(filters: DeviceFilters): List<DeviceListItem> = filter { device ->
    filters.presence.facetMatches(device.presence) &&
        filters.trust.facetMatches(device.trust) &&
        filters.enforcement.facetMatches(device.enforcement) &&
        filters.scopes.facetMatches(device.scope) &&
        (!filters.onlyWithAlerts || device.alerts.hasAny)
}

/**
 * How loudly a device is asking for an operator. Lower sorts first.
 *
 * Healthy devices are never hidden — they simply sort below the ones with
 * something outstanding.
 */
fun attentionRank(device: DeviceListItem): Int = when {
    device.alerts.critical > 0 -> 0
    device.enforcement == DeviceEnforcement.Failed -> 1
    device.enforcement == DeviceEnforcement.Reconciling -> 2
    device.trust == TrustState.Revoked -> 3
    device.alerts.warning > 0 -> 4
    device.freshness is DataFreshness.Unknown -> 5
    device.enforcement == DeviceEnforcement.Quarantined -> 6
    device.trust == TrustState.Unverified -> 7
    device.freshness is DataFreshness.Stale -> 8
    else -> 9
}

private fun presenceOrder(presence: com.example.nexa.ui.devices.Presence): Int = when (presence) {
    com.example.nexa.ui.devices.Presence.Present -> 0
    com.example.nexa.ui.devices.Presence.Unknown -> 1
    com.example.nexa.ui.devices.Presence.Absent -> 2
}

/**
 * Ordering, always ending in the device id.
 *
 * The id tie-break is not decoration. Two devices can legitimately share a
 * label, and without a deterministic final comparison they would swap places
 * between two identical loads — and again on every realtime update, which is
 * exactly when a list must hold still.
 */
fun List<DeviceListItem>.applySort(sort: DeviceSort): List<DeviceListItem> = when (sort) {
    DeviceSort.Attention ->
        sortedWith(compareBy({ attentionRank(it) }, { it.label.lowercase() }, { it.id }))
    DeviceSort.Name ->
        sortedWith(compareBy({ it.label.lowercase() }, { it.id }))
    DeviceSort.Presence ->
        sortedWith(compareBy({ presenceOrder(it.presence) }, { it.label.lowercase() }, { it.id }))
}

/**
 * The whole pipeline, in one place so the screen never does this itself.
 *
 * Order is fixed and shared across every domain: search, then filter, then
 * sort. The query is normalized once here rather than per record.
 */
fun List<DeviceListItem>.resolve(
    query: String,
    filters: DeviceFilters,
    sort: DeviceSort
): List<DeviceListItem> = applyQuery(nexaQuery(query)).applyFilters(filters).applySort(sort)

// ============================================================
// ACTIONS
// ============================================================

enum class DeviceActionKind { Quarantine, Release, RequireReverification }

/**
 * An action offered for a device.
 *
 * The mobile client never executes anything: [actionCode] is handed to the
 * existing Phase 4 confirmation flow, which owns snapshot, authorization and
 * execution. [enabled] plus [disabledReason] exist so an unavailable action
 * explains itself rather than silently vanishing.
 */
data class DeviceAction(
    val kind: DeviceActionKind,
    val label: String,
    val actionCode: String,
    val enabled: Boolean,
    val disabledReason: String? = null,
    val destructive: Boolean = false
)

/**
 * Which actions a device's current state actually supports.
 *
 * Availability follows state rather than being offered universally: you
 * cannot release a device that is not quarantined, and you cannot require
 * reverification of a device that has no cryptographic identity to
 * reverify. When enforcement is globally paused, actions remain visible but
 * disabled with the reason stated — hiding them would leave an operator
 * wondering why the option disappeared.
 */
fun availableActions(device: DeviceListItem): List<DeviceAction> {
    val paused = device.enforcement == DeviceEnforcement.Paused
    val pausedReason = "Enforcement is paused by the circuit breaker."

    // The same objection the confirmation screen would raise, raised here so
    // an operator is not walked up to a full-strength destructive button and
    // turned away at the last step. Wording is shared with that screen.
    val availability = availabilityOf(device.freshness)
    val blocksEnforcement =
        if (availability.isActionable) null else unreadableStateReason(availability)
    val blocksEverything =
        if (availability.hasData) null else unreadableStateReason(availability)

    val actions = mutableListOf<DeviceAction>()

    when (device.enforcement) {
        DeviceEnforcement.Quarantined -> actions += DeviceAction(
            kind = DeviceActionKind.Release,
            label = "Release Quarantine",
            actionCode = "RELEASE_QUARANTINE",
            enabled = blocksEnforcement == null,
            disabledReason = blocksEnforcement
        )
        DeviceEnforcement.Normal, DeviceEnforcement.Paused -> actions += DeviceAction(
            kind = DeviceActionKind.Quarantine,
            label = "Quarantine Device",
            actionCode = "QUARANTINE_DEVICE",
            enabled = !paused && blocksEnforcement == null,
            disabledReason = if (paused) pausedReason else blocksEnforcement,
            destructive = true
        )
        DeviceEnforcement.Reconciling -> actions += DeviceAction(
            kind = DeviceActionKind.Quarantine,
            label = "Quarantine Device",
            actionCode = "QUARANTINE_DEVICE",
            enabled = false,
            disabledReason = "Enforcement state is still being reconciled for this device.",
            destructive = true
        )
        DeviceEnforcement.Failed -> actions += DeviceAction(
            kind = DeviceActionKind.Quarantine,
            label = "Retry Quarantine",
            actionCode = "QUARANTINE_DEVICE",
            enabled = !paused && blocksEnforcement == null,
            disabledReason = if (paused) pausedReason else blocksEnforcement,
            destructive = true
        )
        DeviceEnforcement.Unknown -> actions += DeviceAction(
            kind = DeviceActionKind.Quarantine,
            label = "Quarantine Device",
            actionCode = "QUARANTINE_DEVICE",
            enabled = false,
            disabledReason = "Current enforcement state for this device is unknown.",
            destructive = true
        )
    }

    // Reverification is a Phase 2 concept: it requires an identity to exist.
    if (device.trust == TrustState.Trusted || device.trust == TrustState.Pending) {
        actions += DeviceAction(
            kind = DeviceActionKind.RequireReverification,
            label = "Require Reverification",
            actionCode = "REQUIRE_REVERIFICATION",
            // Reverification is not a firewall change: an old observation
            // does not block it, because it asks the identity itself to
            // prove it is there. State NEXA cannot read at all does.
            enabled = blocksEverything == null,
            disabledReason = blocksEverything
        )
    }

    return actions
}

// ============================================================
// SCREEN STATE
// ============================================================

sealed interface DevicesUiState {
    data object Loading : DevicesUiState

    /**
     * [visible] is resolved off the UI thread by the state holder, so the
     * list never filters or sorts during composition.
     */
    data class Content(
        val all: List<DeviceListItem>,
        val visible: List<DeviceListItem>,
        val query: String,
        val filters: DeviceFilters,
        val sort: DeviceSort,
        val freshness: DataFreshness,
        val degraded: Boolean,
        /**
         * A revalidation is running over this content.
         *
         * Not an availability state: the data on screen is exactly as
         * trustworthy as it was a moment ago, and NEXA is checking it again.
         * Replacing the list with a spinner would take away the only
         * information the operator currently has in order to tell them that
         * better information is coming.
         */
        val refreshing: Boolean = false,
        /**
         * Cached inventory, no connection.
         *
         * Separate from [degraded] and from [freshness]: an offline picture
         * may be complete and only minutes old, and a stale one may have
         * arrived over a perfectly good connection. Collapsing the two would
         * leave an operator unable to tell "this is old" from "I cannot ask".
         */
        val offline: Boolean = false
    ) : DevicesUiState

    data object Offline : DevicesUiState

    data object Unavailable : DevicesUiState

    data class Error(val message: String) : DevicesUiState
}

// ============================================================
// DEVICE DETAIL
// ============================================================

/** Phase 1 observation: what the network saw, and when. */
data class DeviceRecordContext(
    val presence: Presence,
    val scope: String,
    val ip: String?,
    val mac: String,
    val lastObservedLabel: String,
    val freshness: DataFreshness
)

/** Phase 2 cryptographic identity. Absent when the device is merely observed. */
data class TrustedIdentityContext(
    val identityId: String,
    val trust: TrustState,
    val owner: String?,
    val verifiedLabel: String,
    val reverificationLabel: String?
)

/** Phase 4 enforcement context for one device. */
data class DeviceEnforcementContext(
    val state: DeviceEnforcement,
    val detail: String,
    /** Which scope owns the binding — ownership is never inferred from an IP. */
    val ownershipScope: String?,
    val bindingLabel: String?,
    val targetStale: Boolean
)

data class DeviceAlertItem(
    val id: String,
    val title: String,
    val severity: String,
    val timeAgo: String
)

data class DeviceDetailData(
    val device: DeviceListItem,
    val record: DeviceRecordContext,
    val identity: TrustedIdentityContext?,
    val enforcement: DeviceEnforcementContext,
    val alerts: List<DeviceAlertItem>,
    val activity: List<ActivityEntry>,
    val actions: List<DeviceAction>
)

sealed interface DeviceDetailUiState {
    data object Loading : DeviceDetailUiState
    data class Content(val data: DeviceDetailData) : DeviceDetailUiState
    data object Unavailable : DeviceDetailUiState
    data class Error(val message: String) : DeviceDetailUiState
}
