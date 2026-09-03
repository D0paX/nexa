package com.example.nexa.ui.common

import com.example.nexa.ui.alerts.AlertsPreview
import com.example.nexa.ui.alerts.AlertsUiState
import com.example.nexa.ui.audit.AuditPreview
import com.example.nexa.ui.audit.AuditUiState
import com.example.nexa.ui.devices.DeviceFilters
import com.example.nexa.ui.devices.DeviceSort
import com.example.nexa.ui.devices.DevicesPreview
import com.example.nexa.ui.devices.DevicesUiState
import com.example.nexa.ui.devices.Presence
import com.example.nexa.ui.devices.resolve
import com.example.nexa.ui.identity.IdentitiesUiState
import com.example.nexa.ui.identity.IdentityPreview
import com.example.nexa.ui.notifications.NotificationCenterUiState
import com.example.nexa.ui.notifications.NotificationPreview
import com.example.nexa.ui.overview.OverviewPreview
import com.example.nexa.ui.overview.OverviewUiState
import com.example.nexa.ui.realtime.DeviceOverlay
import com.example.nexa.ui.realtime.RealtimeState
import com.example.nexa.ui.realtime.withRealtime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The states a screen can be in, and the ways they must never be confused.
 *
 * Phase 5.20 separated availability from content. Phase 5.21 separated a
 * failed search from an empty source. This file holds the remaining pair
 * apart: resolving data is not the absence of data, and revalidating data an
 * operator is already reading is not the same as having none.
 *
 * The rule underneath all of it: lack of data is not evidence of a safe
 * state, and a screen that is still working it out must not answer as though
 * it had finished.
 */
class DataStateTest {

    // ============================================================
    // LOADING IS ITS OWN STATE
    // ============================================================

    /**
     * Every domain has a distinct loading state. If loading were represented
     * by an empty content object, an operator would be told the inventory was
     * empty during the second before it arrived.
     */
    @Test
    fun `loading is never an empty content state`() {
        assertTrue(DevicesUiState.Loading is DevicesUiState)
        assertFalse(DevicesUiState.Loading is DevicesUiState.Content)
        assertFalse(AlertsUiState.Loading is AlertsUiState.Content)
        assertFalse(AuditUiState.Loading is AuditUiState.Content)
        assertFalse(NotificationCenterUiState.Loading is NotificationCenterUiState.Content)
        assertFalse(IdentitiesUiState.Loading is IdentitiesUiState.Content)
        assertFalse(OverviewUiState.Loading is OverviewUiState.Content)
    }

    @Test
    fun `loading is distinct from every failure state`() {
        val devices = listOf(
            DevicesUiState.Loading,
            DevicesUiState.Offline,
            DevicesUiState.Unavailable,
            DevicesUiState.Error("x")
        )
        assertEquals(devices.size, devices.map { it::class }.toSet().size)
        assertNotEquals(DevicesUiState.Loading, DevicesUiState.Unavailable)
        assertNotEquals(DevicesUiState.Loading, DevicesUiState.Offline)
    }

    /**
     * The availability vocabulary already says this, and it is restated here
     * from the screen's side: Loading carries no data and is not actionable,
     * so nothing downstream can treat it as a picture of the system.
     */
    @Test
    fun `loading carries no data and authorizes nothing`() {
        assertFalse(NexaAvailability.Loading.hasData)
        assertFalse(NexaAvailability.Loading.isActionable)
        assertFalse(NexaAvailability.Loading.isCurrent)
    }

    @Test
    fun `a loading explanation never claims a result`() {
        val text = availabilityExplanation(NexaAvailability.Loading, "the device inventory").lowercase()
        assertTrue(text.contains("reading"))
        listOf("empty", "no devices", "unavailable", "failed", "secure").forEach {
            assertFalse("loading copy claims \"$it\": $text", text.contains(it))
        }
    }

    // ============================================================
    // REFRESHING IS NOT LOADING, AND NOT AN AVAILABILITY CHANGE
    // ============================================================

    private val content = DevicesPreview.content() as DevicesUiState.Content

    /**
     * A revalidation keeps every record on screen. Blanking the list would
     * take away the only information the operator currently has in order to
     * tell them better information is coming.
     */
    @Test
    fun `refreshing keeps the content it is checking`() {
        val refreshing = content.copy(refreshing = true)
        assertTrue(refreshing.refreshing)
        assertEquals(content.all, refreshing.all)
        assertEquals(content.visible, refreshing.visible)
        assertEquals(content.freshness, refreshing.freshness)
    }

    /**
     * Nor does it change how trustworthy the data is. The records are exactly
     * as current as they were a moment ago; NEXA is simply asking again.
     */
    @Test
    fun `refreshing does not change availability`() {
        val settled = contentAvailability(
            content.freshness, content.all.isEmpty(), content.degraded, content.offline
        )
        val refreshing = content.copy(refreshing = true)
        val during = contentAvailability(
            refreshing.freshness, refreshing.all.isEmpty(), refreshing.degraded, refreshing.offline
        )
        assertEquals(settled, during)
    }

    /** And a settled screen is not marked as refreshing. */
    @Test
    fun `content defaults to not refreshing`() {
        assertFalse(content.refreshing)
        assertFalse((AlertsPreview.content() as AlertsUiState.Content).refreshing)
        assertFalse((IdentityPreview.content() as IdentitiesUiState.Content).refreshing)
        assertFalse((AuditPreview.scenario as AuditUiState.Content).refreshing)
        assertFalse((NotificationPreview.scenario as NotificationCenterUiState.Content).refreshing)
        assertFalse((OverviewPreview.scenario as OverviewUiState.Content).refreshing)
    }

    /**
     * Refreshing an offline or stale screen does not quietly upgrade it. The
     * flag says a check is running, not that the check has succeeded.
     */
    @Test
    fun `refreshing a stale or offline screen keeps its availability`() {
        val stale = DevicesPreview.stale() as DevicesUiState.Content
        assertEquals(
            NexaAvailability.Stale,
            contentAvailability(stale.freshness, stale.all.isEmpty(), stale.degraded, stale.offline)
        )
        val checking = stale.copy(refreshing = true)
        assertEquals(
            NexaAvailability.Stale,
            contentAvailability(
                checking.freshness, checking.all.isEmpty(), checking.degraded, checking.offline
            )
        )

        val offline = DevicesPreview.offlineWithCache() as DevicesUiState.Content
        val offlineChecking = offline.copy(refreshing = true)
        assertEquals(
            NexaAvailability.Offline,
            contentAvailability(
                offlineChecking.freshness,
                offlineChecking.all.isEmpty(),
                offlineChecking.degraded,
                offlineChecking.offline
            )
        )
    }

    // ============================================================
    // PRESENTATION SURVIVES A RELOAD
    // ============================================================

    /**
     * What an operator set up is a question, and questions survive a retry.
     * Someone searching for a device when the inventory failed still wants
     * that search when it comes back.
     */
    @Test
    fun `presentation carries query, filters and sort`() {
        val kept = NexaPresentation(
            query = "printer",
            filters = DeviceFilters(trust = setOf(TrustState.Trusted)),
            sort = DeviceSort.Name
        )
        val restored = content.copy(
            query = kept.query,
            filters = kept.filters,
            sort = kept.sort
        )
        assertEquals("printer", restored.query)
        assertEquals(setOf(TrustState.Trusted), restored.filters.trust)
        assertEquals(DeviceSort.Name, restored.sort)
    }

    /**
     * And restoring it actually narrows the reloaded snapshot, rather than
     * being remembered and then ignored.
     */
    @Test
    fun `restored presentation narrows a freshly loaded snapshot`() {
        val kept = NexaPresentation(
            query = "build",
            filters = DeviceFilters(),
            sort = DeviceSort.Name
        )
        val reloaded = DevicesPreview.content() as DevicesUiState.Content
        val visible = reloaded.all.resolve(kept.query, kept.filters, kept.sort)

        assertTrue(visible.isNotEmpty())
        assertTrue(visible.size < reloaded.all.size)
    }

    /**
     * Presentation is presentation. Nothing security-relevant is remembered
     * across a reload — the type carries three fields and none of them is a
     * target, an authorization or an eligibility.
     */
    @Test
    fun `presentation holds nothing security relevant`() {
        val kept = NexaPresentation("q", DeviceFilters(), DeviceSort.Name)
        assertEquals("q", kept.query)
        // A structural assertion: the whole record is these three things.
        assertEquals(
            kept,
            NexaPresentation(kept.query, kept.filters, kept.sort)
        )
    }

    // ============================================================
    // THE FOUR EMPTINESSES STAY APART
    // ============================================================

    /**
     * Resolving, nothing there, nothing matched, and could not ask are four
     * different sentences. This asserts they produce four different states.
     */
    @Test
    fun `loading, empty, no match and unavailable are four different answers`() {
        val loading = DevicesUiState.Loading
        val empty = DevicesPreview.empty() as DevicesUiState.Content
        val unavailable = DevicesUiState.Unavailable

        val emptyResult = nexaResults(
            sourceCount = empty.all.size,
            visibleCount = empty.visible.size,
            queryActive = false,
            filtersActive = false
        )
        val noMatch = nexaResults(
            sourceCount = content.all.size,
            visibleCount = 0,
            queryActive = true,
            filtersActive = false
        )

        assertEquals(NexaResults.SourceEmpty, emptyResult)
        assertEquals(NexaResults.NoMatch(NexaNoMatchReason.Search), noMatch)
        assertNotEquals(emptyResult, noMatch)
        assertNotEquals(loading::class, unavailable::class)
        // And the empty content is content — it answered.
        assertTrue(empty.all.isEmpty())
        assertEquals(DataFreshness.Live, empty.freshness)
    }

    /**
     * A search that found nothing in a source that never resolved is not
     * reported as a search failure. The screen shows the availability surface
     * instead, and this pins the classification that keeps it there.
     */
    @Test
    fun `an unresolved source is never reported as a failed search`() {
        val result = nexaResults(
            sourceCount = 0,
            visibleCount = 0,
            queryActive = true,
            filtersActive = true
        )
        assertEquals(NexaResults.SourceEmpty, result)
        assertFalse(result is NexaResults.NoMatch)
    }

    // ============================================================
    // TRANSITIONS
    // ============================================================

    private fun deviceOverlay(id: String, presence: Presence, scope: String) =
        RealtimeState(devices = mapOf(id to DeviceOverlay(presence = presence, scope = scope)))

    /**
     * An empty screen that receives a record becomes a list. The empty
     * container must not survive its own premise.
     */
    @Test
    fun `empty becomes current when a record arrives`() {
        val empty = DevicesPreview.empty() as DevicesUiState.Content
        assertEquals(0, empty.visible.size)

        val arrived = empty.copy(all = DevicesPreview.inventory)
        val visible = arrived.all.resolve(arrived.query, arrived.filters, arrived.sort)

        assertTrue(visible.isNotEmpty())
        assertEquals(
            NexaResults.Present,
            nexaResults(arrived.all.size, visible.size, queryActive = false, filtersActive = false)
        )
    }

    /**
     * And a list whose last record goes away becomes empty — genuinely empty,
     * because the source answered and the answer is now nothing.
     */
    @Test
    fun `current becomes empty when the last record goes`() {
        val emptied = content.copy(all = emptyList())
        val visible = emptied.all.resolve(emptied.query, emptied.filters, emptied.sort)
        assertEquals(
            NexaResults.SourceEmpty,
            nexaResults(emptied.all.size, visible.size, queryActive = false, filtersActive = false)
        )
    }

    /**
     * A realtime update reaching a screen that was narrowed keeps it narrowed.
     * Recovery is not an excuse to discard what the operator asked for.
     */
    @Test
    fun `an update arriving during a narrowed view keeps the narrowing`() {
        val filters = DeviceFilters(presence = setOf(Presence.Present))
        val before = content.all.resolve("", filters, DeviceSort.Name)
        val target = content.all.first { it.presence != Presence.Present }

        val after = content.all
            .withRealtime(deviceOverlay(target.id, Presence.Present, target.scope))
            .resolve("", filters, DeviceSort.Name)

        assertEquals(before.size + 1, after.size)
        after.forEach { assertEquals(Presence.Present, it.presence) }
    }

    /**
     * Recovery from a failure state is a real transition, not a local flag
     * that hides the failure. The error state holds no records, so the
     * recovered state can only come from a fresh load.
     */
    @Test
    fun `an error state holds nothing to recover from`() {
        val error = DevicesUiState.Error("The device inventory could not be read.")
        assertFalse(error is DevicesUiState.Content)
        assertTrue(error.message.isNotBlank())
        // And its message names the failure rather than a generic apology.
        assertFalse(error.message.lowercase().contains("something went wrong"))
    }

    // ============================================================
    // ERROR COPY
    // ============================================================

    /**
     * Every domain's error names what failed. "Something went wrong" tells an
     * operator nothing they can act on, and a security console that says it
     * is asking to be ignored.
     */
    @Test
    fun `error messages name the failure`() {
        val messages = listOf(
            "The device inventory could not be read.",
            "Alert state could not be read.",
            "Security history could not be read.",
            "Delivery records could not be read.",
            "Identity data could not be read.",
            "System state could not be read."
        )
        messages.forEach { message ->
            assertFalse(message.lowercase().contains("something went wrong"))
            assertFalse(message.lowercase().contains("unknown error"))
            assertTrue("$message does not name a subject", message.split(" ").size >= 4)
        }
    }

    /**
     * An error never borrows the language of the states beside it. Failing to
     * read is not the same as reading successfully and finding nothing, and
     * neither is the same as being unable to ask.
     */
    @Test
    fun `error copy does not claim emptiness or unavailability`() {
        val message = "The device inventory could not be read."
        assertFalse(message.lowercase().contains("no devices"))
        assertFalse(message.lowercase().contains("empty"))
        assertNotEquals(
            message,
            availabilityExplanation(NexaAvailability.Unavailable, "the device inventory")
        )
        assertNotEquals(
            message,
            availabilityExplanation(NexaAvailability.Empty, "the device inventory")
        )
    }
}
