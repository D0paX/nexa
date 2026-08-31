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

    /** The press response shared by every interactive NEXA surface. */
    fun pressedSurface(isPressed: Boolean): Color =
        if (isPressed) NexaInk.copy(alpha = 0.08f) else Color.Transparent
}
