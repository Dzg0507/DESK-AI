package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * DeskAI palette: dark slate surfaces, ElectricCyan as the brand accent, Emerald for "good / connected",
 * Amber for "working / waiting", Rose for errors. Screens use these names instead of raw Color(0xFF…) values so
 * every sheet shares one look. Values that existed before keep their exact colors.
 */

// ---- Brand & status (unchanged identity) ----------------------------------------------------------------------
val ElectricCyan = Color(0xFF38BDF8)
val ElectricCyanGlow = Color(0xFF0284C7)
val NeonIndigo = Color(0xFF6366F1)
val NeonPurple = Color(0xFFA855F7)
val EmeraldConnected = Color(0xFF10B981)
val AmberPending = Color(0xFFF59E0B)
val RoseError = Color(0xFFF43F5E)

// ---- Slate scale (Tailwind slate) ---------------------------------------------------------------------------------
val Slate50 = Color(0xFFF8FAFC)
val Slate100 = Color(0xFFF1F5F9)
val Slate200 = Color(0xFFE2E8F0)
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Slate500 = Color(0xFF64748B)
val Slate600 = Color(0xFF475569)
val Slate700 = Color(0xFF334155)
val Slate800 = Color(0xFF1E293B)
val Slate900 = Color(0xFF0F172A)
val Slate950 = Color(0xFF020617)

// Deep surfaces between slate-900 and black (sheet gradients, wells, code)
val DeepNavy = Color(0xFF0B1120)
val DeepPanel = Color(0xFF101726)
val DeepCard = Color(0xFF131D31)
val DeepCardAlt = Color(0xFF131C2E)
val DeepTeal = Color(0xFF0E2A33)
val Gray800 = Color(0xFF1F2937)
val Gray900 = Color(0xFF111827)
val NearBlack = Color(0xFF111111)

// ---- Accent tints -------------------------------------------------------------------------------------------------
val Sky100 = Color(0xFFE0F2FE)
val Sky300 = Color(0xFF7DD3FC)
val Sky700 = Color(0xFF0369A1)
val Cyan400 = Color(0xFF22D3EE)
val Blue500 = Color(0xFF3B82F6)
val Blue600 = Color(0xFF2563EB)
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
val SlateDarkBackground = Color(0xFF0B0F19)
val SlateDarkSurface = Color(0xFF131B2E)
val SlateDarkSurfaceVariant = Slate800
val SlateDarkBorder = Color(0xFF2E3A52)
/** Behind the chat messages: a touch darker than the app background. */
val ChatBackground = Color(0xFF080B11)

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
val CodeBlockBackground = Color(0xFF090D16)

// Light theme palette (kept for completeness; the app runs dark)
val SlateLightBackground = Slate50
val SlateLightSurface = Color(0xFFFFFFFF)
val SlateLightSurfaceVariant = Color(0xFFEEF2F6)
val SlateLightBorder = Slate200
val TextPrimaryLight = Slate900
val TextSecondaryLight = Slate600

// ---- Gradients ----------------------------------------------------------------------------------------------------
/** The background every sheet/dialog uses: slate-900 fading into deep navy. */
val SheetGradient: Brush = Brush.verticalGradient(listOf(Slate900, DeepNavy))

/** A hairline border that is a touch brighter at the top, so sheets look lit from above. */
val SheetBorderGradient: Brush = Brush.verticalGradient(listOf(Slate700, Slate800.copy(alpha = 0.6f)))

/** Card fill: slate-800 with a slightly darker bottom. */
val CardGradient: Brush = Brush.verticalGradient(listOf(Slate800, Color(0xFF1A2436)))
