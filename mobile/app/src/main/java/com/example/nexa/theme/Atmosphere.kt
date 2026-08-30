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
    // Subtle radial gradient from center-top to simulate depth
    val atmosphereBrush = Brush.radialGradient(
        colors = listOf(
            Color(0xFF151820), // Slightly lighter tonal center
            NexaBackground     // Complete darkness at edges
        ),
        radius = 1500f
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(atmosphereBrush)
    ) {
        content()
    }
}
