package com.example.nexa.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.example.nexa.theme.NexaMotion
import kotlin.math.roundToInt

/**
 * How a [NavigationDirection] looks.
 *
 * One function, three families, no per-screen animation anywhere else in the
 * application. The direction has already been decided as data; this only
 * dresses it.
 *
 * The families differ in what they are trying to say:
 *
 *   Root   — horizontal travel along a row of peers. No depth, no scale.
 *   Detail — a surface arriving in front of the one that opened it, so it
 *            emerges very slightly as well as moving.
 *   Modal  — the same gesture with more weight, for a surface where the
 *            operator commits to something.
 */

/** Duration for a direction, in milliseconds. Every value comes from tokens. */
fun navigationDuration(direction: NavigationDirection, reducedMotion: Boolean): Int {
    if (reducedMotion) return NexaMotion.DurationReduced
    return when (direction.tier) {
        NavigationTier.Root -> NexaMotion.DurationRoot
        NavigationTier.Detail -> NexaMotion.DurationDetail
        NavigationTier.Modal -> NexaMotion.DurationModal
    }
}

/** Horizontal travel as a fraction of container width. */
fun navigationDisplacement(direction: NavigationDirection, reducedMotion: Boolean): Float {
    if (reducedMotion) return NexaMotion.DisplacementReduced
    return when (direction.tier) {
        NavigationTier.Root -> NexaMotion.DisplacementRoot
        NavigationTier.Detail -> NexaMotion.DisplacementDetail
        NavigationTier.Modal -> NexaMotion.DisplacementModal
    }
}

/**
 * Whether the surface emerges as well as travels.
 *
 * Only the planes that have depth. Root tabs are peers — giving them a scale
 * would claim a spatial relationship they do not have.
 */
private fun usesEmergence(direction: NavigationDirection, reducedMotion: Boolean): Boolean =
    !reducedMotion && direction.tier != NavigationTier.Root

/**
 * The transition for a resolved direction.
 *
 * The incoming surface travels the full displacement; the outgoing one travels
 * a fraction of it when it is merely being covered, and the full distance when
 * it is the surface actually being dismissed. That asymmetry is what makes a
 * drill-down read as "this arrived on top" and a back read as "this left".
 */
fun navigationTransform(
    direction: NavigationDirection,
    reducedMotion: Boolean
): ContentTransform {
    if (direction == NavigationDirection.None) {
        return ContentTransform(
            targetContentEnter = EnterTransition.None,
            initialContentExit = ExitTransition.None,
            targetContentZIndex = 0f,
            sizeTransform = SizeTransform(clip = false)
        )
    }

    val duration = navigationDuration(direction, reducedMotion)
    val displacement = navigationDisplacement(direction, reducedMotion)
    val forward = direction.isForward
    val emergence = usesEmergence(direction, reducedMotion)

    val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(
        durationMillis = duration,
        easing = NexaMotion.EasingStandard
    )
    val enterFade = tween<Float>(durationMillis = duration, easing = NexaMotion.EasingEnter)
    // The outgoing surface clears early so the two never sit on top of each
    // other at half opacity for long — NEXA's surfaces are translucent, and a
    // slow crossfade between two of them is unreadable.
    val exitFade = tween<Float>(durationMillis = duration / 2, easing = NexaMotion.EasingStandard)
    val scaleSpec = tween<Float>(durationMillis = duration, easing = NexaMotion.EasingStandard)

    // Covering a surface moves it a little; dismissing one moves it all the way.
    val exitTravel = if (forward) COVERED_TRAVEL else 1f

    val enter = slideInHorizontally(animationSpec = slideSpec) { width ->
        val offset = (width * displacement).roundToInt()
        if (forward) offset else -offset
    } + fadeIn(animationSpec = enterFade) +
        if (emergence) {
            scaleIn(animationSpec = scaleSpec, initialScale = NexaMotion.EmergenceScale)
        } else {
            EnterTransition.None
        }

    val exit = slideOutHorizontally(animationSpec = slideSpec) { width ->
        val offset = (width * displacement * exitTravel).roundToInt()
        if (forward) -offset else offset
    } + fadeOut(animationSpec = exitFade) +
        if (emergence && forward) {
            // Forward only: the surface being covered settles back a touch.
            // On the way out it simply leaves — receding as it goes would read
            // as a second surface arriving.
            scaleOut(animationSpec = scaleSpec, targetScale = NexaMotion.EmergenceScale)
        } else {
            ExitTransition.None
        }

    return ContentTransform(
        targetContentEnter = enter,
        initialContentExit = exit,
        // Forward, the arriving surface sits in front of the one it covers;
        // going back, the returning surface sits behind the one leaving. That
        // stacking is what makes the two read as opposite motions rather than
        // as the same slide played twice.
        targetContentZIndex = if (forward) 1f else 0f,
        sizeTransform = SizeTransform(clip = false)
    )
}

/** How far a merely-covered surface travels, relative to the incoming one. */
private const val COVERED_TRAVEL = 0.5f
