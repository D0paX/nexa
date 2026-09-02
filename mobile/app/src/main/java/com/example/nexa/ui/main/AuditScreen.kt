package com.example.nexa.ui.main

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.nexa.AuditDetail
import com.example.nexa.theme.*
import com.example.nexa.ui.audit.*
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.availabilityOf
import com.example.nexa.ui.common.isTrustworthy
import com.example.nexa.ui.common.label
import com.example.nexa.ui.components.*

/**
 * The security history.
 *
 * A chronological record of what the system did, built to be read backwards
 * from now. It is not an alert queue: nothing here is waiting for the
 * operator, and nothing here can be acted on. Every row states what happened,
 * to what, when, and — where the record carries one — whether it was live or
 * simulated.
 */
@Composable
fun AuditScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuditViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is AuditUiState.Loading ->
            LoadingState(message = "Reading security history...", modifier = modifier)

        is AuditUiState.Offline ->
            OfflineState(
                message = "No connection. NEXA cannot retrieve security history, and any history shown elsewhere was last confirmed earlier.",
                modifier = modifier,
                action = { RetryAuditButton(viewModel::refresh) }
            )

        is AuditUiState.Unavailable ->
            UnavailableState(
                title = "Security history unavailable",
                message = "NEXA cannot reach the event store. What happened during this period is unknown — this is not a report that nothing happened.",
                modifier = modifier,
                action = { RetryAuditButton(viewModel::refresh) }
            )

        is AuditUiState.Error ->
            ErrorState(
                title = "Could not load history",
                message = current.message,
                modifier = modifier,
                action = { RetryAuditButton(viewModel::refresh) }
            )

        is AuditUiState.Content ->
            AuditContent(
                state = current,
                onQueryChange = viewModel::onQueryChange,
                onFiltersChange = viewModel::onFiltersChange,
                onSortChange = viewModel::onSortChange,
                onQuickFilter = viewModel::onQuickFilter,
                onClearFilters = viewModel::clearFilters,
                onLoadMore = viewModel::loadMore,
                onItemClick = onItemClick,
                modifier = modifier
            )
    }
}

@Composable
private fun RetryAuditButton(onRetry: () -> Unit) {
    NexaOutlinedButton(
        text = "Retry",
        onClick = onRetry,
        icon = NexaIcons.Refresh,
        modifier = Modifier.widthIn(max = 240.dp)
    )
}

@Composable
private fun AuditContent(
    state: AuditUiState.Content,
    onQueryChange: (String) -> Unit,
    onFiltersChange: (AuditFilters) -> Unit,
    onSortChange: (AuditSort) -> Unit,
    onQuickFilter: (AuditQuickFilter) -> Unit,
    onClearFilters: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    var showFilters by remember { mutableStateOf(false) }
    val activeQuick = state.filters.activeQuickFilter()
    val days = groupByDay(state.page) { it.dayLabel }

    NexaScreen(modifier = modifier) {
        item {
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Audit", style = NexaType.Display, color = NexaTextPrimary)
                AuditFreshness(state.freshness)
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
            Text(
                text = auditSummaryLine(state),
                style = NexaType.Metadata,
                color = NexaTextSecondary
            )
            // Live and simulated runs are counted apart. A single total would
            // tell an operator that more happened to the network than did.
            if (state.summary.simulated > 0 || state.summary.liveEnforcement > 0) {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingHairline))
                Text(
                    text = "${state.summary.liveEnforcement} live enforcement record(s) · ${state.summary.simulated} simulated",
                    style = NexaType.Metadata,
                    color = NexaTextMuted
                )
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
        }

        // Incompleteness has its own notice, because it can name the window
        // that is missing — which the shared wording cannot. When the record
        // is complete but old, the shared notice covers it. Never both: two
        // banners saying different things about the same list is worse than
        // either alone.
        if (!state.coverage.isComplete) {
            item {
                AuditCoverageNotice(state.coverage as AuditCoverage.Partial)
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            }
        } else {
            val availability = availabilityOf(state.freshness)
            if (availability.warrantsNotice) {
                item {
                    AvailabilityNotice(
                        availability = availability,
                        subject = "the audit record",
                        detail = state.freshness.label
                    )
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingSmall),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                AuditQuickFilter.entries.forEach { quick ->
                    NexaFilterChip(
                        label = quick.label,
                        selected = activeQuick == quick,
                        onClick = { onQuickFilter(quick) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
        }

        item {
            NexaSearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = "Search event, action, alert, device, MAC, scope",
                label = "Search security history"
            )
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingSmall),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                NexaFilterChip(
                    label = if (state.filters.isActive) "Filters (${state.filters.activeCount})" else "Filters",
                    selected = state.filters.isActive,
                    onClick = { showFilters = true }
                )
                NexaFilterChip(
                    label = state.sort.label,
                    selected = state.sort != AuditSort.Newest,
                    onClick = {
                        onSortChange(
                            if (state.sort == AuditSort.Newest) AuditSort.Oldest else AuditSort.Newest
                        )
                    }
                )
                if (state.filters.isActive) {
                    NexaFilterChip(label = "Clear", selected = false, onClick = onClearFilters)
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
        }

        if (state.page.isEmpty()) {
            item { AuditEmpty(state) }
        } else {
            days.forEach { day ->
                item(key = "day-${day.label}") {
                    AuditDayHeader(day.label)
                }
                items(day.entries, key = { it.id }) { entry ->
                    AuditRow(entry = entry, onClick = { onItemClick(AuditDetail(entry.id)) })
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                }
            }

            item(key = "audit-footer") {
                AuditFooter(state = state, onLoadMore = onLoadMore)
            }
        }

        item { Spacer(modifier = Modifier.height(NexaTokens.NavigationContentClearance)) }
    }

    if (showFilters) {
        AuditFilterSheet(
            filters = state.filters,
            sort = state.sort,
            scopes = state.all.mapNotNull { it.target.scopeOrNull }.distinct().sorted(),
            onFiltersChange = onFiltersChange,
            onSortChange = onSortChange,
            onClear = onClearFilters,
            onDismiss = { showFilters = false }
        )
    }
}

/**
 * One historical record.
 *
 * The headline is derived, never stored, so a simulated run reads as simulated
 * here for as long as the record exists. The mode badge is a second,
 * independent carrier of the same fact — an operator who misses one sees the
 * other.
 */
@Composable
private fun AuditRow(entry: AuditEntry, onClick: () -> Unit) {
    val status = auditStatus(entry)
    val badge = auditModeBadge(entry)

    NexaListRow(
        title = auditHeadline(entry),
        onClick = onClick,
        variant = auditSurfaceFor(entry),
        leadingIcon = entry.type.icon,
        leadingTint = status.style.onLight,
        leadingContentDescription = "${entry.type.label}, ${entry.outcome.label}",
        titleStyle = NexaType.Body,
        titleColor = NexaTextPrimary,
        secondary = auditRowSubtitle(entry),
        technical = auditRowTechnical(entry),
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
 * A day anchor.
 *
 * The whole of the timeline's structure: a quiet label and the spacing around
 * it. A drawn connector would add weight without adding information.
 */
@Composable
private fun AuditDayHeader(label: String) {
    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
    Text(
        text = label.uppercase(),
        style = NexaType.Metadata,
        color = NexaTextMuted,
        modifier = Modifier.padding(bottom = NexaTokens.SpacingSmall)
    )
}

/** Says plainly that the record being shown is not the whole record. */
@Composable
private fun AuditCoverageNotice(coverage: AuditCoverage.Partial) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NexaIcon(icon = NexaIcons.Warning, size = NexaTokens.IconMedium, tint = NexaWarning)
            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
            Column {
                Text("History may be incomplete", style = NexaType.Title, color = NexaTextPrimary)
                Spacer(modifier = Modifier.height(NexaTokens.SpacingHairline))
                Text(
                    text = coverage.reason,
                    style = NexaType.BodySecondary,
                    color = NexaTextSecondary
                )
                Spacer(modifier = Modifier.height(NexaTokens.SpacingHairline))
                Text(
                    text = "Do not read this timeline as a complete account of what happened.",
                    style = NexaType.Metadata,
                    color = NexaWarning
                )
            }
        }
    }
}

/**
 * The end of the rendered window.
 *
 * History is loaded a page at a time, so the screen states how much of it is
 * currently being shown rather than implying the list is everything.
 */
@Composable
private fun AuditFooter(state: AuditUiState.Content, onLoadMore: () -> Unit) {
    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
    if (state.hasMore) {
        LaunchedEffect(state.page.size, state.query, state.filters, state.sort) {
            onLoadMore()
        }
        Text(
            text = "Showing ${state.page.size} of ${state.visible.size} records...",
            style = NexaType.Metadata,
            color = NexaTextMuted
        )
    } else {
        Text(
            text = "End of the retrieved history · ${state.visible.size} record(s)",
            style = NexaType.Metadata,
            color = NexaTextMuted
        )
    }
}

/**
 * The empty state.
 *
 * Reports what the event store said and nothing more. An empty history is not
 * evidence that the system is secure, and never says so.
 */
@Composable
private fun AuditEmpty(state: AuditUiState.Content) {
    val filtered = state.query.isNotEmpty() || state.filters.isActive
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = if (filtered) "No matching records" else "No security history recorded yet",
                style = NexaType.Title,
                color = NexaTextPrimary
            )
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
            Text(
                text = if (filtered) {
                    "No record matches the current search or filters."
                } else {
                    "The event store returned no records. This reports the contents of the history only — it is not an assessment of system posture."
                },
                style = NexaType.BodySecondary,
                color = NexaTextSecondary
            )
        }
    }
}

@Composable
private fun AuditFreshness(freshness: DataFreshness) {
    val stale = !freshness.isTrustworthy
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (stale) {
            NexaIcon(icon = NexaIcons.Stale, size = NexaTokens.IconSmall, tint = NexaWarning)
            Spacer(modifier = Modifier.width(NexaTokens.SpacingXSmall))
        }
        Text(
            text = freshness.label,
            style = NexaType.Metadata,
            color = if (stale) NexaWarning else NexaTextMuted
        )
    }
}

private fun auditSummaryLine(state: AuditUiState.Content): String {
    val shown = state.visible.size
    val base = "$shown record(s)"
    val s = state.summary
    val flags = buildList {
        if (s.failures > 0) add("${s.failures} failed")
        if (s.unknownOutcome > 0) add("${s.unknownOutcome} unknown outcome")
    }
    return if (flags.isEmpty()) base else "$base · ${flags.joinToString(" · ")}"
}
