package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.R

/*
 * DeskAI type (2026-10-27, docs/FEED_AND_DESIGN_PLAN_2026-10-27.md): Inter for everything you read, Newsreader for
 * Today's headlines, JetBrains Mono for ids, times and numbers. Static TTFs bundled in res/font (licenses in
 * assets/licenses): downloadable fonts fall back to Roboto offline and in the screenshot tests, and variable-font
 * weights need API 26 while the app supports 24. Bold asks get the 600 file, so nothing is faux-bolded.
 *
 * Sizes come from DeskTextSize only (eight steps); a test fails on a raw `fontSize = 13.sp` in a migrated file.
 * Line heights are in em, so a Text that sets only a size still gets a line height that fits it.
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)
val Newsreader = FontFamily(
    Font(R.font.newsreader_medium, FontWeight.Medium),
    Font(R.font.newsreader_semibold, FontWeight.SemiBold),
)
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

/** The only text sizes. Was 17 sizes from 9 to 32 sp, mostly 10-12 (cramped). */
object DeskTextSize {
    val xs = 11.sp        // captions, pills, timestamps
    val s = 12.sp         // labels, chips, metadata
    val m = 13.sp         // secondary body, list details
    val base = 14.sp      // body in sheets and cards
    val l = 16.sp         // chat messages, prominent body
    val xl = 18.sp        // card and section titles
    val xxl = 22.sp       // sheet titles, Today's headlines
    val display = 28.sp   // the edition date, big numbers
}

/** Numbers line up in columns (stats, times, counts): tabular figures. */
private const val TABULAR = "tnum"

/** Ready-made styles for new code; Material's slots below map onto the same scale. */
object DeskType {
    val display = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.SemiBold, fontSize = DeskTextSize.display,
        lineHeight = 1.15.em, letterSpacing = (-0.02).em)
    val headline = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = DeskTextSize.xxl,
        lineHeight = 1.2.em, letterSpacing = (-0.02).em)
    val title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = DeskTextSize.xl,
        lineHeight = 1.3.em, letterSpacing = (-0.01).em)
    val titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = DeskTextSize.l,
        lineHeight = 1.35.em)
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = DeskTextSize.base,
        lineHeight = 1.45.em)
    val bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = DeskTextSize.m,
        lineHeight = 1.4.em)
    val label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = DeskTextSize.s,
        lineHeight = 1.35.em, fontFeatureSettings = TABULAR)
    val caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = DeskTextSize.xs,
        lineHeight = 1.3.em, fontFeatureSettings = TABULAR)
    val overline = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = DeskTextSize.xs,
        lineHeight = 1.3.em, letterSpacing = 0.08.em)
    val mono = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = DeskTextSize.s,
        lineHeight = 1.4.em, fontFeatureSettings = TABULAR)
    val monoSmall = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = DeskTextSize.xs,
        lineHeight = 1.35.em, fontFeatureSettings = TABULAR)
}

val Typography =
  Typography(
    displaySmall = DeskType.display,
    headlineSmall = DeskType.headline,
    titleLarge = DeskType.title,
    titleMedium = DeskType.titleSmall,
    titleSmall = DeskType.body.copy(fontWeight = FontWeight.SemiBold),
    // bodyLarge is every Text's default style (LocalTextStyle): Inter, no tracking, an em line height
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = DeskTextSize.l,
        lineHeight = 1.45.em),
    bodyMedium = DeskType.body,
    bodySmall = DeskType.bodySmall,
    labelLarge = DeskType.body.copy(fontWeight = FontWeight.Medium),
    labelMedium = DeskType.label,
    labelSmall = DeskType.caption,
  )
