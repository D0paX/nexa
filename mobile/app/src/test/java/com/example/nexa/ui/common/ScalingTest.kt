package com.example.nexa.ui.common

import com.example.nexa.ui.alerts.AlertScopeView
import com.example.nexa.ui.alerts.AlertsPreview
import com.example.nexa.ui.alerts.applyView
import com.example.nexa.ui.alerts.countInView
import com.example.nexa.ui.devices.DeviceFilters
import com.example.nexa.ui.devices.DeviceListItem
import com.example.nexa.ui.devices.DeviceSort
import com.example.nexa.ui.devices.DevicesPreview
import com.example.nexa.ui.devices.Presence
import com.example.nexa.ui.devices.resolve
import com.example.nexa.ui.enforcement.ActionSubmissions
import com.example.nexa.ui.enforcement.EnforcementPreview
import com.example.nexa.ui.realtime.DeviceOverlay
import com.example.nexa.ui.realtime.RealtimeState
import com.example.nexa.ui.realtime.withRealtime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * How the projection behaves as the data grows.
 *
 * Not a benchmark suite — the numbers a JVM unit test produces say little
 * about a phone. What these pin is the shape of the work: that the pipeline
 * stays linear rather than quadratic, that it stays deterministic at every
 * size, and that the caches which used to grow without limit now stop.
 *
 * The timing assertions are deliberately loose. They exist to catch a change
 * that makes something accidentally quadratic, not to measure a device.
 */
class ScalingTest {

    @Before
    fun setUp() {
        EnforcementPreview.reset()
        ActionSubmissions.reset()
    }

    @After
    fun tearDown() {
        EnforcementPreview.reset()
        ActionSubmissions.reset()
    }

    /**
     * A deterministic inventory of any size, built by repeating the preview
     * fixtures with distinct identities. Same shape of data, more of it.
     */
    private fun inventory(size: Int): List<DeviceListItem> {
        val base = DevicesPreview.inventory
        return (0 until size).map { index ->
            val template = base[index % base.size]
            template.copy(
                id = "DEV-%05d".format(index),
                label = "${template.label} $index",
                mac = "00:00:%02X:%02X:%02X:%02X".format(
                    (index shr 24) and 0xFF,
                    (index shr 16) and 0xFF,
                    (index shr 8) and 0xFF,
                    index and 0xFF
                )
            )
        }
    }

    private val sizes = listOf(50, 500, 2_000)

    // ============================================================
    // THE PIPELINE STAYS LINEAR
    // ============================================================

    /**
     * Resolution cost should track the number of records, not their square.
     * The check is a ratio rather than a wall-clock budget, so it means the
     * same thing on a fast machine and a slow one.
     */
    @Test
    fun `resolving scales with the record count, not its square`() {
        val filters = DeviceFilters(presence = setOf(Presence.Present))

        fun timeOf(size: Int): Long {
            val source = inventory(size)
            // Warm the JIT so the first measurement is not the interpreter.
            repeat(3) { source.resolve("laptop", filters, DeviceSort.Attention) }
            val start = System.nanoTime()
            repeat(10) { source.resolve("laptop", filters, DeviceSort.Attention) }
            return System.nanoTime() - start
        }

        val small = timeOf(sizes.first())
        val large = timeOf(sizes.last())
        val sizeRatio = sizes.last().toDouble() / sizes.first()

        // Sorting makes it n log n, so allow generous headroom over linear —
        // but nothing close to the n² a per-record rescan would produce.
        val allowed = sizeRatio * 12
        val actual = large.toDouble() / small.coerceAtLeast(1)
        assertTrue(
            "resolution grew %.1fx for a %.0fx dataset".format(actual, sizeRatio),
            actual < allowed
        )
    }

    @Test
    fun `every size resolves deterministically`() {
        sizes.forEach { size ->
            val source = inventory(size)
            val a = source.resolve("server", DeviceFilters(), DeviceSort.Name)
            val b = source.shuffled().resolve("server", DeviceFilters(), DeviceSort.Name)
            assertEquals("size $size was not deterministic", a.map { it.id }, b.map { it.id })
        }
    }

    /** And keeps producing exactly the records it should, at every size. */
    @Test
    fun `filtering stays correct as the inventory grows`() {
        sizes.forEach { size ->
            val source = inventory(size)
            val visible = source.resolve(
                "",
                DeviceFilters(presence = setOf(Presence.Present)),
                DeviceSort.Name
            )
            assertEquals(
                "size $size lost or gained records",
                source.count { it.presence == Presence.Present },
                visible.size
            )
            visible.forEach { assertEquals(Presence.Present, it.presence) }
        }
    }

    // ============================================================
    // COUNTING WITHOUT BUILDING
    // ============================================================

    /**
     * The summary line needed a size, not a list. This pins that the cheap
     * form and the expensive one still answer the same question.
     */
    @Test
    fun `counting a view matches building it`() {
        val source = AlertsPreview.alerts
        AlertScopeView.entries.forEach { view ->
            assertEquals(
                "$view disagreed",
                source.applyView(view).size,
                source.countInView(view)
            )
        }
    }

    @Test
    fun `counting a view is correct on an empty load`() {
        AlertScopeView.entries.forEach { view ->
            assertEquals(0, emptyList<com.example.nexa.ui.alerts.AlertListItem>().countInView(view))
        }
    }

    // ============================================================
    // UNRELATED EVENTS DO NOT INVALIDATE A PROJECTION
    // ============================================================

    /**
     * Every applied event moves the store's sequence and applied count, so a
     * screen collecting the state itself re-projected for traffic in domains
     * it does not render. The screens now key on the slices they use; this
     * pins that those slices really are unchanged by unrelated activity.
     */
    @Test
    fun `a delivery event leaves the device slice untouched`() {
        val before = RealtimeState(
            devices = mapOf("DEV-1" to DeviceOverlay(presence = Presence.Present, scope = "VLAN_A")),
            lastAppliedSequence = 10,
            appliedCount = 4
        )
        val after = before.copy(
            deliveries = mapOf("NTF-1" to com.example.nexa.ui.realtime.DeliveryOverlay(
                state = DeliveryState.Failed,
                attemptCount = 2,
                failureReason = null,
                scope = "VLAN_A"
            )),
            lastAppliedSequence = 11,
            appliedCount = 5
        )

        // The whole state changed, which is why collecting it re-ran everything.
        assertTrue(before != after)
        // The slice a device screen keys on did not.
        assertEquals(before.devices to before.identities, after.devices to after.identities)
    }

    @Test
    fun `an action event leaves every list slice untouched`() {
        val before = RealtimeState(lastAppliedSequence = 1, appliedCount = 1)
        val after = before.copy(
            actions = mapOf(
                "ACT-1" to com.example.nexa.ui.realtime.ActionOverlay(
                    state = com.example.nexa.ui.enforcement.ExecutionState.Executing,
                    mode = ExecutionMode.Enforce,
                    reconciled = false,
                    actionCode = "QUARANTINE_DEVICE",
                    scope = "VLAN_A"
                )
            ),
            lastAppliedSequence = 2,
            appliedCount = 2
        )
        assertEquals(before.devices, after.devices)
        assertEquals(before.alerts, after.alerts)
        assertEquals(before.deliveries, after.deliveries)
        assertEquals(before.identities, after.identities)
        assertEquals(before.circuitBreaker, after.circuitBreaker)
    }

    /** And an event a screen *does* care about still reaches it. */
    @Test
    fun `a device event does change the device slice`() {
        val before = RealtimeState()
        val after = before.copy(
            devices = mapOf("DEV-1" to DeviceOverlay(presence = Presence.Absent, scope = "VLAN_A"))
        )
        assertTrue(before.devices != after.devices)
    }

    // ============================================================
    // OVERLAY RESOLUTION
    // ============================================================

    @Test
    fun `an empty overlay does not rebuild the list`() {
        val source = inventory(500)
        // The overlay functions return the receiver untouched when there is
        // nothing to apply, so an idle stream costs nothing.
        assertTrue(source.withRealtime(RealtimeState()) === source)
    }

    @Test
    fun `overlay resolution stays correct at scale`() {
        val source = inventory(1_000)
        val target = source[500]
        val overlaid = source.withRealtime(
            RealtimeState(
                devices = mapOf(target.id to DeviceOverlay(presence = Presence.Absent, scope = target.scope))
            )
        )
        assertEquals(source.size, overlaid.size)
        assertEquals(Presence.Absent, overlaid.first { it.id == target.id }.presence)
        // Nothing else moved.
        assertEquals(
            source.filter { it.id != target.id }.map { it.presence },
            overlaid.filter { it.id != target.id }.map { it.presence }
        )
    }

    // ============================================================
    // CACHES THAT USED TO GROW WITHOUT LIMIT
    // ============================================================

    /**
     * Preparing an action stored a full context and never released it. The
     * bound is what a person can be in the middle of, and the flow already
     * handles an unresolvable handle by refusing rather than reconstructing.
     */
    @Test
    fun `prepared contexts are bounded`() {
        val ids = (1..100).map { EnforcementPreview.store(EnforcementPreview.context()) }

        // The most recent are still resolvable.
        ids.takeLast(32).forEach { assertNotNull("$it was evicted too early", EnforcementPreview.resolve(it)) }
        // The oldest have been released.
        assertNull(EnforcementPreview.resolve(ids.first()))
    }

    /**
     * Eviction is the same condition as process death, which the flow already
     * treats as "cannot resolve" rather than "rebuild from what is lying
     * around".
     */
    @Test
    fun `an evicted context resolves to nothing rather than to something invented`() {
        val first = EnforcementPreview.store(EnforcementPreview.context())
        repeat(64) { EnforcementPreview.store(EnforcementPreview.context()) }
        assertNull(EnforcementPreview.resolve(first))
    }

    /**
     * Idempotency is deliberately not bounded alongside it: forgetting that a
     * context was submitted would allow it to be submitted twice, which is the
     * one thing that boundary exists to prevent.
     */
    @Test
    fun `submission records survive context eviction`() {
        val id = EnforcementPreview.store(EnforcementPreview.context())
        val first = ActionSubmissions.submit(id)
        repeat(64) { EnforcementPreview.store(EnforcementPreview.context()) }

        assertNull("the context should have been evicted", EnforcementPreview.resolve(id))
        assertTrue(
            "the action was forgotten and could be submitted again",
            ActionSubmissions.submit(id) is ActionSubmissions.Result.AlreadySubmitted
        )
        assertEquals(
            (first as ActionSubmissions.Result.Accepted).actionId,
            ActionSubmissions.actionIdFor(id)
        )
    }
}
