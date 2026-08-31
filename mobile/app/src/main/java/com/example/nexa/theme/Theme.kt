package com.example.nexa.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val NexaColorScheme = lightColorScheme(
    primary = NexaAction,
    secondary = NexaTextSecondary,
    background = NexaBackground,
    surface = NexaElevatedBackground,
    surfaceVariant = NexaGlassSurface,
    onPrimary = NexaTextOnDark,
    onSecondary = NexaTextPrimary,
    onBackground = NexaTextPrimary,
    onSurface = NexaTextPrimary,
    onSurfaceVariant = NexaTextPrimary,
    error = NexaDanger,
    onError = NexaTextOnDark
)

@Composable
fun NexaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexaColorScheme,
        typography = Typography,
        content = content
    )
}
