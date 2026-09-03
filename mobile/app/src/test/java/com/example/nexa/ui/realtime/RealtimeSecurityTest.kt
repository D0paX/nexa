package com.example.nexa.ui.realtime

import com.example.nexa.ui.alerts.AlertLifecycle
import com.example.nexa.ui.alerts.AlertsPreview
import com.example.nexa.ui.common.DeliveryState
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.devices.DevicesPreview
import com.example.nexa.ui.devices.DevicesUiState
import com.example.nexa.ui.enforcement.ExecutionState
import com.example.nexa.ui.identity.IdentityPreview
import com.example.nexa.ui.identity.IdentitiesUiState
import com.example.nexa.ui.notifications.NotificationCenterUiState
import com.example.nexa.ui.notifications.NotificationPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a realtime stream must never be able to do.
 *
 * An event reports that something happened. It is not authorization, not
 * execution, not proof, and not a licence to rewrite a neighbouring domain.
 * Every test here is one way a live feed could quietly make a security
 * console wrong.
 */
class RealtimeSecurityTest {

    private fun event(frame: Map<String, String>): RealtimeEvent =
        (RealtimeEventParser.parse(frame) as RealtimeParseResult.Accepted).event

    private fun reduce(state: RealtimeState, frame: Map<String, String>): RealtimeState =
        RealtimeReducer.reduce(state, event(frame)).next

    private fun frame(
        id: String,
        sequence: Long,
        type: String,
        subject: String?,
        scope: String = "VLAN_SECURE",
        extra: Map<String, String>
    ) = PreviewRealtimeScenario.frame(id, sequence, type, scope, subject, extra)

    // ============================================================
    // NO EVENT EXECUTES ANYTHING
    // ============================================================

    /**
     * The stream has no imperative. Every event type reports a past fact, and
     * there is no variant that asks the client to do something.
     */
    @Test
    fun `no event type is a command`() {
        val commandish = listOf(
            "QUARANTINE_DEVICE", "RELEASE_QUARANTINE", "REQUIRE_REVERIFICATION",
            "EXECUTE", "AUTHORIZE", "APPLY_RULE", "NFTABLES_COMMIT"
        )
        commandish.forEach { candidate ->
            assertNull("\"$candidate\" was recognised", RealtimeEventType.fromWire(candidate))
        }
    }

    /**
     * The store has no field an authorization could live in, so no event can
     * write one. Trust arriving is only trust.
     */
    @Test
    fun `a trust change grants nothing`() {
        val state = reduce(
            RealtimeState(),
            frame(
                "RT-1", 1, "TRUST_CHANGED", "TID-88F1",
                extra = mapOf("trust" to "TRUSTED")
            )
        )
        assertEquals(TrustState.Trusted, state.identities["TID-88F1"]?.trust)
        // Nothing else moved, and there is nowhere for permission to be stored.
        assertTrue(state.actions.isEmpty())
        assertTrue(state.devices.isEmpty())
        assertTrue(state.alerts.isEmpty())
    }

    @Test
    fun `a trust change does not alter enforcement`() {
        var state = reduce(
            RealtimeState(),
            frame(
                "RT-2", 2, "DEVICE_ENFORCEMENT_CHANGED", "DEV-1001",
                extra = mapOf("enforcement" to "QUARANTINED")
            )
        )
        state = reduce(
            state,
            frame("RT-3", 3, "IDENTITY_REVOKED", "TID-88F1", extra = mapOf("trust" to "REVOKED"))
        )
        assertEquals(DeviceEnforcement.Quarantined, state.devices["DEV-1001"]?.enforcement)
    }

    // ============================================================
    // DOMAIN SEPARATION
    // ============================================================

    /** A notification failing is not a change to the incident it was about. */
    @Test
    fun `a delivery event never touches an alert`() {
        var state = reduce(
            RealtimeState(),
            frame("RT-10", 10, "ALERT_ACKNOWLEDGED", "ALRT-1092", extra = mapOf("lifecycle" to "ACKNOWLEDGED"))
        )
        val before = state.alerts["ALRT-1092"]

        state = reduce(
            state,
            frame(
                "RT-11", 11, "DELIVERY_STATE_CHANGED", "NTF-7002",
                extra = mapOf("deliveryState" to "FAILED", "attemptCount" to "3")
            )
        )
        assertEquals(before, state.alerts["ALRT-1092"])
        assertEquals(AlertLifecycle.Acknowledged, state.alerts["ALRT-1092"]?.lifecycle)
        assertEquals(DeliveryState.Failed, state.deliveries["NTF-7002"]?.state)
    }

    /** And an alert moving is not a delivery. */
    @Test
    fun `an alert event never touches a delivery`() {
        var state = reduce(
            RealtimeState(),
            frame(
                "RT-12", 12, "DELIVERY_STATE_CHANGED", "NTF-7002",
                extra = mapOf("deliveryState" to "DELIVERED", "attemptCount" to "1")
            )
        )
        val before = state.deliveries["NTF-7002"]

        state = reduce(
            state,
            frame("RT-13", 13, "ALERT_RESOLVED", "ALRT-1092", extra = mapOf("lifecycle" to "RESOLVED"))
        )
        assertEquals(before, state.deliveries["NTF-7002"])
    }

    /** An observation is not an identity statement. */
    @Test
    fun `a device address change never changes trust`() {
        var state = reduce(
            RealtimeState(),
            frame("RT-20", 20, "TRUST_CHANGED", "TID-88F1", extra = mapOf("trust" to "TRUSTED"))
        )
        state = reduce(
            state,
            frame(
                "RT-21", 21, "DEVICE_ADDRESS_CHANGED", "DEV-1001",
                extra = mapOf(
                    "presence" to "PRESENT",
                    "observedAddress" to "10.0.0.55",
                    "lastSeen" to "now"
                )
            )
        )
        assertEquals(TrustState.Trusted, state.identities["TID-88F1"]?.trust)
        assertEquals(1, state.identities.size)
    }

    // ============================================================
    // SCOPE
    // ============================================================

    /**
     * A scope is mandatory on every event, and every overlay records the one
     * it came from. Without it the client could not tell which segment a
     * change belongs to.
     */
    @Test
    fun `every overlay records the scope it came from`() {
        val state = reduce(
            RealtimeState(),
            frame(
                "RT-30", 30, "DEVICE_ENFORCEMENT_CHANGED", "DEV-1001",
                scope = "VLAN_BUILD", extra = mapOf("enforcement" to "QUARANTINED")
            )
        )
        assertEquals("VLAN_BUILD", state.devices["DEV-1001"]?.scope)
    }

    @Test
    fun `an event without a scope is refused`() {
        val bad = frame(
            "RT-31", 31, "ALERT_RAISED", "ALRT-1", extra = mapOf("lifecycle" to "NEW")
        ) - "scope"
        val result = RealtimeEventParser.parse(bad)
        assertTrue(result is RealtimeParseResult.Rejected)
        assertEquals(
            RealtimeRejection.MissingField,
            (result as RealtimeParseResult.Rejected).reason
        )
    }

    // ============================================================
    // OPTIMISM
    // ============================================================

    /**
     * A request is a request. Nothing in the pipeline turns one into a
     * success, however much the operator would like it to.
     */
    @Test
    fun `no path leads from requested to succeeded`() {
        val state = reduce(
            RealtimeState(),
            frame(
                "RT-40", 40, "ACTION_STATE_CHANGED", "ACT-1",
                extra = mapOf(
                    "executionState" to "REQUESTED",
                    "executionMode" to "ENFORCE",
                    "actionCode" to "QUARANTINE_DEVICE"
                )
            )
        )
        assertEquals(ExecutionState.Requested, state.actions["ACT-1"]?.state)
        assertFalse(isLegalExecutionTransition(ExecutionState.Requested, ExecutionState.Succeeded))
        assertFalse(isLegalExecutionTransition(ExecutionState.Requested, ExecutionState.RolledBack))
    }

    @Test
    fun `the lifecycle refuses every impossible move`() {
        val impossible = listOf(
            ExecutionState.Succeeded to ExecutionState.Executing,
            ExecutionState.Succeeded to ExecutionState.Failed,
            ExecutionState.Denied to ExecutionState.Executing,
            ExecutionState.RolledBack to ExecutionState.Succeeded,
            ExecutionState.RollbackFailed to ExecutionState.RolledBack,
            ExecutionState.Reconciling to ExecutionState.Requested,
            ExecutionState.Executing to ExecutionState.Authorized
        )
        impossible.forEach { (from, to) ->
            assertFalse("$from -> $to was allowed", isLegalExecutionTransition(from, to))
        }
    }

    @Test
    fun `joining mid-flight accepts whatever the pipeline reports`() {
        ExecutionState.entries.forEach { state ->
            assertTrue("$state", isLegalExecutionTransition(null, state))
        }
    }

    // ============================================================
    // AUDIT_ONLY
    // ============================================================

    @Test
    fun `a simulated action cannot become live by arriving again`() {
        var state = reduce(
            RealtimeState(),
            frame(
                "RT-50", 50, "ACTION_STATE_CHANGED", "ACT-9004",
                extra = mapOf(
                    "executionState" to "EXECUTING",
                    "executionMode" to "AUDIT_ONLY",
                    "actionCode" to "RELEASE_QUARANTINE"
                )
            )
        )
        state = reduce(
            state,
            frame(
                "RT-51", 51, "ACTION_STATE_CHANGED", "ACT-9004",
                extra = mapOf(
                    "executionState" to "SUCCEEDED",
                    "executionMode" to "AUDIT_ONLY",
                    "actionCode" to "RELEASE_QUARANTINE"
                )
            )
        )
        assertEquals(ExecutionMode.AuditOnly, state.actions["ACT-9004"]?.mode)
        assertEquals(ExecutionState.Succeeded, state.actions["ACT-9004"]?.state)
    }

    /**
     * A simulation does not mutate the firewall, so nothing in the reducer
     * lets one write a device's enforcement. Only an enforcement event does
     * that, and it is a different event about a different subject.
     */
    @Test
    fun `a simulated action does not change device enforcement`() {
        val state = reduce(
            RealtimeState(),
            frame(
                "RT-52", 52, "ACTION_STATE_CHANGED", "ACT-9004",
                extra = mapOf(
                    "executionState" to "SUCCEEDED",
                    "executionMode" to "AUDIT_ONLY",
                    "actionCode" to "QUARANTINE_DEVICE"
                )
            )
        )
        assertTrue("a simulation wrote enforcement state", state.devices.isEmpty())
    }

    // ============================================================
    // OVERLAYS
    // ============================================================

    @Test
    fun `a device overlay lands on the right device`() {
        val inventory = DevicesPreview.inventory
        val target = inventory.first()
        val state = reduce(
            RealtimeState(),
            frame(
                "RT-60", 60, "DEVICE_ENFORCEMENT_CHANGED", target.id,
                extra = mapOf("enforcement" to "QUARANTINED")
            )
        )
        val updated = inventory.withRealtime(state)
        assertEquals(DeviceEnforcement.Quarantined, updated.first { it.id == target.id }.enforcement)
        // Everything else is untouched.
        updated.filter { it.id != target.id }.forEach { device ->
            assertEquals(
                inventory.first { it.id == device.id }.enforcement,
                device.enforcement
            )
        }
    }

    /**
     * An address change follows the device. It never moves an update to a
     * different record, which is what would happen if the overlay keyed on
     * the address.
     */
    @Test
    fun `re-addressing a device does not move the update elsewhere`() {
        val inventory = DevicesPreview.inventory
        val target = inventory[0]
        val other = inventory[1]

        val state = reduce(
            RealtimeState(),
            frame(
                "RT-61", 61, "DEVICE_ADDRESS_CHANGED", target.id,
                extra = mapOf(
                    "presence" to "PRESENT",
                    // Deliberately another device's address.
                    "observedAddress" to (other.ip ?: "10.0.0.1"),
                    "lastSeen" to "now"
                )
            )
        )
        val updated = inventory.withRealtime(state)
        assertEquals(other.ip, updated.first { it.id == target.id }.ip)
        // The device that actually owns that address is unchanged.
        assertEquals(other.ip, updated.first { it.id == other.id }.ip)
        assertEquals(other.enforcement, updated.first { it.id == other.id }.enforcement)
    }

    @Test
    fun `an alert overlay changes lifecycle and nothing else`() {
        val content = AlertsPreview.scenario as com.example.nexa.ui.alerts.AlertsUiState.Content
        val target = content.all.first()
        val state = reduce(
            RealtimeState(),
            frame(
                "RT-62", 62, "ALERT_RESOLVED", target.id,
                extra = mapOf("lifecycle" to "RESOLVED")
            )
        )
        val updated = content.all.withRealtime(state)
        val after = updated.first { it.id == target.id }
        assertEquals(AlertLifecycle.Resolved, after.lifecycle)
        // Severity and delivery are separate axes and were not published.
        assertEquals(target.severity, after.severity)
        assertEquals(target.delivery, after.delivery)
    }

    @Test
    fun `a delivery overlay changes delivery and never the source`() {
        val content = NotificationPreview.scenario as NotificationCenterUiState.Content
        val target = content.all.first()
        val state = reduce(
            RealtimeState(),
            frame(
                "RT-63", 63, "DELIVERY_STATE_CHANGED", target.id,
                extra = mapOf("deliveryState" to "DELIVERED", "attemptCount" to "1")
            )
        )
        val after = content.all.withRealtime(state).first { it.id == target.id }
        assertEquals(DeliveryState.Delivered, after.delivery.state)
        assertEquals(target.source, after.source)
        assertEquals(target.target, after.target)
        assertEquals(target.subject, after.subject)
    }

    @Test
    fun `an identity overlay changes trust and nothing else`() {
        val content = IdentityPreview.scenario as IdentitiesUiState.Content
        val target = content.all.first()
        val state = reduce(
            RealtimeState(),
            frame(
                "RT-64", 64, "IDENTITY_REVOKED", target.identityId,
                extra = mapOf("trust" to "REVOKED")
            )
        )
        val after = content.all.withRealtime(state).first { it.identityId == target.identityId }
        assertEquals(TrustState.Revoked, after.trust)
        assertEquals(target.credential, after.credential)
        assertEquals(target.verification, after.verification)
    }

    @Test
    fun `an empty overlay leaves every snapshot untouched`() {
        val empty = RealtimeState()
        val inventory = DevicesPreview.inventory
        assertEquals(inventory, inventory.withRealtime(empty))

        val alerts = (AlertsPreview.scenario as com.example.nexa.ui.alerts.AlertsUiState.Content).all
        assertEquals(alerts, alerts.withRealtime(empty))
    }

    // ============================================================
    // PUSH AND REALTIME CONVERGE
    // ============================================================

    /**
     * Push and the stream both report delivery. They must not become two
     * truths: both end up as delivery state on the same record, keyed by the
     * same delivery id, and the later authoritative report wins.
     */
    @Test
    fun `push and realtime resolve to one delivery state`() {
        val content = NotificationPreview.scenario as NotificationCenterUiState.Content
        val target = content.all.first { it.delivery.state == DeliveryState.Exhausted }

        // The stream reports something newer than the snapshot did.
        val state = reduce(
            RealtimeState(),
            frame(
                "RT-70", 70, "DELIVERY_STATE_CHANGED", target.id,
                extra = mapOf("deliveryState" to "DELIVERED", "attemptCount" to "5")
            )
        )
        val after = content.all.withRealtime(state).first { it.id == target.id }

        assertEquals(DeliveryState.Delivered, after.delivery.state)
        // One record, one state. Not two entries disagreeing.
        assertEquals(1, content.all.withRealtime(state).count { it.id == target.id })
    }

    // ============================================================
    // DIAGNOSTICS
    // ============================================================

    @Test
    fun `history keeps identifiers and never payload content`() {
        val state = reduce(
            RealtimeState(),
            frame(
                "RT-80", 80, "DELIVERY_STATE_CHANGED", "NTF-1",
                extra = mapOf(
                    "deliveryState" to "FAILED",
                    "attemptCount" to "2",
                    "failureReason" to "SENSITIVE-TOKEN-DETAIL"
                )
            )
        )
        val entry = state.history.single()
        assertEquals("RT-80", entry.eventId)
        assertFalse(entry.toString().contains("SENSITIVE-TOKEN-DETAIL"))
    }

    @Test
    fun `connection state is never a security verdict`() {
        // The vocabularies do not overlap: no connection state is named after
        // a posture, and none of them can be mistaken for one.
        val connectionWords = RealtimeConnectionState.entries.map { it.name.lowercase() }.toSet()
        val postureWords = setOf("secure", "insecure", "enforcing", "compromised", "safe")
        assertTrue(connectionWords.intersect(postureWords).isEmpty())
    }
}
