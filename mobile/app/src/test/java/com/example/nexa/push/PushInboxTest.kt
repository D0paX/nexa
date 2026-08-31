package com.example.nexa.push

import com.example.nexa.push.debug.PushFixtures
import com.example.nexa.ui.alerts.AlertSeverity
import com.example.nexa.ui.common.DeliveryState
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.notifications.NotificationSource
import com.example.nexa.ui.notifications.NotificationTarget
import com.example.nexa.ui.notifications.deliveryExplanation
import com.example.nexa.ui.notifications.deliveryImpactStatement
import com.example.nexa.ui.notifications.notificationLinks
import com.example.nexa.ui.notifications.notificationSourceFields
import com.example.nexa.ui.notifications.sourceStateSummary
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What arriving on this handset does, and — mostly — what it does not.
 *
 * A message reaching this device is one small fact. It is not the
 * notification service confirming delivery, not the alert service reporting a
 * lifecycle, not the enforcement pipeline reporting an outcome, and not the
 * trust service verifying anything.
 */
class PushInboxTest {

    private fun payloadOf(fixture: Map<String, String>): PushPayload =
        (PushPayloadParser.parse(fixture) as PushParseResult.Accepted).payload

    @Before
    fun setUp() = PushInbox.clear()

    @After
    fun tearDown() = PushInbox.clear()

    // ============================================================
    // ARRIVAL IS NOT DELIVERY CONFIRMATION
    // ============================================================

    /**
     * The Phase 3 delivery model is the backend's. A push landing here says
     * the transport reached one handset, which is a different fact from the
     * notification service reporting DELIVERED — and NEXA does not upgrade
     * the first into the second.
     */
    @Test
    fun `an arriving push is not recorded as delivered`() {
        val record = PushInbox.recordFor(payloadOf(PushFixtures.criticalAlert))
        assertEquals(DeliveryState.Unavailable, record.delivery.state)
        assertTrue(record.delivery.receivedOnThisDevice)
    }

    @Test
    fun `the record says arrival is not confirmation`() {
        val record = PushInbox.recordFor(payloadOf(PushFixtures.criticalAlert))
        val explanation = deliveryExplanation(record.delivery).lowercase()
        assertTrue(explanation.contains("arrived on this device"))
        assertTrue(explanation.contains("not the same as the notification service confirming"))
    }

    @Test
    fun `no arriving push claims a delivery failure either`() {
        val record = PushInbox.recordFor(payloadOf(PushFixtures.criticalAlert))
        assertFalse(record.delivery.isFailure)
        assertTrue(deliveryExplanation(record.delivery).lowercase().contains("unknown"))
    }

    // ============================================================
    // ARRIVAL IS NOT SECURITY STATE
    // ============================================================

    /** A message about an alert says nothing about where the incident stands. */
    @Test
    fun `a push never reports an alert lifecycle`() {
        val record = PushInbox.recordFor(payloadOf(PushFixtures.criticalAlert))
        val source = record.source as NotificationSource.Alert
        assertNull(source.lifecycle)
        assertTrue(sourceStateSummary(source)!!.contains("STATE NOT READ"))
        assertTrue(deliveryImpactStatement(record).contains("has not been read"))
    }

    @Test
    fun `a push never reports an action outcome`() {
        listOf(
            PushFixtures.actionExecuting,
            PushFixtures.actionSucceeded,
            PushFixtures.actionFailed,
            PushFixtures.rollbackFailed
        ).forEach { fixture ->
            val record = PushInbox.recordFor(payloadOf(fixture))
            val source = record.source as NotificationSource.Action
            assertNull(fixture.toString(), source.executionState)
            assertTrue(
                deliveryImpactStatement(record).contains("has not been read from the enforcement pipeline")
            )
        }
    }

    @Test
    fun `a push never reports a trust standing`() {
        val record = PushInbox.recordFor(payloadOf(PushFixtures.identityRevoked))
        val source = record.source as NotificationSource.Trust
        assertNull(source.trust)
        val statement = deliveryImpactStatement(record)
        assertTrue(statement.contains("has not been read"))
        assertTrue(statement.contains("neither verifies an identity nor changes its standing"))
    }

    @Test
    fun `a push never reports enforcement state`() {
        val record = PushInbox.recordFor(payloadOf(PushFixtures.actionExecuting))
        val text = (
            deliveryExplanation(record.delivery) + " " + deliveryImpactStatement(record)
            ).lowercase()
        listOf("has been quarantined", "was quarantined", "binding created", "enforcement applied")
            .forEach { assertFalse("said \"$it\"", text.contains(it)) }
    }

    /** The mode does travel, because losing it would hide a simulation. */
    @Test
    fun `a simulated push keeps its mode and its disclaimer`() {
        val record = PushInbox.recordFor(payloadOf(PushFixtures.auditOnlySimulation))
        val source = record.source as NotificationSource.Action
        assertEquals(ExecutionMode.AuditOnly, source.executionMode)
        assertTrue(
            deliveryImpactStatement(record).contains("no firewall mutation occurred")
        )
    }

    @Test
    fun `a push with no mode is recorded as unknown, never as live`() {
        val record = PushInbox.recordFor(payloadOf(PushFixtures.unknownExecutionMode))
        val source = record.source as NotificationSource.Action
        assertEquals(ExecutionMode.Unknown, source.executionMode)
        assertFalse(source.executionMode == ExecutionMode.Enforce)
    }

    @Test
    fun `the severity the sender claimed is kept as the sender's claim`() {
        val record = PushInbox.recordFor(payloadOf(PushFixtures.criticalAlert))
        val source = record.source as NotificationSource.Alert
        assertEquals(AlertSeverity.Critical, source.severity)
        // and still no lifecycle, so the record cannot read as an open incident
        assertNull(source.lifecycle)
    }

    // ============================================================
    // TARGETS ARE REFERENCES, NOT OBJECTS
    // ============================================================

    @Test
    fun `a target arrives unresolved, carrying only its identifier`() {
        val record = PushInbox.recordFor(payloadOf(PushFixtures.warningAlert))
        val target = record.target as NotificationTarget.UnresolvedDevice
        assertEquals("00:1A:2B:3C:4D:5E", target.mac)
        // Nothing was read, so nothing else is claimed.
        assertNull(record.target.let { (it as? NotificationTarget.Device)?.scope })
        val labels = notificationSourceFields(record).map { it.label }
        assertFalse(labels.contains("SCOPE"))
        assertFalse(labels.contains("OBSERVED ADDRESS"))
    }

    @Test
    fun `a device reference that is not a MAC produces no target at all`() {
        val record = PushInbox.recordFor(payloadOf(PushFixtures.deviceReferencedByAddress))
        assertEquals(NotificationTarget.None, record.target)
        assertTrue(notificationLinks(record).none { it is com.example.nexa.ui.notifications.NotificationLink.Device })
    }

    @Test
    fun `an unresolved target still links to its authoritative surface`() {
        val record = PushInbox.recordFor(payloadOf(PushFixtures.warningAlert))
        val links = notificationLinks(record)
        assertTrue(links.any { it is com.example.nexa.ui.notifications.NotificationLink.Device })
        assertTrue(links.any { it is com.example.nexa.ui.notifications.NotificationLink.Alert })
    }

    // ============================================================
    // DUPLICATES
    // ============================================================

    /**
     * FCM can legitimately deliver the same message twice. An operator
     * counting delivery records should not see one incident twice because the
     * transport retried.
     */
    @Test
    fun `the same notification id is recorded once`() {
        val payload = payloadOf(PushFixtures.criticalAlert)
        assertTrue(PushInbox.onIncomingPush(payload))
        assertFalse(PushInbox.onIncomingPush(payload))
        assertEquals(1, PushInbox.records.value.size)
    }

    @Test
    fun `the duplicate fixture does not create a second record`() {
        assertTrue(PushInbox.onIncomingPush(payloadOf(PushFixtures.criticalAlert)))
        assertFalse(PushInbox.onIncomingPush(payloadOf(PushFixtures.duplicateOfCriticalAlert)))
        assertEquals(1, PushInbox.records.value.size)
    }

    @Test
    fun `different notifications are all recorded`() {
        PushInbox.onIncomingPush(payloadOf(PushFixtures.criticalAlert))
        PushInbox.onIncomingPush(payloadOf(PushFixtures.warningAlert))
        PushInbox.onIncomingPush(payloadOf(PushFixtures.auditOnlySimulation))
        assertEquals(3, PushInbox.records.value.size)
    }

    @Test
    fun `the newest arrival is first`() {
        PushInbox.onIncomingPush(payloadOf(PushFixtures.criticalAlert))
        PushInbox.onIncomingPush(payloadOf(PushFixtures.warningAlert))
        assertEquals("NTF-9002", PushInbox.records.value.first().id)
    }

    // ============================================================
    // REJECTIONS
    // ============================================================

    @Test
    fun `a rejected push creates a diagnostic and no delivery record`() {
        val rejected = PushPayloadParser.parse(PushFixtures.unsupportedVersion)
            as PushParseResult.Rejected
        PushInbox.onRejectedPush(rejected)

        assertTrue(PushInbox.records.value.isEmpty())
        assertEquals(1, PushInbox.rejections.value.size)
        assertEquals(
            PushRejectionReason.UnsupportedSchemaVersion,
            PushInbox.rejections.value.first().reason
        )
    }

    @Test
    fun `a rejection diagnostic keeps no payload content`() {
        val data = PushFixtures.criticalAlert +
            (PushPayloadParser.KEY_SOURCE_ID to "LEAKY-VALUE with spaces")
        val rejected = PushPayloadParser.parse(data) as PushParseResult.Rejected
        PushInbox.onRejectedPush(rejected)
        assertFalse(PushInbox.rejections.value.first().detail.contains("LEAKY-VALUE"))
    }

    // ============================================================
    // FEEDING THE NOTIFICATION CENTER
    // ============================================================

    /**
     * Push records are ordinary delivery records. They flow through the same
     * read model as everything else rather than into a parallel push list.
     */
    @Test
    fun `an arriving push produces a record the notification center can hold`() {
        val payload = payloadOf(PushFixtures.criticalAlert)
        PushInbox.onIncomingPush(payload)
        val record = PushInbox.records.value.single()

        assertEquals(payload.notificationId, record.id)
        assertEquals(payload.notificationId, record.delivery.deliveryId)
        assertEquals(payload.title, record.subject)
        assertEquals(
            com.example.nexa.ui.notifications.NotificationChannel.Push,
            record.delivery.channel
        )
    }
}
