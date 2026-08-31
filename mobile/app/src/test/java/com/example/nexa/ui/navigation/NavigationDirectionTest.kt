package com.example.nexa.ui.navigation

import com.example.nexa.ActionConfirmation
import com.example.nexa.AlertDetail
import com.example.nexa.Alerts
import com.example.nexa.Audit
import com.example.nexa.AuditDetail
import com.example.nexa.DeviceDetail
import com.example.nexa.Devices
import com.example.nexa.Identities
import com.example.nexa.IdentityDetail
import com.example.nexa.Overview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Navigation direction is ordinary logic and is tested as such.
 *
 * No animation is asserted here — pixel assertions on a transition are
 * brittle and would not tell us the thing that actually matters, which is
 * that the application agrees with itself about which way it is travelling.
 */
class NavigationDirectionTest {

    // ============================================================
    // ROOT ORDERING
    // ============================================================

    @Test
    fun `root destinations are ordered overview devices alerts audit`() {
        assertEquals(0, navigationIndex(Overview))
        assertEquals(1, navigationIndex(Devices))
        assertEquals(2, navigationIndex(Alerts))
        assertEquals(3, navigationIndex(Audit))
    }

    @Test
    fun `non-root destinations have no root index`() {
        assertNull(navigationIndex(DeviceDetail("00:11:22:33:44:55")))
        assertNull(navigationIndex(AlertDetail("ALRT-1")))
        assertNull(navigationIndex(AuditDetail("EVT-1")))
        assertNull(navigationIndex(IdentityDetail("TID-1")))
        assertNull(navigationIndex(Identities))
        assertNull(navigationIndex(ActionConfirmation("ctx-1")))
        assertNull(navigationIndex(null))
    }

    @Test
    fun `tiers separate roots details and confirmations`() {
        assertEquals(NavigationTier.Root, navigationTier(Overview))
        assertEquals(NavigationTier.Root, navigationTier(Audit))
        assertEquals(NavigationTier.Detail, navigationTier(DeviceDetail("mac")))
        assertEquals(NavigationTier.Detail, navigationTier(AuditDetail("EVT-1")))
        assertEquals(NavigationTier.Detail, navigationTier(Identities))
        assertEquals(NavigationTier.Modal, navigationTier(ActionConfirmation("ctx-1")))
    }

    // ============================================================
    // ROOT MOTION
    // ============================================================

    @Test
    fun `moving to a higher-index root is forward`() {
        assertEquals(NavigationDirection.RootForward, navigationDirection(Overview, Devices))
        assertEquals(NavigationDirection.RootForward, navigationDirection(Devices, Alerts))
        assertEquals(NavigationDirection.RootForward, navigationDirection(Alerts, Audit))
    }

    @Test
    fun `moving to a lower-index root is reverse`() {
        assertEquals(NavigationDirection.RootReverse, navigationDirection(Audit, Alerts))
        assertEquals(NavigationDirection.RootReverse, navigationDirection(Alerts, Devices))
        assertEquals(NavigationDirection.RootReverse, navigationDirection(Devices, Overview))
    }

    @Test
    fun `skipping tabs keeps the direction of travel`() {
        assertEquals(NavigationDirection.RootForward, navigationDirection(Overview, Audit))
        assertEquals(NavigationDirection.RootReverse, navigationDirection(Audit, Overview))
    }

    /**
     * Root direction follows the index, not the gesture. Landing on Alerts
     * from Devices travels forward whether it came from a tap or from a back.
     */
    @Test
    fun `root direction ignores whether the stack shrank`() {
        assertEquals(
            NavigationDirection.RootForward,
            navigationDirection(Devices, Alerts, popping = true)
        )
    }

    // ============================================================
    // NO MOTION
    // ============================================================

    @Test
    fun `the same destination does not move`() {
        assertEquals(NavigationDirection.None, navigationDirection(Audit, Audit))
        assertEquals(
            NavigationDirection.None,
            navigationDirection(AuditDetail("EVT-1"), AuditDetail("EVT-1"))
        )
    }

    @Test
    fun `first composition does not animate`() {
        assertEquals(NavigationDirection.None, navigationDirection(null, Overview))
        assertEquals(NavigationDirection.None, navigationDirection(Overview, null))
    }

    // ============================================================
    // DRILL-DOWN
    // ============================================================

    @Test
    fun `entering a detail surface is forward`() {
        assertEquals(
            NavigationDirection.DrillForward,
            navigationDirection(Devices, DeviceDetail("00:1A:2B:3C:4D:5E"))
        )
        assertEquals(
            NavigationDirection.DrillForward,
            navigationDirection(Alerts, AlertDetail("ALRT-1092"))
        )
        assertEquals(
            NavigationDirection.DrillForward,
            navigationDirection(Audit, AuditDetail("EVT-4401"))
        )
    }

    @Test
    fun `leaving a detail surface is back`() {
        assertEquals(
            NavigationDirection.DrillBack,
            navigationDirection(DeviceDetail("00:1A:2B:3C:4D:5E"), Devices)
        )
        assertEquals(
            NavigationDirection.DrillBack,
            navigationDirection(AlertDetail("ALRT-1092"), Alerts)
        )
        assertEquals(
            NavigationDirection.DrillBack,
            navigationDirection(AuditDetail("EVT-4401"), Audit)
        )
    }

    /**
     * Detail to detail is the one case depth cannot resolve on its own:
     * opening an identity from the inventory looks exactly like returning to
     * the inventory from an identity. The shrinking stack tells them apart.
     */
    @Test
    fun `detail to detail uses the stack to tell push from pop`() {
        assertEquals(
            NavigationDirection.DrillForward,
            navigationDirection(Identities, IdentityDetail("TID-88F1"))
        )
        assertEquals(
            NavigationDirection.DrillBack,
            navigationDirection(IdentityDetail("TID-88F1"), Identities, popping = true)
        )
    }

    @Test
    fun `audit detail can travel sideways into another detail`() {
        assertEquals(
            NavigationDirection.DrillForward,
            navigationDirection(AuditDetail("EVT-4402"), AlertDetail("ALRT-1092"))
        )
    }

    // ============================================================
    // CONFIRMATION
    // ============================================================

    @Test
    fun `entering a confirmation is a modal move whatever it came from`() {
        assertEquals(
            NavigationDirection.ModalForward,
            navigationDirection(DeviceDetail("mac"), ActionConfirmation("ctx-1"))
        )
        assertEquals(
            NavigationDirection.ModalForward,
            navigationDirection(AlertDetail("ALRT-1"), ActionConfirmation("ctx-1"))
        )
        assertEquals(
            NavigationDirection.ModalForward,
            navigationDirection(IdentityDetail("TID-1"), ActionConfirmation("ctx-1"))
        )
    }

    @Test
    fun `leaving a confirmation returns to the source`() {
        assertEquals(
            NavigationDirection.ModalBack,
            navigationDirection(ActionConfirmation("ctx-1"), DeviceDetail("mac"))
        )
        assertEquals(
            NavigationDirection.ModalBack,
            navigationDirection(ActionConfirmation("ctx-1"), Alerts)
        )
    }

    // ============================================================
    // FORWARD FLAG
    // ============================================================

    @Test
    fun `forward directions are forward and reverse ones are not`() {
        assertTrue(NavigationDirection.RootForward.isForward)
        assertTrue(NavigationDirection.DrillForward.isForward)
        assertTrue(NavigationDirection.ModalForward.isForward)
        assertFalse(NavigationDirection.RootReverse.isForward)
        assertFalse(NavigationDirection.DrillBack.isForward)
        assertFalse(NavigationDirection.ModalBack.isForward)
        assertFalse(NavigationDirection.None.isForward)
    }

    // ============================================================
    // RESOLVER
    // ============================================================

    @Test
    fun `the resolver follows a full session`() {
        val resolver = NavigationDirectionResolver()

        assertEquals(NavigationDirection.None, resolver.resolve(Overview, 1))
        assertEquals(NavigationDirection.RootForward, resolver.resolve(Devices, 2))
        assertEquals(
            NavigationDirection.DrillForward,
            resolver.resolve(DeviceDetail("00:1A:2B:3C:4D:5E"), 3)
        )
        assertEquals(
            NavigationDirection.ModalForward,
            resolver.resolve(ActionConfirmation("ctx-1"), 4)
        )
        assertEquals(
            NavigationDirection.ModalBack,
            resolver.resolve(DeviceDetail("00:1A:2B:3C:4D:5E"), 3)
        )
        assertEquals(NavigationDirection.DrillBack, resolver.resolve(Devices, 2))
        assertEquals(NavigationDirection.RootForward, resolver.resolve(Alerts, 3))
        assertEquals(NavigationDirection.RootForward, resolver.resolve(Audit, 4))
        assertEquals(NavigationDirection.DrillForward, resolver.resolve(AuditDetail("EVT-4401"), 5))
        assertEquals(NavigationDirection.DrillBack, resolver.resolve(Audit, 4))
        assertEquals(NavigationDirection.RootReverse, resolver.resolve(Alerts, 5))
    }

    @Test
    fun `the resolver reports no motion when the destination repeats`() {
        val resolver = NavigationDirectionResolver()
        resolver.resolve(Devices, 1)
        assertEquals(NavigationDirection.None, resolver.resolve(Devices, 1))
    }

    // ============================================================
    // TOKENS
    // ============================================================

    @Test
    fun `standard navigation stays inside the interaction budget`() {
        listOf(
            NavigationDirection.RootForward,
            NavigationDirection.RootReverse,
            NavigationDirection.DrillForward,
            NavigationDirection.DrillBack,
            NavigationDirection.ModalForward,
            NavigationDirection.ModalBack
        ).forEach { direction ->
            val duration = navigationDuration(direction, reducedMotion = false)
            assertTrue(
                "$direction lasts ${duration}ms",
                duration in 150..280
            )
        }
    }

    @Test
    fun `each plane moves differently from the others`() {
        val root = navigationDuration(NavigationDirection.RootForward, false)
        val detail = navigationDuration(NavigationDirection.DrillForward, false)
        val modal = navigationDuration(NavigationDirection.ModalForward, false)
        assertTrue(root < detail)
        assertTrue(detail < modal)

        val rootShift = navigationDisplacement(NavigationDirection.RootForward, false)
        val detailShift = navigationDisplacement(NavigationDirection.DrillForward, false)
        val modalShift = navigationDisplacement(NavigationDirection.ModalForward, false)
        assertTrue(rootShift < detailShift)
        assertTrue(detailShift < modalShift)
    }

    @Test
    fun `displacement never becomes a full-width slide`() {
        listOf(
            NavigationDirection.RootForward,
            NavigationDirection.DrillForward,
            NavigationDirection.ModalForward
        ).forEach { direction ->
            assertTrue(navigationDisplacement(direction, false) <= 0.25f)
        }
    }

    // ============================================================
    // REDUCED MOTION
    // ============================================================

    @Test
    fun `reduced motion shortens every transition`() {
        NavigationDirection.entries.forEach { direction ->
            val normal = navigationDuration(direction, reducedMotion = false)
            val reduced = navigationDuration(direction, reducedMotion = true)
            assertTrue("$direction", reduced < normal)
        }
    }

    @Test
    fun `reduced motion collapses displacement without removing it`() {
        NavigationDirection.entries.forEach { direction ->
            val reduced = navigationDisplacement(direction, reducedMotion = true)
            assertTrue("$direction", reduced > 0f)
            assertTrue("$direction", reduced < navigationDisplacement(direction, false))
        }
    }

    @Test
    fun `zeroed animation scales mean reduced motion`() {
        assertTrue(isReducedMotion(animatorScale = 0f, transitionScale = 0f))
        assertTrue(isReducedMotion(animatorScale = 0f, transitionScale = 1f))
        assertTrue(isReducedMotion(animatorScale = 1f, transitionScale = 0f))
    }

    /** A user who merely sped animations up has not asked for them to stop. */
    @Test
    fun `a faster animation scale is not reduced motion`() {
        assertFalse(isReducedMotion(animatorScale = 0.5f, transitionScale = 0.5f))
        assertFalse(isReducedMotion(animatorScale = 1f, transitionScale = 1f))
        assertFalse(isReducedMotion(animatorScale = 2f, transitionScale = 2f))
    }

    @Test
    fun `every direction produces a transition`() {
        NavigationDirection.entries.forEach { direction ->
            assertNotNull(navigationTransform(direction, reducedMotion = false))
            assertNotNull(navigationTransform(direction, reducedMotion = true))
        }
    }
}
