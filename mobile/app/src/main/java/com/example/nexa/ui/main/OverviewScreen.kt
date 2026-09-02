package com.example.nexa.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.nexa.AlertDetail
import com.example.nexa.Alerts
import com.example.nexa.DeviceDetail
import com.example.nexa.Devices
import com.example.nexa.theme.*
import com.example.nexa.ui.common.ActivityEntry
import com.example.nexa.ui.common.ActivityKind
import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.availabilityOf
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.icon
import com.example.nexa.ui.common.isTrustworthy
import com.example.nexa.ui.common.label
import com.example.nexa.ui.components.*
import com.example.nexa.ui.overview.*

/**
 * The NEXA Security Command Center.
 *
 * Ordered by what an operator needs first: overall posture, then anything
 * demanding action, then what enforcement is doing, then the summaries, then
 * recent history. The screen renders state — every judgement about what that
 * state means was made before it got here.
 */
@Composable
fun OverviewScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OverviewViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is OverviewUiState.Loading ->
            LoadingState(message = "Reading system state...", modifier = modifier)

        is OverviewUiState.Offline ->
            OfflineState(
                message = "No network connection. NEXA cannot read system state and has no confirmed picture to show instead. This is not a report that the network is quiet.",
                modifier = modifier,
                action = { RetryAction(onRetry = viewModel::refresh) }
            )

        is OverviewUiState.Unavailable ->
            UnavailableState(
                modifier = modifier,
                action = { RetryAction(onRetry = viewModel::refresh) }
            )

        is OverviewUiState.Error ->
            ErrorState(
                title = "Could not load system state",
                message = current.message,
                modifier = modifier,
                action = { RetryAction(onRetry = viewModel::refresh) }
            )

        is OverviewUiState.Content ->
            OverviewContent(
                data = current.data,
                onItemClick = onItemClick,
                modifier = modifier
            )
    }
}

@Composable
private fun RetryAction(onRetry: () -> Unit) {
    NexaOutlinedButton(
        text = "Retry",
        onClick = onRetry,
        icon = NexaIcons.Refresh,
        modifier = Modifier.widthIn(max = 240.dp)
    )
}

@Composable
private fun OverviewContent(
    data: OverviewData,
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    NexaScreen(modifier = modifier) {
        item {
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = NexaTextPrimary)) { append("NEXA") }
                        withStyle(SpanStyle(color = NexaAction)) { append(".") }
                    },
                    style = NexaType.Display
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingSmall)
                ) {
                    // The event feed's own state, kept visibly apart from the
                    // security posture below it. A feed being down is not a
                    // finding about the network.
                    RealtimeIndicator()
                    FreshnessLabel(freshness = data.freshness)
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
        }

        // Said before the posture, not after it. An operator who reads the
        // hero first and the caveat second has already formed a judgement,
        // and this is precisely the screen where a stale reading is most
        // likely to be mistaken for a current one.
        val availability = availabilityOf(data.freshness)
        if (availability.warrantsNotice) {
            item {
                AvailabilityNotice(
                    availability = availability,
                    subject = "the system picture",
                    detail = data.freshness.label
                )
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            }
        }

        // 1 — SYSTEM POSTURE
        item {
            PostureHero(data = data)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
        }

        // 2 — WHAT NEEDS ATTENTION
        if (data.attention.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                SectionHeader(text = "Needs attention")
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            }
            items(data.attention, key = { it.id }) { attention ->
                AttentionRow(item = attention, onItemClick = onItemClick)
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            }
        }

        // 3 — ENFORCEMENT CONDITION
        item {
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            SectionHeader(text = "Enforcement")
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            EnforcementCard(enforcement = data.enforcement)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))
        }

        // 4 — DEVICE / ALERT SUMMARY (Bento)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingMedium)
            ) {
                MetricSurface(
                    title = "ACTIVE DEVICES",
                    value = data.devices.active.toString(),
                    caption = deviceCaption(data.devices),
                    glassVariant = GlassVariant.Interactive,
                    onClick = { onItemClick(Devices) },
                    modifier = Modifier.weight(1f)
                )
                MetricSurface(
                    title = "ACTIVE ALERTS",
                    value = data.alerts.total.toString(),
                    valueColor = alertValueColor(data.alerts),
                    caption = alertCaption(data.alerts),
                    glassVariant = GlassVariant.Strong,
                    onClick = { onItemClick(Alerts) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))
        }

        // 5 — RECENT SECURITY ACTIVITY
        item {
            SectionHeader(text = "Recent activity")
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
        }

        if (data.activity.isEmpty()) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No security activity recorded recently.",
                        style = NexaType.Body,
                        color = NexaTextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            }
        } else {
            items(data.activity, key = { it.id }) { entry ->
                ActivityRow(entry = entry, onItemClick = onItemClick)
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            }
        }

        item {
            Spacer(modifier = Modifier.height(NexaTokens.NavigationContentClearance))
        }
    }
}

/**
 * The posture anchor.
 *
 * Word, shape and color together — so the state survives both a glance and
 * color-blind viewing — with the qualifying sentence directly beneath it.
 */
@Composable
private fun PostureHero(data: OverviewData) {
    val posture = data.posture
    val style = posture.status.style

    GlassSurface(variant = GlassVariant.Hero, modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = NexaTokens.SpacingSmall)
        ) {
            Text(text = "SYSTEM STATE", style = NexaType.Metadata, color = NexaTextOnDarkMuted)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexaIcon(
                    icon = posture.icon,
                    size = NexaTokens.IconLarge,
                    tint = style.onDark
                )
                Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                Text(
                    text = posture.label,
                    style = NexaType.Display,
                    color = style.onDark
                )
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            Text(
                text = data.postureDetail,
                style = NexaType.BodySecondary,
                color = NexaTextOnDark
            )
        }
    }
}

/**
 * What enforcement is doing, stated as facts rather than a verdict.
 *
 * Execution mode is shown here because AUDIT_ONLY changes what every number
 * below it means.
 */
@Composable
private fun EnforcementCard(enforcement: EnforcementState) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (enforcement.isOperational) "Enforcement available" else "Enforcement halted",
                    style = NexaType.Body,
                    color = NexaTextPrimary
                )
                StatusBadge(
                    status = if (enforcement.executionMode == ExecutionMode.AuditOnly) {
                        NexaStatus.Simulation
                    } else {
                        NexaStatus.Permitted
                    },
                    label = if (enforcement.executionMode == ExecutionMode.AuditOnly) "AUDIT ONLY" else "ENFORCE"
                )
            }

            if (enforcement.executionMode == ExecutionMode.AuditOnly) {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                Row(verticalAlignment = Alignment.Top) {
                    NexaIcon(
                        icon = NexaIcons.Simulated,
                        size = NexaTokens.IconSmall,
                        tint = NexaSimulation
                    )
                    Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                    Text(
                        text = "Actions are simulated. No firewall mutation will occur. Counts below describe existing bindings, not simulated activity.",
                        style = NexaType.Metadata,
                        color = NexaTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))

            EnforcementFact("Quarantined devices", enforcement.quarantinedDevices.toString())
            if (enforcement.pendingActions > 0) {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                EnforcementFact("Pending actions", enforcement.pendingActions.toString())
            }
            if (enforcement.failedActions > 0) {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                EnforcementFact(
                    label = "Failed actions",
                    value = enforcement.failedActions.toString(),
                    valueColor = NexaDanger
                )
            }
            if (enforcement.reconciliationIssues > 0) {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                EnforcementFact(
                    label = "Reconciliation issues",
                    value = enforcement.reconciliationIssues.toString(),
                    valueColor = NexaWarning
                )
            }
            if (enforcement.circuitBreaker != CircuitBreakerState.Closed) {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NexaIcon(
                        icon = NexaIcons.Paused,
                        size = NexaTokens.IconSmall,
                        tint = NexaPaused
                    )
                    Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                    Text(
                        text = if (enforcement.circuitBreaker == CircuitBreakerState.Open) {
                            "Circuit breaker open — enforcement will not execute."
                        } else {
                            "Circuit breaker recovering — enforcement is limited."
                        },
                        style = NexaType.Metadata,
                        color = NexaTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun EnforcementFact(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = NexaTextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = NexaType.BodySecondary, color = NexaTextSecondary)
        Text(value, style = NexaType.TechnicalStrong, color = valueColor)
    }
}

@Composable
private fun AttentionRow(item: AttentionItem, onItemClick: (NavKey) -> Unit) {
    val style = item.status.style
    val target = item.target

    NexaListRow(
        title = item.title,
        onClick = when (target) {
            is AttentionTarget.Alert -> ({ onItemClick(AlertDetail(target.id)) })
            is AttentionTarget.Device -> ({ onItemClick(DeviceDetail(target.mac)) })
            AttentionTarget.None -> null
        },
        variant = if (item.status == NexaStatus.Critical) GlassVariant.Strong else GlassVariant.Standard,
        leadingIcon = style.icon,
        leadingTint = style.onLight,
        leadingContentDescription = style.label,
        titleStyle = NexaType.Title,
        titleColor = NexaTextPrimary,
        secondary = item.detail
    )
}

@Composable
private fun ActivityRow(entry: ActivityEntry, onItemClick: (NavKey) -> Unit) {
    val style = entry.status.style

    NexaListRow(
        title = entry.title,
        onClick = if (entry.kind == ActivityKind.AlertRaised) {
            { onItemClick(AlertDetail(entry.id)) }
        } else {
            { onItemClick(DeviceDetail(entry.target)) }
        },
        leadingIcon = entry.kind.icon,
        leadingTint = style.onLight,
        leadingContentDescription = style.label,
        titleColor = NexaTextSecondary,
        technical = entry.target,
        // A simulated execution is labelled as one. Live and simulated events
        // are never distinguished by color alone.
        timestamp = if (entry.isSimulated) null else entry.timeAgo,
        trailing = if (entry.isSimulated) {
            {
                Column(horizontalAlignment = Alignment.End) {
                    StatusBadge(status = NexaStatus.Simulation, label = "SIMULATED")
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                    Text(entry.timeAgo, style = NexaType.Metadata, color = NexaTextMuted)
                }
            }
        } else {
            null
        }
    )
}

/** States how current the picture is — never implying more certainty than NEXA has. */
@Composable
private fun FreshnessLabel(freshness: DataFreshness) {
    val stale = !freshness.isTrustworthy
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (stale) {
            NexaIcon(
                icon = NexaIcons.Stale,
                size = NexaTokens.IconSmall,
                tint = NexaWarning
            )
            Spacer(modifier = Modifier.width(NexaTokens.SpacingXSmall))
        }
        Text(
            text = freshness.label,
            style = NexaType.Metadata,
            color = if (stale) NexaWarning else NexaTextMuted
        )
    }
}

private fun deviceCaption(devices: DeviceSummary): String = buildString {
    append("${devices.online} online")
    if (devices.quarantined > 0) append(" · ${devices.quarantined} quarantined")
    else if (devices.offline > 0) append(" · ${devices.offline} offline")
}

private fun alertCaption(alerts: AlertSummary): String = when {
    alerts.total == 0 -> "Nothing requires attention"
    alerts.critical > 0 -> "${alerts.critical} critical · ${alerts.unacknowledged} unacknowledged"
    else -> "${alerts.unacknowledged} unacknowledged"
}

private fun alertValueColor(alerts: AlertSummary) = when {
    alerts.critical > 0 -> NexaCritical
    alerts.total > 0 -> NexaWarning
    else -> NexaSecure
}
