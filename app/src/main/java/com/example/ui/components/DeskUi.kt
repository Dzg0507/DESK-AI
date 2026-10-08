package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardGradient
import com.example.ui.theme.DeskRadius
import com.example.ui.theme.DeskShapes
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RoseError
import com.example.ui.theme.SheetBorderGradient
import com.example.ui.theme.SheetGradient
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate800
import java.text.SimpleDateFormat
import java.util.Locale

/*
 * Shared building blocks for DeskAI's sheets and cards, so every screen has the same surfaces, headers, pills and
 * empty states. All are cheap (no state, no effects) and safe inside LazyColumn items.
 */

// ---------------------------------------------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------------------------------------------

/** The look of every sheet/dialog body: rounded, slate→navy gradient, hairline lit border. */
fun Modifier.deskSheet(): Modifier = this
    .clip(DeskShapes.sheet)
    .background(SheetGradient)
    .border(1.dp, SheetBorderGradient, DeskShapes.sheet)

/** A standard card: rounded 12, slate gradient fill, slate-700 hairline (or [borderColor]). */
fun Modifier.deskCard(borderColor: Color = CardBorder, radius: Dp = DeskRadius.card): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(CardGradient)
        .border(1.dp, borderColor, shape)
}

/** A tinted panel (banners, error boxes): [color] at low alpha with a matching border. */
fun Modifier.deskTinted(color: Color, radius: Dp = DeskRadius.card, fillAlpha: Float = 0.10f): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(color.copy(alpha = fillAlpha))
        .border(1.dp, color.copy(alpha = 0.40f), shape)
}

/**
 * A card with an optional colored accent bar down its left edge (the phase / kind of the item).
 * [onClick] makes the whole card tappable.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    borderColor: Color = CardBorder,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .deskCard(borderColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .height(IntrinsicSize.Min)
    ) {
        if (accent != null) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.35f))))
            )
        }
        Column(modifier = Modifier.weight(1f).padding(contentPadding), content = content)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Headers & labels
// ---------------------------------------------------------------------------------------------------------------

/**
 * The header every sheet shares: the icon in a softly glowing tinted circle, a title, an optional subtitle,
 * optional action icons, and a close button.
 */
@Composable
fun SheetHeader(
    title: String,
    icon: ImageVector? = null,
    accent: Color = ElectricCyan,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.32f), accent.copy(alpha = 0.08f))))
                    .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Slate400,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        if (onClose != null) {
            Spacer(modifier = Modifier.width(2.dp))
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(Slate800),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Slate400, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** A small uppercase section label with an optional count bubble. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, count: Int? = null, color: Color = Slate400) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 1.sp
        )
        if (count != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(DeskShapes.pill)
                    .background(Slate800)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(count.toString(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate400)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Pills & chips
// ---------------------------------------------------------------------------------------------------------------

/** A rounded status pill: a colored dot and an uppercase label on a soft tint of the same color. */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier, showDot: Boolean = true) {
    Row(
        modifier = modifier
            .clip(DeskShapes.pill)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), DeskShapes.pill)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showDot) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            text = text.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.5.sp,
            maxLines = 1
        )
    }
}

/** A quiet tag chip (source, engine, kind): optional icon, colored text on a faint tint. Tappable if [onClick]. */
@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Slate400,
    icon: ImageVector? = null,
    active: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .clip(DeskShapes.chip)
            .background(color.copy(alpha = if (active) 0.24f else 0.10f))
            .border(1.dp, color.copy(alpha = if (active) 0.55f else 0.25f), DeskShapes.chip)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Empty & loading states
// ---------------------------------------------------------------------------------------------------------------

/** A centered empty state: a soft icon disc, a title and a one-line hint. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    accent: Color = Slate500
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.04f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Slate100, textAlign = TextAlign.Center)
        if (!message.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(message, fontSize = 12.sp, color = Slate500, textAlign = TextAlign.Center, lineHeight = 17.sp)
        }
    }
}

/** A centered spinner with a quiet label. */
@Composable
fun LoadingState(label: String, modifier: Modifier = Modifier, color: Color = ElectricCyan) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = color, strokeWidth = 2.dp)
        Spacer(modifier = Modifier.height(10.dp))
        Text(label, fontSize = 12.sp, color = Slate400)
    }
}

/** A one-line note under an action ("Copied", "Queued…"), colored by tone. */
@Composable
fun StatusNote(text: String, modifier: Modifier = Modifier, color: Color = ElectricCyan) {
    Row(
        modifier = modifier
            .clip(DeskShapes.control)
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 11.sp, color = color, lineHeight = 15.sp)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Task helpers (pure)
// ---------------------------------------------------------------------------------------------------------------

/** The color for a task phase: completed emerald, running amber, failed rose, cancelled slate, queued cyan. */
fun phaseColor(phase: String, cancelled: Boolean = false): Color = when {
    cancelled || phase == "cancelled" -> Slate400
    phase == "completed" -> EmeraldConnected
    phase == "in_progress" -> AmberPending
    phase == "failed" -> RoseError
    else -> ElectricCyan
}

/** A human label for a phase ("in_progress" → "Running"). */
fun phaseLabel(phase: String, cancelled: Boolean = false): String = when {
    cancelled || phase == "cancelled" -> "Cancelled"
    phase == "completed" -> "Done"
    phase == "in_progress" -> "Running"
    phase == "failed" -> "Failed"
    phase == "backlog" -> "Queued"
    else -> phase.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/**
 * "just now", "5m ago", "3h ago", "yesterday", "4d ago", or "Oct 7". Reads the first 19 characters of an ISO stamp
 * ("2026-10-07T20:00:00…") in the device's zone, like TaskDetailsSheet (java.time needs API 26; the app supports 24).
 * Returns null when the stamp can't be read.
 */
fun relativeTime(iso: String?, nowMillis: Long = System.currentTimeMillis()): String? {
    val s = iso?.trim().orEmpty()
    if (s.length < 19) return null
    val date = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(s.substring(0, 19))
    } catch (_: Exception) {
        null
    } ?: return null
    val secs = (nowMillis - date.time) / 1000
    return when {
        secs < -60 -> SimpleDateFormat("MMM d, h:mm a", Locale.US).format(date)
        secs < 45 -> "just now"
        secs < 3600 -> "${(secs / 60).coerceAtLeast(1)}m ago"
        secs < 86_400 -> "${secs / 3600}h ago"
        secs < 172_800 -> "yesterday"
        secs < 7 * 86_400 -> "${secs / 86_400}d ago"
        else -> SimpleDateFormat("MMM d", Locale.US).format(date)
    }
}

/** A short friendly name for where a task came from ("schedule #1 'Weekly'" → "Schedule #1"). */
fun shortSource(source: String?): String? {
    val s = source?.trim().orEmpty()
    if (s.isEmpty() || s == "unknown") return null
    return when {
        s.startsWith("schedule #") -> "Schedule #" + s.removePrefix("schedule #").takeWhile { it.isDigit() }
        s.length > 22 -> s.take(21) + "…"
        else -> s
    }
}

/** Monospace style helper for ids and numbers. */
val MonoFamily: FontFamily = FontFamily.Monospace
