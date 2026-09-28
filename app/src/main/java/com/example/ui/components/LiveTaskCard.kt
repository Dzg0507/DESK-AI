package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RoseError
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun LiveTaskCard(
    taskId: String,
    onGetTask: suspend (String) -> Result<AgentTaskItem>,
    onCancelTask: (suspend (String) -> Result<String>)? = null,
    onRetryTask: (suspend (String) -> Result<String>)? = null,
    onPlayVideo: ((url: String, filename: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var taskItem by remember(taskId) { mutableStateOf<AgentTaskItem?>(null) }
    var isPolling by remember(taskId) { mutableStateOf(true) }
    var isCancelling by remember { mutableStateOf(false) }
    var cancelFeedback by remember { mutableStateOf<String?>(null) }

    // Live refresh loop: Poll every 2 seconds while active
    LaunchedEffect(taskId, isPolling) {
        if (!isPolling) return@LaunchedEffect
        while (isActive && isPolling) {
            val res = onGetTask(taskId)
            if (res.isSuccess) {
                val item = res.getOrNull()
                if (item != null) {
                    taskItem = item
                    val isTerminal = item.phase == "completed" ||
                            item.phase == "failed" ||
                            item.phase == "cancelled" ||
                            item.cancelled
                    if (isTerminal) {
                        isPolling = false
                    }
                }
            }
            delay(2000)
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
    val isCancelled = item?.cancelled == true || item?.phase == "cancelled"
    val isCompleted = item?.phase == "completed"
    val isFailed = item?.phase == "failed" && !isCancelled
    val isInProgress = item?.phase == "in_progress" && !isCancelled
    val isBacklog = (item?.phase == "backlog" || item == null) && !isCancelled

    val phaseColor = when {
        isCancelled -> Color(0xFF94A3B8)
        isCompleted -> EmeraldConnected
        isInProgress -> Color(0xFFF59E0B)
        isFailed -> RoseError
        else -> ElectricCyan
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, phaseColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
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
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Phase Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(phaseColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = when {
                            isCancelled -> "CANCELLED"
                            isCompleted -> "COMPLETED"
                            isInProgress -> "RUNNING"
                            isFailed -> "FAILED"
                            else -> "WAITING"
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = phaseColor,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Title or Prompt
            if (item != null && item.title.isNotBlank()) {
                Text(
                    text = item.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE2E8F0)
                )
            }

            // Status Text (Dynamic server label)
            val statusDisplay = item?.statusText ?: when {
                isInProgress -> "Executing on computer worker pool..."
                isCompleted -> "Mission finished successfully"
                isCancelled -> "Task was cancelled"
                isFailed -> item?.lastError ?: "Execution failed"
                else -> "Waiting to start in backlog..."
            }
            Text(
                text = statusDisplay,
                fontSize = 11.sp,
                color = if (isFailed) RoseError else Color(0xFF94A3B8),
                lineHeight = 15.sp
            )

            // Progress Bar & ETA
            if (isInProgress) {
                val prog = item?.progress
                if (prog != null) {
                    // Compute countdown
                    val parsedUpdateMs = remember(prog.updatedAt) {
                        try {
                            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                            format.timeZone = TimeZone.getDefault()
                            val cleaned = prog.updatedAt.substringBefore("+").substringBefore("Z")
                            format.parse(cleaned)?.time ?: System.currentTimeMillis()
                        } catch (_: Exception) {
                            System.currentTimeMillis()
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
                                color = Color(0xFFF59E0B)
                            )
                            Text(
                                text = "${prog.percent}% • $timeText",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF59E0B),
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (prog.detail.isNotBlank()) {
                            Text(
                                text = prog.detail,
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
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
                            color = Color(0xFFF59E0B),
                            trackColor = Color(0xFF1E293B)
                        )
                    }
                } else {
                    // Indeterminate linear progress
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFF59E0B),
                        trackColor = Color(0xFF1E293B)
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
                    trackColor = Color(0xFF1E293B)
                )
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
                        color = Color(0xFFF59E0B),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Abort Button while in_progress or backlog
                    if ((isInProgress || isBacklog) && onCancelTask != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF334155))
                                .border(1.dp, RoseError.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
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
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0284C7))
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
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, ElectricCyan, RoundedCornerShape(6.dp))
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
