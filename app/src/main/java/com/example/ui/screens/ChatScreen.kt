package com.example.ui.screens

import com.example.ui.theme.DeskTextSize
import androidx.compose.ui.graphics.Brush
import com.example.ui.theme.ChatBackground
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.AmberPending
import com.example.ui.components.EmptyState
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Menu
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
import com.example.ui.dialogs.BackgroundWorkSheet
import com.example.ui.dialogs.CommandPaletteDialog
import com.example.ui.dialogs.MediaGallerySheet
import com.example.ui.dialogs.MemorySheet
import com.example.ui.dialogs.TasksSheet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.RoseError
import kotlinx.coroutines.launch
import com.example.ui.theme.Amber200
import com.example.ui.theme.Amber900
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SlateDarkBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
    openTaskId: String? = null,
    onClearOpenTaskId: () -> Unit = {},
    openNeedsYou: String? = null,
    onClearOpenNeedsYou: () -> Unit = {},
    sharedTick: Int = 0,
    modifier: Modifier = Modifier
) {
    // Something shared to DeskAI from another app: its text goes in the message box, its files are attached
    LaunchedEffect(sharedTick) {
        if (sharedTick > 0) com.example.SharedInbox.take()?.let { viewModel.acceptShared(it) }
    }
    val currentSession by viewModel.currentSession.collectAsStateWithLifecycle()
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val daemonStats by viewModel.daemonStats.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val isRefining by viewModel.isRefining.collectAsStateWithLifecycle()
    val canUndoRefine by viewModel.canUndoRefine.collectAsStateWithLifecycle()
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
    var openCanvas by remember { mutableStateOf<com.example.data.model.CanvasRef?>(null) }
    val activityState by viewModel.activityState.collectAsStateWithLifecycle()
    var showBackgroundWorkSheet by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "awayShiftPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "awayShiftAlpha"
    )

    // Check for updates in background
    LaunchedEffect(config.getResolvedUrl()) {
        withContext(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                // Updates are published only on GitHub; the same check as the update screen (UpdateSource), so the
                // badge and the screen can't disagree
                val latest = com.example.data.remote.UpdateSource.fetch(client)
                if (latest != null && latest.versionCode > com.example.BuildConfig.VERSION_CODE) {
                    withContext(Dispatchers.Main) { hasUpdateAvailable = true }
                }
            } catch (_: Exception) {}
        }
    }
    var showTasksSheet by remember { mutableStateOf(false) }
    var showMemorySheet by remember { mutableStateOf(false) }
    var showMemoryCheck by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }
    var showAgentWorkSheet by remember { mutableStateOf(false) }
    var showSchedulesSheet by remember { mutableStateOf(false) }
    var showRetasksSheet by remember { mutableStateOf(false) }
    var showCredentialsSheet by remember { mutableStateOf(false) }
    var showCommandsSheet by remember { mutableStateOf(false) }
    // A slash command picked (the / pop-up or the Commands list): run it, start it in the chat box, or open the list
    val pickCommand: (com.example.data.model.SlashCommand) -> Unit = { c ->
        when {
            c.command in com.example.data.model.SlashCommands.helpWords -> { viewModel.onInputTextChange(""); showCommandsSheet = true }
            c.run -> { viewModel.onInputTextChange(""); viewModel.sendMessage(c.template) }
            else -> viewModel.onInputTextChange(c.template)
        }
    }
    var showMaintenanceSheet by remember { mutableStateOf(false) }
    var showNeedsYou by remember { mutableStateOf(false) }
    var showTakeOver by remember { mutableStateOf(false) }
    var showRepos by remember { mutableStateOf(false) }
    // A task card tapped in the chat: the same details sheet as tapping the task in Missions
    var chatTaskDetails by remember { mutableStateOf<com.example.data.model.AgentTaskItem?>(null) }
    var takeOverFor by remember { mutableStateOf<String?>(null) }
    // Take over's "View only" box on its hub card, remembered on the phone. View only shows the screen without taking
    // control, so a running task carries on while the owner watches (2026-10-08: opening Take over to watch a task
    // took control and stopped it).
    val uiPrefs = remember { context.getSharedPreferences("deskai_ui", android.content.Context.MODE_PRIVATE) }
    var takeOverViewOnly by remember { mutableStateOf(uiPrefs.getBoolean("takeover_view_only", false)) }
    // Tasks folded in chat (2026-10-11): what's known of each linked task, which strips are open, and the Tasks switch
    var hideTasks by remember { mutableStateOf(uiPrefs.getBoolean("hide_tasks", false)) }
    // Chat or Today (2026-10-28): the frosted switch under the status pill; the app reopens where it was left
    var showToday by remember { mutableStateOf(uiPrefs.getBoolean("show_today", false)) }
    val todayVm: com.example.ui.today.TodayViewModel = viewModel()
    val todayState by todayVm.state.collectAsStateWithLifecycle()
    val todayList = androidx.compose.foundation.lazy.rememberLazyListState()
    val taskStates = remember { androidx.compose.runtime.mutableStateMapOf<String, com.example.data.model.AgentTaskItem>() }
    var openStrips by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(messages) {
        while (true) {
            val ids = messages.filter { com.example.ui.components.isTaskMessage(it) }.mapNotNull { it.linkedTaskId }.distinct()
            ids.filter { id -> taskStates[id]?.let { !com.example.ui.components.isFinished(it) } ?: true }
                .takeLast(30)
                .forEach { id -> viewModel.getTask(id).onSuccess { taskStates[id] = it } }
            kotlinx.coroutines.delay(6000)
        }
    }
    var takeOverWatching by remember { mutableStateOf(false) }      // the open Take over screen is view only
    val needsYou by viewModel.needsYou.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.startNeedsYouPolling() }
    // A "Needs you" notification was tapped: the panel with the screenshot and the answer
    LaunchedEffect(openNeedsYou) {
        if (!openNeedsYou.isNullOrBlank()) {
            viewModel.refreshNeedsYou()
            showNeedsYou = true
            onClearOpenNeedsYou()
        }
    }

    // Open Tasks Sheet directly if tapped from push notification
    LaunchedEffect(openTaskId) {
        if (!openTaskId.isNullOrBlank()) {
            showTasksSheet = true
        }
    }

    val hasEarlierMessages by viewModel.hasEarlierMessages.collectAsStateWithLifecycle()
    // What the list shows: task messages fold into rows and strips (or hide), so there can be far fewer rows than
    // messages. Counting messages put the scroll target past the end and kept the "scroll to bottom" button up
    // while already at the bottom (2026-10-26). Derived, so the 100 ms reply timer doesn't refold every tick.
    val chatEntries by remember(messages, hideTasks, openStrips) {
        derivedStateOf { com.example.ui.components.foldChat(messages, taskStates, hideTasks, openStrips) }
    }
    // The list has a "Load earlier messages" row above the messages when older ones exist
    val lastItemIndex = chatEntries.size - 1 + (if (hasEarlierMessages) 1 else 0)

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
            // The list's own row count (not captured from composition, so never stale)
            val info = listState.layoutInfo
            val totalItems = info.totalItemsCount
            if (totalItems == 0) false
            else {
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible < totalItems - 2
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Slate900
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
      Box(Modifier.fillMaxSize()) {
        // Near-black with a soft glow from the top (2026-10-27 spatial-glass target; was the Circuit texture)
        com.example.ui.components.AtmosphereBackground()
        // Today: the weather now, unboxed behind the status bar, the switch and the date (2026-10-28, Devin's spec)
        if (showToday) com.example.ui.today.WeatherAtmosphere(todayState.page?.header?.weather, todayList)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Connection dot: emerald when the bridge is up, rose when it isn't
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnected) EmeraldConnected else RoseError)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = currentSession?.title ?: "AlwaysOnAgent",
                                fontSize = DeskTextSize.l,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                        // Show or hide task messages in chat (remembered)
                        com.example.ui.components.TasksToggle(showing = !hideTasks) {
                            hideTasks = !hideTasks
                            uiPrefs.edit().putBoolean("hide_tasks", hideTasks).apply()
                        }
                        // Start a fresh conversation (the assistant's memory lives on the server, so nothing is forgotten)
                        IconButton(
                            onClick = { viewModel.createNewSession() },
                            modifier = Modifier.testTag("new_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddComment,
                                contentDescription = "New chat",
                                tint = Slate400
                            )
                        }

                        // Away shift background activity pulsing indicator
                        if (activityState?.active == true) {
                            IconButton(
                                onClick = { showBackgroundWorkSheet = true },
                                modifier = Modifier.testTag("background_activity_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Background Activity",
                                    tint = com.example.ui.theme.PhosphorMint.copy(alpha = pulseAlpha)
                                )
                            }
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
                                            Text("!", fontSize = DeskTextSize.xs, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = "Update App",
                                    tint = if (hasUpdateAvailable) ElectricCyan else Slate400
                                )
                            }
                        }

                        // (The three-dot button only opened the Connection Hub; it's in the menu now)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = com.example.ui.theme.PhosphorMint
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
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // A computer task is waiting for the owner (also when the alert was missed)
                    if (needsYou.isNotEmpty()) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(Brush.horizontalGradient(listOf(Amber900, Amber900.copy(alpha = 0.75f))))
                                .clickable { showNeedsYou = true }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚠️ Needs you (${needsYou.size}): ${needsYou.first().question.take(70)}",
                                 color = Amber200, fontSize = DeskTextSize.m,
                                 fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1,
                                 overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                 modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open ›", color = AmberPending, fontSize = DeskTextSize.s, fontWeight = FontWeight.Bold)
                        }
                    }
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

                    Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
                        com.example.ui.today.ChatTodaySwitch(showToday, { today ->
                            showToday = today
                            uiPrefs.edit().putBoolean("show_today", today).apply()
                        })
                    }

                    if (showToday) {
                        // Each visit: the agent puts a new edition together first when one is due
                        LaunchedEffect(Unit) { todayVm.load() }
                        com.example.ui.today.TodayScreen(
                            state = todayState,
                            onRefresh = { todayVm.load(refresh = true) },
                            onReact = { item, action -> todayVm.react(item, action) },
                            // Ask about a card: back to the chat with it written out, ready to add to and send
                            onAsk = { item ->
                                showToday = false
                                uiPrefs.edit().putBoolean("show_today", false).apply()
                                val about = item.title.ifBlank { item.body }.lineSequence().first().take(140)
                                viewModel.onInputTextChange(
                                    "About “$about”" + (if (item.link.isNotBlank()) " (${item.link})" else "") + ": "
                                )
                            },
                            // A question answered on its card goes to the chat like any message
                            onAnswer = { item, answer ->
                                val sent = viewModel.sendMessage("About your question “${item.body}”: $answer")
                                if (sent) todayVm.react(item, "answered")
                                sent
                            },
                            onSaveRecipe = { text, done -> todayVm.saveRecipe(text, done) },
                            modifier = Modifier.weight(1f),
                            listState = todayList
                        )
                    } else {

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
                                            Text("Load earlier messages", color = ElectricCyan, fontSize = DeskTextSize.s)
                                        }
                                    }
                                }
                            }
                            items(
                                chatEntries,
                                key = { it.key }
                            ) { entry ->
                              when (entry) {
                                is com.example.ui.components.ChatEntry.Strip -> com.example.ui.components.TaskStrip(entry.tasks, entry.expanded) {
                                    openStrips = if (entry.expanded) openStrips - entry.groupKey else openStrips + entry.groupKey
                                }
                                is com.example.ui.components.ChatEntry.TaskRow -> com.example.ui.components.TaskRow(entry.task, onClick = { chatTaskDetails = entry.task })
                                is com.example.ui.components.ChatEntry.Message -> {
                                val message = entry.message
                                MessageBubble(
                                    message = message,
                                    onRunProposal = { msg, prop -> viewModel.runProposal(msg, prop) },
                                    // Computer tasks: run it and watch the Mini's screen in View only (no control)
                                    onRunAndWatchProposal = { msg, prop ->
                                        viewModel.runProposal(msg, prop)
                                        takeOverFor = null; takeOverWatching = true; showTakeOver = true
                                    },
                                    onWatchTask = { _ -> takeOverFor = null; takeOverWatching = true; showTakeOver = true },
                                    onOpenTaskDetails = { t -> chatTaskDetails = t },
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
                                    onActionClick = { action ->
                                        when (action.action) {
                                            "chat" -> {
                                                action.command?.let { cmd ->
                                                    viewModel.sendMessage(cmd)
                                                }
                                            }
                                            "stream" -> {
                                                action.url?.let { url ->
                                                    val cleanBase = config.getResolvedUrl().trimEnd('/')
                                                    val fullUrl = when {
                                                        url.startsWith("http://") || url.startsWith("https://") -> url
                                                        url.startsWith("/") -> "$cleanBase$url"
                                                        url.isNotBlank() -> "$cleanBase/$url"
                                                        else -> url
                                                    }
                                                    playbackVideo = com.example.data.model.VideoItem(
                                                        filename = "Stream",
                                                        sizeMb = 0.0,
                                                        createdAt = "",
                                                        url = fullUrl
                                                    )
                                                }
                                            }
                                            "post" -> {
                                                action.url?.let { postUrl ->
                                                    if (postUrl.contains("/api/videos/") && postUrl.contains("/publish")) {
                                                        // (substringAfter returns the whole string when the marker
                                                        // is missing, so only parse a real publish URL)
                                                        val filename = postUrl.substringAfter("/api/videos/").substringBefore("/publish")
                                                        if (filename.isNotBlank()) {
                                                            scope.launch { viewModel.publishVideoToTikTok(filename) }
                                                        }
                                                    } else {
                                                        // Any other endpoint chip, e.g. "🚀 Push live" / "⚡ Apply update"
                                                        viewModel.runChipAction(action.label, postUrl)
                                                    }
                                                }
                                            }
                                            "none" -> { /* a status label, e.g. "✅ Live" */ }
                                            else -> {
                                                action.command?.let { cmd -> viewModel.sendMessage(cmd) }
                                            }
                                        }
                                    },
                                    onOpenCanvas = { ref -> openCanvas = ref },
                                    onOpenMemoryCheck = { showMemoryCheck = true },
                                    onFeedback = { msg, reaction -> viewModel.onFeedback(msg, reaction) },
                                    serverBaseUrl = config.getResolvedUrl(),
                                    authToken = config.apiKey
                                )
                                }
                              }
                            }
                        }

                        // A brand-new conversation: a friendly prompt instead of a blank screen
                        if (messages.isEmpty() && !hasEarlierMessages) {
                            EmptyState(
                                icon = Icons.Default.AddComment,
                                title = "Ask AlwaysOnAgent anything",
                                message = "Chat, dispatch a mission, or type / for commands.",
                                accent = ElectricCyan,
                                modifier = Modifier.align(Alignment.Center)
                            )
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
                                containerColor = Slate800,
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

                    // Typing "/" shows the commands right here, narrowing as you type
                    // "/retask " lists the saved retasks to tap; fetched when that's typed
                    val wantsRetasks = inputText.lowercase().startsWith("/retask")
                    var retaskList by remember { mutableStateOf<List<com.example.data.model.Retask>>(emptyList()) }
                    LaunchedEffect(wantsRetasks) {
                        if (wantsRetasks) viewModel.fetchRetasks().onSuccess { retaskList = it }
                    }
                    com.example.ui.dialogs.SlashSuggestions(
                        typed = inputText, onPick = pickCommand, retasks = retaskList,
                        onPickRetask = { r -> viewModel.onInputTextChange(""); viewModel.sendMessage("/retask " + r.goal) }
                    )

                    // Chat Input Bar with Quick Commands, Voice Dictation & Command Deck
                    ChatInputBar(
                        text = inputText,
                        onTextChange = { viewModel.onInputTextChange(it) },
                        onSend = {
                            if (inputText.trim().lowercase() in com.example.data.model.SlashCommands.helpWords) {
                                viewModel.onInputTextChange("")
                                showCommandsSheet = true          // /? and /help open the full list
                            } else {
                                viewModel.sendMessage()
                            }
                        },
                        isStreaming = isStreaming,
                        onStopStreaming = { viewModel.stopStreaming() },
                        onOpenCommandPalette = { showCommandPalette = true },
                        attachments = attachments,
                        onAttachFile = { viewModel.attachFile(it) },
                        onRemoveAttachment = { viewModel.removeAttachment(it) },
                        isRefining = isRefining,
                        onRefine = { viewModel.refineInputText() },
                        canUndoRefine = canUndoRefine,
                        onUndoRefine = { viewModel.undoRefine() }
                    )
                    }
                }
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
            onOpenSchedules = { showSchedulesSheet = true },
            onOpenRetasks = { showRetasksSheet = true },
            onOpenCredentials = { showCredentialsSheet = true },
            onOpenTakeOver = { takeOverFor = null; takeOverWatching = takeOverViewOnly; showTakeOver = true },
            takeOverViewOnly = takeOverViewOnly,
            onTakeOverViewOnlyChange = { on ->
                takeOverViewOnly = on
                uiPrefs.edit().putBoolean("takeover_view_only", on).apply()
            },
            onOpenRepos = { showRepos = true },
            onOpenCommands = { showCommandsSheet = true },
            stats = daemonStats,
            onFetchRetasks = { viewModel.fetchRetasks() },
            onFetchCredentials = { viewModel.fetchCredentials() },
            onFetchSchedules = { viewModel.fetchSchedules() },
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
            onRetryTask = { taskId -> viewModel.retryTask(taskId) },
            serverBaseUrl = config.getResolvedUrl(),
            authToken = config.apiKey,
            onGetTask = { tid -> viewModel.getTask(tid) },
            onDownloadVideo = { url, file, onProg -> viewModel.downloadVideo(url, file, onProg) },
            onPublishVideo = { filename -> viewModel.publishVideoToTikTok(filename) }
        )
    }

    // Memory Sheet
    if (showMemorySheet) {
        MemorySheet(
            onDismiss = { showMemorySheet = false },
            onLoad = { viewModel.fetchMemoryOverview() },
            onAddFact = { fact -> viewModel.addMemoryFact(fact) },
            onDeleteFact = { id -> viewModel.deleteMemoryFact(id) },
            onUpdateRecipe = { id, pinned, status -> viewModel.updateRecipe(id, pinned, status) },
            onDeleteRecipe = { id -> viewModel.deleteRecipe(id) }
        )
    }

    // The agent's Saturday memory check (a card under its message opens it)
    if (showMemoryCheck) {
        com.example.ui.dialogs.MemoryCheckSheet(
            onDismiss = { showMemoryCheck = false },
            onLoad = { viewModel.fetchMemoryCheck() },
            onSave = { keep -> viewModel.answerMemoryCheck(keep) }
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

    // Schedules: videos, phone reminders and tasks the agent runs at set times
    if (showSchedulesSheet) {
        com.example.ui.dialogs.SchedulesSheet(
            onDismiss = { showSchedulesSheet = false },
            onFetch = { viewModel.fetchSchedules() },
            onPreview = { cron, kind -> viewModel.previewSchedule(cron, kind) },
            onCreate = { fields -> viewModel.createSchedule(fields) },
            onUpdate = { id, fields -> viewModel.updateSchedule(id, fields) },
            onDelete = { id -> viewModel.deleteSchedule(id) },
            onRunNow = { id -> viewModel.runScheduleNow(id) }
        )
    }

    // Commands: every slash command by group (/? or /help in chat, or the hub's Commands button)
    if (showCommandsSheet) {
        com.example.ui.dialogs.SlashCommandsSheet(
            onDismiss = { showCommandsSheet = false },
            onPick = { c -> showCommandsSheet = false; pickCommand(c) }
        )
    }

    // Retasks: clean computer runs the agent repeats without the AI when the same task is asked again
    if (showRetasksSheet) {
        com.example.ui.dialogs.RetasksSheet(
            onDismiss = { showRetasksSheet = false },
            onFetch = { viewModel.fetchRetasks() },
            onDelete = { id -> viewModel.deleteRetask(id) }
        )
    }

    // Credentials vault: saved accounts in Windows Credential Manager, protected by 9-dot pattern lock
    if (showCredentialsSheet) {
        com.example.ui.dialogs.CredentialsVaultDialog(
            onDismiss = { showCredentialsSheet = false },
            onFetchCredentials = { viewModel.fetchCredentials() },
            onSaveCredential = { domain, user, pw -> viewModel.saveCredential(domain, user, pw) },
            onDeleteCredential = { domain, user -> viewModel.deleteCredential(domain, user) }
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

    // The canvas: a draft full screen, with its versions (from a draft card in the chat)
    openCanvas?.let { ref ->
        com.example.ui.components.CanvasDialog(
            ref = ref,
            serverBaseUrl = config.getResolvedUrl(),
            authToken = config.apiKey,
            loadVersions = { id -> viewModel.fetchCanvasVersions(id) },
            onDiscuss = { r -> viewModel.onInputTextChange("About the draft \"${r.title}\": ") },
            onDismiss = { openCanvas = null }
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

    // Needs you: what computer tasks are waiting on, with their screenshots
    if (showNeedsYou) {
        com.example.ui.dialogs.NeedsYouSheet(
            requests = needsYou,
            onDismiss = { showNeedsYou = false },
            onRefresh = { viewModel.refreshNeedsYou() },
            onAnswer = { id, answer -> viewModel.answerComputerRequest(id, answer) },
            onScreenshot = { path -> viewModel.requestScreenshot(path) },
            // A request is answered by doing it yourself: always real control
            onTakeOver = { id -> showNeedsYou = false; takeOverFor = id; takeOverWatching = false; showTakeOver = true }
        )
    }

    chatTaskDetails?.let { t ->
        com.example.ui.dialogs.TaskDetailsSheet(
            task = t,
            serverBaseUrl = config.getResolvedUrl(),
            authToken = config.apiKey,
            onDismiss = { chatTaskDetails = null },
            onRefreshTask = { tid -> viewModel.getTask(tid) },
            onDownloadVideo = { url, file, onProg -> viewModel.downloadVideo(url, file, onProg) },
            onPublishVideo = { filename -> viewModel.publishVideoToTikTok(filename) }
        )
    }

    // Repos: the owner's GitHub repos like a file explorer, and "Work on this" (docs/API.md "Repos")
    if (showRepos) {
        com.example.ui.dialogs.ReposScreen(
            onClose = { showRepos = false },
            call = { method, path, body, key -> viewModel.github(method, path, body, key) },
            download = { path, dir -> viewModel.githubDownload(path, dir) },
            onStarted = { tid, repo, asked -> viewModel.repoWorkStarted(tid, repo, asked) }
        )
    }

    // Take over: the Mini's screen on the phone, the owner's taps replayed there
    if (showTakeOver) {
        com.example.ui.dialogs.TakeOverScreen(
            requestId = takeOverFor,
            viewOnly = takeOverWatching,
            onClose = { showTakeOver = false; takeOverFor = null },
            call = { action, body -> viewModel.takeover(action, body) },
            fetchFrame = { machine ->
                val view = if (takeOverWatching) "&view=1" else ""
                viewModel.requestScreenshot("/api/computer/takeover/frame?machine=$machine$view")
            },
            fetchActivity = { machine -> viewModel.takeoverActivity(machine) }
        )
    }

    // System Maintenance & Logs (Section 7.5)
    if (showMaintenanceSheet) {
        com.example.ui.dialogs.MaintenanceSheet(
            onDismiss = { showMaintenanceSheet = false },
            onRunBackup = { viewModel.runBackup() },
            onRunCleanup = { viewModel.runCleanup() },
            onRestartAgent = { viewModel.restartAgent() },
            onCheckUptime = { viewModel.agentUptime() },
            onFetchLogs = { limit -> viewModel.fetchSystemLogs(limit) },
            onTestPush = { viewModel.testPush() }
        )
    }

    if (showBackgroundWorkSheet) {
        BackgroundWorkSheet(
            activity = activityState,
            onDismiss = { showBackgroundWorkSheet = false }
        )
    }
}
