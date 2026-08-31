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
import com.example.nexa.ui.audit.AuditCategory
import com.example.nexa.ui.audit.AuditFilters
import com.example.nexa.ui.audit.AuditOutcome
import com.example.nexa.ui.audit.AuditSort
import com.example.nexa.ui.audit.AuditTimeRange
import com.example.nexa.ui.audit.auditLabel
import com.example.nexa.ui.audit.label
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.components.NexaBottomSheet
import com.example.nexa.ui.components.NexaFilterChip
import com.example.nexa.ui.components.NexaOutlinedButton
import com.example.nexa.ui.components.SectionHeader
import com.example.nexa.ui.components.SectionLevel

/**
 * History filtering and ordering.
 *
 * Event family, outcome and execution mode are three separate groups, labelled
 * as such. Keeping them apart in the sheet is the clearest possible statement
 * that they are independent axes: what kind of thing happened, how it ended,
 * and whether it touched anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditFilterSheet(
    filters: AuditFilters,
    sort: AuditSort,
    scopes: List<String>,
    onFiltersChange: (AuditFilters) -> Unit,
    onSortChange: (AuditSort) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    NexaBottomSheet(onDismissRequest = onDismiss, title = "Filter history") {
        Column(modifier = Modifier.fillMaxWidth()) {

            Group(title = "Order") {
                AuditSort.entries.forEach { option ->
                    NexaFilterChip(
                        label = option.label,
                        selected = sort == option,
                        onClick = { onSortChange(option) }
                    )
                }
            }

            Group(title = "Time range") {
                AuditTimeRange.entries.forEach { option ->
                    NexaFilterChip(
                        label = option.label,
                        selected = filters.timeRange == option,
                        onClick = { onFiltersChange(filters.copy(timeRange = option)) }
                    )
                }
            }

            Group(title = "Event category") {
                AuditCategory.entries.forEach { value ->
                    NexaFilterChip(
                        label = value.label,
                        selected = value in filters.categories,
                        onClick = {
                            onFiltersChange(filters.copy(categories = filters.categories.toggle(value)))
                        }
                    )
                }
            }

            Group(title = "Outcome") {
                AuditOutcome.entries.forEach { value ->
                    NexaFilterChip(
                        label = value.label,
                        selected = value in filters.outcomes,
                        onClick = {
                            onFiltersChange(filters.copy(outcomes = filters.outcomes.toggle(value)))
                        }
                    )
                }
            }

            Group(
                title = "Execution mode",
                note = "Applies to execution records only. Events that never had a mode are not live."
            ) {
                ExecutionMode.entries.forEach { value ->
                    NexaFilterChip(
                        label = value.auditLabel,
                        selected = value in filters.executionModes,
                        onClick = {
                            onFiltersChange(
                                filters.copy(executionModes = filters.executionModes.toggle(value))
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
