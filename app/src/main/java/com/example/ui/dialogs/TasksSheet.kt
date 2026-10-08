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
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import com.example.ui.components.RichBlock
import com.example.ui.components.SectionCard
import com.example.ui.components.SectionLabel
import com.example.ui.components.SheetHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.TagChip
import com.example.ui.components.deskTinted
import com.example.ui.components.parseRichText
import com.example.ui.components.phaseColor
import com.example.ui.components.phaseLabel
import com.example.ui.components.relativeTime
import com.example.ui.components.richInlineString
import com.example.ui.components.shortSource
import com.example.ui.theme.DeskShapes
import com.example.ui.theme.Green600
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.Red300
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
                val runningCount = tasks.count { it.phase == "in_progress" && !it.cancelled }
                val queuedCount = tasks.count { it.phase == "backlog" && !it.cancelled }
                SheetHeader(
                    title = "Missions",
                    icon = Icons.Default.Task,
                    subtitle = when {
                        runningCount > 0 -> "$runningCount running · $queuedCount queued · ${tasks.size} total"
                        tasks.isNotEmpty() -> "${tasks.size} on AlwaysOnAgent"
                        else -> "AlwaysOnAgent task queue"
                    },
                    onClose = onDismiss
                ) {
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
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ElectricCyan, strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (!initialTaskId.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .deskTinted(ElectricCyan, radius = 10.dp, fillAlpha = 0.10f)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
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
                            .deskTinted(AmberPending, fillAlpha = 0.08f)
                            .padding(12.dp)
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
                                        .clip(DeskShapes.pill)
                                        .background(RoseError.copy(alpha = 0.16f))
                                        .border(1.dp, RoseError.copy(alpha = 0.5f), DeskShapes.pill)
                                        .clickable {
                                            scope.launch {
                                                onAbortRunningTask()
                                                val f = onRefreshTasks()
                                                if (f.isNotEmpty()) tasks = f
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
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
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Toggle Quick Dispatch Form
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DeskShapes.control)
                        .background(
                            if (isCreating) Brush.horizontalGradient(listOf(Slate800, Slate800))
                            else Brush.horizontalGradient(listOf(ElectricCyanGlow, NeonIndigo))
                        )
                        .clickable { isCreating = !isCreating }
                        .padding(vertical = 10.dp),
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
                            .clip(DeskShapes.control)
                            .background(Brush.horizontalGradient(listOf(EmeraldConnected, Green600)))
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
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Launch Mission Now 🚀", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tasks List
                SectionLabel("Mission history & queue", count = tasks.size)
                Spacer(modifier = Modifier.height(8.dp))

                if (tasks.isEmpty()) {
                    if (isRefreshing) {
                        LoadingState("Loading missions…")
                    } else {
                        EmptyState(
                            icon = Icons.Default.Task,
                            title = "No missions yet",
                            message = "Dispatch one above, or ask in chat and it will show up here.",
                            accent = ElectricCyan
                        )
                    }
                }

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
    val phaseColor = phaseColor(task.phase, isCancelled)
    val failed = task.phase == "failed" && !isCancelled

    SectionCard(accent = phaseColor, onClick = onOpen, contentPadding = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = task.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Slate100,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            StatusPill(text = phaseLabel(task.phase, isCancelled), color = phaseColor)
        }

        // Status line (what it's doing / why it stopped)
        val displayStatus = task.statusText ?: task.outputSummary
        val statusIsSummary = task.statusText == null
        if (!displayStatus.isNullOrBlank() && !(statusIsSummary && (task.phase == "completed" || task.phase == "failed"))) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = displayStatus,
                fontSize = 11.sp,
                color = if (failed) RoseError else Slate400,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        // What a finished task produced: a short, readable preview (markdown stripped to inline styling).
        // Tap the preview to see more of it; tap anywhere else for the full details.
        val summary = task.outputSummary?.trim()
        if (!summary.isNullOrBlank() && (task.phase == "completed" || task.phase == "failed")) {
            var expanded by remember(task.id) { mutableStateOf(false) }
            val preview = remember(summary) { summaryPreview(summary) }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = preview,
                fontSize = 12.sp,
                color = if (failed) Red300 else Slate300,
                lineHeight = 17.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .clickable { expanded = !expanded }
            )
        }

        // Task Progress Bar
        if (task.phase == "in_progress" && !isCancelled) {
            val prog = task.progress
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (prog != null && prog.label.isNotBlank()) "⚡ ${prog.label}" else "⚡ Running on host",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AmberPending,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (prog != null) "${prog.percent}%" else "in flight",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberPending,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (prog != null && prog.detail.isNotBlank()) {
                    Text(
                        text = prog.detail,
                        fontSize = 10.sp,
                        color = Slate400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (prog != null) {
                    LinearProgressIndicator(
                        progress = { (prog.percent.coerceIn(0, 100)) / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(DeskShapes.pill),
                        color = AmberPending,
                        trackColor = Slate900
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(DeskShapes.pill),
                        color = AmberPending,
                        trackColor = Slate900
                    )
                }
            }
        }

        // Footer: when · where from · id/engine, then the action
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val stamp = task.completedAt ?: task.startedAt ?: task.createdAt
            val whenText = remember(stamp) { relativeTime(stamp) }
            val source = remember(task.source) { shortSource(task.source) }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (whenText != null) {
                    TagChip(text = whenText, icon = Icons.Default.Schedule, color = Slate400)
                }
                if (source != null) {
                    TagChip(text = source, color = if (source.startsWith("Schedule")) NeonPurple else ElectricCyan)
                }
                Text(
                    text = if (whenText == null && source == null) "#${task.id.take(12)} • ${task.engine}" else task.engine,
                    fontSize = 10.sp,
                    color = Slate500,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if ((task.phase == "in_progress" || task.phase == "backlog") && !isCancelled) {
                TagChip(text = "Cancel", color = RoseError, onClick = onCancel)
            } else if (failed) {
                TagChip(text = "Retry", color = ElectricCyan, icon = Icons.Default.Refresh, onClick = onRetry)
            }
        }
    }
}

/** The first few readable lines of a result: headings/bullets/paragraphs with inline styling, no markdown marks. */
private fun summaryPreview(text: String): AnnotatedString {
    val lines = parseRichText(text).mapNotNull { b ->
        when (b) {
            is RichBlock.Heading -> b.text
            is RichBlock.Paragraph -> b.text
            is RichBlock.Bullet -> "• " + b.text
            is RichBlock.Numbered -> "${b.number}. " + b.text
            is RichBlock.TagLine -> "[${b.tag}] " + b.text
            is RichBlock.BranchBadge -> b.text
            is RichBlock.DiffStatSummary -> "${b.files} file${if (b.files == 1) "" else "s"} changed (+${b.insertions} −${b.deletions})"
            else -> null
        }
    }.filter { it.isNotBlank() }.take(8)
    if (lines.isEmpty()) return AnnotatedString(text.trim())
    return buildAnnotatedString {
        lines.forEachIndexed { i, line ->
            if (i > 0) append("\n")
            append(richInlineString(line, Slate300))
        }
    }
}
