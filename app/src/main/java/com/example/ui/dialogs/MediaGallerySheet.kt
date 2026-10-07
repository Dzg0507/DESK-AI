package com.example.ui.dialogs

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.model.ImageItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AgentTaskItem
import com.example.data.model.VideoItem
import com.example.ui.components.LiveTaskCard
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldConnected
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun MediaGallerySheet(
    onDismiss: () -> Unit,
    onFetchVideos: suspend () -> List<VideoItem>,
    onDownloadVideo: suspend (url: String, destinationFile: File, onProgress: (Long, Long) -> Unit) -> Result<File>,
    onTriggerRender: suspend (quote: String?) -> Result<String>,
    onTriggerTikTok: suspend (quote: String?) -> Result<String>,
    onPublishExistingVideo: suspend (filename: String) -> Result<String> = { Result.success("published") },
    onGetTask: (suspend (String) -> Result<AgentTaskItem>)? = null,
    onCancelTask: (suspend (String) -> Result<String>)? = null,
    onRetryTask: (suspend (String) -> Result<String>)? = null,
    onFetchImages: (suspend () -> List<ImageItem>)? = null,
    onDeleteImage: (suspend (String) -> Result<Boolean>)? = null,
    authToken: String = ""
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = Images, 1 = Videos
    var customQuote by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var activeRenderTaskId by remember { mutableStateOf<String?>(null) }

    suspend fun publishExisting(filename: String) {
        statusMessage = "Sending $filename to the agent..."
        val res = onPublishExistingVideo(filename)
        val tid = res.getOrNull()
        if (res.isSuccess && tid != null && tid.startsWith("task-")) {
            activeRenderTaskId = tid
            statusMessage = "🚀 Queued: posting $filename to TikTok"
        } else {
            statusMessage = "⚠️ ${res.exceptionOrNull()?.message ?: "Couldn't queue the TikTok post"}"
        }
    }

    var isLoading by remember { mutableStateOf(true) }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var selectedVideoForPlayback by remember { mutableStateOf<VideoItem?>(null) }

    var isImagesLoading by remember { mutableStateOf(true) }
    var images by remember { mutableStateOf<List<ImageItem>>(emptyList()) }
    var selectedImageForViewer by remember { mutableStateOf<ImageItem?>(null) }

    fun refreshVideos() {
        scope.launch {
            isLoading = true
            try {
                videos = onFetchVideos()
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    fun refreshImages() {
        if (onFetchImages == null) return
        scope.launch {
            isImagesLoading = true
            try {
                images = onFetchImages()
            } catch (_: Exception) {
            } finally {
                isImagesLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshVideos()
        refreshImages()
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Default.Image else Icons.Default.Videocam,
                            contentDescription = "Media Hub",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Agent Media Hub",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                refreshVideos()
                                refreshImages()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Media",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Selector (Images vs Videos)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Images Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == 0) ElectricCyan.copy(alpha = 0.22f) else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (selectedTab == 0) ElectricCyan else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = if (selectedTab == 0) ElectricCyan else Color(0xFF94A3B8),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Images (${images.size})",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Videos Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == 1) ElectricCyan.copy(alpha = 0.22f) else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (selectedTab == 1) ElectricCyan else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = if (selectedTab == 1) ElectricCyan else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Videos (${videos.size})",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Content
                if (selectedTab == 0) {
                    // ================= IMAGES VIEW =================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AGENT CREATIONS (${images.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 0.5.sp
                        )
                        if (isImagesLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (images.isEmpty() && !isImagesLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "🎨", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No images created yet",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF94A3B8)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Run /image <prompt> in chat to generate visuals!",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(images) { image ->
                                ImageGalleryCard(
                                    image = image,
                                    authToken = authToken,
                                    onClick = { selectedImageForViewer = image },
                                    onSave = {
                                        scope.launch {
                                            saveImageToDevice(context, image.url, authToken, image.filename)
                                        }
                                    },
                                    onDelete = {
                                        scope.launch {
                                            val res = onDeleteImage?.invoke(image.filename)
                                            if (res?.isSuccess == true) {
                                                images = images.filter { it.filename != image.filename }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // ================= VIDEOS VIEW =================
                    // Custom Quote Input for Render / TikTok
                    OutlinedTextField(
                        value = customQuote,
                        onValueChange = { customQuote = it },
                        placeholder = { Text("Custom quote (leave blank for daily affirmation)...", color = Color(0xFF64748B), fontSize = 12.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1E293B),
                            unfocusedContainerColor = Color(0xFF1E293B),
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = Color(0xFF334155)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action buttons: Render 3D Video & Post to TikTok
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Render 3D Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                            .clickable {
                                scope.launch {
                                    val res = onTriggerRender(customQuote.ifBlank { null })
                                    if (res.isSuccess) {
                                        val tid = res.getOrNull()
                                        activeRenderTaskId = tid
                                        statusMessage = "🎬 3D Render Dispatched ($tid)"
                                    } else {
                                        statusMessage = "⚠️ Failed: ${res.exceptionOrNull()?.message}"
                                    }
                                    refreshVideos()
                                }
                            }
                            .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🎬 Render 3D Video",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0)
                            )
                        }

                        // Post to TikTok
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0284C7))
                                .clickable {
                                    scope.launch {
                                        val res = onTriggerTikTok(customQuote.ifBlank { null })
                                        if (res.isSuccess) {
                                            val tid = res.getOrNull()
                                            activeRenderTaskId = tid
                                            statusMessage = "🚀 TikTok Mission Queued ($tid)"
                                        } else {
                                            statusMessage = "⚠️ Failed: ${res.exceptionOrNull()?.message}"
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🚀 Post to TikTok",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    if (statusMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = statusMessage!!,
                            fontSize = 11.sp,
                            color = EmeraldConnected,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Live Task Card inside sheet for active render
                    activeRenderTaskId?.let { tid ->
                        if (onGetTask != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LiveTaskCard(
                                taskId = tid,
                                onGetTask = onGetTask,
                                onCancelTask = onCancelTask,
                                onRetryTask = onRetryTask,
                                onPlayVideo = { url, fn ->
                                    selectedVideoForPlayback = VideoItem(
                                        filename = fn,
                                        url = url,
                                        sizeMb = 0.0,
                                        createdAt = "Just now"
                                    )
                                    refreshVideos()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RENDERED VIDEOS (${videos.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 0.5.sp
                        )
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    if (videos.isEmpty() && !isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "🎥", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No videos rendered yet",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF94A3B8)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap 'Render 3D Video' above to generate your first clip!",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(videos) { video ->
                                VideoGalleryCard(
                                    video = video,
                                    authToken = authToken,
                                    onPlay = { selectedVideoForPlayback = video },
                                    onPostTikTok = { scope.launch { publishExisting(video.filename) } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Image Fullscreen Viewer Dialog
    if (selectedImageForViewer != null) {
        ImageViewerDialog(
            imageUrl = selectedImageForViewer!!.url,
            altText = selectedImageForViewer!!.filename,
            authToken = authToken,
            onDismiss = { selectedImageForViewer = null }
        )
    }

    // Video Player Dialog with Local Cache & Stream
    if (selectedVideoForPlayback != null) {
        VideoPlayerDialog(
            video = selectedVideoForPlayback!!,
            onDismiss = { selectedVideoForPlayback = null },
            onDownloadVideo = onDownloadVideo,
            onPostTikTok = {
                val filename = selectedVideoForPlayback?.filename
                if (filename != null) scope.launch { publishExisting(filename) }
            }
        )
    }
}

@Composable
fun VideoGalleryCard(
    video: VideoItem,
    authToken: String,
    onPlay: () -> Unit,
    onPostTikTok: () -> Unit
) {
    val context = LocalContext.current
    val thumbRequest = remember(video.thumbnailUrl, authToken) {
        if (video.thumbnailUrl.isNotBlank()) {
            ImageRequest.Builder(context)
                .data(video.thumbnailUrl)
                .crossfade(true)
                .apply {
                    if (authToken.isNotBlank()) {
                        addHeader("X-HUD-Token", authToken.trim())
                        addHeader("Authorization", "Bearer ${authToken.trim()}")
                    }
                }
                .build()
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
            .clickable { onPlay() }
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video Thumbnail preview box (60.dp x 86.dp, ~9:16 vertical video ratio)
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(86.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbRequest != null) {
                    SubcomposeAsyncImage(
                        model = thumbRequest,
                        contentDescription = video.filename,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = ElectricCyan,
                                    strokeWidth = 2.dp
                                )
                            }
                        },
                        error = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Frosted glass play icon badge overlay in center
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Metadata & action buttons
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = video.filename,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF1F5F9),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${video.sizeMb} MB • ${video.createdAt}",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B),
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Watch button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                            .clickable { onPlay() }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "▶ Watch",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                    }

                    // 🚀 Direct TikTok Publish Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0284C7).copy(alpha = 0.25f))
                            .border(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable { onPostTikTok() }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "🚀 Post TikTok",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImageGalleryCard(
    image: ImageItem,
    authToken: String,
    onClick: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(image.url, authToken) {
        ImageRequest.Builder(context)
            .data(image.url)
            .crossfade(true)
            .apply {
                if (authToken.isNotBlank()) {
                    addHeader("X-HUD-Token", authToken.trim())
                    addHeader("Authorization", "Bearer ${authToken.trim()}")
                }
            }
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color(0xFF0F172A))
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = imageRequest,
                    contentDescription = image.filename,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    error = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Preview error",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                )
            }

            // Info & Action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = image.filename,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF1F5F9),
                        maxLines = 1
                    )
                    Text(
                        text = "${image.sizeMb} MB",
                        fontSize = 9.sp,
                        color = Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onSave,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Save to device",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete image",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayerDialog(
    video: VideoItem,
    onDismiss: () -> Unit,
    onDownloadVideo: suspend (url: String, destinationFile: File, onProgress: (Long, Long) -> Unit) -> Result<File>,
    onPostTikTok: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isBuffering by remember { mutableStateOf(true) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadText by remember { mutableStateOf("Buffering video stream...") }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    val videosCacheDir = remember {
        File(context.cacheDir, "videos").apply { mkdirs() }
    }
    val targetFile = remember(video.filename) {
        File(videosCacheDir, video.filename)
    }

    fun playLocalFile(file: File) {
        videoViewRef?.let { vv ->
            isBuffering = false
            vv.setVideoURI(Uri.fromFile(file))
        }
    }

    fun startStreamOrDownload() {
        if (targetFile.exists() && targetFile.length() > 0) {
            isBuffering = false
            playLocalFile(targetFile)
            return
        }

        isBuffering = true
        playbackError = null
        scope.launch {
            val res = onDownloadVideo(video.url, targetFile) { bytesRead, totalBytes ->
                if (totalBytes > 0) {
                    val prog = (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                    downloadProgress = prog
                    val readMb = String.format(Locale.US, "%.1f", bytesRead / (1024f * 1024f))
                    val totMb = String.format(Locale.US, "%.1f", totalBytes / (1024f * 1024f))
                    downloadText = "Buffering video stream: $readMb MB / $totMb MB (${(prog * 100).toInt()}%)"
                } else {
                    val readMb = String.format(Locale.US, "%.1f", bytesRead / (1024f * 1024f))
                    downloadText = "Buffering video stream: $readMb MB"
                }
            }

            if (res.isSuccess) {
                isBuffering = false
                val downloadedFile = res.getOrThrow()
                playLocalFile(downloadedFile)
            } else {
                isBuffering = false
                playbackError = "Stream connection interrupted (${res.exceptionOrNull()?.localizedMessage ?: "Network error"}). Tap 'Retry' or use 'Open in External Player' above."
            }
        }
    }

    LaunchedEffect(video.filename) {
        startStreamOrDownload()
    }

    DisposableEffect(Unit) {
        onDispose {
            videoViewRef?.stopPlayback()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF090D16))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.filename,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = "${video.sizeMb} MB • ${video.createdAt}",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Open in External App (VLC / MX Player / System Gallery)
                        IconButton(
                            onClick = {
                                try {
                                    val streamUri = if (targetFile.exists() && targetFile.length() > 0) {
                                        Uri.fromFile(targetFile)
                                    } else {
                                        Uri.parse(video.url)
                                    }
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(streamUri, "video/*")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Play with"))
                                } catch (e: Exception) {
                                    // Fallback to browser
                                    try {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(video.url))
                                        context.startActivity(browserIntent)
                                    } catch (_: Exception) {}
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in External Player",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Share Link
                        IconButton(
                            onClick = {
                                try {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "AlwaysOnAgent Video: ${video.filename}")
                                        putExtra(Intent.EXTRA_TEXT, video.url)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Video Stream"))
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Video URL",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Player",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Video Display Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                // Centred: VideoView shrinks itself to the video's shape, and without a gravity it
                                // sat at the top-left, so a video taller than the frame had all the black on the
                                // right (2026-10-06, the full-screen phone recording). Sizing stays MATCH_PARENT,
                                // as before: TikTok renders that fill the frame look exactly the same.
                                val vView = VideoView(ctx).apply {
                                    layoutParams = FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        android.view.Gravity.CENTER
                                    )
                                    videoViewRef = this

                                    val controller = MediaController(ctx)
                                    controller.setAnchorView(this)
                                    setMediaController(controller)

                                    setOnPreparedListener { mp ->
                                        isBuffering = false
                                        mp.isLooping = true
                                        start()
                                    }

                                    setOnErrorListener { _, what, extra ->
                                        isBuffering = false
                                        playbackError = "Playback error ($what, $extra)."
                                        true
                                    }

                                    if (targetFile.exists() && targetFile.length() > 0) {
                                        setVideoURI(Uri.fromFile(targetFile))
                                    }
                                }

                                addView(vView)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Buffering & Progress Indicator
                    if (isBuffering && playbackError == null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(42.dp),
                                color = ElectricCyan,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = downloadText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFF1F5F9),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (downloadProgress > 0f) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = ElectricCyan,
                                    trackColor = Color(0xFF1E293B)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Buffering directly into app for zero-stutter playback",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    // Playback or Stream Error View
                    if (playbackError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.88f))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "⚠️", fontSize = 28.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = playbackError!!,
                                    fontSize = 11.sp,
                                    color = Color(0xFFF87171),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ElectricCyan)
                                            .clickable { startStreamOrDownload() }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "🔄 Retry Buffer",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1E293B))
                                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                                        .clickable {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(video.url))
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "Open in Browser",
                                            fontSize = 12.sp,
                                            color = Color(0xFFCBD5E1)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Controls / Actions Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick TikTok Upload Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0284C7))
                            .clickable {
                                onPostTikTok()
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🚀 Post This to TikTok",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Done / Close Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                            .clickable { onDismiss() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Done",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }
    }
}
