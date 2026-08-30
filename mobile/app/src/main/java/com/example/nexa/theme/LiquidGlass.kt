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
        GlassVariant.Hero -> NexaStrongGlassSurface.copy(alpha = 0.8f)
        GlassVariant.Destructive -> NexaDanger.copy(alpha = 0.1f)
        GlassVariant.Interactive -> NexaGlassSurface.copy(alpha = 0.5f)
    }

    val elevation = when (variant) {
        GlassVariant.Hero -> NexaTokens.ElevationHero
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
    val borderBrush = Brush.verticalGradient(
        colors = listOf(borderColor, borderColor.copy(alpha = 0.0f))
    )

    this
        .shadow(
            elevation = elevation,
            shape = shape,
            spotColor = NexaGlassHighlight,
            ambientColor = NexaBackground
        )
        .background(surfaceColor, shape)
        .border(width = 0.5.dp, brush = borderBrush, shape = shape)
}
