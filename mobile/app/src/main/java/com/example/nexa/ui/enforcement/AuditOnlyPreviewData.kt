package com.example.nexa.ui.enforcement

import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.devices.DeviceEnforcement

/**
 * PREVIEW DATA — NOT LIVE SYSTEM STATE.
 *
 * AUDIT_ONLY scenarios for development and review.
 *
 * Every scenario here is fabricated. None of them executes anything, and a
 * simulated "success" means only that the preview reached that state — no
 * nftables rule is written, no Phase 4 pipeline runs, and no firewall is
 * touched by anything in this file or by the screens that render it.
 *
 * The scenarios exist to prove one property in particular: that an operator
 * looking at any stage of an AUDIT_ONLY action can tell it apart from a live
 * one.
 */
object AuditOnlyPreview {

    // --- 1-4: the ordinary simulated quarantine path ---

    /** AUDIT_ONLY quarantine, available to request. */
    fun quarantineAvailable(): ActionContext = EnforcementPreview.context(
        action = EnforcementAction.QuarantineDevice,
        mode = ExecutionMode.AuditOnly,
        authorization = AuthorizationState.ApprovalRequired,
        enforcement = DeviceEnforcement.Normal
    )

    /** The same context, stored and ready to walk through to a simulated success. */
    fun quarantineSuccessHandle(): String = EnforcementPreview.store(
        quarantineAvailable(),
        EnforcementPreview.Outcome.Success
    )

    // --- 5: simulated failure ---

    fun quarantineFailureHandle(): String = EnforcementPreview.store(
        quarantineAvailable(),
        EnforcementPreview.Outcome.Failed
    )

    // --- 6: simulated rollback ---

    fun quarantineRollbackHandle(): String = EnforcementPreview.store(
        quarantineAvailable(),
        EnforcementPreview.Outcome.RolledBack
    )

    fun quarantineRollbackFailedHandle(): String = EnforcementPreview.store(
        quarantineAvailable(),
        EnforcementPreview.Outcome.RollbackFailed
    )

    // --- 7-8: authorization is independent of execution mode ---

    /**
     * AUDIT_ONLY still requires authorization. Simulation is not a bypass:
     * the request is evaluated the same way, it simply does not mutate.
     */
    fun approvalRequired(): ActionContext = EnforcementPreview.context(
        mode = ExecutionMode.AuditOnly,
        authorization = AuthorizationState.ApprovalRequired
    )

    /** AUDIT_ONLY with authorization refused — refused just as a live one would be. */
    fun authorizationDenied(): ActionContext = EnforcementPreview.context(
        mode = ExecutionMode.AuditOnly,
        authorization = AuthorizationState.Denied
    )

    fun deniedHandle(): String = EnforcementPreview.store(
        approvalRequired(),
        EnforcementPreview.Outcome.Denied
    )

    // --- 9: a simulation does not relax target requirements ---

    /**
     * A stale target blocks an AUDIT_ONLY quarantine exactly as it blocks a
     * live one. The requirement exists to keep the request honest about what
     * it names, not merely to protect the kernel.
     */
    fun staleTarget(): ActionContext = EnforcementPreview.context(
        mode = ExecutionMode.AuditOnly,
        target = EnforcementPreview.target(
            freshness = DataFreshness.Stale("Last seen 3h ago"),
            lastObserved = "3h ago"
        )
    )

    // --- 10: circuit breaker interaction ---

    /**
     * The breaker halts enforcement, and the client does not invent an
     * exemption for simulation: Phase 4 owns that rule, not this UI.
     */
    fun breakerPaused(): ActionContext = EnforcementPreview.context(
        mode = ExecutionMode.AuditOnly,
        breaker = CircuitBreakerState.Open
    )

    /** A trust operation in AUDIT_ONLY — not a firewall mutation in either mode. */
    fun reverification(): ActionContext = EnforcementPreview.context(
        action = EnforcementAction.RequireReverification,
        mode = ExecutionMode.AuditOnly,
        target = EnforcementPreview.target(identityId = "TID-88F1", trust = TrustState.Trusted)
    )

    fun reverificationHandle(): String = EnforcementPreview.store(
        reverification(),
        EnforcementPreview.Outcome.Success
    )

    // --- 11: unknown mode is refused rather than guessed ---

    fun unknownMode(): ActionContext = EnforcementPreview.context(
        mode = ExecutionMode.Unknown
    )

    // --- 12: the live comparison ---

    fun liveQuarantine(): ActionContext = EnforcementPreview.context(
        action = EnforcementAction.QuarantineDevice,
        mode = ExecutionMode.Enforce,
        authorization = AuthorizationState.Authorized
    )

    fun liveQuarantineHandle(): String = EnforcementPreview.store(
        liveQuarantine(),
        EnforcementPreview.Outcome.Success
    )

    /** Every AUDIT_ONLY context, for exhaustive checks. */
    fun allAuditOnlyContexts(): List<ActionContext> = listOf(
        quarantineAvailable(),
        approvalRequired(),
        authorizationDenied(),
        staleTarget(),
        breakerPaused(),
        reverification()
    )
}
