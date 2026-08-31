package com.example.nexa.theme

import androidx.compose.ui.unit.dp

object NexaTokens {
    // Touch targets
    val MinTouchTarget = 48.dp

    // Iconography — one scale, chosen optically against the type it sits beside
    val IconSmall = 16.dp    // inline with metadata / label text
    val IconMedium = 20.dp   // list rows, badges, inline affordances
    val IconLarge = 24.dp    // navigation, icon-only controls, buttons
    val IconHero = 40.dp     // empty and error states

    val BackIconSize = IconLarge
    
    // Spacing
    val SpacingXSmall = 4.dp
    val SpacingSmall = 8.dp
    val SpacingMedium = 16.dp
    val SpacingLarge = 24.dp
    val SpacingXLarge = 32.dp
    
    // Corner Radius
    val CornerRadiusSmall = 8.dp
    val CornerRadiusMedium = 16.dp
    val CornerRadiusLarge = 24.dp
    
    // Elevation (shadow depth only — soft separation, never heavy drop shadow)
    val ElevationStandard = 3.dp
    val ElevationInteractive = 5.dp
    val ElevationStrong = 6.dp
    val ElevationHero = 12.dp
    val ElevationDestructive = 8.dp

    // Navigation
    val NavigationHeight = 56.dp
    val NavigationHorizontalMargin = 20.dp
    val NavigationBottomSpacing = 16.dp
    val NavigationContentClearance = 100.dp
    val NavigationItemSpacing = 4.dp
    val NavigationBarVerticalPadding = 6.dp
    val NavigationActivePillHeight = 44.dp
    val NavigationActivePillVerticalPadding = 4.dp
    val NavigationIconSize = IconLarge
}
