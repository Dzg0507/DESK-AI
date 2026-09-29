package com.example.ui.screens

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.AgentStatusBar
import com.example.ui.components.ChatInputBar
import com.example.ui.components.MessageBubble
import com.example.ui.components.SessionDrawerContent
import com.example.ui.dialogs.ConnectionHubDialog
import com.example.ui.dialogs.AppUpdaterDialog
import com.example.ui.dialogs.CommandPaletteDialog
import com.example.ui.dialogs.MediaGallerySheet
import com.example.ui.dialogs.MemorySheet
import com.example.ui.dialogs.TasksSheet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.RoseError
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
    openTaskId: String? = null,
    onClearOpenTaskId: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentSession by viewModel.currentSession.collectAsStateWithLifecycle()
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val daemonStats by viewModel.daemonStats.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val streamingDurationMs by viewModel.streamingDurationMs.collectAsStateWithLifecycle()
    val streamingPhase by viewModel.streamingPhase.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showConnectionDialog by remember { mutableStateOf(false) }
    var showUpdaterDialog by remember { mutableStateOf(false) }
    var hasUpdateAvailable by remember { mutableStateOf(false) }
    var playbackVideo by remember { mutableStateOf<com.example.data.model.VideoItem?>(null) }

    // Check for updates in background
    LaunchedEffect(config.getResolvedUrl()) {
        withContext(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val cleanBase = config.getResolvedUrl().trimEnd('/')
                val checkUrls = mutableListOf<String>()
                if (cleanBase.isNotBlank()) {
                    checkUrls.add("$cleanBase/version.json")
                    checkUrls.add("$cleanBase/static/version.json")
                }
                checkUrls.add("https://raw.githubusercontent.com/Dzg0507/Desk-ai/main/web_dist/version.json")
                checkUrls.add("https://raw.githubusercontent.com/Dzg0507/Desk-ai/master/web_dist/version.json")
                for (u in checkUrls) {
                    try {
                        val req = okhttp3.Request.Builder().url(u).build()
                        val resp = client.newCall(req).execute()
                        if (resp.isSuccessful && resp.body != null) {
                            val bodyStr = resp.body!!.string()
                            val json = org.json.JSONObject(bodyStr)
                            val vCode = json.optInt("versionCode", 1)
                            if (vCode > com.example.BuildConfig.VERSION_CODE) {
                                withContext(Dispatchers.Main) {
                                    hasUpdateAvailable = true
                                }
                                break
                            }
                        }
                        resp.close()
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }
    var showTasksSheet by remember { mutableStateOf(false) }
    var showMemorySheet by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }
    var showAgentWorkSheet by remember { mutableStateOf(false) }
    var showMaintenanceSheet by remember { mutableStateOf(false) }

    // Open Tasks Sheet directly if tapped from push notification
    LaunchedEffect(openTaskId) {
        if (!openTaskId.isNullOrBlank()) {
            showTasksSheet = true
        }
    }

    val hasEarlierMessages by viewModel.hasEarlierMessages.collectAsStateWithLifecycle()
    // The list has a "Load earlier messages" row above the messages when older ones exist
    val lastItemIndex = messages.size - 1 + (if (hasEarlierMessages) 1 else 0)

    // Opening a chat jumps straight to the newest message (no animated scroll through the history);
    // a new or updated last message scrolls smoothly. Loading earlier messages keeps the reading position.
    var jumpedForSession by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentSession?.id, messages.lastOrNull()?.id, messages.lastOrNull()?.content) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (jumpedForSession != currentSession?.id) {
            listState.scrollToItem(lastItemIndex)
            jumpedForSession = currentSession?.id
        } else {
            listState.animateScrollToItem(lastItemIndex)
        }
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            val totalItems = messages.size
            if (totalItems == 0) false
            else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible < totalItems - 2
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0F172A)
            ) {
                SessionDrawerContent(
                    sessions = allSessions,
                    activeSessionId = currentSession?.id ?: "",
                    onSelectSession = { session ->
                        viewModel.switchSession(session)
                        scope.launch { drawerState.close() }
                    },
                    onNewSession = {
                        viewModel.createNewSession()
                        scope.launch { drawerState.close() }
                    },
                    onDeleteSession = { sessionId ->
                        viewModel.deleteSession(sessionId)
                    },
                    onTogglePin = { session ->
                        // toggle
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentSession?.title ?: "AlwaysOnAgent",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("drawer_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Sessions",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        // Start a fresh conversation (the assistant's memory lives on the server, so nothing is forgotten)
                        IconButton(
                            onClick = { viewModel.createNewSession() },
                            modifier = Modifier.testTag("new_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddComment,
                                contentDescription = "New chat",
                                tint = Color(0xFF94A3B8)
                            )
                        }

                        // In-App OTA Update Button with badge indicator
                        IconButton(
                            onClick = { showUpdaterDialog = true },
                            modifier = Modifier.testTag("update_app_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (hasUpdateAvailable) {
                                        Badge(
                                            containerColor = ElectricCyan,
                                            contentColor = Color.Black
                                        ) {
                                            Text("!", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = "Update App",
                                    tint = if (hasUpdateAvailable) ElectricCyan else Color(0xFF94A3B8)
                                )
                            }
                        }

                        IconButton(
                            onClick = { showConnectionDialog = true },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Bridge Settings",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0B0F19),
                        titleContentColor = Color.White
                    )
                )
            },
            modifier = modifier
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // The Scaffold already padded for the system bars; the input bar adds only the keyboard's extra height
                    .consumeWindowInsets(innerPadding)
                    .background(Color(0xFF080B11))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // AlwaysOnAgent Live Status & Pulse Bar
                    AgentStatusBar(
                        stats = daemonStats,
                        serverUrl = config.serverUrl,
                        isConnected = isConnected,
                        onToggleDaemon = { viewModel.toggleDaemon() },
                        onOpenTasks = { showTasksSheet = true },
                        onOpenMemory = { showMemorySheet = true },
                        onOpenMedia = { showMediaSheet = true },
                        onOpenSettings = { showConnectionDialog = true }
                    )

                    // Messages Scroll Area
                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp)
                        ) {
                            if (hasEarlierMessages) {
                                item(key = "load_earlier") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        TextButton(onClick = { viewModel.loadEarlierMessages() }) {
                                            Text("Load earlier messages", color = ElectricCyan, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            items(messages, key = { it.id }) { message ->
                                MessageBubble(
                                    message = message,
                                    onRunProposal = { msg, prop -> viewModel.runProposal(msg, prop) },
                                    onDismissProposal = { msg, prop -> viewModel.dismissProposal(msg, prop) },
                                    onDeleteMessage = { viewModel.deleteMessage(it) },
                                    onRegenerate = { viewModel.sendMessage("Please retry: ${it.content.take(60)}") },
                                    onSpeak = { viewModel.speak(it) },
                                    onAnswerQuestion = { msg, opt -> viewModel.answerQuestion(msg, opt) },
                                    onFocusInput = { /* chat input is directly below */ },
                                    onGetTask = { tid -> viewModel.getTask(tid) },
                                    onCancelTask = { tid -> viewModel.cancelTask(tid) },
                                    onRetryTask = { tid -> viewModel.retryTask(tid) },
                                    onPlayVideo = { url, filename ->
                                        val cleanBase = config.getResolvedUrl().trimEnd('/')
                                        val fullUrl = when {
                                            url.startsWith("http://") || url.startsWith("https://") -> url
                                            url.startsWith("/") -> "$cleanBase$url"
                                            url.isNotBlank() -> "$cleanBase/$url"
                                            else -> url
                                        }
                                        playbackVideo = com.example.data.model.VideoItem(
                                            filename = filename,
                                            sizeMb = 0.0,
                                            createdAt = "",
                                            url = fullUrl
                                        )
                                    },
                                    serverBaseUrl = config.getResolvedUrl(),
                                    authToken = config.apiKey
                                )
                            }
                        }

                        // Scroll-to-bottom FAB
                        if (showScrollToBottom) {
                            FloatingActionButton(
                                onClick = {
                                    scope.launch {
                                        if (messages.isNotEmpty()) {
                                            listState.animateScrollToItem(lastItemIndex)
                                        }
                                    }
                                },
                                containerColor = Color(0xFF1E293B),
                                contentColor = ElectricCyan,
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                                    .size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Scroll to bottom",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Chat Input Bar with Quick Commands, Voice Dictation & Command Deck
                    ChatInputBar(
                        text = inputText,
                        onTextChange = { viewModel.onInputTextChange(it) },
                        onSend = { viewModel.sendMessage() },
                        isStreaming = isStreaming,
                        onStopStreaming = { viewModel.stopStreaming() },
                        onOpenCommandPalette = { showCommandPalette = true }
                    )
                }
            }
        }
    }

    // Interactive Command Deck & Bundled Views Hub
    if (showCommandPalette) {
        CommandPaletteDialog(
            serverUrl = config.getResolvedUrl(),
            isConnected = isConnected,
            daemonStatus = daemonStats.daemonStatus,
            hasUpdateAvailable = hasUpdateAvailable,
            onOpenSettings = { showConnectionDialog = true },
            onOpenMedia = { showMediaSheet = true },
            onOpenMemory = { showMemorySheet = true },
            onOpenTasks = { showTasksSheet = true },
            onOpenUpdater = { showUpdaterDialog = true },
            onOpenAgentWork = { showAgentWorkSheet = true },
            onOpenMaintenance = { showMaintenanceSheet = true },
            onOpenDashboard = {
                // The HUD takes a one-time ?token= link, sets its own cookie and drops the token from the URL
                val base = config.getResolvedUrl().trimEnd('/')
                val token = config.apiKey.trim()
                val link = if (token.isNotEmpty()) "$base/?token=${android.net.Uri.encode(token)}" else "$base/"
                try {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link)))
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "No browser available to open the dashboard", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onSelectCommandTemplate = { template ->
                viewModel.onInputTextChange(template)
            },
            onExecuteCommand = { cmd ->
                viewModel.sendMessage(cmd)
            },
            onDismiss = { showCommandPalette = false }
        )
    }

    // Connection Hub Dialog
    if (showConnectionDialog) {
        ConnectionHubDialog(
            config = config,
            onSaveConfig = { viewModel.saveConfig(it) },
            onTestPing = { viewModel.testPing(it) },
            onDismiss = { showConnectionDialog = false }
        )
    }

    // In-App OTA Updater Dialog
    if (showUpdaterDialog) {
        AppUpdaterDialog(
            serverBaseUrl = config.getResolvedUrl(),
            authToken = config.apiKey,
            onDismiss = { showUpdaterDialog = false }
        )
    }

    // Tasks Sheet
    if (showTasksSheet) {
        TasksSheet(
            onDismiss = {
                showTasksSheet = false
                onClearOpenTaskId()
            },
            initialTaskId = openTaskId,
            liveTasks = daemonStats.tasks,
            activeTask = daemonStats.activeTask,
            onRefreshTasks = { viewModel.fetchTasks(50) },
            onDispatchTask = { title, prompt, eng -> viewModel.dispatchTask(title, prompt, eng) },
            onCancelTask = { taskId -> viewModel.cancelTask(taskId) },
            onAbortRunningTask = { viewModel.abortRunningTask() },
            onRetryTask = { taskId -> viewModel.retryTask(taskId) }
        )
    }

    // Memory Sheet
    if (showMemorySheet) {
        MemorySheet(
            onDismiss = { showMemorySheet = false },
            onAddFact = { fact -> viewModel.addMemoryFact(fact) },
            onDeleteFact = { id -> viewModel.deleteMemoryFact(id) }
        )
    }

    // Media Gallery Sheet
    if (showMediaSheet) {
        MediaGallerySheet(
            onDismiss = { showMediaSheet = false },
            onFetchVideos = { viewModel.fetchVideos() },
            onDownloadVideo = { url, file, onProg -> viewModel.downloadVideo(url, file, onProg) },
            onTriggerRender = { quote -> viewModel.triggerMedia("video", quote) },
            onTriggerTikTok = { quote -> viewModel.triggerMedia("tiktok", quote) },
            onPublishExistingVideo = { filename -> viewModel.publishVideoToTikTok(filename) },
            onGetTask = { tid -> viewModel.getTask(tid) },
            onCancelTask = { tid -> viewModel.cancelTask(tid) },
            onRetryTask = { tid -> viewModel.retryTask(tid) },
            onFetchImages = { viewModel.fetchImages() },
            onDeleteImage = { filename -> viewModel.deleteImage(filename) },
            authToken = config.apiKey
        )
    }

    // AgentWork Project Hub (Section 7.3)
    if (showAgentWorkSheet) {
        com.example.ui.dialogs.AgentWorkSheet(
            onDismiss = { showAgentWorkSheet = false },
            onFetchProjects = { viewModel.fetchAgentWorkProjects() },
            onDispatchJob = { project, instruction -> viewModel.dispatchAgentWorkJob(project, instruction) },
            onAddProject = { name, repo, desc -> viewModel.addAgentWorkProject(name, repo, desc) }
        )
    }

    // Video Player Dialog (from Live Task Card or Media Gallery)
    if (playbackVideo != null) {
        com.example.ui.dialogs.VideoPlayerDialog(
            video = playbackVideo!!,
            onDismiss = { playbackVideo = null },
            onDownloadVideo = { url, file, onProg -> viewModel.downloadVideo(url, file, onProg) },
            onPostTikTok = {
                val filename = playbackVideo?.filename
                if (filename != null) scope.launch { viewModel.publishVideoToTikTok(filename) }
            }
        )
    }

    // System Maintenance & Logs (Section 7.5)
    if (showMaintenanceSheet) {
        com.example.ui.dialogs.MaintenanceSheet(
            onDismiss = { showMaintenanceSheet = false },
            onRunBackup = { viewModel.runBackup() },
            onRunCleanup = { viewModel.runCleanup() },
            onRestartAgent = { viewModel.restartAgent() },
            onFetchLogs = { limit -> viewModel.fetchSystemLogs(limit) },
            onTestPush = { viewModel.testPush() }
        )
    }
}
