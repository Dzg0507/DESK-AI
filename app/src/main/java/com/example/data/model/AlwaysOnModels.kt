package com.example.data.model

data class DaemonStats(
    val daemonStatus: String = "idle",
    val overallPhase: String = "idle",
    val defaultEngine: String = "auto",
    val lastHeartbeat: String = "",
    val tasksCompleted: Int = 0,
    val tasksInProgress: Int = 0,
    val tasksBacklog: Int = 0,
    val tasksFailed: Int = 0,
    val totalHeartbeats: Int = 0,
    val cpuPercent: Double = 0.0,
    val ramPercent: Double = 0.0,
    val tasks: List<AgentTaskItem> = emptyList(),
    val activeTask: AgentTaskItem? = null
)

data class TaskProgress(
    val stage: String = "",       // narration -> capture -> assemble -> send
    val label: String = "",       // e.g. "Capturing frames"
    val percent: Int = 0,         // 0..99
    val detail: String = "",      // e.g. "Frame 480 of 1078"
    val etaSeconds: Int = 0,      // time left as of updatedAt
    val updatedAt: String = ""
)

data class TaskResult(
    val type: String = "",        // "video"
    val filename: String = "",
    val url: String = ""          // resolved against the server URL
)

/** A draft in the agent's canvas (the chat reply's `canvas`): kind is document or page. */
data class CanvasRef(
    val id: Long,
    val title: String,
    val kind: String,
    val version: Int
)

/** A message the agent started itself (GET /api/inbox): kind is brief, notice, suggestion, question or review.
 *  canvasJson: a draft card ({"id","title","kind","version"}), e.g. the Sunday growth review. */
data class InboxMessage(
    val id: Long,
    val kind: String,
    val text: String,
    val createdAt: String,
    val canvasJson: String? = null
)

data class TaskAction(
    val label: String = "",
    val action: String = "",      // "stream", "post", "chat", "cancel", "retry"
    val url: String? = null,
    val command: String? = null,
    val variant: String = "secondary" // "primary", "secondary", "danger"
)

data class AgentTaskItem(
    val id: String,
    val title: String,
    val prompt: String = "",
    val phase: String, // backlog, in_progress, completed, failed
    val engine: String = "auto",
    val priority: String = "medium",
    val startedAt: String? = null,
    val completedAt: String? = null,
    val outputSummary: String? = null,
    val lastError: String? = null,
    val workerPid: Int? = null,
    val statusText: String? = null,
    val progress: TaskProgress? = null,
    val cancelled: Boolean = false,
    val result: TaskResult? = null,
    val actions: List<TaskAction> = emptyList()
)

data class MemoryFactItem(
    val id: Int,
    val content: String,
    val category: String = "fact",
    val importance: Int = 5,
    val pinned: Boolean = false,
    val updatedAt: String = ""
)

data class MemoryOverview(
    val profile: String = "",
    val factsActive: Int = 0,
    val factsPinned: Int = 0,
    val messagesCount: Int = 0,
    val summariesCount: Int = 0,
    val facts: List<MemoryFactItem> = emptyList()
)

data class VideoItem(
    val filename: String,
    val sizeMb: Double,
    val createdAt: String,
    val url: String
)

data class ImageItem(
    val filename: String,
    val sizeMb: Double,
    val createdAt: String,
    val url: String
)

data class TaskProposal(
    val id: String = "",
    val token: String = "",
    val instruction: String,
    val reason: String = "",
    val project: String? = null,
    val expired: Boolean = false,
    val isLocal: Boolean = false,
    val state: String = "pending",
    val kind: String = "task" // "task" or "add_project"
)

data class ChatQuestion(
    val id: String = "",
    val text: String = "",
    val options: List<String> = emptyList(),
    val allowOther: Boolean = true,
    val answeredOption: String? = null
)

data class AgentWorkProject(
    val name: String,
    val description: String = "",
    val repo: String = ""
)

data class SystemLogEntry(
    val id: String = "",
    val timestamp: String = "",
    val level: String = "INFO",
    val message: String = ""
)

data class PushRegistrationResult(
    val status: String = "ok",
    val pushReady: Boolean = false,
    val message: String = ""
)

/** A schedule on the agent: a video, a phone reminder or a background task at set times (GET /api/schedules). */
data class AgentSchedule(
    val id: Int,
    val name: String,
    val kind: String,               // video | reminder | task
    val cron: String,
    val description: String,        // plain English, e.g. "weekdays at 08:30"
    val enabled: Boolean,
    val nextRunAt: String?,
    val lastRunAt: String?,
    val lastResult: String?,
    val runs: Int,
    val postToTiktok: Boolean,
    val text: String?,
    val instruction: String?,
    val project: String?
)

data class ScheduleList(val schedules: List<AgentSchedule>, val timezone: String)

data class SchedulePreview(val valid: Boolean, val description: String, val nextRuns: List<String>, val error: String?)
