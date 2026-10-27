package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.ui.theme.RoseError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

private fun dotCenter(index: Int, cellW: Float, cellH: Float): Offset =
    Offset((index % 3 + 0.5f) * cellW, (index / 3 + 0.5f) * cellH)

/**
 * Stock-Android-style 9-dot pattern lock: small dots that grow when touched, a thin line, theme colors and no
 * background of its own, so it sits on whatever sheet hosts it. Drawing reads touch state only in the draw phase,
 * so a finger move redraws one Canvas and never recomposes.
 */
@Composable
fun PatternLockView(
    modifier: Modifier = Modifier,
    patternState: PatternState = PatternState.IDLE,
    enabled: Boolean = true,
    onPatternStart: () -> Unit = {},
    onPatternComplete: (List<Int>) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val selectedDots = remember { mutableStateListOf<Int>() }
    var currentTouchPoint by remember { mutableStateOf<Offset?>(null) }
    var localState by remember { mutableStateOf(patternState) }
    // 0 = resting dot, 1 = touched: animated per dot for the native "pop" when the finger reaches it
    val dotScale = remember { List(9) { Animatable(0f) } }

    // The gesture loop is long-lived (keyed only on `enabled`), so it must read the latest callbacks
    val currentOnStart by rememberUpdatedState(onPatternStart)
    val currentOnComplete by rememberUpdatedState(onPatternComplete)

    LaunchedEffect(patternState) {
        localState = patternState
        if (patternState == PatternState.ERROR || patternState == PatternState.SUCCESS) {
            delay(700)
            selectedDots.clear()
            currentTouchPoint = null
            localState = PatternState.IDLE
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { selectedDots.toList() }.collect { dots ->
            for (i in 0 until 9) {
                val target = if (i in dots) 1f else 0f
                if (dotScale[i].targetValue != target) {
                    if (target == 1f) {
                        launch {
                            dotScale[i].animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy,
                                                             stiffness = Spring.StiffnessMedium))
                        }
                    } else {
                        dotScale[i].snapTo(0f)
                    }
                }
            }
        }
    }

    val dotColor = MaterialTheme.colorScheme.onSurface
    val accent by animateColorAsState(
        targetValue = when (localState) {
            PatternState.ERROR -> RoseError
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(durationMillis = 150),
        label = "patternAccent"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(12.dp)
            // Keyed only on `enabled`: keying on drawing state restarted this block on touch-down,
            // cancelling the in-flight gesture (the "first touch won't drag" bug).
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Ignore touches while the success/error result is showing
                    if (localState == PatternState.ERROR || localState == PatternState.SUCCESS) return@awaitEachGesture

                    val cellW = size.width / 3f
                    val cellH = size.height / 3f
                    val hitRadiusPx = cellW * 0.38f

                    fun checkHit(pos: Offset) {
                        for (i in 0 until 9) {
                            if (i in selectedDots) continue
                            val center = dotCenter(i, cellW, cellH)
                            if (hypot(pos.x - center.x, pos.y - center.y) <= hitRadiusPx) {
                                selectedDots.add(i)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                break
                            }
                        }
                    }

                    down.consume()
                    selectedDots.clear()
                    localState = PatternState.DRAWING
                    currentOnStart()
                    currentTouchPoint = down.position
                    checkHit(down.position)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        currentTouchPoint = change.position
                        checkHit(change.position)
                    }

                    currentTouchPoint = null
                    if (selectedDots.isNotEmpty()) {
                        currentOnComplete(selectedDots.toList())
                    } else {
                        localState = PatternState.IDLE
                    }
                }
            }
    ) {
        val cellW = size.width / 3f
        val cellH = size.height / 3f
        val restRadius = 5.dp.toPx()
        val touchedRadius = 8.dp.toPx()
        val haloRadius = 26.dp.toPx()

        // Line: under the dots, from dot to dot, then to the finger while drawing
        val touch = currentTouchPoint
        val drawing = localState == PatternState.DRAWING && touch != null
        if (selectedDots.size + (if (drawing) 1 else 0) >= 2) {
            val line = Path()
            selectedDots.forEachIndexed { idx, dot ->
                val c = dotCenter(dot, cellW, cellH)
                if (idx == 0) line.moveTo(c.x, c.y) else line.lineTo(c.x, c.y)
            }
            if (drawing) line.lineTo(touch!!.x, touch.y)
            drawPath(
                path = line,
                color = accent.copy(alpha = 0.55f),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        for (i in 0 until 9) {
            val center = dotCenter(i, cellW, cellH)
            val t = dotScale[i].value
            if (t > 0f) {
                drawCircle(color = accent.copy(alpha = 0.16f * t.coerceIn(0f, 1f)), radius = haloRadius * t, center = center)
            }
            drawCircle(
                color = if (i in selectedDots) accent else dotColor.copy(alpha = 0.75f),
                radius = restRadius + (touchedRadius - restRadius) * t,
                center = center
            )
        }
    }
}
