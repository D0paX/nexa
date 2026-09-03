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
import com.example.nexa.DeviceDetail
import com.example.nexa.theme.*
import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.enforcement.ActionPreparation
import com.example.nexa.ui.enforcement.ActionTarget
import com.example.nexa.ui.enforcement.AuthorizationState
import com.example.nexa.ui.enforcement.EnforcementAction
import com.example.nexa.ui.common.icon
import com.example.nexa.ui.common.label
import com.example.nexa.ui.common.status
import com.example.nexa.ui.components.*
import com.example.nexa.ui.devices.label as presenceLabel
import com.example.nexa.ui.devices.status as presenceStatus
import com.example.nexa.ui.identity.*

/**
 * One cryptographic identity, in full.
 *
 * The screen is ordered so the reader meets the security facts in the order
 * that keeps them separate: what this identity *is*, whether it is currently
 * trusted, how fresh that verification is, what credential backs it, which
 * observed device it claims to belong to, and only then what may be done
 * about it. Trust and authorization are answered in different sections
 * because they are different questions.
 */
@Composable
fun IdentityDetailScreen(
    identityId: String,
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IdentityDetailViewModel = viewModel()
) {
    LaunchedEffect(identityId) { viewModel.load(identityId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is IdentityDetailUiState.Loading ->
            LoadingState(message = "Reading identity context...", modifier = modifier)

        is IdentityDetailUiState.Unavailable ->
            UnavailableState(
                title = "Identity unavailable",
                message = "NEXA cannot resolve this identity. Its trust standing is unknown.",
                modifier = modifier,
                action = {
                    NexaOutlinedButton(
                        text = "Back",
                        onClick = onBack,
                        icon = NexaIcons.Back
                    )
                }
            )

        is IdentityDetailUiState.Error ->
            ErrorState(
                title = "Could not load identity",
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

        is IdentityDetailUiState.Content ->
            IdentityDetailContent(current.data, onBack, onNavigate, modifier)
    }
}

@Composable
private fun IdentityDetailContent(
    data: IdentityDetailData,
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val identity = data.identity

    NexaScreen(
        modifier = modifier,
        title = "Identity",
        onBack = onBack,
        itemSpacing = NexaTokens.SpacingMedium
    ) {
        item { IdentityHeader(identity) }

        // --- Verification freshness ---
        item { SectionHeader(text = "Verification", level = SectionLevel.Group) }
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Last verification", style = NexaType.Body, color = NexaTextPrimary)
                        StatusBadge(
                            status = identity.verification.freshnessStatus,
                            label = if (identity.verification.isCurrent) "CURRENT" else "NOT CURRENT"
                        )
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    Text(
                        text = identity.verification.freshnessLabel,
                        style = NexaType.BodySecondary,
                        color = NexaTextSecondary
                    )
                    if (identity.verification.nextReverificationLabel != null) {
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                        Text(
                            text = identity.verification.nextReverificationLabel,
                            style = NexaType.Metadata,
                            color = NexaTextMuted
                        )
                    }
                    // Verification is reported as a past event, never as a
                    // standing guarantee about the present.
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    Text(
                        text = "Verification describes a completed check, not a continuous guarantee.",
                        style = NexaType.Metadata,
                        color = NexaTextMuted
                    )

                    reverificationPrompt(identity)?.let { reason ->
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                        InlineNote(
                            icon = NexaIcons.Reverification,
                            tint = NexaWarning,
                            text = reason
                        )
                    }
                }
            }
        }

        // --- Authorization, kept apart from trust on purpose ---
        item { SectionHeader(text = "Authorization", level = SectionLevel.Group) }
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Operator approval", style = NexaType.Body, color = NexaTextPrimary)
                        StatusBadge(
                            status = NexaStatus.Warning,
                            label = if (data.authorization.operatorApprovalRequired) "REQUIRED" else "NOT REQUIRED"
                        )
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    Text(
                        text = data.authorization.note,
                        style = NexaType.BodySecondary,
                        color = NexaTextSecondary
                    )
                }
            }
        }

        // --- Credential lifecycle ---
        item { SectionHeader(text = "Credential", level = SectionLevel.Group) }
        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    CredentialLine(identity.credential, current = true)
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    Text(
                        text = "Lifecycle only. NEXA never displays key material.",
                        style = NexaType.Metadata,
                        color = NexaTextMuted
                    )
                }
            }
        }

        if (data.credentialHistory.size > 1) {
            item { SectionHeader(text = "Credential history", level = SectionLevel.Group) }
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        data.credentialHistory.drop(1).forEachIndexed { index, credential ->
                            if (index > 0) Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                            CredentialLine(credential, current = false)
                        }
                    }
                }
            }
        }

        // --- Observed device association ---
        item { SectionHeader(text = "Observed device", level = SectionLevel.Group) }
        item { AssociationCard(identity, onNavigate) }

        // --- Alerts ---
        if (data.alerts.isNotEmpty()) {
            item { SectionHeader(text = "Active alerts", level = SectionLevel.Group) }
            items(data.alerts, key = { it.id }) { alert ->
                NexaListRow(
                    title = alert.title,
                    onClick = { onNavigate(AlertDetail(alert.id)) },
                    variant = if (alert.severity == "CRITICAL") GlassVariant.Strong else GlassVariant.Standard,
                    leadingIcon = NexaIcons.forSeverity(alert.severity),
                    leadingTint = if (alert.severity == "CRITICAL") NexaCritical else NexaWarning,
                    leadingContentDescription = alert.severity,
                    titleStyle = NexaType.Title,
                    titleColor = NexaTextPrimary,
                    technical = alert.id,
                    timestamp = alert.timeAgo
                )
            }
        }

        // --- Trust history ---
        if (data.activity.isNotEmpty()) {
            item { SectionHeader(text = "Trust history", level = SectionLevel.Group) }
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
                    IdentityActionControl(
                        action = action,
                        onInvoke = {
                            when (action.kind) {
                                IdentityActionKind.ViewDevice ->
                                    identity.device?.let { onNavigate(DeviceDetail(it.mac)) }

                                IdentityActionKind.RequireReverification -> {
                                    // A trust operation, routed through the same
                                    // confirmation flow as every other action.
                                    val contextId = prepareReverification(identity)
                                    if (contextId != null) onNavigate(ActionConfirmation(contextId))
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                }
                Text(
                    text = "Reverification is a trust operation. It is not a quarantine and it does not revoke trust.",
                    style = NexaType.Metadata,
                    color = NexaTextMuted
                )
            }
        }

        item { Spacer(modifier = Modifier.height(NexaTokens.SpacingXLarge)) }
    }
}

/** Subject, trust standing and identifier — the identity as its own thing. */
@Composable
private fun IdentityHeader(identity: IdentitySummary) {
    GlassSurface(variant = GlassVariant.Hero, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = NexaTokens.SpacingSmall)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexaIcon(
                    icon = NexaIcons.Identity,
                    size = NexaTokens.IconLarge,
                    tint = NexaTextOnDarkMuted
                )
                Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                Text(
                    text = "CRYPTOGRAPHIC IDENTITY",
                    style = NexaType.Metadata,
                    color = NexaTextOnDarkMuted
                )
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Text(text = identity.subjectLabel, style = NexaType.Headline, color = NexaTextOnDark)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
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
            Row(horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingSmall)) {
                StatusBadge(
                    status = identity.trust.status,
                    label = identity.trust.label.uppercase(),
                    onDarkSurface = true
                )
                StatusBadge(
                    status = identity.credential.state.status,
                    label = "CRED ${identity.credential.state.label.uppercase()}",
                    onDarkSurface = true
                )
            }
            if (identity.trust == TrustState.Revoked) {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                Text(
                    text = "Trust has been withdrawn for this identity. It is not merely unverified — it was verified and is no longer accepted.",
                    style = NexaType.BodySecondary,
                    color = NexaTextOnDark
                )
            }
        }
    }
}

/**
 * The identity-to-device association.
 *
 * When NEXA cannot assert the relationship, this card says so rather than
 * choosing a side — the ambiguity is the security-relevant fact.
 */
@Composable
private fun AssociationCard(identity: IdentitySummary, onNavigate: (NavKey) -> Unit) {
    val device = identity.device
    val ambiguous = identity.relationship != IdentityRelationship.Confirmed

    GlassSurface(
        variant = if (ambiguous) GlassVariant.Strong else GlassVariant.Interactive,
        onClick = if (device != null) ({ onNavigate(DeviceDetail(device.mac)) }) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexaIcon(
                    icon = if (ambiguous) NexaIcons.Warning else NexaIcons.Devices,
                    size = NexaTokens.IconMedium,
                    tint = identity.relationship.status.style.onLight
                )
                Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                Text(
                    text = identity.relationship.label,
                    style = NexaType.Title,
                    color = NexaTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Text(
                text = identity.relationship.explanation,
                style = NexaType.BodySecondary,
                color = NexaTextSecondary
            )
            if (identity.relationshipNote != null) {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                Text(
                    text = identity.relationshipNote,
                    style = NexaType.Metadata,
                    color = NexaWarning
                )
            }

            if (device != null) {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                // Presence is the device's fact, not the identity's.
                DetailLine("Presence") {
                    StatusBadge(
                        status = device.presence.presenceStatus,
                        label = device.presence.presenceLabel.uppercase()
                    )
                }
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                DetailLine("Network scope") { TechnicalValue(device.scope, emphasized = true) }
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                DetailLine("Current IP") { TechnicalValue(device.ip ?: "unknown") }
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                DetailLine("MAC address") { TechnicalValue(device.mac) }
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                DetailLine("Last observed") {
                    Text(device.lastObservedLabel, style = NexaType.BodySecondary, color = NexaTextSecondary)
                }
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                Text(
                    text = "Address and MAC are observation context. Neither is an identity.",
                    style = NexaType.Metadata,
                    color = NexaTextMuted
                )
                if (device.recordFreshness !is DataFreshness.Live) {
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    InlineNote(
                        icon = NexaIcons.Stale,
                        tint = NexaWarning,
                        text = "The observed record behind this association is not current."
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                Text(
                    text = "No observed device record is currently associated with this identity.",
                    style = NexaType.Metadata,
                    color = NexaTextMuted
                )
            }
        }
    }
}

@Composable
private fun CredentialLine(credential: CredentialSummary, current: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexaIcon(
                    icon = credential.state.icon,
                    size = NexaTokens.IconMedium,
                    tint = credential.state.status.style.onLight
                )
                Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                TechnicalValue(credential.identifier, emphasized = current)
            }
            StatusBadge(
                status = credential.state.status,
                label = credential.state.label.uppercase()
            )
        }
        Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
        Text(credential.effectiveLabel, style = NexaType.Metadata, color = NexaTextSecondary)
        if (credential.note != null) {
            Text(credential.note, style = NexaType.Metadata, color = NexaTextMuted)
        }
    }
}

@Composable
private fun IdentityActionControl(action: IdentityAction, onInvoke: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        NexaOutlinedButton(
            text = action.label,
            onClick = onInvoke,
            enabled = action.enabled,
            icon = when (action.kind) {
                IdentityActionKind.RequireReverification -> NexaIcons.Reverification
                IdentityActionKind.ViewDevice -> NexaIcons.Devices
            }
        )
        if (!action.enabled && action.disabledReason != null) {
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
            Text(action.disabledReason, style = NexaType.Metadata, color = NexaTextMuted)
        }
    }
}

/**
 * Assembles the context for a reverification request.
 *
 * Requires a resolvable device association: an identity with no observed
 * device has no target for the pipeline to act against, and the UI does not
 * invent one.
 */
private fun prepareReverification(identity: IdentitySummary): String? {
    val device = identity.device ?: return null
    return ActionPreparation.prepare(
        action = EnforcementAction.RequireReverification,
        target = ActionTarget(
            deviceId = device.deviceId,
            label = device.label,
            mac = device.mac,
            ip = device.ip,
            scope = device.scope,
            presence = device.presence,
            identityId = identity.identityId,
            trust = identity.trust,
            observationFreshness = device.recordFreshness,
            lastObservedLabel = device.lastObservedLabel
        ),
        authorization = AuthorizationState.ApprovalRequired,
        executionMode = ExecutionMode.AuditOnly,
        currentEnforcement = DeviceEnforcement.Normal,
        circuitBreaker = CircuitBreakerState.Closed
    )
}

@Composable
private fun DetailLine(label: String, value: @Composable () -> Unit) {
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
private fun InlineNote(
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
