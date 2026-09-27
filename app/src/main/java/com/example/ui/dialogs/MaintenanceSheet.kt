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
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RoseError
import kotlinx.coroutines.launch

@Composable
fun MaintenanceSheet(
    onDismiss: () -> Unit,
    onRunBackup: suspend () -> Result<String>,
    onRunCleanup: suspend () -> Result<String>,
    onRestartAgent: suspend () -> Result<String>,
    onFetchLogs: suspend (limit: Int) -> List<SystemLogEntry>,
    onTestPush: suspend () -> Result<String>
) {
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<SystemLogEntry>>(emptyList()) }
    var isLoadingLogs by remember { mutableStateOf(true) }
    var actionStatus by remember { mutableStateOf<String?>(null) }
    var actionSuccess by remember { mutableStateOf(true) }
    var isOperating by remember { mutableStateOf(false) }

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
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "System Maintenance & Logs",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Host daemon supervisor & diagnostics",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Row {
                        IconButton(onClick = { refreshLogs() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Logs",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
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
                        color = Color(0xFFF59E0B),
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
                            actionStatus = "Dispatching restart signal (202 Accepted)..."
                            val res = onRestartAgent()
                            actionSuccess = res.isSuccess
                            actionStatus = if (res.isSuccess) "🔄 ${res.getOrNull()}" else "⚠️ ${res.exceptionOrNull()?.message}"
                            isOperating = false
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

                if (actionStatus != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = actionStatus!!,
                        fontSize = 11.sp,
                        color = if (actionSuccess) EmeraldConnected else RoseError,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Log Viewer Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE SYSTEM LOGS (LAST ${logs.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.8.sp
                    )
                    if (isLoadingLogs) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = ElectricCyan, strokeWidth = 1.5.dp)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF090D16))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    if (logs.isEmpty() && !isLoadingLogs) {
                        Text(
                            text = "No log records returned from host daemon.",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(logs) { entry ->
                                val levelColor = when (entry.level.uppercase()) {
                                    "ERROR" -> RoseError
                                    "WARN", "WARNING" -> Color(0xFFF59E0B)
                                    else -> EmeraldConnected
                                }
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    if (entry.timestamp.isNotBlank()) {
                                        Text(
                                            text = entry.timestamp.takeLast(12),
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF64748B)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = "[${entry.level.uppercase()}]",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = levelColor
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = entry.message,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
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
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = if (enabled) 0.15f else 0.05f))
            .border(1.dp, color.copy(alpha = if (enabled) 0.4f else 0.1f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
