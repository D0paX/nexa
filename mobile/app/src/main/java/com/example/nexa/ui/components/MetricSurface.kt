package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaTextMuted
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTextSecondary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType

/**
 * A headline number with its context.
 *
 * [caption] carries the breakdown that makes the number decision-useful — a
 * device count means little without knowing how many are offline or
 * quarantined. It is optional: metrics that are complete on their own stay
 * a single figure.
 */
@Composable
fun MetricSurface(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = NexaTextPrimary,
    caption: String? = null,
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
            Text(text = title, style = NexaType.Metadata, color = NexaTextSecondary)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Text(
                text = value,
                style = NexaType.Display,
                color = valueColor
            )
            if (caption != null) {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                Text(text = caption, style = NexaType.Metadata, color = NexaTextMuted)
            }
        }
    }
}
