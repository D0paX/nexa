package com.example.nexa.ui.enforcement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives one enforcement action through its lifecycle.
 *
 * The client submits and then reports what the pipeline says. It does not
 * decide authorization, does not touch firewall state, and does not infer an
 * outcome from a timeout — an action whose result it cannot read is reported
 * as [ExecutionState.Unknown], never as success or failure.
 *
 * Duplicate submission is refused at the state machine, not only at the
 * button, so an impatient double-tap cannot produce a second request.
 */
class ActionViewModel : ViewModel() {

    private val _state = MutableStateFlow<ActionUiState>(ActionUiState.Preparing)
    val state: StateFlow<ActionUiState> = _state.asStateFlow()

    private var contextId: String? = null
    private var submitted = false

    fun load(id: String) {
        if (contextId == id && _state.value !is ActionUiState.Preparing) return
        contextId = id
        submitted = false
        viewModelScope.launch {
            _state.value = ActionUiState.Preparing
            delay(PREPARE_DELAY_MS)
            val context = EnforcementPreview.resolve(id)
            _state.value = if (context == null) {
                // The handle could not be resolved. Nothing is reconstructed
                // from loose arguments; the operator restarts from the target.
                ActionUiState.Unavailable
            } else {
                ActionUiState.AwaitingConfirmation(
                    context = context,
                    availability = availabilityOf(context),
                    consequence = consequenceOf(context.action, context.executionMode)
                )
            }
        }
    }

    /**
     * Re-resolves the target before confirmation.
     *
     * Present so a stale target has an explicit route forward rather than the
     * operator being tempted to proceed anyway. Re-resolution reports what it
     * finds; it never simply clears the stale flag.
     */
    fun refreshTarget() {
        val current = _state.value as? ActionUiState.AwaitingConfirmation ?: return
        viewModelScope.launch {
            _state.value = ActionUiState.Preparing
            delay(PREPARE_DELAY_MS)
            val refreshed = EnforcementPreview.resolve(current.context.id) ?: run {
                _state.value = ActionUiState.Unavailable
                return@launch
            }
            _state.value = ActionUiState.AwaitingConfirmation(
                context = refreshed,
                availability = availabilityOf(refreshed),
                consequence = consequenceOf(refreshed.action, refreshed.executionMode)
            )
        }
    }

    /**
     * Submits the request.
     *
     * Refuses if the availability matrix does not permit it, and refuses a
     * second submission outright.
     */
    fun confirm() {
        val current = _state.value as? ActionUiState.AwaitingConfirmation ?: return
        if (current.availability !is ActionAvailability.Available) return
        if (submitted) return
        submitted = true

        val context = current.context
        val outcome = EnforcementPreview.outcomeFor(context.id)

        viewModelScope.launch {
            step(context, ExecutionState.Requested)

            if (outcome == EnforcementPreview.Outcome.Denied) {
                // Authorization refusal is not an execution failure: nothing ran.
                _state.value = ActionUiState.Result(
                    context = context,
                    state = ExecutionState.Denied,
                    reconciled = false,
                    detail = ExecutionState.Denied.explanation
                )
                return@launch
            }

            step(context, ExecutionState.Authorized)
            step(context, ExecutionState.Executing)

            when (outcome) {
                EnforcementPreview.Outcome.Unknown -> {
                    _state.value = ActionUiState.Result(
                        context = context,
                        state = ExecutionState.Unknown,
                        reconciled = false,
                        detail = ExecutionState.Unknown.explanation
                    )
                }

                EnforcementPreview.Outcome.Failed -> {
                    _state.value = ActionUiState.Result(
                        context = context,
                        state = ExecutionState.Failed,
                        reconciled = false,
                        detail = ExecutionState.Failed.explanation
                    )
                }

                EnforcementPreview.Outcome.RolledBack -> {
                    step(context, ExecutionState.RollbackRequested)
                    _state.value = ActionUiState.Result(
                        context = context,
                        state = ExecutionState.RolledBack,
                        reconciled = true,
                        detail = ExecutionState.RolledBack.explanation
                    )
                }

                EnforcementPreview.Outcome.RollbackFailed -> {
                    step(context, ExecutionState.RollbackRequested)
                    _state.value = ActionUiState.Result(
                        context = context,
                        state = ExecutionState.RollbackFailed,
                        reconciled = false,
                        detail = ExecutionState.RollbackFailed.explanation
                    )
                }

                EnforcementPreview.Outcome.SuccessPendingReconciliation -> {
                    step(context, ExecutionState.Reconciling)
                    // Execution returned, but reconciliation has not confirmed
                    // the resulting state. Success is not claimed for it.
                    _state.value = ActionUiState.Result(
                        context = context,
                        state = ExecutionState.Succeeded,
                        reconciled = false,
                        detail = "Execution completed. Enforcement reconciliation is still pending — the resulting system state is not yet confirmed."
                    )
                }

                else -> {
                    step(context, ExecutionState.Reconciling)
                    _state.value = ActionUiState.Result(
                        context = context,
                        state = ExecutionState.Succeeded,
                        reconciled = true,
                        detail = if (context.executionMode == com.example.nexa.ui.common.ExecutionMode.AuditOnly) {
                            "Simulation completed. No firewall mutation occurred."
                        } else {
                            "Execution completed and enforcement was reconciled."
                        }
                    )
                }
            }
        }
    }

    private suspend fun step(context: ActionContext, state: ExecutionState) {
        _state.value = ActionUiState.InFlight(context, state, state.explanation)
        delay(STEP_DELAY_MS)
    }

    private companion object {
        const val PREPARE_DELAY_MS = 320L
        const val STEP_DELAY_MS = 700L
    }
}
