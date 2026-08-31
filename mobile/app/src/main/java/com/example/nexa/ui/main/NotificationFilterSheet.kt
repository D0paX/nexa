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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.nexa.theme.NexaTextMuted
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType
import com.example.nexa.ui.common.DeliveryState
import com.example.nexa.ui.common.label
import com.example.nexa.ui.components.NexaBottomSheet
import com.example.nexa.ui.components.NexaFilterChip
import com.example.nexa.ui.components.NexaOutlinedButton
import com.example.nexa.ui.components.SectionHeader
import com.example.nexa.ui.components.SectionLevel
import com.example.nexa.ui.notifications.NotificationFilters
import com.example.nexa.ui.notifications.NotificationSort
import com.example.nexa.ui.notifications.NotificationSourceType
import com.example.nexa.ui.notifications.NotificationTimeRange
import com.example.nexa.ui.notifications.label

/**
 * Delivery filtering and ordering.
 *
 * There is no channel group. Phase 3 declares a single delivery channel, and
 * a control that could only ever select everything would tell an operator the
 * system has destinations it does not have.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationFilterSheet(
    filters: NotificationFilters,
    sort: NotificationSort,
    scopes: List<String>,
    onFiltersChange: (NotificationFilters) -> Unit,
    onSortChange: (NotificationSort) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    NexaBottomSheet(onDismissRequest = onDismiss, title = "Filter delivery records") {
        Column(modifier = Modifier.fillMaxWidth()) {

            Group(title = "Order") {
                NotificationSort.entries.forEach { option ->
                    NexaFilterChip(
                        label = option.label,
                        selected = sort == option,
                        onClick = { onSortChange(option) }
                    )
                }
            }

            Group(title = "Time range") {
                NotificationTimeRange.entries.forEach { option ->
                    NexaFilterChip(
                        label = option.label,
                        selected = filters.timeRange == option,
                        onClick = { onFiltersChange(filters.copy(timeRange = option)) }
                    )
                }
            }

            Group(
                title = "Delivery state",
                note = "The state of the message, not of the incident it was about."
            ) {
                DeliveryState.entries.forEach { value ->
                    NexaFilterChip(
                        label = value.label,
                        selected = value in filters.states,
                        onClick = {
                            onFiltersChange(filters.copy(states = filters.states.toggle(value)))
                        }
                    )
                }
            }

            Group(title = "Source") {
                NotificationSourceType.entries.forEach { value ->
                    NexaFilterChip(
                        label = value.label,
                        selected = value in filters.sourceTypes,
                        onClick = {
                            onFiltersChange(
                                filters.copy(sourceTypes = filters.sourceTypes.toggle(value))
                            )
                        }
                    )
                }
            }

            if (scopes.isNotEmpty()) {
                Group(title = "Network scope") {
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
private fun Group(title: String, note: String? = null, content: @Composable () -> Unit) {
    SectionHeader(text = title, level = SectionLevel.Group)
    if (note != null) {
        Spacer(modifier = Modifier.height(NexaTokens.SpacingHairline))
        Text(text = note, style = NexaType.Metadata, color = NexaTextMuted)
    }
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

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
