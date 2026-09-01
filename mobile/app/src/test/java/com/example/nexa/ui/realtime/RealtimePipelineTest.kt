package com.example.nexa.ui.realtime

import com.example.nexa.ui.alerts.AlertLifecycle
import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.DeliveryState
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.enforcement.ExecutionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validation, sequencing and reduction — the three stages between a frame
 * arriving and a screen changing.
 *
 * All pure. None of it needs a device, a socket or a composition.
 */
class RealtimePipelineTest {

    private fun accept(frame: Map<String, String>): RealtimeEvent {
        val result = RealtimeEventParser.parse(frame)
        assertTrue("expected acceptance, got $result", result is RealtimeParseResult.Accepted)
        return (result as RealtimeParseResult.Accepted).event
    }

    private fun reject(frame: Map<String, String>): RealtimeParseResult.Rejected {
        val result = RealtimeEventParser.parse(frame)
        assertTrue("expected rejection, got $result", result is RealtimeParseResult.Rejected)
        return result as RealtimeParseResult.Rejected
    }

    private fun alertFrame(
        id: String = "RT-1",
        sequence: Long = 1,
        lifecycle: String = "NEW",
        scope: String = "VLAN_SECURE",
        subject: String = "ALRT-1092"
    ) = PreviewRealtimeScenario.frame(
        eventId = id, sequence = sequence, type = "ALERT_RAISED",
        scope = scope, subjectId = subject, extra = mapOf("lifecycle" to lifecycle)
    )

    private fun actionFrame(
        id: String,
        sequence: Long,
        state: String,
        mode: String = "ENFORCE",
        reconciled: Boolean = false,
        subject: String = "ACT-1"
    ) = PreviewRealtimeScenario.frame(
        eventId = id, sequence = sequence, type = "ACTION_STATE_CHANGED",
        scope = "VLAN_SECURE", subjectId = subject,
        extra = mapOf(
            "executionState" to state,
            "executionMode" to mode,
            "reconciled" to reconciled.toString(),
            "actionCode" to "QUARANTINE_DEVICE"
        )
    )

    // ============================================================
    // VALIDATION
    // ============================================================

    @Test
    fun `a well-formed frame parses with every field intact`() {
        val event = accept(alertFrame())
        assertEquals("RT-1", event.eventId)
        assertEquals(1L, event.sequence)
        assertEquals(RealtimeEventType.AlertRaised, event.type)
        assertEquals("VLAN_SECURE", event.scope)
        assertEquals(RealtimeSubject.Alert("ALRT-1092"), event.subject)
    }

    @Test
    fun `every scenario frame is either accepted or safely refused`() {
        PreviewRealtimeScenario.frames.forEach { frame ->
            assertNotNull(RealtimeEventParser.parse(frame))
        }
    }

    @Test
    fun `an unsupported schema version is held, not reinterpreted`() {
        val frame = alertFrame() + ("schemaVersion" to "99")
        val result = RealtimeEventParser.parse(frame)
        assertTrue(result is RealtimeParseResult.UnsupportedVersion)
        assertEquals(99, (result as RealtimeParseResult.UnsupportedVersion).version)
    }

    @Test
    fun `schema version is checked before anything else`() {
        val frame = alertFrame() + ("schemaVersion" to "99") + ("eventType" to "NONSENSE")
        assertTrue(RealtimeEventParser.parse(frame) is RealtimeParseResult.UnsupportedVersion)
    }

    @Test
    fun `an unknown event type is refused, never defaulted`() {
        assertEquals(
            RealtimeRejection.UnknownEventType,
            reject(alertFrame() + ("eventType" to "QUARANTINE_NOW")).reason
        )
    }

    @Test
    fun `enum values are matched exactly`() {
        listOf("new", "New", " NEW", "RESOLVED_MAYBE").forEach { candidate ->
            assertEquals(
                "\"$candidate\" was accepted",
                RealtimeRejection.InvalidEnum,
                reject(alertFrame(lifecycle = candidate)).reason
            )
        }
    }

    @Test
    fun `malformed identifiers, sequences, timestamps and scopes are refused`() {
        assertEquals(
            RealtimeRejection.InvalidIdentifier,
            reject(alertFrame(id = "../../etc")).reason
        )
        assertEquals(
            RealtimeRejection.InvalidSequence,
            reject(alertFrame() + ("sequence" to "not-a-number")).reason
        )
        assertEquals(
            RealtimeRejection.InvalidSequence,
            reject(alertFrame() + ("sequence" to "-4")).reason
        )
        assertEquals(
            RealtimeRejection.InvalidTimestamp,
            reject(alertFrame() + ("occurredAt" to "10")).reason
        )
        assertEquals(
            RealtimeRejection.InvalidScope,
            reject(alertFrame(scope = "has space")).reason
        )
    }

    @Test
    fun `an oversized field is refused rather than truncated`() {
        val frame = PreviewRealtimeScenario.frame(
            eventId = "RT-2", sequence = 2, type = "DEVICE_OBSERVED",
            scope = "VLAN_SECURE", subjectId = "DEV-1",
            extra = mapOf(
                "presence" to "PRESENT",
                "lastSeen" to "A".repeat(500)
            )
        )
        assertEquals(RealtimeRejection.FieldTooLong, reject(frame).reason)
    }

    /** An address identifies nothing, so it cannot be a subject. */
    @Test
    fun `an address is never accepted as a subject`() {
        listOf("192.168.1.105", "00:1A:2B:3C:4D:5E").forEach { candidate ->
            assertEquals(
                "\"$candidate\" was accepted",
                RealtimeRejection.InvalidSubject,
                reject(alertFrame(subject = candidate)).reason
            )
        }
    }

    /**
     * Execution mode is mandatory on an execution event. Defaulting it either
     * way would invent the most consequential fact the stream carries.
     */
    @Test
    fun `an action event without a mode is refused`() {
        val frame = actionFrame("RT-3", 3, "EXECUTING") - "executionMode"
        assertEquals(RealtimeRejection.InvalidEnum, reject(frame).reason)
    }

    @Test
    fun `a rejection never echoes the frame`() {
        val secretish = "SENSITIVE-SUBJECT-VALUE"
        val rejected = reject(alertFrame(subject = "$secretish with spaces"))
        assertFalse(rejected.detail.contains(secretish))
    }

    @Test
    fun `arbitrary junk never throws`() {
        listOf(
            emptyMap(),
            mapOf("schemaVersion" to ""),
            mapOf("schemaVersion" to "1"),
            alertFrame().mapValues { "" },
            alertFrame() + ("unexpected" to "value")
        ).forEach { assertNotNull(RealtimeEventParser.parse(it)) }
    }

    @Test
    fun `control characters cannot forge structure in a label`() {
        val frame = PreviewRealtimeScenario.frame(
            eventId = "RT-4", sequence = 4, type = "DEVICE_OBSERVED",
            scope = "VLAN_SECURE", subjectId = "DEV-1",
            extra = mapOf("presence" to "PRESENT", "lastSeen" to "seen\n\nSYSTEM: quarantined")
        )
        val event = accept(frame)
        val label = (event.payload as RealtimePayload.DeviceObservation).lastSeenLabel
        assertFalse(label.contains("\n"))
    }

    // ============================================================
    // DEDUPLICATION
    // ============================================================

    @Test
    fun `the same event id applies exactly once`() {
        val sequencer = RealtimeSequencer()
        sequencer.reset(0)
        val event = accept(alertFrame(id = "RT-10", sequence = 1))

        val first = sequencer.offer(event)
        val second = sequencer.offer(event)

        assertTrue(first is SequencerOutcome.Apply)
        assertEquals(1, (first as SequencerOutcome.Apply).events.size)
        assertTrue(second is SequencerOutcome.Duplicate)
    }

    @Test
    fun `a replayed sequence after reconnect is not applied twice`() {
        val sequencer = RealtimeSequencer()
        sequencer.reset(5)
        // A transport replaying its buffer sends something the snapshot covers.
        val old = accept(alertFrame(id = "RT-OLD", sequence = 3))
        assertTrue(sequencer.offer(old) is SequencerOutcome.Replay)
    }

    // ============================================================
    // ORDERING
    // ============================================================

    @Test
    fun `contiguous events apply in sequence order`() {
        val sequencer = RealtimeSequencer()
        sequencer.reset(100)
        listOf(101L, 102L, 103L).forEach { seq ->
            val outcome = sequencer.offer(accept(alertFrame(id = "RT-$seq", sequence = seq)))
            assertTrue("$seq", outcome is SequencerOutcome.Apply)
        }
        assertEquals(103L, sequencer.lastAppliedSequence)
    }

    /**
     * Arrival order is not event order. 102 arriving after 103 must still be
     * applied before it, or a console shows effects before their causes.
     */
    @Test
    fun `an out-of-order event waits for its predecessor`() {
        val sequencer = RealtimeSequencer()
        sequencer.reset(100)

        val late = sequencer.offer(accept(alertFrame(id = "RT-103", sequence = 103)))
        assertTrue(late is SequencerOutcome.Buffered)
        assertEquals(100L, sequencer.lastAppliedSequence)

        val filler = sequencer.offer(accept(alertFrame(id = "RT-101", sequence = 101)))
        assertTrue(filler is SequencerOutcome.Buffered || filler is SequencerOutcome.Apply)

        val closing = sequencer.offer(accept(alertFrame(id = "RT-102", sequence = 102)))
        // 102 closes the run, so 102 and 103 go together in order.
        val applied = (closing as SequencerOutcome.Apply).events.map { it.sequence }
        assertEquals(listOf(102L, 103L), applied)
        assertEquals(103L, sequencer.lastAppliedSequence)
    }

    // ============================================================
    // GAPS
    // ============================================================

    /**
     * Missing events cannot be guessed at. The client says so rather than
     * quietly presenting incomplete state as complete.
     */
    @Test
    fun `a sequence gap that does not close triggers a resync`() {
        val sequencer = RealtimeSequencer(gapTolerance = 3)
        sequencer.reset(100)
        sequencer.offer(accept(alertFrame(id = "RT-101", sequence = 101)))

        // 102 never arrives.
        val outcomes = (103L..106L).map { seq ->
            sequencer.offer(accept(alertFrame(id = "RT-$seq", sequence = seq)))
        }
        val gap = outcomes.filterIsInstance<SequencerOutcome.GapDetected>().firstOrNull()
        assertNotNull("no gap was reported", gap)
        assertEquals(102L, gap!!.expectedSequence)
        // Nothing after the gap was applied.
        assertEquals(101L, sequencer.lastAppliedSequence)
    }

    @Test
    fun `a wide jump is treated as a gap immediately`() {
        val sequencer = RealtimeSequencer(maxGap = 10)
        sequencer.reset(100)
        val outcome = sequencer.offer(accept(alertFrame(id = "RT-far", sequence = 500)))
        assertTrue(outcome is SequencerOutcome.GapDetected)
    }

    @Test
    fun `a resync re-anchors and clears what was waiting`() {
        val sequencer = RealtimeSequencer()
        sequencer.reset(100)
        sequencer.offer(accept(alertFrame(id = "RT-105", sequence = 105)))
        assertEquals(1, sequencer.bufferedCount)

        sequencer.reset(200)
        assertEquals(0, sequencer.bufferedCount)
        assertEquals(200L, sequencer.lastAppliedSequence)
        assertNull(sequencer.pendingGap())
    }

    @Test
    fun `a pending gap is reported while it is open`() {
        val sequencer = RealtimeSequencer()
        sequencer.reset(100)
        sequencer.offer(accept(alertFrame(id = "RT-104", sequence = 104)))
        assertEquals(101L..103L, sequencer.pendingGap())
    }

    // ============================================================
    // REDUCTION
    // ============================================================

    private fun reduce(state: RealtimeState, frame: Map<String, String>): ReduceResult =
        RealtimeReducer.reduce(state, accept(frame))

    @Test
    fun `an alert event writes only the alert map`() {
        val result = reduce(RealtimeState(), alertFrame(lifecycle = "ACKNOWLEDGED"))
        val state = result.next
        assertEquals(AlertLifecycle.Acknowledged, state.alerts["ALRT-1092"]?.lifecycle)
        assertTrue(state.deliveries.isEmpty())
        assertTrue(state.identities.isEmpty())
        assertTrue(state.actions.isEmpty())
    }

    @Test
    fun `a delivery event writes only the delivery map`() {
        val frame = PreviewRealtimeScenario.frame(
            eventId = "RT-20", sequence = 20, type = "DELIVERY_STATE_CHANGED",
            scope = "VLAN_SECURE", subjectId = "NTF-7002",
            extra = mapOf("deliveryState" to "FAILED", "attemptCount" to "3")
        )
        val state = reduce(RealtimeState(), frame).next
        assertEquals(DeliveryState.Failed, state.deliveries["NTF-7002"]?.state)
        assertTrue("a delivery event touched alerts", state.alerts.isEmpty())
    }

    @Test
    fun `a trust event writes only the identity map`() {
        val frame = PreviewRealtimeScenario.frame(
            eventId = "RT-21", sequence = 21, type = "IDENTITY_REVOKED",
            scope = "VLAN_GUEST", subjectId = "TID-9E12",
            extra = mapOf("trust" to "REVOKED")
        )
        val state = reduce(RealtimeState(), frame).next
        assertEquals(TrustState.Revoked, state.identities["TID-9E12"]?.trust)
        assertTrue(state.devices.isEmpty())
        assertTrue(state.actions.isEmpty())
    }

    @Test
    fun `an observation keys on the device id, not the address`() {
        val first = PreviewRealtimeScenario.frame(
            eventId = "RT-30", sequence = 30, type = "DEVICE_OBSERVED",
            scope = "VLAN_SECURE", subjectId = "DEV-1001",
            extra = mapOf(
                "presence" to "PRESENT",
                "observedAddress" to "10.0.0.1",
                "lastSeen" to "now"
            )
        )
        val second = PreviewRealtimeScenario.frame(
            eventId = "RT-31", sequence = 31, type = "DEVICE_OBSERVED",
            scope = "VLAN_SECURE", subjectId = "DEV-1001",
            extra = mapOf(
                "presence" to "PRESENT",
                "observedAddress" to "10.0.0.99",
                "lastSeen" to "now"
            )
        )
        var state = reduce(RealtimeState(), first).next
        state = reduce(state, second).next

        // One device, re-addressed. Not two.
        assertEquals(1, state.devices.size)
        assertEquals("10.0.0.99", state.devices["DEV-1001"]?.observedAddress)
    }

    @Test
    fun `a breaker change is recorded for the whole subsystem`() {
        val frame = PreviewRealtimeScenario.frame(
            eventId = "RT-40", sequence = 40, type = "CIRCUIT_BREAKER_CHANGED",
            scope = "VLAN_BUILD", subjectId = null,
            extra = mapOf("circuitBreaker" to "OPEN")
        )
        assertEquals(CircuitBreakerState.Open, reduce(RealtimeState(), frame).next.circuitBreaker)
    }

    // ============================================================
    // EXECUTION LIFECYCLE
    // ============================================================

    @Test
    fun `the legal action progression applies`() {
        var state = RealtimeState()
        listOf(
            "REQUESTED" to ExecutionState.Requested,
            "AUTHORIZED" to ExecutionState.Authorized,
            "EXECUTING" to ExecutionState.Executing,
            "RECONCILING" to ExecutionState.Reconciling,
            "SUCCEEDED" to ExecutionState.Succeeded
        ).forEachIndexed { index, (wire, expected) ->
            val result = reduce(state, actionFrame("RT-a$index", 50L + index, wire))
            assertTrue("$wire was refused", result is ReduceResult.Applied)
            state = result.next
            assertEquals(expected, state.actions["ACT-1"]?.state)
        }
    }

    @Test
    fun `a failure may roll back`() {
        var state = reduce(RealtimeState(), actionFrame("RT-b0", 60, "EXECUTING")).next
        state = reduce(state, actionFrame("RT-b1", 61, "FAILED")).next
        state = reduce(state, actionFrame("RT-b2", 62, "ROLLBACK_REQUESTED")).next
        assertEquals(ExecutionState.RollbackRequested, state.actions["ACT-1"]?.state)

        state = reduce(state, actionFrame("RT-b3", 63, "ROLLBACK_FAILED")).next
        assertEquals(ExecutionState.RollbackFailed, state.actions["ACT-1"]?.state)
    }

    /**
     * Submitting an action and it working are different events, and no stream
     * may join them.
     */
    @Test
    fun `a request cannot become a success`() {
        val state = reduce(RealtimeState(), actionFrame("RT-c0", 70, "REQUESTED")).next
        val result = reduce(state, actionFrame("RT-c1", 71, "SUCCEEDED"))
        assertTrue(result is ReduceResult.Ignored)
        assertEquals(IgnoreReason.IllegalTransition, (result as ReduceResult.Ignored).reason)
        assertEquals(ExecutionState.Requested, result.next.actions["ACT-1"]?.state)
    }

    @Test
    fun `a rollback request is not a rollback success`() {
        val state = reduce(RealtimeState(), actionFrame("RT-d0", 80, "ROLLBACK_REQUESTED")).next
        assertEquals(ExecutionState.RollbackRequested, state.actions["ACT-1"]?.state)
        assertFalse(state.actions["ACT-1"]?.state == ExecutionState.RolledBack)
    }

    @Test
    fun `a terminal action does not un-finish`() {
        val state = reduce(RealtimeState(), actionFrame("RT-e0", 90, "EXECUTING")).next
            .let { reduce(it, actionFrame("RT-e1", 91, "SUCCEEDED")).next }

        listOf("EXECUTING", "FAILED", "RECONCILING", "REQUESTED").forEachIndexed { i, wire ->
            val result = reduce(state, actionFrame("RT-e${i + 2}", 92L + i, wire))
            assertTrue("$wire was applied to a succeeded action", result is ReduceResult.Ignored)
        }
    }

    /**
     * Reconciliation is its own claim. Succeeding is the pipeline reporting
     * the action finished; reconciled is a separate check that the system
     * agrees.
     */
    @Test
    fun `success does not imply reconciliation`() {
        val state = reduce(RealtimeState(), actionFrame("RT-f0", 100, "EXECUTING")).next
            .let { reduce(it, actionFrame("RT-f1", 101, "SUCCEEDED", reconciled = false)).next }
        assertEquals(ExecutionState.Succeeded, state.actions["ACT-1"]?.state)
        assertFalse(state.actions["ACT-1"]!!.reconciled)
    }

    // ============================================================
    // EXECUTION MODE
    // ============================================================

    @Test
    fun `a simulated action stays simulated`() {
        val state = reduce(
            RealtimeState(),
            actionFrame("RT-g0", 110, "SUCCEEDED", mode = "AUDIT_ONLY")
        ).next
        assertEquals(ExecutionMode.AuditOnly, state.actions["ACT-1"]?.mode)
    }

    /**
     * Mode belongs to the event, never to whatever the client saw last. A
     * live run must not inherit AUDIT_ONLY because a simulation came before
     * it, and a simulation must not inherit live.
     */
    @Test
    fun `mode is never inherited from a previous event`() {
        var state = reduce(
            RealtimeState(),
            actionFrame("RT-h0", 120, "EXECUTING", mode = "AUDIT_ONLY", subject = "ACT-2")
        ).next
        assertEquals(ExecutionMode.AuditOnly, state.actions["ACT-2"]?.mode)

        state = reduce(
            state,
            actionFrame("RT-h1", 121, "SUCCEEDED", mode = "ENFORCE", subject = "ACT-2")
        ).next
        assertEquals(ExecutionMode.Enforce, state.actions["ACT-2"]?.mode)
    }

    @Test
    fun `an unknown mode stays unknown`() {
        val state = reduce(
            RealtimeState(),
            actionFrame("RT-i0", 130, "EXECUTING", mode = "UNKNOWN")
        ).next
        assertEquals(ExecutionMode.Unknown, state.actions["ACT-1"]?.mode)
    }

    // ============================================================
    // HISTORY
    // ============================================================

    @Test
    fun `one applied event yields one history entry`() {
        var state = RealtimeState()
        state = reduce(state, alertFrame(id = "RT-200", sequence = 200)).next
        state = reduce(state, alertFrame(id = "RT-201", sequence = 201, subject = "ALRT-1")).next
        assertEquals(2, state.history.size)
        assertEquals(listOf("RT-201", "RT-200"), state.history.map { it.eventId })
    }

    @Test
    fun `history is bounded`() {
        var state = RealtimeState()
        repeat(REALTIME_HISTORY_LIMIT + 40) { i ->
            state = reduce(state, alertFrame(id = "RT-h$i", sequence = 300L + i)).next
        }
        assertEquals(REALTIME_HISTORY_LIMIT, state.history.size)
    }

    // ============================================================
    // BACKOFF
    // ============================================================

    @Test
    fun `backoff grows and is capped`() {
        assertEquals(RealtimeBackoff.INITIAL_DELAY_MS, RealtimeBackoff.delayFor(1))
        assertTrue(RealtimeBackoff.delayFor(2) > RealtimeBackoff.delayFor(1))
        assertTrue(RealtimeBackoff.delayFor(5) > RealtimeBackoff.delayFor(3))
        assertEquals(RealtimeBackoff.MAX_DELAY_MS, RealtimeBackoff.delayFor(30))
        (1..40).forEach {
            assertTrue(RealtimeBackoff.delayFor(it) <= RealtimeBackoff.MAX_DELAY_MS)
            assertTrue(RealtimeBackoff.delayFor(it) >= RealtimeBackoff.INITIAL_DELAY_MS)
        }
    }

    // ============================================================
    // FRESHNESS
    // ============================================================

    @Test
    fun `a disconnected feed is never described as live`() {
        RealtimeConnectionState.entries
            .filter { it != RealtimeConnectionState.Connected }
            .forEach { connection ->
                assertEquals(
                    "$connection",
                    RealtimeFreshness.Stale,
                    RealtimeFreshness.of(connection, System.currentTimeMillis(), System.currentTimeMillis())
                )
            }
    }

    @Test
    fun `a connected but silent feed ages into stale`() {
        val now = 1_000_000L
        assertEquals(
            RealtimeFreshness.Live,
            RealtimeFreshness.of(RealtimeConnectionState.Connected, now - 1_000, now)
        )
        assertEquals(
            RealtimeFreshness.Aging,
            RealtimeFreshness.of(RealtimeConnectionState.Connected, now - 40_000, now)
        )
        assertEquals(
            RealtimeFreshness.Stale,
            RealtimeFreshness.of(RealtimeConnectionState.Connected, now - 200_000, now)
        )
    }

    @Test
    fun `a connection with no events yet is unknown, not live`() {
        assertEquals(
            RealtimeFreshness.Unknown,
            RealtimeFreshness.of(RealtimeConnectionState.Connected, null, 1_000L)
        )
    }
}
