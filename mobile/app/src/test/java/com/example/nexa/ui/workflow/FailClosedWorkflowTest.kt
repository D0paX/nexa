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
import com.example.nexa.ui.realtime.DeviceOverlay
import com.example.nexa.ui.realtime.IdentityOverlay
import com.example.nexa.ui.realtime.RealtimeState
import com.example.nexa.ui.realtime.withLiveTarget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * INTEGRATION — what happens when the answer is not known.
 *
 * Two directions, and both are failures. Failing open means an ambiguous
 * state becomes an executable one: a target NEXA cannot currently see gets
 * quarantined anyway. Failing shut for its own sake means an operator with a
 * genuinely current, genuinely authorized target cannot act, which teaches
 * them to distrust the refusals that matter.
 *
 * The first half of this file pins the fail-closed direction, including the
 * two defects Phase 5.27 found. The second half pins the opposite, so a
 * future tightening cannot quietly block everything and still be green.
 */
class FailClosedWorkflowTest {

    @Before
    fun setUp() {
        EnforcementPreview.reset()
    }

    @After
    fun tearDown() {
        EnforcementPreview.reset()
    }

    private fun ActionContext.offered() = availabilityOf(this) is ActionAvailability.Available

    private fun live(action: EnforcementAction = EnforcementAction.QuarantineDevice) =
        EnforcementPreview.context(
            action = action,
            authorization = AuthorizationState.Authorized,
            mode = ExecutionMode.Enforce,
            enforcement = when (action) {
                EnforcementAction.ReleaseQuarantine -> DeviceEnforcement.Quarantined
                else -> DeviceEnforcement.Normal
            },
            target = EnforcementPreview.target(freshness = DataFreshness.Live)
        )

    // ============================================================
    // DEFECT — AN ABSENCE WAS BEING READ AS A SIGHTING
    // ============================================================

    /**
     * Found in Phase 5.27. A quarantine prepared against a stale target is
     * correctly refused. Then a realtime event arrived saying the device was
     * ABSENT, last seen three hours ago — and the target came back current,
     * the refusal disappeared, and the confirmation screen described the
     * observation as CURRENT.
     *
     * The rule that produced it read: any event carrying a last-seen label
     * means the observation is live. An event arriving proves the stream is
     * alive; it does not prove the device was seen. This one said the
     * opposite of a sighting, and NEXA became more confident because of it.
     */
    @Test
    fun `an absence event does not make a stale target current`() {
        val stale = EnforcementPreview.context(
            authorization = AuthorizationState.Authorized,
            mode = ExecutionMode.Enforce,
            target = EnforcementPreview.target(
                freshness = DataFreshness.Stale("Last seen 3h ago"),
                lastObserved = "3h ago"
            )
        )
        assertFalse("the stale target was not refused to begin with", stale.offered())

        val absence = RealtimeState(
            devices = mapOf(
                stale.target.deviceId to DeviceOverlay(
                    presence = Presence.Absent,
                    lastSeenLabel = "3h ago",
                    scope = stale.target.scope
                )
            )
        )
        val after = stale.withLiveTarget(absence)

        assertNotEquals(
            "an absence was read as a fresh observation",
            DataFreshness.Live,
            after.target.observationFreshness
        )
        assertFalse(
            "an absence event unblocked an enforcement change",
            after.offered()
        )
    }

    /** A device whose presence cannot be established is not a sighting either. */
    @Test
    fun `an unknown presence leaves the target unknown rather than current`() {
        val stale = EnforcementPreview.context(
            authorization = AuthorizationState.Authorized,
            mode = ExecutionMode.Enforce,
            target = EnforcementPreview.target(freshness = DataFreshness.Stale("3h ago"))
        )
        val unknown = RealtimeState(
            devices = mapOf(
                stale.target.deviceId to DeviceOverlay(
                    presence = Presence.Unknown,
                    lastSeenLabel = "unknown",
                    scope = stale.target.scope
                )
            )
        )
        val after = stale.withLiveTarget(unknown)

        assertEquals(DataFreshness.Unknown, after.target.observationFreshness)
        assertEquals(NexaAvailability.Unknown, after.dataAvailability)
        assertFalse(after.offered())
    }

    /**
     * The same downgrade applies to a target that *was* current: a device
     * going absent mid-workflow takes the observation with it.
     */
    @Test
    fun `a current target goes stale when the device leaves`() {
        val current = live()
        assertTrue(current.offered())

        val left = current.withLiveTarget(
            RealtimeState(
                devices = mapOf(
                    current.target.deviceId to DeviceOverlay(
                        presence = Presence.Absent,
                        lastSeenLabel = "just now",
                        scope = current.target.scope
                    )
                )
            )
        )
        assertFalse("a departed device stayed actionable", left.offered())
    }

    // ============================================================
    // DEFECT — AN UNRECORDED OUTCOME PLAYED AS SUCCESS
    // ============================================================

    /**
     * Also found in Phase 5.27. The outcome registry answered a handle it had
     * never seen with [EnforcementPreview.Outcome.Success], so a context the
     * store no longer held — evicted, or never stored — would have played a
     * complete winning lifecycle. Being told nothing is the definition of
     * unknown, not a synonym for it having worked.
     */
    @Test
    fun `an outcome nobody recorded is unknown, not success`() {
        assertEquals(
            EnforcementPreview.Outcome.Unknown,
            EnforcementPreview.outcomeFor("CTX-NEVER-STORED")
        )
    }

    @Test
    fun `an evicted context does not leave a success behind it`() {
        val first = EnforcementPreview.store(EnforcementPreview.context())
        assertEquals(EnforcementPreview.Outcome.Success, EnforcementPreview.outcomeFor(first))

        // Push it past the retention bound.
        repeat(64) { EnforcementPreview.store(EnforcementPreview.context()) }

        assertEquals(
            "an evicted context still answered with a success story",
            EnforcementPreview.Outcome.Unknown,
            EnforcementPreview.outcomeFor(first)
        )
    }

    // ============================================================
    // AMBIGUITY IN GENERAL
    // ============================================================

    /**
     * Every value in the vocabulary that means "NEXA does not know" refuses
     * an enforcement change, one dimension at a time.
     */
    @Test
    fun `every unknown refuses`() {
        val base = live()
        val ambiguous = listOf<Pair<String, ActionContext>>(
            "authorization unknown" to base.copy(authorization = AuthorizationState.Unknown),
            "mode unknown" to base.copy(executionMode = ExecutionMode.Unknown),
            "enforcement unknown" to base.copy(currentEnforcement = DeviceEnforcement.Unknown),
            "availability unknown" to base.copy(dataAvailability = NexaAvailability.Unknown),
            "observation unknown" to base.copy(
                target = base.target.copy(observationFreshness = DataFreshness.Unknown)
            )
        )
        ambiguous.forEach { (name, context) ->
            assertFalse("$name was offered", context.offered())
        }
    }

    /** Trust standing being unknown is not, by itself, one of them. */
    @Test
    fun `unknown trust does not block containing a device`() {
        val context = live().copy(
            target = EnforcementPreview.target(
                trust = TrustState.Unknown,
                freshness = DataFreshness.Live
            )
        )
        assertTrue(
            "a device of unknown trust could not be quarantined, which is backwards",
            context.offered()
        )
    }

    // ============================================================
    // FALSE POSITIVES — THE OTHER FAILURE
    // ============================================================

    /**
     * The re-resolution path must still work. A genuine sighting clears
     * staleness; if it did not, the stale refusal would be a dead end and
     * Journey D would have no way forward.
     */
    @Test
    fun `a real sighting still clears staleness`() {
        val stale = EnforcementPreview.context(
            authorization = AuthorizationState.Authorized,
            mode = ExecutionMode.Enforce,
            target = EnforcementPreview.target(freshness = DataFreshness.Stale("3h ago"))
        )
        assertFalse(stale.offered())

        val seen = stale.withLiveTarget(
            RealtimeState(
                devices = mapOf(
                    stale.target.deviceId to DeviceOverlay(
                        presence = Presence.Present,
                        lastSeenLabel = "just now",
                        scope = stale.target.scope
                    )
                )
            )
        )

        assertEquals(DataFreshness.Live, seen.target.observationFreshness)
        assertEquals(NexaAvailability.Current, seen.dataAvailability)
        assertTrue("a genuine re-observation did not restore the action", seen.offered())
    }

    /**
     * An event about something else does not disturb the target either way —
     * neither freshening it nor tightening it.
     */
    @Test
    fun `an unrelated event leaves the target exactly as prepared`() {
        val context = live()
        val elsewhere = context.withLiveTarget(
            RealtimeState(
                devices = mapOf(
                    "DEV-OTHER" to DeviceOverlay(
                        presence = Presence.Absent,
                        lastSeenLabel = "1h ago",
                        scope = "VLAN_OTHER"
                    )
                )
            )
        )
        assertEquals(context.target.observationFreshness, elsewhere.target.observationFreshness)
        assertTrue(elsewhere.offered())
    }

    /**
     * An enforcement change on its own does not carry an observation, so it
     * must not be treated as one in either direction.
     */
    @Test
    fun `an enforcement event alone does not change the observation`() {
        val context = live()
        val enforced = context.withLiveTarget(
            RealtimeState(
                devices = mapOf(
                    context.target.deviceId to DeviceOverlay(
                        enforcement = DeviceEnforcement.Quarantined,
                        scope = context.target.scope
                    )
                )
            )
        )
        assertEquals(DataFreshness.Live, enforced.target.observationFreshness)
        assertEquals(DeviceEnforcement.Quarantined, enforced.currentEnforcement)
    }

    /**
     * Reverification must not be blocked by an enforcement-only condition.
     * The breaker halts firewall mutation; a trust operation is not one.
     */
    @Test
    fun `reverification survives conditions that only concern enforcement`() {
        val reverify = EnforcementPreview.context(
            action = EnforcementAction.RequireReverification,
            authorization = AuthorizationState.Authorized,
            mode = ExecutionMode.AuditOnly,
            breaker = CircuitBreakerState.Open,
            enforcement = DeviceEnforcement.Unknown,
            target = EnforcementPreview.target(freshness = DataFreshness.Stale("3h ago"))
        )
        assertTrue(
            "a trust operation was blocked by enforcement-only conditions",
            reverify.offered()
        )
    }

    /**
     * A trust change arriving mid-workflow is applied, and it is applied to
     * trust alone — it does not become an observation or an authorization.
     */
    @Test
    fun `a trust event moves trust and nothing else`() {
        val context = live()
        val revoked = context.withLiveTarget(
            RealtimeState(
                identities = mapOf(
                    context.target.identityId!! to IdentityOverlay(
                        trust = TrustState.Revoked,
                        scope = context.target.scope
                    )
                )
            )
        )
        assertEquals(TrustState.Revoked, revoked.target.trust)
        assertEquals(context.authorization, revoked.authorization)
        assertEquals(context.target.observationFreshness, revoked.target.observationFreshness)
    }

    /** A resolved state is not an unavailable one: released means released. */
    @Test
    fun `a released device is not treated as unreadable`() {
        val released = EnforcementPreview.context(
            action = EnforcementAction.QuarantineDevice,
            authorization = AuthorizationState.Authorized,
            mode = ExecutionMode.Enforce,
            enforcement = DeviceEnforcement.Normal,
            target = EnforcementPreview.target(freshness = DataFreshness.Live)
        )
        assertTrue(released.offered())
    }
}
