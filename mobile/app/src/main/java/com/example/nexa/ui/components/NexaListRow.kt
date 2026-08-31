package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaTextMuted
import com.example.nexa.theme.NexaTextSecondary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType

/**
 * The NEXA list row.
 *
 * One structure behind every list in the product — devices, alerts, audit
 * events, actions — with fixed slots so rows stay comparable across screens:
 *
 *     [leading state icon]  title
 *                           technical identifier      [trailing / timestamp]
 *
 * Emphasis is expressed by the glass variant and the title treatment, not by
 * a different layout per screen.
 */
@Composable
fun NexaListRow(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    variant: GlassVariant = GlassVariant.Standard,
    leadingIcon: ImageVector? = null,
    leadingTint: Color = NexaTextSecondary,
    leadingContentDescription: String? = null,
    titleStyle: TextStyle = NexaType.Body,
    titleColor: Color = NexaTextSecondary,
    technical: String? = null,
    secondary: String? = null,
    timestamp: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    GlassSurface(
        variant = variant,
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                NexaIcon(
                    icon = leadingIcon,
                    contentDescription = leadingContentDescription,
                    size = NexaTokens.IconMedium,
                    tint = leadingTint
                )
                Spacer(modifier = Modifier.width(NexaTokens.SpacingMedium))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = titleStyle, color = titleColor)
                if (secondary != null) {
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingHairline))
                    Text(text = secondary, style = NexaType.BodySecondary, color = NexaTextMuted)
                }
                if (technical != null) {
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingHairline))
                    TechnicalValue(text = technical)
                }
            }

            if (timestamp != null) {
                Text(text = timestamp, style = NexaType.Metadata, color = NexaTextMuted)
            }
            trailing?.invoke()
        }
    }
}
