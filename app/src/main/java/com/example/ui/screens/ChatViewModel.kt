package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.ChatAttachment
import com.example.data.model.MessageAttachment
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.AgentTaskItem
import com.example.data.model.AgentWorkProject
import com.example.data.model.BridgeConfig
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.DaemonStats
import com.example.data.model.TaskProposal
import com.example.data.model.VideoItem
import com.example.data.model.ImageItem
import com.example.data.remote.TaskEvents
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application, AppDatabase.getDatabase(application))

    val allSessions: StateFlow<List<ChatSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val config: StateFlow<BridgeConfig> = repository.bridgeConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BridgeConfig())

    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Only the newest messages are loaded; older ones on request ("Load earlier messages")
    private var messageLimit = PAGE_SIZE
    private val _hasEarlierMessages = MutableStateFlow(false)
    val hasEarlierMessages: StateFlow<Boolean> = _hasEarlierMessages.asStateFlow()

    private val _daemonStats = MutableStateFlow(DaemonStats())
    val daemonStats: StateFlow<DaemonStats> = _daemonStats.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    // Files attached to the message being written (uploaded and read by the agent as soon as they're picked)
    private val _attachments = MutableStateFlow<List<ChatAttachment>>(emptyList())
    val attachments: StateFlow<List<ChatAttachment>> = _attachments.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _streamingDurationMs = MutableStateFlow(0L)
    val streamingDurationMs: StateFlow<Long> = _streamingDurationMs.asStateFlow()

    private val _streamingPhase = MutableStateFlow("Processing mission...")
    val streamingPhase: StateFlow<String> = _streamingPhase.asStateFlow()

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var streamJob: Job? = null
    private var messagesCollectJob: Job? = null

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        // Links handed to other apps (external player, share) are signed by the agent, never carry the token
        com.example.data.remote.MediaLinks.signer = { url -> repository.signMediaLink(url) }

        // Initialize Android Text To Speech
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                isTtsReady = true
            }
        }

        // Initialize session and poll stats
        viewModelScope.launch {
            val session = repository.ensureDefaultSession()
            _currentSession.value = session
            listenToMessages(session.id)
            pollStatsPeriodically()
        }
    }

    private fun listenToMessages(sessionId: String, resetPaging: Boolean = true) {
        if (resetPaging) messageLimit = PAGE_SIZE
        messagesCollectJob?.cancel()
        messagesCollectJob = viewModelScope.launch {
            repository.getRecentMessages(sessionId, messageLimit).collect { list ->
                _messages.value = list
                _hasEarlierMessages.value = list.size >= messageLimit &&
                    repository.getMessageCount(sessionId) > list.size
            }
        }
    }

    fun loadEarlierMessages() {
        val session = _currentSession.value ?: return
        messageLimit += PAGE_SIZE
        listenToMessages(session.id, resetPaging = false)
    }

    companion object {
        const val PAGE_SIZE = 60
        const val MAX_ATTACHMENTS = 5                       // the agent takes up to 5 per message
        const val MAX_ATTACHMENT_BYTES = 15 * 1024 * 1024   // and up to 15 MB each
    }

    private fun pollStatsPeriodically() {
        viewModelScope.launch {
            var lastInboxSync = 0L
            while (true) {
                val stamp = TaskEvents.stampAny()         // before fetching, so a change during the fetch counts
                val fetchedAt = System.currentTimeMillis()
                try {
                    val stats = repository.fetchDaemonStats()
                    if (stats != null) {
                        _daemonStats.value = stats
                        _isConnected.value = true
                    }
                } catch (_: Exception) {
                    // Ignore transient errors
                }
                // The agent's own messages (brief, questions…): on opening, then once a minute, so a missed push
                // loses nothing (a push also syncs right away). By the clock: task events make this loop run more often
                // Cards for tasks started outside the chat (schedules, Repos, the hub, other AIs): every pass,
                // so one shows up within seconds of starting
                try {
                    repository.syncTaskCards(_currentSession.value?.id)
                } catch (_: Exception) {
                }
                if (fetchedAt - lastInboxSync >= 60_000L) {
                    lastInboxSync = fetchedAt
                    try {
                        repository.syncInbox(_currentSession.value?.id)
                    } catch (_: Exception) {
                    }
                }
                // Every 6 s as before, or as soon as a task changes while the live events stream is up
                TaskEvents.awaitAnyChange(stamp, pollMs = 6000, lastFetchAt = fetchedAt)
            }
        }
    }

    fun onInputTextChange(text: String) {
        _inputText.value = text
    }

    /** What was shared to DeskAI from another app: the text joins the message box, the files are attached
     *  (copies ShareReceiverActivity made). Nothing is sent until the owner taps Send. */
    fun acceptShared(p: com.example.SharedInbox.Payload) {
        p.files.forEach { attachFile(Uri.fromFile(it)) }
        val text = p.text?.trim().orEmpty()
        if (text.isNotEmpty()) {
            _inputText.value = listOf(_inputText.value.trim(), text).filter { it.isNotEmpty() }.joinToString("\n")
        }
    }

    /** Reads a picked file and uploads it; its chip shows "Reading…" until the agent has its text. */
    fun attachFile(uri: Uri) {
        if (_attachments.value.size >= MAX_ATTACHMENTS) return
        val resolver = getApplication<Application>().contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val localId = UUID.randomUUID().toString()
        _attachments.value = _attachments.value + ChatAttachment(localId = localId, name = name)
        viewModelScope.launch {
            var localPath: String? = null
            val result = try {
                val bytes = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { input ->
                        val buf = input.readBytes()
                        if (buf.size > MAX_ATTACHMENT_BYTES) null else buf
                    }
                }
                if (bytes != null && MessageAttachment.kindOf(name) == "image") {
                    // The chip shows the picture at once, and the sent message keeps it for the bubble
                    localPath = withContext(Dispatchers.IO) { saveImageCopy(bytes, localId) }
                    if (localPath != null) _attachments.value = _attachments.value.map {
                        if (it.localId == localId) it.copy(localPath = localPath) else it
                    }
                }
                if (bytes == null) ChatAttachment(localId = localId, name = name, status = "error",
                    error = "Too big: the limit is ${MAX_ATTACHMENT_BYTES / (1024 * 1024)} MB")
                else repository.uploadAttachment(localId, name, bytes)
            } catch (e: Exception) {
                ChatAttachment(localId = localId, name = name, status = "error", error = e.message ?: "Couldn't read the file")
            }
            // Only if it wasn't removed while it uploaded
            _attachments.value = _attachments.value.map {
                if (it.localId == localId) result.copy(localPath = localPath) else it
            }
        }
    }

    /** A copy of a picked image, at most 1280 px and upright, in the app's files (the picker's link expires). */
    private fun saveImageCopy(bytes: ByteArray, localId: String): String? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        val degrees = when (ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (bmp != null && degrees != 0f) {
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
        }
        bmp?.let { b ->
            val dir = File(getApplication<Application>().filesDir, "chat_images").apply { mkdirs() }
            val file = File(dir, "$localId.jpg")
            file.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            file.absolutePath
        }
    } catch (_: Exception) {
        null
    }

    private var currentTurnId: String? = null     // the message whose reply is on its way (for Stop)

    fun removeAttachment(localId: String) {
        _attachments.value = _attachments.value.filterNot { it.localId == localId }
    }

    fun sendMessage(textOverride: String? = null) {
        val ready = if (textOverride == null) _attachments.value.filter { it.status == "ready" } else emptyList()
        if (textOverride == null && _attachments.value.any { it.status == "uploading" }) return
        // A file may go on its own (the repository asks the agent about it)
        val textToSend = (textOverride ?: _inputText.value).trim()
        if ((textToSend.isBlank() && ready.isEmpty()) || _isStreaming.value) return

        val session = _currentSession.value ?: return
        val turnId = UUID.randomUUID().toString()
        currentTurnId = turnId
        _inputText.value = ""
        if (textOverride == null) _attachments.value = emptyList()
        _isStreaming.value = true
        _streamingDurationMs.value = 0L

        val initialPhase = when {
            textToSend.startsWith("/image") -> "🎨 Synthesizing image asset with AI..."
            textToSend.startsWith("/video") || textToSend.startsWith("/tiktok") -> "🎬 Rendering 3D motion video pipeline..."
            textToSend.startsWith("/memory") -> "🧠 Searching long-term memory vault..."
            textToSend.startsWith("/status") -> "🔍 Querying daemon health & telemetry..."
            else -> "⚡ Reasoning & executing computer mission..."
        }
        _streamingPhase.value = initialPhase

        val startTime = System.currentTimeMillis()
        val timerJob = viewModelScope.launch {
            while (_isStreaming.value) {
                _streamingDurationMs.value = System.currentTimeMillis() - startTime
                delay(100)
            }
        }

        streamJob = viewModelScope.launch {
            try {
                val history = _messages.value
                repository.sendMessageStream(session, textToSend, history, ready, turnId).collect { (msg, _) ->
                    if (msg.content.isNotEmpty()) {
                        _streamingPhase.value = "📡 Streaming response from AlwaysOnAgent..."
                    }
                }
            } finally {
                if (currentTurnId == turnId) currentTurnId = null
                _isStreaming.value = false
                timerJob.cancel()
            }
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        // The agent finishes the reply anyway: tell it to drop it rather than keep it in its memory
        currentTurnId?.let { id -> viewModelScope.launch { repository.cancelChat(id) } }
        currentTurnId = null
        _isStreaming.value = false
        _streamingDurationMs.value = 0L
    }

    fun toggleDaemon() {
        viewModelScope.launch {
            val curr = _daemonStats.value.daemonStatus
            val target = if (curr in listOf("paused", "sleep", "stopped")) "idle" else "paused"
            val res = repository.setDaemonState(target)
            if (res.isSuccess) {
                _daemonStats.value = _daemonStats.value.copy(daemonStatus = target)
            }
        }
    }

    fun saveConfig(newConfig: BridgeConfig) {
        viewModelScope.launch {
            repository.saveConfig(newConfig)
            testPing(newConfig)
        }
    }

    suspend fun testPing(testConfig: BridgeConfig? = null): Triple<Boolean, Long, String> {
        val res = repository.testPing()
        _isConnected.value = res.first
        return res
    }

    fun createNewSession() {
        viewModelScope.launch {
            val count = allSessions.value.size + 1
            val session = repository.createSession("Mission #$count")
            _currentSession.value = session
            listenToMessages(session.id)
        }
    }

    fun switchSession(session: ChatSession) {
        _currentSession.value = session
        listenToMessages(session.id)
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            val remaining = allSessions.value.filter { it.id != sessionId }
            if (remaining.isNotEmpty()) {
                switchSession(remaining.first())
            } else {
                val newS = repository.ensureDefaultSession()
                switchSession(newS)
            }
        }
    }

    fun deleteMessage(message: ChatMessage) {
        viewModelScope.launch {
            repository.deleteMessage(message.id)
        }
    }

    fun runProposal(message: ChatMessage, targetProposal: TaskProposal? = null) {
        val prop = targetProposal ?: message.getProposals().firstOrNull() ?: TaskProposal(
            id = message.proposalToken ?: "",
            token = message.proposalToken ?: "",
            instruction = message.proposalInstruction ?: return,
            project = message.proposalProject,
            isLocal = message.proposalToken.isNullOrBlank() || message.proposalToken.startsWith("local_"),
            state = message.proposalState ?: "pending"
        )
        val instruction = prop.instruction
        val targetId = if (prop.id.isNotBlank()) prop.id else prop.token

        viewModelScope.launch {
            // Only call agent /api/proposals/... if it's NOT local and has a real agent id
            if (!prop.isLocal && targetId.isNotBlank() && !targetId.startsWith("local_")) {
                val res = repository.runProposal(targetId)
                if (res.isSuccess) {
                    val resultString = res.getOrNull() ?: "success"
                    repository.updateMessage(message.withUpdatedProposal(targetId, "run"))
                    val isAddProject = prop.kind == "add_project"
                    val confirmContent = if (isAddProject) {
                        "📁 **Project Registered with AgentWork!**\n\n$resultString\n\nTasks can now target this repository in AgentWork."
                    } else {
                        "🚀 **Proposal Started on Host!**\n\n• **Task ID:** `[$resultString]`\n• **Instruction:** \"$instruction\"\n\nExecuting on your computer. View in the **Tasks** panel."
                    }
                    val confirmMessage = ChatMessage(
                        sessionId = message.sessionId,
                        role = "assistant",
                        content = confirmContent,
                        modelUsed = "AlwaysOnAgent Bridge",
                        linkedTaskId = if (isAddProject) null else resultString
                    )
                    repository.insertMessage(confirmMessage)
                    return@launch
                } else {
                    val errMsg = res.exceptionOrNull()?.message ?: "Unknown error"
                    if (errMsg.contains("EXPIRED")) {
                        repository.updateMessage(message.withUpdatedProposal(targetId, "expired"))
                        val expiredMsg = ChatMessage(
                            sessionId = message.sessionId,
                            role = "assistant",
                            content = "⚠️ **Proposal Expired:** This suggestion expired because the desktop agent restarted.",
                            status = "error",
                            modelUsed = "AlwaysOnAgent Bridge"
                        )
                        repository.insertMessage(expiredMsg)
                        return@launch
                    } else if (prop.kind == "add_project") {
                        val failedMsg = ChatMessage(
                            sessionId = message.sessionId,
                            role = "assistant",
                            content = "❌ **Could Not Add Project:** $errMsg",
                            status = "error",
                            modelUsed = "AlwaysOnAgent Bridge"
                        )
                        repository.insertMessage(failedMsg)
                        return@launch
                    }
                }
            }

            // Local proposal or direct fallback: Dispatch as task to computer's worker pool!
            repository.updateMessage(message.withUpdatedProposal(targetId, "run"))
            val projectPrefix = if (!prop.project.isNullOrBlank()) "[${prop.project}] " else ""
            val fullTitle = "$projectPrefix$instruction".take(50)
            val res = repository.createTask(fullTitle, instruction)
            if (res.isSuccess) {
                val tid = res.getOrNull() ?: "queued"
                val confirmMessage = ChatMessage(
                    sessionId = message.sessionId,
                    role = "assistant",
                    content = "🚀 **Task Dispatched to Worker Pool!**\n\n• **ID:** `[$tid]`\n• **Instruction:** \"$instruction\"\n• **Status:** `Queued in Backlog ⏳`\n\n⚡ Executing on your computer's background worker pool. View live progress in the **Tasks** panel.",
                    modelUsed = "AlwaysOnAgent Bridge",
                    linkedTaskId = tid
                )
                repository.insertMessage(confirmMessage)
            } else {
                val errMessage = ChatMessage(
                    sessionId = message.sessionId,
                    role = "assistant",
                    content = "⚠️ **Failed to dispatch task:** ${res.exceptionOrNull()?.message ?: "Bridge connection error"}",
                    status = "error",
                    modelUsed = "AlwaysOnAgent Bridge"
                )
                repository.insertMessage(errMessage)
            }
        }
    }

    fun answerQuestion(message: ChatMessage, option: String) {
        viewModelScope.launch {
            repository.updateMessage(message.withAnsweredQuestion(option))
        }
        sendMessage(option)
    }

    suspend fun getTask(taskId: String): Result<AgentTaskItem> {
        return repository.getTask(taskId)
    }

    suspend fun cancelTask(taskId: String): Result<String> {
        return repository.cancelTask(taskId)
    }

    suspend fun retryTask(taskId: String): Result<String> {
        return repository.retryTask(taskId)
    }

    suspend fun addAgentWorkProject(name: String, repo: String, description: String = ""): Result<AgentWorkProject> {
        return repository.addAgentWorkProject(name, repo, description)
    }

    fun dismissProposal(message: ChatMessage, targetProposal: TaskProposal? = null) {
        val prop = targetProposal ?: message.getProposals().firstOrNull() ?: return
        val targetId = if (prop.id.isNotBlank()) prop.id else prop.token
        viewModelScope.launch {
            if (!prop.isLocal && targetId.isNotBlank() && !targetId.startsWith("local_")) {
                repository.dismissProposal(targetId)
            }
            repository.updateMessage(message.withUpdatedProposal(targetId, "dismissed"))
        }
    }

    suspend fun fetchCanvasVersions(canvasId: Long): List<Int> = repository.fetchCanvasVersions(canvasId)

    /** A task card's "post" chip (e.g. "🚀 Push live"): calls the agent and shows its answer in the chat. */
    fun runChipAction(label: String, path: String) {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            val res = repository.postChipAction(path)
            repository.insertMessage(
                ChatMessage(
                    sessionId = session.id,
                    role = "assistant",
                    content = res.fold({ "$label: $it" }, { "⚠️ $label didn't start: ${it.message}" }),
                    status = "sent",
                    modelUsed = "AlwaysOnAgent Bridge"
                )
            )
        }
    }

    suspend fun publishVideoToTikTok(filename: String): Result<String> {
        val res = repository.publishVideoToTikTok(filename)
        val tid = res.getOrNull()
        val session = _currentSession.value
        // The agent only queues the post here; the linked card shows whether TikTok actually published it
        if (res.isSuccess && session != null && !tid.isNullOrBlank() && tid.startsWith("task-")) {
            repository.insertMessage(
                ChatMessage(
                    sessionId = session.id,
                    role = "assistant",
                    content = "🚀 Posting $filename to TikTok",
                    status = "sent",
                    modelUsed = "AlwaysOnAgent Bridge",
                    linkedTaskId = tid
                )
            )
        }
        return res
    }

    // Schedules sheet
    suspend fun fetchSchedules() = repository.fetchSchedules()
    suspend fun previewSchedule(cron: String, kind: String) = repository.previewSchedule(cron, kind)
    suspend fun createSchedule(fields: org.json.JSONObject) = repository.createSchedule(fields)
    suspend fun updateSchedule(id: Int, fields: org.json.JSONObject) = repository.updateSchedule(id, fields)
    suspend fun deleteSchedule(id: Int) = repository.deleteSchedule(id)
    suspend fun runScheduleNow(id: Int) = repository.runScheduleNow(id)

    suspend fun fetchAgentWorkProjects(): List<com.example.data.model.AgentWorkProject> {
        return repository.fetchAgentWorkProjects()
    }

    suspend fun dispatchAgentWorkJob(project: String, instruction: String): Result<String> {
        return repository.dispatchAgentWorkJob(project, instruction)
    }

    suspend fun runBackup(): Result<String> {
        return repository.runBackup()
    }

    suspend fun runCleanup(): Result<String> {
        return repository.runCleanup()
    }

    suspend fun restartAgent(): Result<String> {
        return repository.restartAgent()
    }

    suspend fun agentUptime(): Long? = repository.agentUptime()

    // Needs you: what computer tasks on the Mini are waiting on (refreshed every 20 s while the app is open, so a
    // missed alert still shows as a banner)
    private val _needsYou = MutableStateFlow<List<com.example.data.model.ComputerRequest>>(emptyList())
    val needsYou: StateFlow<List<com.example.data.model.ComputerRequest>> = _needsYou.asStateFlow()
    private var needsYouPolling: Job? = null

    fun refreshNeedsYou() {
        viewModelScope.launch {
            _needsYou.value = try { repository.computerRequests() } catch (_: Exception) { _needsYou.value }
        }
    }

    fun startNeedsYouPolling() {
        if (needsYouPolling?.isActive == true) return
        needsYouPolling = viewModelScope.launch {
            while (true) {
                _needsYou.value = try { repository.computerRequests() } catch (_: Exception) { _needsYou.value }
                delay(20_000)
            }
        }
    }

    suspend fun answerComputerRequest(requestId: String, answer: String): Result<String> {
        val result = repository.answerComputerRequest(requestId, answer)
        if (result.isSuccess) {
            com.example.service.NeedsYou.cancel(getApplication(), requestId)
            _needsYou.value = _needsYou.value.filterNot { it.id == requestId }
        }
        return result
    }

    suspend fun requestScreenshot(path: String): ByteArray? = repository.fetchBytes(path)

    /** What a computer-use run is doing, for Take over's banner; null when it can't be read. */
    suspend fun takeoverActivity(machine: String): org.json.JSONObject? = repository.takeoverActivity(machine).getOrNull()

    /** Take over: start, stop (hand back) or input (docs/API.md "Take over"). */
    suspend fun takeover(action: String, body: org.json.JSONObject? = null): Result<org.json.JSONObject> {
        val result = repository.takeover(action, body)
        // Handing back answers the request it was taken over for: its alert goes, and the list refreshes
        if (action == "stop" && result.isSuccess) {
            result.getOrNull()?.optString("handed_back_to")?.takeIf { it.isNotBlank() && it != "null" }?.let {
                com.example.service.NeedsYou.cancel(getApplication(), it)
            }
            refreshNeedsYou()
        }
        return result
    }

    /** The repo browser (docs/API.md "Repos"). */
    suspend fun github(method: String, path: String, body: org.json.JSONObject? = null,
                       idempotencyKey: String? = null): Result<org.json.JSONObject> =
        repository.github(method, path, body, idempotencyKey)

    suspend fun fetchSystemLogs(limit: Int = 100): List<com.example.data.model.SystemLogEntry> {
        return repository.fetchSystemLogs(limit)
    }

    suspend fun testPush(): Result<String> {
        return repository.testPush()
    }

    suspend fun dispatchTask(title: String, prompt: String, engine: String): Result<String> {
        return repository.createTask(title, prompt, engine)
    }

    suspend fun abortRunningTask(): Result<String> {
        return repository.abortRunningTask()
    }

    suspend fun fetchTasks(limit: Int = 50): List<com.example.data.model.AgentTaskItem> {
        return repository.fetchTasks(limit)
    }

    suspend fun triggerMedia(action: String, quote: String?): Result<String> {
        val res = repository.triggerMedia(action, quote)
        if (res.isSuccess) {
            val tid = res.getOrNull()
            val session = _currentSession.value
            if (session != null && !tid.isNullOrBlank()) {
                val bubbleText = if (action == "tiktok") "🚀 Rendering & publishing to TikTok" else "🎬 Rendering your video"
                val assistantMsg = ChatMessage(
                    sessionId = session.id,
                    role = "assistant",
                    content = bubbleText,
                    status = "sent",
                    modelUsed = "AlwaysOnAgent Bridge",
                    linkedTaskId = tid
                )
                repository.insertMessage(assistantMsg)
            }
        }
        return res
    }

    suspend fun fetchVideos(): List<VideoItem> {
        return repository.fetchVideos()
    }

    suspend fun fetchImages(): List<ImageItem> {
        return repository.fetchImages()
    }

    suspend fun deleteImage(filename: String): Result<Boolean> {
        return repository.deleteImage(filename)
    }

    suspend fun downloadVideo(
        videoUrl: String,
        destinationFile: java.io.File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<java.io.File> {
        return repository.downloadVideo(videoUrl, destinationFile, onProgress)
    }

    suspend fun fetchMemoryOverview(): com.example.data.model.MemoryOverview? = repository.fetchMemoryOverview()

    suspend fun addMemoryFact(content: String): Result<Int> {
        return repository.addMemoryFact(content)
    }

    suspend fun deleteMemoryFact(id: Int): Result<Boolean> {
        return repository.deleteMemoryFact(id)
    }

    suspend fun updateRecipe(id: Int, pinned: Boolean? = null, status: String? = null): Result<Boolean> =
        repository.updateRecipe(id, pinned, status)

    suspend fun deleteRecipe(id: Int): Result<Boolean> = repository.deleteRecipe(id)

    suspend fun fetchMemoryCheck() = repository.fetchMemoryCheck()

    suspend fun answerMemoryCheck(keep: List<Int>) = repository.answerMemoryCheck(keep)

    fun speak(text: String) {
        if (isTtsReady && tts != null) {
            val clean = text.replace(Regex("[#*`•\\[\\]]"), "")
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "DeskAI_TTS")
        }
    }

    override fun onCleared() {
        super.onCleared()
        com.example.data.remote.MediaLinks.signer = null
        tts?.stop()
        tts?.shutdown()
    }
}
