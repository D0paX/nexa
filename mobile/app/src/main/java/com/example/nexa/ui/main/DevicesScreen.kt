package com.example.nexa.ui.main

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.nexa.DeviceDetail
import com.example.nexa.Identities
import com.example.nexa.theme.*
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.isTrustworthy
import com.example.nexa.ui.common.label
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.components.*
import com.example.nexa.ui.devices.*

/**
 * The device inventory.
 *
 * Built for scanning rather than for reading: each row leads with what the
 * operator needs to recognize the device, and marks it only when something
 * about it is outstanding. Presence, trust and enforcement stay three
 * separate facts throughout.
 */
@Composable
fun DevicesScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DevicesViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is DevicesUiState.Loading ->
            LoadingState(message = "Reading device inventory...", modifier = modifier)

        is DevicesUiState.Offline ->
            OfflineState(
                modifier = modifier,
                action = { RetryButton(viewModel::refresh) }
            )

        is DevicesUiState.Unavailable ->
            UnavailableState(
                title = "Inventory unavailable",
                message = "NEXA cannot reach the device inventory. The current set of devices is unknown — this is not a report that no devices are present.",
                modifier = modifier,
                action = { RetryButton(viewModel::refresh) }
            )

        is DevicesUiState.Error ->
            ErrorState(
                title = "Could not load devices",
                message = current.message,
                modifier = modifier,
                action = { RetryButton(viewModel::refresh) }
            )

        is DevicesUiState.Content ->
            DevicesContent(
                state = current,
                onQueryChange = viewModel::onQueryChange,
                onFiltersChange = viewModel::onFiltersChange,
                onSortChange = viewModel::onSortChange,
                onClearFilters = viewModel::clearFilters,
                onItemClick = onItemClick,
                modifier = modifier
            )
    }
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    NexaOutlinedButton(
        text = "Retry",
        onClick = onRetry,
        icon = NexaIcons.Refresh,
        modifier = Modifier.widthIn(max = 240.dp)
    )
}

@Composable
private fun DevicesContent(
    state: DevicesUiState.Content,
    onQueryChange: (String) -> Unit,
    onFiltersChange: (DeviceFilters) -> Unit,
    onSortChange: (DeviceSort) -> Unit,
    onClearFilters: () -> Unit,
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
                Text(text = "Devices", style = NexaType.Display, color = NexaTextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FreshnessLabel(state.freshness)
                    // Entry to the cryptographic identity inventory — a different
                    // question from the network one this screen answers.
                    NexaIconButton(
                        icon = NexaIcons.Identity,
                        onClick = { onItemClick(Identities) },
                        contentDescription = "Cryptographic identities"
                    )
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
            Text(
                text = deviceCountLabel(state),
                style = NexaType.Metadata,
                color = NexaTextSecondary
            )
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
        }

        if (state.degraded) {
            item {
                DegradedNotice()
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            }
        }

        item {
            NexaSearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = "Search name, MAC, IP, scope",
                label = "Search devices"
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
                    label = "Needs attention",
                    selected = state.filters.onlyWithAlerts,
                    onClick = { onFiltersChange(state.filters.copy(onlyWithAlerts = !state.filters.onlyWithAlerts)) }
                )
                NexaFilterChip(
                    label = "Quarantined",
                    selected = DeviceEnforcement.Quarantined in state.filters.enforcement,
                    onClick = { onFiltersChange(state.filters.toggleEnforcement(DeviceEnforcement.Quarantined)) }
                )
                NexaFilterChip(
                    label = "Unverified",
                    selected = TrustState.Unverified in state.filters.trust,
                    onClick = { onFiltersChange(state.filters.toggleTrust(TrustState.Unverified)) }
                )
                if (state.filters.isActive) {
                    NexaFilterChip(label = "Clear", selected = false, onClick = onClearFilters)
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
        }

        if (state.visible.isEmpty()) {
            item {
                NoResults(
                    inventoryEmpty = state.all.isEmpty(),
                    query = state.query,
                    filtersActive = state.filters.isActive
                )
            }
        } else {
            items(state.visible, key = { it.id }) { device ->
                DeviceRow(device = device, onClick = { onItemClick(DeviceDetail(device.mac)) })
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            }
        }

        item {
            Spacer(modifier = Modifier.height(NexaTokens.NavigationContentClearance))
        }
    }

    if (showFilters) {
        DeviceFilterSheet(
            filters = state.filters,
            sort = state.sort,
            scopes = state.all.map { it.scope }.distinct().sorted(),
            onFiltersChange = onFiltersChange,
            onSortChange = onSortChange,
            onClear = onClearFilters,
            onDismiss = { showFilters = false }
        )
    }
}

/**
 * One device, scannable in a glance.
 *
 * The trailing slot carries a state badge only when the device has something
 * outstanding; otherwise it shows when the device was last seen. A healthy
 * device stays quiet rather than competing for attention.
 */
@Composable
private fun DeviceRow(device: DeviceListItem, onClick: () -> Unit) {
    val badge = attentionBadge(device)
    val badgeLabel = attentionLabel(device)

    NexaListRow(
        title = device.label,
        onClick = onClick,
        variant = if (badge == NexaStatus.Critical) GlassVariant.Strong else GlassVariant.Standard,
        leadingIcon = device.presence.icon,
        leadingTint = device.presence.status.style.onLight,
        leadingContentDescription = device.presence.label,
        titleStyle = NexaType.Title,
        titleColor = NexaTextPrimary,
        secondary = deviceSubtitle(device),
        technical = device.mac,
        timestamp = if (badge == null) device.lastSeenLabel else null,
        trailing = if (badge != null && badgeLabel != null) {
            { StatusBadge(status = badge, label = badgeLabel) }
        } else {
            null
        }
    )
}

/**
 * Distinguishes "your search matched nothing" from "there are no devices" —
 * and neither is ever shown when the inventory itself is unavailable.
 */
@Composable
private fun NoResults(inventoryEmpty: Boolean, query: String, filtersActive: Boolean) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = when {
                    inventoryEmpty -> "No devices in inventory"
                    else -> "No matching devices"
                },
                style = NexaType.Title,
                color = NexaTextPrimary
            )
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
            Text(
                text = when {
                    inventoryEmpty ->
                        "NEXA has not observed any devices. The inventory is confirmed empty, not unavailable."
                    query.isNotEmpty() && filtersActive ->
                        "No device matches \"$query\" with the current filters."
                    query.isNotEmpty() ->
                        "No device matches \"$query\"."
                    else ->
                        "No device matches the current filters."
                },
                style = NexaType.BodySecondary,
                color = NexaTextSecondary
            )
        }
    }
}

@Composable
private fun DegradedNotice() {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NexaIcon(
                icon = NexaIcons.Warning,
                size = NexaTokens.IconMedium,
                tint = NexaWarning
            )
            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
            Column {
                Text("Inventory degraded", style = NexaType.Title, color = NexaTextPrimary)
                Spacer(modifier = Modifier.height(NexaTokens.SpacingHairline))
                Text(
                    text = "Some devices may be missing from this list. Do not treat it as a complete inventory.",
                    style = NexaType.BodySecondary,
                    color = NexaTextSecondary
                )
            }
        }
    }
}

@Composable
private fun FreshnessLabel(freshness: DataFreshness) {
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

private fun deviceCountLabel(state: DevicesUiState.Content): String {
    val total = state.all.size
    val shown = state.visible.size
    val base = if (shown == total) "$total devices" else "$shown of $total devices"
    val attention = state.visible.count { attentionBadge(it) != null }
    return if (attention > 0) "$base · $attention need attention" else base
}

private fun DeviceFilters.toggleEnforcement(value: DeviceEnforcement) =
    copy(enforcement = if (value in enforcement) enforcement - value else enforcement + value)

private fun DeviceFilters.toggleTrust(value: TrustState) =
    copy(trust = if (value in trust) trust - value else trust + value)
