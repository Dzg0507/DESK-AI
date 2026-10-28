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
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** The sky over Today's header, from the weather now and the time of day. */
enum class Sky { Rain, Storm, ClearNight, ClearDay, Golden, Cloudy, Fog }

/** The open-meteo summary ("Light rain", "Mostly clear"...) and day/night -> the sky to draw; null without weather. */
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

private val RainTeal = Color(0xFF0F292E)
private val HorizonAmber = Color(0xFF2B1F14)
private val NightCerulean = Color(0xFF081C24)
private val NightEmerald = Color(0xFF0B2219)
private val DaySky = Color(0xFF14404D)
private val GoldenAmber = Color(0xFF3A2A12)
private val GoldenEmerald = Color(0xFF12301F)
private val Mist = Color(0xFF16241F)
private val StormSlate = Color(0xFF0B1D22)

/**
 * The weather behind the top of Today (2026-10-28, Devin's spec with a reference image): no box, no border. It sits
 * under the status bar, the agent pill, the Chat | Today switch and the date, about 380 dp tall, and fades into the
 * background through a vertical alpha mask (BlendMode.DstIn). Rain gets slow diagonal streaks at 8% white and mist,
 * a clear night faint stars, a clear day light from the top corner (warm at golden hour), clouds and fog drifting
 * mist. As the cards scroll up it rises a little slower than them and fades out.
 */
@Composable
fun WeatherAtmosphere(weather: TodayWeather?, listState: LazyListState?, modifier: Modifier = Modifier) {
    val sky = skyFor(weather) ?: return
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
    val streaks = remember { List(70) { Random(it * 7 + 3).let { r -> Triple(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 0.8f) } } }
    val stars = remember { List(55) { Random(it * 13 + 1).let { r -> Triple(r.nextFloat(), r.nextFloat() * 0.75f, r.nextFloat()) } } }

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
        when (sky) {
            Sky.Rain, Sky.Storm -> {
                glow(if (sky == Sky.Storm) StormSlate else RainTeal, Offset(size.width * 0.3f, 0f), size.width * 1.1f)
                glow(HorizonAmber, Offset(size.width * 0.8f, size.height * 0.75f), size.width * 0.8f)
                mist(drift, 0.55f)
                val len = size.height * 0.07f
                streaks.forEach { (x, y, speed) ->
                    val yy = ((y + fall * speed) % 1f) * size.height
                    val xx = x * size.width * 1.2f - yy * 0.25f
                    drawLine(Color.White.copy(alpha = if (sky == Sky.Storm) 0.11f else 0.08f),
                        Offset(xx, yy), Offset(xx - len * 0.3f, yy + len), strokeWidth = 1.2f)
                }
            }
            Sky.ClearNight -> {
                glow(NightCerulean, Offset(size.width * 0.75f, 0f), size.width * 1.1f)
                glow(NightEmerald, Offset(size.width * 0.15f, size.height * 0.2f), size.width * 0.8f)
                stars.forEach { (x, y, a) ->
                    drawCircle(Color.White.copy(alpha = 0.12f + a * 0.35f), radius = 0.8f + a * 1.1f,
                        center = Offset(x * size.width, y * size.height))
                }
            }
            Sky.ClearDay -> {
                glow(DaySky, Offset(size.width, 0f), size.width * 1.3f)
                glow(NightEmerald, Offset(0f, size.height * 0.3f), size.width * 0.7f)
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
        drawRect(Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black.copy(alpha = 0.75f), 1f to Color.Transparent),
            blendMode = BlendMode.DstIn)
    }
}

private fun DrawScope.glow(color: Color, center: Offset, radius: Float) =
    drawRect(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = radius))

/** Soft mist: a few wide radial blobs drifting slowly side to side. */
private fun DrawScope.mist(drift: Float, strength: Float) {
    for (i in 0 until 4) {
        val phase = drift * 2f * PI.toFloat() + i * 1.7f
        val c = Offset(size.width * (0.15f + i * 0.25f) + sin(phase) * size.width * 0.08f, size.height * (0.25f + (i % 2) * 0.25f))
        drawCircle(Brush.radialGradient(listOf(Mist.copy(alpha = strength), Mist.copy(alpha = 0f)), center = c,
            radius = size.width * 0.45f), radius = size.width * 0.45f, center = c)
    }
}
