package com.example.nexa.ui.overview

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaStatus
import com.example.nexa.ui.common.DataFreshness

/**
 * How command-center state is presented.
 *
 * The mapping from domain state to the shared vocabulary lives here rather
 * than in the design system, so the theme stays independent of any feature,
 * and rather than in composables, so it can be reasoned about and tested.
 */

/** The state vocabulary word for a posture — carries its color and label. */
val SecurityPosture.status: NexaStatus
    get() = when (this) {
        SecurityPosture.Secure -> NexaStatus.Secure
        SecurityPosture.Enforcing -> NexaStatus.Permitted
        SecurityPosture.Degraded -> NexaStatus.Degraded
        SecurityPosture.Paused -> NexaStatus.Paused
        SecurityPosture.Critical -> NexaStatus.Critical
        SecurityPosture.Unknown -> NexaStatus.Unknown
    }

/** The word shown on the hero. */
val SecurityPosture.label: String
    get() = when (this) {
        SecurityPosture.Secure -> "SECURE"
        SecurityPosture.Enforcing -> "ENFORCING"
        SecurityPosture.Degraded -> "DEGRADED"
        SecurityPosture.Paused -> "PAUSED"
        SecurityPosture.Critical -> "CRITICAL"
        SecurityPosture.Unknown -> "UNKNOWN"
    }

/** Posture is legible without color: every state also has a shape. */
val SecurityPosture.icon: ImageVector
    get() = when (this) {
        SecurityPosture.Secure -> NexaIcons.Secure
        SecurityPosture.Enforcing -> NexaIcons.Enforcing
        SecurityPosture.Degraded -> NexaIcons.Warning
        SecurityPosture.Paused -> NexaIcons.Paused
        SecurityPosture.Critical -> NexaIcons.Critical
        SecurityPosture.Unknown -> NexaIcons.Unknown
    }

// Activity icons and freshness wording are shared vocabulary — see
// com.example.nexa.ui.common.
