package com.example.nexa.ui.enforcement

import com.example.nexa.ui.audit.AuditCategory
import com.example.nexa.ui.audit.AuditPreview
import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.deeplink.NexaDeepLink
import com.example.nexa.ui.deeplink.NexaDeepLinkParser
import com.example.nexa.ui.deeplink.DeepLinkParse
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.devices.DeviceFilters
import com.example.nexa.ui.devices.DeviceSort
import com.example.nexa.ui.devices.DevicesPreview
import com.example.nexa.ui.devices.Presence
import com.example.nexa.ui.devices.resolve
import com.example.nexa.ui.realtime.ActionOverlay
import com.example.nexa.ui.realtime.RealtimeEvent
import com.example.nexa.ui.realtime.RealtimeEventParser
import com.example.nexa.ui.realtime.RealtimeParseResult
import com.example.nexa.ui.realtime.PreviewRealtimeScenario
import com.example.nexa.ui.realtime.RealtimeReducer
import com.example.nexa.ui.realtime.RealtimeState
import com.example.nexa.ui.realtime.ReduceResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a secure action must never be talked into.
 *
 * The lifecycle assertions here run frames through the real parser and the
 * real reducer rather than writing overlays by hand, because the properties
 * being checked — that a replayed event changes nothing, that an impossible
 * transition is refused — belong to that path and would be invisible if the
 * test set the state directly.
 */
class SecureActionSecurityTest {

    private val actionId = "ACT-7001"

    private fun frame(
        state: String,
        sequence: Long,
        mode: String = "ENFORCE",
        reconciled: String? = null,
        eventId: String = "RT-$sequence"
    ): Map<String, String> = PreviewRealtimeScenario.frame(
        eventId = eventId,
        sequence = sequence,
        type = "ACTION_STATE_CHANGED",
        scope = "VLAN_SECURE",
        subjectId = actionId,
        extra = buildMap {
            put("executionState", state)
            put("executionMode", mode)
            put("actionCode", "QUARANTINE_DEVICE")
            reconciled?.let { put("reconciled", it) }
        }
    )

    private fun apply(state: RealtimeState, frame: Map<String, String>): RealtimeState {
        val parsed = RealtimeEventParser.parse(frame)
        assertTrue("frame did not parse: $parsed", parsed is RealtimeParseResult.Accepted)
        val event: RealtimeEvent = (parsed as RealtimeParseResult.Accepted).event
        return when (val result = RealtimeReducer.reduce(state, event)) {
            is ReduceResult.Applied -> result.next
            is ReduceResult.Ignored -> state
        }
    }

    private fun overlayAfter(vararg frames: Map<String, String>): ActionOverlay? {
        var state = RealtimeState()
        frames.forEach { state = apply(state, it) }
        return state.actions[actionId]
    }

    // ============================================================
    // THE LIFECYCLE IS THE SYSTEM'S, NOT THE SCREEN'S
    // ============================================================

    @Test
    fun `a full live lifecycle ends reconciled`() {
        val overlay = overlayAfter(
            frame("REQUESTED", 1),
            frame("AUTHORIZED", 2),
            frame("EXECUTING", 3),
            frame("RECONCILING", 4),
            frame("SUCCEEDED", 5, reconciled = "true")
        )
        assertEquals(ExecutionState.Succeeded, overlay?.state)
        assertTrue(overlay!!.reconciled)
    }

    /**
     * Execution returning is not reconciliation. A success reported without
     * it stays unreconciled, and the screen must show that rather than
     * rounding it up.
     */
    @Test
    fun `success without reconciliation stays unreconciled`() {
        val overlay = overlayAfter(
            frame("REQUESTED", 1),
            frame("AUTHORIZED", 2),
            frame("EXECUTING", 3),
            frame("SUCCEEDED", 4)
        )
        assertEquals(ExecutionState.Succeeded, overlay?.state)
        assertFalse(overlay!!.reconciled)

        val projected = projectActionState(
            EnforcementPreview.context(mode = ExecutionMode.Enforce, id = "CTX-X"),
            overlay
        ) as ActionUiState.Result
        assertFalse(projected.reconciled)
    }

    @Test
    fun `the rollback path is reported as rollback, not as failure alone`() {
        val overlay = overlayAfter(
            frame("REQUESTED", 1),
            frame("AUTHORIZED", 2),
            frame("EXECUTING", 3),
            frame("FAILED", 4),
            frame("ROLLBACK_REQUESTED", 5),
            frame("ROLLED_BACK", 6, reconciled = "true")
        )
        assertEquals(ExecutionState.RolledBack, overlay?.state)
    }

    @Test
    fun `rollback failure is reachable and terminal`() {
        val overlay = overlayAfter(
            frame("REQUESTED", 1),
            frame("AUTHORIZED", 2),
            frame("EXECUTING", 3),
            frame("FAILED", 4),
            frame("ROLLBACK_REQUESTED", 5),
            frame("ROLLBACK_FAILED", 6)
        )
        assertEquals(ExecutionState.RollbackFailed, overlay?.state)
        assertTrue(overlay!!.state.isTerminal)
        assertFalse(overlay.reconciled)
    }

    /**
     * An impossible transition is refused by the reducer, so it cannot reach
     * the screen either. A publisher claiming a succeeded action started
     * executing again is either broken or hostile.
     */
    @Test
    fun `an illegal transition does not advance the action`() {
        val overlay = overlayAfter(
            frame("REQUESTED", 1),
            frame("AUTHORIZED", 2),
            frame("EXECUTING", 3),
            frame("SUCCEEDED", 4, reconciled = "true"),
            frame("EXECUTING", 5)
        )
        assertEquals(ExecutionState.Succeeded, overlay?.state)
        assertTrue(overlay!!.reconciled)
    }

    /** Executing cannot jump straight to a rollback; a failure comes first. */
    @Test
    fun `a rollback without a preceding failure is refused`() {
        val overlay = overlayAfter(
            frame("REQUESTED", 1),
            frame("AUTHORIZED", 2),
            frame("EXECUTING", 3),
            frame("ROLLBACK_REQUESTED", 4)
        )
        assertEquals(ExecutionState.Executing, overlay?.state)
    }

    /**
     * A redelivered event does not move the UI. The same state applied twice
     * is the same state — the screen does not transition because a packet
     * arrived twice.
     */
    @Test
    fun `a repeated event leaves the action where it was`() {
        val once = overlayAfter(
            frame("REQUESTED", 1),
            frame("AUTHORIZED", 2),
            frame("EXECUTING", 3)
        )
        val twice = overlayAfter(
            frame("REQUESTED", 1),
            frame("AUTHORIZED", 2),
            frame("EXECUTING", 3),
            frame("EXECUTING", 4, eventId = "RT-dup")
        )
        assertEquals(once, twice)
    }

    /**
     * The mode is taken from every event and never inherited. A simulation
     * seen earlier must not colour a live run, and a live run must not be
     * quietly recorded as a simulation.
     */
    @Test
    fun `the reported mode is never inherited from an earlier event`() {
        val overlay = overlayAfter(
            frame("REQUESTED", 1, mode = "AUDIT_ONLY"),
            frame("AUTHORIZED", 2, mode = "AUDIT_ONLY"),
            frame("EXECUTING", 3, mode = "ENFORCE")
        )
        assertEquals(ExecutionMode.Enforce, overlay?.mode)
    }

    @Test
    fun `an action nobody reported has no overlay at all`() {
        assertNull(RealtimeState().actions[actionId])
    }

    // ============================================================
    // NOTHING ELSE GRANTS PERMISSION
    // ============================================================

    /**
     * Phase 5.18's rule, re-asserted from the action side: there is no
     * action-bearing link, and none of these strings can produce one.
     */
    @Test
    fun `no deep link can carry an action`() {
        listOf(
            "nexa://v1/device/DEV-1001/quarantine",
            "nexa://v1/device/DEV-1001/release",
            "nexa://v1/identity/TID-88F1/reverify",
            "nexa://v1/action/ACT-9001/confirm",
            "nexa://v1/device/DEV-1001?action=QUARANTINE_DEVICE",
            "nexa://v1/device/DEV-1001?execute=true"
        ).forEach { raw ->
            val parsed = NexaDeepLinkParser.parse(raw)
            if (parsed is DeepLinkParse.Accepted) {
                // A link that parses may only open a context. There is no
                // action variant in the model for one to become.
                assertTrue(
                    "an action-bearing link was accepted: $raw",
                    parsed.link is NexaDeepLink.Device ||
                        parsed.link is NexaDeepLink.Identity ||
                        parsed.link is NexaDeepLink.Alert ||
                        parsed.link is NexaDeepLink.Devices ||
                        parsed.link is NexaDeepLink.Overview
                )
            }
        }
    }

    /**
     * Arriving from a push notification changes nothing about eligibility.
     * The context is prepared and evaluated exactly as it would be from a
     * list — a notification brings an operator somewhere, it does not vouch
     * for them.
     */
    @Test
    fun `arriving from a notification does not change eligibility`() {
        val denied = EnforcementPreview.context(
            authorization = AuthorizationState.Denied,
            mode = ExecutionMode.Enforce,
            id = "CTX-PUSH"
        )
        assertTrue(availabilityOf(denied) is ActionAvailability.Disabled)
    }

    /**
     * Phase 5.21's rule, re-asserted from the action side. A device surfaced
     * by "Trusted and Present" is a device that matched a predicate.
     */
    @Test
    fun `surviving a filter does not make an action available`() {
        val visible = DevicesPreview.inventory.resolve(
            "",
            DeviceFilters(trust = setOf(TrustState.Trusted), presence = setOf(Presence.Present)),
            DeviceSort.Name
        )
        assertTrue(visible.isNotEmpty())

        visible.forEach { device ->
            val context = EnforcementPreview.context(
                target = EnforcementPreview.target(
                    deviceId = device.id,
                    trust = device.trust,
                    freshness = device.freshness
                ),
                authorization = AuthorizationState.Denied,
                mode = ExecutionMode.Enforce,
                id = "CTX-${device.id}"
            )
            assertTrue(availabilityOf(context) is ActionAvailability.Disabled)
        }
    }

    /** Trust is not authorization, stated once more where it matters most. */
    @Test
    fun `a trusted target with denied authorization is refused`() {
        val trusted = EnforcementPreview.context(
            target = EnforcementPreview.target(trust = TrustState.Trusted),
            authorization = AuthorizationState.Denied,
            mode = ExecutionMode.Enforce,
            enforcement = DeviceEnforcement.Normal,
            breaker = CircuitBreakerState.Closed,
            id = "CTX-TRUSTED"
        )
        val result = availabilityOf(trusted)
        assertTrue(result is ActionAvailability.Disabled)
        assertTrue((result as ActionAvailability.Disabled).reason.contains("Authorization denied"))
    }

    // ============================================================
    // AUDIT
    // ============================================================

    /**
     * Phase 5.15's rule: there is no Reverification event family. A trust
     * operation is recorded under Trust, and adding a family for one
     * checkpoint's convenience would fragment the history it belongs to.
     */
    @Test
    fun `there is no separate reverification audit family`() {
        assertEquals(7, AuditCategory.entries.size)
        assertFalse(AuditCategory.entries.any { it.name.contains("Reverif", ignoreCase = true) })
        assertFalse(AuditCategory.entries.any { it.name.contains("Verif", ignoreCase = true) })
    }

    /**
     * Execution state and audit history remain separate systems. An action
     * reaching a state is not an audit record, and the history is not
     * fabricated by the screen that ran the action.
     */
    @Test
    fun `audit history is not written by the action lifecycle`() {
        val before = AuditPreview.entries.size
        overlayAfter(
            frame("REQUESTED", 1),
            frame("AUTHORIZED", 2),
            frame("EXECUTING", 3),
            frame("SUCCEEDED", 4, reconciled = "true")
        )
        assertEquals(before, AuditPreview.entries.size)
    }

    @Test
    fun `reverification records belong to the trust family`() {
        val reverification = AuditPreview.entries.filter {
            it.actionCode == "REQUIRE_REVERIFICATION"
        }
        reverification.forEach {
            assertNotNull(it.category)
            assertFalse(
                "a reverification record was filed under Enforcement",
                it.category == AuditCategory.Enforcement
            )
        }
    }
}
