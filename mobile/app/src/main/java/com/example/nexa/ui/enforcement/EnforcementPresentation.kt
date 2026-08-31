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
