package com.example.nexa.ui.notifications

import com.example.nexa.ui.alerts.AlertLifecycle
import com.example.nexa.ui.alerts.AlertSeverity
import com.example.nexa.ui.common.DeliveryState
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.enforcement.ExecutionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The separation this checkpoint exists to guarantee.
 *
 *   SecurityEvent  ≠  Alert  ≠  Notification  ≠  its delivery
 *
 * The guarantee is structural rather than editorial. Delivery presentation is
 * a function of the delivery half of a record; source presentation is a
 * function of the source half; neither can read the other. These tests prove
 * that by holding one half fixed, varying the other through every value it
 * can take, and requiring the first half's output to be byte-identical.
 *
 * A regression here would be a screen telling an operator that a security
 * incident failed because a push message did not arrive.
 */
class NotificationSeparationTest {

    private val records = NotificationPreview.records

    private val alertRecord = records.first { it.id == "NTF-7002" }
    private val actionRecord = records.first { it.id == "NTF-7008" }
    private val trustRecord = records.first { it.id == "NTF-7011" }

    private fun NotificationRecord.withState(state: DeliveryState) =
        copy(delivery = delivery.copy(state = state))

    // ============================================================
    // SOURCE PRESENTATION IS DELIVERY-INDEPENDENT
    // ============================================================

    @Test
    fun `the subject's presentation is identical whatever became of the message`() {
        records.forEach { record ->
            val fields = notificationSourceFields(record)
            val summary = sourceStateSummary(record.source)
            val status = sourceStatus(record.source)
            val provenance = sourceProvenance(record.source)
            val type = notificationTypeLabel(record.source)

            DeliveryState.entries.forEach { state ->
                val altered = record.withState(state)
                assertEquals("${record.id}/$state", fields, notificationSourceFields(altered))
                assertEquals("${record.id}/$state", summary, sourceStateSummary(altered.source))
                assertEquals("${record.id}/$state", status, sourceStatus(altered.source))
                assertEquals("${record.id}/$state", provenance, sourceProvenance(altered.source))
                assertEquals("${record.id}/$state", type, notificationTypeLabel(altered.source))
            }
        }
    }

    // ============================================================
    // DELIVERY PRESENTATION IS SOURCE-INDEPENDENT
    // ============================================================

    @Test
    fun `the delivery's presentation is identical whatever the incident is doing`() {
        val headline = deliveryHeadline(alertRecord.delivery)
        val explanation = deliveryExplanation(alertRecord.delivery)
        val fields = notificationDeliveryFields(alertRecord)
        val surface = deliverySurfaceFor(alertRecord)
        val rank = deliveryAttentionRank(alertRecord)
        val source = alertRecord.source as NotificationSource.Alert

        AlertLifecycle.entries.forEach { lifecycle ->
            AlertSeverity.entries.forEach { severity ->
                val altered = alertRecord.copy(
                    source = source.copy(lifecycle = lifecycle, severity = severity)
                )
                assertEquals(headline, deliveryHeadline(altered.delivery))
                assertEquals(explanation, deliveryExplanation(altered.delivery))
                assertEquals(fields, notificationDeliveryFields(altered))
                assertEquals(surface, deliverySurfaceFor(altered))
                assertEquals(rank, deliveryAttentionRank(altered))
            }
        }
    }

    // ============================================================
    // NOTIFICATION / ALERT
    // ============================================================

    /** Alert NEW + notification FAILED — the alert is still NEW. */
    @Test
    fun `a failed notification leaves a new alert new`() {
        val source = NotificationSource.Alert(
            "ALRT-2001", "Suspicious Port Scan", AlertSeverity.Critical, AlertLifecycle.New
        )
        val record = alertRecord.copy(source = source).withState(DeliveryState.Failed)

        assertEquals(AlertLifecycle.New, (record.source as NotificationSource.Alert).lifecycle)
        val statement = deliveryImpactStatement(record)
        assertTrue(statement.contains("remains NEW"))
        assertTrue(statement.contains("does not change an incident's state"))
    }

    /** Alert RESOLVED + notification FAILED — the incident stays closed. */
    @Test
    fun `a failed notification does not reopen a resolved alert`() {
        val source = NotificationSource.Alert(
            "ALRT-2002", "Quarantine released", AlertSeverity.Information, AlertLifecycle.Resolved
        )
        val record = alertRecord.copy(source = source).withState(DeliveryState.Failed)

        assertEquals(AlertLifecycle.Resolved, (record.source as NotificationSource.Alert).lifecycle)
        val statement = deliveryImpactStatement(record)
        assertTrue(statement.contains("remains RESOLVED"))
        assertFalse(statement.lowercase().contains("reopened"))
    }

    /** Notification DELIVERED is not alert RESOLVED. */
    @Test
    fun `a delivered notification does not resolve anything`() {
        val source = NotificationSource.Alert(
            "ALRT-2003", "Suspicious Port Scan", AlertSeverity.Critical, AlertLifecycle.New
        )
        val record = alertRecord.copy(source = source).withState(DeliveryState.Delivered)

        assertEquals(AlertLifecycle.New, (record.source as NotificationSource.Alert).lifecycle)
        val statement = deliveryImpactStatement(record)
        assertTrue(statement.contains("does not mean the alert has been read, acknowledged or resolved"))
    }

    /** Notification FAILED is not alert FAILED. */
    @Test
    fun `no wording ever says the alert failed`() {
        val forbidden = listOf(
            "alert failed",
            "incident failed",
            "security alert failed",
            "alert exhausted",
            "incident exhausted"
        )
        records.forEach { record ->
            DeliveryState.entries.forEach { state ->
                val altered = record.withState(state)
                val texts = buildList {
                    add(deliveryHeadline(altered.delivery))
                    add(deliveryExplanation(altered.delivery))
                    add(deliveryImpactStatement(altered))
                    sourceStateSummary(altered.source)?.let { add(it) }
                    notificationDeliveryFields(altered).forEach { add("${it.label} ${it.value}") }
                    notificationSourceFields(altered).forEach { add("${it.label} ${it.value}") }
                }
                texts.forEach { text ->
                    val lower = text.lowercase()
                    forbidden.forEach { phrase ->
                        assertFalse(
                            "${record.id}/$state says \"$phrase\": $text",
                            lower.contains(phrase)
                        )
                    }
                }
            }
        }
    }

    /**
     * The scenario matrix. Every delivery state against every alert lifecycle:
     * the alert's lifecycle survives untouched, and the wording says so.
     */
    @Test
    fun `every delivery state against every alert lifecycle leaves the alert alone`() {
        DeliveryState.entries.forEach { state ->
            AlertLifecycle.entries.forEach { lifecycle ->
                val source = NotificationSource.Alert(
                    "ALRT-3000", "Test incident", AlertSeverity.Critical, lifecycle
                )
                val record = alertRecord.copy(source = source).withState(state)

                assertEquals(
                    "$state/$lifecycle",
                    lifecycle,
                    (record.source as NotificationSource.Alert).lifecycle
                )
                assertEquals(
                    "$state/$lifecycle",
                    AlertSeverity.Critical,
                    (record.source as NotificationSource.Alert).severity
                )
                assertTrue(
                    "$state/$lifecycle",
                    deliveryImpactStatement(record).contains(lifecycle.name.uppercase())
                )
            }
        }
    }

    /** A critical incident stays critical however badly its message fared. */
    @Test
    fun `a critical alert with a failed delivery is still reported as critical`() {
        val record = records.first { it.id == "NTF-7002" }
        val source = record.source as NotificationSource.Alert

        assertEquals(AlertSeverity.Critical, source.severity)
        assertEquals(DeliveryState.Failed, record.delivery.state)
        assertTrue(sourceStateSummary(record.source)!!.contains("CRITICAL"))
        // The delivery badge speaks for the delivery only.
        assertEquals("Failed", record.delivery.stateLabel)
        assertTrue(deliveryImpactStatement(record).contains("CRITICAL"))
    }

    // ============================================================
    // NOTIFICATION / ACTION
    // ============================================================

    /** Notification DELIVERED does not mean the action succeeded. */
    @Test
    fun `a delivered notification about a reconciling action claims no success`() {
        val record = actionRecord.withState(DeliveryState.Delivered)
        val source = record.source as NotificationSource.Action

        assertEquals(ExecutionState.Reconciling, source.executionState)
        val statement = deliveryImpactStatement(record)
        assertTrue(statement.contains("RECONCILING"))
        assertTrue(statement.contains("is not evidence of its outcome"))
        // The disclaimer is worded so it does not itself contain any of these
        // phrases — otherwise the guard could only pass by being weakened.
        listOf(
            "the action succeeded",
            "the action was successful",
            "action complete",
            "enforcement succeeded"
        ).forEach { claim ->
            assertFalse(claim, statement.lowercase().contains(claim))
        }
    }

    /** Notification FAILED does not mean the action failed. */
    @Test
    fun `a failed notification does not change an action's outcome`() {
        ExecutionState.entries.forEach { execution ->
            val source = NotificationSource.Action(
                "ACT-4000", "QUARANTINE_DEVICE", execution, ExecutionMode.Enforce
            )
            val record = actionRecord.copy(source = source).withState(DeliveryState.Failed)

            assertEquals(
                "$execution",
                execution,
                (record.source as NotificationSource.Action).executionState
            )
            assertTrue(
                "$execution",
                deliveryImpactStatement(record).contains("outcome is unchanged by the delivery failure")
            )
        }
    }

    @Test
    fun `a simulated action stays simulated in its notification`() {
        val record = records.first { it.id == "NTF-7010" }
        val source = record.source as NotificationSource.Action

        assertEquals(ExecutionMode.AuditOnly, source.executionMode)
        assertTrue(deliveryImpactStatement(record).contains("no firewall mutation occurred"))
        val mode = notificationSourceFields(record).first { it.label == "EXECUTION MODE" }
        assertEquals("AUDIT_ONLY (simulated)", mode.value)
    }

    // ============================================================
    // NOTIFICATION / TRUST
    // ============================================================

    @Test
    fun `delivery never changes trust standing`() {
        TrustState.entries.forEach { trust ->
            val source = NotificationSource.Trust("TID-5000", "Test identity", trust)
            DeliveryState.entries.forEach { state ->
                val record = trustRecord.copy(source = source).withState(state)
                assertEquals(
                    "$trust/$state",
                    trust,
                    (record.source as NotificationSource.Trust).trust
                )
            }
        }
    }

    @Test
    fun `a delivered notification does not verify an identity`() {
        val record = trustRecord.withState(DeliveryState.Delivered)
        val source = record.source as NotificationSource.Trust

        assertEquals(TrustState.Pending, source.trust)
        val statement = deliveryImpactStatement(record)
        assertTrue(statement.contains("does not verify it or change its trust standing"))
        assertTrue(statement.contains("PENDING"))
    }

    // ============================================================
    // NOTIFICATION / ENFORCEMENT
    // ============================================================

    /**
     * Nothing this screen renders claims a change to enforcement state. The
     * notification center is observational; it has no path to nftables, to a
     * binding, or to a reconciliation.
     */
    @Test
    fun `no delivery wording claims an enforcement change`() {
        val forbidden = listOf(
            "has been quarantined",
            "was quarantined",
            "has been released",
            "was released",
            "firewall was updated",
            "binding created",
            "binding removed",
            "enforcement applied"
        )
        records.forEach { record ->
            DeliveryState.entries.forEach { state ->
                val altered = record.withState(state)
                val texts = listOf(
                    deliveryHeadline(altered.delivery),
                    deliveryExplanation(altered.delivery),
                    deliveryImpactStatement(altered)
                )
                texts.forEach { text ->
                    val lower = text.lowercase()
                    forbidden.forEach { phrase ->
                        assertFalse(
                            "${record.id}/$state says \"$phrase\": $text",
                            lower.contains(phrase)
                        )
                    }
                }
            }
        }
    }

    /**
     * The structural guarantee behind the realtime seam: a delivery update
     * replaces the delivery half and cannot touch the source half.
     */
    @Test
    fun `a delivery update cannot edit the incident it was about`() {
        records.forEach { record ->
            DeliveryState.entries.forEach { state ->
                val updated = record.copy(delivery = record.delivery.copy(state = state))
                assertEquals(record.id, record.source, updated.source)
                assertEquals(record.id, record.target, updated.target)
                assertEquals(record.id, record.subject, updated.subject)
            }
        }
    }

    // ============================================================
    // PRIVACY
    // ============================================================

    @Test
    fun `nothing in a delivery record exposes secret material`() {
        val forbidden = listOf(
            "private key", "secret", "password", "bearer", "-----begin",
            "api_key", "apikey", "passphrase", "registration token", "device token:"
        )
        records.forEach { record ->
            val texts = buildList {
                add(deliveryHeadline(record.delivery))
                add(deliveryExplanation(record.delivery))
                add(deliveryImpactStatement(record))
                add(record.subject)
                notificationDeliveryFields(record).forEach { add("${it.label} ${it.value}") }
                notificationSourceFields(record).forEach { add("${it.label} ${it.value}") }
                record.delivery.attempts.forEach { add("${it.channel} ${it.detail}") }
            }
            texts.forEach { text ->
                val lower = text.lowercase()
                forbidden.forEach { term ->
                    assertFalse(
                        "${record.id} exposes \"$term\": $text",
                        lower.contains(term)
                    )
                }
            }
        }
    }
}
