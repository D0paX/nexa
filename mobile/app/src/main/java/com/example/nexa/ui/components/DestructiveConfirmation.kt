package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaDanger
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaTextOnDark
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType

@Composable
fun DestructiveConfirmation(
    actionName: String,
    consequenceText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("Execution Consequence", style = NexaType.GroupLabel, color = NexaDanger)
        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))

        GlassSurface(variant = GlassVariant.Destructive) {
            Text(
                text = consequenceText,
                style = NexaType.Body,
                color = NexaTextOnDark
            )
        }
        
        Spacer(modifier = Modifier.height(NexaTokens.SpacingXLarge))
        
        NexaButton(
            text = "CONFIRM $actionName",
            onClick = onConfirm,
            variant = NexaButtonVariant.Destructive,
            icon = NexaIcons.Quarantine
        )

        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))

        NexaOutlinedButton(
            text = "CANCEL",
            onClick = onCancel,
            icon = NexaIcons.Cancel
        )
    }
}
