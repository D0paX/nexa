package com.example.nexa.ui.realtime

/**
 * The one place an event becomes state.
 *
 * Pure: `state + event -> state`. Every screen reads the result rather than
 * interpreting the event itself, which is what stops two surfaces reaching
 * different conclusions from the same packet.
 *
 * Structural separations the shape of this function enforces, rather than
 * relying on anyone remembering them:
 *
 *   a delivery event writes only `deliveries`
 *   an alert event writes only `alerts`
 *   a trust event writes only `identities`
 *   nothing writes an authorization, because there is no field for one
 */
object RealtimeReducer {

    fun reduce(state: RealtimeState, event: RealtimeEvent): ReduceResult {
        val next = when (val payload = event.payload) {
            is RealtimePayload.DeviceObservation -> reduceObservation(state, event, payload)
            is RealtimePayload.DeviceEnforcementChange -> reduceEnforcement(state, event, payload)
            is RealtimePayload.TrustChange -> reduceTrust(state, event, payload)
            is RealtimePayload.AlertLifecycleChange -> reduceAlert(state, event, payload)
            is RealtimePayload.DeliveryChange -> reduceDelivery(state, event, payload)
            is RealtimePayload.ActionStateChange -> reduceAction(state, event, payload)
            is RealtimePayload.CircuitBreakerChange -> reduceBreaker(state, event, payload)
        }
        return next
    }

    // ============================================================
    // PHASE 1 — OBSERVATION
    // ============================================================

    /**
     * An observation updates the device it names.
     *
     * Keyed by the device record id. The observed address is stored beside it
     * as context and is never a key — a device that changes address is the
     * same device, and a console that keyed on the address would quietly
     * split it into two.
     */
    private fun reduceObservation(
        state: RealtimeState,
        event: RealtimeEvent,
        payload: RealtimePayload.DeviceObservation
    ): ReduceResult {
        val id = (event.subject as? RealtimeSubject.Device)?.deviceId
            ?: return ReduceResult.Ignored(state, IgnoreReason.SubjectMismatch)
        val existing = state.devices[id]
        val overlay = (existing ?: DeviceOverlay(scope = event.scope)).copy(
            presence = payload.presence,
            observedAddress = payload.observedAddress,
            lastSeenLabel = payload.lastSeenLabel,
            scope = event.scope
        )
        return applied(state, event, state.copy(devices = state.devices + (id to overlay)))
    }

    private fun reduceEnforcement(
        state: RealtimeState,
        event: RealtimeEvent,
        payload: RealtimePayload.DeviceEnforcementChange
    ): ReduceResult {
        val id = (event.subject as? RealtimeSubject.Device)?.deviceId
            ?: return ReduceResult.Ignored(state, IgnoreReason.SubjectMismatch)
        val existing = state.devices[id]
        val overlay = (existing ?: DeviceOverlay(scope = event.scope)).copy(
            enforcement = payload.enforcement,
            scope = event.scope
        )
        return applied(state, event, state.copy(devices = state.devices + (id to overlay)))
    }

    // ============================================================
    // PHASE 2 — TRUST
    // ============================================================

    /**
     * Trust standing, and nothing else.
     *
     * Writes one map. It cannot grant an action, because the store has no
     * notion of one — availability is recomputed by the action flow against
     * authorization, which this stream does not carry.
     */
    private fun reduceTrust(
        state: RealtimeState,
        event: RealtimeEvent,
        payload: RealtimePayload.TrustChange
    ): ReduceResult {
        val id = (event.subject as? RealtimeSubject.Identity)?.identityId
            ?: return ReduceResult.Ignored(state, IgnoreReason.SubjectMismatch)
        val overlay = IdentityOverlay(trust = payload.trust, scope = event.scope)
        return applied(state, event, state.copy(identities = state.identities + (id to overlay)))
    }

    // ============================================================
    // PHASE 3 — ALERTS, AND SEPARATELY, DELIVERY
    // ============================================================

    private fun reduceAlert(
        state: RealtimeState,
        event: RealtimeEvent,
        payload: RealtimePayload.AlertLifecycleChange
    ): ReduceResult {
        val id = (event.subject as? RealtimeSubject.Alert)?.alertId
            ?: return ReduceResult.Ignored(state, IgnoreReason.SubjectMismatch)
        val overlay = AlertOverlay(lifecycle = payload.lifecycle, scope = event.scope)
        return applied(state, event, state.copy(alerts = state.alerts + (id to overlay)))
    }

    /**
     * Delivery, which touches no alert.
     *
     * A notification failing says nothing about the incident it was about,
     * and the reducer cannot say otherwise because it writes a different map.
     */
    private fun reduceDelivery(
        state: RealtimeState,
        event: RealtimeEvent,
        payload: RealtimePayload.DeliveryChange
    ): ReduceResult {
        val id = (event.subject as? RealtimeSubject.Delivery)?.deliveryId
            ?: return ReduceResult.Ignored(state, IgnoreReason.SubjectMismatch)
        val overlay = DeliveryOverlay(
            state = payload.state,
            attemptCount = payload.attemptCount,
            failureReason = payload.failureReason,
            scope = event.scope
        )
        return applied(state, event, state.copy(deliveries = state.deliveries + (id to overlay)))
    }

    // ============================================================
    // PHASE 4 — EXECUTION
    // ============================================================

    /**
     * An execution state change, checked against the lifecycle.
     *
     * An illegal transition is refused rather than applied. A stream claiming
     * a request went straight to success, or that a completed action has
     * un-completed, is either broken or hostile, and either way the client
     * keeps what it had.
     *
     * The mode is taken from the event every time and never inherited. A
     * simulation seen earlier must not colour a live run, and a live run must
     * not be quietly recorded as a simulation.
     */
    private fun reduceAction(
        state: RealtimeState,
        event: RealtimeEvent,
        payload: RealtimePayload.ActionStateChange
    ): ReduceResult {
        val id = (event.subject as? RealtimeSubject.Action)?.actionId
            ?: return ReduceResult.Ignored(state, IgnoreReason.SubjectMismatch)

        val existing = state.actions[id]
        if (!isLegalExecutionTransition(existing?.state, payload.state)) {
            return ReduceResult.Ignored(state, IgnoreReason.IllegalTransition)
        }

        val overlay = ActionOverlay(
            state = payload.state,
            mode = payload.mode,
            // Reconciliation is only ever what the event reports. It is never
            // inferred from the execution having succeeded.
            reconciled = payload.reconciled,
            actionCode = payload.actionCode,
            scope = event.scope
        )
        return applied(state, event, state.copy(actions = state.actions + (id to overlay)))
    }

    private fun reduceBreaker(
        state: RealtimeState,
        event: RealtimeEvent,
        payload: RealtimePayload.CircuitBreakerChange
    ): ReduceResult =
        applied(state, event, state.copy(circuitBreaker = payload.breaker))

    // ============================================================

    /**
     * Records the application and appends one history entry.
     *
     * One event id yields one entry, because the sequencer has already
     * guaranteed the reducer sees each event once. History is trimmed to a
     * fixed limit rather than growing for the life of the process.
     */
    private fun applied(
        previous: RealtimeState,
        event: RealtimeEvent,
        next: RealtimeState
    ): ReduceResult {
        val entry = RealtimeHistoryEntry(
            eventId = event.eventId,
            sequence = event.sequence,
            type = event.type,
            subject = event.subject,
            scope = event.scope,
            occurredAtEpochSeconds = event.occurredAtEpochSeconds
        )
        val history = (listOf(entry) + next.history).take(REALTIME_HISTORY_LIMIT)
        return ReduceResult.Applied(
            next.copy(
                history = history,
                lastAppliedSequence = event.sequence,
                appliedCount = previous.appliedCount + 1
            )
        )
    }
}

/** Why an event changed nothing. */
enum class IgnoreReason {
    /** The subject type did not match the payload the event carried. */
    SubjectMismatch,

    /** The Phase 4 lifecycle does not allow the reported move. */
    IllegalTransition
}

sealed interface ReduceResult {
    /** The state after the event, whether or not anything changed. */
    val next: RealtimeState

    data class Applied(override val next: RealtimeState) : ReduceResult
    data class Ignored(override val next: RealtimeState, val reason: IgnoreReason) : ReduceResult
}
