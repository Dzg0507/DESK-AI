package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActivityLine
import com.example.ui.theme.AmberPending
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate500
import com.example.ui.theme.Violet300

/** The color and mark for a line's level: errors red ✖, warnings amber ⚠, the finish green ✓, and a retask's own
 *  lines violet ↻ (saved as a retask, repeating one, each repeated step; the owner's ask, 2026-10-11). */
/** An info line's color by what kind of step it is (the owner, 2026-10-11: "basically all white text"). */
fun kindColor(kind: String): Color = when (kind) {
    "open" -> com.example.ui.theme.CeruleanText
    "type" -> Color(0xFF9DFF3A)          // lime
    "save" -> Color(0xFF2EE6B8)          // teal
    "plan" -> Slate500                    // housekeeping, dimmed
    else -> Slate200                      // clicking and keys, and anything else
}

private fun kindMark(kind: String): String = when (kind) {
    "open" -> "↗ "
    "type" -> "⌨ "
    "save" -> "⤓ "
    else -> ""
}

fun activityColor(level: String, kind: String = ""): Color = if (level == "info") kindColor(kind) else when (level) {
    "error" -> RoseError
    "warn" -> AmberPending
    "ok" -> EmeraldConnected
    "retask" -> Violet300
    else -> Slate200
}

private fun mark(level: String): String = when (level) {
    "error" -> "✖ "
    "warn" -> "⚠ "
    "ok" -> "✓ "
    "retask" -> "↻ "
    else -> ""
}

/** One line of what a computer task did: the time, then the plain sentence ("Opening Notepad"). */
@Composable
fun ActivityLogLine(line: ActivityLine, fontSize: Int = 12, showTime: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (showTime && line.time.isNotBlank()) {
            Text(line.time, fontSize = (fontSize - 2).sp, color = Slate500, fontFamily = com.example.ui.theme.JetBrainsMono,
                 modifier = Modifier.padding(top = 1.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text((if (line.level == "info") kindMark(line.kind) else mark(line.level)) + line.text, fontSize = fontSize.sp,
             color = activityColor(line.level, line.kind),
             lineHeight = (fontSize + 4).sp)
    }
}

/** A whole log, oldest first (the task card's "What it did"), with a legend of the colors it uses. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun ActivityLogList(lines: List<ActivityLine>, fontSize: Int = 12) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        lines.forEach { ActivityLogLine(it, fontSize) }
        val kinds = lines.filter { it.level == "info" }.map { it.kind }.toSet()
        val legend = listOfNotNull(
            ("opening" to kindColor("open")).takeIf { "open" in kinds },
            ("typing" to kindColor("type")).takeIf { "type" in kinds },
            ("clicking" to kindColor("click")).takeIf { "click" in kinds },
            ("saving" to kindColor("save")).takeIf { "save" in kinds },
            ("retask" to activityColor("retask")).takeIf { lines.any { it.level == "retask" } },
            ("warning" to activityColor("warn")).takeIf { lines.any { it.level == "warn" } },
            ("housekeeping" to kindColor("plan")).takeIf { "plan" in kinds }
        )
        if (legend.size > 1) {
            Spacer(Modifier.width(0.dp))
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                legend.forEach { (label, color) ->
                    Text("● $label", fontSize = (fontSize - 1).sp, color = color.copy(alpha = 0.9f))
                }
            }
        }
    }
}

/** The log as plain text, for Copy. */
fun activityLogText(lines: List<ActivityLine>): String =
    lines.joinToString("\n") { l -> listOf(l.time, mark(l.level) + l.text).filter { it.isNotBlank() }.joinToString("  ") }
