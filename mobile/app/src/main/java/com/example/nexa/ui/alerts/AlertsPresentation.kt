package com.example.nexa.ui.alerts

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaStatus
import com.example.nexa.ui.common.DeliveryState
import com.example.nexa.ui.common.label

/**
 * How alert state is presented.
 *
 * Severity, lifecycle and delivery each map to their own word, shape and
 * color. They are resolved here rather than inside composables so the three
 * axes cannot be quietly merged during layout.
 */

// --- Severity ---

val AlertSeverity.status: NexaStatus
    get() = when (this) {
        AlertSeverity.Critical -> NexaStatus.Critical
        AlertSeverity.Danger -> NexaStatus.Danger
        AlertSeverity.Warning -> NexaStatus.Warning
        AlertSeverity.Information -> NexaStatus.Information
    }

val AlertSeverity.label: String
    get() = when (this) {
        AlertSeverity.Critical -> "Critical"
        AlertSeverity.Danger -> "Danger"
        AlertSeverity.Warning -> "Warning"
        AlertSeverity.Information -> "Information"
    }

val AlertSeverity.icon: ImageVector
    get() = when (this) {
        AlertSeverity.Critical -> NexaIcons.Critical
        AlertSeverity.Danger -> NexaIcons.Quarantine
        AlertSeverity.Warning -> NexaIcons.Warning
        AlertSeverity.Information -> NexaIcons.Information
    }

/**
 * Material weight by severity: critical earns a denser surface, information
 * stays quiet. Closed alerts drop to the quietest surface whatever their
 * severity was — history should not shout.
 */
fun surfaceFor(alert: AlertListItem): GlassVariant = when {
    !alert.lifecycle.isOpen -> GlassVariant.Standard
    alert.severity == AlertSeverity.Critical -> GlassVariant.Strong
    alert.severity == AlertSeverity.Danger -> GlassVariant.Strong
    else -> GlassVariant.Standard
}

// --- Lifecycle ---

val AlertLifecycle.status: NexaStatus
    get() = when (this) {
        AlertLifecycle.New -> NexaStatus.Warning
        AlertLifecycle.Acknowledged -> NexaStatus.Information
        AlertLifecycle.Resolved -> NexaStatus.Secure
        AlertLifecycle.Ignored -> NexaStatus.Paused
    }

val AlertLifecycle.label: String
    get() = when (this) {
        AlertLifecycle.New -> "New"
        AlertLifecycle.Acknowledged -> "Acknowledged"
        AlertLifecycle.Resolved -> "Resolved"
        AlertLifecycle.Ignored -> "Ignored"
    }

val AlertLifecycle.icon: ImageVector
    get() = when (this) {
        AlertLifecycle.New -> NexaIcons.Critical
        AlertLifecycle.Acknowledged -> NexaIcons.Acknowledge
        AlertLifecycle.Resolved -> NexaIcons.Resolve
        AlertLifecycle.Ignored -> NexaIcons.Ignore
    }

/** Says what the state means, so "acknowledged" is never read as "closed". */
val AlertLifecycle.explanation: String
    get() = when (this) {
        AlertLifecycle.New -> "This alert has not been picked up by an operator."
        AlertLifecycle.Acknowledged -> "An operator has seen this alert. It is not resolved."
        AlertLifecycle.Resolved -> "This incident has been closed."
        AlertLifecycle.Ignored -> "This alert was set aside without being resolved."
    }

// --- Notification delivery: a separate axis ---
//
// The state's label, tone and icon are shared vocabulary and live in
// com.example.nexa.ui.common.Delivery. Only the alert-context wording is
// decided here.

/**
 * Delivery wording, phrased so it can never be read as the alert's own
 * state. Every string names the notification explicitly.
 */
val DeliveryState.explanation: String
    get() = when (this) {
        DeliveryState.Delivered -> "The notification for this alert was delivered."
        DeliveryState.Sent -> "The notification was sent and has not been confirmed delivered."
        DeliveryState.Pending -> "The notification has not been sent yet."
        DeliveryState.Retrying -> "Notification delivery failed and is being retried."
        DeliveryState.Failed -> "Notification delivery failed. The alert itself is unaffected."
        DeliveryState.Exhausted -> "Notification delivery failed and no further attempts will be made. The alert itself is unaffected."
        DeliveryState.Unavailable -> "Notification delivery state could not be read."
    }

// --- Row presentation ---

/**
 * The single badge a row shows: always the alert's own lifecycle, never its
 * delivery state. Delivery gets its own separate marker so the two can
 * never be mistaken for one another at a glance.
 */
fun rowLifecycleBadge(alert: AlertListItem): NexaStatus = alert.lifecycle.status

/** Shown only when notification delivery needs attention. */
fun rowDeliveryWarning(alert: AlertListItem): String? = when {
    alert.delivery.isFailure -> "NOTIFY ${alert.delivery.label.uppercase()}"
    alert.delivery == DeliveryState.Retrying -> "NOTIFY RETRYING"
    else -> null
}

/**
 * The row subtitle: target and, when present, scope — kept factual and
 * free of any claim about severity or state.
 */
fun alertSubtitle(alert: AlertListItem): String {
    val device = alert.target.deviceRef
    return when {
        device != null -> "${device.label} · ${device.scope}"
        alert.target is AlertTarget.ScopeTarget -> (alert.target as AlertTarget.ScopeTarget).scope
        else -> "Target unresolved"
    }
}
