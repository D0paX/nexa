package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaDanger
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaTextOnDark
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType

/**
 * The confirmation an operator reads before an action executes.
 *
 * [destructive] is not decoration. An action that changes enforcement state
 * gets the charcoal anchor, the danger heading and a destructive button; a
 * trust operation such as reverification does not, because presenting it
 * with quarantine's weight would tell the operator it does something it
 * does not do.
 */
@Composable
fun DestructiveConfirmation(
    actionName: String,
    consequenceText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = true,
    confirmIcon: ImageVector = NexaIcons.Quarantine,
    /** Overrides the confirm label. Used so a simulation reads "SIMULATE …". */
    confirmLabel: String? = null,
    /**
     * Marks the request as a simulation, which gives the confirm control its
     * own non-red treatment so it cannot be mistaken for a live-fire command.
     */
    simulation: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (destructive) "Execution Consequence" else "What this action does",
            style = NexaType.GroupLabel,
            color = if (destructive) NexaDanger else NexaTextPrimary
        )
        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))

        GlassSurface(
            variant = if (destructive) GlassVariant.Destructive else GlassVariant.Strong
        ) {
            Text(
                text = consequenceText,
                style = NexaType.Body,
                color = if (destructive) NexaTextOnDark else NexaTextPrimary
            )
        }

        Spacer(modifier = Modifier.height(NexaTokens.SpacingXLarge))

        NexaButton(
            text = confirmLabel ?: "CONFIRM $actionName",
            onClick = onConfirm,
            variant = when {
                simulation -> NexaButtonVariant.Simulation
                destructive -> NexaButtonVariant.Destructive
                else -> NexaButtonVariant.Primary
            },
            icon = confirmIcon
        )

        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))

        NexaOutlinedButton(
            text = "CANCEL",
            onClick = onCancel,
            icon = NexaIcons.Cancel
        )
    }
}
