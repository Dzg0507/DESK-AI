package com.example.ui.dialogs

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
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

data class CommandEntry(
    val command: String,
    val template: String,
    val title: String,
    val description: String,
    val category: String,
    val icon: ImageVector,
    val color: Color,
    val isDirectAction: Boolean = false
)

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
    onOpenDashboard: () -> Unit = {},
    onSelectCommandTemplate: (String) -> Unit,
    onExecuteCommand: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val allCommands = remember {
        listOf(
            // Daemon controls
            CommandEntry(
                command = "/status",
                template = "/status",
                title = "System Telemetry & Status",
                description = "Inspect daemon state, CPU/RAM, and task counts",
                category = "Daemon Controls",
                icon = Icons.Default.Terminal,
                color = ElectricCyan,
                isDirectAction = true
            ),
            CommandEntry(
                command = "/wake",
                template = "/wake",
                title = "Awaken Daemon",
                description = "Ignite AlwaysOnAgent background worker pool",
                category = "Daemon Controls",
                icon = Icons.Default.PlayArrow,
                color = EmeraldConnected,
                isDirectAction = true
            ),
            CommandEntry(
                command = "/standby",
                template = "/standby",
                title = "Daemon Standby",
                description = "Sleep background daemon (0% host CPU/GPU)",
                category = "Daemon Controls",
                icon = Icons.Default.PowerSettingsNew,
                color = Color(0xFF94A3B8),
                isDirectAction = true
            ),
            CommandEntry(
                command = "/engine",
                template = "/engine auto",
                title = "Switch Engine Pipeline",
                description = "Configure auto, antigravity, cloud, or ollama",
                category = "Daemon Controls",
                icon = Icons.Default.Settings,
                color = NeonIndigo,
                isDirectAction = false
            ),

            // Creative & Media
            CommandEntry(
                command = "/video",
                template = "/video \"Daily Affirmation\"",
                title = "Render 3D Card-Flip Video",
                description = "Dispatches local 3D motion video rendering pipeline",
                category = "Media & Automation",
                icon = Icons.Default.Videocam,
                color = Color(0xFFC084FC),
                isDirectAction = false
            ),
            CommandEntry(
                command = "/tiktok",
                template = "/tiktok \"Daily Affirmation\"",
                title = "Render & Auto-Post to TikTok",
                description = "Renders 3D video and uploads to @Thevibecheckproject",
                category = "Media & Automation",
                icon = Icons.Default.Videocam,
                color = Color(0xFFF43F5E),
                isDirectAction = false
            ),
            CommandEntry(
                command = "/image",
                template = "/image cyberpunk desk setup with glowing cyan neon, 8k",
                title = "Synthesize AI Image",
                description = "Generate visual concept or graphic assets",
                category = "Media & Automation",
                icon = Icons.Default.Terminal,
                color = Color(0xFF38BDF8),
                isDirectAction = false
            ),

            // Memory & Knowledge
            CommandEntry(
                command = "/memory",
                template = "/memory",
                title = "Query Memory Vault",
                description = "Fetch long-term associative memory & profile",
                category = "Memory & Knowledge",
                icon = Icons.Default.Memory,
                color = EmeraldConnected,
                isDirectAction = true
            ),
            CommandEntry(
                command = "/remember",
                template = "/remember ",
                title = "Store Permanent Fact",
                description = "Commit a persistent fact to the host SQLite database",
                category = "Memory & Knowledge",
                icon = Icons.Default.Memory,
                color = EmeraldConnected,
                isDirectAction = false
            ),
            CommandEntry(
                command = "/forget",
                template = "/forget #",
                title = "Erase Fact by ID",
                description = "Permanently remove a memory fact by its #ID",
                category = "Memory & Knowledge",
                icon = Icons.Default.Memory,
                color = RoseError,
                isDirectAction = false
            ),

            // Task Orchestration
            CommandEntry(
                command = "/tasks",
                template = "/status",
                title = "List Background Tasks",
                description = "Inspect active missions in the worker pool",
                category = "Task Orchestration",
                icon = Icons.Default.Task,
                color = Color(0xFFF59E0B),
                isDirectAction = true
            ),
            CommandEntry(
                command = "/cancel",
                template = "/cancel ",
                title = "Abort Mission / Task",
                description = "Cancel an in-progress background mission by task ID",
                category = "Task Orchestration",
                icon = Icons.Default.Close,
                color = RoseError,
                isDirectAction = false
            ),
            CommandEntry(
                command = "/retry",
                template = "/retry ",
                title = "Retry Failed Mission",
                description = "Re-enqueue a failed task back to the worker pool",
                category = "Task Orchestration",
                icon = Icons.Default.PlayArrow,
                color = Color(0xFFF59E0B),
                isDirectAction = false
            ),
            CommandEntry(
                command = "/help",
                template = "/help",
                title = "Full Mission Manual",
                description = "Display complete guide & instructions",
                category = "System & Support",
                icon = Icons.Default.Terminal,
                color = ElectricCyan,
                isDirectAction = true
            )
        )
    }

    val filteredCommands = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            allCommands
        } else {
            val q = searchQuery.trim().lowercase()
            allCommands.filter {
                it.command.lowercase().contains(q) ||
                it.title.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF090D16),
                            Color(0xFF05070B)
                        )
                    )
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        colors = listOf(ElectricCyan.copy(alpha = 0.5f), Color(0xFF1E293B))
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
                                .clip(RoundedCornerShape(8.dp))
                                .background(ElectricCyan.copy(alpha = 0.15f))
                                .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
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
                                    color = if (isConnected) Color(0xFF94A3B8) else RoseError
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Filter Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search commands, actions, or views...",
                            color = Color(0xFF64748B),
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF131D31),
                        unfocusedContainerColor = Color(0xFF101726),
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color(0xFFE2E8F0)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // SECTION 1: BUNDLED WORKSTATION HUBS (Show when search is empty or matches)
                    if (searchQuery.isBlank() || "settings views memory tasks video updater connection".contains(searchQuery.lowercase())) {
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
                                        color = Color(0xFF64748B)
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
                                            accentColor = Color(0xFFC084FC),
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
                                            accentColor = Color(0xFFF59E0B),
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
                                            accentColor = Color(0xFF38BDF8),
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
                                            accentColor = Color(0xFFA855F7),
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                onDismiss()
                                                onOpenMaintenance()
                                            }
                                        )
                                    }

                                    // 7. The server's web dashboard (the HUD), opened already signed in
                                    WorkstationCard(
                                        title = "Web Dashboard",
                                        subtitle = "Mission Control HUD in the browser",
                                        icon = Icons.Default.Dashboard,
                                        accentColor = Color(0xFF22D3EE),
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            onDismiss()
                                            onOpenDashboard()
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // In-App OTA Update Quick Bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF131C2E))
                                        .border(
                                            1.dp,
                                            if (hasUpdateAvailable) ElectricCyan else Color(0xFF1E293B),
                                            RoundedCornerShape(12.dp)
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
                                                tint = if (hasUpdateAvailable) ElectricCyan else Color(0xFF94A3B8),
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
                                                    color = Color(0xFF94A3B8)
                                                )
                                            }
                                        }

                                        if (hasUpdateAvailable) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
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
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 2: COMMANDS LIST
                    item {
                        Text(
                            text = "INTERACTIVE SLASH COMMANDS (${filteredCommands.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    items(filteredCommands) { cmd ->
                        CommandItemRow(
                            entry = cmd,
                            onClick = {
                                onDismiss()
                                if (cmd.isDirectAction) {
                                    onExecuteCommand(cmd.command)
                                } else {
                                    onSelectCommandTemplate(cmd.template)
                                }
                            }
                        )
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
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF131D31))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
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
                color = Color(0xFF94A3B8),
                maxLines = 1
            )
        }
    }
}

@Composable
fun CommandItemRow(
    entry: CommandEntry,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(entry.color.copy(alpha = 0.12f))
                        .border(1.dp, entry.color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = entry.command,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = entry.color
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = entry.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = entry.description,
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (entry.isDirectAction) "EXECUTE ▶" else "INSERT ✎",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (entry.isDirectAction) EmeraldConnected else Color(0xFFCBD5E1)
                )
            }
        }
    }
}
