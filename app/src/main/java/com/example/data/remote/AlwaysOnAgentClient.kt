package com.example.data.remote

import com.example.data.model.AgentTaskItem
import com.example.data.model.AgentWorkProject
import com.example.data.model.BridgeConfig
import com.example.data.model.BridgeProtocol
import com.example.data.model.ChatMessage
import com.example.data.model.DaemonStats
import com.example.data.model.MemoryFactItem
import com.example.data.model.MemoryOverview
import com.example.data.model.PushRegistrationResult
import com.example.data.model.SystemLogEntry
import com.example.data.model.TaskProposal
import com.example.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class AlwaysOnAgentClient {

    @Volatile
    private var lastWorkingUrl: String? = null

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    var lastReceivedProposal: TaskProposal? = null

    private fun addAuth(builder: Request.Builder, config: BridgeConfig): Request.Builder {
        if (config.apiKey.isNotBlank()) {
            builder.addHeader("X-HUD-Token", config.apiKey.trim())
            builder.addHeader("Cookie", "hud_token=${config.apiKey.trim()}")
            builder.addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
        }
        return builder
    }

    private fun getCandidateUrls(config: BridgeConfig): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val home = if (config.serverUrl.isNotBlank()) Pair(config.serverUrl.trim().removeSuffix("/"), "Home LAN") else null
        val remote = if (config.remoteUrl.isNotBlank() && config.useRemoteWhenAway) Pair(config.remoteUrl.trim().removeSuffix("/"), "Remote Away") else null

        // If last working URL was Remote Away, try Remote Away first for zero-delay response
        val last = lastWorkingUrl
        if (last != null && remote != null && last.startsWith(remote.first)) {
            list.add(remote)
            if (home != null) list.add(home)
        } else {
            if (home != null) list.add(home)
            if (remote != null) list.add(remote)
        }

        return list.ifEmpty { listOf(Pair("http://10.0.2.2:8080", "Default")) }
    }

    private suspend fun <T> executeWithFailover(
        config: BridgeConfig,
        block: suspend (baseUrl: String) -> T
    ): T {
        val candidates = getCandidateUrls(config)
        var lastException: Exception? = null

        for ((url, _) in candidates) {
            try {
                val result = block(url)
                lastWorkingUrl = url
                return result
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw lastException ?: IllegalStateException("Unable to connect to computer bridge")
    }

    suspend fun ping(config: BridgeConfig): Triple<Boolean, Long, String> = withContext(Dispatchers.IO) {
        val candidates = getCandidateUrls(config)
        var lastError = "Unable to connect"
        var lastLatency = 0L
        var localFailedReason: String? = null

        for ((baseUrl, label) in candidates) {
            val startTime = System.currentTimeMillis()
            try {
                val reqBuilder = Request.Builder().url("$baseUrl/").get()
                addAuth(reqBuilder, config)

                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    val latency = System.currentTimeMillis() - startTime
                    lastLatency = latency
                    if (response.isSuccessful || response.code == 401) {
                        if (response.code == 401) {
                            return@withContext Triple(false, latency, "HTTP 401: Unauthorized. Please enter HUD_AUTH_TOKEN in Settings.")
                        }
                        val note = if (label.contains("Remote") && localFailedReason != null) {
                            "Connected via $label (${latency}ms) [Home LAN: $localFailedReason]"
                        } else {
                            "Connected via $label (${latency}ms)"
                        }
                        return@withContext Triple(true, latency, note)
                    } else {
                        val err = "HTTP ${response.code}: ${response.message}"
                        if (label.contains("Home")) localFailedReason = err
                        lastError = err
                    }
                }
            } catch (e: Exception) {
                lastLatency = System.currentTimeMillis() - startTime
                val cleanErr = e.localizedMessage ?: "Connection failed"
                if (label.contains("Home")) localFailedReason = cleanErr
                lastError = cleanErr
            }
        }

        Triple(false, lastLatency, lastError)
    }

    suspend fun getDaemonStats(config: BridgeConfig): DaemonStats? = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val req = addAuth(Request.Builder().url("$baseUrl/api/stream"), config).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@executeWithFailover null
                    val reader = BufferedReader(InputStreamReader(resp.body?.byteStream() ?: return@executeWithFailover null))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val curr = line?.trim() ?: continue
                        if (curr.startsWith("data:")) {
                            val json = JSONObject(curr.removePrefix("data:").trim())
                            val stats = json.optJSONObject("stats")
                            val tasksArray = json.optJSONArray("tasks")
                            val parsedTasks = mutableListOf<AgentTaskItem>()
                            if (tasksArray != null) {
                                for (i in 0 until tasksArray.length()) {
                                    val tObj = tasksArray.getJSONObject(i)
                                    parsedTasks.add(parseTaskJson(tObj))
                                }
                            }
                            val activeTaskObj = json.optJSONObject("active_task")
                            val parsedActive = if (activeTaskObj != null) parseTaskJson(activeTaskObj) else null

                            return@executeWithFailover DaemonStats(
                                daemonStatus = json.optString("daemon_status", "idle"),
                                overallPhase = json.optString("overall_phase", "idle"),
                                defaultEngine = json.optString("default_engine", "auto"),
                                lastHeartbeat = json.optString("last_heartbeat", ""),
                                tasksCompleted = stats?.optInt("tasks_completed") ?: 0,
                                tasksInProgress = stats?.optInt("tasks_in_progress") ?: 0,
                                tasksBacklog = stats?.optInt("tasks_backlog") ?: 0,
                                tasksFailed = stats?.optInt("tasks_failed") ?: 0,
                                totalHeartbeats = stats?.optInt("total_heartbeats") ?: 0,
                                tasks = parsedTasks,
                                activeTask = parsedActive
                            )
                        }
                    }
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun setDaemonState(config: BridgeConfig, state: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val payload = JSONObject().apply { put("status", state) }
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/daemon/state")
                        .post(payload.toString().toRequestBody(jsonMediaType)),
                    config
                ).build()

                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        Result.success("Daemon state set to $state")
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: $body"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setDefaultEngine(config: BridgeConfig, engine: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val payload = JSONObject().apply { put("engine", engine) }
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/engine")
                        .post(payload.toString().toRequestBody(jsonMediaType)),
                    config
                ).build()

                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        Result.success("Default engine set to $engine")
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: $body"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTask(
        config: BridgeConfig,
        title: String,
        prompt: String,
        engine: String = "auto",
        priority: String = "medium"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val payload = JSONObject().apply {
                    put("title", title)
                    put("prompt", prompt)
                    put("engine", engine)
                    put("priority", priority)
                    put("mode", "accept-edits")
                    put("timeout_seconds", 300)
                }
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/tasks")
                        .post(payload.toString().toRequestBody(jsonMediaType)),
                    config
                ).build()

                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        val json = JSONObject(body)
                        val tid = json.optString("task_id", "queued")
                        Result.success(tid)
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: $body"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelTask(config: BridgeConfig, taskId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val emptyBody = "".toRequestBody(jsonMediaType)
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/cancel/$taskId")
                        .post(emptyBody),
                    config
                ).build()

                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        Result.success("Task #$taskId cancelled")
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: $body"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun abortRunningTask(config: BridgeConfig): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val emptyBody = "{}".toRequestBody(jsonMediaType)
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/tasks/cancel")
                        .post(emptyBody),
                    config
                ).build()

                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        Result.success(body)
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: $body"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTasks(config: BridgeConfig, limit: Int = 50): List<AgentTaskItem> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val req = addAuth(Request.Builder().url("$baseUrl/api/tasks?limit=$limit"), config).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@executeWithFailover emptyList()
                    val body = resp.body?.string() ?: return@executeWithFailover emptyList()
                    val array = if (body.trim().startsWith("[")) {
                        JSONArray(body)
                    } else {
                        JSONObject(body).optJSONArray("tasks") ?: JSONArray()
                    }
                    val list = mutableListOf<AgentTaskItem>()
                    for (i in 0 until array.length()) {
                        list.add(parseTaskJson(array.getJSONObject(i)))
                    }
                    list
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseTaskJson(item: JSONObject): AgentTaskItem {
        fun optNullableString(key: String): String? {
            if (!item.has(key) || item.isNull(key)) return null
            val str = item.optString(key, "").trim()
            return if (str.isEmpty() || str.equals("null", ignoreCase = true)) null else str
        }

        return AgentTaskItem(
            id = optNullableString("id") ?: optNullableString("task_id") ?: "",
            title = optNullableString("title") ?: optNullableString("prompt") ?: "Task",
            prompt = optNullableString("prompt") ?: optNullableString("title") ?: "",
            phase = optNullableString("phase") ?: "backlog",
            engine = optNullableString("engine") ?: "auto",
            priority = optNullableString("priority") ?: "medium",
            startedAt = optNullableString("started_at"),
            completedAt = optNullableString("completed_at"),
            outputSummary = optNullableString("output_summary"),
            lastError = optNullableString("last_error"),
            workerPid = if (item.has("worker_pid") && !item.isNull("worker_pid")) item.optInt("worker_pid") else null
        )
    }

    suspend fun retryTask(config: BridgeConfig, taskId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val emptyBody = "".toRequestBody(jsonMediaType)
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/retry/$taskId")
                        .post(emptyBody),
                    config
                ).build()

                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        Result.success("Task #$taskId requeued to backlog")
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: $body"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun publishVideoToTikTok(config: BridgeConfig, filename: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val emptyBody = "".toRequestBody(jsonMediaType)
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/videos/$filename/publish")
                        .post(emptyBody),
                    config
                ).build()

                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        val json = JSONObject(body)
                        Result.success(json.optString("task_id", "tiktok_published"))
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: $body"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun runProposal(config: BridgeConfig, proposalId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val emptyBody = "".toRequestBody(jsonMediaType)
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/proposals/$proposalId/run")
                        .post(emptyBody),
                    config
                ).build()

                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        val json = JSONObject(body)
                        Result.success(json.optString("task_id", "proposal_running"))
                    } else if (resp.code == 404) {
                        Result.failure(Exception("EXPIRED: This proposal expired because the agent restarted."))
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: $body"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dismissProposal(config: BridgeConfig, proposalId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val emptyBody = "".toRequestBody(jsonMediaType)
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/proposals/$proposalId/dismiss")
                        .post(emptyBody),
                    config
                ).build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        Result.success(true)
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAgentWorkProjects(config: BridgeConfig): List<AgentWorkProject> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val req = addAuth(Request.Builder().url("$baseUrl/api/agentwork/projects"), config).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@executeWithFailover emptyList()
                    val body = resp.body?.string() ?: return@executeWithFailover emptyList()
                    val array = JSONArray(body)
                    val list = mutableListOf<AgentWorkProject>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        list.add(
                            AgentWorkProject(
                                name = obj.optString("name", "Project"),
                                description = obj.optString("description", ""),
                                repo = obj.optString("repo", "")
                            )
                        )
                    }
                    list
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun dispatchAgentWorkJob(config: BridgeConfig, project: String, instruction: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val payload = JSONObject().apply {
                    put("project", project)
                    put("instruction", instruction)
                }
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/agentwork/jobs")
                        .post(payload.toString().toRequestBody(jsonMediaType)),
                    config
                ).build()

                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        val json = JSONObject(body)
                        Result.success(json.optString("task_id", "agentwork_dispatched"))
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: $body"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun runBackup(config: BridgeConfig): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val emptyBody = "".toRequestBody(jsonMediaType)
                val req = addAuth(Request.Builder().url("$baseUrl/api/backup").post(emptyBody), config).build()
                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        val json = JSONObject(body)
                        Result.success(json.optString("message", "Backup completed successfully"))
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: $body"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun runCleanup(config: BridgeConfig): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val emptyBody = "".toRequestBody(jsonMediaType)
                val req = addAuth(Request.Builder().url("$baseUrl/api/cleanup").post(emptyBody), config).build()
                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        val json = JSONObject(body)
                        Result.success(json.optString("message", "System cleanup completed"))
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: $body"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restartAgent(config: BridgeConfig): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val emptyBody = "".toRequestBody(jsonMediaType)
                val req = addAuth(Request.Builder().url("$baseUrl/api/restart").post(emptyBody), config).build()
                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful || resp.code == 202) {
                        Result.success("Agent restarting... Standby for re-connection.")
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: $body"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSystemLogs(config: BridgeConfig, limit: Int = 100): List<SystemLogEntry> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val req = addAuth(Request.Builder().url("$baseUrl/api/logs?limit=$limit"), config).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@executeWithFailover emptyList()
                    val body = resp.body?.string() ?: return@executeWithFailover emptyList()
                    val array = JSONArray(body)
                    val list = mutableListOf<SystemLogEntry>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        list.add(
                            SystemLogEntry(
                                id = obj.optString("id", i.toString()),
                                timestamp = obj.optString("timestamp", ""),
                                level = obj.optString("level", "INFO"),
                                message = obj.optString("message", "")
                            )
                        )
                    }
                    list
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun registerPushToken(config: BridgeConfig, token: String, deviceName: String = "Android Device"): PushRegistrationResult = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val payload = JSONObject().apply {
                    put("token", token)
                    put("device_name", deviceName)
                }
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/push/register")
                        .post(payload.toString().toRequestBody(jsonMediaType)),
                    config
                ).build()
                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    val json = if (body.isNotBlank()) JSONObject(body) else JSONObject()
                    PushRegistrationResult(
                        status = json.optString("status", if (resp.isSuccessful) "ok" else "error"),
                        pushReady = json.optBoolean("push_ready", false),
                        message = json.optString("message", "")
                    )
                }
            }
        } catch (e: Exception) {
            PushRegistrationResult(status = "error", pushReady = false, message = e.message ?: "")
        }
    }

    suspend fun testPush(config: BridgeConfig): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val emptyBody = "".toRequestBody(jsonMediaType)
                val req = addAuth(Request.Builder().url("$baseUrl/api/push/test").post(emptyBody), config).build()
                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        val json = JSONObject(body)
                        val status = json.optString("status")
                        when (status) {
                            "sent" -> Result.success("Push delivered to ${json.optInt("devices", 1)} device(s)!")
                            "not_configured" -> Result.success("Firebase key pending on host PC (registered successfully).")
                            "no_devices" -> Result.failure(Exception("No devices currently registered for push."))
                            else -> Result.success(body)
                        }
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: $body"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun triggerMedia(config: BridgeConfig, action: String, quote: String?): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val payload = JSONObject().apply {
                    put("action", action)
                    if (quote != null) put("quote", quote)
                }
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/trigger_media")
                        .post(payload.toString().toRequestBody(jsonMediaType)),
                    config
                ).build()

                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        val json = JSONObject(body)
                        Result.success(json.optString("task_id", "media_queued"))
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: $body"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVideos(config: BridgeConfig): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val urlsToTry = listOf("$baseUrl/api/videos", "$baseUrl/api/media")
                for (url in urlsToTry) {
                    try {
                        val req = addAuth(Request.Builder().url(url), config).build()
                        httpClient.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@use
                            val body = resp.body?.string() ?: ""
                            val json = JSONObject(body)
                            val array = json.optJSONArray("videos") ?: json.optJSONArray("media") ?: return@use
                            val list = mutableListOf<VideoItem>()
                            val cleanBase = baseUrl.trimEnd('/')
                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)
                                val fname = obj.optString("filename", obj.optString("name"))
                                val rawPath = obj.optString("url", "").trim()
                                val fullUrl = when {
                                    rawPath.startsWith("http://") || rawPath.startsWith("https://") -> rawPath
                                    rawPath.startsWith("/") -> "$cleanBase$rawPath"
                                    rawPath.isNotBlank() -> "$cleanBase/$rawPath"
                                    fname.isNotBlank() -> "$cleanBase/videos/$fname"
                                    else -> ""
                                }
                                val authedUrl = if (config.apiKey.isNotBlank() && !fullUrl.contains("token=")) {
                                    "$fullUrl${if (fullUrl.contains("?")) "&" else "?"}token=${config.apiKey.trim()}"
                                } else {
                                    fullUrl
                                }
                                if (fname.isNotBlank()) {
                                    list.add(
                                        VideoItem(
                                            filename = fname,
                                            sizeMb = obj.optDouble("size_mb", obj.optDouble("size", 0.0)),
                                            createdAt = obj.optString("created_at", obj.optString("date", "")),
                                            url = authedUrl
                                        )
                                    )
                                }
                            }
                            if (list.isNotEmpty()) return@executeWithFailover list
                        }
                    } catch (_: Exception) {}
                }
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun downloadVideo(
        config: BridgeConfig,
        videoUrl: String,
        destinationFile: java.io.File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<java.io.File> = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder().url(videoUrl)
            val req = addAuth(reqBuilder, config).build()
            val response = httpClient.newCall(req).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
            val body = response.body ?: return@withContext Result.failure(Exception("Empty video response body"))
            val totalBytes = body.contentLength()

            destinationFile.parentFile?.mkdirs()
            val tempFile = java.io.File(destinationFile.parentFile, "${destinationFile.name}.tmp")

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        onProgress(totalRead, totalBytes)
                    }
                    output.flush()
                }
            }
            if (tempFile.renameTo(destinationFile)) {
                Result.success(destinationFile)
            } else {
                Result.success(tempFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMemoryOverview(config: BridgeConfig): MemoryOverview? = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val req = addAuth(Request.Builder().url("$baseUrl/api/memory"), config).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@executeWithFailover null
                    val json = JSONObject(resp.body?.string() ?: "")
                    val stats = json.optJSONObject("stats")
                    val factsArray = json.optJSONArray("facts")
                    val factList = mutableListOf<MemoryFactItem>()
                    if (factsArray != null) {
                        for (i in 0 until factsArray.length()) {
                            val f = factsArray.getJSONObject(i)
                            factList.add(
                                MemoryFactItem(
                                    id = f.optInt("id"),
                                    content = f.optString("content"),
                                    category = f.optString("category", "fact"),
                                    importance = f.optInt("importance", 5),
                                    pinned = f.optBoolean("pinned", false),
                                    updatedAt = f.optString("updated_at", "")
                                )
                            )
                        }
                    }
                    MemoryOverview(
                        profile = json.optString("profile", ""),
                        factsActive = stats?.optInt("facts_active") ?: 0,
                        factsPinned = stats?.optInt("facts_pinned") ?: 0,
                        messagesCount = stats?.optInt("messages") ?: 0,
                        summariesCount = stats?.optInt("summaries") ?: 0,
                        facts = factList
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun addMemoryFact(config: BridgeConfig, content: String, category: String = "fact"): Result<Int> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val payload = JSONObject().apply {
                    put("content", content)
                    put("category", category)
                    put("importance", 5)
                    put("pinned", true)
                }
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/memory/facts")
                        .post(payload.toString().toRequestBody(jsonMediaType)),
                    config
                ).build()

                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        val json = JSONObject(body)
                        Result.success(json.optInt("id", 0))
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: $body"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMemoryFact(config: BridgeConfig, factId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/memory/facts/$factId?erase=true")
                        .delete(),
                    config
                ).build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        Result.success(true)
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Executes real conversational chat or commands with streaming directly from your computer.
     */
    fun streamChat(
        config: BridgeConfig,
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String
    ): Flow<String> = flow {
        val trimmed = userMessage.trim()

        // 1. Process slash commands natively
        if (trimmed.startsWith("/")) {
            handleSlashCommand(config, trimmed) { chunk ->
                emit(chunk)
            }
            return@flow
        }

        // 2. Direct task enqueue syntax
        if (trimmed.startsWith("task:", ignoreCase = true) ||
            trimmed.startsWith("build:", ignoreCase = true) ||
            trimmed.startsWith("do:", ignoreCase = true) ||
            trimmed.startsWith("run:", ignoreCase = true)
        ) {
            val promptText = trimmed.substringAfter(":").trim()
            val title = promptText.take(45)
            val res = createTask(config, title, promptText)
            if (res.isSuccess) {
                emit("📥 **Task Enqueued in AlwaysOnAgent Worker Pool**\n\n• **ID:** `[${res.getOrNull()}]`\n• **Title:** \"$title\"\n• **Status:** `Queued in Backlog ⏳`\n\nThe background daemon will execute this mission automatically!")
            } else {
                emit("⚠️ Failed to enqueue task on computer: ${res.exceptionOrNull()?.message}")
            }
            return@flow
        }

        // 3. Real conversational chat with the computer assistant
        executeRealConversation(config, systemPrompt, history, trimmed) { chunk ->
            emit(chunk)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun handleSlashCommand(
        config: BridgeConfig,
        cmdText: String,
        onChunk: suspend (String) -> Unit
    ) {
        val parts = cmdText.split(Regex("\\s+"), limit = 2)
        val cmd = parts[0].lowercase()
        val arg = if (parts.size > 1) parts[1].trim() else ""

        when (cmd) {
            "/status" -> {
                val stats = getDaemonStats(config)
                if (stats != null) {
                    val statusText = if (stats.daemonStatus == "running_task") "🟢 Executing Mission" else if (stats.daemonStatus == "idle") "🟢 Online / Idle" else "⏸️ Standby (Sleep)"
                    onChunk("""
                        📊 **AlwaysOnAgent Command Center (v2.1)**
                        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                        • **Daemon Status:** $statusText
                        • **Project Phase:** `${stats.overallPhase.uppercase()}`
                        • **Default Engine:** `${stats.defaultEngine}`
                        • **Last Pulse:** `${stats.lastHeartbeat.ifBlank { "Live" }}`
                        
                        📈 **Task Metrics:**
                        • Completed: `${stats.tasksCompleted}`
                        • In Progress: `${stats.tasksInProgress}`
                        • Backlog: `${stats.tasksBacklog}`
                        • Failed: `${stats.tasksFailed}`
                        • Total Heartbeats: `${stats.totalHeartbeats}`
                    """.trimIndent())
                } else {
                    onChunk("""
                        ⚠️ **Unable to retrieve live telemetry from AlwaysOnAgent**
                        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                        • **Host:** `${config.getResolvedUrl()}`
                        • **Status:** Could not read from `/api/stream`.
                        • **Verification:**
                          1. Ensure `python supervisor.py` is running on your PC.
                          2. Verify `HUD_AUTH_TOKEN` matches your PC `.env`.
                          3. Confirm Windows Firewall allows port `8080`.
                    """.trimIndent())
                }
            }

            "/wake", "/start_daemon", "/boot", "/ignite" -> {
                val res = setDaemonState(config, "idle")
                if (res.isSuccess) {
                    onChunk("""
                        🟢 **AlwaysOnAgent Daemon Awakened!**
                        
                        • **State:** `ONLINE & ACTIVE`
                        • **Worker Pool:** Listening for missions
                        • **Host Hardware:** Resumed processing
                        
                        🚀 Pending backlog tasks will be executed immediately.
                    """.trimIndent())
                } else {
                    onChunk("⚠️ Error waking daemon: ${res.exceptionOrNull()?.message}")
                }
            }

            "/standby", "/stop_daemon", "/pause", "/sleep" -> {
                val res = setDaemonState(config, "paused")
                if (res.isSuccess) {
                    onChunk("""
                        ⏸️ **AlwaysOnAgent Daemon in Standby**
                        
                        • **State:** `STANDBY (SLEEP)`
                        • **Host Hardware:** `0% CPU / GPU`
                        
                        💡 Send `/wake` or speak any instruction to wake it back up!
                    """.trimIndent())
                } else {
                    onChunk("⚠️ Error putting daemon into standby: ${res.exceptionOrNull()?.message}")
                }
            }

            "/engine" -> {
                if (arg.isNotBlank() && arg in listOf("auto", "antigravity", "cloud", "ollama")) {
                    val res = setDefaultEngine(config, arg)
                    if (res.isSuccess) {
                        onChunk("⚙️ **Default engine updated to:** `$arg`")
                    } else {
                        onChunk("⚠️ Failed to update engine: ${res.exceptionOrNull()?.message}")
                    }
                } else {
                    onChunk("""
                        ⚙️ **AlwaysOnAgent Execution Engines:**
                        Usage: `/engine <choice>`
                        • `/engine auto` - Antigravity CLI, then free cloud models
                        • `/engine antigravity` - Antigravity CLI only
                        • `/engine cloud` - Free cloud models, then local Ollama
                        • `/engine ollama` - Local Ollama only
                    """.trimIndent())
                }
            }

            "/video" -> {
                val quote = arg.ifBlank { null }
                val res = triggerMedia(config, "video", quote)
                if (res.isSuccess) {
                    onChunk("""
                        🎬 **3D Card-Flip Video Render Dispatched!**
                        
                        • **Quote:** "${quote ?: "Daily Affirmation"}"
                        • **Engine:** `Media Pipeline (Render Only)`
                        • **Task ID:** `[${res.getOrNull()}]`
                        
                        ⚡ Executing in background worker pool... Check Media Gallery or Web HUD when complete.
                    """.trimIndent())
                } else {
                    onChunk("⚠️ Failed to dispatch video render: ${res.exceptionOrNull()?.message}")
                }
            }

            "/tiktok" -> {
                val quote = arg.ifBlank { null }
                val res = triggerMedia(config, "tiktok", quote)
                if (res.isSuccess) {
                    onChunk("""
                        🚀 **TikTok Auto-Post Mission Dispatched!**
                        
                        • **Target Account:** `@Thevibecheckproject`
                        • **Quote:** "${quote ?: "Daily Affirmation"}"
                        • **Engine:** `Media Pipeline (Render + Auto-Post)`
                        • **Task ID:** `[${res.getOrNull()}]`
                        
                        ⚡ Uploading directly to TikTok upon completion.
                    """.trimIndent())
                } else {
                    onChunk("⚠️ Failed to dispatch TikTok post: ${res.exceptionOrNull()?.message}")
                }
            }

            "/image", "/imagine", "/draw" -> {
                if (arg.isBlank()) {
                    onChunk("🎨 **AI Image Generation:**\n\nUsage: `/image <prompt>`\nExample: `/image futuristic cyberpunk workstation with neon cyan glow, 8k`")
                } else {
                    streamAlwaysOnAgentChat(config, "You are AlwaysOnAgent AI assistant.", emptyList(), "/image $arg", onChunk)
                }
            }

            "/cancel" -> {
                if (arg.isNotBlank()) {
                    val res = cancelTask(config, arg)
                    onChunk(if (res.isSuccess) "🛑 **Task Cancelled:** `[$arg]`" else "⚠️ ${res.exceptionOrNull()?.message}")
                } else {
                    onChunk("⏹️ Aborting currently executing mission on host PC...")
                    val res = abortRunningTask(config)
                    if (res.isSuccess) {
                        val body = res.getOrNull() ?: ""
                        if (body.contains("\"idle\"") || body.contains("idle")) {
                            onChunk("ℹ️ **Daemon status: Idle.** No task was currently executing on your computer.")
                        } else {
                            onChunk("🛑 **Active Task Aborted!** Process tree terminated on computer.")
                        }
                    } else {
                        onChunk("⚠️ Failed to abort running task: ${res.exceptionOrNull()?.message}")
                    }
                }
            }

            "/retry" -> {
                if (arg.isNotBlank()) {
                    val res = retryTask(config, arg)
                    onChunk(if (res.isSuccess) "🔄 **Task Queued for Retry:** `[$arg]`" else "⚠️ ${res.exceptionOrNull()?.message}")
                } else {
                    onChunk("Usage: `/retry <task_id>`")
                }
            }

            "/memory" -> {
                val mem = getMemoryOverview(config)
                if (mem != null) {
                    val factsPreview = mem.facts.take(5).joinToString("\n") {
                        val pin = if (it.pinned) "📌 " else ""
                        "• `$pin#${it.id}` ${it.content}"
                    }
                    onChunk("""
                        🧠 **AlwaysOnAgent Memory Overview**
                        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                        ${mem.profile.ifBlank { "Profile: Adaptive profile active." }}
                        
                        📌 **Recent Memories (${mem.factsActive} total, ${mem.factsPinned} pinned):**
                        ${factsPreview.ifBlank { "• No memories stored yet." }}
                        
                        Use `/remember <fact>` to add, or `/forget <#id>` to remove.
                    """.trimIndent())
                } else {
                    onChunk("""
                        ⚠️ **Unable to retrieve memory overview from AlwaysOnAgent**
                        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                        • **Host:** `${config.getResolvedUrl()}/api/memory`
                        • Verify Web HUD is running and authorized with your HUD_AUTH_TOKEN.
                    """.trimIndent())
                }
            }

            "/remember" -> {
                if (arg.isNotBlank()) {
                    val res = addMemoryFact(config, arg)
                    if (res.isSuccess) {
                        onChunk("📌 **Saved memory (`#${res.getOrNull()}`):** \"$arg\". Stored in state.db.")
                    } else {
                        onChunk("⚠️ Couldn't save fact: ${res.exceptionOrNull()?.message}")
                    }
                } else {
                    onChunk("Usage: `/remember <something to keep>`")
                }
            }

            "/forget" -> {
                if (arg.isNotBlank()) {
                    val num = arg.removePrefix("#").toIntOrNull()
                    if (num != null) {
                        val res = deleteMemoryFact(config, num)
                        onChunk(if (res.isSuccess) "🗑️ Forgotten memory `(#$num)`." else "⚠️ Memory `#$num` not found.")
                    } else {
                        onChunk("Usage: `/forget <#id>` (e.g. `/forget #3`)")
                    }
                } else {
                    onChunk("Usage: `/forget <#id>`")
                }
            }

            "/videos", "/gallery" -> {
                val videos = getVideos(config)
                if (videos.isNotEmpty()) {
                    val preview = videos.take(5).joinToString("\n") { v ->
                        "• **${v.filename}** (${v.sizeMb} MB) — ${v.createdAt}\n  *Stream:* `${v.url}`"
                    }
                    onChunk("""
                        🎬 **Rendered Workflow Videos (${videos.size} found):**
                        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                        $preview
                        
                        💡 *Tap the 🎥 Videocam icon in the top bar to watch them right inside DeskAI!*
                    """.trimIndent())
                } else {
                    onChunk("🎬 No rendered videos found on computer yet. Use `/video <quote>` to render one!")
                }
            }

            "/hud" -> {
                onChunk("""
                    ⚡ **Mission Control Web HUD**
                    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                    • **Workstation:** `${config.serverUrl}`
                    • **API Documentation:** `${config.serverUrl}/docs`
                    • **Memory Page:** `${config.serverUrl}/memory`
                """.trimIndent())
            }

            "/help", "/start", "/briefing" -> {
                onChunk("""
                    # 🛰️ DeskAI × AlwaysOnAgent (v2.1)
                    ### Autonomous Computer Companion & Mobile Mission Control
                    
                    • **Tap `[/]` on the input bar** to open the **Command Deck** with all workstation hubs:
                      - ⚙️ **Connection Hub** (Network routing & credentials)
                      - 🎬 **3D Video Studio** (Watch 3D card-flip TikToks)
                      - 🧠 **Memory Vault** (SQLite associative knowledge)
                      - 📋 **Mission Tasks** (Active tasks & worker backlog)
                      - 🔄 **In-App Updater** (Direct GitHub OTA updates)
                    
                    ### ⚡ Interactive Slash Directives:
                    • `/status` - Current daemon state, pulse & tasks
                    • `/wake` - Wake daemon from standby (0% -> Active)
                    • `/standby` - Standby mode (0% CPU/GPU)
                    • `/video [quote]` - Render 3D Card Flip Video
                    • `/tiktok [quote]` - Render & Auto-post to TikTok
                    • `/image [prompt]` - AI Image generation
                    • `/engine <auto|antigravity|cloud|ollama>` - Select execution engine
                    • `/memory` - Inspect stored facts & profile
                    • `/remember <fact>` - Save permanent memory
                    • `/forget <#id>` - Erase memory fact
                    • `/cancel <id>` - Abort running task
                    • `/retry <id>` - Retry failed task
                    • `/hud` - Mission Control link
                    
                    💡 Or just type any question or instruction to chat directly!
                """.trimIndent())
            }

            else -> {
                onChunk("Command `$cmd` unrecognized. Type `/help` for list of commands.")
            }
        }
    }

    private suspend fun executeRealConversation(
        config: BridgeConfig,
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String,
        onChunk: suspend (String) -> Unit
    ) {
        val protocol = try {
            BridgeProtocol.valueOf(config.protocol)
        } catch (_: Exception) {
            BridgeProtocol.ALWAYSON_AGENT
        }

        when (protocol) {
            BridgeProtocol.ALWAYSON_AGENT -> streamAlwaysOnAgentChat(config, systemPrompt, history, userMessage, onChunk)
            BridgeProtocol.OPENAI_COMPATIBLE -> streamOpenAiChat(config, systemPrompt, history, userMessage, onChunk)
            BridgeProtocol.OLLAMA_NATIVE -> streamOllamaChat(config, systemPrompt, history, userMessage, onChunk)
        }
    }

    private suspend fun streamAlwaysOnAgentChat(
        config: BridgeConfig,
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String,
        onChunk: suspend (String) -> Unit
    ) {
        executeWithFailover(config) { baseUrl ->
            val payload = JSONObject().apply {
                put("message", userMessage)
                put("prompt", userMessage)
                put("model", if (config.selectedModel.isNotBlank()) config.selectedModel else "auto")
                put("engine", if (config.selectedModel.isNotBlank()) config.selectedModel else "auto")
                put("system", systemPrompt)
                val histArray = JSONArray()
                history.takeLast(10).forEach { msg ->
                    histArray.put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
                put("history", histArray)
            }

            val req = addAuth(
                Request.Builder()
                    .url("$baseUrl/api/chat")
                    .post(payload.toString().toRequestBody(jsonMediaType)),
                config
            ).build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val contentType = resp.header("Content-Type") ?: ""
                    if (contentType.contains("text/event-stream")) {
                        val reader = BufferedReader(InputStreamReader(resp.body?.byteStream() ?: return@use))
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val l = line?.trim() ?: continue
                            if (l.startsWith("data:")) {
                                val data = l.removePrefix("data:").trim()
                                if (data == "[DONE]") break
                                try {
                                    val json = JSONObject(data)
                                    val chunk = json.optString("chunk", json.optString("delta", json.optString("text", json.optString("response", ""))))
                                    if (chunk.isNotEmpty()) onChunk(chunk)
                                } catch (_: Exception) {
                                    if (data.isNotEmpty()) onChunk(data)
                                }
                            }
                        }
                    } else {
                        val body = resp.body?.string() ?: ""
                        try {
                            val json = JSONObject(body)
                            var text = json.optString("response",
                                json.optString("reply",
                                    json.optString("message",
                                        json.optString("content",
                                            json.optString("text",
                                                json.optString("result", body))))))
                            val cleanBase = baseUrl.trimEnd('/')
                            val tokenQuery = if (config.apiKey.isNotBlank()) "?token=${config.apiKey.trim()}" else ""
                            if (text.contains("](/images/")) {
                                text = text.replace("](/images/", "]($cleanBase/images/")
                                if (tokenQuery.isNotEmpty()) {
                                    text = text.replace(Regex("""(\($cleanBase/images/[^\s)]+\.(?:jpe?g|png|webp))""")) {
                                        val matched = it.value
                                        if (!matched.contains("token=")) "$matched$tokenQuery" else matched
                                    }
                                }
                            }

                            // Structured proposals from agent Phase 2
                            val proposalsArray = json.optJSONArray("proposals")
                            if (proposalsArray != null && proposalsArray.length() > 0) {
                                val firstProp = proposalsArray.getJSONObject(0)
                                val propId = firstProp.optString("id")
                                val propInstr = firstProp.optString("instruction")
                                val propReason = firstProp.optString("reason", "")
                                val propProj = if (firstProp.has("project") && !firstProp.isNull("project")) firstProp.optString("project") else null
                                lastReceivedProposal = TaskProposal(
                                    id = propId,
                                    token = propId,
                                    instruction = propInstr,
                                    reason = propReason,
                                    project = propProj
                                )
                            }

                            onChunk(text)
                        } catch (_: Exception) {
                            onChunk(body)
                        }
                    }
                    return@executeWithFailover
                } else if (resp.code == 404) {
                    // /api/chat not found, try fallback /api/ask
                    tryFallbackAskOrNotify(baseUrl, config, userMessage, onChunk)
                    return@executeWithFailover
                } else {
                    val body = resp.body?.string() ?: ""
                    onChunk("⚠️ AlwaysOnAgent error (HTTP ${resp.code}): ${body.take(300)}")
                    return@executeWithFailover
                }
            }
        }
    }

    private suspend fun tryFallbackAskOrNotify(
        baseUrl: String,
        config: BridgeConfig,
        userMessage: String,
        onChunk: suspend (String) -> Unit
    ) {
        val askPayload = JSONObject().apply {
            put("prompt", userMessage)
            put("query", userMessage)
        }
        val req = addAuth(
            Request.Builder()
                .url("$baseUrl/api/ask")
                .post(askPayload.toString().toRequestBody(jsonMediaType)),
            config
        ).build()

        try {
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    try {
                        val json = JSONObject(body)
                        val text = json.optString("response", json.optString("reply", json.optString("answer", body)))
                        onChunk(text)
                        return
                    } catch (_: Exception) {
                        onChunk(body)
                        return
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore fallback failure
        }

        // Neither /api/chat nor /api/ask is mounted on WebHUD. Give real, actionable guidance!
        onChunk("""
            ℹ️ **Web HUD Connected, but `/api/chat` is not mounted on your computer yet.**
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            Your computer's Web HUD (:8080) is online and authenticated, but it currently provides telemetry & task execution.
            
            To stream live LLM conversations (Groq, Mistral, Antigravity) directly into DeskAI:
            1. Open `REMOTE_SETUP_GUIDE.md` in the project.
            2. Add the quick `/api/chat` route to `WebHUD.py`.
            3. Restart `run_agent.bat`.
            
            ⚡ **What you can run right now on your PC:**
            • `task: $userMessage` — Queue this as an autonomous mission into your PC worker pool!
            • `/status` — View real-time daemon CPU, RAM & heartbeat
            • `/memory` — Inspect facts stored in `state.db`
            • `/video` or `/tiktok` — Render 3D Affirmation videos
        """.trimIndent())
    }

    private suspend fun streamOpenAiChat(
        config: BridgeConfig,
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String,
        onChunk: suspend (String) -> Unit
    ) {
        executeWithFailover(config) { baseUrl ->
            val messagesArray = JSONArray().apply {
                if (systemPrompt.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                }
                history.takeLast(10).forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })
            }

            val payload = JSONObject().apply {
                put("model", config.selectedModel.ifBlank { "default" })
                put("messages", messagesArray)
                put("stream", true)
                put("temperature", config.temperature)
            }

            val req = addAuth(
                Request.Builder()
                    .url("$baseUrl/v1/chat/completions")
                    .post(payload.toString().toRequestBody(jsonMediaType)),
                config
            ).build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: ""
                    onChunk("⚠️ OpenAI endpoint error (HTTP ${resp.code}): $err")
                    return@executeWithFailover
                }
                val reader = BufferedReader(InputStreamReader(resp.body?.byteStream() ?: return@use))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line?.trim() ?: continue
                    if (l.startsWith("data:")) {
                        val data = l.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        try {
                            val json = JSONObject(data)
                            val choices = json.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val delta = choices.getJSONObject(0).optJSONObject("delta")
                                val content = delta?.optString("content", "") ?: ""
                                if (content.isNotEmpty()) onChunk(content)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private suspend fun streamOllamaChat(
        config: BridgeConfig,
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String,
        onChunk: suspend (String) -> Unit
    ) {
        executeWithFailover(config) { baseUrl ->
            val messagesArray = JSONArray().apply {
                if (systemPrompt.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                }
                history.takeLast(10).forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })
            }

            val payload = JSONObject().apply {
                put("model", config.selectedModel.ifBlank { "llama3" })
                put("messages", messagesArray)
                put("stream", true)
            }

            val req = addAuth(
                Request.Builder()
                    .url("$baseUrl/api/chat")
                    .post(payload.toString().toRequestBody(jsonMediaType)),
                config
            ).build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: ""
                    onChunk("⚠️ Ollama endpoint error (HTTP ${resp.code}): $err")
                    return@executeWithFailover
                }
                val reader = BufferedReader(InputStreamReader(resp.body?.byteStream() ?: return@use))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line?.trim() ?: continue
                    try {
                        val json = JSONObject(l)
                        val msg = json.optJSONObject("message")
                        val content = msg?.optString("content", "") ?: ""
                        if (content.isNotEmpty()) onChunk(content)
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
