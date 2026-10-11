package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Retask
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import com.example.ui.components.SheetHeader
import com.example.ui.components.StatusNote
import com.example.ui.components.TagChip
import com.example.ui.components.deskCard
import com.example.ui.components.deskSheet
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate500
import com.example.ui.theme.Violet300
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/** Retasks' color: the same violet as their lines in a task's log ("What it did", View only). */
private val RetaskViolet = Violet300

/** "Oct 11 09:35" from the tool's local time ("2026-10-11T09:35:48"). */
private fun shortTime(iso: String?): String {
    if (iso.isNullOrBlank()) return "never"
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(iso)
        SimpleDateFormat("MMM d HH:mm", Locale.getDefault()).format(parsed!!)
    } catch (_: Exception) {
        iso.take(16).replace('T', ' ')
    }
}

/**
 * Retasks (2026-10-11, the owner's idea): a computer task that ran clean is saved, and the SAME task asked again
 * (same wording) is repeated without the AI. A question is repeated up to the reading point and its answer is
 * always read fresh; written text is always written fresh. Here the owner sees them and deletes one.
 */
@Composable
fun RetasksSheet(
    onDismiss: () -> Unit,
    onFetch: suspend () -> Result<List<Retask>>,
    onDelete: suspend (id: String) -> Result<Unit>
) {
    val scope = rememberCoroutineScope()
    var retasks by remember { mutableStateOf<List<Retask>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }     // message, success

    fun refresh() {
        scope.launch {
            isLoading = true
            onFetch().onSuccess { retasks = it }.onFailure { status = "⚠️ ${it.message}" to false }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .deskSheet()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader(
                    title = "Retasks",
                    icon = Icons.Default.Autorenew,
                    accent = RetaskViolet,
                    subtitle = "Clean runs, repeated without the AI",
                    onClose = onDismiss
                ) {
                    IconButton(onClick = { refresh() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = RetaskViolet, modifier = Modifier.size(18.dp))
                    }
                }
                status?.let { (msg, ok) ->
                    Spacer(modifier = Modifier.height(8.dp))
                    StatusNote(msg, modifier = Modifier.fillMaxWidth(), color = if (ok) EmeraldConnected else RoseError)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        isLoading && retasks.isEmpty() -> LoadingState("Loading retasks…", color = RetaskViolet)
                        retasks.isEmpty() -> EmptyState(
                            icon = Icons.Default.Autorenew,
                            title = "No retasks yet",
                            message = "When a computer task runs clean (no errors, nothing it needed you for), it's " +
                                    "saved here. Ask the same thing again and its steps are repeated without the AI. " +
                                    "Answers are always read fresh, and anything it writes is written fresh.",
                            accent = RetaskViolet
                        )
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(retasks, key = { it.id }) { r ->
                                RetaskCard(r) {
                                    scope.launch {
                                        onDelete(r.id)
                                            .onSuccess { retasks = retasks.filter { it.id != r.id }; status = "🗑️ Deleted. That task will be done by the AI next time" to true }
                                            .onFailure { status = "⚠️ ${it.message}" to false }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RetaskCard(r: Retask, onDelete: () -> Unit) {
    var confirmDelete by remember(r.id) { mutableStateOf(false) }
    val question = r.kind == "info"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .deskCard(borderColor = RetaskViolet.copy(alpha = 0.35f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(30.dp).clip(CircleShape).background(RetaskViolet.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (question) Icons.Default.QuestionAnswer else Icons.Default.Autorenew, contentDescription = null,
                     tint = RetaskViolet, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(r.goal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 4,
                     overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(2.dp))
                Text(if (question) "Repeats up to the reading point, then reads the answer fresh"
                     else "Repeats the whole task without the AI", fontSize = 11.sp, color = RetaskViolet)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TagChip(text = "${r.steps} step${if (r.steps == 1) "" else "s"}", color = Slate300)
            TagChip(text = "used ${r.uses}×", color = Slate300)
            if (r.fails > 0) TagChip(text = "went off course ${r.fails}×", color = RoseError)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("Saved ${shortTime(r.created)} · last used ${shortTime(r.lastUsed)}", fontSize = 10.sp, color = Slate500,
             fontFamily = FontFamily.Monospace)
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = RoseError, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (confirmDelete) "Tap again to delete" else "Delete", fontSize = 11.sp, color = RoseError)
            }
        }
    }
}
