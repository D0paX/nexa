package com.example.nexa.ui.common

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaStatus

/** The kinds of security history NEXA reports, system-wide and per device. */
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
 * A single security event: what happened, to whom, when, and what state it
 * ended in.
 *
 * [executionMode] is carried structurally rather than implied by a color,
 * so a simulated action can never be read as a live one — in the feed, and
 * later in the audit history. A null mode means the event is not an action
 * execution at all (an alert being raised, a device appearing), not that it
 * was live.
 */
data class ActivityEntry(
    val id: String,
    val kind: ActivityKind,
    val title: String,
    val target: String,
    val timeAgo: String,
    val status: NexaStatus,
    val executionMode: ExecutionMode? = null
) {
    /** True only when this event records a simulated execution. */
    val isSimulated: Boolean get() = executionMode == ExecutionMode.AuditOnly

    /** True only when this event records a real firewall mutation. */
    val isLiveEnforcement: Boolean get() = executionMode == ExecutionMode.Enforce
}

/** The shape a piece of security history takes. */
val ActivityKind.icon: ImageVector
    get() = when (this) {
        ActivityKind.AlertRaised -> NexaIcons.Critical
        ActivityKind.AlertAcknowledged -> NexaIcons.Acknowledge
        ActivityKind.DeviceAppeared -> NexaIcons.Devices
        ActivityKind.TrustChanged -> NexaIcons.Secure
        ActivityKind.ReverificationRequired -> NexaIcons.Reverification
        ActivityKind.EnforcementStarted -> NexaIcons.Pending
        ActivityKind.EnforcementCompleted -> NexaIcons.Quarantine
        ActivityKind.ReleaseCompleted -> NexaIcons.Release
        ActivityKind.ActionFailed -> NexaIcons.Critical
    }
