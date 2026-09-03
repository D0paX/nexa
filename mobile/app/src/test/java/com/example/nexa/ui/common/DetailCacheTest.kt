package com.example.nexa.ui.common

import com.example.nexa.ui.devices.DeviceDetailUiState
import com.example.nexa.ui.devices.DeviceDetailViewModel
import com.example.nexa.ui.devices.DevicesPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A cached detail record must not outlive the condition it was read under.
 *
 * Found on a physical device during Phase 5.26: with the offline condition
 * active, the inventory said "Last confirmed 6 min ago" while a device detail
 * opened before the change still said "2m ago" — and the action confirmation
 * prepared from that record reported its observation as CURRENT. The record
 * was correct when it was loaded and stale afterwards, and nothing invalidated
 * it, because the cache was keyed on the identifier alone.
 *
 * That is the precise failure the freshness vocabulary exists to prevent: an
 * enforcement decision taken against a snapshot the app could no longer stand
 * behind, presented as though it could.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailCacheTest {

    private val dispatcher = StandardTestDispatcher()
    private val mac = DevicesPreview.inventory.first().mac

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        DegradedScenario.activate(null)
    }

    @After
    fun tearDown() {
        DegradedScenario.activate(null)
        Dispatchers.resetMain()
    }

    private fun DeviceDetailUiState.freshness(): DataFreshness =
        (this as DeviceDetailUiState.Content).data.device.freshness

    private fun DeviceDetailUiState.lastSeen(): String =
        (this as DeviceDetailUiState.Content).data.device.lastSeenLabel

    // ============================================================
    // THE DEFECT
    // ============================================================

    @Test
    fun `a record loaded while current is re-read once the condition changes`() = runTest(dispatcher) {
        val model = DeviceDetailViewModel()

        model.load(mac)
        advanceUntilIdle()
        assertEquals(DataFreshness.Live, model.state.value.freshness())

        DegradedScenario.activate(DegradedScenario.Scenario.Offline)
        model.load(mac)
        advanceUntilIdle()

        assertTrue(
            "the cached record survived the loss of connectivity",
            model.state.value.freshness() is DataFreshness.Stale
        )
    }

    /**
     * The record the action path reads is the same one the screen renders, so
     * ageing it is what makes a prepared context honest rather than confident.
     */
    @Test
    fun `the aged record is what an action would be prepared from`() = runTest(dispatcher) {
        val model = DeviceDetailViewModel()
        model.load(mac)
        advanceUntilIdle()

        DegradedScenario.activate(DegradedScenario.Scenario.Offline)
        model.load(mac)
        advanceUntilIdle()

        val content = model.state.value as DeviceDetailUiState.Content
        // Both copies move together: the one shown and the one prepared from.
        assertEquals(content.data.device.freshness, content.data.record.freshness)
        assertEquals(content.data.device.lastSeenLabel, content.data.record.lastObservedLabel)
        assertTrue(
            "the record still claimed a recent observation",
            content.data.record.freshness !is DataFreshness.Live
        )
    }

    @Test
    fun `an unreadable condition reports unknown rather than an invented age`() = runTest(dispatcher) {
        val model = DeviceDetailViewModel()
        model.load(mac)
        advanceUntilIdle()

        DegradedScenario.activate(DegradedScenario.Scenario.Degraded)
        model.load(mac)
        advanceUntilIdle()

        assertEquals(DataFreshness.Unknown, model.state.value.freshness())
        assertEquals("unknown", model.state.value.lastSeen())
    }

    /** And back again — recovering is a condition change like any other. */
    @Test
    fun `returning to current re-reads the record`() = runTest(dispatcher) {
        val model = DeviceDetailViewModel()

        DegradedScenario.activate(DegradedScenario.Scenario.Offline)
        model.load(mac)
        advanceUntilIdle()
        assertTrue(model.state.value.freshness() is DataFreshness.Stale)

        DegradedScenario.activate(null)
        model.load(mac)
        advanceUntilIdle()
        assertEquals(DataFreshness.Live, model.state.value.freshness())
    }

    // ============================================================
    // WITHOUT LOSING WHAT THE CACHE WAS FOR
    // ============================================================

    /**
     * The cache exists so that returning to a record does not re-run the load
     * and flash a spinner over content that is still true. An unchanged
     * condition must still hit it.
     */
    @Test
    fun `an unchanged condition still serves the cached record`() = runTest(dispatcher) {
        val model = DeviceDetailViewModel()
        model.load(mac)
        advanceUntilIdle()
        val first = model.state.value

        model.load(mac)
        // No advance: a cache hit produces no reload, so nothing is pending.
        assertTrue(
            "re-entering the same record started a fresh load",
            model.state.value === first
        )
    }

    @Test
    fun `a different record is always loaded`() = runTest(dispatcher) {
        val model = DeviceDetailViewModel()
        model.load(mac)
        advanceUntilIdle()

        val other = DevicesPreview.inventory.first { it.mac != mac }.mac
        model.load(other)
        advanceUntilIdle()

        assertEquals(
            other,
            (model.state.value as DeviceDetailUiState.Content).data.device.mac
        )
    }

    // ============================================================
    // WITHOUT WAITING TO BE ASKED
    // ============================================================

    /**
     * The screen that was open when connectivity went is the one that matters.
     * Re-reading only on re-entry would leave the record on screen unchanged
     * for as long as someone kept looking at it.
     */
    @Test
    fun `an open record is re-read without being loaded again`() = runTest(dispatcher) {
        val model = DeviceDetailViewModel()
        model.load(mac)
        advanceUntilIdle()
        assertEquals(DataFreshness.Live, model.state.value.freshness())

        // No second load() call — only the condition changes.
        DegradedScenario.activate(DegradedScenario.Scenario.Offline)
        advanceUntilIdle()

        assertTrue(
            "the record on screen kept its original freshness",
            model.state.value.freshness() is DataFreshness.Stale
        )
    }

    @Test
    fun `nothing is loaded for a screen that never opened a record`() = runTest(dispatcher) {
        val model = DeviceDetailViewModel()
        DegradedScenario.activate(DegradedScenario.Scenario.Offline)
        advanceUntilIdle()
        assertTrue(model.state.value is DeviceDetailUiState.Loading)
    }
}
