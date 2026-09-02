package com.example.nexa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.nexa.theme.NexaAction
import com.example.nexa.theme.NexaBorderNeutral
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaShapes
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTextSecondary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType

/**
 * A selectable filter value.
 *
 * Selection is carried by a check mark, a border and a tint together — never
 * by color alone — and is reported to assistive technology through the
 * `selected` semantic rather than being inferred from appearance.
 */
@Composable
fun NexaFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * What to say when the printed label does not read aloud well.
     *
     * "Filters · 3" is right on screen and wrong out loud — a screen reader
     * reads the separator. The typography keeps its shorthand and the
     * announcement gets a sentence.
     */
    spokenLabel: String? = null
) {
    // The pill stays 36dp so a row of chips keeps its density, but the thing
    // that receives the touch is 48dp tall. Shrinking the target to match the
    // paint would put every filter control below the minimum, and filters are
    // exactly the controls someone uses one-handed while looking at something
    // else.
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minHeight = NexaTokens.MinTouchTarget)
            // The boolean is what assistive technology filters and navigates
            // by; the phrase is what a person hears. Colour carries neither.
            // One stop, one sentence. The chip's printed label is cleared so
            // it is not a second focus stop announcing the same value; the
            // selection state travels as a semantic rather than as a tint.
            .clearAndSetSemantics {
                this.selected = selected
                contentDescription = spokenLabel ?: label
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            // No indication here. The target is 48dp and the pill is 36dp, so
            // a bounded ripple would paint a rectangle a third taller than the
            // chip, with corners the chip does not have. The press is answered
            // on the pill below instead.
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Checkbox,
                onClick = onClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingXSmall),
            modifier = Modifier
                .pressResponse(pressScale)
                .defaultMinSize(minHeight = 36.dp)
                .background(
                    color = if (selected) NexaAction.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent,
                    shape = NexaShapes.Pill
                )
                .border(
                    width = NexaTokens.BorderHairline,
                    color = if (selected) NexaAction.copy(alpha = 0.45f) else NexaBorderNeutral,
                    shape = NexaShapes.Pill
                )
                .padding(horizontal = NexaTokens.SpacingMedium, vertical = NexaTokens.SpacingSmall)
        ) {
            if (selected) {
                NexaIcon(
                    icon = NexaIcons.Acknowledge,
                    size = NexaTokens.IconSmall,
                    tint = NexaAction
                )
            }
            Text(
                text = label,
                style = NexaType.Metadata,
                color = if (selected) NexaTextPrimary else NexaTextSecondary
            )
        }
    }
}
