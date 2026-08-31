package com.example.nexa.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.example.nexa.theme.NexaTextTechnical
import com.example.nexa.theme.NexaType

/**
 * A security identifier: IP, MAC, UUID, scope, telemetry value.
 *
 * Monospaced so digits align and a transposed character is visible, in
 * technical ink rather than brand red — being technical is not a state.
 *
 * A long identifier **wraps**; it is never truncated by default. Silently
 * ellipsing half a MAC address or a rule ID would hide exactly the detail an
 * operator is checking. Callers with a genuinely constrained slot may cap
 * [maxLines], and the value then ellipses visibly rather than vanishing.
 */
@Composable
fun TechnicalValue(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NexaTextTechnical,
    emphasized: Boolean = false,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text,
        style = if (emphasized) NexaType.TechnicalStrong else NexaType.Technical,
        color = color,
        softWrap = true,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
