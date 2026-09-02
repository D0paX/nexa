package com.example.nexa.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Which surface the system's indicators are drawn over.
 *
 * The clock, the signal strength, the SIM indicator and the battery are drawn
 * by Android inside NEXA's window. Android will invert them, but only if it is
 * told to, and the default answer comes from the phone's night setting rather
 * than from what the app has painted. NEXA has one surface and it is light, so
 * on a phone in dark mode the platform picked white indicators and put them on
 * a near-white background.
 *
 * These hold the policy that replaced it. They are not a test of Android's
 * rendering — nothing here can check what a pixel of the clock looks like.
 * They pin the decision that feeds the window configuration, including the one
 * case that was wrong.
 */
class SystemBarsTest {

    // ============================================================
    // THE DEFECT
    // ============================================================

    /**
     * The case that broke. A phone in dark mode used to produce white
     * indicators over NEXA's light surface; the app's answer no longer moves
     * with the phone's setting at all.
     */
    @Test
    fun `a phone in dark mode does not turn NEXA's indicators white`() {
        assertEquals(SystemBarIcons.Dark, NexaSystemBars.icons())
        assertNotEquals(
            "the appearance still follows the system night setting",
            NexaSystemBars.platformDefaultIcons(systemInDarkMode = true),
            NexaSystemBars.icons()
        )
    }

    /** And on a phone in light mode the two happen to agree, as they always did. */
    @Test
    fun `a phone in light mode agrees with the platform default`() {
        assertEquals(
            NexaSystemBars.platformDefaultIcons(systemInDarkMode = false),
            NexaSystemBars.icons()
        )
    }

    // ============================================================
    // THE POLICY
    // ============================================================

    @Test
    fun `indicators contrast with the surface beneath them`() {
        assertEquals(SystemBarIcons.Dark, NexaSystemBars.iconsOver(surfaceIsLight = true))
        assertEquals(SystemBarIcons.Light, NexaSystemBars.iconsOver(surfaceIsLight = false))
    }

    /**
     * NEXA's surface is light, and the app-wide answer is derived from that
     * rather than restated — so a future dark surface changes one constant and
     * every bar follows.
     */
    @Test
    fun `the app-wide answer is derived from the declared surface`() {
        assertEquals(
            NexaSystemBars.iconsOver(NexaSystemBars.SURFACE_IS_LIGHT),
            NexaSystemBars.icons()
        )
    }

    @Test
    fun `NEXA declares a light surface`() {
        // Not decoration: there are no values-night resources and one light
        // color scheme, so anything else here would be a claim the app cannot
        // keep.
        assertEquals(true, NexaSystemBars.SURFACE_IS_LIGHT)
    }

    /** The same answer every time it is asked, on every screen. */
    @Test
    fun `the answer does not vary between screens`() {
        val answers = (1..8).map { NexaSystemBars.icons() }.distinct()
        assertEquals(listOf(SystemBarIcons.Dark), answers)
    }
}
