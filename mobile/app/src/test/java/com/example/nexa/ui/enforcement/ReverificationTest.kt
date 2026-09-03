package com.example.nexa.ui.enforcement

import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.NexaAvailability
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.realtime.ActionOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reverification is a trust operation, and this file exists to keep it one.
 *
 * The failure it guards against has already happened once in this project: a
 * trust operation wearing enforcement's clothes, telling an operator that a
 * device would be isolated when nothing of the sort was going to happen. The
 * separation is not editorial. Reverification asks an identity to prove it is
 * still there. It does not change the firewall, it does not grant permission,
 * and a successful run establishes a verification fact and nothing more.
 *
 *   REVERIFICATION  is not  ENFORCEMENT
 *   VERIFIED        is not  AUTHORIZED
 *   TRUSTED         is not  AUTHORIZED
 */
class ReverificationTest {

    private val enforcementWords = listOf(
        "quarantine", "quarantined", "isolate", "isolated",
        "release", "released", "firewall rule", "nftables",
        "network access", "remediation vlan", "drop"
    )

    private fun reverification(
        trust: TrustState = TrustState.Trusted,
        identityId: String? = "TID-88F1",
        mode: ExecutionMode = ExecutionMode.Enforce,
        authorization: AuthorizationState = AuthorizationState.Authorized,
        breaker: CircuitBreakerState = CircuitBreakerState.Closed,
        enforcement: DeviceEnforcement = DeviceEnforcement.Normal,
        freshness: DataFreshness = DataFreshness.Live
    ) = EnforcementPreview.context(
        action = EnforcementAction.RequireReverification,
        target = EnforcementPreview.target(
            identityId = identityId,
            trust = trust,
            freshness = freshness
        ),
        authorization = authorization,
        mode = mode,
        enforcement = enforcement,
        breaker = breaker,
        id = "CTX-REV"
    )

    // ============================================================
    // IT IS NOT AN ENFORCEMENT ACTION
    // ============================================================

    @Test
    fun `reverification does not mutate enforcement`() {
        assertFalse(EnforcementAction.RequireReverification.mutatesEnforcement)
        assertTrue(EnforcementAction.QuarantineDevice.mutatesEnforcement)
        assertTrue(EnforcementAction.ReleaseQuarantine.mutatesEnforcement)
    }

    /**
     * The circuit breaker halts enforcement. A trust operation is not an
     * enforcement change, so it is deliberately unaffected — and this has to
     * keep being deliberate rather than becoming an oversight.
     */
    @Test
    fun `an open breaker does not block reverification`() {
        assertEquals(
            ActionAvailability.Available,
            availabilityOf(reverification(breaker = CircuitBreakerState.Open))
        )
        // While it does block the enforcement actions beside it.
        val quarantine = EnforcementPreview.context(
            authorization = AuthorizationState.Authorized,
            breaker = CircuitBreakerState.Open,
            id = "CTX-Q"
        )
        assertTrue(availabilityOf(quarantine) is ActionAvailability.Disabled)
    }

    /**
     * An unknown enforcement state makes a firewall change unpredictable. It
     * says nothing about whether an identity can be asked to verify again.
     */
    @Test
    fun `unknown enforcement state does not block reverification`() {
        assertEquals(
            ActionAvailability.Available,
            availabilityOf(reverification(enforcement = DeviceEnforcement.Unknown))
        )
    }

    /**
     * A stale observation is a problem for acting *on* a target. Reverification
     * asks the identity itself, so an old sighting does not stop it — the same
     * reasoning the enforcement rules already encode.
     */
    @Test
    fun `a stale observation does not block reverification`() {
        assertEquals(
            ActionAvailability.Available,
            availabilityOf(reverification(freshness = DataFreshness.Stale("3h ago")))
        )
    }

    // ============================================================
    // BUT IT IS NOT EXEMPT FROM SECURITY EITHER
    // ============================================================

    @Test
    fun `reverification still requires an identity to reverify`() {
        assertEquals(
            ActionAvailability.Hidden,
            availabilityOf(reverification(identityId = null, trust = TrustState.Unverified))
        )
    }

    @Test
    fun `reverification does not restore revoked trust`() {
        val result = availabilityOf(reverification(trust = TrustState.Revoked))
        assertTrue(result is ActionAvailability.Disabled)
        assertTrue(
            (result as ActionAvailability.Disabled).reason
                .contains("does not restore withdrawn trust")
        )
    }

    @Test
    fun `reverification obeys authorization`() {
        listOf(AuthorizationState.Denied, AuthorizationState.Unknown).forEach { standing ->
            assertTrue(
                "$standing did not block reverification",
                availabilityOf(reverification(authorization = standing)) is ActionAvailability.Disabled
            )
        }
    }

    @Test
    fun `reverification is blocked when the state behind it cannot be read`() {
        listOf(
            NexaAvailability.Offline,
            NexaAvailability.Unavailable,
            NexaAvailability.Unknown,
            NexaAvailability.Error
        ).forEach { availability ->
            val context = reverification().copy(dataAvailability = availability)
            assertTrue(
                "$availability did not block reverification",
                availabilityOf(context) is ActionAvailability.Disabled
            )
        }
    }

    @Test
    fun `an unknown execution mode blocks reverification`() {
        assertTrue(
            availabilityOf(reverification(mode = ExecutionMode.Unknown)) is ActionAvailability.Disabled
        )
    }

    // ============================================================
    // ITS WORDING IS ITS OWN
    // ============================================================

    /**
     * Flags enforcement language used as a promise.
     *
     * Saying "it does not quarantine the device" is not a promise to
     * quarantine anything — it is the disclaimer this file wants. So a word is
     * only a finding when the text does not also disclaim it, which is the
     * same shape as the AUDIT_ONLY copy guard used elsewhere.
     */
    private fun assertNoEnforcementPromise(text: String) {
        val lower = text.lowercase()
        val disclaims = lower.contains("does not") || lower.contains("no change") ||
            lower.contains("unaffected") || lower.contains("unchanged") ||
            lower.contains("no firewall mutation") || lower.contains("is not")
        enforcementWords.forEach { word ->
            if (lower.contains(word)) {
                assertTrue(
                    "reverification copy uses \"$word\" without disclaiming it: $text",
                    disclaims
                )
            }
        }
    }

    @Test
    fun `the consequence describes trust, never enforcement`() {
        ExecutionMode.entries.forEach { mode ->
            val consequence = consequenceOf(EnforcementAction.RequireReverification, mode)
            assertNoEnforcementPromise(consequence.summary)
            assertFalse("reverification is not destructive", consequence.destructive)
        }
    }

    @Test
    fun `every reverification explanation avoids enforcement promises`() {
        ExecutionMode.entries.forEach { mode ->
            ExecutionState.entries.forEach { state ->
                assertNoEnforcementPromise(reverificationExplanation(state, mode))
            }
        }
    }

    /**
     * The wording is genuinely different, not the enforcement text with a word
     * swapped. If these ever became equal, a trust result would be describing
     * a firewall operation again.
     */
    @Test
    fun `reverification wording differs from enforcement wording`() {
        ExecutionMode.entries.forEach { mode ->
            ExecutionState.entries.forEach { state ->
                assertNotEquals(
                    "$state/$mode reuses the enforcement explanation",
                    resultExplanation(state, mode),
                    resultExplanation(state, mode, EnforcementAction.RequireReverification)
                )
            }
        }
    }

    @Test
    fun `the action-aware overload routes reverification to its own wording`() {
        ExecutionState.entries.forEach { state ->
            ExecutionMode.entries.forEach { mode ->
                assertEquals(
                    reverificationExplanation(state, mode),
                    resultExplanation(state, mode, EnforcementAction.RequireReverification)
                )
            }
        }
    }

    /** Enforcement actions keep the enforcement wording they already had. */
    @Test
    fun `enforcement actions are unaffected by the new overload`() {
        listOf(EnforcementAction.QuarantineDevice, EnforcementAction.ReleaseQuarantine).forEach { action ->
            ExecutionState.entries.forEach { state ->
                ExecutionMode.entries.forEach { mode ->
                    assertEquals(
                        resultExplanation(state, mode),
                        resultExplanation(state, mode, action)
                    )
                }
            }
        }
    }

    // ============================================================
    // IT NEVER OVERCLAIMS
    // ============================================================

    /**
     * Success establishes a verification fact. It does not authorize anything,
     * and it does not make a device safe.
     */
    @Test
    fun `successful reverification never claims authorization or safety`() {
        val text = reverificationExplanation(ExecutionState.Succeeded, ExecutionMode.Enforce).lowercase()
        assertFalse(text.contains("authorized"))
        assertFalse(text.contains("safe"))
        assertFalse(text.contains("secure"))
        assertTrue(text.contains("does not grant authorization"))
    }

    /**
     * Failure is not revocation. An identity that could not be re-checked
     * keeps the standing it had; concluding otherwise would let a transient
     * failure read as a security event that never occurred.
     */
    @Test
    fun `failed reverification is not revocation`() {
        val text = reverificationExplanation(ExecutionState.Failed, ExecutionMode.Enforce).lowercase()
        assertFalse(text.contains("revoked"))
        assertTrue(text.contains("not a revocation"))
        assertTrue(text.contains("unchanged"))
    }

    @Test
    fun `an unknown reverification outcome is neither confirmation nor withdrawal`() {
        val text = reverificationExplanation(ExecutionState.Unknown, ExecutionMode.Enforce).lowercase()
        assertTrue(text.contains("cannot determine"))
        assertTrue(text.contains("neither confirmed nor withdrawn"))
    }

    /**
     * A simulated reverification checked nothing. Saying it "verified" would
     * be the trust-domain version of claiming a firewall change that never
     * happened.
     */
    @Test
    fun `a simulated reverification says the identity was not checked`() {
        val text = reverificationExplanation(ExecutionState.Succeeded, ExecutionMode.AuditOnly).lowercase()
        assertTrue(text.contains("not actually re-checked"))
        assertTrue(text.contains("no trust standing changed"))
        assertTrue(text.contains("no firewall mutation"))
    }

    @Test
    fun `every simulated reverification stage disclaims a firewall mutation`() {
        ExecutionState.entries.forEach { state ->
            val text = reverificationExplanation(state, ExecutionMode.AuditOnly).lowercase()
            assertTrue(
                "$state does not disclaim a firewall mutation",
                text.contains("no firewall mutation")
            )
        }
    }

    /** Every live stage states that network enforcement is untouched. */
    @Test
    fun `every live reverification stage states enforcement is unaffected`() {
        ExecutionState.entries.forEach { state ->
            val text = reverificationExplanation(state, ExecutionMode.Enforce).lowercase()
            assertTrue(
                "$state does not say enforcement is unaffected: $text",
                text.contains("enforcement") || text.contains("unchanged")
            )
        }
    }

    // ============================================================
    // ITS RESULTS ARE DISTINGUISHABLE
    // ============================================================

    private fun projected(state: ExecutionState, mode: ExecutionMode = ExecutionMode.Enforce) =
        projectActionState(
            reverification(mode = mode),
            ActionOverlay(
                state = state,
                mode = mode,
                reconciled = false,
                actionCode = "REQUIRE_REVERIFICATION",
                scope = "VLAN_SECURE"
            )
        )

    @Test
    fun `succeeded, failed and unknown reverification are three distinct results`() {
        val outcomes = listOf(
            ExecutionState.Succeeded,
            ExecutionState.Failed,
            ExecutionState.Unknown
        ).map { (projected(it) as ActionUiState.Result).detail }

        assertEquals(outcomes.size, outcomes.toSet().size)
    }

    @Test
    fun `a reverification result never borrows enforcement language`() {
        ExecutionState.entries.forEach { state ->
            ExecutionMode.entries.forEach { mode ->
                val text = when (val p = projected(state, mode)) {
                    is ActionUiState.Result -> p.detail
                    is ActionUiState.InFlight -> p.detail.orEmpty()
                    else -> ""
                }
                assertNoEnforcementPromise(text)
            }
        }
    }

    /**
     * The confirmation control names the operation. "CONFIRM" alone on a
     * screen that could equally be quarantining something is how the wrong
     * action gets taken.
     */
    @Test
    fun `the confirmation label names reverification`() {
        assertEquals(
            "SIMULATE REVERIFICATION",
            confirmLabel(EnforcementAction.RequireReverification, ExecutionMode.AuditOnly)
        )
        assertEquals(
            "CONFIRM REQUIRE_REVERIFICATION",
            confirmLabel(EnforcementAction.RequireReverification, ExecutionMode.Enforce)
        )
        assertNoEnforcementPromise(
            confirmLabel(EnforcementAction.RequireReverification, ExecutionMode.Enforce)
        )
    }
}
