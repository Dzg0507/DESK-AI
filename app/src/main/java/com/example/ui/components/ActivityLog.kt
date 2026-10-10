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

/** The color and mark for a line's level: errors red ✖, warnings amber ⚠, the finish green ✓. */
fun activityColor(level: String): Color = when (level) {
    "error" -> RoseError
    "warn" -> AmberPending
    "ok" -> EmeraldConnected
    else -> Slate200
}

private fun mark(level: String): String = when (level) {
    "error" -> "✖ "
    "warn" -> "⚠ "
    "ok" -> "✓ "
    else -> ""
}

/** One line of what a computer task did: the time, then the plain sentence ("Opening Notepad"). */
@Composable
fun ActivityLogLine(line: ActivityLine, fontSize: Int = 12, showTime: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (showTime && line.time.isNotBlank()) {
            Text(line.time, fontSize = (fontSize - 2).sp, color = Slate500, fontFamily = FontFamily.Monospace,
                 modifier = Modifier.padding(top = 1.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(mark(line.level) + line.text, fontSize = fontSize.sp, color = activityColor(line.level),
             lineHeight = (fontSize + 4).sp)
    }
}

/** A whole log, oldest first (the task card's "What it did"). */
@Composable
fun ActivityLogList(lines: List<ActivityLine>, fontSize: Int = 12) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        lines.forEach { ActivityLogLine(it, fontSize) }
    }
}

/** The log as plain text, for Copy. */
fun activityLogText(lines: List<ActivityLine>): String =
    lines.joinToString("\n") { l -> listOf(l.time, mark(l.level) + l.text).filter { it.isNotBlank() }.joinToString("  ") }
