package com.example.nexa.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaShapes
import com.example.nexa.theme.NexaTextMuted
import com.example.nexa.theme.NexaTextOnDark
import com.example.nexa.theme.NexaTextSecondary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType
import com.example.nexa.ui.navigation.rememberReducedMotion

/**
 * How NEXA looks while it is resolving data.
 *
 * Two treatments, for the two situations that are genuinely different:
 * a first load with nothing on screen, and a revalidation over content the
 * operator is already reading. [LoadingState] in [StateContainers] remains
 * the treatment for a surface with no list shape to hold.
 *
 * Neither borrows danger language. Loading is not a failure, and a screen
 * that looks alarming while it is merely waiting teaches an operator to
 * ignore the screens that are actually alarming.
 *
 * Neither says anything about what will arrive, either. "Reading the device
 * inventory" describes NEXA's activity; it is not a promise that devices
 * exist, which leaves the empty and unavailable surfaces free to say
 * something completely different when the answer comes.
 */

private const val PLACEHOLDER_ALPHA_LOW = 0.45f
private const val PLACEHOLDER_ALPHA_HIGH = 1f

/**
 * The loading treatment for a list, shaped like the list that is coming.
 *
 * A centred spinner on a blank screen tells an operator only that something
 * is happening somewhere. Holding the layout — a status line and a few
 * row-shaped surfaces — keeps the screen from jumping when content lands,
 * and the shape itself says "a list is being read" without a word.
 *
 * The placeholders carry no text and no count. A skeleton that guessed at
 * "loading 8 devices" would be inventing the one fact the screen does not
 * have yet, and an operator would read the number.
 */
@Composable
fun NexaListLoading(
    message: String,
    modifier: Modifier = Modifier,
    rows: Int = 4,
    /**
     * Whether the first placeholder is hero-shaped.
     *
     * The command centre leads with a posture panel rather than a row, and a
     * loading screen that holds the wrong shape makes the content jump when
     * it lands.
     */
    heroFirst: Boolean = false
) {
    val alpha = placeholderAlpha()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = NexaTokens.SpacingLarge)
            // One announcement for the whole screen. Reading out four
            // identical placeholder shapes would be noise.
            .semantics(mergeDescendants = true) { contentDescription = message }
    ) {
        Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                color = NexaTextMuted,
                strokeWidth = 2.dp,
                modifier = Modifier.size(NexaTokens.IconMedium)
            )
            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
            Text(text = message, style = NexaType.Metadata, color = NexaTextSecondary)
        }
        Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))

        repeat(rows) { index ->
            if (index > 0) Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            val hero = heroFirst && index == 0
            GlassSurface(
                variant = if (hero) GlassVariant.Hero else GlassVariant.Standard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.alpha(alpha)) {
                    if (hero) {
                        // The hero surface is charcoal, so its placeholders
                        // are drawn light. A muted-on-dark bar is invisible,
                        // and an invisible placeholder is just dead space.
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                        PlaceholderBar(widthFraction = 0.35f, height = 10.dp, onDark = true)
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                        PlaceholderBar(widthFraction = 0.60f, height = 26.dp, onDark = true)
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                        PlaceholderBar(widthFraction = 0.90f, height = 10.dp, onDark = true)
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                    } else {
                        PlaceholderBar(widthFraction = 0.55f, height = 14.dp)
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                        PlaceholderBar(widthFraction = 0.80f, height = 10.dp)
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                        PlaceholderBar(widthFraction = 0.40f, height = 10.dp)
                    }
                }
            }
        }
    }
}

/**
 * Revalidation happening over content that is already on screen.
 *
 * Small on purpose. The operator is reading a list; that NEXA is checking it
 * again is worth stating and not worth interrupting for. Replacing the list
 * with a spinner would take away the only information they currently have,
 * in order to tell them that better information is coming.
 */
@Composable
fun RefreshingIndicator(
    modifier: Modifier = Modifier,
    label: String = "Refreshing"
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = label }
    ) {
        CircularProgressIndicator(
            color = NexaTextMuted,
            strokeWidth = 2.dp,
            modifier = Modifier.size(NexaTokens.IconSmall)
        )
        Spacer(modifier = Modifier.width(NexaTokens.SpacingXSmall))
        Text(text = label, style = NexaType.Metadata, color = NexaTextMuted)
    }
}

/**
 * A slow, shallow fade — and no fade at all when the platform asks for
 * reduced motion, in which case the placeholders simply sit at full opacity.
 */
@Composable
private fun placeholderAlpha(): Float {
    if (rememberReducedMotion()) return PLACEHOLDER_ALPHA_HIGH

    val transition = rememberInfiniteTransition(label = "loading")
    return transition.animateFloat(
        initialValue = PLACEHOLDER_ALPHA_LOW,
        targetValue = PLACEHOLDER_ALPHA_HIGH,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loadingAlpha"
    ).value
}

@Composable
private fun PlaceholderBar(widthFraction: Float, height: Dp, onDark: Boolean = false) {
    val tint = if (onDark) NexaTextOnDark else NexaTextMuted
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(color = tint.copy(alpha = 0.18f), shape = NexaShapes.Pill)
    )
}
