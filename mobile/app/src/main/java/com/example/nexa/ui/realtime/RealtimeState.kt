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
 * What realtime knows on top of the snapshot.
 *
 * Deliberately **overlays**, not objects. An event is a change, so the client
 * records the change and leaves the object where it came from — the snapshot.
 * A screen renders its snapshot with the overlay applied.
 *
 * The alternative, rebuilding whole objects out of a stream, is what makes a
 * missed event invisible: the reconstruction still looks complete, and
 * nothing indicates which parts of it are fiction.
 *
 * Note what has no field here at all: authorization. Trust standing is
 * recorded because Phase 2 publishes it; permission to act is not, because
 * the client never learns it from a stream and must never appear to.
 */

/** Phase 1 observation and Phase 4 enforcement for one device record. */
data class DeviceOverlay(
    val presence: Presence? = null,
    val enforcement: DeviceEnforcement? = null,
    /** Observation context. Never an identity, never a key. */
    val observedAddress: String? = null,
    val lastSeenLabel: String? = null,
    /** The scope the change was published in. */
    val scope: String
)

/** Phase 2 trust standing. Says nothing about what may be done. */
data class IdentityOverlay(
    val trust: TrustState,
    val scope: String
)

/** Phase 3 alert lifecycle. Never written by a delivery event. */
data class AlertOverlay(
    val lifecycle: AlertLifecycle,
    val scope: String
)

/** Phase 3 delivery. Never written by an alert event. */
data class DeliveryOverlay(
    val state: DeliveryState,
    val attemptCount: Int,
    val failureReason: String?,
    val scope: String
)

/**
 * Phase 4 execution.
 *
 * [reconciled] is its own field. Reaching [ExecutionState.Succeeded] is the
 * pipeline reporting the action finished; reconciliation is a separate check
 * that the system actually agrees. Collapsing them would let the console
 * claim the firewall matches when nothing has looked.
 */
data class ActionOverlay(
    val state: ExecutionState,
    val mode: ExecutionMode,
    val reconciled: Boolean,
    val actionCode: String,
    val scope: String
)

/** One applied event, kept so Audit can show live history. Bounded. */
data class RealtimeHistoryEntry(
    val eventId: String,
    val sequence: Long,
    val type: RealtimeEventType,
    val subject: RealtimeSubject,
    val scope: String,
    val occurredAtEpochSeconds: Long
)

/**
 * The shared read model.
 *
 * Immutable. The reducer returns a new instance, so a screen reading it
 * during an update can never see a half-applied change.
 */
data class RealtimeState(
    val devices: Map<String, DeviceOverlay> = emptyMap(),
    val identities: Map<String, IdentityOverlay> = emptyMap(),
    val alerts: Map<String, AlertOverlay> = emptyMap(),
    val deliveries: Map<String, DeliveryOverlay> = emptyMap(),
    val actions: Map<String, ActionOverlay> = emptyMap(),
    /** Null until the stream reports it. Absence is not "closed". */
    val circuitBreaker: CircuitBreakerState? = null,
    val history: List<RealtimeHistoryEntry> = emptyList(),
    val lastAppliedSequence: Long = -1L,
    val appliedCount: Int = 0
) {
    val hasAnyUpdate: Boolean get() = appliedCount > 0
}

/** How much live history is retained. An unbounded buffer is a slow leak. */
const val REALTIME_HISTORY_LIMIT = 100

// ============================================================
// TRANSITIONS
// ============================================================

/**
 * Whether an execution may move from one state to another.
 *
 * The Phase 4 lifecycle, written down. Two consequences matter most:
 *
 *  - A request cannot become a success. Submitting an action and it working
 *    are different events, and no amount of optimism in the client may join
 *    them.
 *  - Terminal states are terminal. Once an execution has succeeded, been
 *    denied, rolled back or failed to roll back, a later event claiming
 *    otherwise is refused rather than applied.
 *
 * [from] is null when the client joined mid-flight and has not seen this
 * action before, in which case any reported state is accepted as the
 * starting point.
 */
fun isLegalExecutionTransition(from: ExecutionState?, to: ExecutionState): Boolean {
    if (from == null) return true
    if (from == to) return true
    if (from.isFinalForRealtime) return false

    return when (from) {
        ExecutionState.Requested -> to in setOf(
            ExecutionState.Authorized,
            ExecutionState.Denied,
            ExecutionState.Executing,
            ExecutionState.Failed,
            ExecutionState.Unknown
        )
        ExecutionState.Authorized -> to in setOf(
            ExecutionState.Executing,
            ExecutionState.Denied,
            ExecutionState.Failed,
            ExecutionState.Unknown
        )
        ExecutionState.Executing -> to in setOf(
            ExecutionState.Reconciling,
            ExecutionState.Succeeded,
            ExecutionState.Failed,
            ExecutionState.Unknown
        )
        ExecutionState.Reconciling -> to in setOf(
            ExecutionState.Succeeded,
            ExecutionState.Failed,
            ExecutionState.Unknown
        )
        ExecutionState.Failed -> to in setOf(
            ExecutionState.RollbackRequested,
            ExecutionState.Unknown
        )
        ExecutionState.RollbackRequested -> to in setOf(
            ExecutionState.RolledBack,
            ExecutionState.RollbackFailed,
            ExecutionState.Unknown
        )
        // Unknown means the client lost track; any authoritative report is an
        // improvement on that.
        ExecutionState.Unknown -> true
        else -> false
    }
}

/** States an execution does not leave. */
private val ExecutionState.isFinalForRealtime: Boolean
    get() = this == ExecutionState.Succeeded ||
        this == ExecutionState.Denied ||
        this == ExecutionState.RolledBack ||
        this == ExecutionState.RollbackFailed
