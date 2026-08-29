package com.example.nexa.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NexaColorScheme = darkColorScheme(
    primary = NexaPrimary,
    secondary = NexaSecondary,
    background = NexaBackground,
    surface = NexaSurface,
    surfaceVariant = NexaSurfaceVariant,
    onPrimary = NexaBackground,
    onSecondary = NexaTextPrimary,
    onBackground = NexaTextPrimary,
    onSurface = NexaTextPrimary,
    error = NexaDanger,
    onError = NexaBackground
)

@Composable
fun NexaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexaColorScheme,
        typography = Typography,
        content = content
    )
}
