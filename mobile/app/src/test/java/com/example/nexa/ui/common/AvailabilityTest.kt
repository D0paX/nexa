package com.example.nexa.ui.common

import com.example.nexa.theme.NexaStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The distinctions the whole checkpoint rests on.
 *
 * The most dangerous failure a security console has is not a crash — it is
 * presenting an absence of knowledge as knowledge of absence. Every test here
 * pins one way those could collapse into each other.
 */
class AvailabilityTest {

    // ============================================================
    // THE VOCABULARY IS DISTINCT
    // ============================================================

    @Test
    fun `every availability state is its own state`() {
        val all = NexaAvailability.entries
        assertEquals(all.size, all.toSet().size)
        assertEquals(all.size, all.map { it.label }.toSet().size)
    }

    /** Nothing there, versus nobody answered. */
    @Test
    fun `empty is not unavailable`() {
        assertNotEquals(NexaAvailability.Empty, NexaAvailability.Unavailable)
        assertNotEquals(NexaAvailability.Empty.label, NexaAvailability.Unavailable.label)
        assertNotEquals(
            availabilityExplanation(NexaAvailability.Empty, "alert state"),
            availabilityExplanation(NexaAvailability.Unavailable, "alert state")
        )
    }

    /** Nothing there, versus we could not ask. */
    @Test
    fun `empty is not offline`() {
        assertNotEquals(NexaAvailability.Empty, NexaAvailability.Offline)
        assertNotEquals(NexaAvailability.Empty.label, NexaAvailability.Offline.label)
    }

    /** Old truth, versus no truth. */
    @Test
    fun `stale is not unknown`() {
        assertNotEquals(NexaAvailability.Stale, NexaAvailability.Unknown)
        assertTrue(NexaAvailability.Stale.hasData)
        assertFalse(NexaAvailability.Unknown.hasData)
    }

    /** Could not answer, versus something broke. */
    @Test
    fun `unavailable is not error`() {
        assertNotEquals(NexaAvailability.Unavailable, NexaAvailability.Error)
        assertNotEquals(NexaAvailability.Unavailable.status, NexaAvailability.Error.status)
    }

    /** Some of it, versus none of it. */
    @Test
    fun `degraded is not empty`() {
        assertNotEquals(NexaAvailability.Degraded, NexaAvailability.Empty)
        assertTrue(NexaAvailability.Degraded.hasData)
        assertFalse(NexaAvailability.Empty.hasData)
    }

    @Test
    fun `loading is not empty`() {
        assertNotEquals(NexaAvailability.Loading, NexaAvailability.Empty)
        assertFalse(NexaAvailability.Loading.hasData)
    }

    // ============================================================
    // NO STATE CLAIMS SECURITY
    // ============================================================

    /**
     * Availability describes what NEXA can see, never whether the network is
     * safe. A green badge on a screen that has stopped knowing anything is
     * precisely the failure this vocabulary exists to prevent.
     */
    @Test
    fun `no availability state is presented as secure`() {
        NexaAvailability.entries.forEach { availability ->
            assertNotEquals("$availability", NexaStatus.Secure, availability.status)
        }
    }

    @Test
    fun `no explanation claims the system is fine`() {
        val forbidden = listOf(
            "secure", "safe", "healthy", "everything is", "all good",
            "no threat", "protected", "nothing is wrong"
        )
        NexaAvailability.entries.forEach { availability ->
            val text = availabilityExplanation(availability, "enforcement state").lowercase()
            forbidden.forEach { claim ->
                assertFalse("$availability says \"$claim\": $text", text.contains(claim))
            }
        }
    }

    /**
     * Not being able to see is never reported as a fact about the network.
     * Each of these sentences says so out loud, because an operator reading
     * quickly will otherwise fill the silence in themselves.
     */
    @Test
    fun `visibility problems say what is not known`() {
        listOf(
            NexaAvailability.Unknown,
            NexaAvailability.Unavailable,
            NexaAvailability.Offline
        ).forEach { availability ->
            val text = availabilityExplanation(availability, "enforcement state").lowercase()
            assertTrue(
                "$availability: $text",
                text.contains("unknown") || text.contains("cannot")
            )
        }
    }

    @Test
    fun `unknown explicitly refuses to imply normality`() {
        val text = availabilityExplanation(NexaAvailability.Unknown, "enforcement state")
        assertTrue(text.contains("not a report that it is normal"))
    }

    @Test
    fun `unavailable explicitly refuses to imply emptiness`() {
        val text = availabilityExplanation(NexaAvailability.Unavailable, "alert state")
        assertTrue(text.contains("not a report that nothing is"))
    }

    @Test
    fun `empty explicitly refuses to imply posture`() {
        val text = availabilityExplanation(NexaAvailability.Empty, "the alert list")
        assertTrue(text.contains("not an assessment of system posture"))
    }

    @Test
    fun `degraded explicitly refuses to imply completeness`() {
        val text = availabilityExplanation(NexaAvailability.Degraded, "the inventory")
        assertTrue(text.contains("may be incomplete"))
        assertTrue(text.contains("do not read it as the whole picture"))
    }

    // ============================================================
    // ACTIONABILITY
    // ============================================================

    /**
     * Only fresh, complete, authoritative state may be acted upon. Everything
     * else is old, partial, absent or unreadable, and a high-impact action
     * taken on any of them is an action taken on a guess.
     */
    @Test
    fun `only current state is actionable`() {
        NexaAvailability.entries.forEach { availability ->
            assertEquals(
                "$availability",
                availability == NexaAvailability.Current,
                availability.isActionable
            )
        }
    }

    @Test
    fun `stale and degraded carry data but are not actionable`() {
        listOf(NexaAvailability.Stale, NexaAvailability.Degraded).forEach {
            assertTrue("$it", it.hasData)
            assertFalse("$it", it.isActionable)
        }
    }

    // ============================================================
    // DERIVATION
    // ============================================================

    /**
     * Freshness describes data that exists. There is deliberately no path
     * from it to empty or unavailable, because conflating "old" with "absent"
     * is one of the mistakes this vocabulary is meant to make impossible.
     */
    @Test
    fun `freshness never derives emptiness or unavailability`() {
        listOf(
            DataFreshness.Live,
            DataFreshness.Stale("earlier"),
            DataFreshness.Unknown
        ).forEach { freshness ->
            val derived = availabilityOf(freshness)
            assertNotEquals(NexaAvailability.Empty, derived)
            assertNotEquals(NexaAvailability.Unavailable, derived)
            assertNotEquals(NexaAvailability.Offline, derived)
        }
    }

    @Test
    fun `freshness maps to the matching availability`() {
        assertEquals(NexaAvailability.Current, availabilityOf(DataFreshness.Live))
        assertEquals(NexaAvailability.Stale, availabilityOf(DataFreshness.Stale("4m")))
        assertEquals(NexaAvailability.Unknown, availabilityOf(DataFreshness.Unknown))
    }

    /**
     * Incompleteness outranks staleness: a partial answer is a worse thing to
     * mistake for the whole than an old one.
     */
    @Test
    fun `degraded outranks stale`() {
        assertEquals(
            NexaAvailability.Degraded,
            contentAvailability(DataFreshness.Stale("4m"), isEmpty = false, degraded = true)
        )
    }

    /**
     * An empty *stale* list is not evidence the set is empty now, so
     * emptiness is only claimed when the data is current.
     */
    @Test
    fun `an empty stale list is reported as stale, not empty`() {
        assertEquals(
            NexaAvailability.Stale,
            contentAvailability(DataFreshness.Stale("4m"), isEmpty = true)
        )
        assertEquals(
            NexaAvailability.Unknown,
            contentAvailability(DataFreshness.Unknown, isEmpty = true)
        )
        assertEquals(
            NexaAvailability.Empty,
            contentAvailability(DataFreshness.Live, isEmpty = true)
        )
    }

    @Test
    fun `current content is current`() {
        assertEquals(
            NexaAvailability.Current,
            contentAvailability(DataFreshness.Live, isEmpty = false)
        )
    }

    // ============================================================
    // COVERAGE
    // ============================================================

    @Test
    fun `partial coverage is never complete and carries a reason`() {
        val partial = NexaCoverage.Partial("the event store returned a partial range")
        assertFalse(partial.isComplete)
        assertTrue(partial.reason.isNotBlank())
        assertTrue(NexaCoverage.Complete.isComplete)
    }

    // ============================================================
    // TRUST IS NOT AVAILABILITY
    // ============================================================

    /**
     * Not looked, versus looked and not proven. Conflating these is how an
     * unread identity gets shown as one that failed verification — or, far
     * worse, how it gets shown as one that passed.
     */
    @Test
    fun `unknown trust is not unverified trust`() {
        assertNotEquals(TrustState.Unknown, TrustState.Unverified)
        assertNotEquals(TrustState.Unknown.label, TrustState.Unverified.label)
        assertNotEquals(TrustState.Unknown.status, TrustState.Unverified.status)
    }

    @Test
    fun `unknown trust is never described as trusted`() {
        val text = TrustState.Unknown.label.lowercase()
        assertFalse(text.contains("trusted"))
        assertFalse(text == "verified")
        assertTrue(text.contains("unknown"))
    }

    @Test
    fun `no trust state is silently good`() {
        listOf(TrustState.Unknown, TrustState.Unverified, TrustState.Revoked, TrustState.Pending)
            .forEach { assertNotEquals("$it", NexaStatus.Secure, it.status) }
        assertEquals(NexaStatus.Verified, TrustState.Trusted.status)
    }

    // ============================================================
    // DELIVERY: UNAVAILABLE IS NOT FAILED
    // ============================================================

    /** Could not read the record, versus the message did not arrive. */
    @Test
    fun `unavailable delivery is not failed delivery`() {
        assertFalse(DeliveryState.Unavailable.isFailure)
        assertTrue(DeliveryState.Failed.isFailure)
        assertNotEquals(DeliveryState.Unavailable.status, DeliveryState.Failed.status)
        assertNotEquals(DeliveryState.Unavailable.label, DeliveryState.Failed.label)
    }

    // ============================================================
    // SCENARIOS
    // ============================================================

    @Test
    fun `no review scenario is active by default`() {
        assertNull(DegradedScenario.active.value)
    }

    /**
     * Only the current and empty scenarios are allowed to describe their data
     * as live. Anything else keeps real content on screen but ages it.
     */
    @Test
    fun `only current and empty scenarios claim live data`() {
        DegradedScenario.Scenario.entries.forEach { scenario ->
            val freshness = DegradedScenario.freshnessFor(scenario)
            val expectsLive = scenario == DegradedScenario.Scenario.Current ||
                scenario == DegradedScenario.Scenario.Empty
            assertEquals("$scenario", expectsLive, freshness is DataFreshness.Live)
        }
    }

    @Test
    fun `every scenario maps to a distinct availability`() {
        val mapped = DegradedScenario.Scenario.entries.map { DegradedScenario.availabilityFor(it) }
        assertEquals(mapped.size, mapped.toSet().size)
    }

    @Test
    fun `offline and stale scenarios keep real data on screen`() {
        listOf(DegradedScenario.Scenario.Offline, DegradedScenario.Scenario.Stale).forEach {
            val freshness = DegradedScenario.freshnessFor(it)
            assertTrue("$it", freshness is DataFreshness.Stale)
            assertTrue("$it", (freshness as DataFreshness.Stale).lastUpdatedLabel.isNotBlank())
        }
    }

    @Test
    fun `unavailable and error scenarios do not pretend to know an age`() {
        listOf(DegradedScenario.Scenario.Unavailable, DegradedScenario.Scenario.Error).forEach {
            assertEquals("$it", DataFreshness.Unknown, DegradedScenario.freshnessFor(it))
        }
    }
}
