package com.example.ui.components

import com.example.ui.theme.DeskColors
import com.example.ui.theme.DeskType
import com.example.ui.theme.DeskTextSize
import com.example.ui.theme.DeskShapes
import androidx.compose.ui.graphics.Brush
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricCyanGlow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ChatAttachment
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.UserBubbleBackground
import java.util.Locale
import com.example.ui.theme.Amber300
import com.example.ui.theme.AmberPending
import com.example.ui.theme.Red300
import com.example.ui.theme.Red500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

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
    isRefining: Boolean = false,
    onRefine: () -> Unit = {},
    canUndoRefine: Boolean = false,
    onUndoRefine: () -> Unit = {},
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

    // One floating capsule (2026-10-28, Devin's redesign): no bar, no boxed buttons. Actions and files on the left,
    // the text in the middle, the mic (empty) or send (typed) on the right; stop while a reply streams.
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Above the keyboard when it's open, above the navigation bar when it isn't
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)
    ) {
        // Attached files, above the capsule
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                attachments.forEach { a -> AttachmentChip(a, onRemove = { onRemoveAttachment(a.localId) }) }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DeskShapes.sheet)
                .background(DeskColors.Capsule)
                .border(1.dp, DeskColors.CapsuleBorder, DeskShapes.sheet)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val slash = text.startsWith("/")
            CapsuleIcon(Icons.Default.Apps, "Actions", if (slash) DeskColors.Agent else DeskColors.TextSecondary, onOpenCommandPalette)
            CapsuleIcon(Icons.Default.Add, "Attach a file", if (attachments.isNotEmpty()) DeskColors.Agent else DeskColors.TextSecondary) {
                try {
                    fileLauncher.launch(arrayOf("application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "text/*", "application/json", "image/*", "video/*"))
                } catch (_: Exception) {
                    // No document picker on this device
                }
            }
            Spacer(modifier = Modifier.width(6.dp))

            Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                if (text.isEmpty()) {
                    Text(
                        text = if (attachments.isNotEmpty()) "Ask about the file…" else "Message AlwaysOnAgent",
                        style = DeskType.body,
                        color = DeskColors.TextMuted,
                        maxLines = 1
                    )
                }
                // The cursor goes to the end when the text is set from outside (a slash command picked from the
                // pop-up: "/" becomes "/remember ", and typing carries on after it)
                var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(text)) }
                if (field.text != text) {
                    field = androidx.compose.ui.text.input.TextFieldValue(text, androidx.compose.ui.text.TextRange(text.length))
                }
                BasicTextField(
                    value = field,
                    onValueChange = { field = it; if (it.text != text) onTextChange(it.text) },
                    textStyle = DeskType.body.copy(color = DeskColors.TextPrimary),
                    cursorBrush = SolidColor(DeskColors.Signal),
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Enhance (while there's text to enhance): sparkle, or undo after enhancing
            AnimatedVisibility(visible = text.isNotBlank() && !isStreaming, enter = fadeIn(), exit = fadeOut()) {
                if (isRefining) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DeskColors.Agent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    }
                } else {
                    CapsuleIcon(if (canUndoRefine) Icons.AutoMirrored.Filled.Undo else Icons.Default.AutoAwesome,
                        if (canUndoRefine) "Undo Enhance" else "Enhance Prompt", DeskColors.Agent,
                        if (canUndoRefine) onUndoRefine else onRefine)
                }
            }

            when {
                isStreaming -> CapsuleIcon(Icons.Default.Stop, "Stop Generating", DeskColors.Error, onStopStreaming)
                canSend -> CapsuleIcon(Icons.AutoMirrored.Filled.Send, "Send Message", DeskColors.Signal, onSend)
                else -> CapsuleIcon(Icons.Default.Mic, "Voice Dictation", DeskColors.TextSecondary) {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak instruction to AlwaysOnAgent...")
                    }
                    try {
                        speechLauncher.launch(intent)
                    } catch (_: Exception) {
                        // Ignore if speech recognizer unavailable
                    }
                }
            }
        }
    }
}

/** An unboxed icon inside the capsule: a 40 dp touch target, no circle, no border. */
@Composable
private fun CapsuleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** One attached file: its type, name and how the agent read it (or why it couldn't), with a remove button. */
@Composable
private fun AttachmentChip(a: ChatAttachment, onRemove: () -> Unit) {
    val warn = a.status == "ready" && a.warnings.isNotEmpty()
    val borderColor = when {
        a.status == "error" -> Red500
        warn -> AmberPending
        a.status == "ready" -> ElectricCyan.copy(alpha = 0.5f)
        else -> Slate700
    }
    Row(
        modifier = Modifier
            .clip(DeskShapes.card)
            .background(Slate800)
            .border(1.dp, borderColor, DeskShapes.card)
            .padding(start = 6.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val lower = a.name.lowercase()
        val thumbModel = remember(a.localPath, a.thumbB64, a.thumbUrl) { a.thumbModel() }
        if (thumbModel != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(DeskShapes.small)
                    .background(Slate900)
                    .border(1.dp, ElectricCyan.copy(alpha = 0.3f), DeskShapes.small),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = thumbModel,
                    contentDescription = a.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Icon(
                imageVector = when {
                    a.kind == "pdf" || lower.endsWith(".pdf") -> Icons.Default.PictureAsPdf
                    a.kind == "image" || Regex("\\.(png|jpe?g|webp|gif|bmp|tiff?)$").containsMatchIn(lower) -> Icons.Default.Image
                    a.kind == "video" || Regex("\\.(mp4|mov|webm|avi|mkv|m4v)$").containsMatchIn(lower) -> Icons.Default.Videocam
                    else -> Icons.Default.Description
                },
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.width(150.dp)) {
            Text(a.name, color = Slate50, fontSize = DeskTextSize.s, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                a.statusLine(),
                color = when {
                    a.status == "error" -> Red300
                    warn -> Amber300
                    else -> Slate400
                },
                fontSize = DeskTextSize.xs,
                maxLines = 2
            )
        }
        if (a.status == "uploading") {
            CircularProgressIndicator(color = ElectricCyan, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Remove ${a.name}", tint = Slate400,
                modifier = Modifier.size(14.dp))
        }
    }
}
