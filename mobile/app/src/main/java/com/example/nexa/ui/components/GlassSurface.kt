package com.example.nexa.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaShapes
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.liquidGlass

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    variant: GlassVariant = GlassVariant.Standard,
    shape: Shape = NexaShapes.Surface,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(NexaTokens.SpacingMedium),
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    // A clickable surface answers a press by compressing, not by having a
    // rectangle drawn over it. The default indication is bounded to the
    // touch rectangle rather than to this shape, so on a rounded surface its
    // corners hang outside the thing they belong to — and on a screen where a
    // list scrolls beneath a translucent bar, one drawn under that bar reads
    // as a grey box around whatever floats above it.
    val pressScale = if (onClick != null) rememberPressScale(interactionSource) else 1f

    val glassModifier = modifier
        .pressResponse(pressScale)
        .liquidGlass(variant = variant, shape = shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
        .padding(contentPadding)

    Box(
        modifier = glassModifier,
        content = content
    )
}
