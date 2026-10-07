package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.model.AgentTaskItem
import com.example.data.model.VideoItem
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RoseError
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * A mission's details, opened by tapping its card in the Missions list: what was asked, who or what started it,
 * when it was created, started and finished, the engine, the full result or error, and retries. A video task shows
 * its thumbnail; while the video is still kept it plays exactly like the Video Hub (same frosted badge, same
 * VideoPlayerDialog). Fields come from the task JSON (API.md, "Tasks").
 */
@Composable
fun TaskDetailsSheet(
    task: AgentTaskItem,
    serverBaseUrl: String,
    authToken: String,
    onDismiss: () -> Unit,
    onRefreshTask: (suspend (String) -> Result<AgentTaskItem>)? = null,
    onDownloadVideo: (suspend (url: String, destinationFile: File, onProgress: (Long, Long) -> Unit) -> Result<File>)? = null,
    onPublishVideo: (suspend (filename: String) -> Result<String>)? = null
) {
    val scope = rememberCoroutineScope()
    var current by remember(task.id) { mutableStateOf(task) }
    var playing by remember { mutableStateOf<VideoItem?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    // The list may hold a slimmer copy (the live stream's); fetch the full task once
    LaunchedEffect(task.id) {
        val fresh = onRefreshTask?.invoke(task.id)?.getOrNull()
        if (fresh != null) current = fresh
    }

    fun absolute(url: String?): String? {
        val u = url?.trim().orEmpty()
        if (u.isEmpty()) return null
        return if (u.startsWith("http://") || u.startsWith("https://")) u
        else "${serverBaseUrl.trimEnd('/')}/${u.trimStart('/')}"
    }

    val t = current
    val isCancelled = t.cancelled || t.phase == "cancelled"
    val phaseColor = when {
        isCancelled -> Color(0xFF94A3B8)
        t.phase == "completed" -> EmeraldConnected
        t.phase == "in_progress" -> Color(0xFFF59E0B)
        t.phase == "failed" -> RoseError
        else -> ElectricCyan
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(t.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(phaseColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isCancelled) "CANCELLED" else t.phase.uppercase(),
                                    fontSize = 9.sp, fontWeight = FontWeight.Bold, color = phaseColor,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("#${t.id}", fontSize = 10.sp, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // The video: thumbnail kept after pruning; plays like the Video Hub while the video is kept
                    val thumb = absolute(t.thumbUrl)
                    val video = absolute(t.videoUrl)
                    if (thumb != null || t.videoKept != null) {
                        TaskVideoPreview(
                            thumbUrl = thumb,
                            authToken = authToken,
                            playable = t.videoKept == true && video != null && onDownloadVideo != null,
                            onPlay = {
                                if (video != null) {
                                    val name = t.videoFilename ?: t.result?.filename ?: video.substringAfterLast('/')
                                    playing = VideoItem(filename = name, sizeMb = 0.0, createdAt = "", url = video,
                                        thumbnailUrl = thumb ?: "")
                                }
                            }
                        )
                    }

                    DetailSection("WHO STARTED IT") {
                        DetailText(friendlySource(t.source))
                    }
                    DetailSection("WHAT WAS ASKED") {
                        SelectionContainer { DetailText(t.prompt.ifBlank { t.title }) }
                    }
                    DetailSection("WHEN") {
                        DetailRow("Created", formatStamp(t.createdAt))
                        DetailRow("Started", formatStamp(t.startedAt))
                        DetailRow("Finished", formatStamp(t.completedAt))
                        durationOf(t.startedAt, t.completedAt)?.let { DetailRow("Took", it) }
                    }
                    DetailSection("HOW") {
                        DetailRow("Engine", t.engine)
                        if (!t.engineUsed.isNullOrBlank() && t.engineUsed != t.engine) DetailRow("Engine used", t.engineUsed)
                        DetailRow("Priority", t.priority)
                        DetailRow("Retries", if (t.maxRetries != null) "${t.retryCount} of ${t.maxRetries}" else "${t.retryCount}")
                        t.exitCode?.let { DetailRow("Exit code", it.toString()) }
                    }
                    val summary = t.outputSummary?.trim()
                    if (!summary.isNullOrBlank()) {
                        DetailSection(if (t.needsInput) "NEEDS YOUR INPUT" else "RESULT") {
                            SelectionContainer { DetailText(summary) }
                        }
                    }
                    val error = t.lastError?.trim()
                    if (!error.isNullOrBlank() && error != summary) {
                        DetailSection("ERROR", accent = RoseError) {
                            SelectionContainer { DetailText(error, color = Color(0xFFFCA5A5)) }
                        }
                    }
                    note?.let { Text(it, fontSize = 11.sp, color = Color(0xFF7DD3FC)) }
                }
            }
        }
    }

    // The Video Hub's own player (local cache, then stream)
    val toPlay = playing
    if (toPlay != null && onDownloadVideo != null) {
        VideoPlayerDialog(
            video = toPlay,
            onDismiss = { playing = null },
            onDownloadVideo = onDownloadVideo,
            onPostTikTok = {
                if (onPublishVideo != null) scope.launch {
                    val res = onPublishVideo(toPlay.filename)
                    note = if (res.isSuccess) "🚀 Queued: posting ${toPlay.filename} to TikTok"
                    else "⚠️ ${res.exceptionOrNull()?.message ?: "Couldn't queue the TikTok post"}"
                }
            }
        )
    }
}

@Composable
private fun TaskVideoPreview(thumbUrl: String?, authToken: String, playable: Boolean, onPlay: () -> Unit) {
    val context = LocalContext.current
    // Same request as the Video Hub's thumbnails: the token goes in headers, never in the URL
    val request = remember(thumbUrl, authToken) {
        thumbUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .apply {
                    if (authToken.isNotBlank()) {
                        addHeader("X-HUD-Token", authToken.trim())
                        addHeader("Authorization", "Bearer ${authToken.trim()}")
                    }
                }
                .build()
        }
    }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(266.dp)                     // 9:16, the videos' shape
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                .then(if (playable) Modifier.clickable { onPlay() } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (request != null) {
                SubcomposeAsyncImage(
                    model = request,
                    contentDescription = "Video thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = ElectricCyan, strokeWidth = 2.dp)
                        }
                    },
                    error = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(28.dp))
                        }
                    }
                )
            } else {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(28.dp))
            }
            if (playable) FrostedPlayBadge(size = 44.dp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (playable) "Tap to play" else "Video no longer kept",
            fontSize = 11.sp,
            color = if (playable) ElectricCyan else Color(0xFF94A3B8)
        )
    }
}

@Composable
private fun DetailSection(label: String, accent: Color = Color(0xFF94A3B8), content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E293B))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent, letterSpacing = 0.5.sp)
        content()
    }
}

@Composable
private fun DetailText(text: String, color: Color = Color(0xFFE2E8F0)) {
    Text(text, fontSize = 12.sp, color = color, lineHeight = 17.sp)
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, color = Color(0xFF94A3B8))
        Spacer(modifier = Modifier.width(12.dp))
        Text(value, fontSize = 11.sp, color = Color(0xFFE2E8F0), fontFamily = FontFamily.Monospace)
    }
}

private fun friendlySource(source: String?): String = when {
    source.isNullOrBlank() || source == "unknown" -> "Not recorded (older task)"
    source.startsWith("schedule #") -> "Schedule: ${source.removePrefix("schedule ")}"
    else -> source
}

// The server's timestamps are ISO 8601 in its own local time ("2026-10-07T20:00:12.345-05:00"). java.time needs
// API 26 and the app supports 24, so read the first 19 characters with SimpleDateFormat.
private fun parseStamp(iso: String?): java.util.Date? {
    val s = iso?.trim().orEmpty()
    if (s.length < 19) return null
    return try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(s.substring(0, 19))
    } catch (_: Exception) {
        null
    }
}

private fun formatStamp(iso: String?): String =
    parseStamp(iso)?.let { SimpleDateFormat("MMM d, h:mm:ss a", Locale.US).format(it) } ?: "—"

private fun durationOf(start: String?, end: String?): String? {
    val a = parseStamp(start) ?: return null
    val b = parseStamp(end) ?: return null
    val secs = ((b.time - a.time) / 1000).coerceAtLeast(0)
    return when {
        secs < 60 -> "$secs s"
        secs < 3600 -> "${secs / 60} min ${secs % 60} s"
        else -> "${secs / 3600} h ${(secs % 3600) / 60} min"
    }
}
