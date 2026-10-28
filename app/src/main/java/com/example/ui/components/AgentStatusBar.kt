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
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.data.model.DaemonStats
import com.example.ui.theme.DeskColors
import com.example.ui.theme.DeskRadius
import com.example.ui.theme.DeskShapes
import com.example.ui.theme.DeskType

/*
 * The bar above the chat (2026-10-27 redesign, docs/FEED_AND_DESIGN_PLAN_2026-10-27.md): the status in plain words,
 * one quiet mono line of numbers instead of four colored pills, and the running task by name. The three menu buttons
 * stay big and in their own colors (the owner, 2026-10-11), as a soft tint rather than a bright outline.
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
    val isRunning = stats.daemonStatus == "running_task"

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
    val statusLabel = when {
        !isConnected -> "Offline"
        isPaused -> "Standby"
        isRunning -> "Working"
        else -> "Online"
    }
    val shape = RoundedCornerShape(bottomStart = DeskRadius.card, bottomEnd = DeskRadius.card)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .deskCardSurface(shape, fill = DeskColors.Base)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).clickable { onOpenSettings() }
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
                        Text(text = statusLabel, style = DeskType.titleSmall, color = DeskColors.TextPrimary)
                        Text(
                            text = serverUrl.removePrefix("http://").removePrefix("https://").take(28),
                            style = DeskType.mono,
                            color = DeskColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // No Standby or settings buttons here (the owner, 2026-10-11): /standby and /wake, and the menu
                    MenuButton(Icons.Default.Task, "Tasks", DeskColors.Waiting, onOpenTasks)
                    MenuButton(Icons.Default.Memory, "Memory", DeskColors.Emerald, onOpenMemory)
                    MenuButton(Icons.Default.Videocam, "3D Videos", DeskColors.Memory, onOpenMedia)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(text = metricsLine(stats), style = DeskType.mono, maxLines = 1, overflow = TextOverflow.Ellipsis)

            if (isRunning || stats.tasksInProgress > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(DeskColors.Waiting))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stats.activeTask?.title?.takeIf { it.isNotBlank() } ?: "Working on a task",
                        style = DeskType.label,
                        color = DeskColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(DeskShapes.pill),
                    color = DeskColors.Waiting,
                    trackColor = DeskColors.Raised2,
                    gapSize = 0.dp
                )
            }
        }
    }
}

/** "auto · 231 done · 1 running · 0 queued": numbers bright, words muted, all on the mono grid. */
private fun metricsLine(stats: DaemonStats) = buildAnnotatedString {
    val parts = buildList {
        add(stats.defaultEngine.lowercase() to "")
        add("${stats.tasksCompleted}" to " done")
        if (stats.tasksInProgress > 0) add("${stats.tasksInProgress}" to " running")
        add("${stats.tasksBacklog}" to " queued")
    }
    parts.forEachIndexed { i, (value, word) ->
        if (i > 0) withStyle(SpanStyle(color = DeskColors.TextMuted)) { append(" · ") }
        withStyle(SpanStyle(color = DeskColors.TextPrimary)) { append(value) }
        withStyle(SpanStyle(color = DeskColors.TextMuted)) { append(word) }
    }
}

/** One of the bar's menu buttons: 44 dp, its own color as a soft tint (the owner, 2026-10-11: big and lit). */
@Composable
private fun MenuButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(DeskShapes.small)
            .background(color.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
    }
}
