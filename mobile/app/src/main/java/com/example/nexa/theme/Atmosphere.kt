package com.example.nexa.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun NexaAtmosphere(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // Spatial tonal gradient from center to simulate depth and light field
    val atmosphereBrush = Brush.radialGradient(
        colors = listOf(
            NexaAtmosphereShade, // Cool blue light
            NexaAtmosphereCore,  // Muted violet core
            NexaBackground       // Deep slate cyan edges
        ),
        radius = 2000f
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(atmosphereBrush)
    ) {
        content()
    }
}
