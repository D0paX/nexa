package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.nexa.theme.NexaDanger
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTextSecondary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.Typography

@Composable
fun LoadingState(message: String = "Loading...", modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = NexaTextPrimary)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            Text(text = message, style = Typography.bodyLarge, color = NexaTextSecondary)
        }
    }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(NexaTokens.SpacingLarge)) {
            Text(text = title, style = Typography.headlineMedium, color = NexaTextPrimary)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Text(text = message, style = Typography.bodyLarge, color = NexaTextSecondary)
        }
    }
}

@Composable
fun ErrorState(title: String, message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(NexaTokens.SpacingLarge)) {
            Text(text = title, style = Typography.headlineMedium, color = NexaDanger)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Text(text = message, style = Typography.bodyLarge, color = NexaTextSecondary)
        }
    }
}
