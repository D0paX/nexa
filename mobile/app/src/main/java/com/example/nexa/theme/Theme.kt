package com.example.nexa.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NexaColorScheme = darkColorScheme(
    primary = NexaAction,
    secondary = NexaTextSecondary,
    background = NexaBackground,
    surface = NexaElevatedBackground,
    surfaceVariant = NexaGlassSurface,
    onPrimary = NexaBackground,
    onSecondary = NexaTextPrimary,
    onBackground = NexaTextPrimary,
    onSurface = NexaTextPrimary,
    onSurfaceVariant = NexaTextPrimary,
    error = NexaDanger,
    onError = NexaTextPrimary
)

@Composable
fun NexaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexaColorScheme,
        typography = Typography,
        content = content
    )
}
