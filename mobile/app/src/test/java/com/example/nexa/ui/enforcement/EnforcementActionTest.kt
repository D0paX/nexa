package com.example.nexa.ui.enforcement

import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.devices.DeviceEnforcement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the enforcement action rules.
 *
 * These guard the boundaries an operator's safety depends on: that trust is
 * not authorization, that a stale observation cannot silently become a
 * target, that a paused breaker cannot be worked around from the client,
 * that success is not reconciliation, and that each action describes only
 * what it actually does.
 */
class EnforcementActionTest {

    private fun ctx(
        action: EnforcementAction = EnforcementAction.QuarantineDevice,
        authorization: AuthorizationState = AuthorizationState.ApprovalRequired,
        mode: ExecutionMode = ExecutionMode.AuditOnly,
        enforcement: DeviceEnforcement = DeviceEnforcement.Normal,
        breaker: CircuitBreakerState = CircuitBreakerState.Closed,
        freshness: DataFreshness = DataFreshness.Live,
        identityId: String? = "TID-1",
        trust: TrustState = TrustState.Trusted,
        already: Boolean = false
    ) = EnforcementPreview.context(
        action = action,
        target = EnforcementPreview.target(
            identityId = identityId,
            trust = trust,
            freshness = freshness
        ),
        authorization = authorization,
        mode = mode,
        enforcement = enforcement,
        breaker = breaker,
        alreadyInDesiredState = already
    )

    private fun reasonOf(availability: ActionAvailability): String =
        (availability as ActionAvailability.Disabled).reason

    // ============================================================
    // Availability
    // ============================================================

    @Test
    fun `quarantine is available for a normal fresh authorized target`() {
        assertEquals(ActionAvailability.Available, availabilityOf(ctx()))
    }

    @Test
    fun `quarantine is refused when already quarantined`() {
        val result = availabilityOf(ctx(enforcement = DeviceEnforcement.Quarantined))
        assertTrue(reasonOf(result).contains("already quarantined"))
    }

    @Test
    fun `release is hidden when the target is not quarantined`() {
        assertEquals(
            ActionAvailability.Hidden,
            availabilityOf(ctx(action = EnforcementAction.ReleaseQuarantine, enforcement = DeviceEnforcement.Normal))
        )
    }

    @Test
    fun `release is available when the target is quarantined`() {
        assertEquals(
            ActionAvailability.Available,
            availabilityOf(ctx(action = EnforcementAction.ReleaseQuarantine, enforcement = DeviceEnforcement.Quarantined))
        )
    }

    @Test
    fun `reverification is hidden without an identity`() {
        assertEquals(
            ActionAvailability.Hidden,
            availabilityOf(
                ctx(
                    action = EnforcementAction.RequireReverification,
                    identityId = null,
                    trust = TrustState.Unverified
                )
            )
        )
    }

    @Test
    fun `reverification is refused for a revoked identity`() {
        val result = availabilityOf(
            ctx(action = EnforcementAction.RequireReverification, trust = TrustState.Revoked)
        )
        assertTrue(reasonOf(result).contains("does not restore"))
    }

    // ============================================================
    // Authorization — never inferred from trust
    // ============================================================

    @Test
    fun `denied authorization blocks the action`() {
        val result = availabilityOf(ctx(authorization = AuthorizationState.Denied))
        assertTrue(reasonOf(result).contains("Authorization denied"))
    }

    @Test
    fun `unknown authorization blocks the action rather than guessing`() {
        val result = availabilityOf(ctx(authorization = AuthorizationState.Unknown))
        assertTrue(reasonOf(result).contains("unknown"))
    }

    /** A trusted identity does not authorize anything by itself. */
    @Test
    fun `trusted identity with denied authorization is still refused`() {
        val result = availabilityOf(
            ctx(authorization = AuthorizationState.Denied, trust = TrustState.Trusted)
        )
        assertTrue(result is ActionAvailability.Disabled)
    }

    @Test
    fun `approval required still permits the request to be prepared`() {
        assertEquals(
            ActionAvailability.Available,
            availabilityOf(ctx(authorization = AuthorizationState.ApprovalRequired))
        )
        assertTrue(AuthorizationState.ApprovalRequired.explanation.contains("not by this screen"))
    }

    /** Authorization refusal is not an execution failure. */
    @Test
    fun `denied execution state states that nothing ran`() {
        assertTrue(ExecutionState.Denied.explanation.contains("never started"))
    }

    // ============================================================
    // Circuit breaker
    // ============================================================

    @Test
    fun `open breaker blocks enforcement actions`() {
        val result = availabilityOf(ctx(breaker = CircuitBreakerState.Open))
        assertTrue(reasonOf(result).contains("circuit breaker"))
    }

    @Test
    fun `open breaker also blocks release`() {
        val result = availabilityOf(
            ctx(
                action = EnforcementAction.ReleaseQuarantine,
                enforcement = DeviceEnforcement.Quarantined,
                breaker = CircuitBreakerState.Open
            )
        )
        assertTrue(result is ActionAvailability.Disabled)
    }

    /** Reverification is not a firewall mutation, so the breaker does not gate it. */
    @Test
    fun `open breaker does not block reverification`() {
        assertEquals(
            ActionAvailability.Available,
            availabilityOf(
                ctx(action = EnforcementAction.RequireReverification, breaker = CircuitBreakerState.Open)
            )
        )
    }

    @Test
    fun `half open breaker still allows execution`() {
        assertTrue(CircuitBreakerState.HalfOpen.allowsExecution)
        assertFalse(CircuitBreakerState.Open.allowsExecution)
    }

    // ============================================================
    // Target safety
    // ============================================================

    /** A stale observation may no longer name the device it would reach. */
    @Test
    fun `stale target blocks quarantine`() {
        val result = availabilityOf(ctx(freshness = DataFreshness.Stale("3h ago")))
        assertTrue(reasonOf(result).contains("stale"))
    }

    @Test
    fun `stale target blocks release`() {
        val result = availabilityOf(
            ctx(
                action = EnforcementAction.ReleaseQuarantine,
                enforcement = DeviceEnforcement.Quarantined,
                freshness = DataFreshness.Stale("3h ago")
            )
        )
        assertTrue(result is ActionAvailability.Disabled)
    }

    @Test
    fun `unknown observation freshness blocks enforcement`() {
        val result = availabilityOf(ctx(freshness = DataFreshness.Unknown))
        assertTrue(result is ActionAvailability.Disabled)
    }

    /** Reverification asks the identity to prove itself, so staleness qualifies rather than blocks. */
    @Test
    fun `stale target does not block reverification`() {
        assertEquals(
            ActionAvailability.Available,
            availabilityOf(
                ctx(
                    action = EnforcementAction.RequireReverification,
                    freshness = DataFreshness.Stale("3h ago")
                )
            )
        )
    }

    @Test
    fun `unknown enforcement state blocks enforcement actions`() {
        val result = availabilityOf(ctx(enforcement = DeviceEnforcement.Unknown))
        assertTrue(reasonOf(result).contains("unknown"))
    }

    /** Scope and identity survive the handoff; an address is never the identity. */
    @Test
    fun `prepared context retains scope identity and mac separately`() {
        EnforcementPreview.reset()
        val id = ActionPreparation.prepare(
            action = EnforcementAction.QuarantineDevice,
            target = EnforcementPreview.target(scope = "VLAN_BUILD", identityId = "TID-2B0C", ip = "10.20.4.11"),
            authorization = AuthorizationState.ApprovalRequired,
            executionMode = ExecutionMode.AuditOnly,
            currentEnforcement = DeviceEnforcement.Normal,
            circuitBreaker = CircuitBreakerState.Closed
        )
        val resolved = EnforcementPreview.resolve(id)!!
        assertEquals("VLAN_BUILD", resolved.target.scope)
        assertEquals("TID-2B0C", resolved.target.identityId)
        assertEquals("10.20.4.11", resolved.target.ip)
        // The identity is not the address, and neither is the MAC.
        assertFalse(resolved.target.identityId == resolved.target.ip)
        assertFalse(resolved.target.identityId == resolved.target.mac)
    }

    /** Two identical MACs in different scopes are different logical targets. */
    @Test
    fun `scope distinguishes otherwise identical targets`() {
        val a = EnforcementPreview.target(mac = "00:11:22:33:44:55", scope = "VLAN_A")
        val b = EnforcementPreview.target(mac = "00:11:22:33:44:55", scope = "VLAN_B")
        assertEquals(a.mac, b.mac)
        assertFalse(a.scope == b.scope)
        assertFalse(a == b)
    }

    /** An unresolvable handle yields no context; nothing is reconstructed. */
    @Test
    fun `unknown context handle resolves to nothing`() {
        EnforcementPreview.reset()
        assertEquals(null, EnforcementPreview.resolve("ACT-does-not-exist"))
    }

    // ============================================================
    // Idempotency
    // ============================================================

    @Test
    fun `an already enforced target reports idempotency rather than re-running`() {
        val result = availabilityOf(ctx(already = true))
        assertTrue(reasonOf(result).contains("already enforced"))
    }

    @Test
    fun `an already released target says so`() {
        val result = availabilityOf(
            ctx(
                action = EnforcementAction.ReleaseQuarantine,
                enforcement = DeviceEnforcement.Quarantined,
                already = true
            )
        )
        assertTrue(reasonOf(result).contains("already been released"))
    }

    // ============================================================
    // Execution semantics
    // ============================================================

    @Test
    fun `execution lifecycle classifies in-flight and terminal states`() {
        assertTrue(ExecutionState.Executing.isInFlight)
        assertTrue(ExecutionState.Reconciling.isInFlight)
        assertFalse(ExecutionState.Succeeded.isInFlight)
        assertTrue(ExecutionState.Succeeded.isTerminal)
        assertTrue(ExecutionState.RollbackFailed.isTerminal)
        assertTrue(ExecutionState.Unknown.isTerminal)
    }

    /** Success is not reconciliation, and the wording never claims it is. */
    @Test
    fun `succeeded explanation does not claim reconciliation`() {
        val text = ExecutionState.Succeeded.explanation.lowercase()
        assertFalse(text.contains("reconcil"))
    }

    @Test
    fun `reconciling is a distinct state from succeeded`() {
        assertFalse(ExecutionState.Reconciling == ExecutionState.Succeeded)
        assertTrue(ExecutionState.Reconciling.explanation.contains("confirming"))
    }

    @Test
    fun `unknown outcome is never presented as success or failure`() {
        assertTrue(ExecutionState.Unknown.explanation.contains("cannot determine"))
        assertFalse(ExecutionState.Unknown.explanation.lowercase().contains("succeeded"))
        assertFalse(ExecutionState.Unknown.explanation.lowercase().contains("failed."))
    }

    // ============================================================
    // Rollback
    // ============================================================

    @Test
    fun `rollback states are distinct from ordinary failure`() {
        assertFalse(ExecutionState.RolledBack == ExecutionState.Failed)
        assertFalse(ExecutionState.RollbackFailed == ExecutionState.Failed)
        assertTrue(ExecutionState.RolledBack.explanation.contains("prior state was restored"))
    }

    /** Rollback failure is a security condition, not a cancellation. */
    @Test
    fun `rollback failure never reads as cancelled or successful`() {
        val text = ExecutionState.RollbackFailed.explanation.lowercase()
        assertTrue(text.contains("rollback failed"))
        assertTrue(text.contains("did not return"))
        assertFalse(text.contains("cancelled"))
        assertFalse(text.contains("succeeded"))
    }

    @Test
    fun `rollback failure is escalated above ordinary failure`() {
        assertEquals(com.example.nexa.theme.NexaStatus.Critical, ExecutionState.RollbackFailed.status)
        assertEquals(com.example.nexa.theme.NexaStatus.Danger, ExecutionState.Failed.status)
    }

    // ============================================================
    // AUDIT_ONLY vs live
    // ============================================================

    @Test
    fun `audit only consequence states no firewall mutation will occur`() {
        val consequence = consequenceOf(EnforcementAction.QuarantineDevice, ExecutionMode.AuditOnly)
        assertTrue(consequence.summary.contains("SIMULATION"))
        assertTrue(consequence.summary.contains("no firewall mutation will occur"))
    }

    @Test
    fun `live consequence does not use simulation language`() {
        val consequence = consequenceOf(EnforcementAction.QuarantineDevice, ExecutionMode.Enforce)
        assertFalse(consequence.summary.contains("SIMULATION"))
        assertTrue(consequence.destructive)
    }

    @Test
    fun `unknown execution mode is never labelled live`() {
        assertFalse(ExecutionMode.Unknown.label.lowercase().contains("live"))
        assertTrue(ExecutionMode.Unknown.label.lowercase().contains("unknown"))
    }

    // ============================================================
    // REGRESSION GUARDS — defects found in earlier checkpoints
    // ============================================================

    /**
     * Guard 1 and 2: the confirmation screen once displayed a hardcoded IP
     * and a hardcoded VERIFIED trust state for every target. Every field must
     * now come from the prepared context.
     */
    @Test
    fun `no target field is fabricated by the action model`() {
        EnforcementPreview.reset()
        val id = ActionPreparation.prepare(
            action = EnforcementAction.QuarantineDevice,
            target = EnforcementPreview.target(
                ip = null,
                identityId = null,
                trust = TrustState.Unverified,
                scope = "VLAN_LAB"
            ),
            authorization = AuthorizationState.ApprovalRequired,
            executionMode = ExecutionMode.AuditOnly,
            currentEnforcement = DeviceEnforcement.Normal,
            circuitBreaker = CircuitBreakerState.Closed
        )
        val target = EnforcementPreview.resolve(id)!!.target
        // A target with no observed address keeps none.
        assertEquals(null, target.ip)
        assertFalse(target.ip == "192.168.1.105")
        // An unverified target is never promoted to trusted.
        assertEquals(TrustState.Unverified, target.trust)
        assertFalse(target.trust == TrustState.Trusted)
        assertEquals(null, target.identityId)
    }

    /**
     * Guard 3: REQUIRE_REVERIFICATION once inherited quarantine's consequence
     * text, telling operators they were isolating a device when they were not.
     */
    @Test
    fun `reverification copy never uses quarantine or firewall language`() {
        listOf(ExecutionMode.AuditOnly, ExecutionMode.Enforce, ExecutionMode.Unknown).forEach { mode ->
            val text = consequenceOf(EnforcementAction.RequireReverification, mode).summary.lowercase()
            assertTrue(text.contains("verified again"))
            assertTrue(text.contains("does not quarantine"))
            assertFalse(text.contains("isolate the device from all network access"))
            assertFalse(text.contains("connections will be dropped"))
        }
    }

    @Test
    fun `release copy is not quarantine copy`() {
        val release = consequenceOf(EnforcementAction.ReleaseQuarantine, ExecutionMode.Enforce).summary
        val quarantine = consequenceOf(EnforcementAction.QuarantineDevice, ExecutionMode.Enforce).summary
        assertFalse(release == quarantine)
        assertTrue(release.contains("removes the enforcement binding"))
        assertFalse(release.contains("isolate the device from all network access"))
    }

    /** An unrecognized action code refuses to manufacture a consequence. */
    @Test
    fun `unknown action code refuses to describe itself`() {
        val consequence = consequenceForCode("DELETE_EVERYTHING", ExecutionMode.Enforce)
        assertFalse(consequence.known)
        assertTrue(consequence.summary.contains("cannot describe"))
        assertTrue(consequence.destructive)
    }

    @Test
    fun `every known action code maps to its own consequence`() {
        val summaries = EnforcementAction.entries.map {
            consequenceForCode(it.code, ExecutionMode.Enforce).summary
        }
        assertEquals(summaries.size, summaries.distinct().size)
        assertTrue(summaries.all { it.isNotBlank() })
    }

    // ============================================================
    // Action identity
    // ============================================================

    @Test
    fun `action codes match the Phase 4 vocabulary`() {
        assertEquals("QUARANTINE_DEVICE", EnforcementAction.QuarantineDevice.code)
        assertEquals("RELEASE_QUARANTINE", EnforcementAction.ReleaseQuarantine.code)
        assertEquals("REQUIRE_REVERIFICATION", EnforcementAction.RequireReverification.code)
    }

    /** Only enforcement actions can mutate firewall state. */
    @Test
    fun `reverification is not an enforcement mutation`() {
        assertTrue(EnforcementAction.QuarantineDevice.mutatesEnforcement)
        assertTrue(EnforcementAction.ReleaseQuarantine.mutatesEnforcement)
        assertFalse(EnforcementAction.RequireReverification.mutatesEnforcement)
    }

    // ============================================================
    // Disabled reasons are always explained
    // ============================================================

    @Test
    fun `every disabled outcome carries a non-vague reason`() {
        val blocked = listOf(
            ctx(enforcement = DeviceEnforcement.Quarantined),
            ctx(authorization = AuthorizationState.Denied),
            ctx(authorization = AuthorizationState.Unknown),
            ctx(breaker = CircuitBreakerState.Open),
            ctx(enforcement = DeviceEnforcement.Unknown),
            ctx(already = true),
            ctx(freshness = DataFreshness.Stale("3h ago")),
            ctx(action = EnforcementAction.RequireReverification, trust = TrustState.Revoked)
        )
        blocked.forEach { context ->
            val availability = availabilityOf(context)
            assertTrue("expected a disabled outcome", availability is ActionAvailability.Disabled)
            val reason = reasonOf(availability)
            assertTrue("reason must be specific", reason.length > 20)
            assertFalse(reason.equals("Unavailable.", ignoreCase = true))
            assertNotNull(reason)
        }
    }

    // ============================================================
    // Preview integrity
    // ============================================================

    @Test
    fun `preview scenarios produce the availability they claim`() {
        assertEquals(ActionAvailability.Available, availabilityOf(EnforcementPreview.quarantineAvailable()))
        assertTrue(availabilityOf(EnforcementPreview.quarantineAlreadyActive()) is ActionAvailability.Disabled)
        assertEquals(ActionAvailability.Available, availabilityOf(EnforcementPreview.releaseAvailable()))
        assertEquals(ActionAvailability.Hidden, availabilityOf(EnforcementPreview.releaseNotApplicable()))
        assertEquals(ActionAvailability.Available, availabilityOf(EnforcementPreview.reverificationAvailable()))
        assertEquals(ActionAvailability.Hidden, availabilityOf(EnforcementPreview.reverificationWithoutIdentity()))
        assertTrue(availabilityOf(EnforcementPreview.reverificationRevoked()) is ActionAvailability.Disabled)
        assertTrue(availabilityOf(EnforcementPreview.staleTarget()) is ActionAvailability.Disabled)
        assertTrue(availabilityOf(EnforcementPreview.authorizationDenied()) is ActionAvailability.Disabled)
        assertTrue(availabilityOf(EnforcementPreview.authorizationUnknown()) is ActionAvailability.Disabled)
        assertTrue(availabilityOf(EnforcementPreview.breakerPaused()) is ActionAvailability.Disabled)
        assertTrue(availabilityOf(EnforcementPreview.unknownEnforcement()) is ActionAvailability.Disabled)
    }

    @Test
    fun `release scenario carries binding and ownership scope`() {
        val context = EnforcementPreview.releaseAvailable()
        assertEquals("BND-4471", context.target.bindingId)
        assertEquals("VLAN_SECURE", context.target.ownershipScope)
        // Ownership is a scope, never an address.
        assertFalse(context.target.ownershipScope == context.target.ip)
    }
}
