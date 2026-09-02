package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTextSecondary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType
import com.example.nexa.ui.common.NexaNoMatchReason
import com.example.nexa.ui.common.NexaResults

/**
 * What a list says when the pipeline returned nothing.
 *
 * One component for all five domains, so "nothing matched" reads the same
 * everywhere and can never be confused with the availability surfaces in
 * [StateContainers]. The distinction it exists to hold:
 *
 *   no match     the operator asked a question with no answer here
 *   empty        the source answered, and the answer was nothing
 *   unavailable  NEXA could not read the source at all
 *
 * The third of those never reaches this component — it is decided before a
 * list is resolved. The first two are separated by [NexaResults].
 *
 * The recovery offered is the one that will actually help: clearing a search
 * when the search is what narrowed, clearing filters when the filters did,
 * and both when either could be. Nothing destructive is ever offered from a
 * no-match state — an operator who cannot find a device is not in a good
 * position to be handed an enforcement control.
 */
@Composable
fun NoMatchNotice(
    results: NexaResults,
    subject: String,
    emptyTitle: String,
    emptyMessage: String,
    onClearSearch: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (results is NexaResults.Present) return

    val reason = (results as? NexaResults.NoMatch)?.reason

    GlassSurface(modifier = modifier.fillMaxWidth()) {
        Column {
            Text(
                text = when (reason) {
                    null -> emptyTitle
                    NexaNoMatchReason.Search -> "No matching $subject"
                    NexaNoMatchReason.Filters -> "No $subject match these filters"
                    NexaNoMatchReason.SearchAndFilters -> "No matching $subject"
                },
                style = NexaType.Title,
                color = NexaTextPrimary
            )
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
            Text(
                text = when (reason) {
                    null -> emptyMessage
                    NexaNoMatchReason.Search ->
                        "Nothing here matches your search. The records are still present — the search simply did not reach any of them."
                    NexaNoMatchReason.Filters ->
                        "Records are present but none satisfy every active filter. Filters narrow what is shown; they do not remove anything."
                    NexaNoMatchReason.SearchAndFilters ->
                        "Nothing satisfies both the search and the active filters. Relaxing either may bring records back."
                },
                style = NexaType.BodySecondary,
                color = NexaTextSecondary
            )

            if (reason != null) {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                // Stacked rather than side by side: both labels carry an
                // icon, and half the width truncates them on a narrow screen
                // — a recovery control the operator cannot read is worse than
                // one that takes an extra row.
                Column(verticalArrangement = Arrangement.spacedBy(NexaTokens.SpacingSmall)) {
                    // Each control clears only its own concern. "Clear
                    // filters" that also wiped the search would silently undo
                    // work the operator did not ask to undo.
                    if (reason != NexaNoMatchReason.Filters) {
                        NexaOutlinedButton(
                            text = "Clear search",
                            onClick = onClearSearch,
                            icon = NexaIcons.Cancel
                        )
                    }
                    if (reason != NexaNoMatchReason.Search) {
                        NexaOutlinedButton(
                            text = "Clear filters",
                            onClick = onClearFilters,
                            icon = NexaIcons.Filter
                        )
                    }
                }
            }
        }
    }
}
