package com.example.nexa.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
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

    // --- Status ---
    val Secure: ImageVector = Icons.Outlined.VerifiedUser
    val Enforcing: ImageVector = Icons.Outlined.Shield
    val Offline: ImageVector = Icons.Outlined.CloudOff
    val Simulated: ImageVector = Icons.Outlined.Science

    // --- Actions ---
    val Quarantine: ImageVector = Icons.Outlined.Block
    val Acknowledge: ImageVector = Icons.Outlined.Check
    val Cancel: ImageVector = Icons.Outlined.Close

    /** Severity is never carried by color alone — every level has a shape. */
    fun forSeverity(severity: String): ImageVector = when (severity) {
        "CRITICAL" -> Critical
        "WARNING" -> Warning
        else -> Information
    }
}
