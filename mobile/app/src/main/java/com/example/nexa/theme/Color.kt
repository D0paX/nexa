package com.example.nexa.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// NEXA BRAND FOUNDATION
// Light atmospheric environment + charcoal anchors + NEXA red.
// ============================================================

// --- Environment (never pure white; carries subtle spatial shading) ---
val NexaCanvas = Color(0xFFF2F3F5)          // Base off-white, cool neutral
val NexaCanvasLight = Color(0xFFFBFBFC)     // Center illumination
val NexaCanvasEdge = Color(0xFFE7EAEF)      // Environmental shading at the edges
val NexaAtmosphereWarm = Color(0xFFF7F0EE)  // Faint brand-adjacent warmth
val NexaAtmosphereCool = Color(0xFFE6EAF0)  // Faint cool depth

val NexaBackground = NexaCanvas
val NexaElevatedBackground = Color(0xFFFFFFFF)

// --- Ink & charcoal anchors ---
val NexaInk = Color(0xFF101316)             // Near-black
val NexaCharcoalLit = Color(0xFF262C35)     // Lit upper face of a spatial anchor
val NexaCharcoal = Color(0xFF171B21)        // Anchor body
val NexaCharcoalDeep = Color(0xFF0C0F13)    // Deepest / destructive

// --- NEXA red (strategic identity signal) ---
val NexaRed = Color(0xFFD11A2A)             // Primary brand red
val NexaRedDeep = Color(0xFF9E1220)         // Pressed / weight
val NexaRedBright = Color(0xFFE8404F)       // Accent over charcoal
val NexaRedWash = Color(0xFFD11A2A)         // Base for low-alpha tints

val NexaAction = NexaRed                    // Primary action / brand accent

// ============================================================
// LIQUID GLASS MATERIAL
// Each level is the same material at a different density of light.
// ============================================================

// Standard — quiet frosted surface for ordinary information
val NexaGlassSurface = Color(0xFFFFFFFF).copy(alpha = 0.60f)
val NexaGlassSurfaceTint = Color(0xFFEDF0F5).copy(alpha = 0.48f)

// Strong — higher-contrast light glass
val NexaStrongGlassSurface = Color(0xFFFFFFFF).copy(alpha = 0.92f)
val NexaStrongGlassSurfaceTint = Color(0xFFF2F5F9).copy(alpha = 0.86f)

// Interactive — brighter, invites touch (neutral, never outlined in red)
val NexaInteractiveGlassSurface = Color(0xFFFFFFFF).copy(alpha = 0.76f)
val NexaInteractiveGlassSurfaceTint = Color(0xFFEFF2F7).copy(alpha = 0.66f)

// Selected — the active control surface: near-opaque, lifted, carrying the NEXA signal
val NexaSelectedGlassSurface = Color(0xFFFFFFFF).copy(alpha = 0.98f)
val NexaSelectedGlassSurfaceTint = Color(0xFFFDF3F4).copy(alpha = 0.96f)
val NexaSelectedHighlight = Color(0xFFFFFFFF).copy(alpha = 0.95f)
val NexaSelectedBorder = Color(0xFFD11A2A).copy(alpha = 0.24f)

// Hero — charcoal spatial anchor, lit from above
val NexaHeroGlassSurface = NexaCharcoalLit.copy(alpha = 0.97f)
val NexaHeroGlassSurfaceDeep = NexaCharcoal.copy(alpha = 0.98f)

// Destructive — high-impact dark surface with controlled red weight
val NexaDestructiveSurface = Color(0xFF1C1417).copy(alpha = 0.97f)
val NexaDestructiveSurfaceDeep = NexaCharcoalDeep.copy(alpha = 0.98f)

// --- Lighting & boundaries ---
val NexaGlassHighlight = Color(0xFFFFFFFF).copy(alpha = 0.90f)   // Lit upper edge
val NexaGlassBorder = Color(0xFF8C97A6).copy(alpha = 0.20f)      // Grounded lower edge
val NexaHeroHighlight = Color(0xFFFFFFFF).copy(alpha = 0.14f)
val NexaHeroBorder = Color(0xFF000000).copy(alpha = 0.30f)
val NexaDestructiveBorder = Color(0xFFD11A2A).copy(alpha = 0.38f)
val NexaBorderNeutral = Color(0xFF8C97A6).copy(alpha = 0.35f)    // Hairline for outlined controls

val NexaShadow = Color(0xFF1A2028)                               // Ink-tinted, never pure black

// ============================================================
// TYPOGRAPHY COLORS
// ============================================================
val NexaTextPrimary = NexaInk
val NexaTextSecondary = Color(0xFF4B535C)
val NexaTextMuted = Color(0xFF858D97)
val NexaTextTechnical = Color(0xFF2A3138)   // IP / MAC identifiers: technical, not red
val NexaTextOnDark = Color(0xFFF4F6F8)
val NexaTextOnDarkMuted = Color(0xFFA9B1BC)

// ============================================================
// SEMANTIC STATE COLORS
// Distinct from the brand accent; never the sole carrier of meaning.
// ============================================================
val NexaSecure = Color(0xFF0F7A55)       // Success
val NexaWarning = Color(0xFFB56A00)      // Warning
val NexaDanger = Color(0xFFC0392F)       // Danger
val NexaCritical = Color(0xFF8A1220)     // Critical
val NexaInformation = Color(0xFF1F5F8B)  // Information
val NexaNeutral = Color(0xFF5C6672)      // Neutral
val NexaUnknown = Color(0xFF5F5A8C)      // Unknown
val NexaDisabled = Color(0xFFA6ACB6)     // Disabled

// Same states, lifted for charcoal anchors — the light-surface tones do not
// carry enough contrast against a Hero or Destructive surface.
val NexaSecureOnDark = Color(0xFF3FBF8F)
val NexaWarningOnDark = Color(0xFFE0A33C)
val NexaDangerOnDark = NexaRedBright
val NexaInformationOnDark = Color(0xFF6FB6E8)
