package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaTextMuted
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTextSecondary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType

/**
 * A headline number with its context.
 *
 * [caption] carries the breakdown that makes the number decision-useful — a
 * device count means little without knowing how many are offline or
 * quarantined. It is optional: metrics that are complete on their own stay
 * a single figure.
 */
@Composable
fun MetricSurface(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = NexaTextPrimary,
    caption: String? = null,
    glassVariant: GlassVariant? = null,
    onClick: (() -> Unit)? = null
) {
    val resolvedVariant = glassVariant ?: if (onClick != null) GlassVariant.Interactive else GlassVariant.Standard

    GlassSurface(
        variant = resolvedVariant,
        onClick = onClick,
        modifier = modifier
    ) {
        Column {
            Text(text = title, style = NexaType.Metadata, color = NexaTextSecondary)
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Text(
                text = value,
                style = NexaType.Display,
                color = valueColor
            )
            if (caption != null) {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                Text(text = caption, style = NexaType.Metadata, color = NexaTextMuted)
            }
        }
    }
}

/**
 * Metrics side by side, sharing one height.
 *
 * Two cards of the same width do not hold the same amount of text. "38 online
 * · 3 quarantined" fits on one line on a wide screen and wraps to two on a
 * narrower one, while "2 unacknowledged" fits either way — so each card
 * measured itself, arrived at a different answer, and the pair sat with their
 * bottom edges 16dp apart. It looked correct on the emulator and wrong on the
 * phone, which is the tell: the height was a property of the text rather than
 * of the row.
 *
 * Measuring at [IntrinsicSize.Min] asks the children how tall they need to be
 * at the width they will get, takes the larger answer, and gives it to both.
 * Nothing is truncated and no line count is forced: the caption wraps when it
 * needs to and the row grows to hold it.
 *
 * Children are expected to carry `Modifier.weight(1f).fillMaxHeight()` — the
 * weight for the equal columns, the fill so the shorter card grows into the
 * height the row settled on instead of leaving a gap under itself.
 */
@Composable
fun MetricRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal =
        Arrangement.spacedBy(NexaTokens.SpacingMedium),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = horizontalArrangement,
        content = content
    )
}
