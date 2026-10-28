package com.example.ui.components

import com.example.ui.theme.DeskTextSize
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.Brush
import com.example.ui.theme.CardGradient
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.DeskShapes
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AgentTaskItem
import com.example.data.model.ChatMessage
import com.example.data.model.ChatQuestion
import com.example.data.model.TaskProposal
import com.example.data.model.TaskAction
import com.example.ui.theme.AssistantBubbleBackground
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RoseError
import com.example.ui.theme.UserBubbleBackground
import com.example.ui.theme.DeskColors
import com.example.ui.theme.DeskRadius
import com.example.ui.theme.DeskType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Description
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.MessageAttachment
import java.io.File
import com.example.ui.theme.Blue500
import com.example.ui.theme.ElectricCyanGlow
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

// 2026-10-27 redesign: the user's bubble is solid cerulean (no gradient, no outline), the agent's is the card surface
// with the mint hairline; the "tail" corner is the small radius
private val UserBubbleShape = RoundedCornerShape(topStart = DeskRadius.card, topEnd = DeskRadius.card,
    bottomStart = DeskRadius.card, bottomEnd = DeskRadius.small / 2)
private val AssistantBubbleShape = RoundedCornerShape(topStart = DeskRadius.small / 2, topEnd = DeskRadius.card,
    bottomStart = DeskRadius.card, bottomEnd = DeskRadius.card)

@Composable
fun MessageBubble(
    message: ChatMessage,
    onRunProposal: (ChatMessage, TaskProposal) -> Unit = { _, _ -> },
    onRunAndWatchProposal: ((ChatMessage, TaskProposal) -> Unit)? = null,
    onWatchTask: ((String) -> Unit)? = null,
    onOpenTaskDetails: ((com.example.data.model.AgentTaskItem) -> Unit)? = null,
    onDismissProposal: (ChatMessage, TaskProposal) -> Unit = { _, _ -> },
    onDeleteMessage: (ChatMessage) -> Unit = {},
    onRegenerate: (ChatMessage) -> Unit = {},
    onSpeak: (String) -> Unit = {},
    onAnswerQuestion: (ChatMessage, String) -> Unit = { _, _ -> },
    onFocusInput: () -> Unit = {},
    onGetTask: (suspend (String) -> Result<AgentTaskItem>)? = null,
    onCancelTask: (suspend (String) -> Result<String>)? = null,
    onRetryTask: (suspend (String) -> Result<String>)? = null,
    onPlayVideo: ((url: String, filename: String) -> Unit)? = null,
    onActionClick: ((TaskAction) -> Unit)? = null,
    onOpenCanvas: ((com.example.data.model.CanvasRef) -> Unit)? = null,
    onOpenMemoryCheck: (() -> Unit)? = null,
    onFeedback: ((ChatMessage, String) -> Unit)? = null,
    serverBaseUrl: String = "",
    authToken: String = "",
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val context = LocalContext.current
    val timeFormatted = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    var showActions by remember { mutableStateOf(false) }

    // 2026-10-28, Devin's redesign: no avatar, no "AlwaysOnAgent" header, no latency. The agent's reply reads straight
    // off the canvas; the owner's is a compact glass capsule. Times show when a message is tapped.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (isUser) 12.dp else 20.dp, vertical = if (isUser) 4.dp else 8.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = if (isUser) Modifier.widthIn(max = 330.dp) else Modifier.weight(1f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .then(if (isUser) Modifier.glass(UserBubbleShape) else Modifier)
                    .clickable { showActions = !showActions }
                    .padding(horizontal = if (isUser) 14.dp else 0.dp, vertical = if (isUser) 9.dp else 0.dp)
            ) {
                Column {
                    val attached = remember(message.attachmentsJson) { message.getAttachments() }
                    if (attached.isNotEmpty()) {
                        SentAttachments(attached)
                        if (message.content.isNotBlank()) Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (message.status == "sending" && message.content.isEmpty()) {
                        ThinkingIndicator()
                    } else if (message.content.isNotBlank() || attached.isEmpty()) {
                        MarkdownContent(
                            content = message.content,
                            textColor = DeskColors.TextPrimary,
                            serverBaseUrl = serverBaseUrl,
                            authToken = authToken
                        )

                    }
                    // An image being made in the background: the bubble fills in when it's ready
                    if (message.status == "imaging") {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.width(180.dp).height(3.dp).clip(CircleShape),
                            color = DeskColors.Agent,
                            trackColor = DeskColors.Agent.copy(alpha = 0.16f),
                            gapSize = 0.dp
                        )
                    }

                    // Interactive Proposal Cards matching AlwaysOnAgent & Telegram (supports multiple proposals)
                    val proposals = remember(message.content, message.proposalsJson, message.proposalInstruction, message.proposalState) {
                        val fromJson = message.getProposals()
                        if (fromJson.isNotEmpty()) {
                            fromJson
                        } else {
                            val extracted = com.example.util.ProposalExtractor.extractAllInstructions(message.content)
                            val proj = message.proposalProject ?: com.example.util.ProposalExtractor.extractProject(message.content)
                            extracted.map { instr ->
                                TaskProposal(
                                    id = "local_${instr.hashCode()}",
                                    token = "",
                                    instruction = instr,
                                    project = proj,
                                    isLocal = true,
                                    state = message.proposalState ?: "pending"
                                )
                            }
                        }
                    }

                    if (proposals.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            proposals.forEach { proposal ->
                                ProposalCard(
                                    instruction = proposal.instruction,
                                    project = proposal.project,
                                    state = proposal.state,
                                    kind = proposal.kind,
                                    phone = proposal.phone,
                                    onRun = { onRunProposal(message, proposal) },
                                    onDismiss = { onDismissProposal(message, proposal) },
                                    onRunAndWatch = if (proposal.kind == "computer" && onRunAndWatchProposal != null) {
                                        { onRunAndWatchProposal(message, proposal) }
                                    } else null
                                )
                            }
                        }
                    }

                    // Section 7.6 Tap-to-answer Multiple Choice Question
                    val question = remember(message.questionJson) { message.getQuestion() }
                    if (question != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        QuestionChoicesCard(
                            question = question,
                            onAnswer = { opt -> onAnswerQuestion(message, opt) },
                            onFocusOther = onFocusInput
                        )
                    }

                    // A draft this reply wrote or changed: opens the canvas
                    val canvasRef = message.getCanvas()
                    if (canvasRef != null && onOpenCanvas != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        CanvasCard(ref = canvasRef, onOpen = onOpenCanvas)
                    }

                    // The agent's Saturday memory check: opens the checklist
                    if (onOpenMemoryCheck != null &&
                        message.modelUsed?.endsWith(com.example.data.repository.MEMORY_CHECK_LABEL) == true) {
                        Spacer(modifier = Modifier.height(10.dp))
                        com.example.ui.dialogs.MemoryCheckCard(onOpen = onOpenMemoryCheck)
                    }

                    // Section 7.7 Live Task Card (Progress, ETA, Abort, Video Result)
                    if (!message.linkedTaskId.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LiveTaskCard(
                            taskId = message.linkedTaskId,
                            onGetTask = { tid -> onGetTask?.invoke(tid) ?: Result.failure(Exception("Get task unavailable")) },
                            onCancelTask = onCancelTask,
                            onRetryTask = onRetryTask,
                            onPlayVideo = onPlayVideo,
                            onActionClick = onActionClick,
                            onWatch = onWatchTask,
                            onOpenDetails = onOpenTaskDetails
                        )
                    }

                    // The "why" line: why this proactive message was sent
                    if (!message.why.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = message.why,
                            fontSize = DeskTextSize.xs,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = Slate400,
                            lineHeight = 15.sp
                        )
                    }

                    // Proactive message reaction chips (thumbs up, thumbs down, less like this)
                    val isProactive = !isUser && (message.inboxId != null || message.modelUsed?.startsWith("AlwaysOnAgent ·") == true)
                    if (isProactive && onFeedback != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FeedbackChip(
                                label = "👍",
                                selected = message.feedback == "thumbs_up",
                                onClick = { onFeedback(message, "thumbs_up") }
                            )
                            FeedbackChip(
                                label = "👎",
                                selected = message.feedback == "thumbs_down",
                                onClick = { onFeedback(message, "thumbs_down") }
                            )
                            FeedbackChip(
                                label = "less like this",
                                selected = message.feedback == "less_like_this",
                                onClick = { onFeedback(message, "less_like_this") }
                            )
                        }
                    }

                }
            }

            // The time, only when the message is tapped (with its actions below)
            if (showActions) {
                Text(
                    text = timeFormatted,
                    style = DeskType.caption,
                    color = DeskColors.TextMuted,
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp)
                )
            }

            // Expandable Action Toolbar
            AnimatedVisibility(
                visible = showActions,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(DeskShapes.pill)
                        .background(Slate800)
                        .border(1.dp, Slate700, DeskShapes.pill)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Copy
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("message", com.example.data.remote.MediaAuth.stripTokensInText(message.content)))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            showActions = false
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Slate300,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // TTS Read Aloud
                    IconButton(
                        onClick = {
                            onSpeak(message.content)
                            showActions = false
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Read Aloud",
                            tint = ElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Regenerate (for assistant)
                    if (!isUser) {
                        IconButton(
                            onClick = {
                                onRegenerate(message)
                                showActions = false
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = EmeraldConnected,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Delete
                    IconButton(
                        onClick = {
                            onDeleteMessage(message)
                            showActions = false
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = RoseError,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProposalCard(
    instruction: String,
    project: String?,
    state: String,
    kind: String = "task",
    phone: String? = null,                   // text/call: {"name","number","body"}: this phone opens Messages or the dialer
    onRun: () -> Unit,
    onDismiss: () -> Unit,
    onRunAndWatch: (() -> Unit)? = null      // computer tasks: run it and open Take over in View only
) {
    val isAddProject = kind == "add_project"
    // Texts and calls (2026-10-11): the agent drafts, this phone does it. The tap opens Messages filled in (or the
    // dialer with the number); the owner sends or calls; then the agent is told it was opened.
    val isPhone = kind == "text" || kind == "call"
    val context = androidx.compose.ui.platform.LocalContext.current
    val info = remember(phone) { try { org.json.JSONObject(phone ?: "{}") } catch (_: Exception) { org.json.JSONObject() } }
    // Several numbers for the person in the phone's contacts: the owner picks (label + number)
    var pickFrom by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    fun openWith(number: String) {
        try {
            val intent = if (kind == "text") {
                android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:" + android.net.Uri.encode(number)))
                    .putExtra("sms_body", info.optString("body"))
            } else {
                android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + android.net.Uri.encode(number)))
            }
            context.startActivity(intent)
            onRun()
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Couldn't open ${if (kind == "text") "Messages" else "the dialer"}: ${e.message}",
                android.widget.Toast.LENGTH_LONG).show()
        }
    }
    // Not in Google Contacts (number ""): look the name up in the phone's own contacts (2026-10-11: the owner's
    // contacts live on the phone). One number: straight in; several: pick; none: Messages/dialer with no recipient.
    fun lookUpAndOpen() {
        val found = com.example.util.PhoneContacts.numbersFor(context, info.optString("name"))
        when {
            found.size == 1 -> openWith(found[0].second)
            found.size > 1 -> pickFrom = found
            else -> {
                android.widget.Toast.makeText(context, "${info.optString("name")} isn't in your contacts: pick who in Messages",
                    android.widget.Toast.LENGTH_LONG).show()
                openWith("")
            }
        }
    }
    val askContacts = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) lookUpAndOpen() else openWith("") }
    val openOnPhone: () -> Unit = {
        val number = info.optString("number")
        when {
            number.isNotBlank() -> openWith(number)
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED -> lookUpAndOpen()
            else -> askContacts.launch(android.Manifest.permission.READ_CONTACTS)
        }
    }
    pickFrom?.let { options ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pickFrom = null },
            title = { Text("Which number for ${info.optString("name")}?") },
            text = {
                Column {
                    options.forEach { (label, number) ->
                        Text(
                            "$label · $number",
                            color = Slate200,
                            fontSize = DeskTextSize.base,
                            modifier = Modifier.fillMaxWidth().clickable { pickFrom = null; openWith(number) }.padding(vertical = 10.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pickFrom = null }) { Text("Cancel") } }
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DeskShapes.card)
            .background(Brush.verticalGradient(listOf(Slate900, DeepNavy)))
            .border(1.dp, if (isAddProject) ElectricCyan else ElectricCyan.copy(alpha = 0.4f), DeskShapes.card)
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = when { isAddProject -> "📁"; kind == "text" -> "💬"; kind == "call" -> "📞"; else -> "🧩" }, fontSize = DeskTextSize.base)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when { isAddProject -> "Add AgentWork Project"; kind == "text" -> "Text"; kind == "call" -> "Call"; else -> "Suggested Mission" },
                        fontSize = DeskTextSize.s,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                }

                if (isAddProject) {
                    Box(
                        modifier = Modifier
                            .clip(DeskShapes.small)
                            .background(ElectricCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "REPOSITORY",
                            fontSize = DeskTextSize.xs,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan,
                            fontFamily = com.example.ui.theme.JetBrainsMono
                        )
                    }
                }
            }

            if (!project.isNullOrBlank()) {
                Text(
                    text = "Project: $project",
                    fontSize = DeskTextSize.xs,
                    fontFamily = com.example.ui.theme.JetBrainsMono,
                    color = Slate400,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = instruction,
                fontSize = DeskTextSize.s,
                color = Slate200
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (state) {
                "run", "running" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Dispatched",
                            tint = EmeraldConnected,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                isAddProject -> "Project registered in AgentWork ✓"
                                kind == "text" -> "Opened in Messages ✓"
                                kind == "call" -> "Opened the dialer ✓"
                                else -> "Dispatched to worker pool 🚀"
                            },
                            fontSize = DeskTextSize.xs,
                            color = EmeraldConnected,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                "expired" -> {
                    Text(
                        text = "This suggestion expired (agent restarted)",
                        fontSize = DeskTextSize.xs,
                        color = RoseError,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
                "dismissed" -> {
                    Text(
                        text = "Dismissed",
                        fontSize = DeskTextSize.xs,
                        color = Slate500,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Action button (Run it or Add project)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(DeskShapes.chip)
                                .background(Brush.horizontalGradient(listOf(ElectricCyanGlow, NeonIndigo)))
                                .clickable { if (isPhone) openOnPhone() else onRun() }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isAddProject) Icons.Default.Add else Icons.Default.PlayArrow,
                                    contentDescription = if (isAddProject) "Add Project" else "Run",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when { isAddProject -> "Add project"; kind == "text" -> "Open in Messages"; kind == "call" -> "Call"; else -> "Run it" },
                                    fontSize = DeskTextSize.xs,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Computer tasks: run it and watch it in View only (2026-10-10, the owner's ask)
                        if (onRunAndWatch != null) {
                            Box(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .clip(DeskShapes.chip)
                                    .background(ElectricCyan.copy(alpha = 0.14f))
                                    .border(1.dp, ElectricCyan.copy(alpha = 0.6f), DeskShapes.chip)
                                    .clickable { onRunAndWatch() }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = "Run and view only",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Run & View only", fontSize = DeskTextSize.xs, fontWeight = FontWeight.Bold,
                                         color = ElectricCyan)
                                }
                            }
                        }

                        // "✕ Dismiss" button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(DeskShapes.small)
                                .background(Slate700)
                                .clickable { onDismiss() }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Slate300,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Dismiss",
                                    fontSize = DeskTextSize.xs,
                                    color = Slate300
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
fun QuestionChoicesCard(
    question: ChatQuestion,
    onAnswer: (String) -> Unit,
    onFocusOther: () -> Unit
) {
    val isAnswered = question.answeredOption != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DeskShapes.card)
            .background(Brush.verticalGradient(listOf(Slate900, DeepNavy)))
            .border(1.dp, if (isAnswered) EmeraldConnected.copy(alpha = 0.4f) else ElectricCyan.copy(alpha = 0.4f), DeskShapes.card)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CHOICE REQUIRED",
                fontSize = DeskTextSize.xs,
                fontWeight = FontWeight.Bold,
                color = ElectricCyan,
                letterSpacing = 0.5.sp
            )
            if (isAnswered) {
                StatusPill(text = "Answered", color = EmeraldConnected)
            }
        }

        if (question.text.isNotBlank()) {
            Text(
                text = question.text,
                fontSize = DeskTextSize.s,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        // Tappable options
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            question.options.forEach { option ->
                val isSelected = question.answeredOption == option
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DeskShapes.control)
                        .background(
                            when {
                                isSelected -> EmeraldConnected.copy(alpha = 0.25f)
                                isAnswered -> Slate800.copy(alpha = 0.5f)
                                else -> Slate800
                            }
                        )
                        .border(
                            1.dp,
                            when {
                                isSelected -> EmeraldConnected
                                isAnswered -> Slate700
                                else -> ElectricCyan.copy(alpha = 0.6f)
                            },
                            DeskShapes.control
                        )
                        .clickable(enabled = !isAnswered) {
                            onAnswer(option)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            fontSize = DeskTextSize.s,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) EmeraldConnected else if (isAnswered) Slate400 else Color.White
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = EmeraldConnected,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            if (question.allowOther) {
                val isOtherSelected = isAnswered && !question.options.contains(question.answeredOption)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DeskShapes.control)
                        .background(if (isOtherSelected) EmeraldConnected.copy(alpha = 0.25f) else Slate800)
                        .border(1.dp, if (isOtherSelected) EmeraldConnected else Slate600, DeskShapes.control)
                        .clickable(enabled = !isAnswered) {
                            onFocusOther()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isOtherSelected) "Other: ${question.answeredOption}" else "Other… (Type answer)",
                            fontSize = DeskTextSize.s,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = if (isOtherSelected) EmeraldConnected else Slate400
                        )
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = if (isOtherSelected) EmeraldConnected else Slate400,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    // "Thinking" with three dots pulsing one after another
    val transition = rememberInfiniteTransition(label = "thinking")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Restart),
        label = "thinking_phase"
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(text = "Thinking", fontSize = DeskTextSize.base, color = Slate400)
        Spacer(modifier = Modifier.width(4.dp))
        repeat(3) { i ->
            val lit = phase.toInt() == i
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (lit) ElectricCyan else Slate600)
            )
        }
    }
}

/** The files a message carried: images drawn (tap for full screen), other files as a small labelled row. */
@Composable
private fun SentAttachments(attached: List<MessageAttachment>) {
    var fullScreen by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        attached.forEach { a ->
            val file = a.localPath?.let { File(it) }?.takeIf { it.exists() }
            if (a.isImage && file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = a.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .heightIn(max = 320.dp)
                        .clip(DeskShapes.small)
                        .clickable { fullScreen = file.absolutePath }
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(DeskShapes.small)
                        .background(Color(0x33000000))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = when (a.kind) {
                            "image" -> Icons.Default.Image
                            "pdf" -> Icons.Default.PictureAsPdf
                            "video" -> Icons.Default.Videocam
                            else -> Icons.Default.Description
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(a.name, color = Color.White, fontSize = DeskTextSize.m, maxLines = 1)
                }
            }
        }
    }
    fullScreen?.let { path ->
        Dialog(onDismissRequest = { fullScreen = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullScreen = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun FeedbackChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(DeskShapes.card)
            .background(if (selected) ElectricCyan.copy(alpha = 0.25f) else Slate800.copy(alpha = 0.6f))
            .border(
                1.dp,
                if (selected) ElectricCyan else Slate700,
                DeskShapes.card
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = DeskTextSize.xs,
            color = if (selected) ElectricCyan else Slate200,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
