package com.example.nexa.ui.main

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.nexa.theme.NexaTokens
import com.example.nexa.ui.components.FilterGroup
import com.example.nexa.ui.components.NexaBottomSheet
import com.example.nexa.ui.components.NexaFilterChip
import com.example.nexa.ui.components.NexaOutlinedButton
import com.example.nexa.ui.components.SectionLevel
import com.example.nexa.ui.components.SectionHeader
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.devices.DeviceFilters
import com.example.nexa.ui.devices.DeviceSort
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.devices.Presence
import com.example.nexa.ui.devices.label
import com.example.nexa.ui.common.label
import com.example.nexa.ui.common.toggleFacet

/**
 * Device filtering and ordering.
 *
 * Every filter here corresponds to state the model actually carries —
 * presence, trust, enforcement and scope — so the sheet cannot offer a
 * filter the inventory has no answer for. Selection is always visible, and
 * a single Clear returns to the full list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceFilterSheet(
    filters: DeviceFilters,
    sort: DeviceSort,
    scopes: List<String>,
    onFiltersChange: (DeviceFilters) -> Unit,
    onSortChange: (DeviceSort) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    NexaBottomSheet(onDismissRequest = onDismiss, title = "Filter devices") {
        Column(modifier = Modifier.fillMaxWidth()) {

            FilterGroup(title = "Sort by") {
                DeviceSort.entries.forEach { option ->
                    NexaFilterChip(
                        label = option.label,
                        selected = sort == option,
                        onClick = { onSortChange(option) }
                    )
                }
            }

            FilterGroup(title = "Presence") {
                Presence.entries.forEach { value ->
                    NexaFilterChip(
                        label = value.label,
                        selected = value in filters.presence,
                        onClick = {
                            onFiltersChange(
                                filters.copy(
                                    presence = filters.presence.toggleFacet(value)
                                )
                            )
                        }
                    )
                }
            }

            FilterGroup(title = "Trust") {
                TrustState.entries.forEach { value ->
                    NexaFilterChip(
                        label = value.label,
                        selected = value in filters.trust,
                        onClick = {
                            onFiltersChange(filters.copy(trust = filters.trust.toggleFacet(value)))
                        }
                    )
                }
            }

            FilterGroup(title = "Enforcement") {
                DeviceEnforcement.entries.forEach { value ->
                    NexaFilterChip(
                        label = value.label,
                        selected = value in filters.enforcement,
                        onClick = {
                            onFiltersChange(filters.copy(enforcement = filters.enforcement.toggleFacet(value)))
                        }
                    )
                }
            }

            if (scopes.isNotEmpty()) {
                FilterGroup(title = "Network scope") {
                    scopes.forEach { scope ->
                        NexaFilterChip(
                            label = scope,
                            selected = scope in filters.scopes,
                            onClick = {
                                onFiltersChange(filters.copy(scopes = filters.scopes.toggleFacet(scope)))
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
