package com.example.nexa.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.nexa.theme.*

@Composable
fun NexaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    enabled: Boolean = true
) {
    val containerColor = if (isDestructive) NexaDanger else NexaAction
    val contentColor = NexaTextOnDark

    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = NexaDisabled,
            disabledContentColor = NexaTextMuted
        ),
        shape = RoundedCornerShape(NexaTokens.CornerRadiusSmall),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = NexaTokens.MinTouchTarget)
    ) {
        Text(text.uppercase(), style = Typography.labelLarge)
    }
}

@Composable
fun NexaOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = NexaTextPrimary
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            disabledContentColor = NexaTextMuted
        ),
        border = BorderStroke(1.dp, NexaBorderNeutral),
        shape = RoundedCornerShape(NexaTokens.CornerRadiusSmall),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = NexaTokens.MinTouchTarget)
    ) {
        Text(text.uppercase(), style = Typography.labelLarge)
    }
}
