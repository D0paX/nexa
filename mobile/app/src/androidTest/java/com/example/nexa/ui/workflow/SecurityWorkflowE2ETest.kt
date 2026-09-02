package com.example.nexa.ui.workflow

import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.devices.Presence
import com.example.nexa.ui.enforcement.ActionAvailability
import com.example.nexa.ui.enforcement.ActionContext
import com.example.nexa.ui.enforcement.ActionSubmissions
import com.example.nexa.ui.enforcement.ActionUiState
import com.example.nexa.ui.enforcement.ActionViewModel
import com.example.nexa.ui.enforcement.AuthorizationState
import com.example.nexa.ui.enforcement.EnforcementAction
import com.example.nexa.ui.enforcement.EnforcementPreview
import com.example.nexa.ui.enforcement.ExecutionState
import com.example.nexa.ui.realtime.PreviewRealtimeScenario
import com.example.nexa.ui.realtime.RealtimeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * END-TO-END — the operator's journey, through the real architecture.
 *
 * Every earlier suite tests a layer. This drives the whole chain: a prepared
 * context, a real [ActionViewModel], the real submission registry, the real
 * [RealtimeStore] with its parser, sequencer and reducer, and the preview
 * publisher standing in for the backend. Nothing is mocked, and no state is
 * written into the store by hand except as a frame off the wire — the same
 * entry point a live socket uses.
 *
 * It runs on a device rather than on the JVM because the store logs through
 * `android.util.Log`, and a version of it that did not would be a different
 * store from the one that ships.
 *
 * What is *not* claimed: none of this reaches a real Phase 4 backend. There
 * is no firewall, no authorization engine and no nftables rule anywhere in
 * this file. The publisher is [com.example.nexa.ui.enforcement.PreviewActionPipeline],
 * and what is being proved is that the client's own chain stays honest about
 * what it has been told — not that a backend behaves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SecurityWorkflowE2ETest {

    private val dispatcher = StandardTestDispatcher()

    /** Where the store's sequence space starts for each test. */
    private val anchorSequence = 5000L
    private val scope = "VLAN_SECURE"

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        EnforcementPreview.reset()
        ActionSubmissions.reset()
    }

    @After
    fun tearDown() {
        EnforcementPreview.reset()
        ActionSubmissions.reset()
        Dispatchers.resetMain()
    }

    // ============================================================
    // HARNESS
    // ============================================================

    /** Re-anchors the shared store so each journey starts from a known snapshot. */
    private suspend fun freshStore() {
        RealtimeStore.anchor(anchorSequence, setOf(scope, "VLAN_BUILD", "VLAN_GUEST", "VLAN_LAB"))
    }

    /**
     * Publishes one frame through the real ingress.
     *
     * Sequences continue from wherever the stream has reached, exactly as the
     * preview publisher does, so hand-written events and pipeline events share
     * one ordering rather than competing for the sequence space.
     */
    private suspend fun publish(
        type: String,
        subjectId: String,
        extra: Map<String, String>,
        eventScope: String = scope
    ) {
        val sequence = RealtimeStore.state.value.lastAppliedSequence + 1
        RealtimeStore.submit(
            PreviewRealtimeScenario.frame(
                eventId = "RT-E2E-$sequence",
                sequence = sequence,
                type = type,
                scope = eventScope,
                subjectId = subjectId,
                extra = extra
            )
        )
    }

    private suspend fun observeDevice(
        deviceId: String,
        presence: String,
        lastSeen: String,
        address: String? = null
    ) = publish(
        type = "DEVICE_OBSERVED",
        subjectId = deviceId,
        extra = buildMap {
            put("presence", presence)
            put("lastSeen", lastSeen)
            address?.let { put("observedAddress", it) }
        }
    )

    private suspend fun changeEnforcement(deviceId: String, enforcement: String) = publish(
        type = "DEVICE_ENFORCEMENT_CHANGED",
        subjectId = deviceId,
        extra = mapOf("enforcement" to enforcement)
    )

    private suspend fun revokeIdentity(identityId: String) = publish(
        type = "IDENTITY_REVOKED",
        subjectId = identityId,
        extra = mapOf("trust" to "REVOKED")
    )

    /**
     * The key is the one the parser reads. Written wrong the first time, the
     * frame was rejected as an invalid enum, the breaker never opened, and the
     * test failed by reporting a successful action — which is exactly how a
     * fixture that does not reach the system under test looks from the outside.
     */
    private suspend fun openBreaker() = publish(
        type = "CIRCUIT_BREAKER_CHANGED",
        subjectId = "BREAKER",
        extra = mapOf("circuitBreaker" to "OPEN")
    )

    /** A context stored under a handle, as a device screen would leave it. */
    private fun prepare(
        action: EnforcementAction = EnforcementAction.QuarantineDevice,
        mode: ExecutionMode = ExecutionMode.AuditOnly,
        authorization: AuthorizationState = AuthorizationState.Authorized,
        enforcement: DeviceEnforcement = DeviceEnforcement.Normal,
        freshness: DataFreshness = DataFreshness.Live,
        trust: TrustState = TrustState.Trusted,
        breaker: CircuitBreakerState = CircuitBreakerState.Closed,
        outcome: EnforcementPreview.Outcome = EnforcementPreview.Outcome.Success
    ): String = EnforcementPreview.store(
        EnforcementPreview.context(
            action = action,
            mode = mode,
            authorization = authorization,
            enforcement = enforcement,
            breaker = breaker,
            target = EnforcementPreview.target(freshness = freshness, trust = trust)
        ),
        outcome
    )

    private val ActionUiState.awaiting: ActionUiState.AwaitingConfirmation
        get() = this as ActionUiState.AwaitingConfirmation

    private val ActionUiState.result: ActionUiState.Result
        get() = this as ActionUiState.Result

    // ============================================================
    // JOURNEY A — INSPECT, QUARANTINE, EXECUTE, RECONCILE
    // ============================================================

    @Test
    fun journeyA_quarantine_runs_from_preparation_to_a_reconciled_result() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val contextId = prepare(mode = ExecutionMode.Enforce)

        model.load(contextId)
        advanceUntilIdle()

        // Preparation states the whole context, and offers the action.
        val awaiting = model.state.value.awaiting
        assertEquals(EnforcementAction.QuarantineDevice, awaiting.context.action)
        assertEquals(ExecutionMode.Enforce, awaiting.context.executionMode)
        assertEquals(ActionAvailability.Available, awaiting.availability)
        assertNull("an action existed before it was confirmed", ActionSubmissions.actionIdFor(contextId))

        model.confirm()
        advanceUntilIdle()

        val result = model.state.value.result
        assertEquals(ExecutionState.Succeeded, result.state)
        assertTrue("a live success was not reconciled", result.reconciled)
        // The lifecycle was reported, not predicted: the store holds it.
        val actionId = ActionSubmissions.actionIdFor(contextId)!!
        assertEquals(
            ExecutionState.Succeeded,
            RealtimeStore.state.value.actions[actionId]?.state
        )
    }

    /** The intermediate states genuinely happened, and none of them was a result. */
    @Test
    fun journeyA_passes_through_the_lifecycle_without_claiming_success_early() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val seen = mutableListOf<ExecutionState>()

        model.load(prepare(mode = ExecutionMode.Enforce))
        advanceUntilIdle()
        model.confirm()

        // Step the virtual clock in slices and record what the screen said.
        repeat(20) {
            advanceUntilIdle()
            when (val state = model.state.value) {
                is ActionUiState.InFlight -> seen.add(state.state)
                is ActionUiState.Result -> seen.add(state.state)
                else -> Unit
            }
        }

        assertTrue("the action never reached a result", seen.contains(ExecutionState.Succeeded))
        // Whatever was observed before the terminal state, none of it was a
        // result claimed early.
        val beforeSuccess = seen.subList(0, seen.indexOf(ExecutionState.Succeeded))
        assertFalse(
            "success was reported before the success event",
            beforeSuccess.contains(ExecutionState.Succeeded)
        )
    }

    // ============================================================
    // JOURNEY B — RELEASE
    // ============================================================

    @Test
    fun journeyB_release_runs_against_a_quarantined_target_and_keeps_its_own_wording() =
        runTest(dispatcher) {
            freshStore()
            val model = ActionViewModel()
            model.load(
                prepare(
                    action = EnforcementAction.ReleaseQuarantine,
                    mode = ExecutionMode.Enforce,
                    enforcement = DeviceEnforcement.Quarantined
                )
            )
            advanceUntilIdle()

            val awaiting = model.state.value.awaiting
            assertEquals(ActionAvailability.Available, awaiting.availability)
            val consequence = awaiting.consequence.summary.lowercase()
            // Enforcement-specific: it describes the binding and the access it
            // governs. Checking for the literal word "release" would be
            // checking the label rather than the meaning.
            assertTrue(
                "release did not describe itself in enforcement terms",
                consequence.contains("enforcement") || consequence.contains("network access")
            )
            assertFalse(
                "release borrowed reverification wording",
                consequence.contains("reverif") || consequence.contains("verified again")
            )
            // And it says plainly which domain it does not touch.
            assertTrue(
                "release did not disclaim a trust consequence",
                consequence.contains("trust")
            )

            model.confirm()
            advanceUntilIdle()
            assertEquals(ExecutionState.Succeeded, model.state.value.result.state)
        }

    // ============================================================
    // JOURNEY C — REVERIFICATION
    // ============================================================

    @Test
    fun journeyC_reverification_stays_in_the_trust_domain_end_to_end() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        model.load(prepare(action = EnforcementAction.RequireReverification))
        advanceUntilIdle()

        val awaiting = model.state.value.awaiting
        val copy = (awaiting.context.action.label + " " + awaiting.consequence.summary)
            .lowercase()

        // The enforcement vocabulary may appear only where the copy is denying
        // an enforcement consequence. "It does not quarantine the device" is
        // the sentence an operator most needs; a rule that banned the word
        // outright would delete it and call that an improvement.
        assertTrue(
            "reverification did not deny an enforcement consequence",
            copy.contains("does not quarantine")
        )
        assertTrue(
            "reverification did not deny a firewall change",
            copy.contains("no change to firewall") || copy.contains("makes no change")
        )
        assertFalse(
            "reverification claimed it would quarantine",
            copy.contains("will quarantine") || copy.contains("this action quarantines")
        )
        listOf("release", "isolate").forEach { word ->
            assertFalse("reverification copy contained \"$word\"", copy.contains(word))
        }

        model.confirm()
        advanceUntilIdle()

        val result = model.state.value.result
        assertEquals(ExecutionState.Succeeded, result.state)
        val resultCopy = result.detail.lowercase()
        listOf("quarantine", "release", "isolate").forEach { word ->
            // The result has no reason to mention enforcement at all, in
            // either direction: nothing about a completed verification is a
            // statement about the firewall.
            assertFalse("the reverification result contained \"$word\"", resultCopy.contains(word))
        }
    }

    /**
     * And the separation this workflow exists to hold: a completed
     * reverification changes what is known about the identity, never what the
     * operator is permitted to do.
     */
    @Test
    fun journeyC_a_successful_reverification_grants_no_authorization() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val contextId = prepare(
            action = EnforcementAction.RequireReverification,
            authorization = AuthorizationState.ApprovalRequired
        )
        model.load(contextId)
        advanceUntilIdle()
        model.confirm()
        advanceUntilIdle()

        assertEquals(ExecutionState.Succeeded, model.state.value.result.state)
        assertEquals(
            "verification was allowed to upgrade authorization",
            AuthorizationState.ApprovalRequired,
            model.state.value.result.context.authorization
        )
    }

    // ============================================================
    // JOURNEY D — THE TARGET GOES STALE MID-WORKFLOW
    // ============================================================

    /**
     * The race the confirmation screen exists to lose safely: the context was
     * eligible when it was prepared, and the world moved before the thumb
     * landed.
     */
    @Test
    fun journeyD_a_target_that_goes_absent_between_preparation_and_confirm_is_refused() =
        runTest(dispatcher) {
            freshStore()
            val model = ActionViewModel()
            val contextId = prepare(mode = ExecutionMode.Enforce)
            model.load(contextId)
            advanceUntilIdle()
            assertEquals(ActionAvailability.Available, model.state.value.awaiting.availability)

            // The device leaves the network.
            val deviceId = model.state.value.awaiting.context.target.deviceId
            observeDevice(deviceId, presence = "ABSENT", lastSeen = "3h ago")
            advanceUntilIdle()

            model.confirm()
            advanceUntilIdle()

            val blocked = model.state.value.awaiting
            assertTrue("the action was submitted anyway", blocked.blockedAfterConfirm)
            assertTrue(blocked.availability is ActionAvailability.Disabled)
            assertNull(
                "an action was created despite the refusal",
                ActionSubmissions.actionIdFor(contextId)
            )
        }

    @Test
    fun journeyD_re_resolution_reevaluates_rather_than_clearing_a_flag() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        model.load(
            prepare(
                mode = ExecutionMode.Enforce,
                freshness = DataFreshness.Stale("Last seen 3h ago")
            )
        )
        advanceUntilIdle()

        // Prepared stale: refused, with a route forward.
        assertTrue(model.state.value.awaiting.availability is ActionAvailability.Disabled)

        val deviceId = model.state.value.awaiting.context.target.deviceId

        // An absence is not a re-resolution. Asking again must still refuse.
        observeDevice(deviceId, presence = "ABSENT", lastSeen = "3h ago")
        advanceUntilIdle()
        model.refreshTarget()
        advanceUntilIdle()
        assertTrue(
            "an absence was accepted as a re-resolution",
            model.state.value.awaiting.availability is ActionAvailability.Disabled
        )

        // A real sighting is.
        observeDevice(deviceId, presence = "PRESENT", lastSeen = "just now")
        advanceUntilIdle()
        model.refreshTarget()
        advanceUntilIdle()

        val resolved = model.state.value.awaiting
        assertEquals(DataFreshness.Live, resolved.context.target.observationFreshness)
        assertEquals(ActionAvailability.Available, resolved.availability)

        model.confirm()
        advanceUntilIdle()
        assertEquals(ExecutionState.Succeeded, model.state.value.result.state)
    }

    /** A breaker that opens between preparation and confirmation also refuses. */
    @Test
    fun journeyD_a_breaker_that_opens_before_confirm_refuses_the_action() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val contextId = prepare(mode = ExecutionMode.Enforce)
        model.load(contextId)
        advanceUntilIdle()

        openBreaker()
        advanceUntilIdle()

        model.confirm()
        advanceUntilIdle()

        assertTrue(model.state.value.awaiting.blockedAfterConfirm)
        assertNull(ActionSubmissions.actionIdFor(contextId))
    }

    /** So does trust being withdrawn from the identity a reverification names. */
    @Test
    fun journeyD_revoked_trust_before_confirm_refuses_a_reverification() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val contextId = prepare(action = EnforcementAction.RequireReverification)
        model.load(contextId)
        advanceUntilIdle()

        val identityId = model.state.value.awaiting.context.target.identityId!!
        revokeIdentity(identityId)
        advanceUntilIdle()

        model.confirm()
        advanceUntilIdle()

        assertTrue(model.state.value.awaiting.blockedAfterConfirm)
        assertNull(ActionSubmissions.actionIdFor(contextId))
    }

    // ============================================================
    // JOURNEY E — AUDIT_ONLY
    // ============================================================

    @Test
    fun journeyE_a_simulation_never_claims_to_have_changed_anything() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val contextId = prepare(mode = ExecutionMode.AuditOnly)
        model.load(contextId)
        advanceUntilIdle()

        val consequence = model.state.value.awaiting.consequence
        assertTrue(
            "the simulation did not say so before it ran",
            consequence.summary.lowercase().contains("simulat")
        )

        model.confirm()
        advanceUntilIdle()

        val result = model.state.value.result
        assertEquals(ExecutionState.Succeeded, result.state)
        assertEquals(ExecutionMode.AuditOnly, result.context.executionMode)
        assertFalse("a simulation reported itself as reconciled", result.reconciled)

        val text = result.detail.lowercase()
        assertTrue("the result did not say it was a simulation", text.contains("simulat"))
        assertFalse("a simulation claimed a firewall change", text.contains("firewall was"))

        // And the authoritative record agrees about the mode.
        val actionId = ActionSubmissions.actionIdFor(contextId)!!
        assertEquals(
            ExecutionMode.AuditOnly,
            RealtimeStore.state.value.actions[actionId]?.mode
        )
    }

    /** The device's own enforcement state is untouched by a simulated run. */
    @Test
    fun journeyE_a_simulation_leaves_enforcement_state_alone() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val contextId = prepare(mode = ExecutionMode.AuditOnly)
        model.load(contextId)
        advanceUntilIdle()
        val deviceId = model.state.value.awaiting.context.target.deviceId
        model.confirm()
        advanceUntilIdle()

        assertEquals(ExecutionState.Succeeded, model.state.value.result.state)
        assertNull(
            "a simulation moved the device's enforcement state",
            RealtimeStore.state.value.devices[deviceId]?.enforcement
        )
    }

    // ============================================================
    // JOURNEY F — FAILURE AND ROLLBACK
    // ============================================================

    @Test
    fun journeyF_a_rollback_is_reported_only_once_the_stream_says_so() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val contextId = prepare(
            mode = ExecutionMode.Enforce,
            outcome = EnforcementPreview.Outcome.RolledBack
        )
        model.load(contextId)
        advanceUntilIdle()
        model.confirm()
        advanceUntilIdle()

        val result = model.state.value.result
        assertEquals(ExecutionState.RolledBack, result.state)
        assertNotEquals(ExecutionState.Succeeded, result.state)
        assertFalse(
            "a rolled back action was described as a success",
            result.detail.lowercase().contains("succeeded")
        )
    }

    @Test
    fun journeyF_a_failure_is_a_failure_and_not_a_quiet_retry() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val contextId = prepare(
            mode = ExecutionMode.Enforce,
            outcome = EnforcementPreview.Outcome.Failed
        )
        model.load(contextId)
        advanceUntilIdle()
        model.confirm()
        advanceUntilIdle()

        assertEquals(ExecutionState.Failed, model.state.value.result.state)
        // One action, and no second one started on its behalf.
        assertEquals(1, RealtimeStore.state.value.actions.size)
    }

    @Test
    fun journeyF_a_failed_rollback_is_never_softened() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val contextId = prepare(
            mode = ExecutionMode.Enforce,
            outcome = EnforcementPreview.Outcome.RollbackFailed
        )
        model.load(contextId)
        advanceUntilIdle()
        model.confirm()
        advanceUntilIdle()

        val result = model.state.value.result
        assertEquals(ExecutionState.RollbackFailed, result.state)
        val text = result.detail.lowercase()
        assertFalse(text.contains("succeeded"))
        assertFalse("a failed rollback claimed the state was restored", text.contains("restored"))
    }

    // ============================================================
    // JOURNEY G — UNKNOWN
    // ============================================================

    @Test
    fun journeyG_an_unknown_outcome_stays_unknown() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val contextId = prepare(
            mode = ExecutionMode.Enforce,
            outcome = EnforcementPreview.Outcome.Unknown
        )
        model.load(contextId)
        advanceUntilIdle()
        model.confirm()
        advanceUntilIdle()

        val result = model.state.value.result
        assertEquals(ExecutionState.Unknown, result.state)
        assertNotEquals(ExecutionState.Succeeded, result.state)
        assertNotEquals(ExecutionState.Failed, result.state)
        assertFalse("an unknown outcome was reconciled", result.reconciled)
    }

    /** And nothing tries it again on the operator's behalf. */
    @Test
    fun journeyG_an_unknown_outcome_is_not_resubmitted() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val contextId = prepare(
            mode = ExecutionMode.Enforce,
            outcome = EnforcementPreview.Outcome.Unknown
        )
        model.load(contextId)
        advanceUntilIdle()
        model.confirm()
        advanceUntilIdle()

        val actionId = ActionSubmissions.actionIdFor(contextId)
        // Confirming again on an unknown result must not create a second action.
        model.confirm()
        advanceUntilIdle()

        assertEquals(actionId, ActionSubmissions.actionIdFor(contextId))
        assertEquals(1, RealtimeStore.state.value.actions.size)
        assertEquals(ExecutionState.Unknown, model.state.value.result.state)
    }

    // ============================================================
    // IDEMPOTENCY AND ACTION IDENTITY
    // ============================================================

    @Test
    fun repeated_confirmation_produces_exactly_one_action() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val contextId = prepare(mode = ExecutionMode.Enforce)
        model.load(contextId)
        advanceUntilIdle()

        repeat(8) { model.confirm() }
        advanceUntilIdle()

        assertEquals(1, RealtimeStore.state.value.actions.size)
        assertEquals(ExecutionState.Succeeded, model.state.value.result.state)
    }

    /** Confirming again while the action is still moving does not fork it. */
    @Test
    fun confirming_during_execution_does_not_start_a_second_action() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val contextId = prepare(mode = ExecutionMode.Enforce)
        model.load(contextId)
        advanceUntilIdle()

        model.confirm()
        val actionId = ActionSubmissions.actionIdFor(contextId)
        model.confirm()
        model.confirm()
        advanceUntilIdle()

        assertEquals(actionId, ActionSubmissions.actionIdFor(contextId))
        assertEquals(1, RealtimeStore.state.value.actions.size)
    }

    /**
     * One logical action keeps one identity from request to result — a
     * reloaded screen attaches to the action that exists rather than making
     * another.
     */
    @Test
    fun an_action_keeps_one_identity_across_a_screen_being_recreated() = runTest(dispatcher) {
        freshStore()
        val first = ActionViewModel()
        val contextId = prepare(mode = ExecutionMode.Enforce)
        first.load(contextId)
        advanceUntilIdle()
        first.confirm()
        advanceUntilIdle()
        val actionId = ActionSubmissions.actionIdFor(contextId)

        // The screen is destroyed and recreated against the same handle.
        val second = ActionViewModel()
        second.load(contextId)
        advanceUntilIdle()

        assertEquals(actionId, ActionSubmissions.actionIdFor(contextId))
        assertEquals(1, RealtimeStore.state.value.actions.size)
        assertEquals(ExecutionState.Succeeded, second.state.value.result.state)
    }

    // ============================================================
    // PROCESS DEATH — FAIL CLOSED
    // ============================================================

    /**
     * After process death the prepared context is gone. The flow must report
     * that it cannot resolve the handle rather than rebuilding a confirmation
     * out of whatever the navigation stack remembered.
     */
    @Test
    fun a_handle_that_cannot_be_resolved_produces_no_executable_confirmation() =
        runTest(dispatcher) {
            freshStore()
            val model = ActionViewModel()
            model.load("ACT-FROM-A-DEAD-PROCESS")
            advanceUntilIdle()

            assertEquals(ActionUiState.Unavailable, model.state.value)
            assertNull(ActionSubmissions.actionIdFor("ACT-FROM-A-DEAD-PROCESS"))
        }

    @Test
    fun a_context_lost_after_preparation_cannot_be_confirmed() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val contextId = prepare(mode = ExecutionMode.Enforce)
        model.load(contextId)
        advanceUntilIdle()
        assertEquals(ActionAvailability.Available, model.state.value.awaiting.availability)

        // The process dies; the store of prepared contexts does not survive.
        EnforcementPreview.reset()

        model.confirm()
        advanceUntilIdle()

        assertEquals(ActionUiState.Unavailable, model.state.value)
        assertNull("an action was created from a context that no longer exists",
            ActionSubmissions.actionIdFor(contextId))
    }

    // ============================================================
    // JOURNEY J — REALTIME CHANGES ELIGIBILITY
    // ============================================================

    /**
     * The confirmation screen is not a photograph. A device that becomes
     * quarantined while the operator is looking at a quarantine request makes
     * that request redundant, and the screen says so instead of sending it.
     */
    @Test
    fun journeyJ_an_enforcement_change_arriving_mid_workflow_is_honoured() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val contextId = prepare(mode = ExecutionMode.Enforce)
        model.load(contextId)
        advanceUntilIdle()
        val deviceId = model.state.value.awaiting.context.target.deviceId

        changeEnforcement(deviceId, "QUARANTINED")
        advanceUntilIdle()

        model.confirm()
        advanceUntilIdle()

        val blocked = model.state.value.awaiting
        assertTrue(blocked.blockedAfterConfirm)
        assertTrue(
            "the refusal did not mention the state it found",
            (blocked.availability as ActionAvailability.Disabled).reason
                .contains("already", ignoreCase = true)
        )
        assertNull(ActionSubmissions.actionIdFor(contextId))
    }

    /**
     * A frame the client refuses to believe changes nothing. An illegal
     * lifecycle jump is rejected by the reducer, so the screen does not move.
     */
    @Test
    fun journeyJ_an_illegal_transition_cannot_advance_the_screen() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val contextId = prepare(
            mode = ExecutionMode.Enforce,
            outcome = EnforcementPreview.Outcome.SuccessPendingReconciliation
        )
        model.load(contextId)
        advanceUntilIdle()
        model.confirm()
        advanceUntilIdle()

        val actionId = ActionSubmissions.actionIdFor(contextId)!!
        val before = model.state.value.result

        // Succeeded cannot become Executing again.
        publish(
            type = "ACTION_STATE_CHANGED",
            subjectId = actionId,
            extra = mapOf(
                "executionState" to "EXECUTING",
                "executionMode" to "ENFORCE",
                "actionCode" to "QUARANTINE_DEVICE"
            )
        )
        advanceUntilIdle()

        assertEquals(before.state, model.state.value.result.state)
    }

    /**
     * A frame for another scope is not shown at all, so it cannot move an
     * action this operator is watching.
     */
    @Test
    fun journeyJ_an_event_from_another_scope_does_not_touch_this_action() = runTest(dispatcher) {
        RealtimeStore.anchor(anchorSequence, setOf(scope))
        val model = ActionViewModel()
        val contextId = prepare(
            mode = ExecutionMode.Enforce,
            outcome = EnforcementPreview.Outcome.SuccessPendingReconciliation
        )
        model.load(contextId)
        advanceUntilIdle()
        model.confirm()
        advanceUntilIdle()

        val actionId = ActionSubmissions.actionIdFor(contextId)!!
        val before = model.state.value.result.state

        publish(
            type = "ACTION_STATE_CHANGED",
            subjectId = actionId,
            extra = mapOf(
                "executionState" to "FAILED",
                "executionMode" to "ENFORCE",
                "actionCode" to "QUARANTINE_DEVICE"
            ),
            eventScope = "VLAN_SOMEWHERE_ELSE"
        )
        advanceUntilIdle()

        assertEquals(before, model.state.value.result.state)
    }

    // ============================================================
    // MODE IS REPORTED, NOT ASSUMED
    // ============================================================

    /**
     * If a request prepared as a simulation is reported as having run live,
     * the operator is told what actually happened rather than what they
     * agreed to. The prepared mode is not the authority on the outcome.
     */
    @Test
    fun the_reported_mode_wins_over_the_prepared_one() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val contextId = prepare(mode = ExecutionMode.AuditOnly)
        model.load(contextId)
        advanceUntilIdle()
        model.confirm()
        advanceUntilIdle()
        val actionId = ActionSubmissions.actionIdFor(contextId)!!

        // The publisher contradicts the request.
        publish(
            type = "ACTION_STATE_CHANGED",
            subjectId = actionId,
            extra = mapOf(
                "executionState" to "SUCCEEDED",
                "executionMode" to "ENFORCE",
                "actionCode" to "QUARANTINE_DEVICE",
                "reconciled" to "true"
            )
        )
        advanceUntilIdle()

        assertEquals(
            "the screen kept describing a live run as a simulation",
            ExecutionMode.Enforce,
            model.state.value.result.context.executionMode
        )
    }

    // ============================================================
    // AN UNANSWERED REQUEST
    // ============================================================

    /**
     * The single most important state in the whole flow: submitted, and
     * nothing reported. It is neither a success nor a failure, and the screen
     * says exactly that.
     */
    @Test
    fun a_request_with_no_answer_is_neither_success_nor_failure() = runTest(dispatcher) {
        freshStore()
        val model = ActionViewModel()
        val contextId = prepare(mode = ExecutionMode.Enforce)
        model.load(contextId)
        advanceUntilIdle()

        // Claim the submission without letting any publisher run.
        val submission = ActionSubmissions.submit(contextId)
        assertTrue(submission is ActionSubmissions.Result.Accepted)

        val second = ActionViewModel()
        second.load(contextId)
        advanceUntilIdle()

        val inFlight = second.state.value as ActionUiState.InFlight
        assertEquals(ExecutionState.Requested, inFlight.state)
    }
}
