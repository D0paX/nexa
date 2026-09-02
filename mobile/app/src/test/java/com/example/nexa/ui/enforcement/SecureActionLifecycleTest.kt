package com.example.nexa.ui.enforcement

import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.NexaAvailability
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.devices.Presence
import com.example.nexa.ui.realtime.ActionOverlay
import com.example.nexa.ui.realtime.DeviceOverlay
import com.example.nexa.ui.realtime.IdentityOverlay
import com.example.nexa.ui.realtime.RealtimeState
import com.example.nexa.ui.realtime.withLiveTarget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The secure action pipeline, from a prepared context to a reported outcome.
 *
 * Two properties dominate everything here. The first is that the UI is never
 * more certain than the system: a request that has been made and not answered
 * is not a success, and an outcome nobody reported is not an outcome. The
 * second is that the decision to submit is re-taken at the moment of
 * submission, not inherited from whatever the screen looked like when it
 * opened.
 */
class SecureActionLifecycleTest {

    @Before
    fun setUp() {
        ActionSubmissions.reset()
        EnforcementPreview.reset()
    }

    @After
    fun tearDown() {
        ActionSubmissions.reset()
        EnforcementPreview.reset()
    }

    // ============================================================
    // IDEMPOTENCY
    // ============================================================

    @Test
    fun `a context can be submitted once`() {
        val first = ActionSubmissions.submit("CTX-1")
        assertTrue(first is ActionSubmissions.Result.Accepted)
        assertEquals(
            (first as ActionSubmissions.Result.Accepted).actionId,
            ActionSubmissions.actionIdFor("CTX-1")
        )
    }

    /**
     * The double tap. The second confirmation does not create a second
     * action — it is handed the first one and joins its lifecycle.
     */
    @Test
    fun `a second submission of the same context yields the same action`() {
        val first = ActionSubmissions.submit("CTX-1") as ActionSubmissions.Result.Accepted
        val second = ActionSubmissions.submit("CTX-1")

        assertTrue(second is ActionSubmissions.Result.AlreadySubmitted)
        assertEquals(first.actionId, (second as ActionSubmissions.Result.AlreadySubmitted).actionId)
    }

    /**
     * Two deliberately prepared actions against the same device are two
     * actions. Idempotency is keyed on the prepared context, not the target,
     * because collapsing them would make a legitimate second quarantine
     * silently do nothing.
     */
    @Test
    fun `different contexts produce different actions`() {
        val a = ActionSubmissions.submit("CTX-1") as ActionSubmissions.Result.Accepted
        val b = ActionSubmissions.submit("CTX-2") as ActionSubmissions.Result.Accepted
        assertNotEquals(a.actionId, b.actionId)
    }

    /**
     * The boundary has to hold when two taps land in the same frame, which is
     * exactly when a disabled button has not repainted yet.
     */
    @Test
    fun `concurrent submissions produce exactly one action`() {
        val threads = 16
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val accepted = java.util.concurrent.atomic.AtomicInteger(0)
        val ids = java.util.Collections.synchronizedSet(mutableSetOf<String>())

        repeat(threads) {
            pool.execute {
                start.await()
                when (val result = ActionSubmissions.submit("CTX-RACE")) {
                    is ActionSubmissions.Result.Accepted -> {
                        accepted.incrementAndGet()
                        ids.add(result.actionId)
                    }
                    is ActionSubmissions.Result.AlreadySubmitted -> ids.add(result.actionId)
                }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        pool.shutdown()

        assertEquals("more than one caller created an action", 1, accepted.get())
        assertEquals("callers disagreed about which action exists", 1, ids.size)
    }

    @Test
    fun `an unsubmitted context has no action`() {
        assertNull(ActionSubmissions.actionIdFor("CTX-NEVER"))
    }

    // ============================================================
    // PROJECTION — WHAT THE OPERATOR IS TOLD
    // ============================================================

    private fun context(
        action: EnforcementAction = EnforcementAction.QuarantineDevice,
        mode: ExecutionMode = ExecutionMode.Enforce
    ) = EnforcementPreview.context(action = action, mode = mode, id = "CTX-P")

    private fun overlay(
        state: ExecutionState,
        mode: ExecutionMode = ExecutionMode.Enforce,
        reconciled: Boolean = false
    ) = ActionOverlay(
        state = state,
        mode = mode,
        reconciled = reconciled,
        actionCode = "QUARANTINE_DEVICE",
        scope = "VLAN_SECURE"
    )

    /**
     * The most important single assertion in this file. A submitted action
     * with nothing reported is a request awaiting an answer — never a
     * success, and never a failure either.
     */
    @Test
    fun `an unreported action is requested, not succeeded`() {
        val projected = projectActionState(context(), overlay = null)
        assertTrue(projected is ActionUiState.InFlight)
        assertEquals(ExecutionState.Requested, (projected as ActionUiState.InFlight).state)
    }

    @Test
    fun `non-terminal states stay in flight`() {
        listOf(
            ExecutionState.Requested,
            ExecutionState.Authorized,
            ExecutionState.Executing,
            ExecutionState.Reconciling,
            ExecutionState.RollbackRequested
        ).forEach { state ->
            val projected = projectActionState(context(), overlay(state))
            assertTrue("$state was reported as a result", projected is ActionUiState.InFlight)
        }
    }

    /** Reconciling is not success. It is the state before an answer exists. */
    @Test
    fun `reconciling is never presented as a result`() {
        val projected = projectActionState(context(), overlay(ExecutionState.Reconciling))
        assertTrue(projected is ActionUiState.InFlight)
        assertNotEquals(ExecutionState.Succeeded, (projected as ActionUiState.InFlight).state)
    }

    @Test
    fun `terminal states become results`() {
        listOf(
            ExecutionState.Succeeded,
            ExecutionState.Failed,
            ExecutionState.Denied,
            ExecutionState.RolledBack,
            ExecutionState.RollbackFailed,
            ExecutionState.Unknown
        ).forEach { state ->
            val projected = projectActionState(context(), overlay(state))
            assertTrue("$state did not become a result", projected is ActionUiState.Result)
            assertEquals(state, (projected as ActionUiState.Result).state)
        }
    }

    /**
     * Reconciliation is whatever the event said. Success does not imply it,
     * and the client does not upgrade it after the fact.
     */
    @Test
    fun `reconciliation is taken from the event and never inferred`() {
        val pending = projectActionState(
            context(),
            overlay(ExecutionState.Succeeded, reconciled = false)
        ) as ActionUiState.Result
        assertFalse("success was treated as reconciliation", pending.reconciled)

        val settled = projectActionState(
            context(),
            overlay(ExecutionState.Succeeded, reconciled = true)
        ) as ActionUiState.Result
        assertTrue(settled.reconciled)
    }

    /**
     * If a request prepared as a simulation is reported as having run live,
     * the operator is told what actually happened — not what was intended.
     */
    @Test
    fun `the reported execution mode wins over the prepared one`() {
        val prepared = context(mode = ExecutionMode.AuditOnly)
        val projected = projectActionState(
            prepared,
            overlay(ExecutionState.Succeeded, mode = ExecutionMode.Enforce, reconciled = true)
        ) as ActionUiState.Result

        assertEquals(ExecutionMode.Enforce, projected.context.executionMode)
    }

    @Test
    fun `an unknown outcome is reported as unknown`() {
        val projected = projectActionState(context(), overlay(ExecutionState.Unknown))
            as ActionUiState.Result
        assertEquals(ExecutionState.Unknown, projected.state)
        assertFalse(projected.reconciled)
        val text = projected.detail.lowercase()
        assertTrue("unknown must say it cannot be determined: $text", text.contains("cannot determine"))
        assertFalse(text.contains("completed."))
    }

    /** Rollback failure keeps its own, worse category. */
    @Test
    fun `rollback failure is not reported as a rollback`() {
        val failed = projectActionState(context(), overlay(ExecutionState.RollbackFailed))
            as ActionUiState.Result
        val rolled = projectActionState(context(), overlay(ExecutionState.RolledBack))
            as ActionUiState.Result

        assertNotEquals(failed.state, rolled.state)
        assertNotEquals(failed.detail, rolled.detail)
        assertTrue(failed.detail.lowercase().contains("rollback failed"))
    }

    // ============================================================
    // RACE SAFETY — THE TARGET MOVES UNDER THE SCREEN
    // ============================================================

    private val liveContext = EnforcementPreview.context(
        action = EnforcementAction.QuarantineDevice,
        authorization = AuthorizationState.Authorized,
        mode = ExecutionMode.Enforce,
        id = "CTX-RACE"
    )

    @Test
    fun `a sound context is available before anything changes`() {
        assertEquals(ActionAvailability.Available, availabilityOf(liveContext))
    }

    /**
     * The device stops being observed while the operator is reading the
     * confirmation. Re-deriving before evaluating is what turns that into a
     * refusal instead of a request sent against a screenshot.
     */
    @Test
    fun `a breaker that opens after preparation blocks the action`() {
        val realtime = RealtimeState(circuitBreaker = CircuitBreakerState.Open)
        val refreshed = liveContext.withLiveTarget(realtime)

        assertEquals(CircuitBreakerState.Open, refreshed.circuitBreaker)
        assertTrue(availabilityOf(refreshed) is ActionAvailability.Disabled)
    }

    @Test
    fun `enforcement changing after preparation is reflected`() {
        val realtime = RealtimeState(
            devices = mapOf(
                liveContext.target.deviceId to DeviceOverlay(
                    enforcement = DeviceEnforcement.Quarantined,
                    scope = liveContext.target.scope
                )
            )
        )
        val refreshed = liveContext.withLiveTarget(realtime)

        assertEquals(DeviceEnforcement.Quarantined, refreshed.currentEnforcement)
        // Somebody else quarantined it first. The request is now redundant and
        // is refused rather than sent.
        assertTrue(availabilityOf(refreshed) is ActionAvailability.Disabled)
    }

    @Test
    fun `trust changing after preparation is reflected`() {
        val realtime = RealtimeState(
            identities = mapOf(
                liveContext.target.identityId!! to IdentityOverlay(trust = TrustState.Revoked, scope = liveContext.target.scope)
            )
        )
        val refreshed = liveContext.withLiveTarget(realtime)
        assertEquals(TrustState.Revoked, refreshed.target.trust)
    }

    /**
     * A live observation makes a stale target current again. This is the
     * re-resolution path having a real effect rather than merely clearing a
     * flag.
     */
    @Test
    fun `an observation refreshes a stale target`() {
        val stale = EnforcementPreview.context(
            authorization = AuthorizationState.Authorized,
            mode = ExecutionMode.Enforce,
            target = EnforcementPreview.target(freshness = DataFreshness.Stale("3h ago")),
            id = "CTX-STALE"
        )
        assertTrue(availabilityOf(stale) is ActionAvailability.Disabled)

        val realtime = RealtimeState(
            devices = mapOf(
                stale.target.deviceId to DeviceOverlay(
                    lastSeenLabel = "just now",
                    scope = stale.target.scope
                )
            )
        )
        val refreshed = stale.withLiveTarget(realtime)

        assertEquals(DataFreshness.Live, refreshed.target.observationFreshness)
        assertEquals(NexaAvailability.Current, refreshed.dataAvailability)
        assertEquals(ActionAvailability.Available, availabilityOf(refreshed))
    }

    /**
     * And the other direction: an event that says nothing about the
     * observation does not quietly make a stale target current.
     */
    @Test
    fun `an unrelated event does not freshen a stale target`() {
        val stale = EnforcementPreview.context(
            authorization = AuthorizationState.Authorized,
            mode = ExecutionMode.Enforce,
            target = EnforcementPreview.target(freshness = DataFreshness.Stale("3h ago")),
            id = "CTX-STALE-2"
        )
        val realtime = RealtimeState(
            devices = mapOf(
                stale.target.deviceId to DeviceOverlay(
                    presence = Presence.Present,
                    scope = stale.target.scope
                )
            )
        )
        val refreshed = stale.withLiveTarget(realtime)

        assertTrue(refreshed.target.observationFreshness is DataFreshness.Stale)
        assertTrue(availabilityOf(refreshed) is ActionAvailability.Disabled)
    }

    /**
     * Authorization is not carried on the stream and is never re-derived from
     * it. Inferring it from trust or presence is the exact mistake the model
     * exists to prevent.
     */
    @Test
    fun `re-deriving never invents authorization or execution mode`() {
        val denied = EnforcementPreview.context(
            authorization = AuthorizationState.Denied,
            mode = ExecutionMode.Unknown,
            id = "CTX-DENIED"
        )
        val realtime = RealtimeState(
            devices = mapOf(
                denied.target.deviceId to DeviceOverlay(
                    presence = Presence.Present,
                    lastSeenLabel = "just now",
                    scope = denied.target.scope
                )
            ),
            identities = mapOf(
                denied.target.identityId!! to IdentityOverlay(trust = TrustState.Trusted, scope = denied.target.scope)
            )
        )
        val refreshed = denied.withLiveTarget(realtime)

        assertEquals(AuthorizationState.Denied, refreshed.authorization)
        assertEquals(ExecutionMode.Unknown, refreshed.executionMode)
        assertTrue(availabilityOf(refreshed) is ActionAvailability.Disabled)
    }

    @Test
    fun `an empty realtime state leaves the prepared context untouched`() {
        assertEquals(liveContext, liveContext.withLiveTarget(RealtimeState()))
    }

    // ============================================================
    // EXECUTION MODE
    // ============================================================

    @Test
    fun `an unknown execution mode blocks the action`() {
        val unknown = EnforcementPreview.context(
            authorization = AuthorizationState.Authorized,
            mode = ExecutionMode.Unknown,
            id = "CTX-MODE"
        )
        val result = availabilityOf(unknown)
        assertTrue(result is ActionAvailability.Disabled)
        assertTrue(
            (result as ActionAvailability.Disabled).reason.lowercase()
                .contains("execution mode is unknown")
        )
    }

    /** Audit-only never claims a firewall change at any stage. */
    @Test
    fun `no audit only projection claims an applied change`() {
        val simulated = context(mode = ExecutionMode.AuditOnly)
        ExecutionState.entries.forEach { state ->
            val projected = projectActionState(
                simulated,
                overlay(state, mode = ExecutionMode.AuditOnly)
            )
            val detail = when (projected) {
                is ActionUiState.InFlight -> projected.detail.orEmpty()
                is ActionUiState.Result -> projected.detail
                else -> ""
            }
            assertTrue(
                "$state does not disclaim a firewall mutation: $detail",
                detail.lowercase().contains("no firewall mutation")
            )
        }
    }
}
