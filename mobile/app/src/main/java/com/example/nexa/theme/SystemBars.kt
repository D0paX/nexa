package com.example.nexa.theme

/**
 * Which way Android should draw the clock, the battery and the rest of its own
 * indicators while NEXA is on screen.
 */
enum class SystemBarIcons {
    /** Charcoal indicators, for a light surface underneath. */
    Dark,

    /** White indicators, for a dark surface underneath. */
    Light
}

/**
 * Who decides how the system's own indicators are drawn.
 *
 * Android draws the clock, the signal strength, the SIM indicator and the
 * battery inside NEXA's window, over whatever NEXA has painted there. It will
 * invert them on request, but it has to be told — and the question it answers
 * by default is the wrong one.
 *
 * `enableEdgeToEdge()` with no arguments resolves the appearance from the
 * *system's* night setting. That is right for an app that follows the system
 * theme and wrong for one that does not: NEXA draws the same light surface
 * whatever the phone is set to, so on a phone in dark mode the platform chose
 * white indicators and put them on a near-white background. The clock was
 * legible on a light-mode device and invisible on a dark-mode one, which is
 * why it survived every check on the emulator.
 *
 * The rule here is the one that was missing: the appearance follows the
 * surface NEXA actually paints, and nothing else.
 */
object NexaSystemBars {

    /**
     * NEXA has one surface and it is light.
     *
     * There is no dark variant — no `values-night` resources, one
     * [lightColorScheme][androidx.compose.material3.lightColorScheme], and an
     * atmosphere built around light glass over a pale canvas. When that stops
     * being true this constant is the single place that has to change, and
     * every bar follows it.
     */
    const val SURFACE_IS_LIGHT = true

    /** Indicators are chosen to contrast with what is behind them. */
    fun iconsOver(surfaceIsLight: Boolean): SystemBarIcons =
        if (surfaceIsLight) SystemBarIcons.Dark else SystemBarIcons.Light

    /** What NEXA asks Android for, on every screen, in every system theme. */
    fun icons(): SystemBarIcons = iconsOver(SURFACE_IS_LIGHT)

    /**
     * What the platform picks when it is left to decide.
     *
     * Not used to configure anything — it exists so the tests can state the
     * difference between the two policies rather than merely restating the
     * one NEXA uses. The case where they disagree is the defect.
     */
    fun platformDefaultIcons(systemInDarkMode: Boolean): SystemBarIcons =
        if (systemInDarkMode) SystemBarIcons.Light else SystemBarIcons.Dark
}
