package com.example.nexa.ui.enforcement

import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.devices.Presence

/**
 * The operator-facing model of a Phase 4 enforcement action.
 *
 * The client never decides anything security-relevant here. It assembles the
 * context an operator needs in order to understand what they are about to
 * request, decides what may be *offered*, and hands the request to the
 * existing pipeline. Authorization, snapshotting, execution, reconciliation
 * and rollback all remain authoritative on the backend.
 *
 * The separations this model exists to hold apart:
 *
 *   trust          is not authorization
 *   authorization  is not execution
 *   execution      is not reconciliation
 *   an address     is never an identity
 */

// ============================================================
// ACTIONS
// ============================================================

enum class EnforcementAction {
    QuarantineDevice,
    ReleaseQuarantine,
    RequireReverification;

    /** The Phase 4 action code handed to the pipeline. */
    val code: String
        get() = when (this) {
            QuarantineDevice -> "QUARANTINE_DEVICE"
            ReleaseQuarantine -> "RELEASE_QUARANTINE"
            RequireReverification -> "REQUIRE_REVERIFICATION"
        }

    val label: String
        get() = when (this) {
            QuarantineDevice -> "Quarantine Device"
            ReleaseQuarantine -> "Release Quarantine"
            RequireReverification -> "Require Reverification"
        }

    /** Whether the action can change firewall state. Trust operations cannot. */
    val mutatesEnforcement: Boolean
        get() = this != RequireReverification
}

// ============================================================
// AUTHORIZATION — never inferred from trust
// ============================================================

enum class AuthorizationState {
    /** The authorization engine has approved this request in advance. */
    Authorized,

    /** The request may be submitted; approval happens during execution. */
    ApprovalRequired,

    /** The authorization engine refuses this request. */
    Denied,

    /** Authorization standing cannot be determined. */
    Unknown
}

// ============================================================
// EXECUTION — the Phase 4 lifecycle
// ============================================================

enum class ExecutionState {
    Requested,
    Authorized,
    Denied,
    Executing,
    Reconciling,
    Succeeded,
    Failed,
    RollbackRequested,
    RolledBack,
    RollbackFailed,

    /** No authoritative execution state is available. Never rendered as success. */
    Unknown;

    val isTerminal: Boolean
        get() = this == Succeeded || this == Failed || this == Denied ||
            this == RolledBack || this == RollbackFailed || this == Unknown

    val isInFlight: Boolean
        get() = this == Requested || this == Authorized || this == Executing ||
            this == Reconciling || this == RollbackRequested
}

// ============================================================
// TARGET
// ============================================================

/**
 * The target of an action.
 *
 * [scope] is mandatory context, not decoration: the same MAC in another
 * NetworkScope is a different logical target. [ip] is observation only and
 * is never sufficient to identify anything.
 */
data class ActionTarget(
    val deviceId: String,
    val label: String,
    val mac: String,
    val ip: String?,
    val scope: String,
    val presence: Presence,
    val identityId: String?,
    val trust: TrustState,
    val observationFreshness: DataFreshness,
    val lastObservedLabel: String,
    val bindingId: String? = null,
    val ownershipScope: String? = null
)

/**
 * Everything an operator needs before requesting an action.
 *
 * Assembled once by [ActionPreparation] and addressed by [id]. Screens hand
 * over the id rather than a bag of loose fields, so a target can never be
 * reconstructed from an address and a guess.
 */
data class ActionContext(
    val id: String,
    val action: EnforcementAction,
    val target: ActionTarget,
    val authorization: AuthorizationState,
    val executionMode: ExecutionMode,
    val currentEnforcement: DeviceEnforcement,
    val circuitBreaker: CircuitBreakerState,
    val alreadyInDesiredState: Boolean = false,
    val note: String? = null
) {
    val targetIsStale: Boolean
        get() = target.observationFreshness !is DataFreshness.Live
}

// ============================================================
// AVAILABILITY MATRIX
// ============================================================

/**
 * Whether an action may be offered, and if not, why.
 *
 * [Disabled] always carries a reason — "unavailable" on its own tells an
 * operator nothing and invites them to go looking for a way around it.
 */
sealed interface ActionAvailability {
    data object Available : ActionAvailability
    data class Disabled(val reason: String) : ActionAvailability
    data object Hidden : ActionAvailability
}

/**
 * The single place that decides whether an action may be requested.
 *
 * Order matters, and is ordered by how fundamental the objection is:
 *
 *  1. Structurally inapplicable — the action makes no sense for this state.
 *  2. Authorization refused or unknown — no point preparing a doomed request.
 *  3. Enforcement globally paused — the breaker is open.
 *  4. Enforcement state unknown — we cannot reason about the outcome.
 *  5. Already in the desired state — idempotency, stated plainly.
 *  6. Target observation stale — for firewall-mutating actions only.
 *
 * Staleness blocks quarantine and release because those act on a target
 * identified by an observation that is no longer current; it does not block
 * reverification, which asks the identity itself to prove it is there.
 */
fun availabilityOf(context: ActionContext): ActionAvailability {
    val action = context.action
    val enforcement = context.currentEnforcement

    // 1. Structural applicability.
    when (action) {
        EnforcementAction.ReleaseQuarantine ->
            if (enforcement != DeviceEnforcement.Quarantined &&
                enforcement != DeviceEnforcement.Reconciling
            ) {
                return ActionAvailability.Hidden
            }
        EnforcementAction.QuarantineDevice ->
            if (enforcement == DeviceEnforcement.Quarantined) {
                return ActionAvailability.Disabled("This target is already quarantined.")
            }
        EnforcementAction.RequireReverification -> {
            if (context.target.identityId == null) {
                return ActionAvailability.Hidden
            }
            if (context.target.trust == TrustState.Revoked) {
                return ActionAvailability.Disabled(
                    "This identity is revoked. Reverification does not restore withdrawn trust."
                )
            }
        }
    }

    // 2. Authorization is authoritative and is never inferred from trust.
    when (context.authorization) {
        AuthorizationState.Denied ->
            return ActionAvailability.Disabled("Authorization denied for this action.")
        AuthorizationState.Unknown ->
            return ActionAvailability.Disabled(
                "Authorization standing is unknown. NEXA will not prepare a request it cannot evaluate."
            )
        AuthorizationState.Authorized, AuthorizationState.ApprovalRequired -> Unit
    }

    // 3. The circuit breaker halts enforcement globally. Trust operations are
    // not firewall mutations and are unaffected by it.
    if (action.mutatesEnforcement && !context.circuitBreaker.allowsExecution) {
        return ActionAvailability.Disabled(
            "Enforcement is currently paused by the circuit breaker."
        )
    }

    // 4. An unknown enforcement state makes the outcome unreasonable to predict.
    if (action.mutatesEnforcement && enforcement == DeviceEnforcement.Unknown) {
        return ActionAvailability.Disabled(
            "Current enforcement state for this target is unknown."
        )
    }

    // 5. Idempotency, stated rather than hidden.
    if (context.alreadyInDesiredState) {
        return ActionAvailability.Disabled(
            when (action) {
                EnforcementAction.QuarantineDevice -> "This target is already enforced."
                EnforcementAction.ReleaseQuarantine -> "This target has already been released."
                EnforcementAction.RequireReverification -> "Reverification is already pending for this identity."
            }
        )
    }

    // 6. A stale observation is a security condition for anything that mutates
    // enforcement: the target it names may no longer be the target it reaches.
    if (action.mutatesEnforcement && context.targetIsStale) {
        return ActionAvailability.Disabled(
            "Target observation is stale. Re-resolve the target before requesting this action."
        )
    }

    return ActionAvailability.Available
}

// ============================================================
// CONSEQUENCE COPY — action-specific, never generic
// ============================================================

/**
 * What an action will do, in its own words.
 *
 * Each action states its own consequence. Reusing quarantine's wording for a
 * trust operation would tell an operator they were isolating a device when
 * they were not — a defect this project has already had once, and which the
 * regression tests now guard.
 */
data class ActionConsequence(
    val summary: String,
    val destructive: Boolean,
    val known: Boolean = true
)

fun consequenceOf(action: EnforcementAction, mode: ExecutionMode): ActionConsequence {
    val base = when (action) {
        EnforcementAction.QuarantineDevice -> ActionConsequence(
            summary = "This action changes network enforcement for the target. It will isolate the device from all network access except the designated remediation VLAN, and existing connections will be dropped.",
            destructive = true
        )
        EnforcementAction.ReleaseQuarantine -> ActionConsequence(
            summary = "This action removes the enforcement binding for the target if the authoritative execution succeeds, restoring its normal network access. It does not change the target's trust standing.",
            destructive = true
        )
        EnforcementAction.RequireReverification -> ActionConsequence(
            summary = "This action requires the target's cryptographic identity to be verified again. It does not quarantine the device, does not revoke trust, and makes no change to firewall state.",
            destructive = false
        )
    }

    // In AUDIT_ONLY nothing is mutated, so the consequence says so rather than
    // describing a firewall change that will not happen.
    return if (mode == ExecutionMode.AuditOnly && action.mutatesEnforcement) {
        base.copy(
            summary = "SIMULATION: ${base.summary} In AUDIT_ONLY mode this is evaluated and recorded, but no firewall mutation will occur."
        )
    } else {
        base
    }
}

/** For an action code NEXA does not recognize, it refuses to invent meaning. */
fun consequenceForCode(code: String, mode: ExecutionMode): ActionConsequence {
    val action = EnforcementAction.entries.firstOrNull { it.code == code }
        ?: return ActionConsequence(
            summary = "NEXA cannot describe the consequence of this action. Do not confirm it unless you know what it does.",
            destructive = true,
            known = false
        )
    return consequenceOf(action, mode)
}

// ============================================================
// UI STATE
// ============================================================

/**
 * The action flow's state.
 *
 * [Unknown] and [Unavailable] exist so the screen is never forced to claim an
 * outcome it does not have.
 */
sealed interface ActionUiState {
    data object Preparing : ActionUiState

    /** Context assembled; the operator has not confirmed. */
    data class AwaitingConfirmation(
        val context: ActionContext,
        val availability: ActionAvailability,
        val consequence: ActionConsequence
    ) : ActionUiState

    /** Submitted and progressing through the Phase 4 lifecycle. */
    data class InFlight(
        val context: ActionContext,
        val state: ExecutionState,
        val detail: String? = null
    ) : ActionUiState

    /** Reached a terminal state. [reconciled] is separate from success. */
    data class Result(
        val context: ActionContext,
        val state: ExecutionState,
        val reconciled: Boolean,
        val detail: String
    ) : ActionUiState

    /** The action context could not be resolved. Nothing is assumed about it. */
    data object Unavailable : ActionUiState
}
