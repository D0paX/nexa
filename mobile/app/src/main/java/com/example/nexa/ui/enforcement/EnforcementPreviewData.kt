package com.example.nexa.ui.enforcement

import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.NexaAvailability
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.common.availabilityOf
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.devices.Presence
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * PREVIEW DATA — NOT LIVE SYSTEM STATE.
 *
 * Fabricated enforcement context for development.
 *
 * NOTHING IN THIS FILE EXECUTES ANYTHING. No firewall is touched, no
 * nftables rule is written, no Phase 4 pipeline is invoked, and no backend
 * API is implied. The execution lifecycle rendered by the action screen is a
 * scripted sequence of states used to build and review the UI before
 * integration — a "SUCCEEDED" here means the preview reached that state, and
 * never that a real mutation occurred.
 *
 * When the real client arrives it replaces [ActionPreparation]'s outcome
 * source; the context model, the availability matrix and the screen do not
 * change.
 */
object EnforcementPreview {

    /** The scripted outcome a prepared action will follow. */
    enum class Outcome {
        Success,
        SuccessPendingReconciliation,
        Denied,
        Failed,
        RolledBack,
        RollbackFailed,
        Unknown
    }

    fun target(
        deviceId: String = "DEV-1001",
        label: String = "Corp Laptop - Engineering",
        mac: String = "00:1A:2B:3C:4D:5E",
        ip: String? = "192.168.1.105",
        scope: String = "VLAN_SECURE",
        presence: Presence = Presence.Present,
        identityId: String? = "TID-88F1",
        trust: TrustState = TrustState.Trusted,
        freshness: DataFreshness = DataFreshness.Live,
        lastObserved: String = "2m ago",
        bindingId: String? = null,
        ownershipScope: String? = null
    ) = ActionTarget(
        deviceId = deviceId,
        label = label,
        mac = mac,
        ip = ip,
        scope = scope,
        presence = presence,
        identityId = identityId,
        trust = trust,
        observationFreshness = freshness,
        lastObservedLabel = lastObserved,
        bindingId = bindingId,
        ownershipScope = ownershipScope
    )

    fun context(
        action: EnforcementAction = EnforcementAction.QuarantineDevice,
        target: ActionTarget = target(),
        authorization: AuthorizationState = AuthorizationState.ApprovalRequired,
        mode: ExecutionMode = ExecutionMode.AuditOnly,
        enforcement: DeviceEnforcement = DeviceEnforcement.Normal,
        breaker: CircuitBreakerState = CircuitBreakerState.Closed,
        alreadyInDesiredState: Boolean = false,
        id: String = "preview"
    ) = ActionContext(
        id = id,
        action = action,
        target = target,
        authorization = authorization,
        executionMode = mode,
        currentEnforcement = enforcement,
        circuitBreaker = breaker,
        alreadyInDesiredState = alreadyInDesiredState
    )

    // --- Named scenarios, one per condition the matrix must handle ---

    fun quarantineAvailable() = context()

    fun quarantineAlreadyActive() = context(enforcement = DeviceEnforcement.Quarantined)

    fun releaseAvailable() = context(
        action = EnforcementAction.ReleaseQuarantine,
        enforcement = DeviceEnforcement.Quarantined,
        target = target(bindingId = "BND-4471", ownershipScope = "VLAN_SECURE")
    )

    fun releaseNotApplicable() = context(
        action = EnforcementAction.ReleaseQuarantine,
        enforcement = DeviceEnforcement.Normal
    )

    fun reverificationAvailable() = context(action = EnforcementAction.RequireReverification)

    fun reverificationWithoutIdentity() = context(
        action = EnforcementAction.RequireReverification,
        target = target(identityId = null, trust = TrustState.Unverified)
    )

    fun reverificationRevoked() = context(
        action = EnforcementAction.RequireReverification,
        target = target(trust = TrustState.Revoked)
    )

    fun staleTarget() = context(
        target = target(freshness = DataFreshness.Stale("Last seen 3h ago"), lastObserved = "3h ago")
    )

    fun authorizationDenied() = context(authorization = AuthorizationState.Denied)

    fun authorizationUnknown() = context(authorization = AuthorizationState.Unknown)

    fun breakerPaused() = context(breaker = CircuitBreakerState.Open)

    fun unknownEnforcement() = context(enforcement = DeviceEnforcement.Unknown)

    fun liveMode() = context(mode = ExecutionMode.Enforce, authorization = AuthorizationState.Authorized)

    fun unknownMode() = context(mode = ExecutionMode.Unknown)

    // ============================================================
    // Context handoff
    // ============================================================

    private val contexts = ConcurrentHashMap<String, ActionContext>()
    private val outcomes = ConcurrentHashMap<String, Outcome>()
    private val counter = AtomicLong(0)

    /** Insertion order, so the oldest prepared context is the one released. */
    private val order = java.util.concurrent.ConcurrentLinkedQueue<String>()

    /**
     * Stores a prepared context and returns its handle.
     *
     * Screens navigate with the handle rather than with loose target fields,
     * so a confirmation screen can never rebuild a target out of an address
     * and an assumption. If the handle cannot be resolved later — for example
     * after process death — the flow reports [ActionUiState.Unavailable]
     * rather than reconstructing anything.
     */
    fun store(context: ActionContext, outcome: Outcome = Outcome.Success): String {
        val id = "ACT-${counter.incrementAndGet()}"
        val stored = context.copy(id = id)
        contexts[id] = stored
        outcomes[id] = outcome
        order.add(id)
        evictOldest()
        return id
    }

    /**
     * How many prepared contexts are kept.
     *
     * Preparing an action stored a context and never released it, so a long
     * session accumulated one full [ActionContext] — target, identity, scope,
     * enforcement — for every action an operator ever started, including the
     * ones they backed out of.
     *
     * The bound is set by what a person can actually be in the middle of. A
     * prepared context is live between a tap on a device screen and a decision
     * on the confirmation screen; nobody has thirty-two of those open at once,
     * and the flow already handles a handle that cannot be resolved by
     * reporting [ActionUiState.Unavailable] rather than reconstructing
     * anything. Evicting the oldest is therefore safe in exactly the way
     * losing it to process death already is.
     *
     * Note what is *not* bounded: [ActionSubmissions]. Forgetting that a
     * context was submitted would let it be submitted again, which is the one
     * thing the idempotency boundary exists to prevent. It holds two strings
     * per action and is left alone deliberately.
     */
    private const val RETAINED_CONTEXTS = 32

    private fun evictOldest() {
        while (order.size > RETAINED_CONTEXTS) {
            val oldest = order.poll() ?: return
            contexts.remove(oldest)
            outcomes.remove(oldest)
        }
    }

    fun resolve(id: String): ActionContext? = contexts[id]

    fun outcomeFor(id: String): Outcome = outcomes[id] ?: Outcome.Success

    /** Clears stored contexts. Test-facing. */
    fun reset() {
        contexts.clear()
        outcomes.clear()
        order.clear()
    }
}

/**
 * Prepares an action for confirmation.
 *
 * This is the boundary a real client replaces: it is where the authoritative
 * target snapshot, authorization standing, execution mode and enforcement
 * state would be fetched. Today it assembles that context from preview data.
 */
object ActionPreparation {

    /**
     * Builds the context for an action against a device target and returns
     * the handle to hand to the confirmation flow.
     */
    fun prepare(
        action: EnforcementAction,
        target: ActionTarget,
        authorization: AuthorizationState,
        executionMode: ExecutionMode,
        currentEnforcement: com.example.nexa.ui.devices.DeviceEnforcement,
        circuitBreaker: CircuitBreakerState,
        alreadyInDesiredState: Boolean = false,
        /**
         * How readable the state behind this action was.
         *
         * Defaults to what the target's own observation freshness implies, so
         * a caller cannot forget it and silently get a confident context out
         * of uncertain data. A screen that knows more — that its whole
         * inventory was unavailable, say — passes that instead.
         */
        dataAvailability: NexaAvailability = availabilityOf(target.observationFreshness),
        outcome: EnforcementPreview.Outcome = EnforcementPreview.Outcome.Success
    ): String = EnforcementPreview.store(
        ActionContext(
            id = "",
            action = action,
            target = target,
            authorization = authorization,
            executionMode = executionMode,
            currentEnforcement = currentEnforcement,
            circuitBreaker = circuitBreaker,
            alreadyInDesiredState = alreadyInDesiredState,
            dataAvailability = dataAvailability
        ),
        outcome
    )
}
