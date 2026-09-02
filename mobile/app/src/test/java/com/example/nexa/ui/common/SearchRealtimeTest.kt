package com.example.nexa.ui.common

import com.example.nexa.ui.devices.DeviceFilters
import com.example.nexa.ui.devices.DeviceSort
import com.example.nexa.ui.devices.DevicesPreview
import com.example.nexa.ui.devices.DevicesUiState
import com.example.nexa.ui.devices.Presence
import com.example.nexa.ui.devices.resolve
import com.example.nexa.ui.realtime.DeviceOverlay
import com.example.nexa.ui.realtime.RealtimeState
import com.example.nexa.ui.realtime.withRealtime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What happens to a narrowed list when the world changes underneath it.
 *
 * The pipeline is re-derived from the overlaid snapshot rather than patched
 * in place, which is what makes these properties hold: a record that stops
 * matching leaves, one that starts matching arrives, the count follows the
 * list, and the order does not churn.
 *
 * These exercise the same functions the view models call — overlay, then
 * resolve — so a change to either path is caught here rather than on a
 * device.
 */
class SearchRealtimeTest {

    private val source = DevicesPreview.inventory

    private fun presenceOverlay(deviceId: String, presence: Presence, scope: String) =
        RealtimeState(devices = mapOf(deviceId to DeviceOverlay(presence = presence, scope = scope)))

    private fun labelOverlay(deviceId: String, lastSeen: String, scope: String) =
        RealtimeState(
            devices = mapOf(deviceId to DeviceOverlay(lastSeenLabel = lastSeen, scope = scope))
        )

    // ============================================================
    // RECORDS ENTER AND LEAVE
    // ============================================================

    /**
     * The scenario the checkpoint names explicitly: a filter on Presence =
     * Present, and a device that goes away.
     */
    @Test
    fun `a record that stops matching leaves the filtered list`() {
        val filters = DeviceFilters(presence = setOf(Presence.Present))
        val before = source.resolve("", filters, DeviceSort.Name)
        assertTrue("the fixture no longer exercises this", before.isNotEmpty())

        val target = before.first()
        val after = source
            .withRealtime(presenceOverlay(target.id, Presence.Absent, target.scope))
            .resolve("", filters, DeviceSort.Name)

        assertFalse("the device stayed visible after going absent", after.any { it.id == target.id })
        assertEquals(before.size - 1, after.size)
    }

    @Test
    fun `a record that starts matching enters the filtered list`() {
        val filters = DeviceFilters(presence = setOf(Presence.Present))
        val absent = source.first { it.presence != Presence.Present }
        val before = source.resolve("", filters, DeviceSort.Name)
        assertFalse(before.any { it.id == absent.id })

        val after = source
            .withRealtime(presenceOverlay(absent.id, Presence.Present, absent.scope))
            .resolve("", filters, DeviceSort.Name)

        assertTrue("the device did not appear after becoming present", after.any { it.id == absent.id })
        assertEquals(before.size + 1, after.size)
    }

    /**
     * A record that changes while a search is active is re-evaluated against
     * that search, not grandfathered in by having matched before.
     */
    @Test
    fun `an active search is re-applied to updated records`() {
        val target = source.first { it.ip != null }
        val query = "10.77.77.77"
        assertTrue(source.resolve(query, DeviceFilters(), DeviceSort.Name).isEmpty())

        val after = source
            .withRealtime(
                RealtimeState(
                    devices = mapOf(
                        target.id to DeviceOverlay(observedAddress = query, scope = target.scope)
                    )
                )
            )
            .resolve(query, DeviceFilters(), DeviceSort.Name)

        assertEquals(listOf(target.id), after.map { it.id })
    }

    @Test
    fun `an active search survives an unrelated update`() {
        val filters = DeviceFilters()
        val before = source.resolve("laptop", filters, DeviceSort.Name)
        assertTrue(before.isNotEmpty())

        val unrelated = source.last()
        val after = source
            .withRealtime(labelOverlay(unrelated.id, "just now", unrelated.scope))
            .resolve("laptop", filters, DeviceSort.Name)

        // The query still governs; the update did not widen the result set.
        assertEquals(before.map { it.id }.toSet(), after.map { it.id }.toSet())
    }

    // ============================================================
    // COUNTS FOLLOW THE LIST
    // ============================================================

    /**
     * The count is derived from the same resolved list rather than cached, so
     * it cannot lag behind an update.
     */
    @Test
    fun `the count recomputes with the list`() {
        val filters = DeviceFilters(presence = setOf(Presence.Present))
        val before = source.resolve("", filters, DeviceSort.Name)
        val beforeLabel = resultCountLabel(before.size, source.size, "device")

        val target = before.first()
        val after = source
            .withRealtime(presenceOverlay(target.id, Presence.Absent, target.scope))
            .resolve("", filters, DeviceSort.Name)
        val afterLabel = resultCountLabel(after.size, source.size, "device")

        assertEquals(before.size - 1, after.size)
        assertFalse("the count did not move with the list", beforeLabel == afterLabel)
        assertTrue(afterLabel.startsWith("${after.size} "))
    }

    // ============================================================
    // NO DUPLICATES, NO CHURN
    // ============================================================

    /**
     * The overlay is a projection, not an append. Applying the same event
     * twice produces the same list — which is what stops a redelivered or
     * replayed event from doubling a row.
     */
    @Test
    fun `a duplicate update does not duplicate a row`() {
        val target = source.first()
        val overlay = presenceOverlay(target.id, Presence.Absent, target.scope)

        val once = source.withRealtime(overlay).resolve("", DeviceFilters(), DeviceSort.Name)
        val twice = source.withRealtime(overlay).withRealtime(overlay)
            .resolve("", DeviceFilters(), DeviceSort.Name)

        assertEquals(once.map { it.id }, twice.map { it.id })
        assertEquals(once.size, once.map { it.id }.toSet().size)
    }

    @Test
    fun `ordering stays deterministic across an update`() {
        val target = source.first()
        val overlay = presenceOverlay(target.id, Presence.Absent, target.scope)
        val a = source.withRealtime(overlay).resolve("", DeviceFilters(), DeviceSort.Attention)
        val b = source.shuffled().withRealtime(overlay).resolve("", DeviceFilters(), DeviceSort.Attention)
        assertEquals(a.map { it.id }, b.map { it.id })
    }

    @Test
    fun `an update does not silently clear the filter set`() {
        val filters = DeviceFilters(
            presence = setOf(Presence.Present),
            trust = setOf(TrustState.Trusted)
        )
        val target = source.first()
        val after = source
            .withRealtime(labelOverlay(target.id, "just now", target.scope))
            .resolve("", filters, DeviceSort.Name)

        after.forEach {
            assertEquals(Presence.Present, it.presence)
            assertEquals(TrustState.Trusted, it.trust)
        }
        assertTrue(after.size < source.size)
    }

    // ============================================================
    // THE STATE A SCREEN CARRIES ACROSS NAVIGATION
    // ============================================================

    /**
     * Search, filters and sort live on the screen's state rather than in the
     * composable, which is what lets them survive a trip to a detail screen
     * and back. This models the re-projection a view model performs on
     * return: the controls are untouched and only the resolved list is
     * recomputed.
     */
    @Test
    fun `re-projecting preserves search, filters and sort`() {
        val content = DevicesPreview.content() as DevicesUiState.Content
        val configured = content.copy(
            query = "build",
            filters = DeviceFilters(trust = setOf(TrustState.Trusted)),
            sort = DeviceSort.Name
        )

        // What the view model does when it comes back to a screen: overlay
        // the newest data and resolve again with the state it already held.
        val target = configured.all.first()
        val live = configured.all.withRealtime(labelOverlay(target.id, "just now", target.scope))
        val reprojected = configured.copy(
            all = live,
            visible = live.resolve(configured.query, configured.filters, configured.sort)
        )

        assertEquals("build", reprojected.query)
        assertEquals(setOf(TrustState.Trusted), reprojected.filters.trust)
        assertEquals(DeviceSort.Name, reprojected.sort)
        assertEquals(
            live.resolve("build", configured.filters, DeviceSort.Name).map { it.id },
            reprojected.visible.map { it.id }
        )
    }

    /** Clearing one control leaves the other exactly where it was. */
    @Test
    fun `clearing filters keeps the query and clearing the query keeps the filters`() {
        val content = DevicesPreview.content() as DevicesUiState.Content
        val both = content.copy(
            query = "build",
            filters = DeviceFilters(trust = setOf(TrustState.Trusted)),
            sort = DeviceSort.Name
        )

        val filtersCleared = both.copy(filters = DeviceFilters())
        assertEquals("build", filtersCleared.query)
        assertFalse(filtersCleared.filters.isActive)

        val queryCleared = both.copy(query = "")
        assertEquals("", queryCleared.query)
        assertEquals(setOf(TrustState.Trusted), queryCleared.filters.trust)

        // And neither clear touched the sort.
        assertEquals(DeviceSort.Name, filtersCleared.sort)
        assertEquals(DeviceSort.Name, queryCleared.sort)
    }
}
