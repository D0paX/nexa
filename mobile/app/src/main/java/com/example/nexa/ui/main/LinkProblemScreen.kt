package com.example.nexa.ui.main

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.nexa.theme.NexaIcons
import com.example.nexa.ui.components.NexaOutlinedButton
import com.example.nexa.ui.components.UnavailableState

/**
 * A link that went nowhere.
 *
 * Uses the ordinary unavailable surface rather than anything special: a
 * broken link is a mundane failure, and inventing a distinct visual language
 * for it would suggest something more alarming happened than did.
 *
 * The wording arrives already finished from the resolver, and describes the
 * situation without describing the parser. An operator who followed a stale
 * notification needs to know the object is gone; they do not need — and a
 * probe should not receive — the rule that failed.
 */
@Composable
fun LinkProblemScreen(
    title: String,
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    UnavailableState(
        title = title,
        message = message,
        modifier = modifier,
        action = {
            NexaOutlinedButton(
                text = "Back",
                onClick = onBack,
                icon = NexaIcons.Back
            )
        }
    )
}
