package com.example.nexa.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// We will use default system fonts but customized for weight, tracking, and size
// to give a technical Retro-Futurism character. Monospace for technical values.

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle( // Section title
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle( // Primary value
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle( // Secondary value
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle( // Status
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle( // Metadata / Timestamp
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

val MonospaceTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    letterSpacing = 0.sp
)

/**
 * The NEXA type vocabulary.
 *
 * The same metric scale as [Typography], named by the job each style does.
 * Screens ask for `NexaType.Metadata`, not `Typography.labelMedium` — so a
 * later decision about how metadata should look has exactly one place to
 * land, and hierarchy is chosen deliberately rather than by reaching for a
 * size that happens to fit.
 */
object NexaType {
    /** Brand wordmark, hero state, metric values. */
    val Display = Typography.displayLarge

    /** The subject of a drill-down screen. */
    val Headline = Typography.headlineMedium

    /** Top app bar titles. */
    val ScreenTitle = Typography.titleLarge

    /** Major divisions within a screen. */
    val SectionTitle = Typography.titleLarge

    /** Quiet labels introducing a group of surfaces. */
    val GroupLabel = Typography.titleMedium

    /** The title of a list row or card. */
    val Title = Typography.titleMedium

    /** Primary reading text. */
    val Body = Typography.bodyLarge

    /** Supporting reading text. */
    val BodySecondary = Typography.bodyMedium

    /** Timestamps, counts, field labels. */
    val Metadata = Typography.labelMedium

    /** State words inside badges. */
    val Status = Typography.labelMedium

    /** Button labels. */
    val Button = Typography.labelLarge

    /** IPs, MACs, UUIDs, telemetry — precise, never decorative. */
    val Technical = MonospaceTextStyle

    /** A technical value that is itself the subject. */
    val TechnicalStrong = MonospaceTextStyle.copy(fontWeight = FontWeight.Medium)
}
