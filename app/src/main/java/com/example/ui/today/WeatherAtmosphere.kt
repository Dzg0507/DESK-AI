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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
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
import androidx.compose.ui.graphics.vector.ImageVector
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

/** A monochrome sign for the header, next to the temperature (no color emoji on Today). */
fun skyIcon(sky: Sky?): ImageVector? = when (sky) {
    Sky.Rain -> Icons.Default.Grain; Sky.Storm -> Icons.Default.Thunderstorm; Sky.ClearNight -> Icons.Default.NightsStay
    Sky.ClearDay -> Icons.Default.WbSunny; Sky.Golden -> Icons.Default.WbTwilight
    Sky.Cloudy, Sky.Fog -> Icons.Default.Cloud; null -> null
}

// Devin's spec (2026-10-28, second pass): rich and unmistakable on an OLED screen, not dark-on-dark
private val Base = Color(0xFF050806)
private val NightSapphire = Color(0xFF163B5C).copy(alpha = 0.90f)
private val NightAurora = Color(0xFF0E4D3B).copy(alpha = 0.75f)
private val MoonCore = Color(0xFF5CE1E6).copy(alpha = 0.45f)
private val MoonHalo = Color(0xFF247BA0).copy(alpha = 0.55f)
private val AuroraGreen = Color(0xFF38E58B).copy(alpha = 0.28f)
private val AuroraTeal = Color(0xFF2EC4B6).copy(alpha = 0.32f)
private val StarHalo = Color(0x6680FFBF)
private val StormTeal = Color(0xFF1F4E5B).copy(alpha = 0.90f)
private val CopperHorizon = Color(0xFF7A4419).copy(alpha = 0.65f)
private val RainMist = Color(0x338AB0AB)
private val DayCerulean = Color(0xFF1D63B8).copy(alpha = 0.85f)
private val SunFlare = Color(0xFFF59E0B).copy(alpha = 0.50f)
private val CloudDeck = Color(0xFF2B4C59).copy(alpha = 0.85f)
private val CloudMist = Color(0x407C9891)

/**
 * The weather behind the top of Today: no box, no border. It sits under the status bar, the top row, the recipe bar
 * and the date, 420 dp tall, and fades into the page (#050806) only between 340 and 420 dp, through a vertical alpha
 * mask (BlendMode.DstIn). Night: a sapphire-to-aurora sky, an ice-cyan moon glow behind the temperature, an aurora
 * ribbon, ten haloed hero stars and fifty twinkling ones. Rain: stormy teal, a copper horizon, visible streaks and
 * mist. Day: cerulean with a sun flare (warmer at golden hour). Clouds and fog: a silver-teal deck with mist bands.
 * As the cards scroll up it rises a little slower than them and fades out.
 */
@Composable
fun WeatherAtmosphere(weather: TodayWeather?, listState: LazyListState?, modifier: Modifier = Modifier) {
    val sky = skyFor(weather) ?: return
    val density = LocalDensity.current
    val fadeOver = with(density) { 320.dp.toPx() }
    val rise = with(density) { 120.dp.toPx() }
    val moonRadius = with(density) { 260.dp.toPx() }
    val moonY = with(density) { 150.dp.toPx() }           // about where the temperature sits
    val haloRadius = with(density) { 8.dp.toPx() }
    val heroRadius = with(density) { 1.5.dp.toPx() }
    // 0 at the top of the feed, 1 once the header has scrolled away
    val scrolled by remember(listState) {
        derivedStateOf {
            when {
                listState == null -> 0f
                listState.firstVisibleItemIndex > 2 -> 1f
                else -> ((listState.firstVisibleItemIndex * fadeOver * 0.5f + listState.firstVisibleItemScrollOffset) / fadeOver)
                    .coerceIn(0f, 1f)
            }
        }
    }
    val motion = rememberInfiniteTransition(label = "weather")
    val fall by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "rain")
    val drift by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Reverse), label = "mist")
    val twinkle by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse), label = "stars")
    val streaks = remember { List(90) { Random(it * 7 + 3).let { r -> Triple(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 0.8f) } } }
    val heroes = remember { List(10) { Random(it * 31 + 5).let { r -> Triple(r.nextFloat(), 0.04f + r.nextFloat() * 0.5f, r.nextFloat()) } } }
    val stars = remember { List(50) { Random(it * 13 + 1).let { r -> Triple(r.nextFloat(), r.nextFloat() * 0.72f, r.nextFloat()) } } }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(420.dp)
            .graphicsLayer {
                alpha = 1f - scrolled
                translationY = -scrolled * rise
                compositingStrategy = CompositingStrategy.Offscreen     // the mask below needs its own layer
            }
    ) {
        val skyEnd = (400.dp.toPx() / size.height).coerceAtMost(1f)
        when (sky) {
            Sky.ClearNight -> {
                drawRect(Brush.verticalGradient(0f to NightSapphire, skyEnd * 0.55f to NightAurora, skyEnd to Base))
                aurora(drift)
                glow(listOf(MoonCore, MoonHalo.copy(alpha = 0.30f), MoonHalo.copy(alpha = 0f)),
                    Offset(size.width * 0.86f, moonY), moonRadius)
                stars.forEach { (x, y, a) ->
                    val alpha = 0.4f + 0.5f * ((a + twinkle * (if (a > 0.5f) 1f else -1f)).mod(1f))
                    drawCircle(Color.White.copy(alpha = alpha), radius = 0.6f + a * 1.2f, center = Offset(x * size.width, y * size.height))
                }
                heroes.forEach { (x, y, _) ->
                    val c = Offset(x * size.width, y * size.height)
                    drawCircle(Brush.radialGradient(listOf(StarHalo, StarHalo.copy(alpha = 0f)), center = c, radius = haloRadius),
                        radius = haloRadius, center = c)
                    drawCircle(Color.White.copy(alpha = 0.95f), radius = heroRadius, center = c)
                }
            }
            Sky.Rain, Sky.Storm -> {
                drawRect(Brush.verticalGradient(0f to StormTeal, skyEnd * 0.6f to StormTeal.copy(alpha = 0.55f), skyEnd to Base))
                glow(listOf(CopperHorizon, CopperHorizon.copy(alpha = 0f)), Offset(size.width * 0.75f, size.height * 0.62f), size.width * 0.75f)
                mist(drift, RainMist, 3)
                val len = size.height * 0.06f
                streaks.forEachIndexed { i, (x, y, speed) ->
                    val yy = ((y + fall * speed) % 1f) * size.height
                    val xx = x * size.width * 1.25f - yy * 0.28f
                    val tint = if (i % 3 == 0) Color(0xFFBFF3F8) else Color.White
                    drawLine(tint.copy(alpha = if (sky == Sky.Storm) 0.35f else 0.25f + (i % 4) * 0.03f),
                        Offset(xx, yy), Offset(xx - len * 0.3f, yy + len), strokeWidth = 1.6f)
                }
            }
            Sky.ClearDay, Sky.Golden -> {
                drawRect(Brush.verticalGradient(0f to DayCerulean, skyEnd * 0.6f to DayCerulean.copy(alpha = 0.45f), skyEnd to Base))
                glow(listOf(SunFlare.copy(alpha = if (sky == Sky.Golden) 0.65f else 0.50f), SunFlare.copy(alpha = 0f)),
                    Offset(size.width * 0.92f, 0f), moonRadius * (if (sky == Sky.Golden) 1.4f else 1.1f))
            }
            Sky.Cloudy, Sky.Fog -> {
                drawRect(Brush.verticalGradient(0f to CloudDeck, skyEnd * 0.6f to CloudDeck.copy(alpha = 0.5f), skyEnd to Base))
                mist(drift, CloudMist, if (sky == Sky.Fog) 6 else 4)
            }
        }
        // Into the page between 340 and 420 dp only: no edge anywhere
        val fadeFrom = (340.dp.toPx() / size.height).coerceIn(0f, 1f)
        drawRect(Brush.verticalGradient(0f to Color.Black, fadeFrom to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
    }
}

private fun DrawScope.glow(colors: List<Color>, center: Offset, radius: Float) =
    drawRect(Brush.radialGradient(colors, center = center, radius = radius))

/** The aurora: a soft diagonal ribbon, emerald into teal, sweeping across the header and swaying slowly. */
private fun DrawScope.aurora(drift: Float) {
    val sway = (drift - 0.5f) * size.width * 0.12f
    drawRect(Brush.linearGradient(
        0f to Color.Transparent, 0.38f to AuroraGreen, 0.52f to AuroraTeal, 0.68f to Color.Transparent,
        start = Offset(size.width * 0.05f + sway, 0f), end = Offset(size.width * 0.75f + sway, size.height * 0.8f)))
}

/** Mist: wide radial bands drifting slowly side to side. */
private fun DrawScope.mist(drift: Float, color: Color, bands: Int) {
    for (i in 0 until bands) {
        val phase = drift * 2f * PI.toFloat() + i * 1.7f
        val c = Offset(size.width * (0.1f + i * (0.85f / bands)) + sin(phase) * size.width * 0.1f,
            size.height * (0.2f + (i % 3) * 0.17f))
        val r = size.width * 0.5f
        drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = c, radius = r), radius = r, center = c)
    }
}
