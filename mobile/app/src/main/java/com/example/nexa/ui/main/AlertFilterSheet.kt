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
import com.example.nexa.ui.alerts.AlertFilters
import com.example.nexa.ui.alerts.AlertLifecycle
import com.example.nexa.ui.alerts.AlertSeverity
import com.example.nexa.ui.alerts.AlertSort
import com.example.nexa.ui.alerts.DeliveryState
import com.example.nexa.ui.alerts.label
import com.example.nexa.ui.components.NexaBottomSheet
import com.example.nexa.ui.components.NexaFilterChip
import com.example.nexa.ui.components.NexaOutlinedButton
import com.example.nexa.ui.components.SectionHeader
import com.example.nexa.ui.components.SectionLevel

/**
 * Alert filtering and ordering.
 *
 * Severity, alert lifecycle and notification delivery are three separate
 * filter groups, labelled as such — the sheet is the clearest place to show
 * that they are independent axes rather than one status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertFilterSheet(
    filters: AlertFilters,
    sort: AlertSort,
    scopes: List<String>,
    onFiltersChange: (AlertFilters) -> Unit,
    onSortChange: (AlertSort) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    NexaBottomSheet(onDismissRequest = onDismiss, title = "Filter alerts") {
        Column(modifier = Modifier.fillMaxWidth()) {

            Group(title = "Sort by") {
                AlertSort.entries.forEach { option ->
                    NexaFilterChip(
                        label = option.sortLabel,
                        selected = sort == option,
                        onClick = { onSortChange(option) }
                    )
                }
            }

            Group(title = "Severity") {
                AlertSeverity.entries.forEach { value ->
                    NexaFilterChip(
                        label = value.label,
                        selected = value in filters.severity,
                        onClick = {
                            onFiltersChange(filters.copy(severity = filters.severity.toggle(value)))
                        }
                    )
                }
            }

            Group(title = "Alert state") {
                AlertLifecycle.entries.forEach { value ->
                    NexaFilterChip(
                        label = value.label,
                        selected = value in filters.lifecycle,
                        onClick = {
                            onFiltersChange(filters.copy(lifecycle = filters.lifecycle.toggle(value)))
                        }
                    )
                }
            }

            Group(
                title = "Notification delivery",
                note = "Delivery is separate from the alert's own state."
            ) {
                DeliveryState.entries.forEach { value ->
                    NexaFilterChip(
                        label = value.label,
                        selected = value in filters.delivery,
                        onClick = {
                            onFiltersChange(filters.copy(delivery = filters.delivery.toggle(value)))
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

private val AlertSort.sortLabel: String
    get() = when (this) {
        AlertSort.Attention -> "Needs attention"
        AlertSort.Newest -> "Newest"
        AlertSort.Severity -> "Severity"
    }

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
