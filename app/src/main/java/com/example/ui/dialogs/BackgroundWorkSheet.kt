package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ActivityState
import com.example.ui.components.SheetHeader
import com.example.ui.components.deskCard
import com.example.ui.components.deskSheet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.PhosphorMint
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500

/**
 * Shows what the agent is doing in the background (the away shift: docs/PROACTIVE_RESEARCH.md 1m, 2.4).
 * Opened by tapping the top-bar pulsing activity icon on ChatScreen.
 */
@Composable
fun BackgroundWorkSheet(
    activity: ActivityState?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .deskSheet()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SheetHeader(
                    title = "Background Activity",
                    icon = Icons.Default.AutoAwesome,
                    subtitle = "The agent's away shift",
                    onClose = onDismiss
                )

                Spacer(modifier = Modifier.height(14.dp))

                val isActive = activity?.active == true
                val label = activity?.label?.takeIf { it.isNotBlank() } ?: if (isActive) "Working in the background" else "Idle"
                val step = activity?.step?.takeIf { it.isNotBlank() } ?: if (isActive) "Processing background tasks..." else "No background work in progress"
                val found = activity?.found ?: 0

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .deskCard()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isActive) PhosphorMint else Slate500)
                        )
                        Text(
                            text = if (isActive) "ACTIVE NOW" else "IDLE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) PhosphorMint else Slate400,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Text(
                        text = label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    Text(
                        text = step,
                        fontSize = 13.sp,
                        color = Slate200,
                        lineHeight = 18.sp
                    )

                    if (found > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "💡 Found $found idea${if (found == 1) "" else "s"} / update${if (found == 1) "" else "s"} so far",
                            fontSize = 12.sp,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "The away shift runs maintenance, consolidates memories, checks open loops, and researches ideas while you're away.",
                    fontSize = 11.sp,
                    color = Slate500,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
