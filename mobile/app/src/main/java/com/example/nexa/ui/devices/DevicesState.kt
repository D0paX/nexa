package com.example.nexa.ui.devices

import com.example.nexa.ui.common.ActivityEntry
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.TrustState

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

enum class DeviceSort {
    /** Devices needing an operator first. The default. */
    Attention,
    Name,
    LastSeen
}

/**
 * Free-text match across the identifiers an operator would actually type.
 * Never matches on anything secret — identity *identifiers* only, no key
 * material.
 */
fun List<DeviceListItem>.applyQuery(query: String): List<DeviceListItem> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { device ->
        device.label.contains(q, ignoreCase = true) ||
            device.mac.contains(q, ignoreCase = true) ||
            (device.ip?.contains(q, ignoreCase = true) == true) ||
            device.scope.contains(q, ignoreCase = true) ||
            (device.identityId?.contains(q, ignoreCase = true) == true)
    }
}

fun List<DeviceListItem>.applyFilters(filters: DeviceFilters): List<DeviceListItem> = filter { device ->
    (filters.presence.isEmpty() || device.presence in filters.presence) &&
        (filters.trust.isEmpty() || device.trust in filters.trust) &&
        (filters.enforcement.isEmpty() || device.enforcement in filters.enforcement) &&
        (filters.scopes.isEmpty() || device.scope in filters.scopes) &&
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

fun List<DeviceListItem>.applySort(sort: DeviceSort): List<DeviceListItem> = when (sort) {
    DeviceSort.Attention -> sortedWith(compareBy({ attentionRank(it) }, { it.label.lowercase() }))
    DeviceSort.Name -> sortedBy { it.label.lowercase() }
    DeviceSort.LastSeen -> sortedWith(compareBy({ it.presence != Presence.Present }, { it.label.lowercase() }))
}

/** The whole pipeline, in one place so the screen never does this itself. */
fun List<DeviceListItem>.resolve(
    query: String,
    filters: DeviceFilters,
    sort: DeviceSort
): List<DeviceListItem> = applyQuery(query).applyFilters(filters).applySort(sort)

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
    val actions = mutableListOf<DeviceAction>()

    when (device.enforcement) {
        DeviceEnforcement.Quarantined -> actions += DeviceAction(
            kind = DeviceActionKind.Release,
            label = "Release Quarantine",
            actionCode = "RELEASE_QUARANTINE",
            enabled = true
        )
        DeviceEnforcement.Normal, DeviceEnforcement.Paused -> actions += DeviceAction(
            kind = DeviceActionKind.Quarantine,
            label = "Quarantine Device",
            actionCode = "QUARANTINE_DEVICE",
            enabled = !paused,
            disabledReason = if (paused) pausedReason else null,
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
            enabled = !paused,
            disabledReason = if (paused) pausedReason else null,
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
            enabled = true
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
        val degraded: Boolean
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
