package com.example.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.runtime.remember
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.DeskShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatSession
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonIndigo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ui.theme.ElectricCyanGlow
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SlateDarkSurface

@Composable
fun SessionDrawerContent(
    sessions: List<ChatSession>,
    activeSessionId: String,
    onSelectSession: (ChatSession) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onTogglePin: (ChatSession) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(Brush.verticalGradient(listOf(Slate900, DeepNavy)))
            .padding(16.dp)
    ) {
        // App / Brand Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(DeskShapes.control)
                    .background(Brush.linearGradient(listOf(ElectricCyan, NeonIndigo))),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⚡", fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "AlwaysOnAgent",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "DESKTOP BRIDGE",
                    fontSize = 10.sp,
                    color = ElectricCyan,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // New Chat Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DeskShapes.control)
                .background(Brush.horizontalGradient(listOf(ElectricCyan.copy(alpha = 0.18f), NeonIndigo.copy(alpha = 0.12f))))
                .border(1.dp, ElectricCyan.copy(alpha = 0.35f), DeskShapes.control)
                .clickable { onNewSession() }
                .padding(vertical = 10.dp, horizontal = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Conversation",
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Conversation",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionLabel(
            text = "Conversations",
            count = sessions.size,
            color = Slate500,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        // Session List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(sessions) { session ->
                val isActive = session.id == activeSessionId
                val timeStr = remember(session.updatedAt) {
                    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(session.updatedAt))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DeskShapes.control)
                        .background(
                            if (isActive) Brush.horizontalGradient(listOf(ElectricCyan.copy(alpha = 0.14f), Slate800))
                            else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .border(1.dp, if (isActive) ElectricCyan.copy(alpha = 0.30f) else Color.Transparent, DeskShapes.control)
                        .clickable { onSelectSession(session) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (session.pinned) Icons.Default.PushPin else Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = if (session.pinned) NeonIndigo else (if (isActive) ElectricCyan else Slate500),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = session.title,
                                    fontSize = 13.sp,
                                    color = if (isActive) Color.White else Slate300,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = timeStr,
                                    fontSize = 10.sp,
                                    color = Slate500,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (sessions.size > 1) {
                            IconButton(
                                onClick = { onDeleteSession(session.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = Slate600,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Footer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DeskShapes.control)
                .background(SlateDarkSurface)
                .border(1.dp, Slate800, DeskShapes.control)
                .padding(10.dp)
        ) {
            Column {
                Text(
                    text = "AlwaysOnAgent (v2.1)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Autonomous AI Worker & Desktop Bridge",
                    fontSize = 9.sp,
                    color = Slate400
                )
            }
        }
    }
}
