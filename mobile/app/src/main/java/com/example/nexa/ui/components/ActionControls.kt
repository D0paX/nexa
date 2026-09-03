package com.example.nexa.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.nexa.theme.NexaAction
import com.example.nexa.theme.NexaBorderNeutral
import com.example.nexa.theme.NexaDanger
import com.example.nexa.theme.NexaDisabled
import com.example.nexa.theme.NexaShapes
import com.example.nexa.theme.NexaSimulation
import com.example.nexa.theme.NexaTextMuted
import com.example.nexa.theme.NexaTextOnDark
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType

/** What a filled action means, which decides how it looks. */
enum class NexaButtonVariant {
    /** The expected forward action. Carries the brand. */
    Primary,

    /** Changes enforcement state. Carries semantic danger, not brand. */
    Destructive,

    /**
     * Requests a simulated action that will not mutate anything.
     *
     * Deliberately not red: an operator glancing at the control must be able
     * to tell a simulation from a live-fire command without reading it.
     */
    Simulation
}

/**
 * A filled NEXA action.
 *
 * Never smaller than the minimum touch target, whatever its label. While
 * [loading] it is inert and shows a spinner in the icon slot, so a
 * long-running enforcement call cannot be double-submitted by an impatient
 * tap.
 */
@Composable
fun NexaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: NexaButtonVariant = NexaButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    val containerColor = when (variant) {
        NexaButtonVariant.Primary -> NexaAction
        NexaButtonVariant.Destructive -> NexaDanger
        NexaButtonVariant.Simulation -> NexaSimulation
    }

    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = NexaTextOnDark,
            disabledContainerColor = NexaDisabled,
            disabledContentColor = NexaTextMuted
        ),
        shape = NexaShapes.Control,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = NexaTokens.MinTouchTarget)
    ) {
        ButtonLeading(icon = icon, loading = loading)
        Text(text.uppercase(), style = NexaType.Button)
    }
}

/**
 * A secondary NEXA action: acknowledge, cancel, dismiss.
 *
 * Quiet by design — it must never compete with the destructive action it
 * usually sits beneath.
 */
@Composable
fun NexaOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    color: Color = NexaTextPrimary,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            disabledContentColor = NexaTextMuted
        ),
        border = BorderStroke(NexaTokens.BorderHairline, NexaBorderNeutral),
        shape = NexaShapes.Control,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = NexaTokens.MinTouchTarget)
    ) {
        ButtonLeading(icon = icon, loading = loading)
        Text(text.uppercase(), style = NexaType.Button)
    }
}

/**
 * The leading slot shared by both button forms, so icon and spinner occupy
 * the same space and the label does not shift when work starts.
 */
@Composable
private fun ButtonLeading(icon: ImageVector?, loading: Boolean) {
    when {
        loading -> {
            CircularProgressIndicator(
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(NexaTokens.IconMedium)
                    // The button's own label already announces the action.
                    .clearAndSetSemantics { }
            )
            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
        }
        icon != null -> {
            NexaIcon(icon = icon, size = NexaTokens.IconMedium, tint = LocalContentColor.current)
            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
        }
    }
}
