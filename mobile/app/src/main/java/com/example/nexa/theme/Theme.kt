package com.example.nexa.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The NEXA theme.
 *
 * NEXA components read the design system directly, so this scheme exists to
 * make any Material component that slips through — a ripple, a text cursor,
 * a platform sheet — land on NEXA colors instead of Material defaults. It is
 * a safety net, not the source of truth.
 */
private val NexaColorScheme = lightColorScheme(
    primary = NexaAction,
    onPrimary = NexaTextOnDark,
    secondary = NexaTextSecondary,
    onSecondary = NexaTextOnDark,
    background = NexaCanvas,
    onBackground = NexaTextPrimary,
    surface = NexaElevatedBackground,
    onSurface = NexaTextPrimary,
    surfaceVariant = NexaGlassSurface,
    onSurfaceVariant = NexaTextSecondary,
    outline = NexaBorderNeutral,
    error = NexaDanger,
    onError = NexaTextOnDark
)

private val NexaMaterialShapes = Shapes(
    extraSmall = NexaShapes.Control,
    small = NexaShapes.Control,
    medium = NexaShapes.Surface,
    large = NexaShapes.Dialog,
    extraLarge = NexaShapes.Dialog
)

@Composable
fun NexaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexaColorScheme,
        typography = Typography,
        shapes = NexaMaterialShapes,
        content = content
    )
}
