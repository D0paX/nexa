package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.Typography

/**
 * A security-state badge.
 *
 * The label always carries the meaning; the optional icon gives the state a
 * shape as well, so posture is readable without relying on color perception.
 * The icon is decorative to a screen reader — the label already says it.
 */
@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(NexaTokens.CornerRadiusSmall),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingXSmall),
            modifier = Modifier.padding(horizontal = NexaTokens.SpacingSmall, vertical = 4.dp)
        ) {
            if (icon != null) {
                NexaIcon(icon = icon, size = NexaTokens.IconSmall, tint = color)
            }
            Text(
                text = text.uppercase(),
                style = Typography.labelMedium,
                color = color
            )
        }
    }
}
