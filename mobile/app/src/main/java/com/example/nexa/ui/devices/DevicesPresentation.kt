package com.example.nexa.ui.devices

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaStatus
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.common.label
import com.example.nexa.ui.common.status

/**
 * How device state is presented.
 *
 * Each domain distinction maps to the shared state vocabulary separately —
 * presence, trust and enforcement each get their own word, shape and color,
 * so the interface cannot quietly merge them into one impression.
 */

val Presence.status: NexaStatus
    get() = when (this) {
        Presence.Present -> NexaStatus.Secure
        Presence.Absent -> NexaStatus.Offline
        Presence.Unknown -> NexaStatus.Unknown
    }

val Presence.label: String
    get() = when (this) {
        Presence.Present -> "Present"
        Presence.Absent -> "Absent"
        Presence.Unknown -> "Presence unknown"
    }

val Presence.icon: ImageVector
    get() = when (this) {
        Presence.Present -> NexaIcons.Devices
        Presence.Absent -> NexaIcons.Offline
        Presence.Unknown -> NexaIcons.Unknown
    }

// Trust presentation is shared vocabulary — see com.example.nexa.ui.common.

val DeviceEnforcement.status: NexaStatus
    get() = when (this) {
        DeviceEnforcement.Normal -> NexaStatus.Permitted
        DeviceEnforcement.Quarantined -> NexaStatus.Danger
        DeviceEnforcement.Reconciling -> NexaStatus.Degraded
        DeviceEnforcement.Failed -> NexaStatus.Critical
        DeviceEnforcement.Paused -> NexaStatus.Paused
        DeviceEnforcement.Unknown -> NexaStatus.Unknown
    }

val DeviceEnforcement.label: String
    get() = when (this) {
        DeviceEnforcement.Normal -> "Normal"
        DeviceEnforcement.Quarantined -> "Quarantined"
        DeviceEnforcement.Reconciling -> "Reconciling"
        DeviceEnforcement.Failed -> "Failed"
        DeviceEnforcement.Paused -> "Paused"
        DeviceEnforcement.Unknown -> "Unknown"
    }

/**
 * The one badge a list row shows, when the device has something outstanding.
 *
 * Deliberately a single worst-of signal rather than four badges: the row is
 * for scanning, and the full picture is one tap away on Device Detail.
 * Returns null for a device with nothing to report.
 */
fun attentionBadge(device: DeviceListItem): NexaStatus? = when {
    device.alerts.critical > 0 -> NexaStatus.Critical
    device.enforcement == DeviceEnforcement.Failed -> NexaStatus.Critical
    device.enforcement == DeviceEnforcement.Reconciling -> NexaStatus.Degraded
    device.trust == TrustState.Revoked -> NexaStatus.Danger
    device.alerts.warning > 0 -> NexaStatus.Warning
    device.freshness is DataFreshness.Unknown -> NexaStatus.Unknown
    device.enforcement == DeviceEnforcement.Quarantined -> NexaStatus.Danger
    device.enforcement == DeviceEnforcement.Paused -> NexaStatus.Paused
    device.trust == TrustState.Unverified -> NexaStatus.Warning
    device.freshness is DataFreshness.Stale -> NexaStatus.Warning
    else -> null
}

/** The badge's word, which names the actual condition rather than a severity. */
fun attentionLabel(device: DeviceListItem): String? = when {
    device.alerts.critical > 0 -> "CRITICAL"
    device.enforcement == DeviceEnforcement.Failed -> "ENF FAILED"
    device.enforcement == DeviceEnforcement.Reconciling -> "RECONCILING"
    device.trust == TrustState.Revoked -> "REVOKED"
    device.alerts.warning > 0 -> "WARNING"
    device.freshness is DataFreshness.Unknown -> "UNKNOWN"
    device.enforcement == DeviceEnforcement.Quarantined -> "QUARANTINED"
    device.enforcement == DeviceEnforcement.Paused -> "PAUSED"
    device.trust == TrustState.Unverified -> "UNVERIFIED"
    device.freshness is DataFreshness.Stale -> "STALE"
    else -> null
}

/**
 * The secondary line: presence, trust and scope, kept as three separate
 * words so the reader can see they are three separate facts.
 */
fun deviceSubtitle(device: DeviceListItem): String =
    "${device.presence.label} · ${device.trust.label} · ${device.scope}"
