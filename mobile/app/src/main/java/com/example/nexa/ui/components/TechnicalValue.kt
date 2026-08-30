package com.example.nexa.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.nexa.theme.MonospaceTextStyle
import com.example.nexa.theme.NexaTextPrimary

@Composable
fun TechnicalValue(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NexaTextPrimary
) {
    Text(
        text = text,
        style = MonospaceTextStyle,
        color = color,
        modifier = modifier
    )
}
