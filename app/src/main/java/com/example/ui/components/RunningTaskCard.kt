package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.AgentTaskItem
import com.example.data.remote.MediaAuth
import com.example.ui.theme.DeskColors
import com.example.ui.theme.DeskShapes
import com.example.ui.theme.DeskType

/**
 * The live card under the Chat/Today switch while the agent is working (Devin's chat mockup, 2026-10-29): the task's
 * thumbnail, "AlwaysOnAgent · N running", and what it's doing now with its percent. A tap opens the Tasks sheet.
 * Shown only while something runs.
 */
@Composable
fun RunningTaskCard(tasks: List<AgentTaskItem>, base: String, token: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val task = tasks.firstOrNull() ?: return
    val context = LocalContext.current
    val thumb = task.thumbUrl?.takeIf { it.isNotBlank() }?.let { u ->
        when {
            u.startsWith("http") -> u
            u.startsWith("/") -> base.trimEnd('/') + u
            else -> base.trimEnd('/') + "/" + u
        }
    }
    val doing = task.progress?.label?.takeIf { it.isNotBlank() } ?: task.statusText?.takeIf { it.isNotBlank() } ?: task.title
    val percent = task.progress?.percent?.takeIf { it > 0 }
    Row(
        modifier
            .widthIn(max = 360.dp)
            .glass(DeskShapes.sheet, DeskColors.GlassStrong)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(DeskColors.Capsule), contentAlignment = Alignment.Center) {
            if (thumb != null) {
                val request = remember(thumb, token) {
                    ImageRequest.Builder(context).data(thumb).crossfade(true).apply {
                        if (token.isNotBlank() && MediaAuth.isOwnServer(thumb, base)) addHeader("X-HUD-Token", token.trim())
                    }.build()
                }
                AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp))
            } else {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = DeskColors.Agent, modifier = Modifier.size(22.dp))
            }
            // A small amber dot: working
            Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(9.dp).clip(CircleShape).background(DeskColors.Waiting))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AlwaysOnAgent", style = DeskType.label, color = DeskColors.TextPrimary)
                Spacer(Modifier.width(10.dp))
                Text("${tasks.size} running", style = DeskType.caption, color = DeskColors.TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(DeskColors.Waiting))
                Spacer(Modifier.width(6.dp))
                Text(
                    doing + (percent?.let { " · $it%" } ?: ""),
                    style = DeskType.mono, color = DeskColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
