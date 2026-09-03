package com.example.nexa.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.nexa.AlertDetail
import com.example.nexa.NotificationCenter
import com.example.nexa.theme.*
import com.example.nexa.ui.alerts.*
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.contentAvailability
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

/**
 * The incident load.
 *
 * Built to answer "what needs my attention?" rather than to list
 * notifications. Open incidents and closed history are separate views, and
 * an alert's own state is always shown apart from what happened to its
 * notification.
 */
@Composable
fun AlertsScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlertsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is AlertsUiState.Loading ->
            NexaListLoading(message = "Reading alert state...", modifier = modifier)

        is AlertsUiState.Offline ->
            OfflineState(
                message = "No connection. Alerts shown elsewhere were last confirmed earlier and may not reflect the current incident load.",
                modifier = modifier,
                action = { RetryAlertsButton(viewModel::refresh) }
            )

        is AlertsUiState.Unavailable ->
            UnavailableState(
                title = "Alert state unavailable",
                message = "NEXA cannot reach the alert service. The current incident load is unknown — this is not a report that there are no alerts.",
                modifier = modifier,
                action = { RetryAlertsButton(viewModel::refresh) }
            )

        is AlertsUiState.Error ->
            ErrorState(
                title = "Could not load alerts",
                message = current.message,
                modifier = modifier,
                action = { RetryAlertsButton(viewModel::refresh) }
            )

        is AlertsUiState.Content ->
            AlertsContent(
                state = current,
                onQueryChange = viewModel::onQueryChange,
                onFiltersChange = viewModel::onFiltersChange,
                onSortChange = viewModel::onSortChange,
                onViewChange = viewModel::onViewChange,
                onClearFilters = viewModel::clearFilters,
                onClearQuery = viewModel::clearQuery,
                onRefresh = viewModel::refresh,
                onItemClick = onItemClick,
                modifier = modifier
            )
    }
}

@Composable
private fun RetryAlertsButton(onRetry: () -> Unit) {
    NexaOutlinedButton(
        text = "Retry",
        onClick = onRetry,
        icon = NexaIcons.Refresh
    )
}

@Composable
private fun AlertsContent(
    state: AlertsUiState.Content,
    onQueryChange: (String) -> Unit,
    onFiltersChange: (AlertFilters) -> Unit,
    onSortChange: (AlertSort) -> Unit,
    onViewChange: (AlertScopeView) -> Unit,
    onClearFilters: () -> Unit,
    onClearQuery: () -> Unit,
    onRefresh: () -> Unit,
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    var showFilters by remember { mutableStateOf(false) }

    NexaScreen(modifier = modifier) {
        item {
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Alerts", style = NexaType.Display, color = NexaTextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // One status line, not two. While a check is running it is the more
                    // useful thing to say, and the freshness it replaces is about to be
                    // restated anyway.
                    HeaderStatus {
                        if (state.refreshing) {
                            RefreshingIndicator()
                        } else {
                            AlertsFreshness(state.freshness)
                        }
                    }
                    // The control that acts on the label beside it. Disabled while a
                    // check is already running, so a second tap cannot start a second.
                    NexaIconButton(
                        icon = NexaIcons.Refresh,
                        onClick = onRefresh,
                        enabled = !state.refreshing,
                        contentDescription = "Refresh alert state"
                    )
                    // The entry point to delivery intelligence. It lives here
                    // rather than in a root tab of its own: delivery is a
                    // property of the messages incidents produce, not a
                    // security surface with equal standing.
                    NexaIconButton(
                        icon = NexaIcons.NotificationDelivery,
                        onClick = { onItemClick(NotificationCenter) },
                        contentDescription = "Notification delivery"
                    )
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
            Text(
                text = summaryLine(state),
                // Said with commas rather than middle dots.
                modifier = Modifier.semantics {
                    contentDescription = spokenSummaryLine(summaryLine(state))
                },
                style = NexaType.Metadata,
                color = NexaTextSecondary
            )
            // Notification delivery is reported as its own operational fact,
            // never folded into the incident counts above.
            if (state.summary.deliveryFailures > 0) {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingHairline))
                // Also the way in: an operator who has just read that messages
                // failed is exactly the operator who wants the delivery record.
                val interactionSource = remember { MutableInteractionSource() }
                val pressScale = rememberPressScale(interactionSource)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = NexaTokens.MinTouchTarget)
                        // The target is taller than the line of text it wraps,
                        // so a bounded ripple would draw a rectangle around
                        // empty space. The line answers the press itself.
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = "Open notification delivery",
                            onClick = { onItemClick(NotificationCenter) }
                        )
                        .pressResponse(pressScale)
                ) {
                    Text(
                        text = "${countLabel(state.summary.deliveryFailures, "notification delivery failure")} — alerts themselves are unaffected",
                        style = NexaType.Metadata,
                        color = NexaWarning,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(NexaTokens.SpacingXSmall))
                    NexaIcon(
                        icon = NexaIcons.Forward,
                        size = NexaTokens.IconSmall,
                        tint = NexaWarning
                    )
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
        }

        // One notice, shared wording. Stale and incomplete are different
        // failures and the operator is told which one this is.
        val availability = contentAvailability(
            freshness = state.freshness,
            isEmpty = state.all.isEmpty(),
            degraded = state.degraded,
            offline = state.offline
        )
        if (availability.warrantsNotice) {
            item {
                AvailabilityNotice(
                    availability = availability,
                    subject = "alert state",
                    detail = if (state.degraded) {
                        "Some alerts may be missing from this list. Do not treat it as the complete incident load."
                    } else {
                        state.freshness.label
                    }
                )
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            }
        }

        // Open vs history: the operator always knows which they are reading.
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingSmall),
                modifier = Modifier.fillMaxWidth()
            ) {
                AlertScopeView.entries.forEach { view ->
                    NexaFilterChip(
                        label = view.viewLabel,
                        selected = state.view == view,
                        onClick = { onViewChange(view) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
        }

        item {
            NexaSearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = "Search alert, device, MAC, scope",
                label = "Search alerts"
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
                    label = "Critical",
                    selected = AlertSeverity.Critical in state.filters.severity,
                    onClick = { onFiltersChange(state.filters.toggleSeverity(AlertSeverity.Critical)) }
                )
                NexaFilterChip(
                    label = "Unacknowledged",
                    selected = AlertLifecycle.New in state.filters.lifecycle,
                    onClick = { onFiltersChange(state.filters.toggleLifecycle(AlertLifecycle.New)) }
                )
                NexaFilterChip(
                    label = "Delivery failed",
                    selected = state.filters.onlyDeliveryFailures,
                    onClick = {
                        onFiltersChange(state.filters.copy(onlyDeliveryFailures = !state.filters.onlyDeliveryFailures))
                    }
                )
                if (state.filters.isActive) {
                    NexaFilterChip(label = "Clear", selected = false, onClick = onClearFilters)
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
        }

        if (state.visible.isEmpty()) {
            item { NoMatchNotice(
                    results = nexaResults(
                        sourceCount = state.all.applyView(state.view).size,
                        visibleCount = state.visible.size,
                        queryActive = nexaQuery(state.query).isActive,
                        filtersActive = state.filters.isActive
                    ),
                    subject = "alerts",
                    emptyTitle = when (state.view) {
                        AlertScopeView.History -> "No closed alerts"
                        else -> "No active alerts"
                    },
                    emptyMessage = when (state.view) {
                        AlertScopeView.History -> "No alerts have been resolved or ignored."
                        else -> "Nothing currently requires attention. This reports the alert load only — it is not an assessment of overall system posture."
                    },
                    onClearSearch = onClearQuery,
                    onClearFilters = onClearFilters
                ) }
        } else {
            items(state.visible, key = { it.id }) { alert ->
                AlertRow(alert = alert, onClick = { onItemClick(AlertDetail(alert.id)) })
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            }
        }

        item { Spacer(modifier = Modifier.height(NexaTokens.NavigationContentClearance)) }
    }

    if (showFilters) {
        AlertFilterSheet(
            filters = state.filters,
            sort = state.sort,
            scopes = state.all.mapNotNull { it.target.deviceRef?.scope }.distinct().sorted(),
            onFiltersChange = onFiltersChange,
            onSortChange = onSortChange,
            onClear = onClearFilters,
            onDismiss = { showFilters = false }
        )
    }
}

/**
 * One alert.
 *
 * The trailing badge is always the alert's own lifecycle. A notification
 * problem appears as a separate, explicitly-worded marker so the two states
 * can never be read as one.
 */
@Composable
private fun AlertRow(alert: AlertListItem, onClick: () -> Unit) {
    val deliveryWarning = rowDeliveryWarning(alert)

    NexaListRow(
        title = alert.title,
        onClick = onClick,
        actionLabel = "Open alert ${alert.id}",
        trailingDescription = listOfNotNull(
            alert.lifecycle.label,
            deliveryWarning,
            alert.createdLabel
        ).joinToString(", "),
        variant = surfaceFor(alert),
        leadingIcon = alert.severity.icon,
        leadingTint = alert.severity.status.style.onLight,
        leadingContentDescription = "${alert.severity.label} severity",
        titleStyle = if (alert.severity == AlertSeverity.Critical) NexaType.Title else NexaType.Body,
        titleColor = if (alert.lifecycle.isOpen) NexaTextPrimary else NexaTextSecondary,
        secondary = alertSubtitle(alert),
        technical = alert.id,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                StatusBadge(
                    status = alert.lifecycle.status,
                    label = alert.lifecycle.label.uppercase()
                )
                if (deliveryWarning != null) {
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                    Text(
                        text = deliveryWarning,
                        style = NexaType.Metadata,
                        color = NexaWarning
                    )
                }
                Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                Text(text = alert.createdLabel, style = NexaType.Metadata, color = NexaTextMuted)
            }
        }
    )
}

/**
 * The empty state.
 *
 * Says only what the alert service actually reported. An empty incident
 * load is not evidence that the system is secure, so it never claims that.
 */

@Composable
private fun AlertsFreshness(freshness: DataFreshness) {
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

private val AlertScopeView.viewLabel: String
    get() = when (this) {
        AlertScopeView.Open -> "Open"
        AlertScopeView.History -> "History"
        AlertScopeView.All -> "All"
    }

private fun summaryLine(state: AlertsUiState.Content): String {
    val s = state.summary
    // Counted against the slice being viewed, not the whole load: "3 of 5
    // open" is true, "3 of 12 open" would silently compare against closed
    // alerts the operator did not ask to see.
    // Counted rather than built. applyView allocates the whole slice, and
    // this needed only its size — on a screen that recomposes whenever a chip
    // is pressed or an alert arrives.
    val inView = state.all.countInView(state.view)
    val base = when (state.view) {
        AlertScopeView.Open -> resultCountLabel(state.visible.size, inView, "open", "open")
        AlertScopeView.History -> resultCountLabel(state.visible.size, inView, "closed", "closed")
        AlertScopeView.All -> resultCountLabel(state.visible.size, inView, "alert")
    }
    val counts = if (state.view == AlertScopeView.Open && s.open > 0) {
        "$base · ${s.critical} critical · ${s.unacknowledged} unacknowledged"
    } else {
        base
    }
    // When part of the alert state could not be retrieved, these are the
    // alerts NEXA can see, not the alerts that exist. The banner above says
    // why; the count is what an operator reads first, so it says so too.
    return if (state.degraded) "$counts · visible only" else counts
}

private fun AlertFilters.toggleSeverity(value: AlertSeverity) =
    copy(severity = if (value in severity) severity - value else severity + value)

private fun AlertFilters.toggleLifecycle(value: AlertLifecycle) =
    copy(lifecycle = if (value in lifecycle) lifecycle - value else lifecycle + value)
