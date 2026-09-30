package com.example.data.repository

import android.content.Context

import com.example.data.local.AppDatabase
import com.example.data.model.AgentTaskItem
import com.example.data.model.AgentWorkProject
import com.example.data.model.BridgeConfig
import com.example.data.model.ChatAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.DaemonStats
import com.example.data.model.MemoryOverview
import com.example.data.model.PushRegistrationResult
import com.example.data.model.SystemLogEntry
import com.example.data.model.TaskProposal
import com.example.data.model.VideoItem
import com.example.data.model.ImageItem
import com.example.data.remote.AlwaysOnAgentClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatRepository(
    private val context: Context? = null,
    private val database: AppDatabase,
    private val agentClient: AlwaysOnAgentClient = AlwaysOnAgentClient()
) {
    private val chatDao = database.chatDao()
    private val configDao = database.bridgeConfigDao()

    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()

    val bridgeConfig: Flow<BridgeConfig> = configDao.getConfig().map { config ->
        val prefs = context?.getSharedPreferences("desk_ai_credentials", Context.MODE_PRIVATE)
        val savedKey = prefs?.getString("api_key", null)
        val savedServer = prefs?.getString("server_url", null)
        val savedRemote = prefs?.getString("remote_url", null)

        if (config == null) {
            BridgeConfig(
                serverUrl = savedServer ?: "http://192.168.12.151:8080",
                remoteUrl = savedRemote ?: "http://100.109.85.92:8080",
                apiKey = savedKey ?: ""
            )
        } else if (config.apiKey.isBlank() && !savedKey.isNullOrBlank()) {
            config.copy(
                apiKey = savedKey,
                serverUrl = if ((config.serverUrl.isBlank() || config.serverUrl == "http://192.168.12.153:8080") && !savedServer.isNullOrBlank()) savedServer else config.serverUrl,
                remoteUrl = if (config.remoteUrl.isBlank() && !savedRemote.isNullOrBlank()) savedRemote else config.remoteUrl
            )
        } else {
            if (config.serverUrl == "http://192.168.12.153:8080") config.copy(serverUrl = "http://192.168.12.151:8080") else config
        }
    }

    suspend fun getActiveConfig(): BridgeConfig = withContext(Dispatchers.IO) {
        val inDb = configDao.getConfigSync()
        val prefs = context?.getSharedPreferences("desk_ai_credentials", Context.MODE_PRIVATE)
        val savedKey = prefs?.getString("api_key", null)
        val savedServer = prefs?.getString("server_url", null)
        val savedRemote = prefs?.getString("remote_url", null)
        val savedModel = prefs?.getString("selected_model", null)
        val savedProtocol = prefs?.getString("protocol", null)

        if (inDb == null) {
            BridgeConfig(
                serverUrl = savedServer ?: "http://192.168.12.151:8080",
                remoteUrl = savedRemote ?: "http://100.109.85.92:8080",
                apiKey = savedKey ?: "",
                selectedModel = savedModel ?: "auto",
                protocol = savedProtocol ?: BridgeConfig().protocol
            ).also { configDao.saveConfig(it) }
        } else if (inDb.apiKey.isBlank() && !savedKey.isNullOrBlank()) {
            val restored = inDb.copy(
                apiKey = savedKey,
                serverUrl = if (inDb.serverUrl.isBlank() && !savedServer.isNullOrBlank()) savedServer else inDb.serverUrl,
                remoteUrl = if (inDb.remoteUrl.isBlank() && !savedRemote.isNullOrBlank()) savedRemote else inDb.remoteUrl
            )
            configDao.saveConfig(restored)
            restored
        } else {
            inDb
        }
    }

    suspend fun saveConfig(config: BridgeConfig) = withContext(Dispatchers.IO) {
        configDao.saveConfig(config)
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences("desk_ai_credentials", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("server_url", config.serverUrl)
                    .putString("remote_url", config.remoteUrl)
                    .putString("api_key", config.apiKey)
                    .putString("selected_model", config.selectedModel)
                    .putString("protocol", config.protocol)
                    .apply()
            } catch (_: Exception) {}
        }
    }

    suspend fun testPing(): Triple<Boolean, Long, String> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        val result = agentClient.ping(config)
        val updated = config.copy(
            lastPingSuccess = result.first,
            lastPingMs = result.second,
            lastPingTimestamp = System.currentTimeMillis()
        )
        configDao.saveConfig(updated)
        result
    }

    suspend fun fetchDaemonStats(): DaemonStats? = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.getDaemonStats(config)
    }

    suspend fun setDaemonState(state: String): Result<String> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.setDaemonState(config, state)
    }

    suspend fun setDefaultEngine(engine: String): Result<String> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.setDefaultEngine(config, engine)
    }

    suspend fun createTask(
        title: String,
        prompt: String,
        engine: String = "auto",
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<String> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.createTask(config, title, prompt, engine, priority = "medium", idempotencyKey = idempotencyKey)
    }

    suspend fun getTask(taskId: String): Result<AgentTaskItem> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.getTask(config, taskId)
    }

    suspend fun cancelTask(taskId: String): Result<String> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.cancelTask(config, taskId)
    }

    suspend fun abortRunningTask(): Result<String> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.abortRunningTask(config)
    }

    suspend fun fetchTasks(limit: Int = 50): List<AgentTaskItem> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.fetchTasks(config, limit)
    }

    suspend fun retryTask(taskId: String): Result<String> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.retryTask(config, taskId)
    }

    suspend fun publishVideoToTikTok(filename: String): Result<String> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.publishVideoToTikTok(config, filename)
    }

    suspend fun runProposal(proposalId: String): Result<String> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.runProposal(config, proposalId)
    }

    suspend fun dismissProposal(proposalId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.dismissProposal(config, proposalId)
    }

    // Schedules: videos, phone reminders and background tasks the agent runs at set times
    suspend fun fetchSchedules(): Result<com.example.data.model.ScheduleList> = withContext(Dispatchers.IO) {
        agentClient.getSchedules(getActiveConfig())
    }

    suspend fun previewSchedule(cron: String, kind: String): Result<com.example.data.model.SchedulePreview> =
        withContext(Dispatchers.IO) { agentClient.previewSchedule(getActiveConfig(), cron, kind) }

    suspend fun createSchedule(fields: org.json.JSONObject): Result<com.example.data.model.AgentSchedule> =
        withContext(Dispatchers.IO) { agentClient.createSchedule(getActiveConfig(), fields) }

    suspend fun updateSchedule(id: Int, fields: org.json.JSONObject): Result<com.example.data.model.AgentSchedule> =
        withContext(Dispatchers.IO) { agentClient.updateSchedule(getActiveConfig(), id, fields) }

    suspend fun deleteSchedule(id: Int): Result<Unit> =
        withContext(Dispatchers.IO) { agentClient.deleteSchedule(getActiveConfig(), id) }

    suspend fun runScheduleNow(id: Int): Result<String> =
        withContext(Dispatchers.IO) { agentClient.runScheduleNow(getActiveConfig(), id) }

    suspend fun fetchAgentWorkProjects(): List<AgentWorkProject> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.getAgentWorkProjects(config)
    }

    suspend fun addAgentWorkProject(name: String, repo: String, description: String = ""): Result<AgentWorkProject> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.addAgentWorkProject(config, name, repo, description)
    }

    suspend fun dispatchAgentWorkJob(
        project: String,
        instruction: String,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<String> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.dispatchAgentWorkJob(config, project, instruction, idempotencyKey)
    }

    suspend fun runBackup(): Result<String> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.runBackup(config)
    }

    suspend fun runCleanup(): Result<String> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.runCleanup(config)
    }

    suspend fun restartAgent(): Result<String> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.restartAgent(config)
    }

    suspend fun fetchSystemLogs(limit: Int = 100): List<SystemLogEntry> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.getSystemLogs(config, limit)
    }

    suspend fun registerPushToken(token: String, deviceName: String = "Android Device"): PushRegistrationResult = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.registerPushToken(config, token, deviceName)
    }

    suspend fun testPush(): Result<String> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.testPush(config)
    }

    suspend fun triggerMedia(
        action: String,
        quote: String?,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<String> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.triggerMedia(config, action, quote, idempotencyKey)
    }

    suspend fun fetchVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.getVideos(config)
    }

    suspend fun fetchImages(): List<ImageItem> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.getImages(config)
    }

    suspend fun deleteImage(filename: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.deleteImage(config, filename)
    }

    suspend fun downloadVideo(
        videoUrl: String,
        destinationFile: java.io.File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<java.io.File> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.downloadVideo(config, videoUrl, destinationFile, onProgress)
    }

    suspend fun fetchMemoryOverview(): MemoryOverview? = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.getMemoryOverview(config)
    }

    suspend fun addMemoryFact(content: String, category: String = "fact"): Result<Int> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.addMemoryFact(config, content, category)
    }

    suspend fun deleteMemoryFact(id: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        val config = getActiveConfig()
        agentClient.deleteMemoryFact(config, id)
    }

    fun getMessages(sessionId: String): Flow<List<ChatMessage>> =
        chatDao.getMessagesForSession(sessionId)

    fun getRecentMessages(sessionId: String, limit: Int): Flow<List<ChatMessage>> =
        chatDao.getRecentMessagesForSession(sessionId, limit)

    suspend fun getMessageCount(sessionId: String): Int = withContext(Dispatchers.IO) {
        chatDao.getMessageCount(sessionId)
    }

    suspend fun createSession(
        title: String = "AlwaysOnAgent",
        systemPrompt: String = "You are AlwaysOnAgent (v2.1), an autonomous AI worker and desktop personal assistant. Be concise, direct, helpful, and provide clear code snippets."
    ): ChatSession = withContext(Dispatchers.IO) {
        val newSession = ChatSession(
            id = UUID.randomUUID().toString(),
            title = title,
            systemPrompt = systemPrompt
        )
        chatDao.insertSession(newSession)
        newSession
    }

    suspend fun updateSession(session: ChatSession) = withContext(Dispatchers.IO) {
        chatDao.updateSession(session.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        chatDao.clearMessagesForSession(sessionId)
        chatDao.deleteSession(sessionId)
    }

    suspend fun clearSession(sessionId: String) = withContext(Dispatchers.IO) {
        chatDao.clearMessagesForSession(sessionId)
    }

    suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
        chatDao.deleteMessage(messageId)
    }

    suspend fun insertMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        chatDao.insertMessage(message)
    }

    suspend fun updateMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        chatDao.updateMessage(message)
    }

    suspend fun ensureDefaultSession(): ChatSession = withContext(Dispatchers.IO) {
        val existing = chatDao.getAllSessions().firstOrNull()?.firstOrNull()
        val welcomeContent = """
        # 🛰️ DeskAI × AlwaysOnAgent (v2.1)
        ### Autonomous Computer Companion & Mobile Mission Control
        
        Welcome to your workstation's neural bridge. You have full command over your computer, background worker pool, and automation pipelines directly from your phone.
        
        ---
        
        ### 🎛️ Command Deck & Workstation Hubs
        Tap **`[/]`** on the input bar or select **`[/] COMMAND DECK`** above to pop open the command matrix:
        • ⚙️ **Connection Hub:** Network routing (`192.168.12.153:8080`), Remote Tailscale, & `HUD_AUTH_TOKEN`.
        • 🎬 **3D Video Studio:** Stream rendered 3D card-flip TikToks directly on your phone.
        • 🧠 **Memory Vault:** Browse and search your personal knowledge stored in SQLite WAL.
        • 📋 **Mission Tasks:** Inspect real-time worker queues, active tasks, and execution logs.
        • 🔄 **In-App Updater:** Download fresh OTA releases with 1 tap.
        
        ---
        
        ### ⚡ Quick Slash Directives
        • `/status` — Live host CPU, RAM, daemon pulse & task counts
        • `/wake` / `/standby` — Awaken daemon or toggle zero-power sleep mode
        • `/video [quote]` — Render 3D motion card-flip animation
        • `/tiktok [quote]` — Render & auto-publish to @Thevibecheckproject
        • `/remember [fact]` — Permanently record a memory fact
        • `/engine [auto|cloud|ollama]` — Switch execution backend
        
        ---
        
        💡 **Pro Tip:** Type any natural language task or question (e.g., *"Summarize my unread emails"* or *"Write a Python script to backup databases"*). Tapping **▶️ Run it** on any proposal will execute it live on your computer!
        """.trimIndent()

        if (existing != null) {
            val existingMessages = chatDao.getMessagesForSession(existing.id).firstOrNull()
            if (existingMessages.isNullOrEmpty()) {
                val welcome = ChatMessage(
                    sessionId = existing.id,
                    role = "assistant",
                    content = welcomeContent,
                    modelUsed = "AlwaysOnAgent Bridge",
                    latencyMs = 8L
                )
                chatDao.insertMessage(welcome)
            } else if (existingMessages.size == 1 && existingMessages.first().content.contains("Command Center")) {
                chatDao.updateMessage(existingMessages.first().copy(content = welcomeContent))
            }
            existing
        } else {
            val session = ChatSession(
                title = "Mission Control",
                systemPrompt = "You are AlwaysOnAgent (v2.1), an autonomous AI worker and desktop assistant."
            )
            chatDao.insertSession(session)
            val welcome = ChatMessage(
                sessionId = session.id,
                role = "assistant",
                content = welcomeContent,
                modelUsed = "AlwaysOnAgent Bridge",
                latencyMs = 8L
            )
            chatDao.insertMessage(welcome)
            session
        }
    }

    /**
     * Sends user message, saves to database, streams response, and persists assistant reply.
     */
    suspend fun uploadAttachment(localId: String, name: String, bytes: ByteArray): ChatAttachment =
        agentClient.uploadAttachment(getActiveConfig(), localId, name, bytes)

    fun sendMessageStream(
        session: ChatSession,
        userText: String,
        history: List<ChatMessage>,
        attachments: List<ChatAttachment> = emptyList()
    ): Flow<Pair<ChatMessage, String>> = flow {
        val config = getActiveConfig()
        val isCommand = userText.trim().startsWith("/")
        // The bubble shows what was attached; the agent gets the files by id
        val shown = if (attachments.isEmpty()) userText.trim()
            else userText.trim() + "\n" + attachments.joinToString("\n") { "📎 ${it.name}" }

        // 1. Insert User Message
        val userMessage = ChatMessage(
            sessionId = session.id,
            role = "user",
            content = shown,
            status = "sent",
            isCommand = isCommand
        )
        chatDao.insertMessage(userMessage)

        // 2. Prepare Assistant Message Placeholder
        val assistantId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        var currentText = ""

        val assistantMessage = ChatMessage(
            id = assistantId,
            sessionId = session.id,
            role = "assistant",
            content = "",
            status = "sending",
            modelUsed = "AlwaysOnAgent v2.2.1",
            proposalToken = null, // Only agent-issued tokens go here
            proposalInstruction = null,
            proposalState = null,
            proposalsJson = null
        )
        chatDao.insertMessage(assistantMessage)

        try {
            agentClient.streamChat(
                config = config,
                systemPrompt = session.systemPrompt,
                history = history,
                userMessage = userText.trim(),
                attachmentIds = attachments.mapNotNull { it.id }
            ).collect { chunk ->
                currentText += chunk
                emit(Pair(assistantMessage.copy(content = currentText), currentText))
            }

            val latency = System.currentTimeMillis() - startTime
            val structuredProps = agentClient.lastReceivedProposals
            agentClient.lastReceivedProposals = emptyList()
            val receivedQuestion = agentClient.lastReceivedQuestion
            agentClient.lastReceivedQuestion = null
            val receivedTaskId = agentClient.lastReceivedTaskId
            agentClient.lastReceivedTaskId = null

            val finalProposals: List<TaskProposal> = if (structuredProps.isNotEmpty()) {
                // Agent-issued structured proposals (preserves all proposals from agent)
                structuredProps
            } else {
                // Text fallback heuristics (marked as local so they go straight to createTask)
                val textInstructions = com.example.util.ProposalExtractor.extractAllInstructions(currentText)
                val textProject = com.example.util.ProposalExtractor.extractProject(currentText)
                if (textInstructions.isNotEmpty()) {
                    textInstructions.map { instr ->
                        TaskProposal(
                            id = "local_${UUID.randomUUID().toString().take(8)}",
                            token = "",
                            instruction = instr,
                            project = textProject,
                            isLocal = true,
                            state = "pending"
                        )
                    }
                } else {
                    emptyList()
                }
            }

            val firstProp = finalProposals.firstOrNull()
            // Fallback regex tolerant to markdown bolding (e.g. **Task ID:** `[task-041]`) and enqueued formats
            val regexFallbackId = Regex("""(?:Task ID:\**|Enqueued task)\s*`?\[?([a-zA-Z0-9_\-]+)\]?`?""", RegexOption.IGNORE_CASE)
                .find(currentText)?.groupValues?.get(1)

            val detectedTaskId = receivedTaskId ?: regexFallbackId

            val finalAssistantMessage = assistantMessage.copy(
                content = currentText.ifBlank { "Task processed." },
                status = "sent",
                latencyMs = latency,
                proposalsJson = if (finalProposals.isNotEmpty()) ChatMessage.serializeProposals(finalProposals) else null,
                proposalInstruction = firstProp?.instruction,
                proposalProject = firstProp?.project,
                proposalToken = if (firstProp != null && !firstProp.isLocal) firstProp.id else null,
                proposalState = if (firstProp != null) firstProp.state else null,
                questionJson = if (receivedQuestion != null) ChatMessage.serializeQuestion(receivedQuestion) else null,
                linkedTaskId = detectedTaskId
            )
            chatDao.updateMessage(finalAssistantMessage)

            // Update session title if first message
            val title = if (session.title == "Mission Control" && history.size <= 2) {
                userText.take(28).trim().replace("\n", " ")
            } else {
                session.title
            }
            chatDao.updateSession(session.copy(title = title, updatedAt = System.currentTimeMillis()))

        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val errMsg = e.localizedMessage ?: "Failed to connect to computer bridge"
            val errorAssistantMessage = assistantMessage.copy(
                content = currentText.ifBlank { "❌ **Error communicating with computer bridge:**\n\n$errMsg\n\n*Check that Web HUD is running on ${config.serverUrl} or verify HUD_AUTH_TOKEN in Settings.*" },
                status = "error",
                latencyMs = latency,
                errorMessage = errMsg
            )
            chatDao.updateMessage(errorAssistantMessage)
            emit(Pair(errorAssistantMessage, errorAssistantMessage.content))
        }
    }
}
