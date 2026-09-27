package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "sent", // "sending", "sent", "error"
    val modelUsed: String? = null,
    val latencyMs: Long? = null,
    val isCommand: Boolean = false,
    val errorMessage: String? = null,
    // Proposed task support matching AlwaysOnAgent
    val proposalToken: String? = null,
    val proposalInstruction: String? = null,
    val proposalProject: String? = null,
    val proposalState: String? = null, // "pending", "run", "dismissed"
    val linkedTaskId: String? = null
)
