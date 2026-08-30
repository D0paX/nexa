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
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.Typography

@Composable
fun DestructiveConfirmation(
    actionName: String,
    consequenceText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("Execution Consequence", style = Typography.titleMedium, color = NexaDanger)
        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
        
        GlassSurface(variant = GlassVariant.Destructive) {
            Text(
                text = consequenceText,
                style = Typography.bodyLarge,
                color = NexaTextPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(NexaTokens.SpacingXLarge))
        
        NexaButton(
            text = "CONFIRM $actionName",
            onClick = onConfirm,
            isDestructive = true
        )
        
        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
        
        NexaOutlinedButton(
            text = "CANCEL",
            onClick = onCancel
        )
    }
}
