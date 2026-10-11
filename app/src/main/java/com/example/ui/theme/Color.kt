package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * 2026-10-11, the owner's pick ("Circuit"): terminal green that glows like a backlit keyboard, DEEP cerulean as its
 * partner, green-black surfaces over a hex circuit texture (CircuitBackground). The names below stay (every screen
 * uses them); their values moved to that look. ElectricCyan is now terminal green, the slates are green-tinted.
 *
 * Was: DeskAI palette: dark slate surfaces, ElectricCyan as the brand accent, Emerald for "good / connected",
 * Amber for "working / waiting", Rose for errors. Screens use these names instead of raw Color(0xFF…) values so
 * every sheet shares one look. Values that existed before keep their exact colors.
 */

// ---- Brand & status (unchanged identity) ----------------------------------------------------------------------
val ElectricCyan = Color(0xFF39FF6A)          // terminal green, the brand accent
val ElectricCyanGlow = Color(0xFF1FBF55)

// ---- Circuit ------------------------------------------------------------------------------------------------------
val TerminalGreen = Color(0xFF39FF6A)
val Cerulean = Color(0xFF0077B6)              // deep cerulean: glows, edges, the lightning flash
val CeruleanText = Color(0xFF1E90D0)          // the same hue, light enough to read on black
val PhosphorMint = Color(0xFFC9FFD6)          // headings: lit, not flat white
val CircuitBlack = Color(0xFF020A07)
val TaskIndigo = Color(0xFF4D5BFF)            // task cards: deep cerulean into electric indigo, so tasks stand apart from chat (green)
val NeonIndigo = Color(0xFF6366F1)
val NeonPurple = Color(0xFFA855F7)
val EmeraldConnected = Color(0xFF39FF6A)
val AmberPending = Color(0xFFF59E0B)
val RoseError = Color(0xFFF43F5E)

// ---- Slate scale (Tailwind slate) ---------------------------------------------------------------------------------
val Slate50 = Color(0xFFEAFFF0)
val Slate100 = Color(0xFFDDF7E5)
val Slate200 = Color(0xFFCDEBD7)
val Slate300 = Color(0xFFADCFB8)
val Slate400 = Color(0xFF86A893)
val Slate500 = Color(0xFF5F8A6A)
val Slate600 = Color(0xFF3E6B4C)
val Slate700 = Color(0xFF1C4A2E)
val Slate800 = Color(0xFF0C1F14)
val Slate900 = Color(0xFF07130C)
val Slate950 = Color(0xFF030805)

// Deep surfaces between slate-900 and black (sheet gradients, wells, code)
val DeepNavy = Color(0xFF020A07)
val DeepPanel = Color(0xFF06130C)
val DeepCard = Color(0xFF0A1A11)
val DeepCardAlt = Color(0xFF0A1910)
val DeepTeal = Color(0xFF0E2A33)
val Gray800 = Color(0xFF1F2937)
val Gray900 = Color(0xFF111827)
val NearBlack = Color(0xFF111111)

// ---- Accent tints -------------------------------------------------------------------------------------------------
val Sky100 = Color(0xFFE0F2FE)
val Sky300 = Color(0xFF1E90D0)
val Sky700 = Color(0xFF0369A1)
val Cyan400 = Color(0xFF1E90D0)
val Blue500 = Color(0xFF1E90D0)
val Blue600 = Color(0xFF0077B6)
val Indigo50 = Color(0xFFEEF2FF)
val Indigo100 = Color(0xFFE0E7FF)
val Indigo600 = Color(0xFF4F46E5)
val Indigo700 = Color(0xFF3730A3)
val Indigo900 = Color(0xFF312E81)
val Violet300 = Color(0xFFC4B5FD)
val Violet400 = Color(0xFFA78BFA)
val Violet600 = Color(0xFF7C3AED)
val Purple400 = Color(0xFFC084FC)
val Purple600 = Color(0xFF9333EA)
val Pink500 = Color(0xFFEC4899)
val HotPink = Color(0xFFFF4D8D)
val Teal500 = Color(0xFF14B8A6)
val Green400 = Color(0xFF4ADE80)
val Green600 = Color(0xFF16A34A)
val Amber200 = Color(0xFFFDE68A)
val Amber300 = Color(0xFFFCD34D)
val Amber900 = Color(0xFF78350F)
val Orange500 = Color(0xFFF97316)
val Red300 = Color(0xFFFCA5A5)
val Red400 = Color(0xFFF87171)
val Red500 = Color(0xFFEF4444)
val Rose300 = Color(0xFFFDA4AF)

// ---- Semantic roles (dark theme) ------------------------------------------------------------------------------
val SlateDarkBackground = Color(0xFF020A07)
val SlateDarkSurface = Color(0xFF08160E)
val SlateDarkSurfaceVariant = Slate800
val SlateDarkBorder = Color(0xFF1C4A2E)
/** Behind the chat messages: a touch darker than the app background. */
val ChatBackground = Color(0xFF020A07)

/** Sheet / dialog body, cards inside it, and their outlines. */
val SheetBackground = Slate900
val CardBackground = Slate800
val CardBorder = Slate700
val FieldBackground = Slate800

// Text
val TextPrimary = Slate50
val TextSecondary = Slate400
val TextMuted = Slate500

// Bubbles
val UserBubbleBackground = Blue600
val AssistantBubbleBackground = Slate800
val CodeBlockBackground = Color(0xFF030B06)

// Light theme palette (kept for completeness; the app runs dark)
val SlateLightBackground = Slate50
val SlateLightSurface = Color(0xFFFFFFFF)
val SlateLightSurfaceVariant = Color(0xFFEEF2F6)
val SlateLightBorder = Slate200
val TextPrimaryLight = Slate900
val TextSecondaryLight = Slate600

// ---- Gradients ----------------------------------------------------------------------------------------------------
/** The background every sheet/dialog uses: slate-900 fading into deep navy. */
val SheetGradient: Brush = Brush.verticalGradient(listOf(Slate900, DeepNavy))   // under the circuit texture (deskSheet)

/** A hairline border that is a touch brighter at the top, so sheets look lit from above. */
val SheetBorderGradient: Brush = Brush.verticalGradient(listOf(TerminalGreen.copy(alpha = 0.55f), Cerulean.copy(alpha = 0.55f)))

/** Card fill: slate-800 with a slightly darker bottom. */
val CardGradient: Brush = Brush.verticalGradient(listOf(Color(0xD90C1F14), Color(0xD9061410)))   // see-through: the texture shows
