package com.example.nexa.ui.enforcement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexa.ui.realtime.RealtimeStore
import com.example.nexa.ui.realtime.withLiveTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives one enforcement action through its lifecycle.
 *
 * This screen reports; it does not predict. Every lifecycle state it shows
 * arrived as an event, went through the parser, the sequencer and the
 * reducer, and is read back out of [RealtimeStore]. Nothing here advances a
 * state because time passed.
 *
 * That is a change worth stating plainly, because the previous version did
 * the opposite: it set "Executing", waited, and set "Succeeded", so the UI
 * was confident about outcomes no system had reported. The states looked
 * identical on screen and meant something entirely different.
 *
 * What still lives here is the request side — deciding whether the action may
 * be submitted at all, re-checking that at the moment of submission, and
 * refusing a second submission. Everything after the request belongs to the
 * store.
 *
 * The client never decides authorization, never touches firewall state, and
 * never infers an outcome from silence: an action whose result has not been
 * reported stays in the last state that was, and one reported as
 * [ExecutionState.Unknown] is shown as unknown, never as success or failure.
 */
class ActionViewModel : ViewModel() {

    private val _state = MutableStateFlow<ActionUiState>(ActionUiState.Preparing)
    val state: StateFlow<ActionUiState> = _state.asStateFlow()

    private var contextId: String? = null
    private var actionId: String? = null
    private var lifecycleJob: Job? = null

    /**
     * Resolves the prepared context behind [id].
     *
     * If this context has already been submitted — the operator navigated
     * away and came back, or the screen was recreated — the action is not
     * restarted. Its authoritative state is picked up where it stands, which
     * is the only reading of "back" that cannot produce a second action.
     */
    fun load(id: String) {
        if (contextId == id && _state.value !is ActionUiState.Preparing) return
        contextId = id

        val context = EnforcementPreview.resolve(id)
        if (context == null) {
            // The handle could not be resolved. Nothing is reconstructed from
            // loose arguments; the operator restarts from the target.
            _state.value = ActionUiState.Unavailable
            return
        }

        val existing = ActionSubmissions.actionIdFor(id)
        if (existing != null) {
            actionId = existing
            observeLifecycle(context, existing)
            return
        }

        _state.value = awaitingConfirmation(context)
    }

    /**
     * Re-resolves the target before confirmation.
     *
     * Present so a stale target has an explicit route forward rather than the
     * operator being tempted to proceed anyway. It reports what it finds; it
     * never simply clears the stale flag, and if the target is still not
     * current the screen still refuses.
     *
     * The re-derivation reads the live overlay for this target, so a device
     * that has been observed since the context was prepared comes back
     * current — and one that has not, does not.
     */
    fun refreshTarget() {
        val current = _state.value as? ActionUiState.AwaitingConfirmation ?: return
        val stored = EnforcementPreview.resolve(current.context.id) ?: run {
            _state.value = ActionUiState.Unavailable
            return
        }
        _state.value = awaitingConfirmation(stored.withLiveTarget(RealtimeStore.state.value))
    }

    /**
     * Submits the request.
     *
     * Two gates, in this order:
     *
     *  1. The availability matrix is evaluated *again*, against the context as
     *     it stands now rather than the copy the screen rendered. Between a
     *     confirmation screen opening and a thumb landing on it the device can
     *     go absent, trust can change, the breaker can open and the mode can
     *     become unknown. Trusting the earlier evaluation would be executing
     *     against a screenshot.
     *
     *  2. The submission registry, which is the idempotency boundary. A second
     *     confirmation of the same context does not create a second action; it
     *     attaches to the first one.
     */
    fun confirm() {
        val current = _state.value as? ActionUiState.AwaitingConfirmation ?: return
        val stored = EnforcementPreview.resolve(current.context.id) ?: run {
            _state.value = ActionUiState.Unavailable
            return
        }

        // Re-derive from the newest target state, then re-evaluate.
        val context = stored.withLiveTarget(RealtimeStore.state.value)
        val availability = availabilityOf(context)
        if (availability !is ActionAvailability.Available) {
            // The prerequisites moved. The operator is shown the refusal
            // rather than a request being sent on the strength of the old one.
            _state.value = ActionUiState.AwaitingConfirmation(
                context = context,
                availability = availability,
                consequence = consequenceOf(context.action, context.executionMode),
                blockedAfterConfirm = true
            )
            return
        }

        when (val submission = ActionSubmissions.submit(context.id)) {
            is ActionSubmissions.Result.Accepted -> {
                actionId = submission.actionId
                observeLifecycle(context, submission.actionId)
                // The publisher's job in the real system. Here it stands in
                // for the backend that would report this action's progress.
                viewModelScope.launch {
                    PreviewActionPipeline.play(
                        actionId = submission.actionId,
                        context = context,
                        outcome = EnforcementPreview.outcomeFor(context.id)
                    )
                }
            }

            is ActionSubmissions.Result.AlreadySubmitted -> {
                // A duplicate confirmation. No second action is created; the
                // screen simply joins the one already running.
                actionId = submission.actionId
                observeLifecycle(context, submission.actionId)
            }
        }
    }

    /**
     * Projects the authoritative lifecycle for this action.
     *
     * Collecting rather than stepping. The store is the only thing that moves
     * this screen forward, so a duplicate event, a replay or an illegal
     * transition — all of which the store already rejects — cannot advance
     * the UI either.
     */
    private fun observeLifecycle(context: ActionContext, id: String) {
        lifecycleJob?.cancel()
        lifecycleJob = viewModelScope.launch {
            // This action's overlay, not the whole store. Collecting the store
            // meant every device observation and every delivery attempt
            // anywhere re-derived this screen's execution state, for an answer
            // that had not changed.
            RealtimeStore.actionState(id).collect { overlay ->
                _state.value = projectActionState(context, overlay)
            }
        }
    }

    private fun awaitingConfirmation(context: ActionContext) = ActionUiState.AwaitingConfirmation(
        context = context,
        availability = availabilityOf(context),
        consequence = consequenceOf(context.action, context.executionMode)
    )
}
