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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.nexa.NotificationDetail
import com.example.nexa.theme.*
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.availabilityOf
import com.example.nexa.ui.common.countLabel
import com.example.nexa.ui.common.isTrustworthy
import com.example.nexa.ui.common.label
import com.example.nexa.ui.common.filterButtonLabel
import com.example.nexa.ui.common.filterCountSpoken
import com.example.nexa.ui.common.spokenSummaryLine
import com.example.nexa.ui.common.nexaQuery
import com.example.nexa.ui.common.nexaResults
import com.example.nexa.ui.common.resultCountLabel
import com.example.nexa.ui.components.*
import com.example.nexa.ui.notifications.*

/**
 * Delivery intelligence.
 *
 * Alerts answer "what security incident exists?". This screen answers a
 * narrower, operational question: did NEXA manage to deliver the message?
 * It is deliberately quieter — nothing here is an incident, and a row that
 * shouted would be claiming otherwise.
 */
@Composable
fun NotificationCenterScreen(
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationCenterViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is NotificationCenterUiState.Loading ->
            NexaListLoading(message = "Reading delivery records...", modifier = modifier)

        is NotificationCenterUiState.Offline ->
            OfflineState(
                message = "No connection. NEXA cannot read notification delivery state, and any delivery information shown elsewhere was last confirmed earlier.",
                modifier = modifier,
                action = { RetryDeliveryButton(viewModel::refresh) }
            )

        is NotificationCenterUiState.Unavailable ->
            UnavailableState(
                title = "Delivery visibility unavailable",
                message = "NEXA cannot reach the notification service. Whether recent messages were delivered is unknown — this is not a report that nothing was sent.",
                modifier = modifier,
                action = { RetryDeliveryButton(viewModel::refresh) }
            )

        is NotificationCenterUiState.Error ->
            ErrorState(
                title = "Could not load delivery records",
                message = current.message,
                modifier = modifier,
                action = { RetryDeliveryButton(viewModel::refresh) }
            )

        is NotificationCenterUiState.Content ->
            NotificationCenterContent(
                state = current,
                onBack = onBack,
                onQueryChange = viewModel::onQueryChange,
                onFiltersChange = viewModel::onFiltersChange,
                onSortChange = viewModel::onSortChange,
                onQuickFilter = viewModel::onQuickFilter,
                onClearFilters = viewModel::clearFilters,
                onClearQuery = viewModel::clearQuery,
                onRefresh = viewModel::refresh,
                onLoadMore = viewModel::loadMore,
                onNavigate = onNavigate,
                modifier = modifier
            )
    }
}

@Composable
private fun RetryDeliveryButton(onRetry: () -> Unit) {
    NexaOutlinedButton(
        text = "Retry",
        onClick = onRetry,
        icon = NexaIcons.Refresh
    )
}

@Composable
private fun NotificationCenterContent(
    state: NotificationCenterUiState.Content,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFiltersChange: (NotificationFilters) -> Unit,
    onSortChange: (NotificationSort) -> Unit,
    onQuickFilter: (NotificationQuickFilter) -> Unit,
    onClearFilters: () -> Unit,
    onClearQuery: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    var showFilters by remember { mutableStateOf(false) }
    val activeQuick = state.filters.activeQuickFilter()

    NexaScreen(
        modifier = modifier,
        title = "Notifications",
        onBack = onBack,
        backContentDescription = "Back to alerts"
    ) {
        item {
            Text(
                text = "Delivery of security messages. Incident state lives in Alerts.",
                style = NexaType.BodySecondary,
                color = NexaTextSecondary
            )
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
            // Stacked rather than side by side: the breakdown is six counts
            // long and the freshness label grows when the answer is old, so
            // sharing a row squeezes the counts into an unreadable column
            // exactly when the screen most needs to be read.
            Text(
                text = deliverySummaryLine(state),
                style = NexaType.Metadata,
                color = NexaTextSecondary,
                // Said with commas rather than middle dots.
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = spokenSummaryLine(deliverySummaryLine(state))
                    }
            )
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
            // One status line, not two, with the control that acts on it
            // beside it. Disabled while a check is already running.
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderStatus {
                    if (state.refreshing) {
                        RefreshingIndicator()
                    } else {
                        DeliveryFreshness(state.freshness)
                    }
                }
                Spacer(modifier = Modifier.width(NexaTokens.SpacingXSmall))
                NexaIconButton(
                    icon = NexaIcons.Refresh,
                    onClick = onRefresh,
                    enabled = !state.refreshing,
                    iconSize = NexaTokens.IconMedium,
                    contentDescription = "Refresh delivery records"
                )
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            PushStatusCard()
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
        }

        // As on the audit trail: the coverage notice names the window that is
        // missing, and the shared notice covers a complete record that is
        // simply old. One or the other, never both.
        if (!state.coverage.isComplete) {
            item {
                DeliveryCoverageNotice(state.coverage as NotificationCoverage.Partial)
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            }
        } else {
            val availability = availabilityOf(state.freshness)
            if (availability.warrantsNotice) {
                item {
                    AvailabilityNotice(
                        availability = availability,
                        subject = "delivery visibility",
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
                NotificationQuickFilter.entries.forEach { quick ->
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
                placeholder = "Search delivery, alert, action, device, MAC",
                label = "Search delivery records"
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
                    label = filterButtonLabel(state.filters.activeCount),
                    spokenLabel = filterCountSpoken(state.filters.activeCount),
                    selected = state.filters.isActive,
                    onClick = { showFilters = true }
                )
                NexaFilterChip(
                    label = state.sort.label,
                    selected = state.sort != NotificationSort.Attention,
                    onClick = { onSortChange(state.sort.next()) }
                )
                if (state.filters.isActive) {
                    NexaFilterChip(label = "Clear", selected = false, onClick = onClearFilters)
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
        }

        if (state.page.isEmpty()) {
            item { NoMatchNotice(
                    results = nexaResults(
                        sourceCount = state.all.size,
                        visibleCount = state.visible.size,
                        queryActive = nexaQuery(state.query).isActive,
                        filtersActive = state.filters.isActive
                    ),
                    subject = "delivery records",
                    emptyTitle = "No notification delivery records yet",
                    emptyMessage = "The notification service has no delivery records. This describes notification delivery only — it is not a statement about alerts or security events.",
                    onClearSearch = onClearQuery,
                    onClearFilters = onClearFilters
                ) }
        } else {
            items(state.page, key = { it.id }) { record ->
                NotificationRow(
                    record = record,
                    onClick = { onNavigate(NotificationDetail(record.id)) }
                )
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            }
            item(key = "notification-footer") {
                NotificationFooter(state = state, onLoadMore = onLoadMore)
            }
        }

        item { Spacer(modifier = Modifier.height(NexaTokens.SpacingXLarge)) }
    }

    if (showFilters) {
        NotificationFilterSheet(
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
 * One delivery record.
 *
 * The title is the delivery outcome, never the subject of the message. An
 * incident headline sitting next to a FAILED badge is the single misreading
 * this screen exists to prevent, so the two are never adjacent: what the
 * message was about is stated underneath, labelled as its source.
 */
@Composable
private fun NotificationRow(record: NotificationRecord, onClick: () -> Unit) {
    val delivery = record.delivery
    val sourceState = sourceStateSummary(record.source)

    NexaListRow(
        title = deliveryHeadline(delivery),
        onClick = onClick,
        actionLabel = "Open delivery record ${record.id}",
        trailingDescription = listOfNotNull(
            delivery.stateLabel,
            sourceState,
            delivery.lastAttemptLabel
        ).joinToString(", "),
        variant = deliverySurfaceFor(record),
        leadingIcon = delivery.icon,
        leadingTint = delivery.status.style.onLight,
        leadingContentDescription = "Delivery ${delivery.stateLabel}",
        titleStyle = NexaType.Body,
        titleColor = NexaTextPrimary,
        secondary = notificationSubtitle(record),
        // The delivery identifier, not the message text: a long subject in
        // monospace pushes every row to three lines and buries the outcome.
        // What the message said belongs on the detail screen.
        technical = record.delivery.deliveryId,
        trailing = {
            Column(
                horizontalAlignment = Alignment.End,
                // Bounded so a long source state cannot squeeze the delivery
                // outcome the row exists to state.
                modifier = Modifier.widthIn(max = SOURCE_STATE_MAX_WIDTH)
            ) {
                StatusBadge(
                    status = delivery.status,
                    label = delivery.stateLabel.uppercase()
                )
                // The subject's own state, stated separately and labelled, so a
                // critical incident and a failed message read as two facts.
                if (sourceState != null) {
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                    Text(
                        text = sourceState,
                        style = NexaType.Metadata,
                        color = NexaTextMuted,
                        textAlign = TextAlign.End
                    )
                }
                Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                Text(
                    text = delivery.lastAttemptLabel,
                    style = NexaType.Metadata,
                    color = NexaTextMuted
                )
            }
        }
    )
}

/** Says plainly that the delivery picture being shown is not the whole picture. */
@Composable
private fun DeliveryCoverageNotice(coverage: NotificationCoverage.Partial) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NexaIcon(icon = NexaIcons.Warning, size = NexaTokens.IconMedium, tint = NexaWarning)
            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
            Column {
                Text(
                    "Delivery visibility may be incomplete",
                    style = NexaType.Title,
                    color = NexaTextPrimary
                )
                Spacer(modifier = Modifier.height(NexaTokens.SpacingHairline))
                Text(
                    text = coverage.reason,
                    style = NexaType.BodySecondary,
                    color = NexaTextSecondary
                )
            }
        }
    }
}

@Composable
private fun NotificationFooter(
    state: NotificationCenterUiState.Content,
    onLoadMore: () -> Unit
) {
    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
    if (state.hasMore) {
        LaunchedEffect(state.page.size, state.query, state.filters, state.sort) {
            onLoadMore()
        }
        Text(
            text = "Showing ${state.page.size} of ${state.visible.size} delivery records...",
            style = NexaType.Metadata,
            color = NexaTextMuted
        )
    } else {
        Text(
            text = "End of the retrieved delivery records · ${countLabel(state.visible.size, "record")}",
            style = NexaType.Metadata,
            color = NexaTextMuted
        )
    }
}

/**
 * The empty state.
 *
 * Reports the contents of the delivery record only. This is not the event
 * store, so it never says anything about whether security events exist.
 */

@Composable
private fun DeliveryFreshness(freshness: DataFreshness) {
    val stale = !freshness.isTrustworthy
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (stale) {
            NexaIcon(icon = NexaIcons.Stale, size = NexaTokens.IconSmall, tint = NexaWarning)
            Spacer(modifier = Modifier.width(NexaTokens.SpacingXSmall))
        }
        Text(
            text = freshness.label,
            style = NexaType.Metadata,
            color = if (stale) NexaWarning else NexaTextMuted,
            // Truncates rather than wrapping the header onto a second line.
            // The same freshness is restated in full below it.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Keeps the trailing column from crowding the delivery outcome. */
private val SOURCE_STATE_MAX_WIDTH = 148.dp

private fun NotificationSort.next(): NotificationSort = when (this) {
    NotificationSort.Attention -> NotificationSort.Newest
    NotificationSort.Newest -> NotificationSort.Oldest
    NotificationSort.Oldest -> NotificationSort.Attention
}

private fun deliverySummaryLine(state: NotificationCenterUiState.Content): String {
    val s = state.summary
    val parts = buildList {
        add(resultCountLabel(state.visible.size, state.all.size, "record"))
        if (s.failed > 0) add("${s.failed} failed")
        if (s.exhausted > 0) add("${s.exhausted} exhausted")
        if (s.retrying > 0) add("${s.retrying} retrying")
        if (s.pending > 0) add("${s.pending} pending")
        if (s.delivered > 0) add("${s.delivered} delivered")
    }
    return parts.joinToString(" · ")
}
