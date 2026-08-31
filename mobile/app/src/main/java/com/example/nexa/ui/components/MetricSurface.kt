package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTextSecondary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.Typography

@Composable
fun MetricSurface(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = NexaTextPrimary,
    glassVariant: GlassVariant? = null,
    onClick: (() -> Unit)? = null
) {
    val resolvedVariant = glassVariant ?: if (onClick != null) GlassVariant.Interactive else GlassVariant.Standard

    GlassSurface(
        variant = resolvedVariant,
        onClick = onClick,
        modifier = modifier
    ) {
        Column {
            Text(text = title, style = Typography.labelMedium, color = NexaTextSecondary)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Text(
                text = value,
                style = Typography.displayLarge,
                color = valueColor
            )
        }
    }
}
