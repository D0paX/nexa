package com.example.nexa.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The NEXA icon registry.
 *
 * One family — Material Outlined — chosen for its precise, technical line
 * quality. Every icon in the application is named here rather than reached
 * for at a call site, so the family stays coherent and a symbol's meaning is
 * defined in exactly one place. Directional icons use the auto-mirrored
 * variants so they resolve correctly under RTL.
 */
object NexaIcons {

    // --- Root navigation ---
    val Overview: ImageVector = Icons.Outlined.Home
    val Devices: ImageVector = Icons.Outlined.Devices
    val Alerts: ImageVector = Icons.Outlined.Notifications
    val Audit: ImageVector = Icons.Outlined.History

    // --- Navigation controls ---
    val Back: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack
    val Forward: ImageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight

    // --- Severity ---
    val Critical: ImageVector = Icons.Outlined.ErrorOutline
    val Warning: ImageVector = Icons.Outlined.WarningAmber
    val Information: ImageVector = Icons.Outlined.Info

    // --- Identity & trust (Phase 2) ---
    /** A cryptographic identity as a subject — not the device that carries it. */
    val Identity: ImageVector = Icons.Outlined.Fingerprint

    /** A credential in an identity's lifecycle. Never used for secret material. */
    val Credential: ImageVector = Icons.Outlined.VpnKey

    /** Trust withdrawn — distinct in shape from a mere warning. */
    val Revoked: ImageVector = Icons.Outlined.GppBad

    // --- Status ---
    val Secure: ImageVector = Icons.Outlined.VerifiedUser
    val Enforcing: ImageVector = Icons.Outlined.Shield
    val Offline: ImageVector = Icons.Outlined.CloudOff
    val Simulated: ImageVector = Icons.Outlined.Science
    val Paused: ImageVector = Icons.Outlined.PauseCircleOutline
    val Unknown: ImageVector = Icons.Outlined.HelpOutline
    val Stale: ImageVector = Icons.Outlined.HistoryToggleOff
    val Unavailable: ImageVector = Icons.Outlined.SyncProblem
    val Empty: ImageVector = Icons.Outlined.Inbox

    // --- Actions ---
    val Quarantine: ImageVector = Icons.Outlined.Block
    val Release: ImageVector = Icons.Outlined.LockOpen
    val Acknowledge: ImageVector = Icons.Outlined.Check
    val Cancel: ImageVector = Icons.Outlined.Close
    val Reverification: ImageVector = Icons.Outlined.Autorenew
    val Pending: ImageVector = Icons.Outlined.Schedule
    val Refresh: ImageVector = Icons.Outlined.Refresh

    // --- Inventory controls ---
    val Search: ImageVector = Icons.Outlined.Search
    val Filter: ImageVector = Icons.Outlined.FilterList
    val Sort: ImageVector = Icons.Outlined.SwapVert

    /** Severity is never carried by color alone — every level has a shape. */
    fun forSeverity(severity: String): ImageVector = when (severity) {
        "CRITICAL" -> Critical
        "WARNING" -> Warning
        else -> Information
    }
}
