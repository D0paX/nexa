package com.example.nexa.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color

/**
 * NEXA motion.
 *
 * Three durations, two easings. Motion here exists to make a state change
 * legible — never to entertain. A security tool that bounces is a security
 * tool nobody trusts.
 */
object NexaMotion {
    /** Pressed states and other immediate feedback. */
    const val DurationFast = 120

    /** Selection, elevation and surface changes. */
    const val DurationStandard = 220

    /** Reserved for content that genuinely needs to be followed by the eye. */
    const val DurationSlow = 360

    /** Responsive: leaves quickly, settles gently. */
    val EasingStandard = FastOutSlowInEasing

    /** For things entering the screen. */
    val EasingEnter = LinearOutSlowInEasing

    fun <T> fast(): AnimationSpec<T> = tween(durationMillis = DurationFast, easing = EasingStandard)

    fun <T> standard(): AnimationSpec<T> = tween(durationMillis = DurationStandard, easing = EasingStandard)

    // ============================================================
    // NAVIGATION
    //
    // Three families, one for each spatial plane. Every screen transition in
    // the application draws its duration and its displacement from here, so
    // "how far forward feels" is a single decision rather than a per-screen
    // guess. Displacements are fractions of the container width, not fixed
    // distances, so the motion is the same gesture on any screen size.
    // ============================================================

    /** Root tabs sit side by side: the shortest, lightest move. */
    const val DurationRoot = 200

    /** A detail surface coming into focus in front of its root. */
    const val DurationDetail = 240

    /** A confirmation taking the foreground. The longest allowed. */
    const val DurationModal = 270

    /** Reduced-motion: fast enough to read as a change, short enough not to travel. */
    const val DurationReduced = 90

    /** Horizontal travel, as a fraction of the container width. */
    const val DisplacementRoot = 0.10f
    const val DisplacementDetail = 0.16f
    const val DisplacementModal = 0.20f
    const val DisplacementReduced = 0.02f

    /**
     * How much a surface emerges from behind the plane in front of it.
     *
     * Two percent. Enough that a detail surface reads as arriving rather than
     * sliding past, and far too little to be a zoom.
     */
    const val EmergenceScale = 0.98f

    /**
     * How far an interactive surface compresses while it is held.
     *
     * The same two percent [EmergenceScale] uses, so acknowledgement and
     * arrival speak with one voice. It replaces the platform's bounded ripple
     * for surfaces whose touch target is larger than their paint, where a
     * rectangle the size of the target is a shape nothing on screen has.
     */
    const val PressedScale = 0.98f

    /** The press response shared by every interactive NEXA surface. */
    fun pressedSurface(isPressed: Boolean): Color =
        if (isPressed) NexaInk.copy(alpha = 0.08f) else Color.Transparent
}
