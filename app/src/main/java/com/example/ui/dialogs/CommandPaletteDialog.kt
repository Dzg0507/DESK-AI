package com.example.ui.dialogs

import com.example.ui.theme.DeskShapes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.ui.components.circuitTexture
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.RoseError
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CodeBlockBackground
import com.example.ui.theme.Cyan400
import com.example.ui.theme.DeepCard
import com.example.ui.theme.DeepCardAlt
import com.example.ui.theme.DeepPanel
import com.example.ui.theme.Green400
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.Purple400
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

@Composable
fun CommandPaletteDialog(
    serverUrl: String,
    isConnected: Boolean,
    daemonStatus: String,
    hasUpdateAvailable: Boolean = false,
    onOpenSettings: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenUpdater: () -> Unit,
    onOpenAgentWork: () -> Unit = {},
    onOpenMaintenance: () -> Unit = {},
    onOpenSchedules: () -> Unit = {},
    onOpenRetasks: () -> Unit = {},
    onOpenTakeOver: () -> Unit = {},
    takeOverViewOnly: Boolean = false,
    onTakeOverViewOnlyChange: (Boolean) -> Unit = {},
    onOpenRepos: () -> Unit = {},
    onOpenCommands: () -> Unit = {},
    onSelectCommandTemplate: (String) -> Unit,
    onExecuteCommand: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(20.dp))
                // the Circuit look (2026-10-11): the circuit texture and a green-to-cerulean frame
                .circuitTexture(glow = 0.8f)
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        colors = listOf(ElectricCyan.copy(alpha = 0.7f), com.example.ui.theme.Cerulean)
                    ),
                    RoundedCornerShape(20.dp)
                )
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(DeskShapes.control)
                                .background(ElectricCyan.copy(alpha = 0.15f))
                                .border(1.dp, ElectricCyan.copy(alpha = 0.4f), DeskShapes.control)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "[/]",
                                color = ElectricCyan,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "COMMAND DECK",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isConnected) EmeraldConnected else RoseError)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isConnected) "ONLINE: ${serverUrl.removePrefix("http://").take(22)}" else "BRIDGE OFFLINE",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isConnected) Slate400 else RoseError
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Slate800)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Slate300,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // SECTION 1: BUNDLED WORKSTATION HUBS (Show when search is empty or matches)
                    run {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "WORKSTATION PANELS & HUBS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = ElectricCyan
                                    )
                                    Text(
                                        text = "4 VIEWS BUNDLED",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Slate500
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 2x2 Grid of Workstation Hubs
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 1. Connection & Settings Hub
                                        WorkstationCard(
                                            title = "Connection Hub",
                                            subtitle = "LAN, Remote & Auth Token",
                                            icon = Icons.Default.Settings,
                                            accentColor = ElectricCyan,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                onDismiss()
                                                onOpenSettings()
                                            }
                                        )

                                        // 2. 3D Video Studio
                                        WorkstationCard(
                                            title = "Video Gallery",
                                            subtitle = "Stream 3D Renders & TikToks",
                                            icon = Icons.Default.Videocam,
                                            accentColor = Purple400,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                onDismiss()
                                                onOpenMedia()
                                            }
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 3. Memory Vault
                                        WorkstationCard(
                                            title = "Memory Vault",
                                            subtitle = "SQLite Facts & Long-term Knowledge",
                                            icon = Icons.Default.Memory,
                                            accentColor = EmeraldConnected,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                onDismiss()
                                                onOpenMemory()
                                            }
                                        )

                                        // 4. Mission Tasks
                                        WorkstationCard(
                                            title = "Mission Tasks",
                                            subtitle = "Active Queue & Worker Pool",
                                            icon = Icons.Default.Task,
                                            accentColor = AmberPending,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                onDismiss()
                                                onOpenTasks()
                                            }
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 5. AgentWork Projects
                                        WorkstationCard(
                                            title = "AgentWork",
                                            subtitle = "Autonomous Code & Git Repos",
                                            icon = Icons.Default.Build,
                                            accentColor = ElectricCyan,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                onDismiss()
                                                onOpenAgentWork()
                                            }
                                        )

                                        // 6. Maintenance & Logs
                                        WorkstationCard(
                                            title = "Maintenance",
                                            subtitle = "Backup, Restart & System Logs",
                                            icon = Icons.Default.CleaningServices,
                                            accentColor = NeonPurple,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                onDismiss()
                                                onOpenMaintenance()
                                            }
                                        )
                                    }

                                    // 7. Schedules: videos, reminders and tasks that run by themselves
                                    WorkstationCard(
                                        title = "Schedules",
                                        subtitle = "Videos, reminders & tasks on a timer",
                                        icon = Icons.Default.Schedule,
                                        accentColor = Cyan400,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            onDismiss()
                                            onOpenSchedules()
                                        }
                                    )

                                    // 7b. Retasks: clean computer runs, repeated without the AI for the same task
                                    WorkstationCard(
                                        title = "Retasks",
                                        subtitle = "Clean runs, repeated without the AI",
                                        icon = Icons.Default.Autorenew,
                                        accentColor = com.example.ui.theme.Violet300,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            onDismiss()
                                            onOpenRetasks()
                                        }
                                    )

                                    // 8. Take over: the Mini's screen on the phone, taps replayed there
                                    WorkstationCard(
                                        title = "Take over",
                                        subtitle = if (takeOverViewOnly) "Watch the Mini's screen (view only)"
                                                   else "Use the Mini's screen from your phone",
                                        icon = if (takeOverViewOnly) Icons.Default.Visibility else Icons.Default.TouchApp,
                                        accentColor = AmberPending,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            onDismiss()
                                            onOpenTakeOver()
                                        }
                                    )
                                    // View only: the picture without taking control, so a running task keeps going
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable { onTakeOverViewOnlyChange(!takeOverViewOnly) }
                                            .padding(start = 4.dp, end = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        androidx.compose.material3.Checkbox(
                                            checked = takeOverViewOnly,
                                            onCheckedChange = { onTakeOverViewOnlyChange(it) }
                                        )
                                        Text("View only (watch without taking control)", color = Slate300, fontSize = 13.sp)
                                    }

                                    // 9. Repos: GitHub repos like a file explorer; "Work on this" starts a job
                                    WorkstationCard(
                                        title = "Repos",
                                        subtitle = "Browse your GitHub repos and work on one",
                                        icon = Icons.Default.FolderOpen,
                                        accentColor = Green400,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            onDismiss()
                                            onOpenRepos()
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // In-App OTA Update Quick Bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(DeskShapes.card)
                                        .background(DeepCardAlt)
                                        .border(
                                            1.dp,
                                            if (hasUpdateAvailable) ElectricCyan else Slate800,
                                            DeskShapes.card
                                        )
                                        .clickable {
                                            onDismiss()
                                            onOpenUpdater()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.SystemUpdate,
                                                contentDescription = null,
                                                tint = if (hasUpdateAvailable) ElectricCyan else Slate400,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = if (hasUpdateAvailable) "New App Update Available!" else "In-App OTA Updater",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (hasUpdateAvailable) ElectricCyan else Color.White
                                                )
                                                Text(
                                                    text = "Check version manifest & download fresh GitHub releases",
                                                    fontSize = 10.sp,
                                                    color = Slate400
                                                )
                                            }
                                        }

                                        if (hasUpdateAvailable) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(DeskShapes.chip)
                                                    .background(ElectricCyan)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "UPDATE",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Black
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "Check",
                                                fontSize = 11.sp,
                                                color = Slate500
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Slash commands live in their own pop-up now (the owner, 2026-10-11): typing / in chat shows
                    // them, and /? or /help (or this button) opens the full list
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DeskShapes.control)
                                .background(Slate900)
                                .border(1.dp, Slate800, DeskShapes.control)
                                .clickable { onDismiss(); onOpenCommands() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Type / in chat for commands, or /? and /help for the full list",
                                fontSize = 12.sp,
                                color = Slate400,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "COMMANDS ↗",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = ElectricCyan
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorkstationCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            // each tile glows in its own color, running into deep cerulean (the Circuit look, 2026-10-11)
            .shadow(10.dp, DeskShapes.card, ambientColor = accentColor, spotColor = accentColor)
            .clip(DeskShapes.card)
            // the same hex circuit as the app's background (the owner's ask), solid so the glow only shows outside,
            // with the tile's own color washed over it
            .circuitTexture(glow = 0.45f)
            .background(Brush.linearGradient(listOf(accentColor.copy(alpha = 0.20f), Color.Transparent,
                                                    com.example.ui.theme.Cerulean.copy(alpha = 0.22f))))
            .border(1.dp, Brush.linearGradient(listOf(accentColor.copy(alpha = 0.85f), com.example.ui.theme.Cerulean.copy(alpha = 0.8f))),
                    DeskShapes.card)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(DeskShapes.control)
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.4f), DeskShapes.control),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "OPEN ↗",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Slate400,
                maxLines = 1
            )
        }
    }
}
