package com.example.nexa.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
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
import com.example.nexa.ActionConfirmation
import com.example.nexa.DeviceDetail
import com.example.nexa.IdentityDetail
import com.example.nexa.theme.*
import com.example.nexa.ui.alerts.*
import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.common.icon
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.enforcement.ActionPreparation
import com.example.nexa.ui.enforcement.ActionTarget
import com.example.nexa.ui.enforcement.AuthorizationState
import com.example.nexa.ui.enforcement.EnforcementAction
import com.example.nexa.ui.common.label as trustLabel
import com.example.nexa.ui.common.status as trustStatus
import com.example.nexa.ui.components.*
import com.example.nexa.ui.devices.label as presenceLabel
import com.example.nexa.ui.devices.status as presenceStatus
import com.example.nexa.ui.common.DeliveryAttempt
import com.example.nexa.ui.common.DeliveryState
import com.example.nexa.ui.common.label
import com.example.nexa.ui.common.status

/**
 * One incident, in full.
 *
 * Ordered so the operator meets the facts in a useful order — what happened,
 * how serious, what state the alert is in, what it points at, and only then
 * what may be done about it.
 *
 * Notification delivery has its own section, deliberately below the alert's
 * own lifecycle and worded so it can never be read as the incident's state.
 */
@Composable
fun AlertDetailScreen(
    alertId: String,
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlertDetailViewModel = viewModel()
) {
    LaunchedEffect(alertId) { viewModel.load(alertId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is AlertDetailUiState.Loading ->
            LoadingState(message = "Reading alert context...", modifier = modifier)

        is AlertDetailUiState.Unavailable ->
            UnavailableState(
                title = "Alert unavailable",
                message = "NEXA cannot resolve this alert. Its current state is unknown.",
                modifier = modifier,
                action = {
                    NexaOutlinedButton(
                        text = "Back",
                        onClick = onBack,
                        icon = NexaIcons.Back,
                        modifier = Modifier.widthIn(max = 240.dp)
                    )
                }
            )

        is AlertDetailUiState.Error ->
            ErrorState(
                title = "Could not load alert",
                message = current.message,
                modifier = modifier,
                action = {
                    NexaOutlinedButton(
                        text = "Retry",
                        onClick = viewModel::refresh,
                        icon = NexaIcons.Refresh,
                        modifier = Modifier.widthIn(max = 240.dp)
                    )
                }
            )

        is AlertDetailUiState.Content ->
            AlertDetailContent(
                data = current.data,
                onBack = onBack,
                onNavigate = onNavigate,
                onLifecycleChange = viewModel::onLifecycleChange,
                modifier = modifier
            )
    }
}

@Composable
private fun AlertDetailContent(
    data: AlertDetailData,
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    onLifecycleChange: (AlertLifecycle) -> Unit,
    modifier: Modifier = Modifier
) {
    val alert = data.alert

    NexaScreen(
        modifier = modifier,
        title = alert.id,
        onBack = onBack,
        itemSpacing = NexaTokens.SpacingMedium
    ) {
        item { AlertHeader(alert) }

        // --- What happened ---
        item { SectionHeader(text = "What happened", level = SectionLevel.Group) }
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = data.description,
                    style = NexaType.Body,
                    color = NexaTextPrimary
                )
            }
        }

        // --- Alert lifecycle: the alert's own state ---
        item { SectionHeader(text = "Alert state", level = SectionLevel.Group) }
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Lifecycle", style = NexaType.Body, color = NexaTextPrimary)
                        StatusBadge(
                            status = alert.lifecycle.status,
                            label = alert.lifecycle.label.uppercase()
                        )
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    Text(
                        text = alert.lifecycle.explanation,
                        style = NexaType.BodySecondary,
                        color = NexaTextSecondary
                    )
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                    FactRow("Created") { MetaText(alert.createdLabel) }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    FactRow("Last updated") { MetaText(alert.updatedLabel) }
                }
            }
        }

        // --- Notification delivery: a different lifecycle entirely ---
        item { SectionHeader(text = "Notification delivery", level = SectionLevel.Group) }
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NexaIcon(
                                icon = data.delivery.state.icon,
                                size = NexaTokens.IconMedium,
                                tint = data.delivery.state.status.style.onLight
                            )
                            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                            Text("Notification", style = NexaType.Body, color = NexaTextPrimary)
                        }
                        StatusBadge(
                            status = data.delivery.state.status,
                            label = data.delivery.state.label.uppercase()
                        )
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    Text(
                        text = data.delivery.state.explanation,
                        style = NexaType.BodySecondary,
                        color = NexaTextSecondary
                    )
                    if (data.delivery.detail != null) {
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                        Text(
                            text = data.delivery.detail,
                            style = NexaType.Metadata,
                            color = NexaTextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    FactRow("Last attempt") { MetaText(data.delivery.lastAttemptLabel) }

                    // Stated explicitly wherever delivery went wrong: this is
                    // the confusion the whole screen is built to prevent.
                    if (data.delivery.state.isFailure || data.delivery.state == DeliveryState.Retrying) {
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                        Row(verticalAlignment = Alignment.Top) {
                            NexaIcon(
                                icon = NexaIcons.Information,
                                size = NexaTokens.IconSmall,
                                tint = NexaInformation
                            )
                            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                            Text(
                                text = "This alert remains ${alert.lifecycle.label.uppercase()}. A notification problem does not change the state of the incident.",
                                style = NexaType.Metadata,
                                color = NexaTextSecondary
                            )
                        }
                    }
                }
            }
        }

        // --- Delivery attempts ---
        if (data.delivery.attempts.size > 1) {
            item { SectionHeader(text = "Delivery attempts", level = SectionLevel.Group) }
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        data.delivery.attempts.forEachIndexed { index, attempt ->
                            if (index > 0) Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(attempt.channel, style = NexaType.BodySecondary, color = NexaTextPrimary)
                                    if (attempt.detail != null) {
                                        Text(attempt.detail, style = NexaType.Metadata, color = NexaTextMuted)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    StatusBadge(
                                        status = attempt.state.status,
                                        label = attempt.state.label.uppercase()
                                    )
                                    Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                                    MetaText(attempt.timeLabel)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Target ---
        item { SectionHeader(text = "Target", level = SectionLevel.Group) }
        item { TargetCard(alert, onNavigate) }

        // --- Timeline ---
        if (data.timeline.isNotEmpty()) {
            item { SectionHeader(text = "Timeline", level = SectionLevel.Group) }
            items(data.timeline, key = { it.id }) { entry ->
                NexaListRow(
                    title = entry.title,
                    leadingIcon = entry.kind.icon,
                    leadingTint = entry.status.style.onLight,
                    leadingContentDescription = entry.status.style.label,
                    titleColor = NexaTextSecondary,
                    timestamp = entry.timeAgo
                )
            }
        }

        // --- Actions ---
        item { SectionHeader(text = "Actions", level = SectionLevel.Group) }
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (data.actions.isEmpty()) {
                    Text(
                        text = "This alert is closed. Responding to the target starts from the device context.",
                        style = NexaType.BodySecondary,
                        color = NexaTextSecondary
                    )
                }
                data.actions.forEach { action ->
                    AlertActionControl(
                        action = action,
                        onInvoke = {
                            handleAction(action, alert, onNavigate, onLifecycleChange)
                        }
                    )
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                }
                if (data.actions.any { it.enforcement }) {
                    Text(
                        text = "Response actions are requested here and executed by NEXA's enforcement pipeline after snapshot and authorization.",
                        style = NexaType.Metadata,
                        color = NexaTextMuted
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(NexaTokens.SpacingXLarge)) }
    }
}

/**
 * Routes an action.
 *
 * Lifecycle transitions are Phase 3 operations on the alert. Enforcement and
 * trust operations leave through the Phase 4 confirmation flow carrying
 * scope and identity — never an address alone.
 */
private fun handleAction(
    action: AlertAction,
    alert: AlertListItem,
    onNavigate: (NavKey) -> Unit,
    onLifecycleChange: (AlertLifecycle) -> Unit
) {
    val device = alert.target.deviceRef
    when (action.kind) {
        AlertActionKind.Acknowledge -> onLifecycleChange(AlertLifecycle.Acknowledged)
        AlertActionKind.Resolve -> onLifecycleChange(AlertLifecycle.Resolved)
        AlertActionKind.Ignore -> onLifecycleChange(AlertLifecycle.Ignored)
        AlertActionKind.ViewDevice -> device?.let { onNavigate(DeviceDetail(it.mac)) }
        AlertActionKind.ViewIdentity ->
            alert.target.identityRef?.let { onNavigate(IdentityDetail(it.identityId)) }
        AlertActionKind.QuarantineTarget, AlertActionKind.RequireReverification -> {
            // Alerts do not own enforcement logic: they prepare the same
            // context and hand it to the same pipeline as everywhere else.
            val contextId = prepareAlertAction(action, alert)
            if (contextId != null) onNavigate(ActionConfirmation(contextId))
        }
    }
}

/**
 * Assembles the enforcement context for a response action on an alert.
 *
 * Returns null when the alert has no resolvable device target — a response
 * cannot be prepared against something NEXA cannot identify.
 */
private fun prepareAlertAction(action: AlertAction, alert: AlertListItem): String? {
    val device = alert.target.deviceRef ?: return null
    val identity = alert.target.identityRef
    val enforcementAction = when (action.kind) {
        AlertActionKind.QuarantineTarget -> EnforcementAction.QuarantineDevice
        AlertActionKind.RequireReverification -> EnforcementAction.RequireReverification
        else -> return null
    }

    return ActionPreparation.prepare(
        action = enforcementAction,
        target = ActionTarget(
            deviceId = device.deviceId,
            label = device.label,
            mac = device.mac,
            ip = device.ip,
            scope = device.scope,
            presence = device.presence,
            identityId = identity?.identityId,
            trust = identity?.trust ?: TrustState.Unverified,
            observationFreshness = device.recordFreshness,
            lastObservedLabel = device.lastObservedLabel
        ),
        authorization = AuthorizationState.ApprovalRequired,
        executionMode = ExecutionMode.AuditOnly,
        currentEnforcement = DeviceEnforcement.Normal,
        circuitBreaker = CircuitBreakerState.Closed
    )
}

/** Identifier, severity and title — the incident at a glance. */
@Composable
private fun AlertHeader(alert: AlertListItem) {
    GlassSurface(variant = GlassVariant.Hero, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = NexaTokens.SpacingSmall)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexaIcon(
                    icon = alert.severity.icon,
                    size = NexaTokens.IconLarge,
                    tint = alert.severity.status.style.onDark
                )
                Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                Text(
                    text = alert.severity.label.uppercase(),
                    style = NexaType.Metadata,
                    color = alert.severity.status.style.onDark
                )
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Text(text = alert.title, style = NexaType.Headline, color = NexaTextOnDark)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
            TechnicalValue(text = alert.id, color = NexaTextOnDarkMuted)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Row(horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingSmall)) {
                StatusBadge(
                    status = alert.lifecycle.status,
                    label = alert.lifecycle.label.uppercase(),
                    onDarkSurface = true
                )
                // Delivery appears here too, but always labelled as the
                // notification's state rather than the alert's.
                StatusBadge(
                    status = alert.delivery.status,
                    label = "NOTIFY ${alert.delivery.label.uppercase()}",
                    onDarkSurface = true
                )
            }
        }
    }
}

/**
 * What the alert points at.
 *
 * An alert does not always refer to a device, and a device does not always
 * carry an identity — both are stated rather than assumed.
 */
@Composable
private fun TargetCard(alert: AlertListItem, onNavigate: (NavKey) -> Unit) {
    when (val target = alert.target) {
        is AlertTarget.DeviceTarget -> {
            val device = target.device
            GlassSurface(
                variant = GlassVariant.Interactive,
                onClick = { onNavigate(DeviceDetail(device.mac)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NexaIcon(icon = NexaIcons.Devices, size = NexaTokens.IconMedium, tint = NexaTextSecondary)
                        Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                        Text("Observed device", style = NexaType.Title, color = NexaTextPrimary)
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    Text(device.label, style = NexaType.Body, color = NexaTextPrimary)
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                    FactRow("Presence") {
                        StatusBadge(
                            status = device.presence.presenceStatus,
                            label = device.presence.presenceLabel.uppercase()
                        )
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    FactRow("Network scope") { TechnicalValue(device.scope, emphasized = true) }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    FactRow("Current IP") { TechnicalValue(device.ip ?: "unknown") }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    FactRow("MAC address") { TechnicalValue(device.mac) }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    FactRow("Last observed") { MetaText(device.lastObservedLabel) }

                    if (device.recordFreshness !is DataFreshness.Live) {
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                        Row(verticalAlignment = Alignment.Top) {
                            NexaIcon(icon = NexaIcons.Stale, size = NexaTokens.IconSmall, tint = NexaWarning)
                            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                            Text(
                                text = "The observation behind this target is not current. Any enforcement action will be re-snapshotted and re-authorized before it executes.",
                                style = NexaType.Metadata,
                                color = NexaTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                    val identity = target.identity
                    if (identity == null) {
                        Text(
                            text = "This target has no cryptographic identity. It is an observed device only.",
                            style = NexaType.Metadata,
                            color = NexaTextMuted
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NexaIcon(
                                icon = NexaIcons.Identity,
                                size = NexaTokens.IconSmall,
                                tint = identity.trust.trustStatus.style.onLight
                            )
                            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                            TechnicalValue(identity.identityId)
                            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                            StatusBadge(
                                status = identity.trust.trustStatus,
                                label = identity.trust.trustLabel.uppercase()
                            )
                        }
                    }
                }
            }
        }

        is AlertTarget.ScopeTarget -> {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Network scope", style = NexaType.Title, color = NexaTextPrimary)
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    TechnicalValue(target.scope, emphasized = true)
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    Text(
                        text = "This alert concerns a network scope rather than a single device.",
                        style = NexaType.Metadata,
                        color = NexaTextMuted
                    )
                }
            }
        }

        AlertTarget.Unknown -> {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Target unresolved", style = NexaType.Title, color = NexaTextPrimary)
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    Text(
                        text = "NEXA cannot resolve what this alert refers to. No response action is offered.",
                        style = NexaType.BodySecondary,
                        color = NexaTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertActionControl(action: AlertAction, onInvoke: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (action.destructive) {
            NexaButton(
                text = action.label,
                onClick = onInvoke,
                variant = NexaButtonVariant.Destructive,
                enabled = action.enabled,
                icon = NexaIcons.Quarantine
            )
        } else {
            NexaOutlinedButton(
                text = action.label,
                onClick = onInvoke,
                enabled = action.enabled,
                icon = when (action.kind) {
                    AlertActionKind.Acknowledge -> NexaIcons.Acknowledge
                    AlertActionKind.Resolve -> NexaIcons.Resolve
                    AlertActionKind.Ignore -> NexaIcons.Ignore
                    AlertActionKind.RequireReverification -> NexaIcons.Reverification
                    AlertActionKind.ViewDevice -> NexaIcons.Devices
                    AlertActionKind.ViewIdentity -> NexaIcons.Identity
                    AlertActionKind.QuarantineTarget -> NexaIcons.Quarantine
                }
            )
        }
        if (!action.enabled && action.disabledReason != null) {
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
            Text(action.disabledReason, style = NexaType.Metadata, color = NexaTextMuted)
        }
        // Acknowledgement must never read as closure.
        if (action.kind == AlertActionKind.Acknowledge) {
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
            Text(
                text = "Acknowledging records that you have seen this alert. It does not resolve it.",
                style = NexaType.Metadata,
                color = NexaTextMuted
            )
        }
    }
}

@Composable
private fun FactRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = NexaType.BodySecondary, color = NexaTextSecondary)
        value()
    }
}

@Composable
private fun MetaText(text: String) {
    Text(text, style = NexaType.BodySecondary, color = NexaTextSecondary)
}
