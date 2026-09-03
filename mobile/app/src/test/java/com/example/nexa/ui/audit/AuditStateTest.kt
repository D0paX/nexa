package com.example.nexa.ui.audit

import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The audit model's behaviour: ordering, mapping, search, filtering and the
 * states the screen can be in.
 *
 * Everything asserted here is pure. None of it needs a composition, which is
 * the point — no security reasoning lives in one.
 */
class AuditStateTest {

    private val content = AuditPreview.scenario as AuditUiState.Content
    private val entries = AuditPreview.entries

    // ============================================================
    // CHRONOLOGY
    // ============================================================

    @Test
    fun `history reads newest first by default`() {
        val ages = content.visible.map { it.ageMinutes }
        assertEquals(ages.sorted(), ages)
        assertEquals(AuditSort.Newest, content.sort)
    }

    @Test
    fun `oldest first reverses the record`() {
        val ordered = entries.applySort(AuditSort.Oldest)
        val ages = ordered.map { it.ageMinutes }
        assertEquals(ages.sortedDescending(), ages)
    }

    /**
     * Two events inside the same displayed minute keep the order the source
     * assigned them. Chronology comes from the record, never from the order
     * rows happened to be built in.
     */
    @Test
    fun `the authoritative sequence breaks ties within a minute`() {
        val sameMinute = entries.filter { it.ageMinutes == 86 }.applySort(AuditSort.Newest)
        assertTrue(sameMinute.size >= 2)
        assertEquals("EVT-4407", sameMinute.first().id)
        assertEquals("EVT-4406", sameMinute.last().id)
    }

    @Test
    fun `a late-arriving event lands where it belongs in time`() {
        val late = entries.first { it.id == "EVT-4302" }
        val reordered = (listOf(late) + entries.filter { it.id != late.id })
            .applySort(AuditSort.Newest)
        assertEquals(entries.map { it.id }, reordered.map { it.id })
    }

    // ============================================================
    // EVENT TYPE MAPPING
    // ============================================================

    @Test
    fun `every event type maps to a category, a label and an icon`() {
        AuditEventType.entries.forEach { type ->
            assertNotNull(type.category)
            assertTrue(type.label.isNotBlank())
            assertNotNull(type.icon)
        }
    }

    @Test
    fun `categories separate the phases they came from`() {
        assertEquals(AuditCategory.Device, AuditEventType.DeviceObserved.category)
        assertEquals(AuditCategory.Trust, AuditEventType.TrustChanged.category)
        assertEquals(AuditCategory.Alert, AuditEventType.AlertResolved.category)
        assertEquals(AuditCategory.Notification, AuditEventType.NotificationDelivered.category)
        assertEquals(AuditCategory.Action, AuditEventType.ActionSucceeded.category)
        assertEquals(AuditCategory.Enforcement, AuditEventType.EnforcementBindingCreated.category)
        assertEquals(AuditCategory.System, AuditEventType.CircuitBreakerOpened.category)
    }

    @Test
    fun `reverification is a trust operation, not an enforcement one`() {
        assertEquals(AuditCategory.Trust, AuditEventType.ReverificationRequested.category)
    }

    // ============================================================
    // SEARCH
    // ============================================================

    @Test
    fun `search finds an event by its id`() {
        val hit = entries.applyQuery("EVT-4433")
        assertEquals(1, hit.size)
        assertEquals(AuditEventType.ActionSucceeded, hit.single().type)
    }

    @Test
    fun `search finds every record of one action`() {
        val hits = entries.applyQuery("ACT-8871")
        assertEquals(6, hits.size)
        assertTrue(hits.all { it.correlationId == "ACT-8871" })
    }

    @Test
    fun `search finds records by alert id`() {
        val hits = entries.applyQuery("ALRT-1092")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.alertId == "ALRT-1092" || it.correlationId == "ALRT-1092" })
    }

    @Test
    fun `search finds records by device label, mac, address and scope`() {
        assertTrue(entries.applyQuery("Build Server").isNotEmpty())
        assertTrue(entries.applyQuery("3C:22:FB").isNotEmpty())
        assertTrue(entries.applyQuery("10.20.7.12").isNotEmpty())
        assertTrue(entries.applyQuery("VLAN_BUILD").isNotEmpty())
    }

    @Test
    fun `search finds records by identity identifier`() {
        val hits = entries.applyQuery("TID-9E12")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.any { it.type == AuditEventType.IdentityRevoked })
    }

    @Test
    fun `search finds records by action code`() {
        val hits = entries.applyQuery("RELEASE_QUARANTINE")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.actionCode == "RELEASE_QUARANTINE" })
    }

    @Test
    fun `an empty query narrows nothing`() {
        assertEquals(entries.size, entries.applyQuery("   ").size)
    }

    @Test
    fun `a query that matches nothing returns nothing`() {
        assertTrue(entries.applyQuery("EVT-0000-NOPE").isEmpty())
    }

    // ============================================================
    // FILTERING
    // ============================================================

    @Test
    fun `filtering by category keeps only that category`() {
        val filtered = entries.applyFilters(
            AuditFilters(categories = setOf(AuditCategory.Notification))
        )
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.category == AuditCategory.Notification })
    }

    @Test
    fun `filtering by outcome keeps only that outcome`() {
        val filtered = entries.applyFilters(AuditFilters(outcomes = setOf(AuditOutcome.Unknown)))
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.outcome == AuditOutcome.Unknown })
    }

    @Test
    fun `filtering by scope keeps only that scope`() {
        val filtered = entries.applyFilters(AuditFilters(scopes = setOf("VLAN_BUILD")))
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.target.scopeOrNull == "VLAN_BUILD" })
    }

    @Test
    fun `filtering by time range bounds the window`() {
        val filtered = entries.applyFilters(AuditFilters(timeRange = AuditTimeRange.LastHour))
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.ageMinutes <= 60 })
        assertTrue(filtered.size < entries.size)
    }

    @Test
    fun `the simulation filter keeps only simulated runs`() {
        val filtered = entries.applyFilters(AuditFilters(onlySimulated = true))
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.executionMode == ExecutionMode.AuditOnly })
    }

    /**
     * The filter that must not over-reach. An alert being raised has no
     * execution mode, and having none is not the same as having been live.
     */
    @Test
    fun `filtering by live mode never sweeps in modeless events`() {
        val filtered = entries.applyFilters(
            AuditFilters(executionModes = setOf(ExecutionMode.Enforce))
        )
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.executionMode == ExecutionMode.Enforce })
        assertTrue(filtered.none { it.category == AuditCategory.Alert })
        assertTrue(entries.any { it.executionMode == null })
    }

    @Test
    fun `filters combine rather than replace one another`() {
        val filtered = entries.applyFilters(
            AuditFilters(
                categories = setOf(AuditCategory.Action),
                outcomes = setOf(AuditOutcome.Failed)
            )
        )
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.category == AuditCategory.Action })
        assertTrue(filtered.all { it.outcome == AuditOutcome.Failed })
    }

    @Test
    fun `an inactive filter set narrows nothing`() {
        val filters = AuditFilters()
        assertFalse(filters.isActive)
        assertEquals(0, filters.activeCount)
        assertEquals(entries.size, entries.applyFilters(filters).size)
    }

    // ============================================================
    // QUICK FILTERS
    // ============================================================

    @Test
    fun `quick filters select the families they name`() {
        assertEquals(
            setOf(AuditCategory.Action, AuditCategory.Enforcement),
            AuditQuickFilter.Actions.categories
        )
        assertEquals(setOf(AuditCategory.Trust), AuditQuickFilter.Trust.categories)
        assertEquals(setOf(AuditCategory.Alert), AuditQuickFilter.Alerts.categories)
        assertEquals(setOf(AuditCategory.Notification), AuditQuickFilter.Notifications.categories)
        assertTrue(AuditQuickFilter.All.categories.isEmpty())
    }

    @Test
    fun `applying a quick filter is reflected back in the chips`() {
        AuditQuickFilter.entries.forEach { quick ->
            val filters = AuditFilters().withQuickFilter(quick)
            assertEquals(quick, filters.activeQuickFilter())
        }
    }

    @Test
    fun `the simulations quick filter narrows to simulated runs`() {
        val filters = AuditFilters().withQuickFilter(AuditQuickFilter.Simulations)
        val filtered = entries.applyFilters(filters)
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.isSimulated })
    }

    @Test
    fun `a hand-built filter set matches no quick filter`() {
        val filters = AuditFilters(
            categories = setOf(AuditCategory.Trust, AuditCategory.Device)
        )
        assertNull(filters.activeQuickFilter())
    }

    // ============================================================
    // PAGINATION
    // ============================================================

    @Test
    fun `only a bounded page is handed to the list`() {
        assertEquals(AUDIT_PAGE_SIZE, content.page.size)
        assertTrue(content.visible.size > content.page.size)
        assertTrue(content.hasMore)
    }

    @Test
    fun `a short history needs no further pages`() {
        val short = auditContent(entries.take(3))
        assertEquals(3, short.page.size)
        assertFalse(short.hasMore)
    }

    @Test
    fun `the page is the head of the ordered result`() {
        assertEquals(content.visible.take(AUDIT_PAGE_SIZE).map { it.id }, content.page.map { it.id })
    }

    // ============================================================
    // GROUPING
    // ============================================================

    @Test
    fun `entries group into contiguous days`() {
        val groups = groupByDay(entries.applySort(AuditSort.Newest)) { it.dayLabel }
        assertEquals(2, groups.size)
        assertTrue(groups.first().label.startsWith("Today"))
        assertTrue(groups.last().label.startsWith("Yesterday"))
        assertEquals(entries.size, groups.sumOf { it.entries.size })
    }

    @Test
    fun `grouping an empty history yields no groups`() {
        assertTrue(groupByDay(emptyList()) { it.dayLabel }.isEmpty())
    }

    // ============================================================
    // TIME PRESENTATION
    // ============================================================

    @Test
    fun `every record carries both an absolute and a relative time`() {
        entries.forEach { entry ->
            assertTrue(entry.id, entry.occurredAtLabel.contains("UTC"))
            assertTrue(entry.id, entry.relativeLabel.endsWith("ago"))
            assertTrue(entry.id, entry.dayLabel.isNotBlank())
        }
    }

    // ============================================================
    // SCREEN STATES
    // ============================================================

    @Test
    fun `an empty history is empty, not broken`() {
        val empty = AuditPreview.empty as AuditUiState.Content
        assertTrue(empty.visible.isEmpty())
        assertTrue(empty.page.isEmpty())
        assertFalse(empty.hasMore)
        assertEquals(0, empty.summary.total)
        assertTrue(empty.coverage.isComplete)
    }

    @Test
    fun `unavailable is its own state and carries no entries`() {
        assertTrue(AuditPreview.unavailable is AuditUiState.Unavailable)
        assertTrue(AuditPreview.offline is AuditUiState.Offline)
    }

    @Test
    fun `a stale feed is marked stale and still complete`() {
        val stale = AuditPreview.stale as AuditUiState.Content
        assertTrue(stale.freshness is DataFreshness.Stale)
        assertTrue(stale.coverage.isComplete)
        assertTrue(stale.visible.isNotEmpty())
    }

    @Test
    fun `a degraded feed says it is incomplete and why`() {
        val degraded = AuditPreview.degraded as AuditUiState.Content
        assertFalse(degraded.coverage.isComplete)
        val partial = degraded.coverage as AuditCoverage.Partial
        assertTrue(partial.reason.isNotBlank())
        assertTrue(degraded.visible.size < entries.size)
    }

    // ============================================================
    // SUMMARY
    // ============================================================

    @Test
    fun `live and simulated records are counted apart`() {
        val summary = content.summary
        assertEquals(entries.size, summary.total)
        assertTrue(summary.liveEnforcement > 0)
        assertTrue(summary.simulated > 0)
        assertEquals(entries.count { it.isSimulated }, summary.simulated)
        assertEquals(entries.count { it.isLiveEnforcement }, summary.liveEnforcement)
        assertTrue(summary.liveEnforcement + summary.simulated < summary.total)
    }

    /**
     * The header states a count and then breaks it down. Both must describe
     * the same records: counting the filtered set and then breaking it down
     * with totals from the whole history produced "6 record(s) · 6 failed",
     * where every number was true of a different set and the sentence was not
     * true of either.
     */
    @Test
    fun `the summary describes the records actually shown`() {
        val filtered = auditContent(
            all = entries,
            filters = AuditFilters(onlySimulated = true)
        )
        assertEquals(filtered.visible.size, filtered.summary.total)
        assertEquals(filtered.visible.count { it.isSimulated }, filtered.summary.simulated)
        assertEquals(0, filtered.summary.liveEnforcement)
        assertEquals(
            filtered.visible.count { it.outcome == AuditOutcome.Failed },
            filtered.summary.failures
        )
        assertTrue(filtered.summary.failures < filtered.summary.total)
    }

    @Test
    fun `a search narrows the summary with the list`() {
        val searched = auditContent(all = entries, query = "ACT-8871")
        assertEquals(6, searched.summary.total)
        assertEquals(6, searched.visible.size)
    }

    @Test
    fun `unknown outcomes are counted as unknown, not as failures`() {
        assertTrue(content.summary.unknownOutcome > 0)
        val unknowns = entries.filter { it.outcome == AuditOutcome.Unknown }
        assertTrue(unknowns.none { it.outcome == AuditOutcome.Failed })
    }

    // ============================================================
    // DETAIL
    // ============================================================

    @Test
    fun `an unresolvable event id reports unavailable rather than inventing one`() {
        assertTrue(AuditPreview.detailFor("EVT-DOES-NOT-EXIST") is AuditDetailUiState.Unavailable)
    }

    @Test
    fun `a record's sequence is the rest of its action, oldest first`() {
        val detail = AuditPreview.detailFor("EVT-4411") as AuditDetailUiState.Content
        val related = detail.data.related
        assertEquals(5, related.size)
        assertTrue(related.all { it.correlationId == "ACT-8871" })
        assertTrue(related.none { it.id == "EVT-4411" })
        val ages = related.map { it.ageMinutes }
        assertEquals(ages.sortedDescending(), ages)
    }

    @Test
    fun `detail fields omit values the record does not have`() {
        val breaker = AuditPreview.detailFor("EVT-4511") as AuditDetailUiState.Content
        val labels = breaker.data.fields.map { it.label }
        assertFalse(labels.contains("MAC"))
        assertFalse(labels.contains("SOURCE ALERT"))
        assertFalse(labels.contains("ACTION"))
        assertTrue(labels.contains("EVENT ID"))
        assertTrue(labels.contains("OUTCOME"))
        assertTrue(breaker.data.fields.none { it.value.isBlank() })
    }

    @Test
    fun `an action record exposes its mode, action and states`() {
        val detail = AuditPreview.detailFor("EVT-4411") as AuditDetailUiState.Content
        val fields = detail.data.fields.associate { it.label to it.value }
        assertEquals("QUARANTINE_DEVICE", fields["ACTION"])
        assertEquals("ENFORCE (live)", fields["EXECUTION MODE"])
        assertEquals("NORMAL", fields["PREVIOUS STATE"])
        assertEquals("QUARANTINED", fields["RESULTING STATE"])
        assertEquals("ACT-8871", fields["CORRELATION"])
    }

    // ============================================================
    // NAVIGATION TARGETS
    // ============================================================

    @Test
    fun `a record originating from an alert links back to that alert`() {
        val raised = entries.first { it.id == "EVT-4402" }
        val links = auditLinks(raised)
        assertTrue(links.any { it is AuditLink.Alert && it.alertId == "ALRT-1092" })
    }

    @Test
    fun `a device record links to the observed device`() {
        val observed = entries.first { it.id == "EVT-4401" }
        assertTrue(auditLinks(observed).any { it is AuditLink.Device })
    }

    @Test
    fun `an identity record links to the identity, not to a device`() {
        val revoked = entries.first { it.id == "EVT-4305" }
        val links = auditLinks(revoked)
        assertTrue(links.any { it is AuditLink.Identity && it.identityId == "TID-9E12" })
        assertTrue(links.none { it is AuditLink.Device })
    }

    @Test
    fun `a subsystem record links nowhere`() {
        val breaker = entries.first { it.id == "EVT-4510" }
        assertTrue(auditLinks(breaker).isEmpty())
    }
}
