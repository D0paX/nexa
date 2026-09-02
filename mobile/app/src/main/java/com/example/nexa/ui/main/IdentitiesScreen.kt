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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.nexa.IdentityDetail
import com.example.nexa.theme.*
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.contentAvailability
import com.example.nexa.ui.common.filterButtonLabel
import com.example.nexa.ui.common.filterCountSpoken
import com.example.nexa.ui.common.spokenSummaryLine
import com.example.nexa.ui.common.nexaQuery
import com.example.nexa.ui.common.nexaResults
import com.example.nexa.ui.common.resultCountLabel
import com.example.nexa.ui.common.toggleFacet
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.common.icon
import com.example.nexa.ui.common.isTrustworthy
import com.example.nexa.ui.common.label
import com.example.nexa.ui.common.status
import com.example.nexa.ui.components.*
import com.example.nexa.ui.identity.*

/**
 * The cryptographic identity inventory.
 *
 * Deliberately not a second device list: this surface answers "what does
 * NEXA cryptographically know about?", where Devices answers "what has NEXA
 * seen on the network?". Devices without identities do not appear here, and
 * that absence is itself the point.
 */
@Composable
fun IdentitiesScreen(
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IdentitiesViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is IdentitiesUiState.Loading ->
            NexaListLoading(message = "Reading identity state...", modifier = modifier)

        is IdentitiesUiState.Offline ->
            OfflineState(
                title = "Offline",
                message = "No connection. Identity standing shown elsewhere was last confirmed earlier and may no longer hold.",
                modifier = modifier,
                action = { RetryControl(viewModel::refresh) }
            )

        is IdentitiesUiState.Unavailable ->
            UnavailableState(
                title = "Identity state unavailable",
                message = "NEXA cannot reach the identity service. Trust standing is currently unknown — this is not a report that no identities exist.",
                modifier = modifier,
                action = { RetryControl(viewModel::refresh) }
            )

        is IdentitiesUiState.Error ->
            ErrorState(
                title = "Could not load identities",
                message = current.message,
                modifier = modifier,
                action = { RetryControl(viewModel::refresh) }
            )

        is IdentitiesUiState.Content ->
            IdentitiesContent(
                state = current,
                onBack = onBack,
                onQueryChange = viewModel::onQueryChange,
                onFiltersChange = viewModel::onFiltersChange,
                onSortChange = viewModel::onSortChange,
                onClearFilters = viewModel::clearFilters,
                onClearQuery = viewModel::clearQuery,
                onRefresh = viewModel::refresh,
                onNavigate = onNavigate,
                modifier = modifier
            )
    }
}

@Composable
private fun RetryControl(onRetry: () -> Unit) {
    NexaOutlinedButton(
        text = "Retry",
        onClick = onRetry,
        icon = NexaIcons.Refresh,
        modifier = Modifier.widthIn(max = 240.dp)
    )
}

@Composable
private fun IdentitiesContent(
    state: IdentitiesUiState.Content,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFiltersChange: (IdentityFilters) -> Unit,
    onSortChange: (IdentitySort) -> Unit,
    onClearFilters: () -> Unit,
    onClearQuery: () -> Unit,
    onRefresh: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    var showFilters by remember { mutableStateOf(false) }

    NexaScreen(
        modifier = modifier,
        title = "Identities",
        onBack = onBack
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = identityCountLabel(state),
                    // Said with commas rather than middle dots.
                    modifier = Modifier.semantics {
                        contentDescription = spokenSummaryLine(identityCountLabel(state))
                    },
                    style = NexaType.Metadata,
                    color = NexaTextSecondary
                )
                // One status line, not two. While a check is running it is the more
                // useful thing to say, and the freshness it replaces is about to be
                // restated anyway.
                if (state.refreshing) {
                    RefreshingIndicator()
                } else {
                    FreshnessTag(state.freshness)
                }
                // The control that acts on the label beside it. Disabled while a
                // check is already running, so a second tap cannot start a second.
                NexaIconButton(
                    icon = NexaIcons.Refresh,
                    onClick = onRefresh,
                    enabled = !state.refreshing,
                    contentDescription = "Refresh identity state"
                )
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
        }

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
                    subject = "identity state",
                    detail = if (state.degraded) {
                        "Some identities may be missing. Do not treat this as the complete set."
                    } else {
                        state.freshness.label
                    }
                )
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            }
        }

        item {
            NexaSearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = "Search identity, owner, MAC, scope",
                label = "Search identities"
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
                // Same filter language as every other domain: the sheet
                // behind a counted button, then a few shortcuts for the
                // narrowings an operator reaches for most.
                NexaFilterChip(
                    label = filterButtonLabel(state.filters.activeCount),
                    spokenLabel = filterCountSpoken(state.filters.activeCount),
                    selected = state.filters.isActive,
                    onClick = { showFilters = true }
                )
                NexaFilterChip(
                    label = TrustState.Revoked.label,
                    selected = TrustState.Revoked in state.filters.trust,
                    onClick = {
                        onFiltersChange(
                            state.filters.copy(
                                trust = state.filters.trust.toggleFacet(TrustState.Revoked)
                            )
                        )
                    }
                )
                NexaFilterChip(
                    label = "Ambiguous binding",
                    selected = IdentityRelationship.Ambiguous in state.filters.relationship,
                    onClick = {
                        onFiltersChange(
                            state.filters.copy(
                                relationship = state.filters.relationship
                                    .toggleFacet(IdentityRelationship.Ambiguous)
                            )
                        )
                    }
                )
                if (state.filters.isActive) {
                    NexaFilterChip(label = "Clear", selected = false, onClick = onClearFilters)
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
        }

        if (state.visible.isEmpty()) {
            item {
                NoMatchNotice(
                    results = nexaResults(
                        sourceCount = state.all.size,
                        visibleCount = state.visible.size,
                        queryActive = nexaQuery(state.query).isActive,
                        filtersActive = state.filters.isActive
                    ),
                    subject = "identities",
                    emptyTitle = "No trusted identities",
                    emptyMessage = "NEXA holds no cryptographic identities. The identity service responded — this is a confirmed empty set, not a failure to read it.",
                    onClearSearch = onClearQuery,
                    onClearFilters = onClearFilters
                )
            }
        } else {
            items(state.visible, key = { it.identityId }) { identity ->
                IdentityRow(
                    identity = identity,
                    onClick = { onNavigate(IdentityDetail(identity.identityId)) }
                )
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            }
        }

        item { Spacer(modifier = Modifier.height(NexaTokens.SpacingXLarge)) }
    }

    if (showFilters) {
        IdentityFilterSheet(
            filters = state.filters,
            sort = state.sort,
            scopes = state.all.mapNotNull { it.device?.scope }.distinct().sorted(),
            onFiltersChange = onFiltersChange,
            onSortChange = onSortChange,
            onClear = onClearFilters,
            onDismiss = { showFilters = false }
        )
    }
}

@Composable
private fun IdentityRow(identity: IdentitySummary, onClick: () -> Unit) {
    val badge = identityAttentionBadge(identity)
    val badgeLabel = identityAttentionLabel(identity)

    NexaListRow(
        title = identity.subjectLabel,
        onClick = onClick,
        actionLabel = "Open identity ${identity.identityId}",
        trailingDescription = badgeLabel,
        variant = if (badge == NexaStatus.Danger || badge == NexaStatus.Critical) {
            GlassVariant.Strong
        } else {
            GlassVariant.Standard
        },
        leadingIcon = identity.trust.icon,
        leadingTint = identity.trust.status.style.onLight,
        leadingContentDescription = identity.trust.label,
        titleStyle = NexaType.Title,
        titleColor = NexaTextPrimary,
        secondary = identitySubtitle(identity),
        technical = identity.identityId,
        timestamp = if (badge == null) identity.verification.lastVerifiedLabel else null,
        trailing = if (badge != null && badgeLabel != null) {
            { StatusBadge(status = badge, label = badgeLabel) }
        } else {
            null
        }
    )
}

@Composable
private fun FreshnessTag(freshness: DataFreshness) {
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

private fun identityCountLabel(state: IdentitiesUiState.Content): String {
    // When part of the set could not be retrieved these are the identities
    // NEXA can see, not the identities that exist.
    val base = if (state.degraded) {
        resultCountLabel(state.visible.size, state.all.size, "identity visible", "identities visible")
    } else {
        resultCountLabel(state.visible.size, state.all.size, "cryptographic identity", "cryptographic identities")
    }
    val attention = state.visible.count { identityAttentionBadge(it) != null }
    return if (attention > 0) "$base · $attention need attention" else base
}
