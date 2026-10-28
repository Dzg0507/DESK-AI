package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DaemonStats
import com.example.ui.theme.DeskColors
import com.example.ui.theme.DeskType

/*
 * The agent's live status above the chat (2026-10-27, the spatial-glass visual target): a floating frosted pill with
 * the beacon, the agent's name and what it's doing, instead of a full-width banner. Tasks, Memory and Videos (also in
 * the hub menu) sit behind one small toggle beside it (Devin: "some kind of collapsable icon, that hides them").
 */
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
    val isRunning = stats.daemonStatus == "running_task" || stats.tasksInProgress > 0
    var hubsOpen by rememberSaveable { mutableStateOf(false) }

    // The beacon's ring pulses outward while the agent is up
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(animation = tween(1800, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Restart),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(animation = tween(1800, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Restart),
        label = "pulse_alpha"
    )
    val beaconColor = when {
        !isConnected -> DeskColors.Error
        isPaused -> DeskColors.TextMuted
        isRunning -> DeskColors.Waiting
        else -> DeskColors.Signal
    }
    val doing = when {
        !isConnected -> "Offline · tap to reconnect"
        isPaused -> "Standby"
        isRunning -> stats.activeTask?.title?.takeIf { it.isNotBlank() } ?: "Working on a task"
        else -> "Online · ${stats.tasksCompleted} done"
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .glass()
                .clickable { onOpenSettings() }
                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                if (isConnected && !isPaused) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .scale(pulseScale)
                            .border(1.dp, beaconColor.copy(alpha = pulseAlpha), CircleShape)
                    )
                }
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(beaconColor))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AlwaysOnAgent", style = DeskType.label, fontWeight = FontWeight.SemiBold,
                        color = DeskColors.TextPrimary)
                    if (stats.tasksInProgress > 0) {
                        Text("  ${stats.tasksInProgress} running", style = DeskType.label, color = DeskColors.TextSecondary)
                    }
                }
                Text(doing, style = DeskType.mono, color = DeskColors.TextSecondary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }

        AnimatedVisibility(
            visible = hubsOpen,
            enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassIconButton(Icons.Default.Task, "Tasks", DeskColors.Waiting) { hubsOpen = false; onOpenTasks() }
                GlassIconButton(Icons.Default.Memory, "Memory", DeskColors.Emerald) { hubsOpen = false; onOpenMemory() }
                GlassIconButton(Icons.Default.Videocam, "Videos", DeskColors.Memory) { hubsOpen = false; onOpenMedia() }
            }
        }
        GlassIconButton(
            icon = if (hubsOpen) Icons.Default.Close else Icons.Default.Dashboard,
            label = if (hubsOpen) "Hide hubs" else "Show hubs",
            tint = DeskColors.TextSecondary
        ) { hubsOpen = !hubsOpen }
    }
}

/** A round frosted-glass icon button (40 dp). */
@Composable
fun GlassIconButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp).glass(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
    }
}
