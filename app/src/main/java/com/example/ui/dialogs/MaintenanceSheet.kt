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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.SystemLogEntry
import com.example.ui.components.LogLevel
import com.example.ui.components.logLineString
import com.example.ui.components.parseLogLine
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RoseError
import kotlinx.coroutines.launch
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CodeBlockBackground
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.components.deskSheet
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.ui.graphics.Brush
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import com.example.ui.components.SectionLabel
import com.example.ui.components.SheetHeader
import com.example.ui.components.deskTinted
import com.example.ui.theme.Amber200
import com.example.ui.theme.DeskShapes
import com.example.ui.theme.Rose300
import com.example.ui.theme.Slate200

@Composable
fun MaintenanceSheet(
    onDismiss: () -> Unit,
    onRunBackup: suspend () -> Result<String>,
    onRunCleanup: suspend () -> Result<String>,
    onRestartAgent: suspend () -> Result<String>,
    onCheckUptime: suspend () -> Long? = { null },
    onFetchLogs: suspend (limit: Int) -> List<SystemLogEntry>,
    onTestPush: suspend () -> Result<String>
) {
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<SystemLogEntry>>(emptyList()) }
    var isLoadingLogs by remember { mutableStateOf(true) }
    var actionStatus by remember { mutableStateOf<String?>(null) }
    var actionSuccess by remember { mutableStateOf(true) }
    var isOperating by remember { mutableStateOf(false) }
    val styledLogs = remember(logs) {
        logs.map { entry ->
            val parsed = parseLogLine(entry.timestamp, entry.level, entry.message)
            parsed.level to logLineString(parsed)
        }
    }

    fun refreshLogs() {
        scope.launch {
            isLoadingLogs = true
            try {
                logs = onFetchLogs(100)
            } catch (_: Exception) {}
            finally {
                isLoadingLogs = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshLogs()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .deskSheet()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                SheetHeader(
                    title = "System Maintenance & Logs",
                    icon = Icons.Default.Terminal,
                    subtitle = "Host daemon supervisor & diagnostics",
                    onClose = onDismiss
                ) {
                    IconButton(onClick = { refreshLogs() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Logs",
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Fast Action Controls Row (Section 7.5)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Backup Now
                    MaintenanceButton(
                        title = "Backup",
                        icon = Icons.Default.Save,
                        color = EmeraldConnected,
                        enabled = !isOperating,
                        modifier = Modifier.weight(1f)
                    ) {
                        scope.launch {
                            isOperating = true
                            actionStatus = "Running backup on host..."
                            val res = onRunBackup()
                            actionSuccess = res.isSuccess
                            actionStatus = if (res.isSuccess) "✅ ${res.getOrNull()}" else "⚠️ ${res.exceptionOrNull()?.message}"
                            isOperating = false
                            refreshLogs()
                        }
                    }

                    // Cleanup
                    MaintenanceButton(
                        title = "Cleanup",
                        icon = Icons.Default.CleaningServices,
                        color = AmberPending,
                        enabled = !isOperating,
                        modifier = Modifier.weight(1f)
                    ) {
                        scope.launch {
                            isOperating = true
                            actionStatus = "Purging temporary artifacts & logs..."
                            val res = onRunCleanup()
                            actionSuccess = res.isSuccess
                            actionStatus = if (res.isSuccess) "✅ ${res.getOrNull()}" else "⚠️ ${res.exceptionOrNull()?.message}"
                            isOperating = false
                            refreshLogs()
                        }
                    }

                    // Restart
                    MaintenanceButton(
                        title = "Restart",
                        icon = Icons.Default.RestartAlt,
                        color = RoseError,
                        enabled = !isOperating,
                        modifier = Modifier.weight(1f)
                    ) {
                        scope.launch {
                            isOperating = true
                            actionStatus = "Asking the agent to restart..."
                            val sentAt = System.currentTimeMillis()
                            val res = onRestartAgent()
                            actionSuccess = res.isSuccess
                            if (res.isFailure) {
                                actionStatus = "⚠️ ${res.exceptionOrNull()?.message}"
                                isOperating = false
                                return@launch
                            }
                            // Watch for it to come back: up again with an uptime shorter than the time since we
                            // asked. It used to say "restarting" forever. While a task finishes ("draining") the
                            // agent waits up to 30 minutes before restarting.
                            val answer = res.getOrNull().orEmpty()
                            val draining = answer.startsWith("⏳")
                            val deadline = sentAt + if (draining) 32 * 60_000L else 3 * 60_000L
                            var back = false
                            while (System.currentTimeMillis() < deadline) {
                                val waited = (System.currentTimeMillis() - sentAt) / 1000
                                actionStatus = (if (draining) answer else "🔄 $answer") +
                                    "\nWaiting for it to come back… ${waited}s"
                                kotlinx.coroutines.delay(3000)
                                val uptime = onCheckUptime()
                                val sinceAsked = (System.currentTimeMillis() - sentAt) / 1000
                                if (uptime != null && uptime <= sinceAsked + 2) {
                                    back = true
                                    break
                                }
                            }
                            actionSuccess = back
                            actionStatus = if (back) "✅ Back online. The agent restarted and is running."
                                else "⚠️ The agent hasn't come back yet. Check the logs, or try again in a minute."
                            isOperating = false
                            refreshLogs()
                        }
                    }

                    // Push Test
                    MaintenanceButton(
                        title = "Test Push",
                        icon = Icons.Default.NotificationsActive,
                        color = ElectricCyan,
                        enabled = !isOperating,
                        modifier = Modifier.weight(1f)
                    ) {
                        scope.launch {
                            isOperating = true
                            actionStatus = "Testing FCM push channel..."
                            val res = onTestPush()
                            actionSuccess = res.isSuccess
                            actionStatus = if (res.isSuccess) "🔔 ${res.getOrNull()}" else "⚠️ ${res.exceptionOrNull()?.message}"
                            isOperating = false
                        }
                    }
                }

                // The last action's result: amber while it runs, green when it worked, rose when it didn't
                val status = actionStatus
                AnimatedVisibility(visible = status != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    val tone = when {
                        isOperating -> AmberPending
                        actionSuccess -> EmeraldConnected
                        else -> RoseError
                    }
                    Row(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .deskTinted(tone, radius = 10.dp, fillAlpha = 0.08f)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isOperating) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), color = tone, strokeWidth = 1.5.dp)
                        } else {
                            Icon(
                                imageVector = if (actionSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = tone,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = status.orEmpty(),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = if (isOperating) Amber200 else if (actionSuccess) Slate200 else Rose300,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Log Viewer Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("Live system logs", count = logs.size)
                    if (isLoadingLogs) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = ElectricCyan, strokeWidth = 1.5.dp)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(DeskShapes.card)
                        .background(CodeBlockBackground)
                        .border(1.dp, Slate800, DeskShapes.card)
                        .padding(8.dp)
                ) {
                    if (logs.isEmpty() && isLoadingLogs) {
                        LoadingState("Reading host logs…")
                    } else if (logs.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.Terminal,
                            title = "No log records",
                            message = "The host daemon returned no log lines. Tap refresh to try again."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Parsed and styled once per refresh (not per frame): time dimmed, level colored,
                            // [Push]/[media] tags as colored pills, errors and warnings tinted (ui/components/RichText.kt)
                            items(styledLogs) { (level, line) ->
                                val rowTint = when (level) {
                                    LogLevel.ERROR -> RoseError.copy(alpha = 0.08f)
                                    LogLevel.WARNING -> AmberPending.copy(alpha = 0.06f)
                                    else -> Color.Transparent
                                }
                                Text(
                                    text = line,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Slate300,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(rowTint)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaintenanceButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(DeskShapes.control)
            .background(
                Brush.verticalGradient(
                    listOf(color.copy(alpha = if (enabled) 0.20f else 0.05f), color.copy(alpha = if (enabled) 0.08f else 0.03f))
                )
            )
            .border(1.dp, color.copy(alpha = if (enabled) 0.4f else 0.1f), DeskShapes.control)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = color.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.White else Slate500
            )
        }
    }
}
