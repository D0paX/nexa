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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.example.nexa.theme.NexaDanger
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaTextMuted
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTextSecondary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType
import com.example.nexa.theme.NexaWarning

/**
 * The shared shape of every non-content state.
 *
 * One layout, so the difference a user sees between "nothing here" and
 * "we cannot see the system right now" is carried entirely by the icon,
 * the tone and the words — which is exactly where that difference belongs.
 */
@Composable
private fun NexaStateContainer(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    message: String,
    titleColor: Color,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(NexaTokens.SpacingLarge)
        ) {
            NexaIcon(icon = icon, size = NexaTokens.IconHero, tint = iconTint)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            Text(text = title, style = NexaType.Headline, color = titleColor)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Text(
                text = message,
                style = NexaType.Body,
                color = NexaTextSecondary,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))
                action()
            }
        }
    }
}

@Composable
fun LoadingState(message: String = "Loading...", modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = NexaTextPrimary)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            Text(text = message, style = NexaType.Body, color = NexaTextSecondary)
        }
    }
}

/** There is genuinely nothing to show, and NEXA is certain of that. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = NexaIcons.Empty
) {
    NexaStateContainer(
        icon = icon,
        iconTint = NexaTextMuted,
        title = title,
        message = message,
        titleColor = NexaTextPrimary,
        modifier = modifier
    )
}

/** Something failed. Stated in danger tone, never mistakable for emptiness. */
@Composable
fun ErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = NexaIcons.Critical,
    action: @Composable (() -> Unit)? = null
) {
    NexaStateContainer(
        icon = icon,
        iconTint = NexaDanger,
        title = title,
        message = message,
        titleColor = NexaDanger,
        modifier = modifier,
        action = action
    )
}

/**
 * NEXA cannot currently reach the system.
 *
 * Deliberately distinct from [EmptyState]: an operator must never read a
 * lost connection as "no devices" or "no alerts". Absence of data and
 * absence of visibility are different security facts.
 */
@Composable
fun UnavailableState(
    title: String = "System unreachable",
    message: String = "NEXA cannot reach the enforcement backend. This is not a report that nothing is happening — the current state is unknown.",
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    NexaStateContainer(
        icon = NexaIcons.Unavailable,
        iconTint = NexaWarning,
        title = title,
        message = message,
        titleColor = NexaTextPrimary,
        modifier = modifier,
        action = action
    )
}

/**
 * There is no connection and no confirmed picture to fall back on.
 *
 * The cached case — content on screen, marked offline — belongs to
 * [AvailabilityNotice], not here. This surface is the one where NEXA has
 * nothing, so the wording must not imply there is something on screen to
 * read, nor that the silence means anything about the network.
 */
@Composable
fun OfflineState(
    title: String = "Offline",
    message: String = "No network connection. NEXA cannot confirm current state and has nothing confirmed to show instead. This says nothing about what is happening on the network.",
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    NexaStateContainer(
        icon = NexaIcons.Offline,
        iconTint = NexaTextMuted,
        title = title,
        message = message,
        titleColor = NexaTextPrimary,
        modifier = modifier,
        action = action
    )
}

/**
 * The data is real but old. Shown when NEXA knows its view has aged past
 * the point where it should be trusted for an enforcement decision.
 */
@Composable
fun StaleState(
    title: String = "State may be stale",
    message: String = "This view has not been confirmed recently. Re-check before acting on it.",
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    NexaStateContainer(
        icon = NexaIcons.Stale,
        iconTint = NexaWarning,
        title = title,
        message = message,
        titleColor = NexaTextPrimary,
        modifier = modifier,
        action = action
    )
}

/** The system is reachable but not operating at full capability. */
@Composable
fun DegradedState(
    title: String = "Degraded",
    message: String = "Some NEXA capabilities are unavailable. Enforcement results may be incomplete.",
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    NexaStateContainer(
        icon = NexaIcons.Warning,
        iconTint = NexaWarning,
        title = title,
        message = message,
        titleColor = NexaTextPrimary,
        modifier = modifier,
        action = action
    )
}
