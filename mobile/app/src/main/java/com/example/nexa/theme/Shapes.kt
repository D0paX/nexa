package com.example.nexa.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * NEXA geometry.
 *
 * The application should be recognizable by its corners as much as by its
 * color. Screens pick a shape by what the thing *is*, not by picking a
 * radius.
 */
object NexaShapes {
    /** Buttons, badges, compact controls. */
    val Control: CornerBasedShape = RoundedCornerShape(NexaTokens.CornerRadiusSmall)

    /** The default liquid glass surface: cards, list rows, metrics. */
    val Surface: CornerBasedShape = RoundedCornerShape(NexaTokens.CornerRadiusMedium)

    /** Spatial anchors — hero and destructive surfaces. */
    val Anchor: CornerBasedShape = RoundedCornerShape(NexaTokens.CornerRadiusMedium)

    /** Dialogs and other modal surfaces. */
    val Dialog: CornerBasedShape = RoundedCornerShape(NexaTokens.CornerRadiusLarge)

    /** Bottom sheets: rounded where they leave the screen edge. */
    val Sheet: CornerBasedShape = RoundedCornerShape(
        topStart = NexaTokens.CornerRadiusLarge,
        topEnd = NexaTokens.CornerRadiusLarge,
        bottomStart = NexaTokens.CornerRadiusSmall,
        bottomEnd = NexaTokens.CornerRadiusSmall
    )

    /** Fully rounded: the floating navigation plane and its active capsule. */
    val Pill: CornerBasedShape = RoundedCornerShape(percent = 50)
}
