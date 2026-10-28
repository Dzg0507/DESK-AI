package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Spacing steps used across screens (4-pt grid). */
object DeskSpace {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}

/**
 * Corner radii (2026-10-27): three, plus the pill. Small things (chips, controls, inputs) 8, cards 16, sheets 28.
 * Was 12 different radii from 1.5 to 30 dp. The old names stay so existing screens keep compiling.
 */
object DeskRadius {
    val pill = 999.dp
    val small = 8.dp
    val card = 16.dp
    val sheet = 28.dp
    val chip = small
    val control = small
}

object DeskShapes {
    val small = RoundedCornerShape(DeskRadius.small)
    val card = RoundedCornerShape(DeskRadius.card)
    val sheet = RoundedCornerShape(DeskRadius.sheet)
    val sheetTop = RoundedCornerShape(topStart = DeskRadius.sheet, topEnd = DeskRadius.sheet)
    val pill = RoundedCornerShape(50)
    val chip = small
    val control = small
}

/** Material shapes, so default components (cards, text fields, dialogs, menus) pick up the same radii. */
val DeskMaterialShapes = Shapes(
    extraSmall = DeskShapes.small,
    small = DeskShapes.small,
    medium = DeskShapes.card,
    large = DeskShapes.card,
    extraLarge = DeskShapes.sheet
)
