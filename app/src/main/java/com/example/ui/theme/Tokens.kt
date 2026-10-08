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

/** Corner radii: chips/pills, inputs and buttons, cards, sheets. */
object DeskRadius {
    val pill = 999.dp
    val chip = 8.dp
    val control = 10.dp
    val card = 12.dp
    val sheet = 20.dp
}

object DeskShapes {
    val chip = RoundedCornerShape(DeskRadius.chip)
    val control = RoundedCornerShape(DeskRadius.control)
    val card = RoundedCornerShape(DeskRadius.card)
    val sheet = RoundedCornerShape(DeskRadius.sheet)
    val pill = RoundedCornerShape(50)
}

/** Material shapes, so default components (cards, text fields, dialogs, menus) pick up the same radii. */
val DeskMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(DeskRadius.control),
    medium = RoundedCornerShape(DeskRadius.card),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(DeskRadius.sheet)
)
