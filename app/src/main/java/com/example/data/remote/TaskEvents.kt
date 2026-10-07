package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call

/**
 * Live task events from the agent (GET /api/events, IDEAS #26) while the app is in the foreground.
 *
 * MainActivity calls [start] in onStart and [stop] in onStop. While the stream is up, task cards and the status
 * bar wait for an event instead of polling; when it's down (Mini unreachable, an agent without /api/events) they
 * poll exactly as before, and the stream is retried with backoff. Push notifications (FCM) still cover the app
 * being closed. An event is only a nudge: the card then fetches GET /api/tasks/{id} as it always has.
 */
object TaskEvents {
    private const val TAG = "TaskEvents"
    private const val ANY = "*"
    private const val MIN_BACKOFF_MS = 2_000L
    private const val MAX_BACKOFF_MS = 60_000L
    private const val SAFETY_REFRESH_MS = 30_000L   // refresh anyway this often, in case an event was missed
    private const val MIN_FETCH_GAP_MS = 1_000L     // progress events can be frequent: at most one fetch a second

    private val _connected = MutableStateFlow(false)
    /** True while the events stream is open. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    // task id -> how many events it has had; ANY counts every event
    private val _changes = MutableStateFlow<Map<String, Long>>(emptyMap())
    // Bumped on every (re)connect: events missed while disconnected aren't replayed, so everything refreshes
    private val _reconnects = MutableStateFlow(0L)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = AlwaysOnAgentClient()
    private var job: Job? = null
    @Volatile private var activeCall: Call? = null

    /** Stamp to pass to [awaitChange]; take it *before* fetching, so an event during the fetch isn't missed. */
    fun stamp(taskId: String): Pair<Long, Long> = _reconnects.value to (_changes.value[taskId] ?: 0L)

    /** Stamp for "any task changed" (the status bar and task list). */
    fun stampAny(): Pair<Long, Long> = stamp(ANY)

    /**
     * Waits until [taskId] has changed since [stamp], the stream drops, or [maxWaitMs] passes. Without a stream it
     * just waits [pollMs], the old polling. Never returns sooner than [MIN_FETCH_GAP_MS] after [lastFetchAt].
     */
    suspend fun awaitChange(
        taskId: String,
        stamp: Pair<Long, Long>,
        pollMs: Long,
        lastFetchAt: Long = 0L,
        maxWaitMs: Long = SAFETY_REFRESH_MS
    ) {
        if (!_connected.value) {
            delay(pollMs)
            return
        }
        withTimeoutOrNull(maxWaitMs) {
            combine(_changes, _reconnects, _connected) { changes, reconnects, up ->
                !up || reconnects != stamp.first || (changes[taskId] ?: 0L) != stamp.second
            }.first { it }
        }
        val gap = System.currentTimeMillis() - lastFetchAt
        if (gap < MIN_FETCH_GAP_MS) delay(MIN_FETCH_GAP_MS - gap)
    }

    /** Any task changed (the status bar and task list): waits at most [pollMs] either way. */
    suspend fun awaitAnyChange(stamp: Pair<Long, Long>, pollMs: Long, lastFetchAt: Long = 0L) =
        awaitChange(ANY, stamp, pollMs, lastFetchAt, maxWaitMs = pollMs)

    @Synchronized
    fun start(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = scope.launch {
            val repository = ChatRepository(app, AppDatabase.getDatabase(app))
            var backoff = MIN_BACKOFF_MS
            while (isActive) {
                try {
                    val config = repository.getActiveConfig()
                    client.streamTaskEvents(
                        config,
                        onCall = { activeCall = it },
                        onOpen = {
                            _connected.value = true
                            _reconnects.value = _reconnects.value + 1
                            backoff = MIN_BACKOFF_MS
                        },
                        onEvent = { taskId, _ -> bump(taskId) }
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d(TAG, "events stream down: ${e.message}")
                } finally {
                    _connected.value = false
                    activeCall = null
                }
                delay(backoff)                  // polling covers the gap meanwhile
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        activeCall?.cancel()                    // unblocks the reader at once instead of at the next ping
        activeCall = null
        _connected.value = false
    }

    private fun bump(taskId: String) {
        val current = _changes.value
        // Bounded: a long session sees many tasks; forgetting old ones only costs those cards one extra refresh
        val base = if (current.size > 200) current.filterKeys { it == ANY } else current
        _changes.value = base + mapOf(
            taskId to (current[taskId] ?: 0L) + 1,
            ANY to (current[ANY] ?: 0L) + 1
        )
    }
}
