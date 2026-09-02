package com.example.nexa.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.nexa.theme.NexaTokens

/**
 * One facet inside a filter sheet: a heading and its selectable values.
 *
 * Four private copies of this had grown, one per sheet, and a fifth was about
 * to be written for identities. They had already begun to drift in spacing.
 * Having one means a facet looks the same on every surface, which is the
 * whole point of the checkpoint: an operator who learns the filter language
 * on Devices should not have to relearn it on Audit.
 *
 * Values scroll horizontally rather than wrapping into a wall of pills. A
 * sheet that grows a second and third row of chips stops being scannable, and
 * on a domain with many scopes it would push the clear control off-screen.
 */
@Composable
fun FilterGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SectionHeader(text = title, level = SectionLevel.Group)
    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
    Row(
        horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingSmall),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        content()
    }
    Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
}
