package com.example.nexa.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.nexa.theme.NexaTokens
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.common.label
import com.example.nexa.ui.common.toggleFacet
import com.example.nexa.ui.components.FilterGroup
import com.example.nexa.ui.components.NexaBottomSheet
import com.example.nexa.ui.components.NexaFilterChip
import com.example.nexa.ui.components.NexaOutlinedButton
import com.example.nexa.ui.identity.IdentityFilters
import com.example.nexa.ui.identity.IdentityFreshnessFacet
import com.example.nexa.ui.identity.IdentityRelationship
import com.example.nexa.ui.identity.IdentitySort
import com.example.nexa.ui.identity.label

/**
 * Identity filtering and ordering.
 *
 * Identities were the one domain still filtering from a row of inline chips
 * with a single facet. They now use the same sheet, the same grouping and the
 * same clear control as every other surface.
 *
 * Two things this sheet deliberately does not offer:
 *
 *  - Anything derived from key material. The credential appears as an
 *    identifier and a lifecycle state; the key itself is not on the model and
 *    is not filterable, searchable or displayable.
 *  - Authorization. Trust is a lifecycle facet here. Selecting "Trusted"
 *    narrows what is listed and says nothing about what may be done to any of
 *    it — that remains the authorization engine's answer, evaluated when an
 *    action is prepared.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityFilterSheet(
    filters: IdentityFilters,
    sort: IdentitySort,
    scopes: List<String>,
    onFiltersChange: (IdentityFilters) -> Unit,
    onSortChange: (IdentitySort) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    NexaBottomSheet(onDismissRequest = onDismiss, title = "Filter identities") {
        Column(modifier = Modifier.fillMaxWidth()) {

            FilterGroup(title = "Sort by") {
                IdentitySort.entries.forEach { option ->
                    NexaFilterChip(
                        label = option.label,
                        selected = sort == option,
                        onClick = { onSortChange(option) }
                    )
                }
            }

            // Only the lifecycle states an identity can actually hold. Trust
            // states that belong to observed devices without identities are
            // not offered, because no identity can be in them.
            FilterGroup(title = "Trust") {
                listOf(TrustState.Trusted, TrustState.Pending, TrustState.Revoked).forEach { value ->
                    NexaFilterChip(
                        label = value.label,
                        selected = value in filters.trust,
                        onClick = {
                            onFiltersChange(filters.copy(trust = filters.trust.toggleFacet(value)))
                        }
                    )
                }
            }

            FilterGroup(title = "Binding") {
                IdentityRelationship.entries.forEach { value ->
                    NexaFilterChip(
                        label = value.label,
                        selected = value in filters.relationship,
                        onClick = {
                            onFiltersChange(
                                filters.copy(relationship = filters.relationship.toggleFacet(value))
                            )
                        }
                    )
                }
            }

            FilterGroup(title = "Verification") {
                IdentityFreshnessFacet.entries.forEach { value ->
                    NexaFilterChip(
                        label = value.label,
                        selected = value in filters.freshness,
                        onClick = {
                            onFiltersChange(
                                filters.copy(freshness = filters.freshness.toggleFacet(value))
                            )
                        }
                    )
                }
            }

            if (scopes.isNotEmpty()) {
                FilterGroup(title = "Scope") {
                    scopes.forEach { scope ->
                        NexaFilterChip(
                            label = scope,
                            selected = scope in filters.scopes,
                            onClick = {
                                onFiltersChange(
                                    filters.copy(scopes = filters.scopes.toggleFacet(scope))
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))

            NexaOutlinedButton(
                text = "Clear all filters",
                onClick = onClear,
                enabled = filters.isActive
            )
        }
    }
}
