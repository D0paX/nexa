package com.example.nexa.ui.notifications

import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.DeliveryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.nexa.ui.common.icon
import com.example.nexa.ui.common.label
import com.example.nexa.ui.common.status

/**
 * Delivery behaviour: mapping, prioritisation, search, filtering, ordering,
 * attempt chronology and the states the screen can be in.
 *
 * All pure. None of it needs a composition.
 */
class NotificationStateTest {

    private val content = NotificationPreview.scenario as NotificationCenterUiState.Content
    private val records = NotificationPreview.records

    // ============================================================
    // STATE MAPPING
    // ============================================================

    @Test
    fun `every delivery state has a label, a tone and its own shape`() {
        val icons = DeliveryState.entries.map { it.icon }
        DeliveryState.entries.forEach { state ->
            assertTrue(state.label.isNotBlank())
            assertNotNull(state.status)
            assertNotNull(state.icon)
        }
        // A retry is not a reverification and a failed message is not a
        // critical event: each state carries a glyph of its own.
        assertEquals(icons.size, icons.distinct().size)
    }

    @Test
    fun `every delivery state produces a headline and an explanation`() {
        val base = records.first().delivery
        DeliveryState.entries.forEach { state ->
            val delivery = base.copy(state = state)
            assertTrue("$state", deliveryHeadline(delivery).isNotBlank())
            assertTrue("$state", deliveryExplanation(delivery).isNotBlank())
        }
    }

    @Test
    fun `delivery wording always names the notification, never the incident`() {
        records.forEach { record ->
            val text = deliveryExplanation(record.delivery).lowercase()
            assertTrue(
                "${record.id}: $text",
                text.contains("notification") || text.contains("delivery") ||
                    text.contains("message") || text.contains("channel")
            )
        }
    }

    @Test
    fun `a failure explanation carries the recorded reason`() {
        val failed = records.first { it.id == "NTF-7002" }
        val text = deliveryExplanation(failed.delivery)
        assertTrue(text.contains("did not reach its destination"))
        assertTrue(text.contains("Device token rejected"))
    }

    @Test
    fun `an unavailable delivery is not reported as a failure`() {
        val unavailable = records.first { it.delivery.state == DeliveryState.Unavailable }
        val text = deliveryExplanation(unavailable.delivery).lowercase()
        assertTrue(text.contains("unknown"))
        assertTrue(text.contains("not a report that it failed"))
        assertFalse(unavailable.delivery.isFailure)
    }

    @Test
    fun `exhausted says no further attempt will be made`() {
        val exhausted = records.first { it.delivery.state == DeliveryState.Exhausted }
        assertEquals("Delivery exhausted", deliveryHeadline(exhausted.delivery))
        assertTrue(deliveryExplanation(exhausted.delivery).contains("No further attempt"))
    }

    @Test
    fun `retrying states which attempt it is on`() {
        val retrying = records.first { it.id == "NTF-7003" }
        assertEquals("Retrying — attempt 2 of 3", deliveryHeadline(retrying.delivery))
    }

    // ============================================================
    // RETRY
    // ============================================================

    @Test
    fun `a retry line appears only when the backend supplied timing`() {
        val scheduled = records.first { it.id == "NTF-7003" }
        assertEquals("Next attempt in 4m", retryLine(scheduled.delivery))

        // Retrying with no reported schedule says so rather than inventing one.
        val unscheduled = scheduled.copy(
            delivery = scheduled.delivery.copy(nextRetryLabel = null)
        )
        assertEquals(
            "A further attempt is scheduled. Timing is not reported.",
            retryLine(unscheduled.delivery)
        )
    }

    @Test
    fun `settled deliveries have no retry line`() {
        listOf(DeliveryState.Delivered, DeliveryState.Exhausted, DeliveryState.Pending)
            .forEach { state ->
                val delivery = records.first().delivery.copy(state = state, nextRetryLabel = null)
                assertNull("$state", retryLine(delivery))
            }
    }

    // ============================================================
    // ATTEMPT CHRONOLOGY
    // ============================================================

    @Test
    fun `attempts are newest first and numbered from the record`() {
        records.filter { it.delivery.attempts.size > 1 }.forEach { record ->
            val numbers = record.delivery.attempts.map { it.attempt }
            assertEquals(record.id, numbers.sortedDescending(), numbers)
            assertEquals(record.id, numbers.size, numbers.distinct().size)
            assertEquals(record.id, 1, numbers.min())
        }
    }

    @Test
    fun `the attempt count matches the attempts recorded`() {
        records.filter { it.delivery.attempts.isNotEmpty() }.forEach { record ->
            assertEquals(
                record.id,
                record.delivery.attempts.size,
                record.delivery.attemptCount
            )
        }
    }

    @Test
    fun `a pending delivery has no attempts yet`() {
        val pending = records.first { it.delivery.state == DeliveryState.Pending }
        assertEquals(0, pending.delivery.attemptCount)
        assertTrue(pending.delivery.attempts.isEmpty())
    }

    @Test
    fun `an exhausted record ends on its exhausted attempt`() {
        val exhausted = records.first { it.id == "NTF-7001" }
        assertEquals(DeliveryState.Exhausted, exhausted.delivery.attempts.first().state)
        assertEquals(4, exhausted.delivery.attempts.first().attempt)
    }

    // ============================================================
    // PRIORITISATION
    // ============================================================

    @Test
    fun `attention puts what nobody was told about first`() {
        val ordered = records.applySort(NotificationSort.Attention)
        assertEquals(DeliveryState.Exhausted, ordered.first().delivery.state)
    }

    @Test
    fun `attention ranks delivery states in operational order`() {
        val base = records.first()
        fun rank(state: DeliveryState) = deliveryAttentionRank(
            base.copy(delivery = base.delivery.copy(state = state, ageMinutes = 5))
        )
        assertTrue(rank(DeliveryState.Exhausted) < rank(DeliveryState.Failed))
        assertTrue(rank(DeliveryState.Failed) < rank(DeliveryState.Retrying))
        assertTrue(rank(DeliveryState.Retrying) < rank(DeliveryState.Pending))
        assertTrue(rank(DeliveryState.Pending) < rank(DeliveryState.Sent))
        assertTrue(rank(DeliveryState.Sent) < rank(DeliveryState.Delivered))
    }

    /**
     * The rule that stops history dominating the present: a twelve-day-old
     * failure is not more urgent than a delivery failing right now.
     */
    @Test
    fun `an old failure ranks below current in-flight delivery`() {
        val base = records.first()
        val fresh = base.copy(
            delivery = base.delivery.copy(state = DeliveryState.Failed, ageMinutes = 5)
        )
        val old = base.copy(
            delivery = base.delivery.copy(
                state = DeliveryState.Failed,
                ageMinutes = STALE_ATTENTION_MINUTES + 1
            )
        )
        val retrying = base.copy(
            delivery = base.delivery.copy(state = DeliveryState.Retrying, ageMinutes = 5)
        )
        assertTrue(deliveryAttentionRank(fresh) < deliveryAttentionRank(old))
        assertTrue(deliveryAttentionRank(retrying) < deliveryAttentionRank(old))
    }

    @Test
    fun `the old failure in the preview sorts below the current ones`() {
        val ordered = records.applySort(NotificationSort.Attention).map { it.id }
        assertTrue(ordered.indexOf("NTF-7014") > ordered.indexOf("NTF-7002"))
        assertTrue(ordered.indexOf("NTF-7014") > ordered.indexOf("NTF-7003"))
    }

    /**
     * The incident's severity plays no part in delivery ranking. Letting it
     * would turn this screen into a second, worse alert queue.
     */
    @Test
    fun `incident severity does not promote a delivery record`() {
        val delivered = records.first { it.id == "NTF-7004" }
        val rankAsIs = deliveryAttentionRank(delivered)
        val downgraded = delivered.copy(
            source = (delivered.source as NotificationSource.Alert).copy(
                severity = com.example.nexa.ui.alerts.AlertSeverity.Information
            )
        )
        assertEquals(rankAsIs, deliveryAttentionRank(downgraded))
    }

    // ============================================================
    // SORTING
    // ============================================================

    @Test
    fun `newest and oldest are exact inverses by age`() {
        val newest = records.applySort(NotificationSort.Newest).map { it.delivery.ageMinutes }
        val oldest = records.applySort(NotificationSort.Oldest).map { it.delivery.ageMinutes }
        assertEquals(newest.sorted(), newest)
        assertEquals(oldest.sortedDescending(), oldest)
    }

    @Test
    fun `ordering is deterministic across identical loads`() {
        NotificationSort.entries.forEach { sort ->
            assertEquals(
                records.applySort(sort).map { it.id },
                records.shuffled().applySort(sort).map { it.id }
            )
        }
    }

    // ============================================================
    // SEARCH
    // ============================================================

    @Test
    fun `search finds a record by its delivery id`() {
        val hits = records.applyQuery("NTF-7002")
        assertEquals(1, hits.size)
        assertEquals(DeliveryState.Failed, hits.single().delivery.state)
    }

    @Test
    fun `search finds records by alert, action and identity identifiers`() {
        assertTrue(records.applyQuery("ALRT-1089").isNotEmpty())
        assertTrue(records.applyQuery("ACT-9127").isNotEmpty())
        assertTrue(records.applyQuery("TID-9E12").isNotEmpty())
        assertTrue(records.applyQuery("EVT-4512").isNotEmpty())
    }

    @Test
    fun `search finds records by device label, mac, address and scope`() {
        assertTrue(records.applyQuery("Build Server").isNotEmpty())
        assertTrue(records.applyQuery("3C:22:FB").isNotEmpty())
        assertTrue(records.applyQuery("10.20.4.11").isNotEmpty())
        assertTrue(records.applyQuery("VLAN_BUILD").isNotEmpty())
    }

    @Test
    fun `an empty query narrows nothing and a miss returns nothing`() {
        assertEquals(records.size, records.applyQuery("   ").size)
        assertTrue(records.applyQuery("NTF-0000-NOPE").isEmpty())
    }

    // ============================================================
    // FILTERING
    // ============================================================

    @Test
    fun `filtering by delivery state keeps only that state`() {
        val filtered = records.applyFilters(
            NotificationFilters(states = setOf(DeliveryState.Retrying))
        )
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.delivery.state == DeliveryState.Retrying })
    }

    @Test
    fun `filtering by source type keeps only that source`() {
        val filtered = records.applyFilters(
            NotificationFilters(sourceTypes = setOf(NotificationSourceType.Action))
        )
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.sourceType == NotificationSourceType.Action })
    }

    @Test
    fun `filtering by scope and time range narrows correctly`() {
        val byScope = records.applyFilters(NotificationFilters(scopes = setOf("VLAN_BUILD")))
        assertTrue(byScope.isNotEmpty())
        assertTrue(byScope.all { it.target.scopeOrNull == "VLAN_BUILD" })

        val recent = records.applyFilters(
            NotificationFilters(timeRange = NotificationTimeRange.LastHour)
        )
        assertTrue(recent.isNotEmpty())
        assertTrue(recent.all { it.delivery.ageMinutes <= 60 })
        assertTrue(recent.size < records.size)
    }

    @Test
    fun `an inactive filter set narrows nothing`() {
        val filters = NotificationFilters()
        assertFalse(filters.isActive)
        assertEquals(0, filters.activeCount)
        assertEquals(records.size, records.applyFilters(filters).size)
    }

    // ============================================================
    // QUICK FILTERS
    // ============================================================

    @Test
    fun `the failed quick filter includes exhausted`() {
        assertEquals(
            setOf(DeliveryState.Failed, DeliveryState.Exhausted),
            NotificationQuickFilter.Failed.states
        )
        val filtered = records.applyFilters(
            NotificationFilters().withQuickFilter(NotificationQuickFilter.Failed)
        )
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.delivery.isFailure })
    }

    @Test
    fun `applying a quick filter is reflected back in the chips`() {
        NotificationQuickFilter.entries.forEach { quick ->
            val filters = NotificationFilters().withQuickFilter(quick)
            assertEquals(quick, filters.activeQuickFilter())
        }
    }

    @Test
    fun `a hand-built state set matches no quick filter`() {
        val filters = NotificationFilters(
            states = setOf(DeliveryState.Delivered, DeliveryState.Failed)
        )
        assertNull(filters.activeQuickFilter())
    }

    // ============================================================
    // SUMMARY
    // ============================================================

    @Test
    fun `the summary counts delivery states and nothing else`() {
        val s = content.summary
        assertEquals(records.size, s.total)
        assertEquals(records.count { it.delivery.state == DeliveryState.Failed }, s.failed)
        assertEquals(records.count { it.delivery.state == DeliveryState.Exhausted }, s.exhausted)
        assertEquals(records.count { it.delivery.state == DeliveryState.Retrying }, s.retrying)
        assertEquals(records.count { it.delivery.state == DeliveryState.Delivered }, s.delivered)
        assertEquals(s.failed + s.exhausted, s.needsAttention)
    }

    @Test
    fun `the summary describes the records actually shown`() {
        val filtered = notificationContent(
            all = records,
            filters = NotificationFilters(states = setOf(DeliveryState.Delivered))
        )
        assertEquals(filtered.visible.size, filtered.summary.total)
        assertEquals(0, filtered.summary.failed)
        assertEquals(0, filtered.summary.exhausted)
        assertEquals(filtered.visible.size, filtered.summary.delivered)
    }

    // ============================================================
    // PAGINATION
    // ============================================================

    @Test
    fun `only a bounded page is handed to the list`() {
        val paged = notificationContent(all = records, pageLimit = 5)
        assertEquals(5, paged.page.size)
        assertTrue(paged.hasMore)
        assertEquals(paged.visible.take(5).map { it.id }, paged.page.map { it.id })
    }

    @Test
    fun `a short list needs no further pages`() {
        val short = notificationContent(records.take(3))
        assertEquals(3, short.page.size)
        assertFalse(short.hasMore)
    }

    // ============================================================
    // SCREEN STATES
    // ============================================================

    @Test
    fun `an empty delivery record is empty, not broken`() {
        val empty = NotificationPreview.empty as NotificationCenterUiState.Content
        assertTrue(empty.visible.isEmpty())
        assertEquals(0, empty.summary.total)
        assertFalse(empty.hasMore)
        assertTrue(empty.coverage.isComplete)
    }

    @Test
    fun `unavailable and offline are their own states`() {
        assertTrue(NotificationPreview.unavailable is NotificationCenterUiState.Unavailable)
        assertTrue(NotificationPreview.offline is NotificationCenterUiState.Offline)
    }

    @Test
    fun `a stale feed is marked stale and still complete`() {
        val stale = NotificationPreview.stale as NotificationCenterUiState.Content
        assertTrue(stale.freshness is DataFreshness.Stale)
        assertTrue(stale.coverage.isComplete)
        assertTrue(stale.visible.isNotEmpty())
    }

    @Test
    fun `a degraded feed says it is incomplete and why`() {
        val degraded = NotificationPreview.degraded as NotificationCenterUiState.Content
        assertFalse(degraded.coverage.isComplete)
        val partial = degraded.coverage as NotificationCoverage.Partial
        assertTrue(partial.reason.isNotBlank())
        assertTrue(degraded.visible.size < records.size)
    }

    // ============================================================
    // DETAIL
    // ============================================================

    @Test
    fun `an unresolvable delivery id reports unavailable rather than inventing one`() {
        assertTrue(
            NotificationPreview.detailFor("NTF-DOES-NOT-EXIST")
                is NotificationDetailUiState.Unavailable
        )
    }

    @Test
    fun `delivery fields carry only delivery concepts`() {
        val detail = NotificationPreview.detailFor("NTF-7002")
            as NotificationDetailUiState.Content
        val labels = detail.data.deliveryFields.map { it.label }
        assertTrue(labels.contains("DELIVERY ID"))
        assertTrue(labels.contains("CHANNEL"))
        assertTrue(labels.contains("REPORTED REASON"))
        // Nothing about the incident may appear in the delivery block.
        assertFalse(labels.contains("ALERT"))
        assertFalse(labels.contains("SEVERITY"))
        assertFalse(labels.contains("ALERT STATE"))
    }

    @Test
    fun `source fields carry the subject and its own state`() {
        val detail = NotificationPreview.detailFor("NTF-7002")
            as NotificationDetailUiState.Content
        val fields = detail.data.sourceFields.associate { it.label to it.value }
        assertEquals("ALRT-1089", fields["ALERT"])
        assertEquals("Critical", fields["SEVERITY"])
        assertEquals("Acknowledged", fields["ALERT STATE"])
        assertEquals("3C:22:FB:19:04:A1", fields["MAC"])
        assertEquals("10.20.4.11", fields["OBSERVED ADDRESS"])
    }

    @Test
    fun `a channel is shown but never invented`() {
        records.forEach { record ->
            assertEquals(record.id, NotificationChannel.Push, record.delivery.channel)
        }
        assertEquals("Push (FCM)", NotificationChannel.Push.label)
    }

    // ============================================================
    // NAVIGATION TARGETS
    // ============================================================

    @Test
    fun `an alert notification links to its alert and its device`() {
        val links = notificationLinks(records.first { it.id == "NTF-7002" })
        assertTrue(links.any { it is NotificationLink.Alert && it.alertId == "ALRT-1089" })
        assertTrue(links.any { it is NotificationLink.Device })
    }

    @Test
    fun `a trust notification links to the identity`() {
        val links = notificationLinks(records.first { it.id == "NTF-7011" })
        assertTrue(links.any { it is NotificationLink.Identity && it.identityId == "TID-9E12" })
    }

    /**
     * No route from a delivery record into an execution. Responding to an
     * incident starts from the incident, not from a transport log.
     */
    @Test
    fun `no delivery record offers a route into the action flow`() {
        records.forEach { record ->
            notificationLinks(record).forEach { link ->
                assertTrue(
                    "${record.id} links to $link",
                    link is NotificationLink.Alert ||
                        link is NotificationLink.Device ||
                        link is NotificationLink.Identity
                )
            }
        }
    }

    /** An action notification points at the target, never at a re-execution. */
    @Test
    fun `an action notification exposes no action route`() {
        val action = records.first { it.id == "NTF-7008" }
        val links = notificationLinks(action)
        assertTrue(links.none { it is NotificationLink.Alert })
        assertTrue(links.all { it is NotificationLink.Device })
    }
}
