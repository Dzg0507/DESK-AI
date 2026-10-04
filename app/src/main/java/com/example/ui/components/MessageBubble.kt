package com.example.ui.components

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: ChatMessage,
    onRunProposal: (ChatMessage, TaskProposal) -> Unit = { _, _ -> },
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Assistant Bot Avatar
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
                    .border(1.dp, ElectricCyan.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = "AlwaysOnAgent",
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 330.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Header for assistant (Model label & latency badge)
            if (!isUser) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = "AlwaysOnAgent v2.1",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                    if (message.latencyMs != null && message.latencyMs > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "⚡ ${message.latencyMs}ms",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = EmeraldConnected
                        )
                    }
                }
            }

            // Message Bubble Box
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (isUser) UserBubbleBackground else AssistantBubbleBackground
                    )
                    .border(
                        1.dp,
                        if (isUser) Color(0xFF3B82F6) else Color(0xFF334155),
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .clickable { showActions = !showActions }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    if (message.status == "sending" && message.content.isEmpty()) {
                        ThinkingIndicator()
                    } else {
                        MarkdownContent(
                            content = message.content,
                            textColor = if (isUser) Color.White else Color(0xFFF1F5F9),
                            serverBaseUrl = serverBaseUrl,
                            authToken = authToken
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
                                    onRun = { onRunProposal(message, proposal) },
                                    onDismiss = { onDismissProposal(message, proposal) }
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

                    // Section 7.7 Live Task Card (Progress, ETA, Abort, Video Result)
                    if (!message.linkedTaskId.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LiveTaskCard(
                            taskId = message.linkedTaskId,
                            onGetTask = { tid -> onGetTask?.invoke(tid) ?: Result.failure(Exception("Get task unavailable")) },
                            onCancelTask = onCancelTask,
                            onRetryTask = onRetryTask,
                            onPlayVideo = onPlayVideo,
                            onActionClick = onActionClick
                        )
                    }

                    // Timestamp
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = timeFormatted,
                        fontSize = 10.sp,
                        color = if (isUser) Color(0xCCFFFFFF) else Color(0xFF94A3B8),
                        modifier = Modifier.align(if (isUser) Alignment.End else Alignment.Start)
                    )
                }
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Copy
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("message", message.content))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            showActions = false
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color(0xFFCBD5E1),
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
    onRun: () -> Unit,
    onDismiss: () -> Unit
) {
    val isAddProject = kind == "add_project"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, if (isAddProject) ElectricCyan else Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = if (isAddProject) "📁" else "🧩", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAddProject) "Add AgentWork Project" else "Suggested Mission",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                }

                if (isAddProject) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ElectricCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "REPOSITORY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            if (!project.isNullOrBlank()) {
                Text(
                    text = "Project: $project",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = instruction,
                fontSize = 12.sp,
                color = Color(0xFFE2E8F0)
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
                            text = if (isAddProject) "Project registered in AgentWork ✓" else "Dispatched to worker pool 🚀",
                            fontSize = 11.sp,
                            color = EmeraldConnected,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                "expired" -> {
                    Text(
                        text = "This suggestion expired (agent restarted)",
                        fontSize = 11.sp,
                        color = RoseError,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
                "dismissed" -> {
                    Text(
                        text = "Dismissed",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
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
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isAddProject) Color(0xFF0284C7) else Color(0xFF0284C7))
                                .clickable { onRun() }
                                .padding(vertical = 6.dp),
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
                                    text = if (isAddProject) "Add project" else "Run it",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // "✕ Dismiss" button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF334155))
                                .clickable { onDismiss() }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color(0xFFCBD5E1),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Dismiss",
                                    fontSize = 11.sp,
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
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CHOICE REQUIRED",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ElectricCyan,
                letterSpacing = 0.5.sp
            )
            if (isAnswered) {
                Text(
                    text = "Answered ✓",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldConnected
                )
            }
        }

        if (question.text.isNotBlank()) {
            Text(
                text = question.text,
                fontSize = 12.sp,
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isSelected -> EmeraldConnected.copy(alpha = 0.25f)
                                isAnswered -> Color(0xFF1E293B).copy(alpha = 0.5f)
                                else -> Color(0xFF1E293B)
                            }
                        )
                        .border(
                            1.dp,
                            when {
                                isSelected -> EmeraldConnected
                                isAnswered -> Color(0xFF334155)
                                else -> ElectricCyan.copy(alpha = 0.6f)
                            },
                            RoundedCornerShape(8.dp)
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
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) EmeraldConnected else if (isAnswered) Color(0xFF94A3B8) else Color.White
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isOtherSelected) EmeraldConnected.copy(alpha = 0.25f) else Color(0xFF1E293B))
                        .border(1.dp, if (isOtherSelected) EmeraldConnected else Color(0xFF475569), RoundedCornerShape(8.dp))
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
                            fontSize = 12.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = if (isOtherSelected) EmeraldConnected else Color(0xFF94A3B8)
                        )
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = if (isOtherSelected) EmeraldConnected else Color(0xFF94A3B8),
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
        Text(text = "Thinking", fontSize = 14.sp, color = Color(0xFF94A3B8))
        Spacer(modifier = Modifier.width(4.dp))
        repeat(3) { i ->
            val lit = phase.toInt() == i
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (lit) ElectricCyan else Color(0xFF475569))
            )
        }
    }
}
