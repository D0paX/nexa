package com.example.nexa.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.nexa.theme.NexaIcons

/**
 * The single back control for every NEXA drill-down screen.
 *
 * A named wrapper over [NexaIconButton] so drill-down screens cannot drift
 * apart: the icon, its weight, its 48dp target and its pressed response are
 * decided here once. The glyph auto-mirrors under RTL.
 */
@Composable
fun NexaBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Back"
) {
    NexaIconButton(
        icon = NexaIcons.Back,
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = modifier
    )
}
