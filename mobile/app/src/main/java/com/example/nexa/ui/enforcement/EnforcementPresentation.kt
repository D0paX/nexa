package com.example.nexa.ui.enforcement

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaStatus
import com.example.nexa.ui.common.ExecutionMode

/**
 * How enforcement action state is presented.
 *
 * Resolved here so no security wording is decided inside a composable.
 */

val EnforcementAction.icon: ImageVector
    get() = when (this) {
        EnforcementAction.QuarantineDevice -> NexaIcons.Quarantine
        EnforcementAction.ReleaseQuarantine -> NexaIcons.Release
        EnforcementAction.RequireReverification -> NexaIcons.Reverification
    }

val AuthorizationState.status: NexaStatus
    get() = when (this) {
        AuthorizationState.Authorized -> NexaStatus.Secure
        AuthorizationState.ApprovalRequired -> NexaStatus.Warning
        AuthorizationState.Denied -> NexaStatus.Danger
        AuthorizationState.Unknown -> NexaStatus.Unknown
    }

val AuthorizationState.label: String
    get() = when (this) {
        AuthorizationState.Authorized -> "Authorized"
        AuthorizationState.ApprovalRequired -> "Approval required"
        AuthorizationState.Denied -> "Denied"
        AuthorizationState.Unknown -> "Unknown"
    }

/** Authorization is stated as its own fact, never derived from trust. */
val AuthorizationState.explanation: String
    get() = when (this) {
        AuthorizationState.Authorized ->
            "The authorization engine has approved this request. Approval is re-checked at execution."
        AuthorizationState.ApprovalRequired ->
            "This request requires operator approval. It will be authorized at execution, not by this screen."
        AuthorizationState.Denied ->
            "The authorization engine refuses this request. Nothing has been executed."
        AuthorizationState.Unknown ->
            "Authorization standing cannot be determined. NEXA will not claim this request would be permitted."
    }

val ExecutionMode.label: String
    get() = when (this) {
        ExecutionMode.Enforce -> "Live enforcement"
        ExecutionMode.AuditOnly -> "Simulation only"
        ExecutionMode.Unknown -> "Execution mode unknown"
    }

val ExecutionMode.status: NexaStatus
    get() = when (this) {
        ExecutionMode.Enforce -> NexaStatus.Danger
        ExecutionMode.AuditOnly -> NexaStatus.Simulation
        ExecutionMode.Unknown -> NexaStatus.Unknown
    }

val ExecutionState.label: String
    get() = when (this) {
        ExecutionState.Requested -> "Requested"
        ExecutionState.Authorized -> "Authorized"
        ExecutionState.Denied -> "Denied"
        ExecutionState.Executing -> "Executing"
        ExecutionState.Reconciling -> "Reconciling"
        ExecutionState.Succeeded -> "Succeeded"
        ExecutionState.Failed -> "Failed"
        ExecutionState.RollbackRequested -> "Rollback requested"
        ExecutionState.RolledBack -> "Rolled back"
        ExecutionState.RollbackFailed -> "Rollback failed"
        ExecutionState.Unknown -> "Unknown"
    }

val ExecutionState.status: NexaStatus
    get() = when (this) {
        ExecutionState.Succeeded -> NexaStatus.Secure
        ExecutionState.RolledBack -> NexaStatus.Warning
        ExecutionState.Requested, ExecutionState.Authorized -> NexaStatus.Information
        ExecutionState.Executing, ExecutionState.Reconciling, ExecutionState.RollbackRequested -> NexaStatus.Information
        ExecutionState.Denied -> NexaStatus.Danger
        ExecutionState.Failed -> NexaStatus.Danger
        // Rollback failure is its own, worse category: the system did not
        // return to its prior state and a human has to look.
        ExecutionState.RollbackFailed -> NexaStatus.Critical
        ExecutionState.Unknown -> NexaStatus.Unknown
    }

/**
 * What each execution state actually means.
 *
 * Success never claims reconciliation, and rollback failure is never worded
 * as a cancellation.
 */
val ExecutionState.explanation: String
    get() = when (this) {
        ExecutionState.Requested -> "The request has been submitted and is awaiting authorization."
        ExecutionState.Authorized -> "The request was authorized and is queued for execution."
        ExecutionState.Denied -> "Authorization was refused. Execution never started."
        ExecutionState.Executing -> "The enforcement pipeline is applying this action."
        ExecutionState.Reconciling -> "Execution returned. NEXA is confirming the resulting system state."
        ExecutionState.Succeeded -> "The action completed."
        ExecutionState.Failed -> "The action did not complete. The resulting state is not confirmed."
        ExecutionState.RollbackRequested -> "The action failed and a rollback has been requested."
        ExecutionState.RolledBack -> "The action failed and the prior state was restored."
        ExecutionState.RollbackFailed ->
            "The action failed AND the rollback failed. The target did not return to its prior state. This requires operator attention."
        ExecutionState.Unknown -> "NEXA cannot determine the outcome of this action."
    }

/** The ordered lifecycle shown as progress. No fabricated percentages. */
val executionProgression: List<ExecutionState> = listOf(
    ExecutionState.Requested,
    ExecutionState.Authorized,
    ExecutionState.Executing,
    ExecutionState.Reconciling,
    ExecutionState.Succeeded
)

// ============================================================
// MODE-AWARE LANGUAGE
//
// Every operator-facing word about an outcome is chosen with the execution
// mode in hand. A simulation reaching a state is not the system reaching
// that state, and the wording must never let the two read alike.
// ============================================================

/** Short verb form for a confirmation control: "SIMULATE QUARANTINE". */
val EnforcementAction.shortLabel: String
    get() = when (this) {
        EnforcementAction.QuarantineDevice -> "QUARANTINE"
        EnforcementAction.ReleaseQuarantine -> "RELEASE"
        EnforcementAction.RequireReverification -> "REVERIFICATION"
    }

/**
 * The confirmation button's label.
 *
 * In AUDIT_ONLY the operator is asked to *simulate*, never to *confirm* a
 * mutation that will not happen. An unknown mode says so rather than
 * offering either.
 */
fun confirmLabel(action: EnforcementAction, mode: ExecutionMode): String = when (mode) {
    ExecutionMode.AuditOnly -> "SIMULATE ${action.shortLabel}"
    ExecutionMode.Enforce -> "CONFIRM ${action.code}"
    ExecutionMode.Unknown -> "EXECUTION MODE UNKNOWN"
}

/**
 * The headline shown on a finished action.
 *
 * A simulated run reports a simulation outcome. Authorization refusal is
 * reported as itself in either mode, because authorization is real even when
 * execution would not have been.
 */
fun resultHeadline(state: ExecutionState, mode: ExecutionMode): String {
    if (state == ExecutionState.Denied) return "AUTHORIZATION DENIED"
    if (mode != ExecutionMode.AuditOnly) return state.label.uppercase()
    return when (state) {
        ExecutionState.Succeeded -> "SIMULATION COMPLETE"
        ExecutionState.Failed -> "SIMULATION FAILED"
        ExecutionState.RolledBack -> "SIMULATION ROLLED BACK"
        ExecutionState.RollbackFailed -> "SIMULATION ROLLBACK FAILED"
        ExecutionState.Unknown -> "SIMULATION OUTCOME UNKNOWN"
        else -> "SIMULATION ${state.label.uppercase()}"
    }
}

/**
 * What a finished action actually means, in its mode.
 *
 * The AUDIT_ONLY strings deliberately never use "quarantined", "released",
 * "blocked", "enforced" or "reconciled" in a way that could be read as a
 * kernel change — every one of them states that no firewall mutation
 * occurred.
 */
fun resultExplanation(state: ExecutionState, mode: ExecutionMode): String {
    if (mode != ExecutionMode.AuditOnly) return state.explanation
    return when (state) {
        ExecutionState.Succeeded ->
            "The simulation completed. No firewall mutation occurred and the target's enforcement state is unchanged."
        ExecutionState.Failed ->
            "The simulation did not complete. No firewall mutation occurred, because none would have been attempted in this mode."
        ExecutionState.Denied ->
            "Authorization was refused. The simulation never ran, and no firewall mutation would have occurred in any case."
        ExecutionState.RolledBack ->
            "The simulation failed and the simulated prior state was restored. No firewall mutation occurred at any point."
        ExecutionState.RollbackFailed ->
            "The simulation failed and its simulated rollback also failed. No firewall mutation occurred, but the simulated outcome is inconsistent and should be investigated."
        ExecutionState.Unknown ->
            "NEXA cannot determine the outcome of this simulation. No firewall mutation occurred."

        // In-flight wording matters as much as the result: "applying this
        // action" would describe a mutation that is not happening.
        ExecutionState.Requested ->
            "The simulated request has been submitted and is awaiting authorization. No firewall mutation will occur."
        ExecutionState.Authorized ->
            "The request was authorized and is queued for simulation. No firewall mutation will occur."
        ExecutionState.Executing ->
            "NEXA is simulating this action. No firewall mutation is being applied."
        ExecutionState.Reconciling ->
            "The simulation returned. NEXA is evaluating the simulated outcome — no firewall mutation occurred, so there is no system state to reconcile."
        ExecutionState.RollbackRequested ->
            "The simulation failed and a simulated rollback has been requested. No firewall mutation occurred."
    }
}

/**
 * How reconciliation is reported.
 *
 * A simulation is never described as reconciled: there is no kernel state to
 * reconcile against, and claiming otherwise would be the single most
 * misleading thing this screen could say.
 */
fun reconciliationLabel(mode: ExecutionMode, reconciled: Boolean): String = when {
    mode == ExecutionMode.AuditOnly -> "SIMULATED — NOT APPLIED"
    reconciled -> "RECONCILED"
    else -> "NOT CONFIRMED"
}

fun reconciliationStatus(mode: ExecutionMode, reconciled: Boolean): NexaStatus = when {
    mode == ExecutionMode.AuditOnly -> NexaStatus.Simulation
    reconciled -> NexaStatus.Secure
    else -> NexaStatus.Warning
}

/** The banner word for an in-flight action, so mode stays visible during execution. */
fun inFlightModeLabel(mode: ExecutionMode): String = when (mode) {
    ExecutionMode.AuditOnly -> "SIMULATION — NO FIREWALL MUTATION"
    ExecutionMode.Enforce -> "LIVE ENFORCEMENT"
    ExecutionMode.Unknown -> "EXECUTION MODE UNKNOWN"
}
