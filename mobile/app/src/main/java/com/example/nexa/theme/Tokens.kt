package com.example.nexa.theme

import androidx.compose.ui.unit.dp

/**
 * The NEXA dimension scale.
 *
 * Every measurement in the application comes from here. A screen that needs
 * a value not on this scale is either wrong or is telling us the scale is
 * missing a step — in which case the step is added here, once.
 */
object NexaTokens {
    // --- Touch targets ---
    val MinTouchTarget = 48.dp

    // --- Iconography — one scale, chosen optically against the type beside it ---
    val IconSmall = 16.dp    // inline with metadata / label text
    val IconMedium = 20.dp   // list rows, badges, inline affordances
    val IconLarge = 24.dp    // navigation, icon-only controls, buttons
    val IconHero = 40.dp     // empty, error and unavailable states

    val BackIconSize = IconLarge
    val NavigationIconSize = IconLarge

    // --- Spacing ---
    val SpacingHairline = 2.dp  // optical separation inside a single text block
    val SpacingXSmall = 4.dp
    val SpacingSmall = 8.dp
    val SpacingMedium = 16.dp
    val SpacingLarge = 24.dp
    val SpacingXLarge = 32.dp

    /** The gutter every screen holds to its edges. */
    val ScreenHorizontalPadding = SpacingMedium

    // --- Borders: a boundary, never a decoration ---
    val BorderHairline = 1.dp

    // --- Corner radius ---
    val CornerRadiusSmall = 8.dp    // buttons, badges, small controls
    val CornerRadiusMedium = 16.dp  // glass surfaces, cards, list rows
    val CornerRadiusLarge = 24.dp   // dialogs, sheets, large anchors

    // --- Elevation: spatial separation, never decorative drop shadow ---
    val ElevationBase = 0.dp
    val ElevationFloating = 3.dp     // standard glass at rest
    val ElevationRaised = 5.dp       // interactive glass
    val ElevationElevated = 6.dp     // strong / selected glass
    val ElevationModal = 8.dp        // destructive surfaces, dialogs, sheets
    val ElevationHero = 12.dp        // the spatial anchor of a screen

    // --- Navigation (geometry approved in 5.5 — do not retune here) ---
    val NavigationHeight = 56.dp
    val NavigationHorizontalMargin = 20.dp
    val NavigationBottomSpacing = 16.dp
    val NavigationContentClearance = 100.dp
    val NavigationItemSpacing = 4.dp
    val NavigationBarVerticalPadding = 6.dp
    val NavigationActivePillHeight = 44.dp
    val NavigationActivePillVerticalPadding = 4.dp
}
