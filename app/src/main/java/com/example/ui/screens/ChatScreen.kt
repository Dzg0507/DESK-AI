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
    modifier: Modifier = Modifier
) {
    val currentSession by viewModel.currentSession.collectAsStateWithLifecycle()
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
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

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
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

                    // Live Streaming Mission Progress HUD
                    AnimatedVisibility(visible = isStreaming) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(13.dp),
                                            strokeWidth = 2.dp,
                                            color = ElectricCyan
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = streamingPhase,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${String.format(java.util.Locale.US, "%.1fs", streamingDurationMs / 1000f)}",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = ElectricCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(RoseError.copy(alpha = 0.2f))
                                                .clickable { viewModel.stopStreaming() }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "STOP",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = RoseError
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(1.5.dp)),
                                    color = ElectricCyan,
                                    trackColor = Color(0xFF1E293B)
                                )
                            }
                        }
                    }

                    // Messages Scroll Area
                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                MessageBubble(
                                    message = message,
                                    onRunProposal = { viewModel.runProposal(it) },
                                    onDismissProposal = { viewModel.dismissProposal(it) },
                                    onDeleteMessage = { viewModel.deleteMessage(it) },
                                    onRegenerate = { viewModel.sendMessage("Please retry: ${it.content.take(60)}") },
                                    onSpeak = { viewModel.speak(it) },
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
                                            listState.animateScrollToItem(messages.size - 1)
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
            onDismiss = { showTasksSheet = false },
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
            onTriggerTikTok = { quote -> viewModel.triggerMedia("tiktok", quote) }
        )
    }
}
