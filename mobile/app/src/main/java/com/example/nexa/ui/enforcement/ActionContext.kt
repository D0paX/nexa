package com.example.nexa.ui.enforcement

import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.NexaAvailability
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
    /**
     * How much NEXA actually knows about the state this decision rests on.
     *
     * Separate from [targetIsStale], which is about one observation. This is
     * about whether the picture as a whole — inventory, enforcement,
     * authorization — was readable at all. A confirmation screen assembled
     * from a service that could not answer is a screen full of plausible
     * blanks, and no operator should be asked to commit against it.
     */
    val dataAvailability: NexaAvailability = NexaAvailability.Current,
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

    // 3. An unknown execution mode is unsafe in both directions: NEXA cannot
    // promise the operator a simulation, and must not offer them a live
    // mutation it cannot confirm. It refuses rather than guessing either way.
    if (context.executionMode == ExecutionMode.Unknown) {
        return ActionAvailability.Disabled(
            "Execution mode is unknown. NEXA cannot confirm whether this would simulate or mutate firewall state."
        )
    }

    // 4. The state this decision rests on has to be readable at all.
    //
    // Offline, unavailable, unknown, still-loading or outright failed means
    // the context an operator would be confirming against was never
    // established. That refusal applies to every action, including trust
    // operations: reverification is not a firewall change, but asking for one
    // against an identity NEXA cannot currently see is still acting blind.
    if (!context.dataAvailability.hasData) {
        return ActionAvailability.Disabled(unreadableStateReason(context.dataAvailability))
    }

    // 5. Data that exists but is old or partial blocks anything that mutates
    // enforcement.
    //
    // Reverification is deliberately exempt here, for the same reason it is
    // exempt from the staleness rule below: it asks the identity itself to
    // prove it is present, rather than acting on what NEXA last saw.
    if (action.mutatesEnforcement && !context.dataAvailability.isActionable) {
        return ActionAvailability.Disabled(unreadableStateReason(context.dataAvailability))
    }

    // 6. The circuit breaker halts enforcement globally. Trust operations are
    // not firewall mutations and are unaffected by it.
    if (action.mutatesEnforcement && !context.circuitBreaker.allowsExecution) {
        return ActionAvailability.Disabled(
            "Enforcement is currently paused by the circuit breaker."
        )
    }

    // 7. An unknown enforcement state makes the outcome unreasonable to predict.
    if (action.mutatesEnforcement && enforcement == DeviceEnforcement.Unknown) {
        return ActionAvailability.Disabled(
            "Current enforcement state for this target is unknown."
        )
    }

    // 8. Idempotency, stated rather than hidden.
    if (context.alreadyInDesiredState) {
        return ActionAvailability.Disabled(
            when (action) {
                EnforcementAction.QuarantineDevice -> "This target is already enforced."
                EnforcementAction.ReleaseQuarantine -> "This target has already been released."
                EnforcementAction.RequireReverification -> "Reverification is already pending for this identity."
            }
        )
    }

    // 9. A stale observation is a security condition for anything that mutates
    // enforcement: the target it names may no longer be the target it reaches.
    // Simulation does not lift this — the requirement exists to keep the
    // request honest, not merely to protect the firewall.
    if (action.mutatesEnforcement && context.targetIsStale) {
        return ActionAvailability.Disabled(
            "Target observation is stale. Re-resolve the target before requesting this action."
        )
    }

    return ActionAvailability.Available
}

/**
 * Why an action cannot be offered when the state behind it is not solid.
 *
 * Each sentence names what NEXA does not know, rather than saying the action
 * is unavailable and leaving the operator to invent a reason — and none of
 * them implies the target is fine.
 *
 * Shared rather than private so the list that offers an action and the screen
 * that refuses it give the same reason. An operator who is told one thing on
 * the device screen and another on the confirmation screen learns to trust
 * neither.
 */
fun unreadableStateReason(availability: NexaAvailability): String = when (availability) {
    NexaAvailability.Offline ->
        "NEXA is offline and cannot confirm this target's current state. Reconnect before requesting an action."
    NexaAvailability.Unavailable ->
        "The service that describes this target is unavailable. NEXA cannot confirm what it would be acting on."
    NexaAvailability.Unknown ->
        "This target's current state cannot be established. NEXA will not request an action it cannot evaluate."
    NexaAvailability.Error ->
        "Reading this target's state failed. NEXA will not request an action against an unconfirmed context."
    NexaAvailability.Loading ->
        "This target's state is still being read."
    NexaAvailability.Stale ->
        "This target's state is no longer current. Refresh before requesting an action that changes enforcement."
    NexaAvailability.Degraded ->
        "Only part of this target's state could be retrieved. NEXA will not request an enforcement change against an incomplete picture."
    NexaAvailability.Empty ->
        "There is no state recorded for this target."
    NexaAvailability.Current -> "This target's state is current."
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
