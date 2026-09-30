package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Apps
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatAttachment
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.UserBubbleBackground
import java.util.Locale

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isStreaming: Boolean,
    onStopStreaming: () -> Unit,
    onOpenCommandPalette: () -> Unit = {},
    attachments: List<ChatAttachment> = emptyList(),
    onAttachFile: (Uri) -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Files: PDF, Word, text-like files and images (the agent reads their text; images and scans by OCR)
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach(onAttachFile)
    }
    val uploading = attachments.any { it.status == "uploading" }
    val canSend = !uploading && (text.isNotBlank() || attachments.any { it.status == "ready" })

    // Android Speech to Text launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                val newText = if (text.isBlank()) spokenText else "$text $spokenText"
                onTextChange(newText)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF1E293B))
            // Above the keyboard when it's open, above the navigation bar when it isn't
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(vertical = 6.dp)
    ) {
        // Attached files, above the input row
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                attachments.forEach { a -> AttachmentChip(a, onRemove = { onRemoveAttachment(a.localId) }) }
            }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Actions: every command and hub (the command palette)
            IconButton(
                onClick = onOpenCommandPalette,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (text.startsWith("/")) ElectricCyan.copy(alpha = 0.2f) else Color(0xFF1E293B))
                    .border(
                        1.dp,
                        if (text.startsWith("/")) ElectricCyan else Color(0xFF334155),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = "Actions",
                    tint = if (text.startsWith("/")) ElectricCyan else Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Voice Mic Button
            IconButton(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak instruction to AlwaysOnAgent...")
                    }
                    try {
                        speechLauncher.launch(intent)
                    } catch (_: Exception) {
                        // Ignore if speech recognizer unavailable
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Dictation",
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Attach files
            IconButton(
                onClick = {
                    try {
                        fileLauncher.launch(arrayOf("application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "text/*", "application/json", "image/*"))
                    } catch (_: Exception) {
                        // No document picker on this device
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (attachments.isNotEmpty()) ElectricCyan.copy(alpha = 0.2f) else Color(0xFF1E293B))
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attach a file",
                    tint = if (attachments.isNotEmpty()) ElectricCyan else Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Main Text Input Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = if (attachments.isNotEmpty()) "Ask about the file…" else "Talk to AlwaysOnAgent or type /help...",
                        color = Color(0xFF64748B),
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(
                        color = Color(0xFFF8FAFC),
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(ElectricCyan),
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Send or Stop Button
            if (isStreaming) {
                IconButton(
                    onClick = onStopStreaming,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Generating",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        if (canSend) onSend()
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) UserBubbleBackground else Color(0xFF1E293B)
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        tint = if (canSend) Color.White else Color(0xFF475569),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}


/** One attached file: its type, name and how the agent read it (or why it couldn't), with a remove button. */
@Composable
private fun AttachmentChip(a: ChatAttachment, onRemove: () -> Unit) {
    val warn = a.status == "ready" && a.warnings.isNotEmpty()
    val borderColor = when {
        a.status == "error" -> Color(0xFFEF4444)
        warn -> Color(0xFFF59E0B)
        a.status == "ready" -> ElectricCyan.copy(alpha = 0.5f)
        else -> Color(0xFF334155)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(start = 8.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val lower = a.name.lowercase()
        Icon(
            imageVector = when {
                a.kind == "pdf" || lower.endsWith(".pdf") -> Icons.Default.PictureAsPdf
                a.kind == "image" || Regex("\\.(png|jpe?g|webp|gif|bmp|tiff?)$").containsMatchIn(lower) -> Icons.Default.Image
                else -> Icons.Default.Description
            },
            contentDescription = null,
            tint = ElectricCyan,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.width(150.dp)) {
            Text(a.name, color = Color(0xFFF8FAFC), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                a.statusLine(),
                color = when {
                    a.status == "error" -> Color(0xFFFCA5A5)
                    warn -> Color(0xFFFCD34D)
                    else -> Color(0xFF94A3B8)
                },
                fontSize = 10.sp,
                maxLines = 2
            )
        }
        if (a.status == "uploading") {
            CircularProgressIndicator(color = ElectricCyan, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Remove ${a.name}", tint = Color(0xFF94A3B8),
                modifier = Modifier.size(14.dp))
        }
    }
}
