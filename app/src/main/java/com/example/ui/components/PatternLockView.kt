package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RoseError
import kotlinx.coroutines.delay
import java.security.MessageDigest
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

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

// High-contrast dark neon palette: Deep Neon Red + Electric Cerulean
private val NeonRed = Color(0xFFFF1744)
private val NeonRedDark = Color(0xFFFF003C)
private val ElectricCerulean = Color(0xFF00D4FF)
private val ElectricCeruleanDeep = Color(0xFF0077B6)
private val DwarfStarWhite = Color(0xFFFFFFFF)
private val CorePitchBlack = Color(0xFF000000)
private val NodeDarkWell = Color(0xFF0A0E16)

// Machined Lathe Spun Metal & Dark Plasma Palette
private val MetalRimHighlight = Color(0xFFFFFFFF)
private val MetalRimMid = Color(0xFF8C9DA8)
private val MetalRimDark = Color(0xFF182025)
private val MetalDialSpecular = Color(0xFFF0F5FA)
private val MetalDialBright = Color(0xFFA0B0BE)
private val MetalDialMid = Color(0xFF43515C)
private val MetalDialDark = Color(0xFF1C242A)
private val MetalCenterApex = Color(0xFF64748B)

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

    val activeStateColor by animateColorAsState(
        targetValue = when (localState) {
            PatternState.ERROR -> RoseError
            PatternState.SUCCESS -> EmeraldConnected
            else -> Color.White
        },
        animationSpec = tween(durationMillis = 180),
        label = "patternActiveColor"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(12.dp)
            .pointerInput(enabled, localState) {
                if (!enabled || localState == PatternState.ERROR || localState == PatternState.SUCCESS) return@pointerInput

                val width = size.width.toFloat()
                val height = size.height.toFloat()
                val cellW = width / 3f
                val cellH = height / 3f
                val hitRadiusPx = cellW * 0.40f

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

                // Raw pointer event loop: captures instant touch down with 0ms slop delay
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    selectedDots.clear()
                    localState = PatternState.DRAWING
                    onPatternStart()
                    currentTouchPoint = down.position
                    checkHit(down.position)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        currentTouchPoint = change.position
                        checkHit(change.position)
                    }

                    currentTouchPoint = null
                    if (selectedDots.isNotEmpty()) {
                        onPatternComplete(selectedDots.toList())
                    } else {
                        localState = PatternState.IDLE
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val cellW = w / 3f
            val cellH = h / 3f
            val buttonRadius = cellW * 0.28f

            fun centerOf(i: Int): Offset {
                val row = i / 3
                val col = i % 3
                return Offset((col + 0.5f) * cellW, (row + 0.5f) * cellH)
            }

            // 0. Dark Emerald Olive Honeycomb Textured Background
            val bgBrush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF102521), // deep emerald olive center
                    Color(0xFF091513),
                    Color(0xFF030706)  // dark vignette edge
                ),
                center = Offset(w / 2f, h / 2f),
                radius = w * 0.72f
            )
            drawRect(brush = bgBrush)

            // Crisp subtle hexagonal honeycomb grid
            val hexRadius = 15.dp.toPx()
            val hDist = hexRadius * 1.7320508f
            val wDist = hexRadius * 1.5f
            val hexLineColor = Color(0xFF00E6B4).copy(alpha = 0.04f)

            var hx = -hexRadius
            var col = 0
            while (hx <= w + hexRadius * 2f) {
                val yOffset = if (col % 2 == 0) 0f else hDist / 2f
                var hy = -hDist
                while (hy <= h + hDist * 2f) {
                    val hexPath = Path().apply {
                        for (side in 0..5) {
                            val angle = (Math.PI / 3.0 * side).toFloat()
                            val px = hx + hexRadius * cos(angle)
                            val py = hy + yOffset + hexRadius * sin(angle)
                            if (side == 0) moveTo(px, py) else lineTo(px, py)
                        }
                        close()
                    }
                    drawPath(hexPath, color = hexLineColor, style = Stroke(width = 1.dp.toPx()))
                    hy += hDist
                }
                hx += wDist
                col++
            }

            // 1. Gather all line points (selected dots + current finger position)
            val pathPoints = selectedDots.map { centerOf(it) }.toMutableList()
            if (currentTouchPoint != null && localState == PatternState.DRAWING) {
                pathPoints.add(currentTouchPoint!!)
            }

            // 2. Draw Connections: DIRECT SMOOTH CONNECTING LINE (Zero Latency, Instant 0ms Tracking)
            if (pathPoints.size > 1) {
                val linePath = Path().apply {
                    moveTo(pathPoints[0].x, pathPoints[0].y)
                    for (i in 1 until pathPoints.size) {
                        lineTo(pathPoints[i].x, pathPoints[i].y)
                    }
                }

                // A. Soft subtle atmospheric aura / contour under the line
                drawPath(
                    path = linePath,
                    color = when (localState) {
                        PatternState.ERROR -> RoseError.copy(alpha = 0.25f)
                        PatternState.SUCCESS -> EmeraldConnected.copy(alpha = 0.25f)
                        else -> Color(0xFFE2E8F0).copy(alpha = 0.14f)
                    },
                    style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // B. Crisp direct smooth connecting line
                drawPath(
                    path = linePath,
                    color = activeStateColor,
                    style = Stroke(width = 3.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // 3. Draw 9 Brushed Metallic Buttons (STATIONARY - No Spinning, matching reference dials)
            for (i in 0 until 9) {
                val center = centerOf(i)
                val isSelected = selectedDots.contains(i)
                // Fixed 45° lathe reflection angle matching reference photo (specular highlight diagonal)
                val rotAngle = 45f

                // A. Physical 3D Elevation Drop Shadow Under Button
                drawCircle(
                    color = Color.Black.copy(alpha = 0.88f),
                    radius = buttonRadius + 2.dp.toPx(),
                    center = center + Offset(0f, 4.dp.toPx())
                )

                // B. Dark Chamfer Outer Rim (Beveled edge)
                drawCircle(
                    color = MetalRimDark,
                    radius = buttonRadius + 2.dp.toPx(),
                    center = center
                )

                // C. Specular Edge Highlight (Upper-diagonal reflection on the rim)
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MetalRimHighlight,
                            MetalRimMid,
                            Color(0xFF242E36),
                            Color(0xFF0E1418)
                        ),
                        start = center - Offset(buttonRadius, buttonRadius),
                        end = center + Offset(buttonRadius, buttonRadius)
                    ),
                    radius = buttonRadius + 0.5.dp.toPx(),
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // D. Lathe-Turned Spun Metal Dial (Hourglass anisotropic reflection)
                rotate(degrees = rotAngle, pivot = center) {
                    val metalConic = Brush.sweepGradient(
                        0.00f to MetalDialDark,
                        0.12f to MetalDialBright,
                        0.24f to MetalDialSpecular,
                        0.36f to MetalDialMid,
                        0.50f to MetalDialDark,
                        0.62f to MetalDialBright,
                        0.74f to MetalDialSpecular,
                        0.86f to MetalDialMid,
                        1.00f to MetalDialDark,
                        center = center
                    )
                    drawCircle(
                        brush = metalConic,
                        radius = buttonRadius - 1.dp.toPx(),
                        center = center
                    )

                    // Concentric Fine Lathe Grooves
                    drawCircle(
                        color = Color.White.copy(alpha = 0.09f),
                        radius = buttonRadius * 0.72f,
                        center = center,
                        style = Stroke(width = 0.8.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.09f),
                        radius = buttonRadius * 0.45f,
                        center = center,
                        style = Stroke(width = 0.8.dp.toPx())
                    )

                    // Subtle Sheen
                    val sheenBrush = Brush.sweepGradient(
                        0.0f to Color.White.copy(alpha = 0.05f),
                        0.25f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.05f),
                        0.75f to Color.Transparent,
                        1.0f to Color.White.copy(alpha = 0.05f),
                        center = center
                    )
                    drawCircle(
                        brush = sheenBrush,
                        radius = buttonRadius - 1.dp.toPx(),
                        center = center
                    )
                }

                // E. Clean Machined Center Apex (No hole/screw, smooth convergence)
                drawCircle(
                    color = if (isSelected) Color.White else MetalCenterApex,
                    radius = 2.5.dp.toPx(),
                    center = center
                )

                // F. Active State Selection Ring
                if (isSelected) {
                    drawCircle(
                        color = activeStateColor.copy(alpha = 0.85f),
                        radius = buttonRadius + 3.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                }
            }
        }
    }
}

