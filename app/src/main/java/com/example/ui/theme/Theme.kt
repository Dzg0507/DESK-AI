package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF032640),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Sky100,
    secondary = NeonIndigo,
    onSecondary = Color.White,
    secondaryContainer = Indigo900,
    onSecondaryContainer = Indigo100,
    tertiary = NeonPurple,
    onTertiary = Color.White,
    background = SlateDarkBackground,
    onBackground = TextPrimary,
    surface = SlateDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateDarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = SlateDarkBorder,
    outlineVariant = Slate800,
    surfaceContainerLowest = DeepNavy,
    surfaceContainerLow = SlateDarkSurface,
    surfaceContainer = SlateDarkSurface,
    surfaceContainerHigh = Slate800,
    surfaceContainerHighest = Slate700,
    inverseSurface = Slate100,
    inverseOnSurface = Slate900,
    error = RoseError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricCyanGlow,
    onPrimary = Color.White,
    primaryContainer = Sky100,
    onPrimaryContainer = Sky700,
    secondary = Indigo600,
    onSecondary = Color.White,
    secondaryContainer = Indigo50,
    onSecondaryContainer = Indigo700,
    tertiary = Purple600,
    background = SlateLightBackground,
    onBackground = TextPrimaryLight,
    surface = SlateLightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = SlateLightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = SlateLightBorder,
    error = RoseError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to sleek tech dark theme by default, while supporting dynamic
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = DeskMaterialShapes,
        content = content
    )
}
