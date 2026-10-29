package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.theme.DeskColors
import com.example.ui.theme.DeskShapes
import com.example.ui.theme.DeskType
import com.example.ui.today.InlineYouTube

private val YOUTUBE_URL = Regex("""https?://(?:www\.|m\.)?(?:youtube\.com/(?:watch\?[^\s)]*v=|shorts/|live/)|youtu\.be/)([\w-]{11})[^\s)]*""")

/** The first YouTube link in a message: (the whole link, the video id), or null. */
fun youTubeLink(text: String): Pair<String, String>? =
    YOUTUBE_URL.find(text)?.let { it.value to it.groupValues[1] }

/**
 * A YouTube video in the chat (the 2026-10-29 redesign, Devin: "anything that shows up as a video to be playable in
 * chat"): its thumbnail with a frosted play button and the channel on a glass pill; a tap plays it in the card, with
 * YouTube's own player (the same one as Today's video cards). A video YouTube won't play in another app opens in
 * YouTube instead.
 */
@Composable
fun ChatVideoCard(videoId: String, link: String, label: String, modifier: Modifier = Modifier) {
    var playing by remember(videoId) { mutableStateOf(false) }
    // hq720 is 16:9 with no black bars but not every video has it; mqdefault (also 16:9) always exists
    var thumb by remember(videoId) { mutableStateOf("https://i.ytimg.com/vi/$videoId/hq720.jpg") }
    val uri = LocalUriHandler.current
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(DeskShapes.media)
            .background(Color.Black)
    ) {
        if (playing) {
            InlineYouTube(videoId, Modifier.fillMaxSize(), onCantPlay = {
                playing = false
                runCatching { uri.openUri(link) }
            })
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(34.dp)
                    .glass(CircleShape, DeskColors.GlassStrong).clickable { playing = false },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = DeskColors.TextPrimary,
                    modifier = Modifier.size(18.dp))
            }
        } else {
            AsyncImage(
                model = thumb, contentDescription = null, contentScale = ContentScale.Crop,
                onError = { if ("hq720" in thumb) thumb = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg" },
                modifier = Modifier.fillMaxSize().clickable { playing = true }
            )
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to DeskColors.Scrim)))
            Box(
                Modifier.align(Alignment.Center).size(60.dp).glass(CircleShape, DeskColors.GlassStrong)
                    .clickable { playing = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = DeskColors.TextPrimary,
                    modifier = Modifier.size(32.dp))
            }
            if (label.isNotBlank()) {
                Text(
                    label, style = DeskType.label, color = DeskColors.TextPrimary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                        .glass().padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
