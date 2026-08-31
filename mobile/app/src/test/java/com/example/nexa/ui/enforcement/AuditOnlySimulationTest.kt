package com.example.nexa.ui.enforcement

import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.overview.EnforcementState
import com.example.nexa.ui.overview.SecurityPosture
import com.example.nexa.ui.overview.postureDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the AUDIT_ONLY simulation experience.
 *
 * The property under test throughout: an operator can never mistake a
 * simulated action for a live one, at any stage, in any wording NEXA
 * produces.
 */
class AuditOnlySimulationTest {

    /**
     * Words that would be read as a real kernel change. A simulation may only
     * use them when the same sentence also says no mutation occurred.
     */
    private val firewallClaimWords = listOf(
        "quarantined", "released", "blocked", "enforced",
        "firewall updated", "firewall reconciled", "firewall state changed",
        // In-flight wording counts too: describing a simulation as being
        // "applied" tells the operator a mutation is under way.
        "is applying", "being applied", "reconciled"
    )

    private fun assertNoUnqualifiedFirewallClaim(text: String) {
        val lower = text.lowercase()
        val disclaims = lower.contains("no firewall mutation") ||
            lower.contains("simulation") ||
            lower.contains("simulated")
        firewallClaimWords.forEach { word ->
            if (lower.contains(word)) {
                assertTrue(
                    "\"$word\" appears without a simulation disclaimer in: $text",
                    disclaims
                )
            }
        }
    }

    // ============================================================
    // Mode separation
    // ============================================================

    @Test
    fun `execution modes are three distinct values`() {
        assertNotEquals(ExecutionMode.AuditOnly, ExecutionMode.Enforce)
        assertNotEquals(ExecutionMode.AuditOnly, ExecutionMode.Unknown)
        assertNotEquals(ExecutionMode.Enforce, ExecutionMode.Unknown)
        assertEquals(3, ExecutionMode.entries.size)
    }

    @Test
    fun `mode labels are distinct and unknown is never live`() {
        assertNotEquals(ExecutionMode.AuditOnly.label, ExecutionMode.Enforce.label)
        assertFalse(ExecutionMode.Unknown.label.lowercase().contains("live"))
        assertFalse(ExecutionMode.AuditOnly.label.lowercase().contains("live"))
        assertTrue(ExecutionMode.Enforce.label.lowercase().contains("live"))
    }

    // ============================================================
    // Confirmation button
    // ============================================================

    /** AUDIT_ONLY asks the operator to simulate, never to confirm a mutation. */
    @Test
    fun `audit only confirm button says simulate`() {
        EnforcementAction.entries.forEach { action ->
            val label = confirmLabel(action, ExecutionMode.AuditOnly)
            assertTrue("expected SIMULATE in \"$label\"", label.startsWith("SIMULATE"))
            assertFalse(label.startsWith("CONFIRM"))
        }
    }

    @Test
    fun `live confirm button does not say simulate`() {
        val label = confirmLabel(EnforcementAction.QuarantineDevice, ExecutionMode.Enforce)
        assertTrue(label.startsWith("CONFIRM"))
        assertFalse(label.contains("SIMULATE"))
    }

    @Test
    fun `unknown mode offers neither confirm nor simulate`() {
        val label = confirmLabel(EnforcementAction.QuarantineDevice, ExecutionMode.Unknown)
        assertFalse(label.contains("CONFIRM"))
        assertFalse(label.contains("SIMULATE"))
        assertTrue(label.contains("UNKNOWN"))
    }

    // ============================================================
    // Unknown mode safety
    // ============================================================

    /** Unknown is refused rather than guessed in either direction. */
    @Test
    fun `unknown execution mode blocks the action`() {
        val availability = availabilityOf(AuditOnlyPreview.unknownMode())
        assertTrue(availability is ActionAvailability.Disabled)
        val reason = (availability as ActionAvailability.Disabled).reason
        assertTrue(reason.contains("Execution mode is unknown"))
        assertFalse(reason.lowercase().contains("live enforcement"))
    }

    @Test
    fun `unknown mode never renders live enforcement wording`() {
        val text = ExecutionMode.Unknown.label + " " + inFlightModeLabel(ExecutionMode.Unknown)
        assertFalse(text.contains("LIVE ENFORCEMENT"))
    }

    // ============================================================
    // Result headlines
    // ============================================================

    @Test
    fun `simulated success does not claim enforcement`() {
        val headline = resultHeadline(ExecutionState.Succeeded, ExecutionMode.AuditOnly)
        assertEquals("SIMULATION COMPLETE", headline)
        assertFalse(headline.contains("ENFORCEMENT"))
    }

    @Test
    fun `live success keeps the plain execution wording`() {
        assertEquals("SUCCEEDED", resultHeadline(ExecutionState.Succeeded, ExecutionMode.Enforce))
    }

    @Test
    fun `simulated failure is distinct from live failure`() {
        val simulated = resultHeadline(ExecutionState.Failed, ExecutionMode.AuditOnly)
        val live = resultHeadline(ExecutionState.Failed, ExecutionMode.Enforce)
        assertEquals("SIMULATION FAILED", simulated)
        assertNotEquals(simulated, live)
    }

    @Test
    fun `simulated rollback is distinct from live rollback`() {
        val simulated = resultHeadline(ExecutionState.RolledBack, ExecutionMode.AuditOnly)
        val live = resultHeadline(ExecutionState.RolledBack, ExecutionMode.Enforce)
        assertEquals("SIMULATION ROLLED BACK", simulated)
        assertNotEquals(simulated, live)

        val simulatedFailure = resultHeadline(ExecutionState.RollbackFailed, ExecutionMode.AuditOnly)
        assertEquals("SIMULATION ROLLBACK FAILED", simulatedFailure)
        assertNotEquals(simulatedFailure, resultHeadline(ExecutionState.RollbackFailed, ExecutionMode.Enforce))
    }

    /** Authorization refusal is real in either mode and reported as itself. */
    @Test
    fun `denied is reported as authorization denied in both modes`() {
        assertEquals("AUTHORIZATION DENIED", resultHeadline(ExecutionState.Denied, ExecutionMode.AuditOnly))
        assertEquals("AUTHORIZATION DENIED", resultHeadline(ExecutionState.Denied, ExecutionMode.Enforce))
    }

    @Test
    fun `simulated unknown outcome is labelled as a simulation outcome`() {
        assertEquals("SIMULATION OUTCOME UNKNOWN", resultHeadline(ExecutionState.Unknown, ExecutionMode.AuditOnly))
    }

    // ============================================================
    // COPY GUARDS — the firewall-claim protection
    // ============================================================

    /**
     * The central guard: no AUDIT_ONLY explanation may use enforcement
     * language without saying in the same breath that nothing was applied.
     */
    @Test
    fun `no audit only result explanation makes an unqualified firewall claim`() {
        ExecutionState.entries.forEach { state ->
            assertNoUnqualifiedFirewallClaim(resultExplanation(state, ExecutionMode.AuditOnly))
        }
    }

    /** Every stage, in-flight included — not only the terminal ones. */
    @Test
    fun `every audit only result explanation states no firewall mutation occurred`() {
        ExecutionState.entries.forEach { state ->
            val text = resultExplanation(state, ExecutionMode.AuditOnly).lowercase()
            assertTrue(
                "state $state must state that no firewall mutation occurred",
                text.contains("no firewall mutation")
            )
        }
    }

    @Test
    fun `audit only consequence copy never makes an unqualified firewall claim`() {
        EnforcementAction.entries.forEach { action ->
            assertNoUnqualifiedFirewallClaim(consequenceOf(action, ExecutionMode.AuditOnly).summary)
        }
    }

    @Test
    fun `audit only release does not say quarantine removed without qualification`() {
        val text = consequenceOf(EnforcementAction.ReleaseQuarantine, ExecutionMode.AuditOnly).summary
        assertTrue(text.contains("SIMULATION"))
        assertTrue(text.lowercase().contains("no firewall mutation will occur"))
    }

    @Test
    fun `audit only reverification never claims the identity was verified`() {
        val text = consequenceOf(EnforcementAction.RequireReverification, ExecutionMode.AuditOnly).summary.lowercase()
        assertFalse(text.contains("identity verified"))
        assertFalse(text.contains("has been verified"))
        assertTrue(text.contains("verified again"))
    }

    @Test
    fun `live explanations are allowed to use enforcement language`() {
        // The guard must not be so blunt that live wording becomes impossible.
        val live = resultExplanation(ExecutionState.Succeeded, ExecutionMode.Enforce)
        assertEquals(ExecutionState.Succeeded.explanation, live)
    }

    // ============================================================
    // Reconciliation
    // ============================================================

    /** A simulation has no kernel state to reconcile against, and says so. */
    @Test
    fun `audit only never reports reconciliation`() {
        assertEquals("SIMULATED — NOT APPLIED", reconciliationLabel(ExecutionMode.AuditOnly, reconciled = true))
        assertEquals("SIMULATED — NOT APPLIED", reconciliationLabel(ExecutionMode.AuditOnly, reconciled = false))
        assertFalse(reconciliationLabel(ExecutionMode.AuditOnly, true).contains("RECONCILED"))
    }

    @Test
    fun `live reconciliation is reported normally`() {
        assertEquals("RECONCILED", reconciliationLabel(ExecutionMode.Enforce, reconciled = true))
        assertEquals("NOT CONFIRMED", reconciliationLabel(ExecutionMode.Enforce, reconciled = false))
    }

    @Test
    fun `simulated reconciliation uses the simulation status not success`() {
        assertEquals(
            com.example.nexa.theme.NexaStatus.Simulation,
            reconciliationStatus(ExecutionMode.AuditOnly, reconciled = true)
        )
    }

    // ============================================================
    // In-flight visibility
    // ============================================================

    /** Mode stays visible while the action runs, not only at confirmation. */
    @Test
    fun `in flight label states the mode`() {
        assertTrue(inFlightModeLabel(ExecutionMode.AuditOnly).contains("SIMULATION"))
        assertTrue(inFlightModeLabel(ExecutionMode.AuditOnly).contains("NO FIREWALL MUTATION"))
        assertEquals("LIVE ENFORCEMENT", inFlightModeLabel(ExecutionMode.Enforce))
        assertNotEquals(
            inFlightModeLabel(ExecutionMode.AuditOnly),
            inFlightModeLabel(ExecutionMode.Enforce)
        )
    }

    // ============================================================
    // Independence from other security facts
    // ============================================================

    /** Simulation is not an authorization bypass. */
    @Test
    fun `audit only still requires authorization`() {
        assertEquals(
            ActionAvailability.Available,
            availabilityOf(AuditOnlyPreview.approvalRequired())
        )
        val denied = availabilityOf(AuditOnlyPreview.authorizationDenied())
        assertTrue(denied is ActionAvailability.Disabled)
        assertTrue((denied as ActionAvailability.Disabled).reason.contains("Authorization denied"))
    }

    /** Simulation does not relax the target freshness requirement. */
    @Test
    fun `audit only does not bypass stale target protection`() {
        val availability = availabilityOf(AuditOnlyPreview.staleTarget())
        assertTrue(availability is ActionAvailability.Disabled)
        assertTrue((availability as ActionAvailability.Disabled).reason.contains("stale"))
    }

    /** The client invents no simulation exemption from the breaker. */
    @Test
    fun `audit only does not bypass the circuit breaker`() {
        val availability = availabilityOf(AuditOnlyPreview.breakerPaused())
        assertTrue(availability is ActionAvailability.Disabled)
        assertTrue((availability as ActionAvailability.Disabled).reason.contains("circuit breaker"))
    }

    /** Simulation does not promote an untrusted target. */
    @Test
    fun `audit only does not change trust standing`() {
        val context = EnforcementPreview.context(
            mode = ExecutionMode.AuditOnly,
            target = EnforcementPreview.target(identityId = null, trust = TrustState.Unverified)
        )
        assertEquals(TrustState.Unverified, context.target.trust)
        assertNotEquals(TrustState.Trusted, context.target.trust)
    }

    /** Nor does it change the target's recorded enforcement state. */
    @Test
    fun `audit only context does not mutate the device enforcement state`() {
        val context = AuditOnlyPreview.quarantineAvailable()
        assertEquals(DeviceEnforcement.Normal, context.currentEnforcement)
        assertNotEquals(DeviceEnforcement.Quarantined, context.currentEnforcement)
    }

    @Test
    fun `audit only target snapshot fields are not fabricated`() {
        val context = AuditOnlyPreview.staleTarget()
        assertTrue(context.targetIsStale)
        assertTrue(context.target.observationFreshness is DataFreshness.Stale)
        assertEquals("VLAN_SECURE", context.target.scope)
    }

    // ============================================================
    // Overview
    // ============================================================

    private fun overviewEnforcement(mode: ExecutionMode, quarantined: Int = 3) = EnforcementState(
        enabled = true,
        circuitBreaker = CircuitBreakerState.Closed,
        executionMode = mode,
        quarantinedDevices = quarantined,
        pendingActions = 0,
        failedActions = 0,
        reconciliationIssues = 0
    )

    /**
     * The Overview must not describe enforcement as being applied when the
     * configured mode cannot apply anything.
     */
    @Test
    fun `audit only posture detail does not claim active enforcement`() {
        val detail = postureDetail(SecurityPosture.Enforcing, overviewEnforcement(ExecutionMode.AuditOnly))
        assertTrue(detail.contains("AUDIT_ONLY"))
        assertTrue(detail.contains("simulated"))
        assertFalse(detail.contains("Enforcement is active"))
    }

    @Test
    fun `live posture detail keeps the enforcement wording`() {
        val detail = postureDetail(SecurityPosture.Enforcing, overviewEnforcement(ExecutionMode.Enforce))
        assertTrue(detail.contains("Enforcement is active"))
        assertFalse(detail.contains("AUDIT_ONLY"))
    }

    @Test
    fun `audit only secure posture states actions are simulated`() {
        val detail = postureDetail(SecurityPosture.Secure, overviewEnforcement(ExecutionMode.AuditOnly, 0))
        assertTrue(detail.contains("AUDIT_ONLY"))
        assertTrue(detail.lowercase().contains("no firewall mutation"))
    }

    @Test
    fun `audit only posture detail never claims firewall protection`() {
        SecurityPosture.entries.forEach { posture ->
            val detail = postureDetail(posture, overviewEnforcement(ExecutionMode.AuditOnly))
            assertNoUnqualifiedFirewallClaim(detail)
            assertFalse(detail.lowercase().contains("firewall protected"))
        }
    }

    // ============================================================
    // Activity history / audit readiness
    // ============================================================

    /** Execution mode survives on the event, so history can distinguish later. */
    @Test
    fun `activity entries retain execution mode`() {
        val simulated = com.example.nexa.ui.common.ActivityEntry(
            id = "A1",
            kind = com.example.nexa.ui.common.ActivityKind.EnforcementCompleted,
            title = "Quarantine applied",
            target = "00:11",
            timeAgo = "2m ago",
            status = com.example.nexa.theme.NexaStatus.Simulation,
            executionMode = ExecutionMode.AuditOnly
        )
        assertTrue(simulated.isSimulated)
        assertFalse(simulated.isLiveEnforcement)

        val live = simulated.copy(executionMode = ExecutionMode.Enforce)
        assertTrue(live.isLiveEnforcement)
        assertFalse(live.isSimulated)
    }

    /** A non-execution event is not silently treated as live enforcement. */
    @Test
    fun `an event with no execution mode is neither simulated nor live`() {
        val alert = com.example.nexa.ui.common.ActivityEntry(
            id = "A2",
            kind = com.example.nexa.ui.common.ActivityKind.AlertRaised,
            title = "Suspicious Port Scan",
            target = "00:11",
            timeAgo = "2m ago",
            status = com.example.nexa.theme.NexaStatus.Critical
        )
        assertFalse(alert.isSimulated)
        assertFalse(alert.isLiveEnforcement)
    }

    @Test
    fun `overview preview marks its simulated enforcement event`() {
        val content = com.example.nexa.ui.overview.OverviewPreview.enforcing()
                as com.example.nexa.ui.overview.OverviewUiState.Content
        val quarantineEvent = content.data.activity.first { it.title.contains("Quarantine applied") }
        assertTrue(quarantineEvent.isSimulated)
    }

    // ============================================================
    // Preview integrity
    // ============================================================

    @Test
    fun `every audit only preview context carries the audit only mode`() {
        AuditOnlyPreview.allAuditOnlyContexts().forEach { context ->
            assertEquals(ExecutionMode.AuditOnly, context.executionMode)
        }
    }

    @Test
    fun `the live comparison scenario is genuinely live`() {
        assertEquals(ExecutionMode.Enforce, AuditOnlyPreview.liveQuarantine().executionMode)
        assertEquals(ActionAvailability.Available, availabilityOf(AuditOnlyPreview.liveQuarantine()))
    }

    @Test
    fun `audit only reverification remains a trust operation`() {
        val context = AuditOnlyPreview.reverification()
        assertEquals(EnforcementAction.RequireReverification, context.action)
        assertFalse(context.action.mutatesEnforcement)
        assertEquals(ActionAvailability.Available, availabilityOf(context))
    }

    @Test
    fun `stored preview handles resolve to their audit only contexts`() {
        EnforcementPreview.reset()
        val handle = AuditOnlyPreview.quarantineSuccessHandle()
        val resolved = EnforcementPreview.resolve(handle)
        assertEquals(ExecutionMode.AuditOnly, resolved?.executionMode)
        assertEquals(EnforcementPreview.Outcome.Success, EnforcementPreview.outcomeFor(handle))
    }
}
