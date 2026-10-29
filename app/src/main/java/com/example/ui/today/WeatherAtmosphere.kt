package com.example.ui.today

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.data.model.TodayWeather
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Open sky left between the top bar and the date, for the moon (and the sun on its way through). */
val SKY_BAND = 64.dp

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

/**
 * What to draw, worked out from the real weather and the clock (Devin, 2026-10-28: "a night sky is black, not
 * greenish"; the real moon phase, a sun that crosses the sky through the day, clouds passing over the moon).
 */
data class SkyState(
    val altitude: Float,        // the sun: 1 at its noon high, 0 at sunrise/sunset, -1 an hour and a half into the night
    val sunX: Float, val sunY: Float,       // 0..1 of the sky's width and height
    val moonX: Float, val moonY: Float,
    val moonUp: Float,          // 0..1: the moon at night (always there, drifting west through the night)
    val phase: Float,           // the moon's age, 0 new .. 0.5 full .. 1 new
    val cloud: Float,           // 0..1 cover
    val windX: Float, val windY: Float,     // cloud drift, sky widths per second
    val rain: Float, val snow: Float, val storm: Float, val fog: Float,
    val sunStyle: Int = 0
)

/** The moon's age in its 29.53-day cycle, 0..1, from a known new moon (2000-01-06 18:14 UTC). */
fun moonPhase(epochSeconds: Long): Float {
    val days = (epochSeconds - 947182440L) / 86400.0
    return ((days / 29.530588853) % 1.0).let { if (it < 0) it + 1 else it }.toFloat()
}

fun skyState(w: TodayWeather?, nowSeconds: Long, preview: SkyPreview.Override? = null): SkyState {
    val now = preview?.at ?: nowSeconds
    // Today's sunrise and sunset from the agent; a plain 7:00 / 18:30 without them
    val midnight = Calendar.getInstance().apply {
        timeInMillis = now * 1000; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis / 1000
    fun sameDay(t: Long, fallback: Long) = if (t > 0) midnight + ((t - midnight) % 86400 + 86400) % 86400 else midnight + fallback
    val rise = sameDay(w?.sunrise ?: 0, 7 * 3600L)
    val set = sameDay(w?.sunset ?: 0, 18 * 3600L + 1800)
    val dayT = (now - rise).toFloat() / (set - rise)
    // At night: how far from the nearest sunset or sunrise, -1 after an hour and a half (twilight in between)
    val altitude = if (dayT in 0f..1f) sin(PI.toFloat() * dayT)
    else -(listOf(set, rise + 86400, rise, set - 86400).minOf { abs(now - it) } / 5400f).coerceAtMost(1f)
    val t = dayT.coerceIn(-0.03f, 1.03f)
    // The night runs sunset -> next sunrise; the moon crosses it east to west, high in the middle
    val nightStart = if (now >= set) set else set - 86400
    val nightEnd = if (now < rise) rise else rise + 86400
    val p = ((now - nightStart).toFloat() / (nightEnd - nightStart)).coerceIn(0f, 1f)

    val code = preview?.code ?: w?.code ?: 0
    val cover = (preview?.cloud ?: w?.cloudCover ?: 0) / 100f
    val rainMm = preview?.rain ?: w?.rainMm ?: 0.0
    val snowCm = preview?.snow ?: w?.snowCm ?: 0.0
    val isRain = code in 51..67 || code in 80..82 || code >= 95
    val isSnow = code in 71..77 || code in 85..86
    // Toward where the wind blows; looking south, east is left and north is up the screen. Always a visible sideways
    // drift (a north or south wind, or no wind data, left the clouds standing still: 2026-10-29), faster in real wind
    val toward = Math.toRadians(((preview?.windDir ?: w?.windDir?.takeIf { (w.windMph) > 0 } ?: 270) + 180.0) % 360)
    val mph = (preview?.wind ?: w?.windMph ?: 0.0).toFloat().coerceIn(0f, 40f)
    val speed = 0.010f + mph * 0.0012f
    val side = (-sin(toward)).toFloat().let { if (abs(it) < 0.5f) (if (it < 0f) -0.5f else 0.5f) else it }
    return SkyState(
        altitude = altitude,
        // The sun: up from behind the cards in the east, highest in the open sky band at noon (never in the status
        // bar: the camera hole is there), down behind them in the west
        // It rises fast and keeps to the band most of the day, crossing the date row only near sunrise and sunset
        // (sin^0.4: an afternoon sun sat behind the temperature)
        sunX = 0.06f + 0.88f * t,
        sunY = 0.60f - 0.29f * Math.pow(sin(PI * t.coerceIn(0f, 1f)).coerceAtLeast(0.0), 0.4).toFloat(),
        // The moon: across the open band of sky above the date (TodayScreen's SKY_BAND), east to west through the night
        moonX = 0.12f + 0.76f * p, moonY = 0.315f - 0.03f * sin(PI.toFloat() * p),
        moonUp = ((-altitude - 0.05f) / 0.25f).coerceIn(0f, 1f),
        phase = preview?.phase ?: moonPhase(now),
        cloud = cover.coerceIn(0f, 1f),
        windX = side * speed, windY = (-cos(toward)).toFloat() * speed * 0.15f,
        rain = if (isRain || rainMm > 0) (0.25f + rainMm.toFloat() / 3f).coerceIn(0f, 1f) else 0f,
        snow = if (isSnow || snowCm > 0) (0.3f + snowCm.toFloat() / 1.5f).coerceIn(0f, 1f) else 0f,
        storm = if (code >= 95) 1f else 0f,
        fog = if (code == 45 || code == 48) 1f else 0f,
        sunStyle = preview?.sun ?: 0
    )
}

/**
 * The weather behind the top of Today: no box, no border, 420 dp under the status bar, the top row, the recipe bar
 * and the date, fading into the page between 340 and 420 dp. As the cards scroll up it rises and fades out.
 * Android 13+ draws it with a GPU shader ([SKY_SHADER]); older phones get a plain gradient with the sun or moon.
 */
@Composable
fun WeatherAtmosphere(weather: TodayWeather?, listState: LazyListState?, modifier: Modifier = Modifier) {
    if (weather == null && SkyPreview.current == null) return
    val density = LocalDensity.current
    val fadeOver = with(density) { 320.dp.toPx() }
    val rise = with(density) { 120.dp.toPx() }
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
    var seconds by remember { mutableFloatStateOf(0f) }
    var sky by remember { mutableStateOf(skyState(weather, System.currentTimeMillis() / 1000, SkyPreview.current)) }
    val preview = SkyPreview.current
    LaunchedEffect(weather, preview) {
        val start = withFrameNanos { it }
        var lastSky = 0L
        while (true) {
            withFrameNanos { frame ->
                seconds = (frame - start) / 1e9f
                val now = System.currentTimeMillis() / 1000
                if (now - lastSky >= 30) { sky = skyState(weather, now, preview); lastSky = now }   // the sun moves slowly
            }
        }
    }
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) RuntimeShader(SKY_SHADER) else null }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(420.dp)
            .graphicsLayer {
                alpha = 1f - scrolled
                translationY = -scrolled * rise
                compositingStrategy = CompositingStrategy.Offscreen     // the fade mask below needs its own layer
            }
    ) {
        if (shader != null && Build.VERSION.SDK_INT >= 33) drawShaderSky(shader, sky, seconds) else drawPlainSky(sky)
        // Into the page between 340 and 420 dp only: no edge anywhere
        val fadeFrom = (340.dp.toPx() / size.height).coerceIn(0f, 1f)
        drawRect(Brush.verticalGradient(0f to Color.Black, fadeFrom to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
    }
}

@RequiresApi(33)
private fun DrawScope.drawShaderSky(shader: RuntimeShader, s: SkyState, seconds: Float) {
    shader.setFloatUniform("uRes", size.width, size.height)
    shader.setFloatUniform("uTime", seconds % 3600f)
    shader.setFloatUniform("uDp", density)
    shader.setFloatUniform("uAlt", s.altitude)
    shader.setFloatUniform("uSun", s.sunX * size.width, s.sunY * size.height)
    shader.setFloatUniform("uMoon", s.moonX * size.width, s.moonY * size.height)
    shader.setFloatUniform("uMoonR", 20.dp.toPx())
    shader.setFloatUniform("uMoonUp", s.moonUp)
    shader.setFloatUniform("uPhase", s.phase)
    shader.setFloatUniform("uCloud", s.cloud)
    shader.setFloatUniform("uWind", s.windX, s.windY)
    shader.setFloatUniform("uRain", s.rain)
    shader.setFloatUniform("uSnow", s.snow)
    shader.setFloatUniform("uStorm", s.storm)
    shader.setFloatUniform("uFog", s.fog)
    shader.setFloatUniform("uSunStyle", s.sunStyle.toFloat())
    drawRect(ShaderBrush(shader))
}

/** Before Android 13: the sky's colors and the sun or a plain moon, no clouds. */
private fun DrawScope.drawPlainSky(s: SkyState) {
    val day = ((s.altitude + 0.05f) / 0.3f).coerceIn(0f, 1f)
    val top = lerp(Color(0xFF010205), Color(0xFF1A54B8), day)
    val horizon = lerp(Color(0xFF050A17), Color(0xFF4D85CC), day)
    drawRect(Brush.verticalGradient(0f to top, 0.8f to horizon))
    if (s.altitude > -0.08f) {
        val c = Offset(s.sunX * size.width, s.sunY * size.height)
        drawCircle(Brush.radialGradient(listOf(Color(0x66FFE6B0), Color.Transparent), c, 90.dp.toPx()), 90.dp.toPx(), c)
        drawCircle(Color(0xFFFFF6E0), 13.dp.toPx(), c)
    }
    if (s.moonUp > 0f) drawCircle(Color(0xFFE8E6DF).copy(alpha = s.moonUp * 0.9f), 20.dp.toPx(),
        Offset(s.moonX * size.width, s.moonY * size.height))
}

/** AGSL: the sky, stars, sun, the moon in its phase, clouds drifting with the wind, rain, snow, lightning and fog. */
private const val SKY_SHADER = """
uniform float2 uRes;
uniform float uTime;
uniform float uDp;
uniform float uAlt;
uniform float2 uSun;
uniform float2 uMoon;
uniform float uMoonR;
uniform float uMoonUp;
uniform float uPhase;
uniform float uCloud;
uniform float2 uWind;
uniform float uRain;
uniform float uSnow;
uniform float uStorm;
uniform float uFog;
uniform float uSunStyle;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1, 0)), u.x), mix(hash(i + float2(0, 1)), hash(i + float2(1, 1)), u.x), u.y);
}
float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + float2(17.0, 9.0);
        a *= 0.5;
    }
    return v;
}
float mare(float2 m, float2 c, float r) {
    float2 d = m - c;
    return exp(-dot(d, d) / (r * r));
}

half4 main(float2 fc) {
    float2 uv = fc / uRes;
    float aspect = uRes.x / uRes.y;
    float h = clamp(uv.y / 0.8, 0.0, 1.0);

    // The sky: black at night with a deep navy horizon; blue by day; warm at sunrise and sunset
    float day = smoothstep(-0.05, 0.3, uAlt);
    float twi = smoothstep(-0.4, -0.02, uAlt) * (1.0 - smoothstep(0.05, 0.4, uAlt));
    float3 top = mix(float3(0.003, 0.006, 0.016), float3(0.06, 0.24, 0.60), day);
    float3 hor = mix(float3(0.016, 0.032, 0.075), float3(0.30, 0.52, 0.80), day);
    float sunSide = exp(-pow((uv.x - uSun.x / uRes.x) * 1.4, 2.0));
    hor = mix(hor, float3(0.98, 0.52, 0.28), twi * (0.45 + 0.55 * sunSide));
    top = mix(top, float3(0.10, 0.14, 0.34), twi * 0.55 * (1.0 - day));
    float3 col = mix(top, hor, pow(h, 1.5));
    float grey = dot(col, float3(0.3, 0.59, 0.11));
    col = mix(col, float3(grey) * mix(0.78, 0.5, uStorm), uCloud * 0.8);

    // Stars: fine points, a few brighter, a slow twinkle; they fade at dusk
    float starVis = 1.0 - smoothstep(-0.3, -0.04, uAlt);
    float2 g = fc / (uDp * 13.0);
    float2 id = floor(g);
    float2 f = fract(g) - 0.5;
    float r = hash(id);
    if (r > 0.80 && starVis > 0.0) {
        float2 off = float2(hash(id + 3.1), hash(id + 7.7)) - 0.5;
        float d = length(f - off * 0.7) * 13.0;
        float mag = pow(hash(id + 1.3), 4.0);
        float tw = 0.7 + 0.3 * sin(uTime * (0.8 + 2.5 * hash(id + 5.0)) + r * 40.0);
        col += float3(0.85, 0.9, 1.0) * smoothstep(0.55 + 0.9 * mag, 0.0, d) * (0.3 + 0.9 * mag) * tw * starVis * (1.0 - h * 0.6);
    }

    // The sun (three looks to choose from, uSunStyle 0/1/2; Devin, 2026-10-29)
    float2 sd = (fc - uSun) / uDp;
    float ds = length(sd);
    float sunVis = smoothstep(-0.1, 0.02, uAlt);
    float high = smoothstep(0.0, 0.45, uAlt);
    float3 warm = mix(float3(1.0, 0.42, 0.12), float3(1.0, 0.86, 0.62), high);
    float sunClear = 1.0 - uCloud * 0.8;
    float3 sun = float3(0.0);
    if (uSunStyle < 0.5) {
        // A: soft glow, no edge: the light itself, brightening the sky around it
        sun = mix(warm, float3(1.0), 0.6) * exp(-ds / 16.0) * 1.1 + warm * (exp(-ds / 55.0) * 0.45 + exp(-ds / 170.0) * 0.3);
    } else if (uSunStyle < 1.5) {
        // B: photographic: a small brilliant disc, strong bloom, a faint 22-degree halo ring
        float disc = smoothstep(7.5, 5.5, ds);
        float ring = exp(-pow((ds - 95.0) / 9.0, 2.0)) * 0.07 * high;
        sun = float3(1.0) * disc * 1.5 + mix(warm, float3(1.0), 0.5) * exp(-ds / 10.0) * 0.8
            + warm * (exp(-ds / 35.0) * 0.35 + exp(-ds / 130.0) * 0.2) + float3(0.95, 0.97, 1.0) * ring;
    } else {
        // C: corona: a larger soft-edged disc, a warm corona fading out
        float disc = smoothstep(17.0, 13.0, ds);
        float corona = exp(-max(ds - 14.0, 0.0) / 14.0) * 0.6 + exp(-ds / 80.0) * 0.25;
        sun = mix(warm, float3(1.0, 0.98, 0.92), 0.75) * disc * 1.15 + warm * corona;
    }
    col += sun * sunVis * sunClear;

    // The moon: a sphere lit from where the sun is in its cycle, maria where the real ones are, faint earthshine
    float illum = 0.5 - 0.5 * cos(uPhase * 6.2831853);
    float2 m = (fc - uMoon) / uMoonR;
    float mr = length(m);
    if (mr < 1.0 && uMoonUp > 0.0) {
        float3 n = float3(m.x, m.y, sqrt(max(0.0, 1.0 - mr * mr)));
        float a = uPhase * 6.2831853;
        float3 L = float3(sin(a), 0.0, -cos(a));
        float lit = smoothstep(-0.04, 0.10, dot(n, L));
        float maria = mare(m, float2(-0.33, -0.36), 0.27) + mare(m, float2(-0.62, 0.02), 0.33)
            + mare(m, float2(0.17, -0.38), 0.16) + mare(m, float2(0.30, -0.06), 0.19)
            + mare(m, float2(0.68, -0.30), 0.10) + mare(m, float2(-0.20, 0.38), 0.17)
            + mare(m, float2(0.55, 0.16), 0.13) + mare(m, float2(0.0, -0.70), 0.10) * 0.6;
        maria = clamp(maria * (0.65 + 0.7 * fbm(m * 4.0 + 2.0)), 0.0, 1.0);
        float craters = fbm(m * 11.0) * 0.16;
        float tycho = exp(-dot(m - float2(-0.12, 0.74), m - float2(-0.12, 0.74)) / 0.004);
        float alb = 0.95 - maria * 0.38 - craters + tycho * 0.25;
        float3 surf = float3(0.94, 0.93, 0.89) * alb * (0.72 + 0.28 * n.z);
        float3 moonCol = surf * (lit * 1.05 + 0.03);
        col = mix(col, moonCol, smoothstep(1.0, 0.96, mr) * uMoonUp);
    }
    col += float3(0.7, 0.78, 0.92) * exp(-max(mr - 1.0, 0.0) * 2.2) * step(1.0, mr) * 0.16 * illum * uMoonUp;

    // Clouds: drifting with the wind, as many as the real cover; lit by the sun, the moon or lightning
    if (uCloud > 0.02) {
        float2 cp = float2(uv.x * aspect, uv.y) * float2(1.6, 3.2) + uWind * uTime * float2(aspect * 1.6, 3.2);
        float n1 = fbm(cp);
        float n2 = fbm(cp * 2.3 + 5.0);
        float th = mix(0.60, 0.16, uCloud);
        float dens = smoothstep(th, th + 0.22, n1 * 0.78 + n2 * 0.34);
        // White fair-weather clouds; a heavy deck is a moody grey, darker still when it rains
        float3 cDay = float3(0.96, 0.97, 0.99) * mix(1.0, 0.46, smoothstep(0.55, 1.0, uCloud))
            * mix(1.0, 0.72, min(uRain * 2.0, 1.0)) * mix(1.0, 0.55, uStorm);
        float3 cNight = float3(0.085, 0.095, 0.12);       // faintly moonlit: visible against the black sky
        float3 cc = mix(cNight, cDay, day);
        cc = mix(cc, float3(1.0, 0.63, 0.47), twi * 0.65);
        cc *= 0.72 + 0.28 * n2;
        // Moonlight through the clouds: bright through thin cloud, only a faint smudge through a heavy deck
        float moonGlow = exp(-length(fc - uMoon) / (uMoonR * 3.5)) * illum * uMoonUp;
        cc += float3(0.6, 0.65, 0.75) * moonGlow * mix(0.7, 0.16, smoothstep(0.75, 1.0, uCloud));
        // Near the sun: lit through, and bright silver-gold edges where the cloud is thin
        float nearSun = exp(-length(fc - uSun) / (uDp * 80.0)) * sunVis;
        cc += warm * nearSun * (0.35 + 1.1 * (1.0 - dens));
        float flash = uStorm * step(0.985, hash(float2(floor(uTime * 6.0), 3.0))) * (0.6 + 0.4 * hash(float2(floor(uTime * 6.0), 9.0)));
        cc += float3(0.8, 0.85, 1.0) * flash * (0.6 + n2);
        col = mix(col, cc, dens * mix(0.93, 1.0, smoothstep(0.8, 1.0, uCloud)));
    }

    // Rain: thin slanted streaks in two layers, as many as it's really raining
    if (uRain > 0.0) {
        for (int layer = 0; layer < 2; layer++) {
            float fl = float(layer);
            float2 rp = fc / uDp;
            rp.x += rp.y * (0.18 + uWind.x * 20.0);
            float colw = 5.0 + fl * 3.0;
            float cx = floor(rp.x / colw);
            float rnd = hash(float2(cx, 1.0 + fl));
            float len = 130.0 + 70.0 * rnd;
            float yy = rp.y - uTime * (520.0 + 260.0 * rnd + fl * 200.0) + rnd * 900.0;
            float cell = floor(yy / len);
            float s = fract(yy / len) * len;
            float on = step(hash(float2(cx, cell)), uRain * 0.55);
            float fx = abs(fract(rp.x / colw) - 0.5) * colw;
            float drop = smoothstep(16.0 + fl * 6.0, 0.0, s) * smoothstep(0.7, 0.0, fx) * on;
            col = mix(col, float3(0.78, 0.84, 0.9), drop * (0.28 + fl * 0.12));
        }
    }

    // Snow: soft flakes falling and swaying
    if (uSnow > 0.0) {
        for (int layer = 0; layer < 2; layer++) {
            float fl = float(layer);
            float cellSz = 26.0 - fl * 8.0;
            float2 sp = fc / uDp;
            sp.y -= uTime * (22.0 + fl * 14.0);
            sp.x += sin(sp.y * 0.05 + fl) * 6.0 + uWind.x * uTime * 300.0;
            float2 sid = floor(sp / cellSz);
            float2 sf = fract(sp / cellSz) - 0.5;
            float2 so = float2(hash(sid + fl), hash(sid + 4.0 + fl)) - 0.5;
            float present = step(hash(sid + 8.0), uSnow * 0.7);
            float d = length(sf - so * 0.6) * cellSz;
            col = mix(col, float3(0.95), smoothstep(1.8 - fl * 0.6, 0.0, d) * present * 0.85);
        }
    }

    // Fog: a haze over the whole sky, drifting slowly, thickest toward the horizon
    if (uFog > 0.0) {
        float drift = fbm(float2(uv.x * aspect * 1.2, uv.y * 2.0) + float2(uTime * 0.012, 0.0));
        float3 fogCol = mix(float3(0.07, 0.075, 0.085), float3(0.50, 0.53, 0.56), day) * (0.9 + 0.2 * drift);
        col = mix(col, fogCol, uFog * clamp(0.62 + 0.3 * uv.y + 0.15 * (drift - 0.5), 0.0, 0.95));
    }
    return half4(clamp(col, 0.0, 1.0), 1.0);
}
"""

/**
 * A preview of any sky, for checking the look on a phone over ADB without waiting for the weather:
 * `adb shell am start -n <app>/com.example.MainActivity --es sky_preview "hour=21.5,phase=0.3,cloud=60,code=61"`
 * (hour: local time today; phase 0..1; cloud %; rain mm; snow cm; code WMO; wind mph; dir degrees). "off" clears it.
 */
object SkyPreview {
    data class Override(
        val at: Long? = null, val phase: Float? = null, val cloud: Int? = null, val rain: Double? = null,
        val snow: Double? = null, val code: Int? = null, val wind: Double? = null, val windDir: Int? = null,
        val sun: Int? = null
    )

    var current by mutableStateOf<Override?>(null)
        private set

    fun apply(spec: String?) {
        spec ?: return
        if (spec.isBlank() || spec == "off") { current = null; return }
        val kv = spec.split(",").mapNotNull { p -> p.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
        val at = kv["hour"]?.toDoubleOrNull()?.let { hr ->
            Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
                .timeInMillis / 1000 + (hr * 3600).toLong()
        }
        current = Override(at, kv["phase"]?.toFloatOrNull(), kv["cloud"]?.toIntOrNull(), kv["rain"]?.toDoubleOrNull(),
            kv["snow"]?.toDoubleOrNull(), kv["code"]?.toIntOrNull(), kv["wind"]?.toDoubleOrNull(), kv["dir"]?.toIntOrNull(),
            kv["sun"]?.toIntOrNull())
    }
}

/**
 * The header's text over the sky (Devin, 2026-10-29: "some kind of contrast or dynamic changing color"): tinted with
 * the light (a cool moonlit white at night, warm cream at golden hour, white by day), and its shadow deeper where the
 * sky behind is bright or the sun is passing behind the date row.
 */
data class SkyInk(val color: Color, val shadowAlpha: Float, val shadowBlur: Float)

fun skyInk(s: SkyState): SkyInk {
    val day = ((s.altitude + 0.05f) / 0.35f).coerceIn(0f, 1f)
    val golden = if (s.altitude > -0.12f) (1f - abs(s.altitude - 0.06f) / 0.22f).coerceIn(0f, 1f) else 0f
    val color = lerp(lerp(Color(0xFFE6EDFF), Color.White, day), Color(0xFFFFE6C4), golden * 0.75f)
    val dy = s.sunY - 0.48f
    val sunBehind = if (s.altitude > -0.1f) kotlin.math.exp(-(dy * dy) / 0.006f) else 0f
    val strength = (0.3f + 0.35f * day * (1f - s.cloud * 0.3f) + 0.5f * sunBehind).coerceIn(0f, 1f)
    return SkyInk(color, strength, 6f + 20f * strength)
}

/** [skyInk] for the weather now, re-read every 30 s (and at once for a preview). */
@Composable
fun rememberSkyInk(weather: TodayWeather?): SkyInk {
    val preview = SkyPreview.current
    val ink by androidx.compose.runtime.produceState(skyInk(skyState(weather, System.currentTimeMillis() / 1000, preview)), weather, preview) {
        while (true) {
            value = skyInk(skyState(weather, System.currentTimeMillis() / 1000, preview))
            kotlinx.coroutines.delay(30_000)
        }
    }
    return ink
}
