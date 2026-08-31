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
import com.example.nexa.ui.audit.*
import com.example.nexa.ui.common.isTrustworthy
import com.example.nexa.ui.common.label
import com.example.nexa.ui.components.*

/**
 * One historical record.
 *
 * Read-only by construction. It exposes what the event store recorded and
 * where that record points, and offers no way to repeat what it describes:
 * responding to something is started from the target, not from history.
 */
@Composable
fun AuditDetailScreen(
    eventId: String,
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuditDetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(eventId) { viewModel.load(eventId) }

    when (val current = state) {
        is AuditDetailUiState.Loading ->
            LoadingState(message = "Reading record...", modifier = modifier)

        is AuditDetailUiState.Unavailable ->
            UnavailableState(
                title = "Record unavailable",
                message = "NEXA cannot resolve event $eventId. Nothing is assumed about what it recorded.",
                modifier = modifier,
                action = {
                    NexaOutlinedButton(
                        text = "Back to history",
                        onClick = onBack,
                        icon = NexaIcons.Back,
                        modifier = Modifier.widthIn(max = 240.dp)
                    )
                }
            )

        is AuditDetailUiState.Error ->
            ErrorState(
                title = "Could not load record",
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

        is AuditDetailUiState.Content ->
            AuditDetailContent(
                data = current.data,
                onBack = onBack,
                onNavigate = onNavigate,
                modifier = modifier
            )
    }
}

@Composable
private fun AuditDetailContent(
    data: AuditDetailData,
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val entry = data.entry
    val status = auditStatus(entry)
    val badge = auditModeBadge(entry)

    NexaScreen(
        modifier = modifier,
        title = "Security Record",
        onBack = onBack,
        backContentDescription = "Back to history"
    ) {
        item {
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexaIcon(
                    icon = entry.type.icon,
                    size = NexaTokens.IconLarge,
                    tint = status.style.onLight,
                    contentDescription = entry.type.label
                )
                Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                Text(
                    text = auditHeadline(entry),
                    style = NexaType.Headline,
                    color = NexaTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Row(horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingSmall)) {
                StatusBadge(status = status, label = entry.outcome.label.uppercase())
                if (badge != null) {
                    StatusBadge(
                        text = badge.label,
                        color = badge.status.style.onLight,
                        icon = badge.icon
                    )
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
            // The authoritative timestamp, not a relative approximation.
            TechnicalValue(text = entry.occurredAtLabel)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
        }

        // A simulated record carries the same banner the flow that produced it
        // carried, in the past tense.
        if (entry.isSimulated) {
            item {
                SimulationBanner(
                    title = "SIMULATED RECORD",
                    detail = "NO FIREWALL MUTATION OCCURRED"
                )
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            }
        }

        item {
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = auditExplanation(entry),
                    style = NexaType.Body,
                    color = NexaTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))
        }

        item {
            SectionHeader(text = "Record")
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(NexaTokens.SpacingSmall)) {
                    data.fields.forEach { field -> RecordRow(field) }
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))
        }

        // Provenance stated as its own fact. An entry that came from an alert
        // says so; it does not become an alert.
        if (entry.alertId != null) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "SOURCE: ALERT",
                            style = NexaType.Status,
                            color = NexaTextSecondary
                        )
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingHairline))
                        Text(
                            text = "This record was produced in response to alert ${entry.alertId}. The alert has its own lifecycle, which this record does not describe.",
                            style = NexaType.BodySecondary,
                            color = NexaTextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))
            }
        }

        if (data.related.isNotEmpty()) {
            item {
                SectionHeader(text = "Sequence")
                Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                Text(
                    text = "Other records sharing this correlation, oldest first.",
                    style = NexaType.Metadata,
                    color = NexaTextMuted
                )
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            }
            data.related.forEach { related ->
                item(key = "related-${related.id}") {
                    RelatedRow(related)
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
                text = "History is a record, not a control surface. To act on this target, open it and start from its current state.",
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

/** One field of the record. Technical values keep their monospace treatment. */
@Composable
private fun RecordRow(field: AuditField) {
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
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            if (field.technical) {
                TechnicalValue(text = field.value)
            } else {
                Text(text = field.value, style = NexaType.BodySecondary, color = NexaTextPrimary)
            }
        }
    }
}

/** A sibling record in the same sequence. Not itself navigable. */
@Composable
private fun RelatedRow(entry: AuditEntry) {
    val badge = auditModeBadge(entry)
    NexaListRow(
        title = auditHeadline(entry),
        variant = GlassVariant.Standard,
        leadingIcon = entry.type.icon,
        leadingTint = auditStatus(entry).style.onLight,
        leadingContentDescription = "${entry.type.label}, ${entry.outcome.label}",
        titleStyle = NexaType.BodySecondary,
        titleColor = NexaTextSecondary,
        technical = entry.id,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                if (badge != null) {
                    StatusBadge(
                        text = badge.label,
                        color = badge.status.style.onLight,
                        icon = badge.icon
                    )
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                }
                Text(
                    text = entry.relativeLabel,
                    style = NexaType.Metadata,
                    color = NexaTextMuted
                )
            }
        }
    )
}

/**
 * Where a link goes.
 *
 * Mapped at the edge of the UI so the audit model stays free of navigation
 * types and can be tested without them.
 */
private val AuditLink.navKey: NavKey
    get() = when (this) {
        is AuditLink.Alert -> AlertDetail(alertId)
        is AuditLink.Device -> DeviceDetail(mac)
        is AuditLink.Identity -> IdentityDetail(identityId)
    }

private val FIELD_LABEL_WIDTH = 128.dp
