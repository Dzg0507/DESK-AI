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
    val workerPid: Int? = null
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

data class TaskProposal(
    val id: String = "",
    val token: String = "",
    val instruction: String,
    val reason: String = "",
    val project: String? = null,
    val expired: Boolean = false,
    val isLocal: Boolean = false,
    val state: String = "pending"
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
