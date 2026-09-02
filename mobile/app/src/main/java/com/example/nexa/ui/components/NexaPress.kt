package com.example.nexa.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.example.nexa.theme.NexaMotion
import com.example.nexa.ui.navigation.rememberReducedMotion

/**
 * How a NEXA control answers a press.
 *
 * The platform default is a bounded ripple: a translucent rectangle drawn over
 * the whole touch target. Two things make that wrong here. The target is
 * deliberately larger than the paint — a 36dp chip inside a 48dp target, a
 * navigation item inside a full-height row — so the rectangle is bigger than
 * the control it belongs to and has corners the control does not. And it is
 * drawn *behind* nothing: on a screen where a translucent surface floats over
 * a scrolling list, a ripple under it reads as a grey box that appeared out of
 * nowhere around whatever was touched.
 *
 * So the response happens on the control itself. It compresses by two percent
 * while held and returns when released — the same two percent the navigation
 * uses when a surface emerges, so the app has one idea about how far something
 * moves to acknowledge you. Nothing new is painted, which means there is no
 * shape to disagree with the shape of the control.
 *
 * The touch target is unchanged. It stays its full size and stays invisible:
 * what a person can hit and what they can see are separate questions, and only
 * the second one is answered here.
 */
@Composable
fun rememberPressScale(interactionSource: InteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    // Someone who has asked the system for less movement is not asking for
    // less feedback in general — but a two percent squeeze is movement, and
    // movement is the thing they turned off. The press still registers, is
    // still announced, and still acts; it simply does not animate.
    val reducedMotion = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) NexaMotion.PressedScale else 1f,
        animationSpec = NexaMotion.fast(),
        label = "press"
    )
    return scale
}

/**
 * Applies [scale] without affecting layout.
 *
 * A graphics layer, not a size change: the control keeps the space it
 * occupies, so nothing around it moves while it is held.
 */
fun Modifier.pressResponse(scale: Float): Modifier =
    if (scale == 1f) this else graphicsLayer(scaleX = scale, scaleY = scale)
