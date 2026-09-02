package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType
import com.example.nexa.ui.common.nexaHeading

/**
 * The frame every NEXA screen sits in.
 *
 * It owns the things that must not vary between screens: the transparent
 * container that lets the atmosphere through, the window-inset contract
 * (insets are applied once by the navigation host, so the screen consumes
 * none of its own), the screen gutter, and the top bar treatment including
 * the shared back control.
 *
 * A screen supplies content; it does not re-decide its own chrome.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexaScreen(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    backContentDescription: String = "Back",
    itemSpacing: Dp = 0.dp,
    content: LazyListScope.() -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            if (title != null) {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = NexaType.ScreenTitle,
                            color = NexaTextPrimary,
                            // The landmark a screen reader jumps to first.
                            modifier = Modifier.nexaHeading()
                        )
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            NexaBackButton(
                                onClick = onBack,
                                contentDescription = backContentDescription
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = NexaTextPrimary
                    )
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = NexaTokens.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            content = content
        )
    }
}
