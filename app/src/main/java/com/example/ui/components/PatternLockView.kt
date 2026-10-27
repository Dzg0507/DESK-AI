package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import kotlinx.coroutines.delay
import java.security.MessageDigest
import kotlin.math.hypot

enum class PatternState {
    IDLE,
    DRAWING,
    SUCCESS,
    ERROR
}

object PatternLockHelper {
    /**
     * Converts a pattern list (e.g. [0, 1, 2, 4, 6]) to a secure SHA-256 hash.
     */
    fun hashPattern(pattern: List<Int>, salt: String = "desk_ai_vault_salt"): String {
        val input = pattern.joinToString(separator = "-", postfix = ":$salt")
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

@Composable
fun PatternLockView(
    modifier: Modifier = Modifier,
    patternState: PatternState = PatternState.IDLE,
    enabled: Boolean = true,
    onPatternStart: () -> Unit = {},
    onPatternComplete: (List<Int>) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val selectedDots = remember { mutableStateListOf<Int>() }
    var currentTouchPoint by remember { mutableStateOf<Offset?>(null) }
    var localState by remember { mutableStateOf(patternState) }

    LaunchedEffect(patternState) {
        localState = patternState
        if (patternState == PatternState.ERROR || patternState == PatternState.SUCCESS) {
            delay(700)
            selectedDots.clear()
            currentTouchPoint = null
            localState = PatternState.IDLE
        }
    }

    val activeColor by animateColorAsState(
        targetValue = when (localState) {
            PatternState.ERROR -> RoseError
            PatternState.SUCCESS -> EmeraldConnected
            else -> ElectricCyan
        },
        animationSpec = tween(durationMillis = 200),
        label = "patternColor"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(16.dp)
            .pointerInput(enabled, localState) {
                if (!enabled || localState == PatternState.ERROR || localState == PatternState.SUCCESS) return@pointerInput

                val width = size.width.toFloat()
                val height = size.height.toFloat()
                val cellW = width / 3f
                val cellH = height / 3f
                val hitRadiusPx = cellW * 0.38f

                fun dotCenter(index: Int): Offset {
                    val row = index / 3
                    val col = index % 3
                    return Offset((col + 0.5f) * cellW, (row + 0.5f) * cellH)
                }

                fun checkHit(pos: Offset) {
                    for (i in 0 until 9) {
                        if (!selectedDots.contains(i)) {
                            val center = dotCenter(i)
                            if (hypot(pos.x - center.x, pos.y - center.y) <= hitRadiusPx) {
                                selectedDots.add(i)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                break
                            }
                        }
                    }
                }

                detectDragGestures(
                    onDragStart = { pos ->
                        selectedDots.clear()
                        localState = PatternState.DRAWING
                        onPatternStart()
                        checkHit(pos)
                        currentTouchPoint = pos
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentTouchPoint = change.position
                        checkHit(change.position)
                    },
                    onDragEnd = {
                        currentTouchPoint = null
                        if (selectedDots.isNotEmpty()) {
                            onPatternComplete(selectedDots.toList())
                        } else {
                            localState = PatternState.IDLE
                        }
                    },
                    onDragCancel = {
                        selectedDots.clear()
                        currentTouchPoint = null
                        localState = PatternState.IDLE
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cellW = size.width / 3f
            val cellH = size.height / 3f
            val outerRadius = cellW * 0.28f
            val dotRadius = cellW * 0.08f

            fun centerOf(i: Int): Offset {
                val row = i / 3
                val col = i % 3
                return Offset((col + 0.5f) * cellW, (row + 0.5f) * cellH)
            }

            // 1. Draw connecting lines between selected dots
            if (selectedDots.size > 1) {
                for (k in 0 until selectedDots.size - 1) {
                    val start = centerOf(selectedDots[k])
                    val end = centerOf(selectedDots[k + 1])
                    // Outer glow
                    drawLine(
                        color = activeColor.copy(alpha = 0.25f),
                        start = start,
                        end = end,
                        strokeWidth = 9.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Core line
                    drawLine(
                        color = activeColor,
                        start = start,
                        end = end,
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // 2. Line from last selected dot to current finger touch point
            if (selectedDots.isNotEmpty() && currentTouchPoint != null && localState == PatternState.DRAWING) {
                val start = centerOf(selectedDots.last())
                val end = currentTouchPoint!!
                drawLine(
                    color = activeColor.copy(alpha = 0.2f),
                    start = start,
                    end = end,
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = activeColor.copy(alpha = 0.8f),
                    start = start,
                    end = end,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 3. Draw 9 nodes
            for (i in 0 until 9) {
                val center = centerOf(i)
                val isSelected = selectedDots.contains(i)

                if (isSelected) {
                    // Outer glow halo
                    drawCircle(
                        color = activeColor.copy(alpha = 0.15f),
                        radius = outerRadius,
                        center = center
                    )
                    // Outer border
                    drawCircle(
                        color = activeColor.copy(alpha = 0.6f),
                        radius = outerRadius,
                        center = center,
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                    // Inner bright dot
                    drawCircle(
                        color = activeColor,
                        radius = dotRadius * 1.3f,
                        center = center
                    )
                } else {
                    // Inactive subtle node
                    drawCircle(
                        color = Slate800.copy(alpha = 0.6f),
                        radius = outerRadius * 0.75f,
                        center = center
                    )
                    drawCircle(
                        color = Slate700.copy(alpha = 0.5f),
                        radius = outerRadius * 0.75f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.35f),
                        radius = dotRadius,
                        center = center
                    )
                }
            }
        }
    }
}
