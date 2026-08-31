package com.example.nexa.ui.navigation

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the operator has asked the platform to stop animating.
 *
 * Android expresses this by zeroing the system animation scales — what the
 * accessibility setting "Remove animations" does. NEXA honors it by shortening
 * transitions and collapsing their displacement rather than by removing them:
 * a screen that changes with no transition at all is harder to follow, not
 * easier, and the state change itself is information the operator needs.
 */

/** Pure so the policy is testable without a device. */
fun isReducedMotion(animatorScale: Float, transitionScale: Float): Boolean =
    animatorScale <= REDUCED_THRESHOLD || transitionScale <= REDUCED_THRESHOLD

private const val REDUCED_THRESHOLD = 0.01f

fun readReducedMotion(resolver: ContentResolver): Boolean = isReducedMotion(
    animatorScale = Settings.Global.getFloat(
        resolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ),
    transitionScale = Settings.Global.getFloat(
        resolver,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        1f
    )
)

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { readReducedMotion(context.contentResolver) }
}
