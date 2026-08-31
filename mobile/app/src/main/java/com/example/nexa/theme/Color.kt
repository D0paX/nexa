package com.example.nexa.theme

import androidx.compose.ui.graphics.Color

// NEXA Atmospheric Background (Light Spatial Environment)
val NexaBackground = Color(0xFFF8F9FA) // Soft off-white foundation
val NexaElevatedBackground = Color(0xFFFFFFFF)

// Atmospheric Light Tones
val NexaAtmosphereLight = Color(0xFFF1F5F9) // Pale cool neutral
val NexaAtmosphereCore = Color(0xFFFFFFFF)  // Soft center illumination

// Liquid Glass Surfaces (Light & Charcoal)
val NexaGlassSurface = Color(0xFFFFFFFF).copy(alpha = 0.65f) // Standard light frosted glass
val NexaStrongGlassSurface = Color(0xFFFFFFFF).copy(alpha = 0.90f) // Stronger white glass
val NexaHeroGlassSurface = Color(0xFF1E293B).copy(alpha = 0.95f) // Charcoal hero anchor
val NexaDestructiveSurface = Color(0xFF18181B).copy(alpha = 0.95f) // Deep charcoal for high impact

val NexaGlassBorder = Color(0xFFFFFFFF).copy(alpha = 0.6f) // White reflection
val NexaGlassHighlight = Color(0xFFFFFFFF).copy(alpha = 0.4f)

// Typography Colors
val NexaTextPrimary = Color(0xFF0F172A) // Near black / deep charcoal
val NexaTextSecondary = Color(0xFF475569) // Restrained dark gray
val NexaTextMuted = Color(0xFF94A3B8)
val NexaTextOnDark = Color(0xFFF8F9FA) // White text on dark/high-impact surfaces

// Semantic Colors
val NexaSecure = Color(0xFF059669) // Success (adjusted for light bg contrast)
val NexaInformation = Color(0xFF2563EB) // Information
val NexaWarning = Color(0xFFD97706) // Warning
val NexaDanger = Color(0xFFDC2626) // Danger
val NexaCritical = Color(0xFF991B1B) // Critical
val NexaNeutral = Color(0xFF64748B) // Neutral
val NexaUnknown = Color(0xFF7C3AED) // Unknown
val NexaDisabled = Color(0xFF94A3B8) // Disabled
val NexaAction = Color(0xFFE11D48) // Primary Action (NEXA Red brand accent)
