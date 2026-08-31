package com.example.nexa.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The environment behind every NEXA surface.
 *
 * A light room, not a flat canvas: illumination gathers toward the upper
 * body of the screen and falls off into shaded edges, with a faint warm
 * light and an opposing cool depth. Low-frequency by design — it should
 * read as space, never as a background effect.
 */
@Composable
fun NexaAtmosphere(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Environmental base: lit toward the top, shaded at the lower edge.
                drawRect(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to NexaCanvasLight,
                            0.55f to NexaCanvas,
                            1.0f to NexaCanvasEdge
                        )
                    )
                )

                // Faint warm light, upper right — brand warmth below the threshold of notice.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            NexaAtmosphereWarm.copy(alpha = 0.55f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.08f),
                        radius = size.maxDimension * 0.70f
                    )
                )

                // Opposing cool depth, lower left — gives the floor of the space.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            NexaAtmosphereCool.copy(alpha = 0.50f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.08f, size.height * 0.88f),
                        radius = size.maxDimension * 0.75f
                    )
                )
            }
    ) {
        content()
    }
}
