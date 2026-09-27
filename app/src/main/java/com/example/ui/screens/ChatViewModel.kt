package com.example.ui.screens

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.BridgeConfig
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.DaemonStats
import com.example.data.model.VideoItem
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

    private val _daemonStats = MutableStateFlow(DaemonStats())
    val daemonStats: StateFlow<DaemonStats> = _daemonStats.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

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

    private fun listenToMessages(sessionId: String) {
        messagesCollectJob?.cancel()
        messagesCollectJob = viewModelScope.launch {
            repository.getMessages(sessionId).collect { list ->
                _messages.value = list
            }
        }
    }

    private fun pollStatsPeriodically() {
        viewModelScope.launch {
            while (true) {
                try {
                    val stats = repository.fetchDaemonStats()
                    if (stats != null) {
                        _daemonStats.value = stats
                        _isConnected.value = true
                    }
                } catch (_: Exception) {
                    // Ignore transient errors
                }
                delay(6000)
            }
        }
    }

    fun onInputTextChange(text: String) {
        _inputText.value = text
    }

    fun sendMessage(textOverride: String? = null) {
        val textToSend = (textOverride ?: _inputText.value).trim()
        if (textToSend.isBlank() || _isStreaming.value) return

        val session = _currentSession.value ?: return
        _inputText.value = ""
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
                repository.sendMessageStream(session, textToSend, history).collect { (msg, _) ->
                    if (msg.content.isNotEmpty()) {
                        _streamingPhase.value = "📡 Streaming response from AlwaysOnAgent..."
                    }
                }
            } finally {
                _isStreaming.value = false
                timerJob.cancel()
            }
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
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

    fun runProposal(message: ChatMessage) {
        val instruction = message.proposalInstruction ?: return
        viewModelScope.launch {
            val proposalId = message.proposalToken
            if (!proposalId.isNullOrBlank()) {
                val res = repository.runProposal(proposalId)
                if (res.isSuccess) {
                    val tid = res.getOrNull() ?: "running"
                    repository.updateMessage(message.copy(proposalState = "run"))
                    val confirmMessage = ChatMessage(
                        sessionId = message.sessionId,
                        role = "assistant",
                        content = "🚀 **Proposal Started on Host!**\n\n• **Task ID:** `[$tid]`\n• **Instruction:** \"$instruction\"\n\nExecuting on your computer. View in the **Tasks** panel.",
                        modelUsed = "AlwaysOnAgent Bridge"
                    )
                    repository.insertMessage(confirmMessage)
                    return@launch
                } else if (res.exceptionOrNull()?.message?.contains("EXPIRED") == true) {
                    repository.updateMessage(message.copy(proposalState = "expired"))
                    val expiredMsg = ChatMessage(
                        sessionId = message.sessionId,
                        role = "assistant",
                        content = "⚠️ **Proposal Expired:** This suggestion expired because the desktop agent restarted.",
                        status = "error",
                        modelUsed = "AlwaysOnAgent Bridge"
                    )
                    repository.insertMessage(expiredMsg)
                    return@launch
                }
            }

            // Fallback: Dispatch as task to computer's worker pool
            repository.updateMessage(message.copy(proposalState = "run"))
            val projectPrefix = if (!message.proposalProject.isNullOrBlank()) "[${message.proposalProject}] " else ""
            val fullTitle = "$projectPrefix$instruction".take(50)
            val res = repository.createTask(fullTitle, instruction)
            if (res.isSuccess) {
                val tid = res.getOrNull() ?: "queued"
                val confirmMessage = ChatMessage(
                    sessionId = message.sessionId,
                    role = "assistant",
                    content = "🚀 **Task Dispatched to Worker Pool!**\n\n• **ID:** `[$tid]`\n• **Instruction:** \"$instruction\"\n• **Status:** `Queued in Backlog ⏳`\n\n⚡ Executing on your computer's background worker pool. View live progress in the **Tasks** panel.",
                    modelUsed = "AlwaysOnAgent Bridge"
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

    fun dismissProposal(message: ChatMessage) {
        viewModelScope.launch {
            val proposalId = message.proposalToken
            if (!proposalId.isNullOrBlank()) {
                repository.dismissProposal(proposalId)
            }
            repository.updateMessage(message.copy(proposalState = "dismissed"))
        }
    }

    suspend fun publishVideoToTikTok(filename: String): Result<String> {
        return repository.publishVideoToTikTok(filename)
    }

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

    suspend fun fetchSystemLogs(limit: Int = 100): List<com.example.data.model.SystemLogEntry> {
        return repository.fetchSystemLogs(limit)
    }

    suspend fun testPush(): Result<String> {
        return repository.testPush()
    }

    suspend fun dispatchTask(title: String, prompt: String, engine: String): Result<String> {
        return repository.createTask(title, prompt, engine)
    }

    suspend fun cancelTask(taskId: String): Result<String> {
        return repository.cancelTask(taskId)
    }

    suspend fun abortRunningTask(): Result<String> {
        return repository.abortRunningTask()
    }

    suspend fun fetchTasks(limit: Int = 50): List<com.example.data.model.AgentTaskItem> {
        return repository.fetchTasks(limit)
    }

    suspend fun retryTask(taskId: String): Result<String> {
        return repository.retryTask(taskId)
    }

    suspend fun triggerMedia(action: String, quote: String?): Result<String> {
        return repository.triggerMedia(action, quote)
    }

    suspend fun fetchVideos(): List<VideoItem> {
        return repository.fetchVideos()
    }

    suspend fun downloadVideo(
        videoUrl: String,
        destinationFile: java.io.File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<java.io.File> {
        return repository.downloadVideo(videoUrl, destinationFile, onProgress)
    }

    suspend fun addMemoryFact(content: String): Result<Int> {
        return repository.addMemoryFact(content)
    }

    suspend fun deleteMemoryFact(id: Int): Result<Boolean> {
        return repository.deleteMemoryFact(id)
    }

    fun speak(text: String) {
        if (isTtsReady && tts != null) {
            val clean = text.replace(Regex("[#*`•\\[\\]]"), "")
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "DeskAI_TTS")
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}
