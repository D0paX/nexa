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
import com.example.nexa.ui.components.NexaBottomSheet
import com.example.nexa.ui.components.NexaFilterChip
import com.example.nexa.ui.components.NexaOutlinedButton
import com.example.nexa.ui.components.SectionLevel
import com.example.nexa.ui.components.SectionHeader
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.devices.DeviceFilters
import com.example.nexa.ui.devices.DeviceSort
import com.example.nexa.ui.devices.DeviceTrust
import com.example.nexa.ui.devices.Presence
import com.example.nexa.ui.devices.label

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
                        label = option.sortLabel,
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
                                    presence = filters.presence.toggle(value)
                                )
                            )
                        }
                    )
                }
            }

            FilterGroup(title = "Trust") {
                DeviceTrust.entries.forEach { value ->
                    NexaFilterChip(
                        label = value.label,
                        selected = value in filters.trust,
                        onClick = {
                            onFiltersChange(filters.copy(trust = filters.trust.toggle(value)))
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
                            onFiltersChange(filters.copy(enforcement = filters.enforcement.toggle(value)))
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
                                onFiltersChange(filters.copy(scopes = filters.scopes.toggle(scope)))
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

@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    SectionHeader(text = title, level = SectionLevel.Group)
    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
    Row(
        horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingSmall),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        content()
    }
    Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
}

private val DeviceSort.sortLabel: String
    get() = when (this) {
        DeviceSort.Attention -> "Needs attention"
        DeviceSort.Name -> "Name"
        DeviceSort.LastSeen -> "Presence"
    }

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
