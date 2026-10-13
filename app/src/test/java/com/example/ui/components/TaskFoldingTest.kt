package com.example.ui.components

import com.example.data.model.AgentTaskItem
import com.example.data.model.ChatMessage
import com.example.data.model.TaskAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-10-13: the go-live card for task-189 was folded into a row, so its ⚡ Apply update chip was unreachable. */
class TaskFoldingTest {
    private fun msg(id: String, task: String?) =
        ChatMessage(id = id, sessionId = "s", role = "assistant", content = id, linkedTaskId = task)

    private val apply = TaskAction("⚡ Apply update", "post", "/api/tasks/task-189/apply", null, "primary")
    private val preview = TaskAction("🔍 Preview diff", "chat", null, "Show diff for task-189", "secondary")
    private val waiting = AgentTaskItem(id = "task-189", title = "research worker", phase = "completed",
                                        actions = listOf(preview, apply))
    private val done = AgentTaskItem(id = "task-188", title = "pins", phase = "completed")

    @Test
    fun theNewestMessageAboutATaskWaitingForATapKeepsItsCard() {
        val messages = listOf(msg("a", "task-189"), msg("b", "task-189"), msg("c", "task-188"))
        val tasks = mapOf("task-189" to waiting, "task-188" to done)
        val shown = foldChat(messages, tasks, hideTasks = false, expanded = emptySet())
        // the older mention and the plain finished task fold (two: a "finished" strip); the newest mention of the
        // waiting task shows its full card, chips and all
        assertEquals(listOf("strip-a", "b"), shown.map { it.key })
        assertTrue(shown[1] is ChatEntry.Message)
    }

    @Test
    fun itStaysVisibleWithTasksHidden() {
        val messages = listOf(msg("a", "task-189"), msg("c", "task-188"))
        val shown = foldChat(messages, mapOf("task-189" to waiting, "task-188" to done), hideTasks = true,
                             expanded = emptySet())
        assertEquals(listOf("a"), shown.map { it.key })
    }

    @Test
    fun onceLiveItFoldsLikeAnyOther() {
        val live = waiting.copy(actions = listOf(TaskAction("✅ Live", "none", null, null, "secondary")))
        val shown = foldChat(listOf(msg("a", "task-189")), mapOf("task-189" to live), hideTasks = false,
                             expanded = emptySet())
        assertEquals(listOf("row-a"), shown.map { it.key })
    }
}
