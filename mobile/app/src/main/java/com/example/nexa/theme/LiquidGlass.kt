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
import androidx.compose.ui.unit.dp

enum class GlassVariant {
    Standard,
    Strong,
    Hero,
    Destructive,
    Interactive,
    Selected
}

/**
 * NEXA Liquid Glass.
 *
 * One material, six densities of light, forming the surface hierarchy every
 * screen builds on:
 *
 *     Atmosphere → Standard → Interactive → Strong → Selected → Hero/Destructive
 *
 * Every surface is lit from above: a brighter upper face, a faintly tinted
 * lower face, a bright hairline on the lit edge and a grounded hairline
 * beneath it. Depth comes from light and elevation — never from blur, and
 * never at the cost of the text on top of it.
 */
fun Modifier.liquidGlass(
    variant: GlassVariant = GlassVariant.Standard,
    shape: Shape = NexaShapes.Surface
): Modifier = composed {
    // Surface: vertical fall-off from the lit upper face to the tinted lower face.
    val surfaceBrush = when (variant) {
        GlassVariant.Standard -> Brush.verticalGradient(
            listOf(NexaGlassSurface, NexaGlassSurfaceTint)
        )
        GlassVariant.Strong -> Brush.verticalGradient(
            listOf(NexaStrongGlassSurface, NexaStrongGlassSurfaceTint)
        )
        GlassVariant.Interactive -> Brush.verticalGradient(
            listOf(NexaInteractiveGlassSurface, NexaInteractiveGlassSurfaceTint)
        )
        GlassVariant.Selected -> Brush.verticalGradient(
            listOf(NexaSelectedGlassSurface, NexaSelectedGlassSurfaceTint)
        )
        GlassVariant.Hero -> Brush.verticalGradient(
            listOf(NexaHeroGlassSurface, NexaHeroGlassSurfaceDeep)
        )
        GlassVariant.Destructive -> Brush.verticalGradient(
            listOf(NexaDestructiveSurface, NexaDestructiveSurfaceDeep)
        )
    }

    // Boundary: bright where the light lands, grounded where it does not.
    val borderBrush = when (variant) {
        GlassVariant.Hero -> Brush.verticalGradient(
            listOf(NexaHeroHighlight, NexaHeroBorder)
        )
        // Destructive earns a controlled red edge — the surface states the stakes.
        GlassVariant.Destructive -> Brush.verticalGradient(
            listOf(NexaHeroHighlight, NexaDestructiveBorder)
        )
        // The only ordinary-scale surface allowed a red boundary: it marks selection.
        GlassVariant.Selected -> Brush.verticalGradient(
            listOf(NexaSelectedHighlight, NexaSelectedBorder)
        )
        else -> Brush.verticalGradient(
            listOf(NexaGlassHighlight, NexaGlassBorder)
        )
    }

    val elevation = when (variant) {
        GlassVariant.Hero -> NexaTokens.ElevationHero
        GlassVariant.Destructive -> NexaTokens.ElevationModal
        GlassVariant.Strong -> NexaTokens.ElevationElevated
        GlassVariant.Selected -> NexaTokens.ElevationElevated
        GlassVariant.Interactive -> NexaTokens.ElevationRaised
        GlassVariant.Standard -> NexaTokens.ElevationFloating
    }

    // Soft, ink-tinted separation. Dark anchors cast slightly more weight.
    val spotAlpha = when (variant) {
        GlassVariant.Hero, GlassVariant.Destructive -> 0.20f
        else -> 0.10f
    }

    this
        .shadow(
            elevation = elevation,
            shape = shape,
            spotColor = NexaShadow.copy(alpha = spotAlpha),
            ambientColor = NexaShadow.copy(alpha = spotAlpha * 0.5f)
        )
        .background(surfaceBrush, shape)
        .border(width = NexaTokens.BorderHairline, brush = borderBrush, shape = shape)
}

/**
 * Brand accent wash for surfaces that carry a NEXA signal (active navigation,
 * destructive weight). Kept low enough that red never becomes the surface.
 */
fun accentWash(alpha: Float): Color = NexaRedWash.copy(alpha = alpha)
