package com.example.nexa.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The NEXA security state vocabulary.
 *
 * Every state the interface can express is declared once, together with the
 * color it takes on light glass, the color it takes on a charcoal anchor,
 * the icon that gives it a shape, and the word that names it. Screens ask
 * for a *state*, never for a color — which is what keeps meaning and brand
 * from drifting into each other, and what guarantees no state is ever
 * carried by color alone.
 */
enum class NexaStatus {
    Secure,
    Permitted,
    Verified,
    Information,
    Simulation,
    Warning,
    Degraded,
    Danger,
    Critical,
    Offline,
    Paused,
    Unknown,
    Disabled
}

/** How a state presents itself on a given surface. */
data class NexaStatusStyle(
    val label: String,
    val icon: ImageVector,
    val onLight: Color,
    val onDark: Color
) {
    /** The state's color for the surface it is being drawn on. */
    fun color(onDarkSurface: Boolean): Color = if (onDarkSurface) onDark else onLight
}

val NexaStatus.style: NexaStatusStyle
    get() = when (this) {
        NexaStatus.Secure -> NexaStatusStyle("SECURE", NexaIcons.Secure, NexaSecure, NexaSecureOnDark)
        NexaStatus.Permitted -> NexaStatusStyle("PERMITTED", NexaIcons.Acknowledge, NexaSecure, NexaSecureOnDark)
        NexaStatus.Verified -> NexaStatusStyle("VERIFIED", NexaIcons.Secure, NexaSecure, NexaSecureOnDark)
        NexaStatus.Information -> NexaStatusStyle("INFO", NexaIcons.Information, NexaInformation, NexaInformationOnDark)
        NexaStatus.Simulation -> NexaStatusStyle("SIMULATION", NexaIcons.Simulated, NexaSimulation, NexaSimulationOnDark)
        NexaStatus.Warning -> NexaStatusStyle("WARNING", NexaIcons.Warning, NexaWarning, NexaWarningOnDark)
        NexaStatus.Degraded -> NexaStatusStyle("DEGRADED", NexaIcons.Warning, NexaDegraded, NexaDegradedOnDark)
        NexaStatus.Danger -> NexaStatusStyle("DANGER", NexaIcons.Quarantine, NexaDanger, NexaDangerOnDark)
        NexaStatus.Critical -> NexaStatusStyle("CRITICAL", NexaIcons.Critical, NexaCritical, NexaDangerOnDark)
        NexaStatus.Offline -> NexaStatusStyle("OFFLINE", NexaIcons.Offline, NexaOffline, NexaOfflineOnDark)
        NexaStatus.Paused -> NexaStatusStyle("PAUSED", NexaIcons.Paused, NexaPaused, NexaPausedOnDark)
        NexaStatus.Unknown -> NexaStatusStyle("UNKNOWN", NexaIcons.Unknown, NexaUnknown, NexaUnknownOnDark)
        NexaStatus.Disabled -> NexaStatusStyle("DISABLED", NexaIcons.Paused, NexaDisabled, NexaDisabled)
    }

/**
 * Maps a Phase 3 SecurityEvent severity onto the display vocabulary.
 * Unrecognized severities fall to [NexaStatus.Unknown] rather than being
 * quietly presented as benign.
 */
fun statusForSeverity(severity: String): NexaStatus = when (severity.uppercase()) {
    "CRITICAL" -> NexaStatus.Critical
    "DANGER" -> NexaStatus.Danger
    "WARNING" -> NexaStatus.Warning
    "INFORMATION", "INFO" -> NexaStatus.Information
    else -> NexaStatus.Unknown
}
