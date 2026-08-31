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
import com.example.nexa.AlertDetail
import com.example.nexa.IdentityDetail
import com.example.nexa.theme.*
import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.icon
import com.example.nexa.ui.enforcement.ActionPreparation
import com.example.nexa.ui.enforcement.ActionTarget
import com.example.nexa.ui.enforcement.AuthorizationState
import com.example.nexa.ui.enforcement.EnforcementAction
import com.example.nexa.ui.common.label
import com.example.nexa.ui.common.status
import com.example.nexa.ui.components.*
import com.example.nexa.ui.devices.*

/**
 * The full context for one device.
 *
 * Structured around the distinctions Phase 1-4 draws, because collapsing
 * them is how an operator ends up believing something the system never
 * said:
 *
 *   Device record  — what the network observed (Phase 1)
 *   Trusted identity — what cryptography verified (Phase 2)
 *   Enforcement    — what execution did (Phase 4)
 *
 * Each is its own section, on its own material, and a device can have the
 * first without the second.
 */
@Composable
fun DeviceDetailScreen(
    mac: String,
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeviceDetailViewModel = viewModel()
) {
    LaunchedEffect(mac) { viewModel.load(mac) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is DeviceDetailUiState.Loading ->
            LoadingState(message = "Reading device context...", modifier = modifier)

        is DeviceDetailUiState.Unavailable ->
            UnavailableState(
                title = "Device context unavailable",
                message = "NEXA cannot resolve context for this device. Its current state is unknown.",
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

        is DeviceDetailUiState.Error ->
            ErrorState(
                title = "Could not load device",
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

        is DeviceDetailUiState.Content ->
            DeviceDetailContent(
                data = current.data,
                onBack = onBack,
                onNavigate = onNavigate,
                modifier = modifier
            )
    }
}

@Composable
private fun DeviceDetailContent(
    data: DeviceDetailData,
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val device = data.device

    NexaScreen(
        modifier = modifier,
        title = "Device Context",
        onBack = onBack,
        itemSpacing = NexaTokens.SpacingMedium
    ) {
        item { DeviceHeader(data) }

        // --- Phase 1: what the network observed ---
        item {
            SectionHeader(text = "Device record", level = SectionLevel.Group)
        }
        item {
            GlassSurface(variant = GlassVariant.Standard, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Network observation. This is what NEXA has seen, not proof of identity.",
                        style = NexaType.Metadata,
                        color = NexaTextMuted
                    )
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                    ContextRow("Presence") {
                        StatusBadge(
                            status = device.presence.status,
                            label = device.presence.label.uppercase()
                        )
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    ContextRow("Network scope") { TechnicalValue(data.record.scope, emphasized = true) }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    ContextRow("Current IP") {
                        TechnicalValue(data.record.ip ?: "unknown")
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    ContextRow("MAC address") { TechnicalValue(data.record.mac) }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    ContextRow("Last observed") {
                        Text(
                            text = data.record.lastObservedLabel,
                            style = NexaType.BodySecondary,
                            color = NexaTextSecondary
                        )
                    }
                    if (data.record.freshness !is DataFreshness.Live) {
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                        InlineNotice(
                            icon = NexaIcons.Stale,
                            tint = NexaWarning,
                            text = if (data.record.freshness is DataFreshness.Unknown) {
                                "NEXA cannot confirm how current this observation is."
                            } else {
                                "This observation is not current. Re-check before acting on it."
                            }
                        )
                    }
                }
            }
        }

        // --- Phase 2: what cryptography verified ---
        item {
            SectionHeader(text = "Trusted identity", level = SectionLevel.Group)
        }
        item {
            val identity = data.identity
            if (identity == null) {
                // Deliberately a different, quieter material: the absence of an
                // identity is itself the finding.
                GlassSurface(variant = GlassVariant.Standard, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NexaIcon(
                            icon = NexaIcons.Warning,
                            size = NexaTokens.IconLarge,
                            tint = NexaWarning
                        )
                        Spacer(modifier = Modifier.width(NexaTokens.SpacingMedium))
                        Column {
                            Text("No trusted identity", style = NexaType.Title, color = NexaTextPrimary)
                            Spacer(modifier = Modifier.height(NexaTokens.SpacingHairline))
                            Text(
                                text = "This device has been observed on the network but has no cryptographic identity. Observation is not verification.",
                                style = NexaType.BodySecondary,
                                color = NexaTextSecondary
                            )
                        }
                    }
                }
            } else {
                GlassSurface(
                    variant = GlassVariant.Hero,
                    // Opens the identity as its own subject, rather than treating
                    // it as a property of the device.
                    onClick = { onNavigate(IdentityDetail(identity.identityId)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = NexaTokens.SpacingSmall)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CRYPTOGRAPHIC IDENTITY",
                                style = NexaType.Metadata,
                                color = NexaTextOnDarkMuted
                            )
                            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                            StatusBadge(
                                status = identity.trust.status,
                                label = identity.trust.label.uppercase(),
                                onDarkSurface = true
                            )
                        }
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                        TechnicalValue(
                            text = identity.identityId,
                            color = NexaTextOnDark,
                            emphasized = true
                        )
                        if (identity.owner != null) {
                            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                            Text(
                                text = "Owner: ${identity.owner}",
                                style = NexaType.BodySecondary,
                                color = NexaTextOnDarkMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                        Text(
                            text = identity.verifiedLabel,
                            style = NexaType.Metadata,
                            color = NexaTextOnDarkMuted
                        )
                        if (identity.reverificationLabel != null) {
                            Text(
                                text = identity.reverificationLabel,
                                style = NexaType.Metadata,
                                color = NexaTextOnDarkMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                        Text(
                            text = "A verified identity does not by itself authorize any action.",
                            style = NexaType.Metadata,
                            color = NexaTextOnDarkMuted
                        )
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Identity detail",
                                style = NexaType.Metadata,
                                color = NexaAction
                            )
                            NexaIcon(
                                icon = NexaIcons.Forward,
                                size = NexaTokens.IconMedium,
                                tint = NexaAction
                            )
                        }
                    }
                }
            }
        }

        // --- Phase 4: what enforcement did ---
        item {
            SectionHeader(text = "Enforcement", level = SectionLevel.Group)
        }
        item {
            GlassSurface(variant = GlassVariant.Standard, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("State", style = NexaType.Body, color = NexaTextPrimary)
                        StatusBadge(
                            status = data.enforcement.state.status,
                            label = data.enforcement.state.label.uppercase()
                        )
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    Text(
                        text = data.enforcement.detail,
                        style = NexaType.BodySecondary,
                        color = NexaTextSecondary
                    )

                    if (data.enforcement.bindingLabel != null) {
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                        ContextRow("Binding") { TechnicalValue(data.enforcement.bindingLabel) }
                    }
                    if (data.enforcement.ownershipScope != null) {
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                        // Ownership is stated against the scope, never an address:
                        // an IP can be reassigned to another device.
                        ContextRow("Owned in scope") {
                            TechnicalValue(data.enforcement.ownershipScope, emphasized = true)
                        }
                    }
                    if (data.enforcement.targetStale) {
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                        InlineNotice(
                            icon = NexaIcons.Stale,
                            tint = NexaWarning,
                            text = "Target context is stale. Any action will be re-snapshotted and re-authorized before it executes."
                        )
                    }
                }
            }
        }

        // --- Alerts ---
        if (data.alerts.isNotEmpty()) {
            item { SectionHeader(text = "Active alerts", level = SectionLevel.Group) }
            items(data.alerts, key = { it.id }) { alert ->
                NexaListRow(
                    title = alert.title,
                    onClick = { onNavigate(AlertDetail(alert.id)) },
                    variant = if (alert.severity == "CRITICAL") GlassVariant.Strong else GlassVariant.Standard,
                    leadingIcon = NexaIcons.forSeverity(alert.severity),
                    leadingTint = statusForSeverityColor(alert.severity),
                    leadingContentDescription = alert.severity,
                    titleStyle = NexaType.Title,
                    titleColor = NexaTextPrimary,
                    technical = alert.id,
                    timestamp = alert.timeAgo
                )
            }
        }

        // --- History ---
        if (data.activity.isNotEmpty()) {
            item { SectionHeader(text = "Recent activity", level = SectionLevel.Group) }
            items(data.activity, key = { it.id }) { entry ->
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
                data.actions.forEach { action ->
                    DeviceActionControl(
                        action = action,
                        onInvoke = {
                            // The context is assembled once, here, and handed on by
                            // handle. The client only requests: snapshot,
                            // authorization and execution stay behind Phase 4.
                            val contextId = prepareDeviceAction(action, data)
                            if (contextId != null) onNavigate(ActionConfirmation(contextId))
                        }
                    )
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                }
                Text(
                    text = "Actions are requested here and executed by NEXA's enforcement pipeline after snapshot and authorization.",
                    style = NexaType.Metadata,
                    color = NexaTextMuted
                )
            }
        }

        item { Spacer(modifier = Modifier.height(NexaTokens.SpacingXLarge)) }
    }
}

/** Identity, presence and primary state — without burying the reader in fields. */
@Composable
private fun DeviceHeader(data: DeviceDetailData) {
    val device = data.device
    GlassSurface(variant = GlassVariant.Hero, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = NexaTokens.SpacingSmall)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexaIcon(
                    icon = device.presence.icon,
                    size = NexaTokens.IconLarge,
                    tint = device.presence.status.style.onDark
                )
                Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                Text(
                    text = device.presence.label.uppercase(),
                    style = NexaType.Metadata,
                    color = NexaTextOnDarkMuted
                )
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Text(text = device.label, style = NexaType.Headline, color = NexaTextOnDark)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Row(horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingSmall)) {
                StatusBadge(
                    status = device.trust.status,
                    label = device.trust.label.uppercase(),
                    onDarkSurface = true
                )
                StatusBadge(
                    status = device.enforcement.status,
                    label = device.enforcement.label.uppercase(),
                    onDarkSurface = true
                )
            }
        }
    }
}

/**
 * An action, with its availability explained.
 *
 * An unavailable action stays visible and says why, rather than silently
 * disappearing and leaving the operator to guess.
 */
@Composable
private fun DeviceActionControl(action: DeviceAction, onInvoke: () -> Unit) {
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
                    DeviceActionKind.Release -> NexaIcons.Release
                    DeviceActionKind.RequireReverification -> NexaIcons.Reverification
                    DeviceActionKind.Quarantine -> NexaIcons.Quarantine
                }
            )
        }
        if (!action.enabled && action.disabledReason != null) {
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
            Text(
                text = action.disabledReason,
                style = NexaType.Metadata,
                color = NexaTextMuted
            )
        }
    }
}

@Composable
private fun ContextRow(label: String, value: @Composable () -> Unit) {
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
private fun InlineNotice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    text: String
) {
    Row(verticalAlignment = Alignment.Top) {
        NexaIcon(icon = icon, size = NexaTokens.IconSmall, tint = tint)
        Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
        Text(text = text, style = NexaType.Metadata, color = NexaTextSecondary)
    }
}

/**
 * Assembles the enforcement context for a device action.
 *
 * Everything the confirmation screen shows is resolved here from the device's
 * own state — nothing is defaulted, and no address is treated as identity.
 */
private fun prepareDeviceAction(
    action: DeviceAction,
    data: DeviceDetailData
): String? {
    val enforcementAction = when (action.kind) {
        DeviceActionKind.Quarantine -> EnforcementAction.QuarantineDevice
        DeviceActionKind.Release -> EnforcementAction.ReleaseQuarantine
        DeviceActionKind.RequireReverification -> EnforcementAction.RequireReverification
    }
    val device = data.device

    return ActionPreparation.prepare(
        action = enforcementAction,
        target = ActionTarget(
            deviceId = device.id,
            label = device.label,
            mac = device.mac,
            ip = device.ip,
            scope = device.scope,
            presence = device.presence,
            identityId = device.identityId,
            trust = device.trust,
            observationFreshness = device.freshness,
            lastObservedLabel = device.lastSeenLabel,
            bindingId = data.enforcement.bindingLabel,
            ownershipScope = data.enforcement.ownershipScope
        ),
        authorization = AuthorizationState.ApprovalRequired,
        executionMode = ExecutionMode.AuditOnly,
        currentEnforcement = device.enforcement,
        circuitBreaker = if (device.enforcement == DeviceEnforcement.Paused) {
            CircuitBreakerState.Open
        } else {
            CircuitBreakerState.Closed
        }
    )
}

private fun statusForSeverityColor(severity: String) = when (severity) {
    "CRITICAL" -> NexaCritical
    "WARNING" -> NexaWarning
    else -> NexaInformation
}
