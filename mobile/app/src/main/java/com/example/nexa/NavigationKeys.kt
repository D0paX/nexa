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

/**
 * One historical security record, addressed by its stable event id.
 *
 * The id is the one the event store assigned, not a list position, so the
 * route stays valid as history grows and is the seam a future deep link
 * resolves against.
 */
@Serializable data class AuditDetail(val eventId: String) : NavKey

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
 * A prepared enforcement action, addressed by its context handle.
 *
 * The route deliberately carries only [actionContextId] rather than the
 * target's fields. An action's target is assembled once, authoritatively, by
 * the preparation step; passing loose fields through navigation would let a
 * confirmation screen rebuild a target out of an address and an assumption,
 * which is precisely the stale-IP class of mistake Phase 4 exists to
 * prevent.
 *
 * If the handle cannot be resolved — after process death, for example — the
 * flow reports the context as unavailable and the operator starts again from
 * the target. It never reconstructs one.
 */
@Serializable data class ActionConfirmation(val actionContextId: String) : NavKey
