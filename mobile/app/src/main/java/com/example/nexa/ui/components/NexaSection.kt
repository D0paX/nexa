package com.example.nexa.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTextSecondary
import com.example.nexa.theme.NexaType
import com.example.nexa.ui.common.nexaHeading

/** How loudly a heading speaks. */
enum class SectionLevel {
    /** A major division of a screen — "Recent Alerts". */
    Section,

    /** A quiet label introducing a group of surfaces — "Identity". */
    Group
}

/**
 * A heading.
 *
 * Carries no spacing of its own: vertical rhythm belongs to the screen that
 * arranges the sections, so a heading can be dropped into an existing
 * composition without disturbing approved geometry.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    level: SectionLevel = SectionLevel.Section,
    /**
     * Whether this heading is a navigation landmark.
     *
     * Major divisions are; the quiet group labels that introduce a pair of
     * surfaces are not. Marking every label a heading turns heading
     * navigation back into linear navigation.
     */
    isHeading: Boolean = level == SectionLevel.Section
) {
    Text(
        text = text,
        style = if (level == SectionLevel.Section) NexaType.SectionTitle else NexaType.GroupLabel,
        color = if (level == SectionLevel.Section) NexaTextPrimary else NexaTextSecondary,
        modifier = if (isHeading) modifier.nexaHeading() else modifier
    )
}
