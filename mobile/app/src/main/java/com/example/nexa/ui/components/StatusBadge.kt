package com.example.nexa.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.Typography

@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(NexaTokens.CornerRadiusSmall),
        modifier = modifier
    ) {
        Text(
            text = text.uppercase(),
            style = Typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = NexaTokens.SpacingSmall, vertical = 4.dp)
        )
    }
}
