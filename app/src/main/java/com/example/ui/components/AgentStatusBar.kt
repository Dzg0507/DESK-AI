package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DaemonStats
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.RoseError

@Composable
fun AgentStatusBar(
    stats: DaemonStats,
    serverUrl: String,
    isConnected: Boolean,
    onToggleDaemon: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPaused = stats.daemonStatus in listOf("paused", "sleep", "stopped")
    val isRunning = stats.daemonStatus == "running_task"

    // Infinite radar pulse animation for the beacon ring
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    val beaconColor = when {
        !isConnected -> RoseError
        isPaused -> Color(0xFF94A3B8)
        isRunning -> Color(0xFFF59E0B)
        else -> EmeraldConnected
    }

    val statusLabel = when {
        !isConnected -> "OFFLINE / UNREACHABLE"
        isPaused -> "STANDBY (SLEEP)"
        isRunning -> "PROCESSING MISSION"
        else -> "ONLINE / IDLE"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Row 1: Pulse Beacon, Status Text & Fast Action Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pulse & Host
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onOpenSettings() }
                ) {
                    Box(
                        modifier = Modifier.size(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isConnected && !isPaused) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .scale(pulseScale)
                                    .border(1.5.dp, beaconColor.copy(alpha = pulseAlpha), CircleShape)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(beaconColor)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = statusLabel,
                            color = beaconColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = serverUrl.removePrefix("http://").removePrefix("https://").take(24),
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Action Buttons Bar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Daemon Wake / Standby Toggle Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPaused) ElectricCyan.copy(alpha = 0.15f) else Color(0xFF1E293B))
                            .border(1.dp, if (isPaused) ElectricCyan else Color(0xFF334155), RoundedCornerShape(8.dp))
                            .clickable { onToggleDaemon() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.PowerSettingsNew,
                                contentDescription = if (isPaused) "Wake Daemon" else "Standby",
                                tint = if (isPaused) ElectricCyan else Color(0xFFCBD5E1),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPaused) "Wake" else "Standby",
                                color = if (isPaused) ElectricCyan else Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Tasks Sheet Trigger
                    IconButton(
                        onClick = onOpenTasks,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Task,
                            contentDescription = "Tasks",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Memory Sheet Trigger
                    IconButton(
                        onClick = onOpenMemory,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "Memory",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Media Videos Trigger
                    IconButton(
                        onClick = onOpenMedia,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "3D Videos",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Settings Hub Trigger
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Connection Hub",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Metrics chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip(label = "ENGINE", value = stats.defaultEngine.uppercase(), color = NeonIndigo)
                MetricChip(label = "COMPLETED", value = "${stats.tasksCompleted}", color = EmeraldConnected)
                if (stats.tasksInProgress > 0) {
                    MetricChip(label = "ACTIVE", value = "${stats.tasksInProgress}", color = Color(0xFFF59E0B))
                }
                MetricChip(label = "QUEUE", value = "${stats.tasksBacklog}", color = ElectricCyan)
            }

            // Active Daemon Processing Progress Bar
            if (isRunning || stats.tasksInProgress > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF59E0B))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "DAEMON BUSY: EXECUTING TASK PIPELINE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B),
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = "${stats.tasksInProgress} active",
                            fontSize = 9.sp,
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFF59E0B),
                        trackColor = Color(0xFF1E293B)
                    )
                }
            }
        }
    }
}

@Composable
fun MetricChip(label: String, value: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .border(0.8.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$label: ",
                color = color,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = value,
                color = Color(0xFFF8FAFC),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
