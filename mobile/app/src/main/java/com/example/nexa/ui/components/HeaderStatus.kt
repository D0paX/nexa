package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The status line in a screen header, and the rule about who gives way.
 *
 * A header row holds a heading, a line saying how current the content is, and
 * the control that acts on it. The line is the only part whose length is not
 * known in advance: "Updated just now" and "Last confirmed 43 minutes ago"
 * differ by a factor of three, and at a large display font the longer one is
 * wider than the row.
 *
 * Laid out plainly, the text takes what it needs and the controls beside it
 * absorb the deficit. On a physical device at a 1.8x font scale the refresh
 * button measured one pixel wide — present in the tree, described correctly to
 * a screen reader, and impossible to hit. A control that cannot be pressed is
 * worse than one that is absent, because nothing says it is gone.
 *
 * So the status line yields first. It is given whatever space is left after
 * the controls have taken theirs, and truncates rather than squeezing them —
 * the freshness is also restated in full below the header on every screen that
 * uses this, while the control has nowhere else to be.
 */
@Composable
fun RowScope.HeaderStatus(content: @Composable () -> Unit) {
    Box(modifier = Modifier.weight(1f, fill = false)) {
        content()
    }
}
