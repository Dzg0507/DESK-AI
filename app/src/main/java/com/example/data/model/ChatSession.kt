package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "New Conversation",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val systemPrompt: String = "You are my desktop personal AI assistant. Be concise, direct, helpful, and provide clear code snippets when needed.",
    val pinned: Boolean = false
)
