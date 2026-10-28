package com.example.ui.today

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.data.model.TodayWeather
import com.example.ui.theme.DeskColors
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** The sky over Today's header, from the weather now and the time of day. */
enum class Sky { Rain, Storm, ClearNight, ClearDay, Golden, Cloudy, Fog }

/** The open-meteo summary ("Light rain", "Mostly clear"...) and day/night -> the sky to draw; defaults by hour if null. */
fun skyFor(weather: TodayWeather?, hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): Sky? {
    weather ?: return null
    val s = weather.summary.lowercase()
    return when {
        "thunder" in s -> Sky.Storm
        listOf("rain", "drizzle", "shower").any { it in s } -> Sky.Rain
        "fog" in s -> Sky.Fog
        "cloud" in s || "snow" in s -> Sky.Cloudy
        !weather.isDay -> Sky.ClearNight
        hour in 6..8 || hour in 17..19 -> Sky.Golden
        else -> Sky.ClearDay
    }
}

/** A one-glyph sign for the header, next to the temperature. */
fun skyGlyph(sky: Sky?): String = when (sky) {
    Sky.Rain -> "🌧"; Sky.Storm -> "⛈"; Sky.ClearNight -> "☾"; Sky.ClearDay -> "☀"; Sky.Golden -> "🌅"
    Sky.Cloudy -> "☁"; Sky.Fog -> "🌫"; null -> ""
}

private val RainTeal = Color(0xFF13363D)
private val HorizonAmber = Color(0xFF2B1F14)
private val TwilightCyan = DeskColors.WeatherTwilightCyan     // #331B4D5C
private val DeepEmeraldGlow = DeskColors.WeatherDeepEmerald   // #26113828
private val NightFadeBase = Color(0x00050806)                 // smooth fade into #050806
private val DaySky = Color(0xFF14404D)
private val GoldenAmber = Color(0xFF3A2A12)
private val GoldenEmerald = Color(0xFF12301F)
private val Mist = Color(0xFF16241F)
private val StormSlate = Color(0xFF0E252C)

private data class StarParticle(
    val x: Float,
    val y: Float,
    val sizeDp: Float,
    val alpha: Float
)

/**
 * Multi-layered weather atmosphere filling the top 380 dp edge-to-edge behind the status bar and top nav.
 */
@Composable
fun WeatherAtmosphere(weather: TodayWeather?, listState: LazyListState?, modifier: Modifier = Modifier) {
    WeatherAtmosphereCanvas(weather = weather, listState = listState, modifier = modifier)
}

@Composable
fun WeatherAtmosphereCanvas(weather: TodayWeather?, listState: LazyListState?, modifier: Modifier = Modifier) {
    val sky = skyFor(weather) ?: Sky.ClearNight
    val density = LocalDensity.current
    val fadeOver = with(density) { 320.dp.toPx() }
    val rise = with(density) { 120.dp.toPx() }
    // 0 at the top of the feed, 1 once the header has scrolled away
    val scrolled by remember(listState) {
        derivedStateOf {
            when {
                listState == null -> 0f
                listState.firstVisibleItemIndex > 1 -> 1f
                listState.firstVisibleItemIndex == 1 -> 0.6f + 0.4f * (listState.firstVisibleItemScrollOffset / fadeOver).coerceIn(0f, 1f)
                else -> (listState.firstVisibleItemScrollOffset / fadeOver * 0.6f).coerceIn(0f, 0.6f)
            }
        }
    }
    val motion = rememberInfiniteTransition(label = "weather")
    val fall by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "rain")
    val drift by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Reverse), label = "mist")
    val streaks = remember {
        List(70) {
            Random(it * 7 + 3).let { r -> Triple(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 0.8f) }
        }
    }
    // 35 varied-size star particles (1 dp to 2.5 dp, varying alpha 0.15f..0.75f)
    val stars = remember {
        List(35) { i ->
            val r = Random(i * 13 + 7)
            StarParticle(
                x = r.nextFloat(),
                y = r.nextFloat() * 0.78f,
                sizeDp = 1.0f + r.nextFloat() * 1.5f,
                alpha = 0.15f + r.nextFloat() * 0.60f
            )
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(380.dp)
            .graphicsLayer {
                alpha = 1f - scrolled
                translationY = -scrolled * rise
                compositingStrategy = CompositingStrategy.Offscreen     // the mask below needs its own layer
            }
    ) {
        // Layer 1: Upper-right celestial radial glow behind the temperature (#331B4D5C twilight cyan -> #26113828 deep emerald -> #050806)
        drawRect(
            brush = Brush.radialGradient(
                0.0f to TwilightCyan,
                0.48f to DeepEmeraldGlow,
                1.0f to NightFadeBase,
                center = Offset(size.width * 0.82f, size.height * 0.18f),
                radius = size.width * 1.15f
            )
        )
        // Secondary upper-left emerald-cyan canopy glow
        drawRect(
            brush = Brush.radialGradient(
                0.0f to DeepEmeraldGlow,
                0.52f to TwilightCyan.copy(alpha = 0.12f),
                1.0f to NightFadeBase,
                center = Offset(size.width * 0.18f, size.height * 0.08f),
                radius = size.width * 0.95f
            )
        )
        // Layer 2: Soft diagonal atmospheric light haze from upper-right toward lower-left
        drawRect(
            brush = Brush.linearGradient(
                0.0f to TwilightCyan.copy(alpha = 0.24f),
                0.45f to DeepEmeraldGlow.copy(alpha = 0.18f),
                1.0f to Color.Transparent,
                start = Offset(size.width * 0.95f, 0f),
                end = Offset(size.width * 0.05f, size.height * 0.85f)
            )
        )

        when (sky) {
            Sky.Rain, Sky.Storm -> {
                glow(if (sky == Sky.Storm) StormSlate else RainTeal, Offset(size.width * 0.3f, 0f), size.width * 1.1f)
                glow(HorizonAmber, Offset(size.width * 0.8f, size.height * 0.75f), size.width * 0.8f)
                mist(drift, 0.55f)
                val len = size.height * 0.07f
                streaks.forEach { (x, y, speed) ->
                    val yy = ((y + fall * speed) % 1f) * size.height
                    val xx = x * size.width * 1.2f - yy * 0.25f
                    drawLine(
                        Color.White.copy(alpha = if (sky == Sky.Storm) 0.11f else 0.08f),
                        Offset(xx, yy),
                        Offset(xx - len * 0.3f, yy + len),
                        strokeWidth = 1.2f
                    )
                }
            }
            Sky.ClearNight -> {
                mist(drift, 0.32f)
                stars.forEach { star ->
                    val diameterPx = star.sizeDp.dp.toPx()
                    val radiusPx = diameterPx / 2f
                    val center = Offset(star.x * size.width, star.y * size.height)
                    if (star.alpha > 0.45f) {
                        drawCircle(
                            color = Color(0xFFD8F3DC).copy(alpha = star.alpha * 0.22f),
                            radius = radiusPx * 2.3f,
                            center = center
                        )
                    }
                    drawCircle(
                        color = Color.White.copy(alpha = star.alpha),
                        radius = radiusPx,
                        center = center
                    )
                }
            }
            Sky.ClearDay -> {
                glow(DaySky, Offset(size.width, 0f), size.width * 1.3f)
                glow(DeepEmeraldGlow, Offset(0f, size.height * 0.3f), size.width * 0.7f)
            }
            Sky.Golden -> {
                glow(GoldenAmber, Offset(size.width, 0f), size.width * 1.2f)
                glow(GoldenEmerald, Offset(0f, size.height * 0.35f), size.width * 0.8f)
            }
            Sky.Cloudy, Sky.Fog -> {
                glow(StormSlate, Offset(size.width * 0.5f, 0f), size.width * 1.1f)
                mist(drift, if (sky == Sky.Fog) 0.9f else 0.7f)
            }
        }
        // Fade into the page: full at the top, gone at the bottom (no edge anywhere)
        drawRect(
            Brush.verticalGradient(
                0f to Color.Black,
                0.6f to Color.Black.copy(alpha = 0.78f),
                1f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }
}

private fun DrawScope.glow(color: Color, center: Offset, radius: Float) =
    drawRect(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = radius))

/** Soft mist: a few wide radial blobs drifting slowly side to side. */
private fun DrawScope.mist(drift: Float, strength: Float) {
    for (i in 0 until 4) {
        val phase = drift * 2f * PI.toFloat() + i * 1.7f
        val c = Offset(size.width * (0.15f + i * 0.25f) + sin(phase) * size.width * 0.08f, size.height * (0.25f + (i % 2) * 0.25f))
        drawCircle(
            Brush.radialGradient(listOf(Mist.copy(alpha = strength), Mist.copy(alpha = 0f)), center = c, radius = size.width * 0.45f),
            radius = size.width * 0.45f,
            center = c
        )
    }
}
