package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTextSecondary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType
import com.example.nexa.theme.style
import com.example.nexa.ui.common.NexaAvailability
import com.example.nexa.ui.common.availabilityExplanation
import com.example.nexa.ui.common.icon
import com.example.nexa.ui.common.label
import com.example.nexa.ui.common.status

/**
 * Whether [AvailabilityNotice] would render anything for this state.
 *
 * Exposed so a screen can decide whether to reserve layout space for the
 * banner, rather than leaving an unexplained gap where a hidden one would be.
 *
 * Current needs no notice. Loading is already announced by the loading
 * surface. Empty is a complete, current answer that happens to contain
 * nothing — it belongs in the list's own empty copy, where it can say what
 * was empty, and not in a banner that would read as a warning.
 */
val NexaAvailability.warrantsNotice: Boolean
    get() = this != NexaAvailability.Current &&
        this != NexaAvailability.Loading &&
        this != NexaAvailability.Empty

/**
 * The banner a screen shows while it is displaying content it cannot fully
 * stand behind.
 *
 * The full-screen containers in [StateContainers] cover the case where there
 * is nothing to show. This covers the more dangerous case: there *is*
 * something on screen, it looks entirely normal, and it is old, partial, or
 * unconfirmed. Without a persistent notice an operator reads a stale list
 * exactly as they read a live one.
 *
 * Wording comes from [availabilityExplanation] rather than from the screen,
 * so "incomplete" means the same thing on the audit trail as it does on the
 * device inventory, and no screen can quietly soften it. [detail] carries the
 * screen-specific part — when the data was last confirmed, or what the source
 * said it left out.
 *
 * Renders nothing when the data is current or still loading: a banner that
 * appears on every screen all the time is a banner nobody reads.
 */
@Composable
fun AvailabilityNotice(
    availability: NexaAvailability,
    subject: String,
    modifier: Modifier = Modifier,
    detail: String? = null
) {
    if (!availability.warrantsNotice) return

    val style = availability.status.style

    GlassSurface(modifier = modifier.fillMaxWidth()) {
        Row {
            NexaIcon(
                icon = availability.icon,
                size = NexaTokens.IconMedium,
                tint = style.onLight
            )
            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
            Column {
                Text(
                    text = noticeTitle(availability, subject),
                    style = NexaType.Title,
                    color = NexaTextPrimary
                )
                Spacer(modifier = Modifier.height(NexaTokens.SpacingHairline))
                Text(
                    text = availabilityExplanation(availability, subject),
                    style = NexaType.BodySecondary,
                    color = NexaTextSecondary
                )
                if (detail != null) {
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                    Text(
                        text = detail,
                        style = NexaType.Metadata,
                        color = style.onLight
                    )
                }
            }
        }
    }
}

/**
 * The headline, which names the subject rather than the state alone.
 *
 * "Stale" on its own leaves the operator to work out stale *what*, on a
 * screen that may be showing several things at once.
 */
private fun noticeTitle(availability: NexaAvailability, subject: String): String {
    val head = subject.replaceFirstChar { it.uppercase() }
    return "$head — ${availability.label.lowercase()}"
}
