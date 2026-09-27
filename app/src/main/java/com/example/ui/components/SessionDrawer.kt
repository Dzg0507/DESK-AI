package com.example.ui.components

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
            .background(Color(0xFF0F172A))
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0284C7)),
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
                    text = "DESKTOP BRIDGE v2.1",
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
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E293B))
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

        Text(
            text = "CONVERSATIONS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B),
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        // Session List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(sessions) { session ->
                val isActive = session.id == activeSessionId
                val timeStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(session.updatedAt))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF1E293B) else Color.Transparent)
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
                                tint = if (session.pinned) NeonIndigo else (if (isActive) ElectricCyan else Color(0xFF64748B)),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = session.title,
                                    fontSize = 13.sp,
                                    color = if (isActive) Color.White else Color(0xFFCBD5E1),
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1
                                )
                                Text(
                                    text = timeStr,
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B),
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
                                    tint = Color(0xFF475569),
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
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF131B2E))
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
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}
