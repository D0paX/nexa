package com.example.nexa.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GlassVariant {
    Standard,
    Strong,
    Hero,
    Destructive,
    Interactive
}

fun Modifier.liquidGlass(
    variant: GlassVariant = GlassVariant.Standard,
    shape: Shape = RoundedCornerShape(NexaTokens.CornerRadiusMedium)
): Modifier = composed {
    val surfaceColor = when (variant) {
        GlassVariant.Standard -> NexaGlassSurface
        GlassVariant.Strong -> NexaStrongGlassSurface
        GlassVariant.Hero -> NexaHeroGlassSurface
        GlassVariant.Destructive -> NexaDanger.copy(alpha = 0.15f)
        GlassVariant.Interactive -> NexaGlassSurface.copy(alpha = 0.55f)
    }

    // Inner subtle glow/gradient to simulate glass volume
    val innerGlowBrush = Brush.verticalGradient(
        colors = listOf(
            surfaceColor,
            surfaceColor.copy(alpha = surfaceColor.alpha * 0.5f)
        )
    )

    val elevation = when (variant) {
        GlassVariant.Hero -> NexaTokens.ElevationHero
        GlassVariant.Strong -> NexaTokens.ElevationStrong
        GlassVariant.Destructive -> NexaTokens.ElevationDestructive
        else -> NexaTokens.ElevationStandard
    }

    val borderColor = when (variant) {
        GlassVariant.Destructive -> NexaDanger.copy(alpha = 0.3f)
        GlassVariant.Hero -> NexaGlassBorder.copy(alpha = 0.2f)
        GlassVariant.Interactive -> NexaAction.copy(alpha = 0.3f)
        else -> NexaGlassBorder
    }
    
    // Vertical gradient for the border to simulate top-down lighting reflection
    val borderBrush = Brush.linearGradient(
        colors = listOf(borderColor, borderColor.copy(alpha = 0.05f))
    )

    this
        .shadow(
            elevation = elevation,
            shape = shape,
            spotColor = NexaGlassHighlight,
            ambientColor = NexaGlassHighlight
        )
        .background(innerGlowBrush, shape)
        .border(width = 1.dp, brush = borderBrush, shape = shape)
}
