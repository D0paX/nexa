package com.example.nexa.ui.workflow

import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.NexaAvailability
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.devices.Presence
import com.example.nexa.ui.enforcement.ActionAvailability
import com.example.nexa.ui.enforcement.ActionContext
import com.example.nexa.ui.enforcement.AuthorizationState
import com.example.nexa.ui.enforcement.EnforcementAction
import com.example.nexa.ui.enforcement.EnforcementPreview
import com.example.nexa.ui.enforcement.availabilityOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * INTEGRATION — the eligibility surface, crossed.
 *
 * Every earlier test asks one question at a time: does a denied authorization
 * block, does a stale target block. This asks what happens when the conditions
 * meet, because that is where a matrix leaks — two rules each correct alone,
 * and a combination that satisfies neither yet is refused by nobody.
 *
 * The combinations are chosen rather than enumerated. The full product of
 * availability, authorization, trust, mode, freshness, enforcement and breaker
 * is thousands of cases that mostly restate one another. What is covered is
 * every dimension crossed against the others at its unsafe value, plus the
 * specific pairs where one condition might plausibly be read as excusing
 * another.
 *
 * The governing rule throughout: an action is offered only when every
 * dimension is satisfied. One unsafe value refuses, and no quantity of
 * favourable values compensates for it.
 */
class SecurityStateMatrixTest {

    // ============================================================
    // THE DIMENSIONS
    // ============================================================

    private val availabilities = listOf(
        NexaAvailability.Current,
        NexaAvailability.Stale,
        NexaAvailability.Offline,
        NexaAvailability.Degraded,
        NexaAvailability.Unavailable,
        NexaAvailability.Unknown,
        NexaAvailability.Error,
        NexaAvailability.Loading,
        NexaAvailability.Empty
    )

    /** The only availability from which an enforcement change may be made. */
    private val actionableAvailability = NexaAvailability.Current

    private val authorizations = AuthorizationState.entries
    private val modes = ExecutionMode.entries
    private val breakers = CircuitBreakerState.entries

    private val freshnessValues = listOf(
        DataFreshness.Live,
        DataFreshness.Stale("3h ago"),
        DataFreshness.Unknown
    )

    private val mutatingActions = listOf(
        EnforcementAction.QuarantineDevice,
        EnforcementAction.ReleaseQuarantine
    )

    // ============================================================
    // FIXTURES
    // ============================================================

    /**
     * A context eligible in every dimension, so a single altered value is
     * unambiguously the cause of any refusal below.
     */
    private fun eligible(
        action: EnforcementAction = EnforcementAction.QuarantineDevice
    ): ActionContext = EnforcementPreview.context(
        action = action,
        authorization = AuthorizationState.Authorized,
        mode = ExecutionMode.AuditOnly,
        enforcement = when (action) {
            EnforcementAction.ReleaseQuarantine -> DeviceEnforcement.Quarantined
            else -> DeviceEnforcement.Normal
        },
        breaker = CircuitBreakerState.Closed,
        target = EnforcementPreview.target(freshness = DataFreshness.Live)
    ).copy(dataAvailability = NexaAvailability.Current)

    private fun ActionContext.offered() = availabilityOf(this) is ActionAvailability.Available

    private fun ActionContext.refusalReason(): String? =
        (availabilityOf(this) as? ActionAvailability.Disabled)?.reason

    // ============================================================
    // BASELINE — THE MATRIX HAS A TRUE CASE
    // ============================================================

    /**
     * Without this the rest proves nothing: a matrix that refused everything
     * would satisfy every negative assertion in the file.
     */
    @Test
    fun `an eligible context in every dimension is offered`() {
        EnforcementAction.entries.forEach { action ->
            assertTrue("$action was refused with nothing wrong", eligible(action).offered())
        }
    }

    // ============================================================
    // AVAILABILITY CROSSED WITH EVERYTHING
    // ============================================================

    @Test
    fun `only current availability permits an enforcement change`() {
        mutatingActions.forEach { action ->
            availabilities.forEach { availability ->
                val context = eligible(action).copy(dataAvailability = availability)
                if (availability == actionableAvailability) {
                    assertTrue("$action blocked while current", context.offered())
                } else {
                    assertFalse("$action was offered against $availability state", context.offered())
                }
            }
        }
    }

    /**
     * Reverification is exempt from staleness — it asks the identity to prove
     * it is there rather than acting on a past sighting — but not from
     * unreadability. Asking an identity NEXA cannot see to reverify is still
     * acting blind.
     */
    @Test
    fun `reverification tolerates old data but not unreadable data`() {
        availabilities.forEach { availability ->
            val context = eligible(EnforcementAction.RequireReverification)
                .copy(dataAvailability = availability)
            assertEquals(
                "reverification against $availability",
                availability.hasData,
                context.offered()
            )
        }
    }

    @Test
    fun `every refusal names a reason`() {
        availabilities.filter { it != actionableAvailability }.forEach { availability ->
            val reason = eligible().copy(dataAvailability = availability).refusalReason()
            assertTrue("$availability refused without a reason", !reason.isNullOrBlank())
        }
    }

    /** And no refusal describes the target as fine. */
    @Test
    fun `no refusal reassures the operator about the target`() {
        val reassuring = listOf("is fine", "is safe", "no problem", "healthy", "secure")
        availabilities.forEach { availability ->
            val reason = eligible().copy(dataAvailability = availability).refusalReason()
                ?: return@forEach
            reassuring.forEach { phrase ->
                assertFalse("this reason reassures: $reason", reason.lowercase().contains(phrase))
            }
        }
    }

    // ============================================================
    // AUTHORIZATION CROSSED WITH EVERYTHING
    // ============================================================

    @Test
    fun `only a settled authorization permits any action`() {
        EnforcementAction.entries.forEach { action ->
            authorizations.forEach { authorization ->
                val context = eligible(action).copy(authorization = authorization)
                val permitted = authorization == AuthorizationState.Authorized ||
                    authorization == AuthorizationState.ApprovalRequired
                assertEquals("$action with $authorization", permitted, context.offered())
            }
        }
    }

    /**
     * The crossing that matters most. Trust is not authorization, so no trust
     * standing rescues a denied or unknown one.
     */
    @Test
    fun `trust never compensates for authorization`() {
        TrustState.entries.forEach { trust ->
            listOf(AuthorizationState.Denied, AuthorizationState.Unknown).forEach { authorization ->
                val context = eligible().copy(
                    authorization = authorization,
                    target = EnforcementPreview.target(trust = trust, freshness = DataFreshness.Live)
                )
                assertFalse(
                    "trust $trust stood in for $authorization",
                    context.offered()
                )
            }
        }
    }

    /** And the inverse: authorization does not restore trust standing. */
    @Test
    fun `authorization does not resurrect revoked trust`() {
        val context = eligible(EnforcementAction.RequireReverification).copy(
            authorization = AuthorizationState.Authorized,
            target = EnforcementPreview.target(
                trust = TrustState.Revoked,
                freshness = DataFreshness.Live
            )
        )
        assertFalse(
            "a revoked identity was reverifiable because it was authorized",
            context.offered()
        )
    }

    // ============================================================
    // EXECUTION MODE CROSSED WITH EVERYTHING
    // ============================================================

    @Test
    fun `an unknown execution mode refuses regardless of everything else`() {
        EnforcementAction.entries.forEach { action ->
            authorizations.forEach { authorization ->
                breakers.forEach { breaker ->
                    val context = eligible(action).copy(
                        executionMode = ExecutionMode.Unknown,
                        authorization = authorization,
                        circuitBreaker = breaker
                    )
                    assertFalse(
                        "$action ran with an unknown mode ($authorization, $breaker)",
                        context.offered()
                    )
                }
            }
        }
    }

    /**
     * Simulation is not a licence. A mode that will mutate nothing does not
     * lift the target, availability or authorization requirements — the
     * request has to be honest even when nothing will be changed.
     */
    @Test
    fun `audit only does not relax any other requirement`() {
        val unsafe = listOf<Pair<String, ActionContext.() -> ActionContext>>(
            "stale target" to {
                copy(target = target.copy(observationFreshness = DataFreshness.Stale("3h ago")))
            },
            "unreadable state" to { copy(dataAvailability = NexaAvailability.Unavailable) },
            "denied authorization" to { copy(authorization = AuthorizationState.Denied) },
            "unknown authorization" to { copy(authorization = AuthorizationState.Unknown) },
            "open breaker" to { copy(circuitBreaker = CircuitBreakerState.Open) },
            "unknown enforcement" to { copy(currentEnforcement = DeviceEnforcement.Unknown) }
        )
        unsafe.forEach { (name, mutate) ->
            val context = eligible().copy(executionMode = ExecutionMode.AuditOnly).mutate()
            assertFalse("simulation excused $name", context.offered())
        }
    }

    @Test
    fun `live mode is held to exactly the same requirements`() {
        val context = eligible().copy(executionMode = ExecutionMode.Enforce)
        assertTrue(context.offered())
        assertFalse(context.copy(dataAvailability = NexaAvailability.Offline).offered())
        assertFalse(
            context.copy(
                target = context.target.copy(observationFreshness = DataFreshness.Unknown)
            ).offered()
        )
    }

    // ============================================================
    // TARGET FRESHNESS CROSSED WITH ACTION
    // ============================================================

    @Test
    fun `a target that is not currently observed blocks enforcement changes`() {
        mutatingActions.forEach { action ->
            freshnessValues.forEach { freshness ->
                val base = eligible(action)
                val context = base.copy(
                    target = base.target.copy(observationFreshness = freshness)
                )
                assertEquals(
                    "$action with $freshness",
                    freshness == DataFreshness.Live,
                    context.offered()
                )
            }
        }
    }

    @Test
    fun `reverification is not blocked by an old sighting`() {
        freshnessValues.forEach { freshness ->
            val context = eligible(EnforcementAction.RequireReverification).copy(
                target = EnforcementPreview.target(freshness = freshness)
            )
            assertTrue(
                "reverification blocked by $freshness, which it does not depend on",
                context.offered()
            )
        }
    }

    // ============================================================
    // IDENTITY CROSSED WITH ACTION
    // ============================================================

    @Test
    fun `reverification requires an identity to address`() {
        val context = eligible(EnforcementAction.RequireReverification).copy(
            target = EnforcementPreview.target(identityId = null, trust = TrustState.Unverified)
        )
        assertEquals(ActionAvailability.Hidden, availabilityOf(context))
    }

    /**
     * A missing identity does not block an enforcement change. Quarantine acts
     * on a network target, and an unidentified device is exactly the kind an
     * operator most needs to be able to contain.
     */
    @Test
    fun `a device without an identity can still be contained`() {
        val context = eligible().copy(
            target = EnforcementPreview.target(
                identityId = null,
                trust = TrustState.Unverified,
                freshness = DataFreshness.Live
            )
        )
        assertTrue("an unidentified device could not be quarantined", context.offered())
    }

    /**
     * Presence is an observation, not a verdict. What gates an enforcement
     * change is whether the observation is current — which presence it
     * reported is a separate fact, and the matrix does not conflate them.
     */
    @Test
    fun `presence does not by itself decide eligibility`() {
        Presence.entries.forEach { presence ->
            val context = eligible().copy(
                target = EnforcementPreview.target(
                    presence = presence,
                    freshness = DataFreshness.Live
                )
            )
            assertTrue("presence $presence changed eligibility on its own", context.offered())
        }
    }

    // ============================================================
    // BREAKER CROSSED WITH ACTION
    // ============================================================

    @Test
    fun `an open breaker halts enforcement but not trust operations`() {
        breakers.forEach { breaker ->
            mutatingActions.forEach { action ->
                assertEquals(
                    "$action with breaker $breaker",
                    breaker != CircuitBreakerState.Open,
                    eligible(action).copy(circuitBreaker = breaker).offered()
                )
            }
            assertTrue(
                "reverification was blocked by an enforcement breaker ($breaker)",
                eligible(EnforcementAction.RequireReverification)
                    .copy(circuitBreaker = breaker).offered()
            )
        }
    }

    // ============================================================
    // ENFORCEMENT STATE CROSSED WITH ACTION
    // ============================================================

    @Test
    fun `an unknown enforcement state blocks changing it`() {
        mutatingActions.forEach { action ->
            assertFalse(
                "$action proceeded against an unknown enforcement state",
                eligible(action).copy(currentEnforcement = DeviceEnforcement.Unknown).offered()
            )
        }
    }

    @Test
    fun `release is hidden where there is nothing to release`() {
        listOf(DeviceEnforcement.Normal, DeviceEnforcement.Failed, DeviceEnforcement.Paused)
            .forEach { enforcement ->
                assertEquals(
                    "release offered against $enforcement",
                    ActionAvailability.Hidden,
                    availabilityOf(
                        eligible(EnforcementAction.ReleaseQuarantine)
                            .copy(currentEnforcement = enforcement)
                    )
                )
            }
    }

    @Test
    fun `quarantine is refused where it is already in effect`() {
        val context = eligible().copy(currentEnforcement = DeviceEnforcement.Quarantined)
        assertFalse(context.offered())
        assertTrue(context.refusalReason()!!.contains("already", ignoreCase = true))
    }

    // ============================================================
    // COMBINATIONS THAT MIGHT LOOK LIKE EXCUSES
    // ============================================================

    /**
     * Each case pairs one favourable condition with one unsafe one. None of
     * the favourable halves is a reason to proceed.
     */
    @Test
    fun `no favourable condition rescues an unsafe one`() {
        val base = eligible()
        val cases = listOf(
            "authorized but unreadable" to base.copy(
                authorization = AuthorizationState.Authorized,
                dataAvailability = NexaAvailability.Unavailable
            ),
            "trusted but stale" to base.copy(
                target = base.target.copy(
                    trust = TrustState.Trusted,
                    observationFreshness = DataFreshness.Stale("3h ago")
                )
            ),
            "present but authorization unknown" to base.copy(
                authorization = AuthorizationState.Unknown,
                target = base.target.copy(presence = Presence.Present)
            ),
            "simulation but breaker open" to base.copy(
                executionMode = ExecutionMode.AuditOnly,
                circuitBreaker = CircuitBreakerState.Open
            ),
            "data current but observation stale" to base.copy(
                dataAvailability = NexaAvailability.Current,
                target = base.target.copy(observationFreshness = DataFreshness.Stale("3h ago"))
            ),
            "everything but the mode" to base.copy(executionMode = ExecutionMode.Unknown)
        )

        cases.forEach { (name, context) ->
            assertFalse("$name was offered", context.offered())
        }
    }

    /**
     * The property the cases above are examples of: across a broad sweep,
     * eligibility is exactly the conjunction of the dimensions, with no
     * combination behaving as an exception to it.
     */
    @Test
    fun `eligibility equals the conjunction of every dimension`() {
        var checked = 0
        mutatingActions.forEach { action ->
            authorizations.forEach { authorization ->
                modes.forEach { mode ->
                    freshnessValues.forEach { freshness ->
                        breakers.forEach { breaker ->
                            val base = eligible(action)
                            val context = base.copy(
                                authorization = authorization,
                                executionMode = mode,
                                circuitBreaker = breaker,
                                target = base.target.copy(observationFreshness = freshness),
                                dataAvailability = NexaAvailability.Current
                            )
                            val expected =
                                (authorization == AuthorizationState.Authorized ||
                                    authorization == AuthorizationState.ApprovalRequired) &&
                                    mode != ExecutionMode.Unknown &&
                                    freshness == DataFreshness.Live &&
                                    breaker != CircuitBreakerState.Open
                            assertEquals(
                                "$action / $authorization / $mode / $freshness / $breaker",
                                expected,
                                context.offered()
                            )
                            checked++
                        }
                    }
                }
            }
        }
        assertEquals("the sweep did not run", 2 * 4 * 3 * 3 * 3, checked)
    }
}
