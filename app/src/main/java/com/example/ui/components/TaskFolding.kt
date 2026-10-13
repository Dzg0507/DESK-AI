package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AgentTaskItem
import com.example.data.model.ChatMessage
import com.example.ui.theme.Cerulean
import com.example.ui.theme.CeruleanText
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.TaskIndigo
import com.example.ui.theme.Violet300

/*
 * Tasks folded in chat (2026-10-11, the owner: "the chat is basically nothing but tasks"). A finished task is one
 * slim row; two or more finished ones in a row fold into a "N tasks finished" strip; a running task keeps its full
 * live card; the Tasks toggle in the top bar hides task messages altogether. Tap a row for the task's details.
 */

/** One line of the chat as shown: a normal message, a finished task's row, or a folded run of them. */
sealed class ChatEntry(val key: String) {
    class Message(val message: ChatMessage) : ChatEntry(message.id)
    class TaskRow(val message: ChatMessage, val task: AgentTaskItem) : ChatEntry("row-" + message.id)
    class Strip(val groupKey: String, val tasks: List<AgentTaskItem>, val expanded: Boolean) : ChatEntry("strip-$groupKey")
}

fun isTaskMessage(m: ChatMessage): Boolean = m.role == "assistant" && !m.linkedTaskId.isNullOrBlank()

fun isFinished(t: AgentTaskItem): Boolean = t.cancelled || t.phase == "completed" || t.phase == "failed"

/**
 * A finished task still waiting for the owner's tap: its card carries a primary endpoint chip (⚡ Apply update,
 * 🚀 Push live, 🔁 Try again). Folding it into a row hid that chip (2026-10-13: "apply task-189" twice, the card
 * came folded, a tap opened the task's details, which have no chips; nothing could be applied from the app).
 */
fun awaitsTap(t: AgentTaskItem): Boolean = t.actions.any { it.action == "post" && it.variant == "primary" }

/**
 * The chat as shown. [tasks]: what's known of each linked task (fetched by the screen). A task whose state isn't
 * known yet, or that's still running, shows as its full message (live card), and so does the newest message about
 * a finished task that awaits a tap (awaitsTap), even with tasks hidden.
 */
fun foldChat(messages: List<ChatMessage>, tasks: Map<String, AgentTaskItem>, hideTasks: Boolean,
             expanded: Set<String>): List<ChatEntry> {
    val out = ArrayList<ChatEntry>()
    // the newest message linking each task: the one that keeps a waiting task's chips
    val newest = HashMap<String, String>()
    messages.forEach { m -> if (isTaskMessage(m)) newest[m.linkedTaskId!!] = m.id }
    val keepsCard = { r: ChatMessage ->
        val t = tasks[r.linkedTaskId]
        t != null && isFinished(t) && awaitsTap(t) && newest[r.linkedTaskId] == r.id
    }
    var i = 0
    while (i < messages.size) {
        val m = messages[i]
        if (!isTaskMessage(m)) { out.add(ChatEntry.Message(m)); i++; continue }
        // a run of task messages in a row
        var j = i
        while (j < messages.size && isTaskMessage(messages[j])) j++
        val run = messages.subList(i, j)
        if (hideTasks) {
            run.filter(keepsCard).forEach { r -> out.add(ChatEntry.Message(r)) }
        } else {
            val done = run.filter { r -> tasks[r.linkedTaskId]?.let(::isFinished) == true && !keepsCard(r) }
            val key = done.firstOrNull()?.id ?: ""
            var stripPlaced = false
            run.forEach { r ->
                val t = tasks[r.linkedTaskId]
                when {
                    t == null || !isFinished(t) -> out.add(ChatEntry.Message(r))        // running or unknown: live card
                    keepsCard(r) -> out.add(ChatEntry.Message(r))                        // waiting for a tap: card + chips
                    done.size >= 2 -> {
                        if (!stripPlaced) {
                            out.add(ChatEntry.Strip(key, done.mapNotNull { d -> tasks[d.linkedTaskId] }, key in expanded))
                            stripPlaced = true
                        }
                        if (key in expanded) out.add(ChatEntry.TaskRow(r, t))
                    }
                    else -> out.add(ChatEntry.TaskRow(r, t))
                }
            }
        }
        i = j
    }
    return out
}

private fun isRetask(t: AgentTaskItem) = (t.outputSummary ?: "").contains("Repeated an earlier run") ||
        (t.source ?: "").contains("/retask")

/** "11:45" from the server's ISO time. */
private fun clock(iso: String?): String = iso?.takeIf { it.length >= 16 }?.substring(11, 16) ?: ""

private val TaskBorder = Brush.linearGradient(listOf(Cerulean.copy(alpha = 0.85f), TaskIndigo.copy(alpha = 0.85f)))
private val TaskFill = Brush.horizontalGradient(listOf(Cerulean.copy(alpha = 0.14f), TaskIndigo.copy(alpha = 0.20f)))

/** A finished task in one line: what happened, its title, when. */
@Composable
fun TaskRow(task: AgentTaskItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val failed = task.phase == "failed" && !task.cancelled
    val (mark, color) = when {
        task.cancelled -> "⊘" to Slate400
        failed -> "✕" to RoseError
        isRetask(task) -> "↻" to Violet300
        else -> "✓" to CeruleanText
    }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .clip(shape)
            .background(TaskFill)
            .border(1.dp, if (failed) Brush.linearGradient(listOf(RoseError.copy(alpha = .7f), TaskIndigo.copy(alpha = .6f))) else TaskBorder, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Text(mark, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Text(
            task.title.removePrefix("Computer: "),
            color = Slate200, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            (if (task.cancelled) "cancelled " else if (failed) "failed " else "") + clock(task.completedAt),
            color = if (failed) RoseError else Slate500, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace
        )
    }
}

/** A folded run of finished tasks: "3 tasks finished · 1 failed", tap to show or hide them. */
@Composable
fun TaskStrip(tasks: List<AgentTaskItem>, expanded: Boolean, onToggle: () -> Unit) {
    val failed = tasks.count { it.phase == "failed" && !it.cancelled }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(shape)
            .background(TaskFill)
            .border(1.dp, TaskBorder, shape)
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${tasks.size} tasks finished", color = Slate200, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        if (failed > 0) Text(" · $failed failed", color = RoseError, fontSize = 12.5.sp)
        Spacer(Modifier.weight(1f))
        Text(if (expanded) "Hide ▴" else "Show ▾", color = CeruleanText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The top bar's Tasks switch: lit when tasks show in chat. */
@Composable
fun TasksToggle(showing: Boolean, onToggle: () -> Unit) {
    val c = if (showing) CeruleanText else Slate500
    Row(
        modifier = Modifier
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = 0.12f))
            .border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(50))
            .clickable { onToggle() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(width = 20.dp, height = 11.dp).clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.35f)),
            contentAlignment = if (showing) Alignment.CenterEnd else Alignment.CenterStart
        ) { Box(Modifier.padding(1.dp).size(9.dp).clip(RoundedCornerShape(50)).background(c)) }
        Spacer(Modifier.width(6.dp))
        Text("Tasks", color = c, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

