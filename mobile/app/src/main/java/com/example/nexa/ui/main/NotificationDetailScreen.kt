package com.example.nexa.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.nexa.AlertDetail
import com.example.nexa.DeviceDetail
import com.example.nexa.IdentityDetail
import com.example.nexa.theme.*
import com.example.nexa.ui.common.DeliveryAttempt
import com.example.nexa.ui.common.icon
import com.example.nexa.ui.common.isTrustworthy
import com.example.nexa.ui.common.label
import com.example.nexa.ui.common.status
import com.example.nexa.ui.components.*
import com.example.nexa.ui.notifications.*

/**
 * One delivery record.
 *
 * Two blocks, kept apart on purpose: what happened to the message, and what
 * the message was about. Between them sits the one sentence that says the
 * first did not change the second.
 *
 * Nothing here executes anything. A delivery record is not a control.
 */
@Composable
fun NotificationDetailScreen(
    deliveryId: String,
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationDetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(deliveryId) { viewModel.load(deliveryId) }

    when (val current = state) {
        is NotificationDetailUiState.Loading ->
            LoadingState(message = "Reading delivery record...", modifier = modifier)

        is NotificationDetailUiState.Unavailable ->
            UnavailableState(
                title = "Delivery record unavailable",
                message = "NEXA cannot resolve delivery $deliveryId. Nothing is assumed about whether it was delivered.",
                modifier = modifier,
                action = {
                    NexaOutlinedButton(
                        text = "Back to notifications",
                        onClick = onBack,
                        icon = NexaIcons.Back
                    )
                }
            )

        is NotificationDetailUiState.Error ->
            ErrorState(
                title = "Could not load delivery record",
                message = current.message,
                modifier = modifier,
                action = {
                    NexaOutlinedButton(
                        text = "Retry",
                        onClick = viewModel::refresh,
                        icon = NexaIcons.Refresh
                    )
                }
            )

        is NotificationDetailUiState.Content ->
            NotificationDetailContent(
                data = current.data,
                onBack = onBack,
                onNavigate = onNavigate,
                modifier = modifier
            )
    }
}

@Composable
private fun NotificationDetailContent(
    data: NotificationDetailData,
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val record = data.record
    val delivery = record.delivery
    val retry = retryLine(delivery)

    NexaScreen(
        modifier = modifier,
        title = "Delivery Record",
        onBack = onBack,
        backContentDescription = "Back to notifications"
    ) {
        // --- WHAT HAPPENED TO THE MESSAGE ---
        item {
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexaIcon(
                    icon = delivery.icon,
                    size = NexaTokens.IconLarge,
                    tint = delivery.status.style.onLight,
                    contentDescription = "Delivery ${delivery.stateLabel}"
                )
                Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                Text(
                    text = deliveryHeadline(delivery),
                    style = NexaType.Headline,
                    color = NexaTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Row(horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingSmall)) {
                StatusBadge(status = delivery.status, label = delivery.stateLabel.uppercase())
                StatusBadge(
                    text = delivery.channel.label,
                    color = NexaTextSecondary,
                    icon = NexaIcons.NotificationDelivery
                )
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
        }

        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = deliveryExplanation(delivery),
                        style = NexaType.Body,
                        color = NexaTextPrimary
                    )
                    if (retry != null) {
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NexaIcon(
                                icon = NexaIcons.Retry,
                                size = NexaTokens.IconSmall,
                                tint = NexaWarning
                            )
                            Spacer(modifier = Modifier.width(NexaTokens.SpacingXSmall))
                            Text(text = retry, style = NexaType.Metadata, color = NexaWarning)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))
        }

        item {
            SectionHeader(text = "Delivery")
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(NexaTokens.SpacingSmall)) {
                    data.deliveryFields.forEach { field -> RecordField(field) }
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))
        }

        // --- WHAT THE MESSAGE WAS ABOUT ---
        item {
            SectionHeader(text = "Source")
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
            Text(
                text = sourceProvenance(record.source),
                style = NexaType.Status,
                color = NexaTextSecondary
            )
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(NexaTokens.SpacingSmall)) {
                    Text(
                        text = notificationTypeLabel(record.source),
                        style = NexaType.Title,
                        color = NexaTextPrimary
                    )
                    Text(
                        text = record.subject,
                        style = NexaType.BodySecondary,
                        color = NexaTextSecondary
                    )
                    data.sourceFields.forEach { field -> RecordField(field) }
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
        }

        // --- THE SENTENCE THE WHOLE SCREEN TURNS ON ---
        item {
            GlassSurface(variant = GlassVariant.Strong, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    NexaIcon(
                        icon = NexaIcons.Information,
                        size = NexaTokens.IconMedium,
                        tint = NexaInformation
                    )
                    Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                    Text(
                        text = deliveryImpactStatement(record),
                        style = NexaType.BodySecondary,
                        color = NexaTextPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))
        }

        if (data.attempts.isNotEmpty()) {
            item {
                SectionHeader(text = "Attempts")
                Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                Text(
                    text = "Most recent first. Each row carries its own attempt number.",
                    style = NexaType.Metadata,
                    color = NexaTextMuted
                )
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            }
            data.attempts.forEach { attempt ->
                item(key = "attempt-${record.id}-${attempt.attempt}") {
                    AttemptRow(attempt)
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                }
            }
            item { Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium)) }
        }

        if (data.links.isNotEmpty()) {
            item {
                SectionHeader(text = "Related")
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                Column(verticalArrangement = Arrangement.spacedBy(NexaTokens.SpacingSmall)) {
                    data.links.forEach { link ->
                        NexaOutlinedButton(
                            text = link.label,
                            onClick = { onNavigate(link.navKey) },
                            icon = link.icon
                        )
                    }
                }
                Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))
            }
        }

        item {
            Text(
                text = "Delivery records are observational. To respond to the incident, open its alert and act from there.",
                style = NexaType.Metadata,
                color = NexaTextMuted
            )
            if (!data.freshness.isTrustworthy) {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                Text(
                    text = data.freshness.label,
                    style = NexaType.Metadata,
                    color = NexaWarning
                )
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXLarge))
        }
    }
}

@Composable
private fun RecordField(field: NotificationField) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = field.label,
            style = NexaType.Metadata,
            color = NexaTextSecondary,
            modifier = Modifier.width(FIELD_LABEL_WIDTH)
        )
        Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            if (field.technical) {
                TechnicalValue(text = field.value)
            } else {
                Text(text = field.value, style = NexaType.BodySecondary, color = NexaTextPrimary)
            }
        }
    }
}

/** One delivery attempt. Numbered from the record, not from its row position. */
@Composable
private fun AttemptRow(attempt: DeliveryAttempt) {
    NexaListRow(
        title = "Attempt ${attempt.attempt} · ${attempt.state.label}",
        variant = GlassVariant.Standard,
        leadingIcon = attempt.state.icon,
        leadingTint = attempt.state.status.style.onLight,
        leadingContentDescription = "Attempt ${attempt.attempt}, ${attempt.state.label}",
        titleStyle = NexaType.BodySecondary,
        titleColor = NexaTextPrimary,
        secondary = attempt.detail,
        technical = attempt.channel,
        trailing = {
            Text(
                text = attempt.timeLabel,
                style = NexaType.Metadata,
                color = NexaTextMuted
            )
        }
    )
}

/**
 * Where a link goes.
 *
 * Mapped at the edge of the UI so the notification model stays free of
 * navigation types. No route leads to an execution.
 */
private val NotificationLink.navKey: NavKey
    get() = when (this) {
        is NotificationLink.Alert -> AlertDetail(alertId)
        is NotificationLink.Device -> DeviceDetail(mac)
        is NotificationLink.Identity -> IdentityDetail(identityId)
    }

private val FIELD_LABEL_WIDTH = 136.dp
