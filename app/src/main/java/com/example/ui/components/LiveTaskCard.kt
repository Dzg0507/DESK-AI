package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.Brush
import com.example.ui.theme.DeskShapes
import com.example.ui.theme.Slate300
import com.example.ui.theme.NeonIndigo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AgentTaskItem
import com.example.data.model.TaskAction
import com.example.data.remote.TaskEvents
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RoseError
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import com.example.ui.theme.AmberPending
import com.example.ui.theme.ElectricCyanGlow
import com.example.ui.theme.Sky300
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

@Composable
fun LiveTaskCard(
    taskId: String,
    onGetTask: suspend (String) -> Result<AgentTaskItem>,
    onCancelTask: (suspend (String) -> Result<String>)? = null,
    onRetryTask: (suspend (String) -> Result<String>)? = null,
    onPlayVideo: ((url: String, filename: String) -> Unit)? = null,
    onActionClick: ((TaskAction) -> Unit)? = null,
    onWatch: ((String) -> Unit)? = null,
    // Tapping the card (not its buttons) opens the task's details, the same sheet as tapping it in Missions
    onOpenDetails: ((AgentTaskItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var taskItem by remember(taskId) { mutableStateOf<AgentTaskItem?>(null) }
    var isPolling by remember(taskId) { mutableStateOf(true) }
    var isCancelling by remember { mutableStateOf(false) }
    var cancelFeedback by remember { mutableStateOf<String?>(null) }

    // Live refresh loop: refresh when the agent says this task changed (/api/events), or every 2 seconds while
    // the events stream is down; stop on 404, backoff on errors
    LaunchedEffect(taskId, isPolling) {
        if (!isPolling) return@LaunchedEffect
        var consecutiveErrors = 0
        while (isActive && isPolling) {
            val stamp = TaskEvents.stamp(taskId)          // before fetching, so a change during the fetch counts
            val fetchedAt = System.currentTimeMillis()
            val res = onGetTask(taskId)
            if (res.isSuccess) {
                consecutiveErrors = 0
                val item = res.getOrNull()
                if (item != null) {
                    taskItem = item
                    val isTerminal = item.phase == "completed" ||
                            item.phase == "failed" ||
                            item.phase == "cancelled" ||
                            item.cancelled
                    if (isTerminal) {
                        isPolling = false
                        break
                    }
                }
                TaskEvents.awaitChange(taskId, stamp, pollMs = 2000, lastFetchAt = fetchedAt)
            } else {
                val err = res.exceptionOrNull()?.message ?: ""
                if (err.contains("404")) {
                    isPolling = false
                    break
                }
                consecutiveErrors++
                val backoffMs = (2000L * consecutiveErrors).coerceAtMost(10000L)
                delay(backoffMs)
            }
        }
    }

    // Local ETA Countdown ticker (1 second precision between server updates)
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(taskId, taskItem?.progress) {
        while (isActive && taskItem?.phase == "in_progress") {
            currentTimeMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    val item = taskItem
    val isNotFound = item == null && !isPolling
    val isCancelled = item?.cancelled == true || item?.phase == "cancelled"
    val isCompleted = item?.phase == "completed"
    val isFailed = (item?.phase == "failed" && !isCancelled) || isNotFound
    val isInProgress = item?.phase == "in_progress" && !isCancelled
    val isBacklog = (item?.phase == "backlog" || item == null) && !isCancelled && !isNotFound

    val phaseColor = when {
        isNotFound -> Slate400
        isCancelled -> Slate400
        isCompleted -> EmeraldConnected
        isInProgress -> AmberPending
        isFailed -> RoseError
        else -> ElectricCyan
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(DeskShapes.card)
            .background(Brush.verticalGradient(listOf(phaseColor.copy(alpha = 0.07f), Slate900)))
            .border(1.dp, phaseColor.copy(alpha = 0.5f), DeskShapes.card)
            .then(if (onOpenDetails != null && item != null) Modifier.clickable { onOpenDetails(item) } else Modifier)
            .animateContentSize()
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row: Task ID & Phase Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(phaseColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isCompleted -> Icons.Default.CheckCircle
                                isCancelled -> Icons.Default.Cancel
                                isFailed -> Icons.Default.Error
                                isInProgress -> Icons.Default.PlayArrow
                                else -> Icons.Default.HourglassEmpty
                            },
                            contentDescription = null,
                            tint = phaseColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TASK #${taskId.take(12)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate300,
                        letterSpacing = 0.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Phase Badge
                StatusPill(
                    text = when {
                        isNotFound -> "Not found (404)"
                        isCancelled -> "Cancelled"
                        isCompleted -> "Completed"
                        isInProgress -> "Running"
                        isFailed -> "Failed"
                        else -> "Waiting"
                    },
                    color = phaseColor
                )
            }

            // Title or Prompt
            if (item != null && item.title.isNotBlank()) {
                Text(
                    text = item.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate200
                )
            }

            // Status Text (Dynamic server label)
            val statusDisplay = item?.statusText ?: when {
                isNotFound -> "Task no longer exists on computer (404)."
                isInProgress -> "Executing on computer worker pool..."
                isCompleted -> "Mission finished successfully"
                isCancelled -> "Task was cancelled"
                isFailed -> item?.lastError ?: "Execution failed"
                else -> "Waiting to start in backlog..."
            }
            Text(
                text = statusDisplay,
                fontSize = 11.sp,
                color = if (isFailed) RoseError else Slate400,
                lineHeight = 15.sp
            )

            // What the task produced (the weekly numbers, a computer task's answer...). Until 2026-10-06 a finished
            // task showed only "Completed", so reports were never seen.
            val summary = item?.outputSummary?.trim()
            if ((isCompleted || isFailed) && !summary.isNullOrBlank() && summary != statusDisplay) {
                var expanded by remember(item?.id) { mutableStateOf(false) }
                val long = summary.lines().size > 24 || summary.length > 1200
                Spacer(modifier = Modifier.height(6.dp))
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text = summary,
                        fontSize = 12.sp,
                        color = Slate200,
                        lineHeight = 17.sp,
                        maxLines = if (expanded || !long) Int.MAX_VALUE else 24,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (long) {
                    Text(
                        text = if (expanded) "Show less" else "Show all",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Sky300,
                        modifier = Modifier.clickable { expanded = !expanded }.padding(top = 4.dp)
                    )
                }
            }

            // Progress Bar & ETA
            if (isInProgress) {
                val prog = item?.progress
                if (prog != null) {
                    // Compute countdown using timezone-offset aware parser
                    val parsedUpdateMs = remember(prog.updatedAt) {
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                java.time.OffsetDateTime.parse(prog.updatedAt).toInstant().toEpochMilli()
                            } else {
                                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                                format.parse(prog.updatedAt)?.time ?: System.currentTimeMillis()
                            }
                        } catch (_: Exception) {
                            try {
                                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                                format.timeZone = TimeZone.getTimeZone("UTC")
                                val cleaned = prog.updatedAt.substringBefore("+").substringBefore("Z")
                                format.parse(cleaned)?.time ?: System.currentTimeMillis()
                            } catch (_: Exception) {
                                System.currentTimeMillis()
                            }
                        }
                    }
                    val elapsedSec = ((currentTimeMs - parsedUpdateMs) / 1000).coerceAtLeast(0)
                    val remainingSec = (prog.etaSeconds - elapsedSec).coerceAtLeast(0)

                    val timeText = when {
                        remainingSec <= 0 -> "almost done"
                        remainingSec < 60 -> "$remainingSec s left"
                        else -> {
                            val m = remainingSec / 60
                            val s = remainingSec % 60
                            "about $m min $s s left"
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = prog.label.ifBlank { "Processing" },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberPending
                            )
                            Text(
                                text = "${prog.percent}% • $timeText",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AmberPending,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (prog.detail.isNotBlank()) {
                            Text(
                                text = prog.detail,
                                fontSize = 10.sp,
                                color = Slate400
                            )
                        }

                        val animatedPercent by animateFloatAsState(
                            targetValue = (prog.percent.coerceIn(0, 100)) / 100f,
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                            label = "live_task_progress"
                        )
                        LinearProgressIndicator(
                            progress = { animatedPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.5.dp)),
                            color = AmberPending,
                            trackColor = Slate800
                        )
                    }
                } else {
                    // Indeterminate linear progress
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = AmberPending,
                        trackColor = Slate800
                    )
                }
            } else if (isCompleted) {
                LinearProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp)),
                    color = EmeraldConnected,
                    trackColor = Slate800
                )
            }

            // Contextual Action Chips (Preview Diff, Apply Update, Discuss Blockers, Post to TikTok, etc.)
            val taskResult = item?.result
            val contextualActions = remember(item?.actions, isCompleted, isInProgress, isBacklog, isFailed, taskResult) {
                item?.actions?.filter { act ->
                    val isCancel = act.action == "cancel" || act.label.contains("Abort", ignoreCase = true)
                    val isRetry = act.action == "retry" || act.label.contains("Retry", ignoreCase = true)
                    val isStreamDuplicate = (act.action == "stream" || act.label.contains("Watch", ignoreCase = true) || act.label.contains("Play", ignoreCase = true)) &&
                            (isCompleted && taskResult != null && taskResult.url.isNotBlank())
                    !isCancel && !isRetry && !isStreamDuplicate
                } ?: emptyList()
            }

            if (contextualActions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    contextualActions.forEach { act ->
                        val chipBg = when (act.variant) {
                            "primary" -> ElectricCyan.copy(alpha = 0.18f)
                            "danger" -> RoseError.copy(alpha = 0.18f)
                            else -> Slate800
                        }
                        val chipBorder = when (act.variant) {
                            "primary" -> ElectricCyan.copy(alpha = 0.8f)
                            "danger" -> RoseError.copy(alpha = 0.8f)
                            else -> Slate700
                        }
                        val chipTextColor = when (act.variant) {
                            "primary" -> ElectricCyan
                            "danger" -> RoseError
                            else -> Slate200
                        }

                        Box(
                            modifier = Modifier
                                .clip(DeskShapes.chip)
                                .background(chipBg)
                                .border(1.dp, chipBorder, DeskShapes.chip)
                                .clickable {
                                    if (act.action == "stream" && act.url != null && onPlayVideo != null) {
                                        onPlayVideo(act.url, taskResult?.filename?.ifBlank { "Stream" } ?: "Stream")
                                    } else {
                                        onActionClick?.invoke(act)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = act.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = chipTextColor
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Action Buttons Row (Abort while running, Play video when done, Retry on fail)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (cancelFeedback != null) {
                    Text(
                        text = cancelFeedback!!,
                        fontSize = 11.sp,
                        color = AmberPending,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // View only: watch a computer task on the Mini's screen without taking control (2026-10-10,
                    // the owner's ask). Opens Take over in View only, landscape; the task keeps running.
                    if ((isInProgress || isBacklog) && item?.engine == "computer" && onWatch != null) {
                        Box(
                            modifier = Modifier
                                .clip(DeskShapes.chip)
                                .background(ElectricCyan.copy(alpha = 0.12f))
                                .border(1.dp, ElectricCyan.copy(alpha = 0.6f), DeskShapes.chip)
                                .clickable { onWatch(taskId) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = "View only",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "View only", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElectricCyan)
                            }
                        }
                    }

                    // Abort Button while in_progress or backlog
                    if ((isInProgress || isBacklog) && onCancelTask != null) {
                        Box(
                            modifier = Modifier
                                .clip(DeskShapes.chip)
                                .background(RoseError.copy(alpha = 0.12f))
                                .border(1.dp, RoseError.copy(alpha = 0.6f), DeskShapes.chip)
                                .clickable(enabled = !isCancelling) {
                                    scope.launch {
                                        isCancelling = true
                                        val res = onCancelTask(taskId)
                                        isCancelling = false
                                        if (res.isSuccess) {
                                            cancelFeedback = "Cancelled"
                                            isPolling = true // Refresh once to confirm cancellation
                                        } else {
                                            cancelFeedback = res.exceptionOrNull()?.message ?: "Cancel failed"
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Abort",
                                    tint = RoseError,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isCancelling) "Aborting..." else "Abort Task",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RoseError
                                )
                            }
                        }
                    }

                    // Play Video Button when completed with video result
                    val result = item?.result
                    if (isCompleted && result != null && result.url.isNotBlank() && onPlayVideo != null) {
                        Box(
                            modifier = Modifier
                                .clip(DeskShapes.chip)
                                .background(Brush.horizontalGradient(listOf(ElectricCyanGlow, NeonIndigo)))
                                .clickable {
                                    onPlayVideo(result.url, result.filename.ifBlank { "Rendered Video" })
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = "Play Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "▶ Play Video",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Retry Button on failure
                    if (isFailed && onRetryTask != null) {
                        Box(
                            modifier = Modifier
                                .clip(DeskShapes.chip)
                                .background(ElectricCyan.copy(alpha = 0.10f))
                                .border(1.dp, ElectricCyan, DeskShapes.chip)
                                .clickable {
                                    scope.launch {
                                        val res = onRetryTask(taskId)
                                        if (res.isSuccess) {
                                            isPolling = true
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Retry Task",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
