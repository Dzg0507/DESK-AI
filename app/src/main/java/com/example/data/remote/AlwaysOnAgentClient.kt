package com.example.data.remote

import com.example.data.model.AgentSchedule
import com.example.data.model.AgentTaskItem
import com.example.data.model.AgentWorkProject
import com.example.data.model.BridgeConfig
import com.example.data.model.BridgeProtocol
import com.example.data.model.ChatAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.ChatQuestion
import com.example.data.model.DaemonStats
import com.example.data.model.MemoryFactItem
import com.example.data.model.MemoryOverview
import com.example.data.model.RecipeItem
import com.example.data.model.PushRegistrationResult
import com.example.data.model.ScheduleList
import com.example.data.model.SchedulePreview
import com.example.data.model.SystemLogEntry
import com.example.data.model.TaskProgress
import com.example.data.model.TaskProposal
import com.example.data.model.TaskResult
import com.example.data.model.TaskAction
import com.example.data.model.VideoItem
import com.example.data.model.ImageItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
import java.util.UUID
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

    var lastReceivedProposals: List<TaskProposal> = emptyList()
    var lastReceivedProposal: TaskProposal?
        get() = lastReceivedProposals.firstOrNull()
        set(value) {
            lastReceivedProposals = if (value != null) listOf(value) else emptyList()
        }

    var lastReceivedQuestion: ChatQuestion? = null
    var lastReceivedCanvas: String? = null      // the reply's "canvas" object, as JSON
    var lastReceivedTaskId: String? = null

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
            } catch (e: CancellationException) {
                throw e                       // the user stopped it: don't try the next address
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
        priority: String = "medium",
        idempotencyKey: String = UUID.randomUUID().toString()
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
                        .addHeader("Idempotency-Key", idempotencyKey)
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
                    } else if (resp.code == 409) {
                        var detail = "Task already finished; nothing to cancel"
                        try {
                            val j = JSONObject(body)
                            detail = j.optString("detail", detail)
                        } catch (_: Exception) {}
                        Result.failure(Exception(detail))
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
                        list.add(parseTaskJson(array.getJSONObject(i), baseUrl))
                    }
                    list
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** A draft's version numbers, oldest first (GET /api/canvas/{id}); empty if it can't be reached. */
    suspend fun fetchCanvasVersions(config: BridgeConfig, canvasId: Long): List<Int> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val req = addAuth(Request.Builder().url("$baseUrl/api/canvas/$canvasId"), config).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@executeWithFailover emptyList()
                    val arr = JSONObject(resp.body?.string() ?: "{}").optJSONArray("versions") ?: JSONArray()
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optInt("version") }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Messages the agent started (brief, notices, suggestions, questions) newer than [after], oldest first.
     *  Their push carries no text (it passes through Google), so the text comes from here. */
    suspend fun fetchInbox(config: BridgeConfig, after: Long): List<com.example.data.model.InboxMessage> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val req = addAuth(Request.Builder().url("$baseUrl/api/inbox?after=$after"), config).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@executeWithFailover emptyList()
                    val arr = JSONObject(resp.body?.string() ?: "{}").optJSONArray("messages") ?: JSONArray()
                    (0 until arr.length()).mapNotNull { i ->
                        val m = arr.optJSONObject(i) ?: return@mapNotNull null
                        com.example.data.model.InboxMessage(
                            id = m.optLong("id"),
                            kind = m.optString("kind", "notice"),
                            text = m.optString("text", ""),
                            createdAt = m.optString("created_at", ""),
                            canvasJson = m.optJSONObject("canvas")?.toString()
                        )
                    }.filter { it.text.isNotBlank() }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getTask(config: BridgeConfig, taskId: String): Result<AgentTaskItem> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val req = addAuth(Request.Builder().url("$baseUrl/api/tasks/$taskId"), config).build()
                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        val json = JSONObject(body)
                        val taskObj = json.optJSONObject("task") ?: json
                        Result.success(parseTaskJson(taskObj, baseUrl))
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: $body"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseTaskJson(item: JSONObject, baseUrl: String? = null): AgentTaskItem {
        fun optNullableString(key: String): String? {
            if (!item.has(key) || item.isNull(key)) return null
            val str = item.optString(key, "").trim()
            return if (str.isEmpty() || str.equals("null", ignoreCase = true)) null else str
        }

        val progressObj = item.optJSONObject("progress")
        val parsedProgress = if (progressObj != null) {
            TaskProgress(
                stage = progressObj.optString("stage", ""),
                label = progressObj.optString("label", ""),
                percent = progressObj.optInt("percent", 0),
                detail = progressObj.optString("detail", ""),
                etaSeconds = progressObj.optInt("eta_seconds", 0),
                updatedAt = progressObj.optString("updated_at", "")
            )
        } else null

        val resultObj = item.optJSONObject("result")
        val parsedResult = if (resultObj != null) {
            val rawUrl = resultObj.optString("url", "").trim()
            val cleanBase = baseUrl?.trimEnd('/') ?: ""
            val fullUrl = when {
                rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
                rawUrl.startsWith("/") && cleanBase.isNotEmpty() -> "$cleanBase$rawUrl"
                rawUrl.isNotBlank() && cleanBase.isNotEmpty() -> "$cleanBase/$rawUrl"
                else -> rawUrl
            }
            TaskResult(
                type = resultObj.optString("type", ""),
                filename = resultObj.optString("filename", ""),
                url = fullUrl
            )
        } else null

        val actionsList = mutableListOf<TaskAction>()
        val actionsArr = item.optJSONArray("actions")
        if (actionsArr != null) {
            val cleanBase = baseUrl?.trimEnd('/') ?: ""
            for (i in 0 until actionsArr.length()) {
                val actObj = actionsArr.optJSONObject(i) ?: continue
                val rawUrl = actObj.optString("url", "").trim()
                val fullUrl = when {
                    rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
                    rawUrl.startsWith("/") && cleanBase.isNotEmpty() -> "$cleanBase$rawUrl"
                    rawUrl.isNotBlank() && cleanBase.isNotEmpty() -> "$cleanBase/$rawUrl"
                    rawUrl.isNotBlank() -> rawUrl
                    else -> null
                }
                actionsList.add(
                    TaskAction(
                        label = actObj.optString("label", ""),
                        action = actObj.optString("action", ""),
                        url = fullUrl,
                        command = actObj.optString("command", "").takeIf { it.isNotBlank() },
                        variant = actObj.optString("variant", "secondary")
                    )
                )
            }
        }

        val isCancelled = item.optBoolean("cancelled", false) || optNullableString("phase") == "cancelled"

        return AgentTaskItem(
            id = optNullableString("id") ?: optNullableString("task_id") ?: "",
            title = optNullableString("title") ?: optNullableString("prompt") ?: "Task",
            prompt = optNullableString("prompt") ?: optNullableString("title") ?: "",
            phase = if (isCancelled) "cancelled" else (optNullableString("phase") ?: "backlog"),
            engine = optNullableString("engine") ?: "auto",
            priority = optNullableString("priority") ?: "medium",
            startedAt = optNullableString("started_at"),
            completedAt = optNullableString("completed_at"),
            outputSummary = optNullableString("output_summary"),
            lastError = optNullableString("last_error"),
            workerPid = if (item.has("worker_pid") && !item.isNull("worker_pid")) item.optInt("worker_pid") else null,
            statusText = optNullableString("status_text"),
            progress = parsedProgress,
            cancelled = isCancelled,
            result = parsedResult,
            actions = actionsList
        )
    }

    /** A task card's "post" chip (e.g. "🚀 Push live" → /api/tasks/{id}/apply): the agent's `message`, or its
     *  refusal reason (`detail`) as the failure. [path] is relative to the server, like the chip's url. */
    suspend fun postChipAction(config: BridgeConfig, path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val url = if (path.startsWith("http")) path else "$baseUrl/${path.trimStart('/')}"
                val req = addAuth(Request.Builder().url(url).post("".toRequestBody(jsonMediaType)), config).build()
                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
                    if (resp.isSuccessful) Result.success(json.optString("message", "Done."))
                    else Result.failure(Exception(json.optString("detail", "HTTP ${resp.code}")))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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

    /**
     * Uploads a file for the chat (POST /api/chat/attachments). The agent reads its text right away (OCR for
     * images can take a few seconds), so this waits for that and returns the attachment as the agent saw it.
     */
    suspend fun uploadAttachment(
        config: BridgeConfig,
        localId: String,
        name: String,
        bytes: ByteArray
    ): ChatAttachment = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val payload = JSONObject().apply {
                    put("name", name)
                    put("data_b64", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                }
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/chat/attachments")
                        .post(payload.toString().toRequestBody(jsonMediaType)),
                    config
                ).build()
                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        val a = JSONObject(body).getJSONObject("attachment")
                        val warnings = a.optJSONArray("warnings")
                        val thumbUrlRaw = a.optString("thumb_url").ifBlank { null }
                        val fullThumbUrl = if (thumbUrlRaw != null && thumbUrlRaw.startsWith("/")) {
                            "$baseUrl$thumbUrlRaw"
                        } else thumbUrlRaw
                        ChatAttachment(
                            localId = localId,
                            name = a.optString("name", name),
                            status = "ready",
                            id = a.getString("id"),
                            kind = a.optString("kind").ifBlank { null },
                            method = a.optString("method").ifBlank { null },
                            confidence = if (a.isNull("confidence") || !a.has("confidence")) null else a.optDouble("confidence"),
                            warnings = (0 until (warnings?.length() ?: 0)).map { warnings!!.getString(it) },
                            thumbUrl = fullThumbUrl,
                            thumbB64 = a.optString("thumb_b64").ifBlank { null },
                            hasThumb = a.optBoolean("has_thumb", false) || thumbUrlRaw != null
                        )
                    } else {
                        // 422: the agent's reason (unsupported type, too big, can't be read)
                        val detail = try { JSONObject(body).optString("detail", "") } catch (_: Exception) { "" }
                        ChatAttachment(localId = localId, name = name, status = "error",
                            error = detail.ifBlank { "HTTP ${resp.code}" })
                    }
                }
            }
        } catch (e: Exception) {
            ChatAttachment(localId = localId, name = name, status = "error",
                error = "Couldn't reach the agent: ${e.message ?: "connection failed"}")
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
                        val taskId = json.optString("task_id", "")
                        val msg = json.optString("message", "")
                        val proj = json.optString("project", "")
                        val returnVal = if (taskId.isNotBlank()) taskId else if (msg.isNotBlank()) msg else if (proj.isNotBlank()) "Added project '$proj'" else "success"
                        Result.success(returnVal)
                    } else if (resp.code == 404) {
                        Result.failure(Exception("EXPIRED: This proposal expired because the agent restarted."))
                    } else if (resp.code == 400) {
                        var detail = body
                        try {
                            val j = JSONObject(body)
                            detail = j.optString("detail", j.optString("message", body))
                        } catch (_: Exception) {}
                        Result.failure(Exception(detail))
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

    suspend fun addAgentWorkProject(
        config: BridgeConfig,
        name: String,
        repo: String,
        description: String = ""
    ): Result<AgentWorkProject> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val payload = JSONObject().apply {
                    put("name", name.trim())
                    put("repo", repo.trim())
                    put("description", description.trim())
                }
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/agentwork/projects")
                        .post(payload.toString().toRequestBody(jsonMediaType)),
                    config
                ).build()

                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        val json = JSONObject(body)
                        val projObj = json.optJSONObject("project") ?: json
                        Result.success(
                            AgentWorkProject(
                                name = projObj.optString("name", name),
                                description = projObj.optString("description", description),
                                repo = projObj.optString("repo", repo)
                            )
                        )
                    } else {
                        var errDetail = "HTTP ${resp.code}: $body"
                        try {
                            val j = JSONObject(body)
                            errDetail = j.optString("detail", j.optString("message", errDetail))
                        } catch (_: Exception) {}
                        Result.failure(Exception(errDetail))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dispatchAgentWorkJob(
        config: BridgeConfig,
        project: String,
        instruction: String,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val payload = JSONObject().apply {
                    put("project", project)
                    put("instruction", instruction)
                }
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/agentwork/jobs")
                        .addHeader("Idempotency-Key", idempotencyKey)
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

    // ------------------------------------------------------------------ schedules (/api/schedules)
    private fun parseSchedule(o: JSONObject): AgentSchedule = AgentSchedule(
        id = o.optInt("id"),
        name = o.optString("name", "Schedule"),
        kind = o.optString("kind", "video"),
        cron = o.optString("cron", ""),
        description = o.optString("description", o.optString("cron", "")),
        enabled = o.optBoolean("enabled", true),
        nextRunAt = o.optString("next_run_at").takeIf { it.isNotBlank() && it != "null" },
        lastRunAt = o.optString("last_run_at").takeIf { it.isNotBlank() && it != "null" },
        lastResult = o.optString("last_result").takeIf { it.isNotBlank() && it != "null" },
        runs = o.optInt("runs", 0),
        postToTiktok = o.optBoolean("post_to_tiktok", false),
        text = o.optString("text").takeIf { it.isNotBlank() && it != "null" },
        instruction = o.optString("instruction").takeIf { it.isNotBlank() && it != "null" },
        project = o.optString("project").takeIf { it.isNotBlank() && it != "null" }
    )

    /** One schedules request; the server's own error message (e.g. "must be at least 60 min apart") on failure. */
    private suspend fun scheduleCall(
        config: BridgeConfig,
        method: String,
        path: String,
        body: JSONObject? = null,
        idempotencyKey: String? = null
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val builder = Request.Builder().url("$baseUrl$path")
                val reqBody = (body ?: JSONObject()).toString().toRequestBody(jsonMediaType)
                when (method) {
                    "POST" -> builder.post(reqBody)
                    "PATCH" -> builder.patch(reqBody)
                    "DELETE" -> builder.delete()
                    else -> builder.get()
                }
                if (idempotencyKey != null) builder.addHeader("Idempotency-Key", idempotencyKey)
                httpClient.newCall(addAuth(builder, config).build()).execute().use { resp ->
                    val text = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        Result.success(if (text.isBlank()) JSONObject() else JSONObject(text))
                    } else {
                        val detail = try { JSONObject(text).optString("detail", "HTTP ${resp.code}") } catch (_: Exception) { "HTTP ${resp.code}" }
                        Result.failure(Exception(detail))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSchedules(config: BridgeConfig): Result<ScheduleList> =
        scheduleCall(config, "GET", "/api/schedules").map { json ->
            val arr = json.optJSONArray("schedules") ?: JSONArray()
            ScheduleList((0 until arr.length()).map { parseSchedule(arr.getJSONObject(it)) },
                json.optString("timezone", "the PC's time"))
        }

    suspend fun previewSchedule(config: BridgeConfig, cron: String, kind: String): Result<SchedulePreview> {
        val q = "cron=${java.net.URLEncoder.encode(cron, "UTF-8")}&kind=${java.net.URLEncoder.encode(kind, "UTF-8")}"
        return scheduleCall(config, "GET", "/api/schedules/preview?$q").map { json ->
            val runs = json.optJSONArray("next_runs") ?: JSONArray()
            SchedulePreview(
                valid = json.optBoolean("valid", false),
                description = json.optString("description", ""),
                nextRuns = (0 until runs.length()).map { runs.getString(it) },
                error = json.optString("error").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }

    suspend fun createSchedule(config: BridgeConfig, fields: JSONObject,
                               idempotencyKey: String = UUID.randomUUID().toString()): Result<AgentSchedule> =
        scheduleCall(config, "POST", "/api/schedules", fields, idempotencyKey).map { parseSchedule(it.getJSONObject("schedule")) }

    suspend fun updateSchedule(config: BridgeConfig, id: Int, fields: JSONObject): Result<AgentSchedule> =
        scheduleCall(config, "PATCH", "/api/schedules/$id", fields).map { parseSchedule(it.getJSONObject("schedule")) }

    suspend fun deleteSchedule(config: BridgeConfig, id: Int): Result<Unit> =
        scheduleCall(config, "DELETE", "/api/schedules/$id").map { }

    suspend fun runScheduleNow(config: BridgeConfig, id: Int): Result<String> =
        scheduleCall(config, "POST", "/api/schedules/$id/run").map { it.optString("result", "done") }

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
                        // The agent's own answer: "Restarting now." or, while a task runs, "Restarting once X
                        // finishes. Nothing new starts until then." (status "draining")
                        val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
                        val message = json.optString("message").ifBlank { "Restarting now." }
                        Result.success(if (json.optString("status") == "draining") "⏳ $message" else message)
                    } else {
                        Result.failure(Exception("HTTP ${resp.code}: $body"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Seconds since the agent last started (GET /api/status), or null while it can't be reached. */
    suspend fun getAgentUptime(config: BridgeConfig): Long? = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val req = addAuth(Request.Builder().url("$baseUrl/api/status"), config).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@executeWithFailover null
                    val json = JSONObject(resp.body?.string() ?: "")
                    if (json.optString("status") == "online") json.optLong("uptime_seconds", -1).takeIf { it >= 0 } else null
                }
            }
        } catch (_: Exception) {
            null
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

    suspend fun triggerMedia(
        config: BridgeConfig,
        action: String,
        quote: String?,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val payload = JSONObject().apply {
                    put("action", action)
                    if (quote != null) put("quote", quote)
                }
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/trigger_media")
                        .addHeader("Idempotency-Key", idempotencyKey)
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

    suspend fun getImages(config: BridgeConfig): List<ImageItem> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                try {
                    val url = "$baseUrl/api/images"
                    val req = addAuth(Request.Builder().url(url), config).build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use emptyList<ImageItem>()
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val array = json.optJSONArray("images") ?: return@use emptyList<ImageItem>()
                        val list = mutableListOf<ImageItem>()
                        val cleanBase = baseUrl.trimEnd('/')
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val fname = obj.optString("filename", "")
                            val rawPath = obj.optString("url", "").trim()
                            val fullUrl = when {
                                rawPath.startsWith("http://") || rawPath.startsWith("https://") -> rawPath
                                rawPath.startsWith("/") -> "$cleanBase$rawPath"
                                rawPath.isNotBlank() -> "$cleanBase/$rawPath"
                                fname.isNotBlank() -> "$cleanBase/images/$fname"
                                else -> ""
                            }
                            val authedUrl = if (config.apiKey.isNotBlank() && !fullUrl.contains("token=")) {
                                "$fullUrl${if (fullUrl.contains("?")) "&" else "?"}token=${config.apiKey.trim()}"
                            } else {
                                fullUrl
                            }
                            if (fname.isNotBlank()) {
                                list.add(
                                    ImageItem(
                                        filename = fname,
                                        sizeMb = obj.optDouble("size_mb", 0.0),
                                        createdAt = obj.optString("created_at", ""),
                                        url = authedUrl
                                    )
                                )
                            }
                        }
                        list
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun deleteImage(config: BridgeConfig, filename: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                try {
                    val url = "$baseUrl/api/images/$filename"
                    val req = addAuth(Request.Builder().url(url).delete(), config).build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) Result.success(true)
                        else Result.failure(Exception("HTTP ${resp.code}"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            } ?: Result.failure(Exception("Connection failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadVideo(
        config: BridgeConfig,
        videoUrl: String,
        destinationFile: java.io.File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<java.io.File> = withContext(Dispatchers.IO) {
        try {
            val cleanBase = config.getResolvedUrl().trimEnd('/')
            val resolvedUrl = when {
                videoUrl.startsWith("http://") || videoUrl.startsWith("https://") -> videoUrl
                videoUrl.startsWith("/") -> "$cleanBase$videoUrl"
                videoUrl.isNotBlank() -> "$cleanBase/$videoUrl"
                else -> videoUrl
            }
            val reqBuilder = Request.Builder().url(resolvedUrl)
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
                                    updatedAt = f.optString("updated_at", ""),
                                    source = f.optString("source", ""),
                                    evidence = f.optString("evidence", "").takeIf { it.isNotBlank() && it != "null" },
                                    sourceAt = f.optJSONObject("source_message")?.optString("at"),
                                    sourceExcerpt = f.optJSONObject("source_message")?.optString("excerpt")
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
                        facts = factList,
                        recipes = fetchRecipes(baseUrl, config)
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** The agent's learned recipes; empty from an agent that doesn't have them yet. */
    private fun fetchRecipes(baseUrl: String, config: BridgeConfig): List<RecipeItem> = try {
        val req = addAuth(Request.Builder().url("$baseUrl/api/memory/procedures"), config).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONObject(resp.body?.string() ?: "").optJSONArray("procedures") ?: return emptyList()
            (0 until arr.length()).map { i ->
                val p = arr.getJSONObject(i)
                RecipeItem(
                    id = p.optInt("id"),
                    area = p.optString("area", "general"),
                    whenText = p.optString("when"),
                    doText = p.optString("do"),
                    source = p.optString("source", ""),
                    evidence = p.optString("evidence", "").takeIf { it.isNotBlank() && it != "null" },
                    status = p.optString("status", "active"),
                    pinned = p.optBoolean("pinned", false),
                    uses = p.optInt("uses"),
                    helped = p.optInt("helped"),
                    failed = p.optInt("failed")
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** PATCH a recipe: pin or unpin, or bring back a retired one (status "active"). */
    suspend fun updateRecipe(config: BridgeConfig, id: Int, pinned: Boolean? = null, status: String? = null): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                executeWithFailover(config) { baseUrl ->
                    val payload = JSONObject().apply {
                        pinned?.let { put("pinned", it) }
                        status?.let { put("status", it) }
                    }
                    val req = addAuth(
                        Request.Builder().url("$baseUrl/api/memory/procedures/$id")
                            .patch(payload.toString().toRequestBody(jsonMediaType)),
                        config
                    ).build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("HTTP ${resp.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun deleteRecipe(config: BridgeConfig, id: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val req = addAuth(Request.Builder().url("$baseUrl/api/memory/procedures/$id").delete(), config).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) Result.success(true) else Result.failure(Exception("HTTP ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
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
        userMessage: String,
        attachmentIds: List<String> = emptyList(),
        turnId: String? = null
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
                val tid = res.getOrNull()
                lastReceivedTaskId = tid
                emit("📥 **Task Enqueued in AlwaysOnAgent Worker Pool**\n\n• **ID:** `[$tid]`\n• **Title:** \"$title\"\n• **Status:** `Queued in Backlog ⏳`\n\nThe background daemon will execute this mission automatically!")
            } else {
                emit("⚠️ Failed to enqueue task on computer: ${res.exceptionOrNull()?.message}")
            }
            return@flow
        }

        // 3. Real conversational chat with the computer assistant
        executeRealConversation(config, systemPrompt, history, trimmed, attachmentIds, turnId) { chunk ->
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
                    val tid = res.getOrNull()
                    lastReceivedTaskId = tid
                    onChunk("🎬 Rendering your video")
                } else {
                    onChunk("⚠️ Failed to dispatch video render: ${res.exceptionOrNull()?.message}")
                }
            }

            "/tiktok" -> {
                val quote = arg.ifBlank { null }
                val res = triggerMedia(config, "tiktok", quote)
                if (res.isSuccess) {
                    val tid = res.getOrNull()
                    lastReceivedTaskId = tid
                    onChunk("🚀 Rendering & publishing to TikTok")
                } else {
                    onChunk("⚠️ Failed to dispatch TikTok post: ${res.exceptionOrNull()?.message}")
                }
            }

            "/image", "/imagine", "/draw" -> {
                if (arg.isBlank()) {
                    onChunk("🎨 **AI Image Generation:**\n\nUsage: `/image <prompt>`\nExample: `/image futuristic cyberpunk workstation with neon cyan glow, 8k`")
                } else {
                    streamAlwaysOnAgentChat(config, "You are AlwaysOnAgent AI assistant.", emptyList(), "/image $arg", emptyList(), null, onChunk)
                }
            }

            "/cancel" -> {
                if (arg.isNotBlank()) {
                    val res = cancelTask(config, arg)
                    if (res.isSuccess) {
                        lastReceivedTaskId = arg
                        onChunk("🛑 **Task Cancelled:** `[$arg]`")
                    } else {
                        onChunk("⚠️ ${res.exceptionOrNull()?.message}")
                    }
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
                    if (res.isSuccess) {
                        lastReceivedTaskId = arg
                        onChunk("🔄 **Task Queued for Retry:** `[$arg]`")
                    } else {
                        onChunk("⚠️ ${res.exceptionOrNull()?.message}")
                    }
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
                    ⚡ **AlwaysOnAgent server**
                    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                    • **Address:** `${config.serverUrl}`
                    • **API Documentation:** `${config.serverUrl}/docs`
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
                    • `/hud` - the server's address
                    
                    💡 Or just type any question or instruction to chat directly!
                """.trimIndent())
            }

            else -> {
                executeRealConversation(config, "You are AlwaysOnAgent AI assistant.", emptyList(), cmdText, onChunk = onChunk)
            }
        }
    }

    private suspend fun executeRealConversation(
        config: BridgeConfig,
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String,
        attachmentIds: List<String> = emptyList(),
        turnId: String? = null,
        onChunk: suspend (String) -> Unit
    ) {
        val protocol = try {
            BridgeProtocol.valueOf(config.protocol)
        } catch (_: Exception) {
            BridgeProtocol.ALWAYSON_AGENT
        }

        when (protocol) {
            BridgeProtocol.ALWAYSON_AGENT -> streamAlwaysOnAgentChat(config, systemPrompt, history, userMessage, attachmentIds, turnId, onChunk)
            BridgeProtocol.OPENAI_COMPATIBLE -> streamOpenAiChat(config, systemPrompt, history, userMessage, onChunk)
            BridgeProtocol.OLLAMA_NATIVE -> streamOllamaChat(config, systemPrompt, history, userMessage, onChunk)
        }
    }

    private suspend fun awaitResponse(call: Call): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) = cont.resume(response)
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        })
    }

    /**
     * Tells the agent the user stopped this message's reply (POST /api/chat/cancel): when the reply is ready the
     * agent drops it instead of keeping it in its memory.
     */
    suspend fun cancelChat(config: BridgeConfig, turnId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            executeWithFailover(config) { baseUrl ->
                val req = addAuth(
                    Request.Builder()
                        .url("$baseUrl/api/chat/cancel")
                        .post(JSONObject().put("turn_id", turnId).toString().toRequestBody(jsonMediaType)),
                    config
                ).build()
                httpClient.newCall(req).execute().use { it.isSuccessful }
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun streamAlwaysOnAgentChat(
        config: BridgeConfig,
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String,
        attachmentIds: List<String>,
        turnId: String?,
        onChunk: suspend (String) -> Unit
    ) {
        executeWithFailover(config) { baseUrl ->
            val payload = JSONObject().apply {
                put("message", userMessage)
                if (turnId != null) put("turn_id", turnId)
                if (attachmentIds.isNotEmpty()) put("attachments", JSONArray(attachmentIds))
                put("prompt", userMessage)
                put("model", if (config.selectedModel.isNotBlank()) config.selectedModel else "auto")
                put("engine", if (config.selectedModel.isNotBlank()) config.selectedModel else "auto")
                put("system", systemPrompt)
                val histArray = JSONArray()
                history.takeLast(10).forEach { msg ->
                    histArray.put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.historyText())
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

            // Cancellable: Stop aborts the request (a blocking execute() kept waiting for the whole reply)
            awaitResponse(httpClient.newCall(req)).use { resp ->
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
                                    val tid = if (json.has("task_id") && !json.isNull("task_id")) {
                                        json.optString("task_id")
                                    } else if (json.has("taskId") && !json.isNull("taskId")) {
                                        json.optString("taskId")
                                    } else {
                                        json.optJSONObject("task")?.optString("id")
                                    }
                                    if (!tid.isNullOrBlank()) {
                                        lastReceivedTaskId = tid
                                    }
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

                            // Structured proposals from agent Phase 2 (supports multiple suggestions)
                            val proposalsArray = json.optJSONArray("proposals")
                            val parsedProposals = mutableListOf<TaskProposal>()
                            if (proposalsArray != null && proposalsArray.length() > 0) {
                                for (i in 0 until proposalsArray.length()) {
                                    val propObj = proposalsArray.getJSONObject(i)
                                    val propId = propObj.optString("id")
                                    val propInstr = propObj.optString("instruction")
                                    val propReason = propObj.optString("reason", "")
                                    val propProj = if (propObj.has("project") && !propObj.isNull("project")) propObj.optString("project") else null
                                    val propKind = propObj.optString("kind", "task")
                                    if (propInstr.isNotBlank()) {
                                        parsedProposals.add(
                                            TaskProposal(
                                                id = propId,
                                                token = propId,
                                                instruction = propInstr,
                                                reason = propReason,
                                                project = propProj,
                                                isLocal = false,
                                                state = "pending",
                                                kind = propKind
                                            )
                                        )
                                    }
                                }
                            }
                            lastReceivedProposals = parsedProposals

                            // Section 7.6 Multiple-choice questions
                            val questionObj = json.optJSONObject("question")
                            if (questionObj != null) {
                                val qId = questionObj.optString("id")
                                val qText = questionObj.optString("text")
                                val qOptsArr = questionObj.optJSONArray("options")
                                val optsList = mutableListOf<String>()
                                if (qOptsArr != null) {
                                    for (i in 0 until qOptsArr.length()) {
                                        optsList.add(qOptsArr.getString(i))
                                    }
                                }
                                val allowOther = questionObj.optBoolean("allow_other", true)
                                lastReceivedQuestion = ChatQuestion(
                                    id = qId,
                                    text = qText,
                                    options = optsList,
                                    allowOther = allowOther
                                )
                            } else {
                                lastReceivedQuestion = null
                            }

                            // A draft this reply wrote or changed: the bubble shows a card that opens it
                            lastReceivedCanvas = json.optJSONObject("canvas")?.toString()

                            // Read JSON field task_id for chat-created tasks (e.g. /task, /cancel)
                            val taskId = if (json.has("task_id") && !json.isNull("task_id")) {
                                json.optString("task_id")
                            } else if (json.has("taskId") && !json.isNull("taskId")) {
                                json.optString("taskId")
                            } else {
                                json.optJSONObject("task")?.optString("id")
                            }
                            if (!taskId.isNullOrBlank()) {
                                lastReceivedTaskId = taskId
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
