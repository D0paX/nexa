package com.example.nexa

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Root contexts
@Serializable data object Overview : NavKey
@Serializable data object Devices : NavKey
@Serializable data object Alerts : NavKey
@Serializable data object Audit : NavKey

// Drill-downs
@Serializable data class DeviceDetail(val mac: String) : NavKey
@Serializable data class AlertDetail(val id: String) : NavKey

/** The cryptographic identity inventory. Reached from Devices, not a root tab. */
@Serializable data object Identities : NavKey

/**
 * One cryptographic identity.
 *
 * Keyed by the Phase 2 identity identifier rather than by a device address:
 * an identity is the subject here, and a MAC or IP is only context.
 */
@Serializable data class IdentityDetail(val identityId: String) : NavKey

/**
 * A requested action, carried to the Phase 4 confirmation flow.
 *
 * [scope] travels with the target because the same MAC in two NetworkScopes
 * is not the same logical target, and [identityId] travels with it when the
 * action concerns a cryptographic identity. Neither is optional context: an
 * action must never be reconstructed from an address alone.
 */
@Serializable data class ActionConfirmation(
    val action: String,
    val targetMac: String,
    val actionLabel: String,
    val scope: String,
    val identityId: String? = null
) : NavKey
