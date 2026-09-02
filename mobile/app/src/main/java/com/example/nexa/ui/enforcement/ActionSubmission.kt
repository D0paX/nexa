package com.example.nexa.ui.enforcement

import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.realtime.ActionOverlay
import com.example.nexa.ui.realtime.PreviewRealtimeScenario
import com.example.nexa.ui.realtime.RealtimeStore
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * One logical action per prepared context.
 *
 * This is the idempotency boundary, and it is deliberately not a disabled
 * button. A button is a picture of a decision; the decision itself has to
 * live somewhere that survives a recomposition, a rotation, a navigation away
 * and back, and two taps landing in the same frame.
 *
 * A prepared context is submitted at most once. The second caller does not
 * get a second action — it gets the handle of the first one and joins the
 * lifecycle already in progress. That is what makes an impatient double tap
 * harmless rather than merely unlikely to be noticed.
 *
 * The registry is keyed by the *context* id rather than by the target,
 * because two deliberately prepared quarantines of the same device are two
 * legitimate actions, while one prepared context confirmed twice is one.
 */
object ActionSubmissions {

    private val byContext = ConcurrentHashMap<String, String>()
    private val counter = AtomicLong(9000)

    sealed interface Result {
        /** This caller created the action. Its lifecycle is theirs to start. */
        data class Accepted(val actionId: String) : Result

        /**
         * The context had already been submitted. No second action exists,
         * and the caller is handed the first one to observe.
         */
        data class AlreadySubmitted(val actionId: String) : Result
    }

    /**
     * Claims the right to submit this context.
     *
     * Atomic: of two concurrent callers exactly one receives [Result.Accepted]
     * and the other receives [Result.AlreadySubmitted] naming the same action.
     */
    fun submit(contextId: String): Result {
        var created = false
        val actionId = byContext.computeIfAbsent(contextId) {
            created = true
            "ACT-${counter.incrementAndGet()}"
        }
        return if (created) Result.Accepted(actionId) else Result.AlreadySubmitted(actionId)
    }

    /** The action a context produced, if it has been submitted. */
    fun actionIdFor(contextId: String): String? = byContext[contextId]

    /** Test and lifecycle hygiene. */
    fun reset() {
        byContext.clear()
    }
}

/**
 * Turns the authoritative overlay into what the action screen shows.
 *
 * Pure, and deliberately outside the view model: this is the single mapping
 * from "what the system reported" to "what the operator is told", and it is
 * the place a wrong answer would be most expensive. Every rule it encodes is
 * one the UI must never decide for itself.
 *
 *  - A submitted action with nothing reported yet is [ExecutionState.Requested].
 *    Not succeeded, not failed — asked, and not yet answered.
 *  - Terminal states become a result; everything else stays in flight. An
 *    action that is reconciling has not succeeded.
 *  - Reconciliation is whatever the event said. It is never inferred from the
 *    execution having succeeded.
 *  - The mode is taken from the event rather than from the prepared context.
 *    If a request prepared as a simulation is reported as having run live —
 *    or the reverse — the operator is told what actually happened.
 */
fun projectActionState(context: ActionContext, overlay: ActionOverlay?): ActionUiState {
    if (overlay == null) {
        return ActionUiState.InFlight(
            context = context,
            state = ExecutionState.Requested,
            detail = "The request has been submitted. NEXA is waiting for the enforcement pipeline to report on it."
        )
    }

    val reported = context.copy(executionMode = overlay.mode)
    val detail = resultExplanation(overlay.state, overlay.mode, context.action)

    return if (overlay.state.isTerminal) {
        ActionUiState.Result(
            context = reported,
            state = overlay.state,
            reconciled = overlay.reconciled,
            detail = detail
        )
    } else {
        ActionUiState.InFlight(context = reported, state = overlay.state, detail = detail)
    }
}

/**
 * PREVIEW PIPELINE — NOT A REAL ENFORCEMENT BACKEND
 *
 * Publishes the lifecycle of a submitted action as realtime frames.
 *
 * Nothing here executes anything. No firewall is touched, no nftables rule is
 * written and no Phase 4 pipeline is invoked. What it does is stand in for
 * the publisher that a real backend would be, so the client's own path —
 * parser, sequencer, reducer, store — is the thing under test rather than a
 * scripted `delay()` chain inside a view model.
 *
 * That distinction is the point of this file. Before, the action screen
 * predicted its own lifecycle: it set "Executing", waited, then set
 * "Succeeded", and the UI was certain of things no system had told it. Now
 * the screen reports the store, and the store reports only what arrived
 * through validation and sequencing. If no event arrives, the screen says so
 * instead of inventing the next state.
 *
 * The frames go through [RealtimeStore.submit] — the same entry point a live
 * socket uses. A preview that wrote overlays directly would be exercising a
 * path that never runs in production.
 */
object PreviewActionPipeline {

    /**
     * Emits the lifecycle for [actionId] according to the scripted [outcome].
     *
     * The pacing is presentation, not a state machine: each frame is
     * authoritative the moment it lands, and the delay only exists so a
     * reviewer can watch the transitions rather than see the terminal state
     * appear instantly. Nothing downstream infers anything from the timing.
     */
    suspend fun play(
        actionId: String,
        context: ActionContext,
        outcome: EnforcementPreview.Outcome
    ) {
        val mode = context.executionMode
        val code = context.action.code
        val scope = context.target.scope

        emit(actionId, code, scope, mode, ExecutionState.Requested)

        if (outcome == EnforcementPreview.Outcome.Denied) {
            // Authorization refusal is not an execution failure: nothing ran.
            emit(actionId, code, scope, mode, ExecutionState.Denied)
            return
        }

        emit(actionId, code, scope, mode, ExecutionState.Authorized)
        emit(actionId, code, scope, mode, ExecutionState.Executing)

        when (outcome) {
            EnforcementPreview.Outcome.Unknown -> {
                // Deliberately the absence of a terminal event as well as a
                // state: the publisher says it cannot determine the outcome,
                // and the client must not fill that in.
                emit(actionId, code, scope, mode, ExecutionState.Unknown)
            }

            EnforcementPreview.Outcome.Failed ->
                emit(actionId, code, scope, mode, ExecutionState.Failed)

            EnforcementPreview.Outcome.RolledBack -> {
                // Failed first: a rollback is what follows a failure, and the
                // reducer's transition table rejects a jump straight from
                // Executing to RollbackRequested. Emitting the real sequence
                // is the point — a preview that skipped a state the client
                // refuses would hide that refusal working.
                emit(actionId, code, scope, mode, ExecutionState.Failed)
                emit(actionId, code, scope, mode, ExecutionState.RollbackRequested)
                emit(
                    actionId, code, scope, mode, ExecutionState.RolledBack,
                    // A simulated rollback reconciles nothing: there was no
                    // system state to restore in the first place.
                    reconciled = mode != ExecutionMode.AuditOnly
                )
            }

            EnforcementPreview.Outcome.RollbackFailed -> {
                emit(actionId, code, scope, mode, ExecutionState.Failed)
                emit(actionId, code, scope, mode, ExecutionState.RollbackRequested)
                emit(actionId, code, scope, mode, ExecutionState.RollbackFailed)
            }

            EnforcementPreview.Outcome.SuccessPendingReconciliation -> {
                // Execution returned and reconciliation has not confirmed the
                // resulting state. The success event carries reconciled=false
                // and the screen must not upgrade that on its own.
                emit(actionId, code, scope, mode, ExecutionState.Reconciling)
                emit(actionId, code, scope, mode, ExecutionState.Succeeded, reconciled = false)
            }

            else -> {
                emit(actionId, code, scope, mode, ExecutionState.Reconciling)
                emit(
                    actionId, code, scope, mode, ExecutionState.Succeeded,
                    reconciled = mode != ExecutionMode.AuditOnly
                )
            }
        }
    }

    private var eventCounter = AtomicLong(0)

    private suspend fun emit(
        actionId: String,
        actionCode: String,
        scope: String,
        mode: ExecutionMode,
        state: ExecutionState,
        reconciled: Boolean = false
    ) {
        // One stream, one monotonic counter. The client does not own the
        // sequence space in a real deployment — the publisher does — so this
        // simply continues from wherever the stream has reached rather than
        // inventing a range of its own.
        val sequence = RealtimeStore.state.value.lastAppliedSequence + 1
        val n = eventCounter.incrementAndGet()
        RealtimeStore.submit(
            PreviewRealtimeScenario.frame(
                eventId = "RT-ACT-$n",
                sequence = sequence,
                type = "ACTION_STATE_CHANGED",
                scope = scope,
                subjectId = actionId,
                extra = buildMap {
                    put("executionState", state.wireName)
                    put("executionMode", mode.wireName)
                    put("actionCode", actionCode)
                    if (reconciled) put("reconciled", "true")
                }
            )
        )
        delay(STEP_DELAY_MS)
    }

    private const val STEP_DELAY_MS = 650L
}

/** The wire spelling the parser expects. Kept beside the emitter that needs it. */
private val ExecutionState.wireName: String
    get() = when (this) {
        ExecutionState.Requested -> "REQUESTED"
        ExecutionState.Authorized -> "AUTHORIZED"
        ExecutionState.Denied -> "DENIED"
        ExecutionState.Executing -> "EXECUTING"
        ExecutionState.Reconciling -> "RECONCILING"
        ExecutionState.Succeeded -> "SUCCEEDED"
        ExecutionState.Failed -> "FAILED"
        ExecutionState.RollbackRequested -> "ROLLBACK_REQUESTED"
        ExecutionState.RolledBack -> "ROLLED_BACK"
        ExecutionState.RollbackFailed -> "ROLLBACK_FAILED"
        ExecutionState.Unknown -> "UNKNOWN"
    }

private val ExecutionMode.wireName: String
    get() = when (this) {
        ExecutionMode.Enforce -> "ENFORCE"
        ExecutionMode.AuditOnly -> "AUDIT_ONLY"
        ExecutionMode.Unknown -> "UNKNOWN"
    }
