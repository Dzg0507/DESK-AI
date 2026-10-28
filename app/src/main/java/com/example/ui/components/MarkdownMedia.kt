package com.example.ui.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.DeskType
import com.example.ui.theme.DeskColors
import com.example.ui.theme.DeskTextSize
import com.example.ui.theme.DeskShapes
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * A video, audio file or PDF the agent showed from the Mini (its mini_files tool, 2026-10-27): "[▶ clip.mp4 (12 MB)](url)".
 * Tap: downloaded with the token in a header (like images; never in the URL) into the cache, then opened in the
 * phone's own player or PDF viewer, which handle mp4, wav, mp3 and the rest natively. Already downloaded: opens at once.
 */
@Composable
fun MarkdownMedia(url: String, label: String, serverBaseUrl: String = "", authToken: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Tap to open") }
    var busy by remember { mutableStateOf(false) }

    val fullUrl = remember(url, serverBaseUrl) {
        if (url.startsWith("http://") || url.startsWith("https://")) url
        else serverBaseUrl.trimEnd('/') + (if (url.startsWith("/")) url else "/$url")
    }
    val fileName = remember(url) {
        URLDecoder.decode(url.substringAfterLast('/').substringBefore('?'), "UTF-8").replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    val onTap: () -> Unit = {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                download(context, fullUrl, fileName, serverBaseUrl, authToken) { done, total ->
                    status = if (total > 0) "Downloading… ${done * 100 / total}%" else "Downloading…"
                }
            }
            busy = false
            result.onSuccess { file ->
                status = "Tap to open"
                open(context, file)
            }.onFailure {
                status = "Couldn't download: ${it.message ?: "error"}"
            }
        }
    }
    // "▶ cabin bear.mp4 (12.3 MB)": the name, and the size apart from it
    val title = label.drop(2).trim().ifEmpty { fileName }
    val size = Regex("""\s*\(([0-9.]+ [KMG]?B)\)$""").find(title)
    val name = size?.let { title.removeRange(it.range) } ?: title
    // A video from the Mini has a still at <its url>/thumbnail; one in the TikTok folder is the videos hub's own
    // (2026-10-27, Devin: "That video really really needs a thumbnail")
    val thumbRequest = remember(fullUrl, authToken) {
        val isVideo = fileName.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
        if (!isVideo || "/api/files/m/" !in fullUrl) null
        else ImageRequest.Builder(context).data(fullUrl.substringBefore('?') + "/thumbnail").crossfade(true).apply {
            if (authToken.isNotBlank() && com.example.data.remote.MediaAuth.isOwnServer(fullUrl, serverBaseUrl)) {
                addHeader("X-HUD-Token", authToken.trim())
            }
        }.build()
    }

    if (thumbRequest != null) {
        // Full bleed (2026-10-27 visual target): the still fills the card, a scrim darkens its lower part, a frosted
        // play button in the middle and the name, size and status on a glass pill at the bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(DeskShapes.media)
                .background(DeskColors.Raised2)
                .clickable(enabled = !busy, onClick = onTap),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = thumbRequest,
                contentDescription = "Video thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier.matchParentSize().background(
                    Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color.Transparent, 1f to DeskColors.Scrim))
            )
            Box(
                modifier = Modifier.size(56.dp).glass(CircleShape, fill = DeskColors.GlassStrong),
                contentAlignment = Alignment.Center
            ) {
                if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                else Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White,
                    modifier = Modifier.size(32.dp))
            }
            Text(
                text = listOfNotNull(name, size?.groupValues?.get(1), status.takeIf { it != "Tap to open" })
                    .joinToString(" · "),
                style = DeskType.label,
                color = DeskColors.TextPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .glass(fill = DeskColors.GlassStrong)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Box(modifier = Modifier.matchParentSize().border(1.dp, DeskColors.GlassBorder, DeskShapes.media))
        }
        return
    }

    // Audio and PDFs: a compact row, the icon in a tinted tile
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .deskCardSurface(DeskShapes.card, fill = DeskColors.Raised2)
            .clickable(enabled = !busy, onClick = onTap)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(DeskShapes.small).background(DeskColors.Agent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(label.take(2).trim().ifEmpty { "▶" }, fontSize = DeskTextSize.l, color = DeskColors.Agent)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = DeskType.body, fontWeight = FontWeight.SemiBold, color = DeskColors.TextPrimary,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(listOfNotNull(size?.groupValues?.get(1), status).joinToString(" · "), style = DeskType.mono,
                color = DeskColors.TextSecondary)
        }
    }
}

private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "mkv", "webm", "avi", "m4v")

private fun download(
    context: Context, url: String, name: String, serverBaseUrl: String, authToken: String,
    onProgress: (Long, Long) -> Unit
): Result<File> = runCatching {
    val dir = File(context.cacheDir, "media").apply { mkdirs() }
    val target = File(dir, name)
    val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    val req = Request.Builder().url(url).apply {
        // Only our own agent gets the token (MediaAuth): a public link in a reply must not receive it
        if (authToken.isNotBlank() && com.example.data.remote.MediaAuth.isOwnServer(url, serverBaseUrl)) {
            addHeader("X-HUD-Token", authToken.trim())
        }
    }.build()
    client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) error("HTTP ${resp.code}")
        val body = resp.body ?: error("empty answer")
        val total = body.contentLength()
        if (target.exists() && total > 0 && target.length() == total) return@runCatching target   // already here
        val tmp = File(dir, "$name.part")
        tmp.outputStream().use { out ->
            body.byteStream().use { input ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    onProgress(done, total)
                }
            }
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) error("couldn't save it")
    }
    target
}

private fun open(context: Context, file: File) {
    val ext = file.extension.lowercase()
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "No app on this phone opens .$ext files", Toast.LENGTH_LONG).show()
    }
}
