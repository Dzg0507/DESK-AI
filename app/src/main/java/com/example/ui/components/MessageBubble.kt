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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
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
import com.example.data.model.ChatMessage
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
    onRunProposal: (ChatMessage) -> Unit = {},
    onDismissProposal: (ChatMessage) -> Unit = {},
    onDeleteMessage: (ChatMessage) -> Unit = {},
    onRegenerate: (ChatMessage) -> Unit = {},
    onSpeak: (String) -> Unit = {},
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
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(15.dp),
                                        strokeWidth = 2.dp,
                                        color = ElectricCyan
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "AlwaysOnAgent Busy",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElectricCyan
                                    )
                                }
                                Text(
                                    text = "Processing...",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = ElectricCyan,
                                trackColor = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                ProgressStepRow(done = true, active = false, label = "Dispatched instruction to host machine")
                                ProgressStepRow(done = false, active = true, label = "Executing autonomous tools & workflows")
                                ProgressStepRow(done = false, active = false, label = "Formatting response & verifying media")
                            }
                        }
                    } else {
                        MarkdownContent(
                            content = message.content,
                            textColor = if (isUser) Color.White else Color(0xFFF1F5F9),
                            serverBaseUrl = serverBaseUrl,
                            authToken = authToken
                        )

                        // Live Streaming Progress Pulse when content is actively arriving
                        if (message.status == "sending") {
                            val cursorTransition = rememberInfiniteTransition(label = "cursor")
                            val cursorAlpha by cursorTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 0.1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(500),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "cursor_alpha"
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Streaming output...",
                                    fontSize = 11.sp,
                                    color = ElectricCyan,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = " ▌",
                                    fontSize = 11.sp,
                                    color = ElectricCyan.copy(alpha = cursorAlpha),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp)),
                                color = ElectricCyan,
                                trackColor = Color(0xFF1E293B)
                            )
                        }
                    }

                    // Interactive Proposal Cards matching AlwaysOnAgent & Telegram
                    val proposals = remember(message.content, message.proposalInstruction) {
                        if (!message.proposalInstruction.isNullOrBlank()) {
                            listOf(message.proposalInstruction)
                        } else {
                            com.example.util.ProposalExtractor.extractAllInstructions(message.content)
                        }
                    }
                    val effectiveProject = message.proposalProject 
                        ?: com.example.util.ProposalExtractor.extractProject(message.content)

                    if (proposals.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            proposals.forEach { proposalInstr ->
                                ProposalCard(
                                    instruction = proposalInstr,
                                    project = effectiveProject,
                                    state = message.proposalState ?: "pending",
                                    onRun = { onRunProposal(message.copy(proposalInstruction = proposalInstr, proposalProject = effectiveProject)) },
                                    onDismiss = { onDismissProposal(message.copy(proposalInstruction = proposalInstr, proposalProject = effectiveProject)) }
                                )
                            }
                        }
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
    onRun: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🧩", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Suggested Mission",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )
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
                "run" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Dispatched",
                            tint = EmeraldConnected,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Dispatched to worker pool",
                            fontSize = 11.sp,
                            color = EmeraldConnected,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
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
                        // "▶️ Run it" button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0284C7))
                                .clickable { onRun() }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Run",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Run it",
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
private fun ProgressStepRow(done: Boolean, active: Boolean, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = when {
                done -> "✓"
                active -> "⚡"
                else -> "○"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                done -> EmeraldConnected
                active -> ElectricCyan
                else -> Color(0xFF64748B)
            },
            modifier = Modifier.width(18.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = when {
                done -> Color(0xFFCBD5E1)
                active -> Color(0xFFE2E8F0)
                else -> Color(0xFF64748B)
            },
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
