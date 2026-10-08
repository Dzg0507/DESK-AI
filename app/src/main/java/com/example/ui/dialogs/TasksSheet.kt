package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.Dialog
import com.example.data.model.AgentTaskItem
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RoseError
import kotlinx.coroutines.launch
import com.example.ui.theme.AmberPending
import com.example.ui.theme.ElectricCyanGlow
import com.example.ui.theme.Sky300
import com.example.ui.theme.Sky700
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.components.deskSheet
import com.example.ui.components.deskCard

@Composable
fun TasksSheet(
    onDismiss: () -> Unit,
    initialTaskId: String? = null,
    liveTasks: List<AgentTaskItem> = emptyList(),
    activeTask: AgentTaskItem? = null,
    onRefreshTasks: suspend () -> List<AgentTaskItem> = { emptyList() },
    onDispatchTask: suspend (title: String, prompt: String, engine: String) -> Result<String>,
    onCancelTask: suspend (taskId: String) -> Result<String>,
    onAbortRunningTask: suspend () -> Result<String>,
    onRetryTask: suspend (taskId: String) -> Result<String>,
    // Tapping a mission opens its details (TaskDetailsSheet)
    serverBaseUrl: String = "",
    authToken: String = "",
    onGetTask: (suspend (String) -> Result<AgentTaskItem>)? = null,
    onDownloadVideo: (suspend (url: String, destinationFile: java.io.File, onProgress: (Long, Long) -> Unit) -> Result<java.io.File>)? = null,
    onPublishVideo: (suspend (filename: String) -> Result<String>)? = null
) {
    val scope = rememberCoroutineScope()
    var detailsTask by remember { mutableStateOf<AgentTaskItem?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var taskTitle by remember { mutableStateOf("") }
    var taskPrompt by remember { mutableStateOf("") }
    var engine by remember { mutableStateOf("auto") }
    var isRefreshing by remember { mutableStateOf(false) }

    // Start with live SSE tasks if present, otherwise empty
    var tasks by remember { mutableStateOf(liveTasks) }

    // Keep tasks in sync with incoming SSE updates
    LaunchedEffect(liveTasks) {
        if (liveTasks.isNotEmpty()) {
            tasks = liveTasks
        }
    }

    // Refresh from GET /api/tasks?limit=50 upon opening
    LaunchedEffect(Unit) {
        isRefreshing = true
        try {
            val fetched = onRefreshTasks()
            if (fetched.isNotEmpty()) {
                tasks = fetched
            }
        } catch (_: Exception) {}
        finally {
            isRefreshing = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .deskSheet()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Task,
                            contentDescription = "Task Matrix",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AlwaysOnAgent Missions",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isRefreshing = true
                                    try {
                                        val f = onRefreshTasks()
                                        if (f.isNotEmpty()) tasks = f
                                    } catch (_: Exception) {}
                                    finally {
                                        isRefreshing = false
                                    }
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ElectricCyan, strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = ElectricCyan, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (!initialTaskId.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Sky700.copy(alpha = 0.25f))
                            .border(1.dp, ElectricCyanGlow.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔔 Opened from Notification: ", fontSize = 11.sp, color = Sky300)
                        Text(initialTaskId, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Active Running Task Banner
                val runningTask = activeTask ?: tasks.firstOrNull { it.phase == "in_progress" }
                if (runningTask != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Slate800)
                            .border(1.dp, AmberPending, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = AmberPending, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "LIVE ACTIVE TASK",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberPending,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(RoseError.copy(alpha = 0.2f))
                                        .border(1.dp, RoseError.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .clickable {
                                            scope.launch {
                                                onAbortRunningTask()
                                                val f = onRefreshTasks()
                                                if (f.isNotEmpty()) tasks = f
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "ABORT PROCESS ⏹",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RoseError,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = runningTask.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Toggle Quick Dispatch Form
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCreating) Slate800 else ElectricCyanGlow)
                        .clickable { isCreating = !isCreating }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isCreating) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = "New Mission",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isCreating) "Hide Mission Dispatch Form" else "Quick-Dispatch New Mission",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                if (isCreating) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = taskTitle,
                        onValueChange = { taskTitle = it },
                        placeholder = { Text("Title (e.g. Audit auth middleware...)", color = Slate500, fontSize = 12.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Slate800,
                            unfocusedContainerColor = Slate800,
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = Slate700
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = taskPrompt,
                        onValueChange = { taskPrompt = it },
                        placeholder = { Text("Mission instructions and context...", color = Slate500, fontSize = 12.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Slate800,
                            unfocusedContainerColor = Slate800,
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = Slate700
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmeraldConnected)
                            .clickable {
                                if (taskTitle.isNotBlank() && taskPrompt.isNotBlank()) {
                                    scope.launch {
                                        val res = onDispatchTask(taskTitle, taskPrompt, engine)
                                        if (res.isSuccess) {
                                            tasks = listOf(
                                                AgentTaskItem(
                                                    id = res.getOrNull() ?: "queued",
                                                    title = taskTitle,
                                                    prompt = taskPrompt,
                                                    phase = "backlog",
                                                    engine = engine
                                                )
                                            ) + tasks
                                            taskTitle = ""
                                            taskPrompt = ""
                                            isCreating = false
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Launch Mission Now 🚀", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tasks List
                Text(
                    text = "MISSION HISTORY & QUEUE (${tasks.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate400,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tasks) { task ->
                        TaskRowCard(
                            task = task,
                            onOpen = { detailsTask = task },
                            onCancel = {
                                scope.launch {
                                    onCancelTask(task.id)
                                    tasks = tasks.map { if (it.id == task.id) it.copy(phase = "failed") else it }
                                }
                            },
                            onRetry = {
                                scope.launch {
                                    onRetryTask(task.id)
                                    tasks = tasks.map { if (it.id == task.id) it.copy(phase = "backlog") else it }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    val openTask = detailsTask
    if (openTask != null) {
        TaskDetailsSheet(
            task = openTask,
            serverBaseUrl = serverBaseUrl,
            authToken = authToken,
            onDismiss = { detailsTask = null },
            onRefreshTask = onGetTask,
            onDownloadVideo = onDownloadVideo,
            onPublishVideo = onPublishVideo
        )
    }
}

@Composable
fun TaskRowCard(
    task: AgentTaskItem,
    onOpen: () -> Unit = {},
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val isCancelled = task.cancelled || task.phase == "cancelled"
    val phaseColor = when {
        isCancelled -> Slate400
        task.phase == "completed" -> EmeraldConnected
        task.phase == "in_progress" -> AmberPending
        task.phase == "failed" -> RoseError
        else -> ElectricCyan
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .deskCard()
            .clickable { onOpen() }
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = task.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate100,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(phaseColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isCancelled) "CANCELLED" else task.phase.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = phaseColor,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Status Text or Output Summary
            val displayStatus = task.statusText ?: task.outputSummary
            if (!displayStatus.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = displayStatus,
                    fontSize = 11.sp,
                    color = if (task.phase == "failed" && !isCancelled) RoseError else Slate400
                )
            }
            // What a finished task produced (the weekly numbers, an answer): the first lines, tap for all of it
            val summary = task.outputSummary?.trim()
            if (!summary.isNullOrBlank() && summary != displayStatus && (task.phase == "completed" || task.phase == "failed")) {
                var expanded by remember(task.id) { mutableStateOf(false) }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary,
                    fontSize = 11.sp,
                    color = Slate300,
                    lineHeight = 15.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 6,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            }

            // Task Progress Bar
            Spacer(modifier = Modifier.height(6.dp))
            if (task.phase == "in_progress" && !isCancelled) {
                val prog = task.progress
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (prog != null && prog.label.isNotBlank()) "⚡ ${prog.label.uppercase()}" else "⚡ EXECUTING LIVE ON HOST",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberPending,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (prog != null) "${prog.percent}%" else "In Flight",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberPending,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (prog != null && prog.detail.isNotBlank()) {
                        Text(
                            text = prog.detail,
                            fontSize = 9.sp,
                            color = Slate400
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    if (prog != null) {
                        LinearProgressIndicator(
                            progress = { (prog.percent.coerceIn(0, 100)) / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = AmberPending,
                            trackColor = Slate900
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp)),
                            color = AmberPending,
                            trackColor = Slate900
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            } else if (task.phase == "completed") {
                LinearProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp)),
                    color = EmeraldConnected.copy(alpha = 0.6f),
                    trackColor = Slate900
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${task.id.take(12)} • ${task.engine}",
                    fontSize = 10.sp,
                    color = Slate500,
                    fontFamily = FontFamily.Monospace
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if ((task.phase == "in_progress" || task.phase == "backlog") && !isCancelled) {
                        Text(
                            text = "Cancel",
                            fontSize = 11.sp,
                            color = RoseError,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onCancel() }
                        )
                    } else if (task.phase == "failed" && !isCancelled) {
                        Text(
                            text = "Retry",
                            fontSize = 11.sp,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onRetry() }
                        )
                    }
                }
            }
        }
    }
}
