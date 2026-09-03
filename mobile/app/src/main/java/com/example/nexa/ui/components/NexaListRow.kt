package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.nexa.ui.common.nexaRowAction

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
    /**
     * What activating the row does, for a screen reader.
     *
     * Present whenever [onClick] is. Without it the row is a clickable
     * surface whose several text children are each their own stop, so the
     * contents are read once as the row and again as its parts.
     */
    actionLabel: String? = null,
    /**
     * What the trailing slot means, in words.
     *
     * The slot is a composable, so the row cannot read it. A badge that says
     * CRITICAL is part of what this row is, and without this the sentence the
     * row speaks would be missing the most important word in it.
     */
    trailingDescription: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    // The row composes its own sentence out of the slots it was handed, so
    // there is no second place for these facts to live and drift from. The
    // leading icon is left out whenever a secondary line exists: on a device
    // row the icon means "Present" and the secondary line already says it, and
    // hearing it twice per row is how a list becomes unusable.
    val spoken = remember(
        title, leadingContentDescription, secondary, trailingDescription, technical, timestamp
    ) {
        listOfNotNull(
            title,
            if (secondary == null) leadingContentDescription else null,
            secondary,
            trailingDescription,
            technical,
            timestamp
        ).joinToString(", ") { it.replace(" · ", ", ") }
    }

    // Every row is one stop, whether or not it is an action: a row of facts
    // read out as five separate fragments is as hard to follow as a clickable
    // one. The action label is attached only when there is an action.
    //
    // Safe only because these rows have no interactive children. A row that
    // grew its own controls would need to opt out, or it would swallow them.
    val rowSemantics = Modifier.nexaRowAction(spoken = spoken, actionLabel = actionLabel)

    GlassSurface(
        variant = variant,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().then(rowSemantics)
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
