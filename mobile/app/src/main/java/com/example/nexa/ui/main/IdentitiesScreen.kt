package com.example.nexa.ui.main

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.nexa.IdentityDetail
import com.example.nexa.theme.*
import com.example.nexa.ui.common.DataFreshness
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
            LoadingState(message = "Reading identity state...", modifier = modifier)

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
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
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
                    style = NexaType.Metadata,
                    color = NexaTextSecondary
                )
                FreshnessTag(state.freshness)
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
        }

        if (state.degraded) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NexaIcon(icon = NexaIcons.Warning, size = NexaTokens.IconMedium, tint = NexaWarning)
                        Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                        Column {
                            Text("Identity subsystem degraded", style = NexaType.Title, color = NexaTextPrimary)
                            Spacer(modifier = Modifier.height(NexaTokens.SpacingHairline))
                            Text(
                                text = "Some identities may be missing. Do not treat this as the complete set.",
                                style = NexaType.BodySecondary,
                                color = NexaTextSecondary
                            )
                        }
                    }
                }
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
                // Only lifecycle states an identity can actually hold.
                listOf(TrustState.Trusted, TrustState.Pending, TrustState.Revoked).forEach { value ->
                    NexaFilterChip(
                        label = value.label,
                        selected = value in state.filters.trust,
                        onClick = {
                            val trust = state.filters.trust
                            onFiltersChange(
                                state.filters.copy(
                                    trust = if (value in trust) trust - value else trust + value
                                )
                            )
                        }
                    )
                }
                if (state.filters.isActive) {
                    NexaFilterChip(
                        label = "Clear",
                        selected = false,
                        onClick = { onFiltersChange(IdentityFilters()) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
        }

        if (state.visible.isEmpty()) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = if (state.all.isEmpty()) "No trusted identities" else "No matching identities",
                            style = NexaType.Title,
                            color = NexaTextPrimary
                        )
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                        Text(
                            text = if (state.all.isEmpty()) {
                                "NEXA holds no cryptographic identities. The identity service responded — this is a confirmed empty set, not a failure to read it."
                            } else {
                                "No identity matches the current search or filters."
                            },
                            style = NexaType.BodySecondary,
                            color = NexaTextSecondary
                        )
                    }
                }
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
}

@Composable
private fun IdentityRow(identity: IdentitySummary, onClick: () -> Unit) {
    val badge = identityAttentionBadge(identity)
    val badgeLabel = identityAttentionLabel(identity)

    NexaListRow(
        title = identity.subjectLabel,
        onClick = onClick,
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
    val total = state.all.size
    val shown = state.visible.size
    val base = if (shown == total) "$total cryptographic identities" else "$shown of $total identities"
    val attention = state.visible.count { identityAttentionBadge(it) != null }
    return if (attention > 0) "$base · $attention need attention" else base
}
