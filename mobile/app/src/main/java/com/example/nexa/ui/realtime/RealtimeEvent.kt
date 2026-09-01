package com.example.nexa.ui.realtime

import com.example.nexa.ui.alerts.AlertLifecycle
import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.DeliveryState
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.devices.Presence
import com.example.nexa.ui.enforcement.ExecutionState

/**
 * The realtime event contract.
 *
 * An event is a **change**, not a snapshot. It says what moved and by how
 * much; it does not describe the whole object, and nothing in the client
 * rebuilds an object from one. The client starts from a snapshot and applies
 * changes on top — which is the only arrangement where a missed event is a
 * detectable problem rather than a silently wrong screen.
 *
 * Everything arriving on this stream is untrusted. It has an authenticated
 * transport in front of it in production, but the client still validates
 * every field, because a compromised or buggy publisher is exactly the case
 * where a security console must not become confidently wrong.
 *
 * Two things this type deliberately cannot express:
 *
 *  - a command. There is no event that asks the client to do anything. Every
 *    event reports that something already happened.
 *  - an authorization. Trust changing does not grant anything, and an action
 *    reaching a state does not mean the operator may act on it.
 */

/** The only envelope version this build understands. */
const val REALTIME_SCHEMA_VERSION = 1

/**
 * What changed.
 *
 * Only changes Phase 1-4 actually produce. Nothing here is a convenience
 * type invented to make the stream look richer.
 */
enum class RealtimeEventType {
    // --- Phase 1: observation ---
    DeviceObserved,
    DeviceAddressChanged,
    DevicePresenceChanged,

    // --- Phase 2: identity and trust ---
    TrustChanged,
    VerificationCompleted,
    ReverificationRequested,
    IdentityRevoked,

    // --- Phase 3: alert lifecycle ---
    AlertRaised,
    AlertAcknowledged,
    AlertResolved,
    AlertIgnored,

    // --- Phase 3: notification delivery, a separate axis ---
    DeliveryStateChanged,

    // --- Phase 4: execution and enforcement ---
    ActionStateChanged,
    DeviceEnforcementChanged,
    CircuitBreakerChanged;

    companion object {
        /** Exact match. A security enum is never fuzzily parsed. */
        fun fromWire(value: String?): RealtimeEventType? = when (value) {
            "DEVICE_OBSERVED" -> DeviceObserved
            "DEVICE_ADDRESS_CHANGED" -> DeviceAddressChanged
            "DEVICE_PRESENCE_CHANGED" -> DevicePresenceChanged
            "TRUST_CHANGED" -> TrustChanged
            "VERIFICATION_COMPLETED" -> VerificationCompleted
            "REVERIFICATION_REQUESTED" -> ReverificationRequested
            "IDENTITY_REVOKED" -> IdentityRevoked
            "ALERT_RAISED" -> AlertRaised
            "ALERT_ACKNOWLEDGED" -> AlertAcknowledged
            "ALERT_RESOLVED" -> AlertResolved
            "ALERT_IGNORED" -> AlertIgnored
            "DELIVERY_STATE_CHANGED" -> DeliveryStateChanged
            "ACTION_STATE_CHANGED" -> ActionStateChanged
            "DEVICE_ENFORCEMENT_CHANGED" -> DeviceEnforcementChanged
            "CIRCUIT_BREAKER_CHANGED" -> CircuitBreakerChanged
            else -> null
        }
    }
}

/**
 * What the change is about.
 *
 * Stable identifiers only. There is no variant carrying an address, because
 * an address identifies nothing — the same rule the rest of the product holds
 * to, restated here because a stream of observations is the easiest place to
 * forget it.
 */
sealed interface RealtimeSubject {
    data class Device(val deviceId: String) : RealtimeSubject
    data class Identity(val identityId: String) : RealtimeSubject
    data class Alert(val alertId: String) : RealtimeSubject
    data class Delivery(val deliveryId: String) : RealtimeSubject
    data class Action(val actionId: String) : RealtimeSubject

    /** The enforcement subsystem itself, for breaker changes. */
    data object Subsystem : RealtimeSubject

    val identifier: String?
        get() = when (this) {
            is Device -> deviceId
            is Identity -> identityId
            is Alert -> alertId
            is Delivery -> deliveryId
            is Action -> actionId
            Subsystem -> null
        }
}

/**
 * The change itself, typed.
 *
 * Parsed into these shapes rather than left as a string map, so a reducer can
 * never read a field the validator did not check.
 */
sealed interface RealtimePayload {

    /** Phase 1 observation. Carries an address as *context*, never as identity. */
    data class DeviceObservation(
        val presence: Presence,
        val observedAddress: String?,
        val lastSeenLabel: String
    ) : RealtimePayload

    data class DeviceEnforcementChange(
        val enforcement: DeviceEnforcement
    ) : RealtimePayload

    /**
     * Phase 2 trust standing.
     *
     * Trust and authorization are separate facts and this carries only the
     * first. Nothing downstream may read a trust change as permission.
     */
    data class TrustChange(
        val trust: TrustState
    ) : RealtimePayload

    /** Phase 3 alert lifecycle. Never produced by a delivery event. */
    data class AlertLifecycleChange(
        val lifecycle: AlertLifecycle
    ) : RealtimePayload

    /** Phase 3 delivery. Never changes an alert. */
    data class DeliveryChange(
        val state: DeliveryState,
        val attemptCount: Int,
        val failureReason: String?
    ) : RealtimePayload

    /**
     * Phase 4 execution.
     *
     * [reconciled] is carried apart from [state] because success and
     * reconciliation are different claims, and collapsing them is how a
     * console starts telling operators the firewall agrees with it when
     * nothing has checked.
     */
    data class ActionStateChange(
        val state: ExecutionState,
        val mode: ExecutionMode,
        val reconciled: Boolean,
        val actionCode: String
    ) : RealtimePayload

    data class CircuitBreakerChange(
        val breaker: CircuitBreakerState
    ) : RealtimePayload
}

/**
 * A validated realtime event.
 *
 * Only constructible through [RealtimeEventParser], so holding one means
 * every field passed validation.
 *
 * [sequence] is the publisher's global ordering. It is the only ordering the
 * client trusts — arrival time says nothing about what happened first, and a
 * console that ordered security events by when packets landed would show a
 * rollback before the failure that caused it.
 */
data class RealtimeEvent(
    val eventId: String,
    val schemaVersion: Int,
    val sequence: Long,
    val type: RealtimeEventType,
    val occurredAtEpochSeconds: Long,
    /** The NetworkScope the change belongs to. Events never cross scopes. */
    val scope: String,
    val subject: RealtimeSubject,
    val payload: RealtimePayload
)
