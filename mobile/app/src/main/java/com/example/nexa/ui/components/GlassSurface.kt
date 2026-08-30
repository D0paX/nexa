package com.example.nexa.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.liquidGlass

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    variant: GlassVariant = GlassVariant.Standard,
    shape: Shape = RoundedCornerShape(NexaTokens.CornerRadiusMedium),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val glassModifier = modifier
        .liquidGlass(variant = variant, shape = shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(NexaTokens.SpacingMedium)

    Box(
        modifier = glassModifier,
        content = content
    )
}
