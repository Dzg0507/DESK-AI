package com.example.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import com.example.ui.theme.Cerulean
import com.example.ui.theme.CeruleanText
import com.example.ui.theme.CircuitBlack
import com.example.ui.theme.TerminalGreen
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.random.Random

/*
 * The Circuit look (the owner's pick, 2026-10-11): green-black ground, a faint hex circuit pattern with cerulean
 * nodes, terminal-green and deep-cerulean light from the corners, scanlines, and black lightning: every few
 * seconds the sky flashes deep cerulean and a thick black bolt with a thin cerulean rim cuts through it.
 */

/** The circuit texture and its glows, drawn behind [this] (sheets use it without the lightning). */
fun Modifier.circuitTexture(glow: Float = 1f): Modifier = this.drawWithCache {
    val hex = hexPattern(size)
    val nodes = hexNodes(size)
    onDrawBehind {
        drawRect(CircuitBlack)
        drawPath(hex, TerminalGreen.copy(alpha = 0.10f), style = Stroke(width = 1f))
        nodes.forEach { drawCircle(Cerulean.copy(alpha = 0.55f), radius = 2.2f, center = it) }
        // light from the corners: green top-right, deep cerulean bottom-left (the strongest), cerulean right and top
        drawGlow(Offset(size.width * 0.85f, 0f), size.width * 0.75f, TerminalGreen, 0.20f * glow)
        drawGlow(Offset(0f, size.height), size.width * 1.0f, Cerulean, 0.62f * glow)
        drawGlow(Offset(size.width, size.height * 0.62f), size.width * 0.55f, Cerulean, 0.35f * glow)
        drawGlow(Offset(size.width * 0.2f, 0f), size.width * 0.45f, Cerulean, 0.25f * glow)
        // scanlines
        var y = 0f
        while (y < size.height) {
            drawRect(TerminalGreen.copy(alpha = 0.03f), topLeft = Offset(0f, y), size = Size(size.width, 1f))
            y += 3.5f * density
        }
    }
}

private fun DrawScope.drawGlow(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), center = center, radius = radius),
        radius = radius, center = center
    )
}

/** Hexagon cells with a stem down from each (the circuit), sized to the screen's density. */
private fun androidx.compose.ui.draw.CacheDrawScope.hexPattern(size: Size): Path {
    val w = 56f * density * 0.75f
    val h = 97f * density * 0.75f
    val p = Path()
    var y = 0f
    while (y < size.height + h) {
        var x = 0f
        while (x < size.width + w) {
            val cx = x + w / 2
            p.moveTo(cx, y)
            p.lineTo(x + w, y + h * 0.165f)
            p.lineTo(x + w, y + h * 0.495f)
            p.lineTo(cx, y + h * 0.66f)
            p.lineTo(x, y + h * 0.495f)
            p.lineTo(x, y + h * 0.165f)
            p.close()
            p.moveTo(cx, y + h * 0.66f)
            p.lineTo(cx, y + h)
            x += w
        }
        y += h
    }
    return p
}

private fun androidx.compose.ui.draw.CacheDrawScope.hexNodes(size: Size): List<Offset> {
    val w = 56f * density * 0.75f
    val h = 97f * density * 0.75f
    val out = ArrayList<Offset>()
    var y = 0f
    while (y < size.height + h) {
        var x = 0f
        while (x < size.width + w) {
            out.add(Offset(x + w / 2, y + h * 0.66f))
            x += w
        }
        y += h
    }
    return out
}

private class Bolt(val main: List<Offset>, val branches: List<List<Offset>>)

private fun displace(a: Offset, b: Offset, rough: Float, depth: Int, out: MutableList<Offset>, r: Random) {
    if (depth == 0) { out.add(b); return }
    val m = Offset((a.x + b.x) / 2 + (r.nextFloat() - .5f) * rough, (a.y + b.y) / 2 + (r.nextFloat() - .5f) * rough * .35f)
    displace(a, m, rough / 2, depth - 1, out, r)
    displace(m, b, rough / 2, depth - 1, out, r)
}

private fun makeBolt(w: Float, h: Float, r: Random): Bolt {
    val x0 = w * (.15f + r.nextFloat() * .7f)
    val start = Offset(x0, -10f)
    val end = Offset(x0 + (r.nextFloat() - .5f) * w * .7f, h * (.55f + r.nextFloat() * .4f))
    val main = mutableListOf(start)
    displace(start, end, w * .45f, 7, main, r)
    val branches = (0 until 2 + r.nextInt(3)).map {
        val from = main[4 + r.nextInt(main.size - 8)]
        val to = Offset(from.x + (r.nextFloat() - .5f) * w * .5f, from.y + h * (.08f + r.nextFloat() * .2f))
        mutableListOf(from).also { displace(from, to, w * .18f, 5, it, r) }
    }
    return Bolt(main, branches)
}

private fun List<Offset>.toPath(): Path = Path().also { p ->
    p.moveTo(first().x, first().y)
    drop(1).forEach { p.lineTo(it.x, it.y) }
}

/**
 * The app's background: the circuit texture plus black lightning. The lightning only runs while the app is in
 * front, and never when the phone's animations are turned off.
 */
@Composable
fun CircuitBackground(modifier: Modifier = Modifier, lightning: Boolean = true) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val motionOff = remember {
        try { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
        catch (_: Exception) { false }
    }
    var bolt by remember { mutableStateOf<Bolt?>(null) }
    var area by remember { mutableStateOf(Size.Zero) }
    val strike = remember { Animatable(0f) }
    if (lightning && !motionOff) {
        LaunchedEffect(Unit) {
            val r = Random(System.nanoTime())
            delay(1500)
            while (true) {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && area.width > 0f) {
                    bolt = makeBolt(area.width, area.height, r)
                    strike.snapTo(0f)
                    // two quick flickers, then a fade (as in the preview the owner approved)
                    strike.animateTo(0f, keyframes {
                        durationMillis = 520
                        1f at 0; 1f at 70; 0f at 71; 0f at 120; 0.9f at 121; 0.9f at 200; 0f at 520
                    })
                    bolt = null
                }
                delay(2600L + r.nextLong(4200L))
            }
        }
    }
    Box(modifier = modifier.fillMaxSize().circuitTexture().onSizeChanged { area = Size(it.width.toFloat(), it.height.toFloat()) }) {
        Canvas(Modifier.fillMaxSize()) {
            val b = bolt ?: return@Canvas
            val a = strike.value
            if (a <= 0f) return@Canvas
            // the sky lights up deep cerulean around the bolt, so the bolt shows BLACK against it
            val mid = b.main[b.main.size / 2]
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Cerulean.copy(alpha = .62f * a), .45f to Cerulean.copy(alpha = .28f * a), 1f to Color.Transparent,
                    center = mid, radius = max(size.width, size.height) * .75f
                ),
                radius = max(size.width, size.height) * .75f, center = mid
            )
            (listOf(b.main) + b.branches).forEachIndexed { i, pts ->
                val k = if (i == 0) 1f else .5f
                val path = pts.toPath()
                fun line(width: Float, color: Color) = drawPath(path, color, style = Stroke(width * density * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
                line(18f, Color.Black.copy(alpha = .28f * a))             // black haze around it
                line(11f, CeruleanText.copy(alpha = .40f * a))            // soft rim glow
                line(8f, CeruleanText.copy(alpha = .95f * a))             // thin cerulean rim
                line(5.5f, Color.Black.copy(alpha = a))                   // the black body
            }
        }
    }
}
