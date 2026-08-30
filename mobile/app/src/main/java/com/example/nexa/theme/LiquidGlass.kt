package com.example.nexa.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.liquidGlass(
    strong: Boolean = false,
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 4.dp
): Modifier {
    val surfaceColor = if (strong) NexaStrongGlassSurface else NexaGlassSurface
    
    return this
        .shadow(elevation, shape, spotColor = NexaGlassHighlight, ambientColor = NexaBackground)
        .background(surfaceColor, shape)
        .border(0.5.dp, NexaGlassBorder, shape)
}
