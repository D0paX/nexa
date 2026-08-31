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
 */
data class ActivityEntry(
    val id: String,
    val kind: ActivityKind,
    val title: String,
    val target: String,
    val timeAgo: String,
    val status: NexaStatus
)

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
