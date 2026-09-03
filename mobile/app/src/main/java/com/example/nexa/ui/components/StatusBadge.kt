package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.nexa.theme.NexaShapes
import com.example.nexa.theme.NexaStatus
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType
import com.example.nexa.theme.style

/**
 * A security-state badge.
 *
 * Prefer the [NexaStatus] overload: it derives the label, icon and
 * surface-correct color from the state vocabulary, so a state cannot be
 * shown one way on a light card and another way on a charcoal anchor.
 */
@Composable
fun StatusBadge(
    status: NexaStatus,
    modifier: Modifier = Modifier,
    onDarkSurface: Boolean = false,
    label: String? = null,
    showIcon: Boolean = true
) {
    val style = status.style
    StatusBadge(
        text = label ?: style.label,
        color = style.color(onDarkSurface),
        modifier = modifier,
        icon = if (showIcon) style.icon else null
    )
}

/**
 * Explicit-color form, for the few places where the color is already decided
 * by a caller that resolved it from the state vocabulary itself.
 *
 * The label always carries the meaning; the icon gives the state a shape as
 * well, so posture survives color-blind viewing. The icon is decorative to a
 * screen reader — the label already says it.
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
        shape = NexaShapes.Control,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingXSmall),
            modifier = Modifier.padding(
                horizontal = NexaTokens.SpacingSmall,
                vertical = NexaTokens.SpacingXSmall
            )
        ) {
            if (icon != null) {
                NexaIcon(icon = icon, size = NexaTokens.IconSmall, tint = color)
            }
            Text(
                text = text.uppercase(),
                style = NexaType.Status,
                color = color,
                // A badge is a single word about state, and it is never the
                // thing that should give way. Squeezed by a long label beside
                // it on a narrow screen at a large font, "AUDIT ONLY" broke
                // one letter per line — a vertical column spelling out the
                // most important fact on the card. It keeps its own width now
                // and whatever shares its row yields instead.
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
