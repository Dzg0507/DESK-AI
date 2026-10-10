package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY pinned DESC, updatedAt DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    fun getSessionById(sessionId: String): Flow<ChatSession?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession)

    @Update
    suspend fun updateSession(session: ChatSession)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>>

    // The newest :limit messages, oldest first: a long chat opens without loading its whole history
    @Query("SELECT * FROM (SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit) ORDER BY timestamp ASC")
    fun getRecentMessagesForSession(sessionId: String, limit: Int): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    // An agent-started message arrives by push and again on the next sync: keep the first copy
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessageIfAbsent(message: ChatMessage): Long

    // The conversation the app opens on (the same order as the session list): where the agent's own messages go
    @Query("SELECT * FROM chat_sessions ORDER BY pinned DESC, updatedAt DESC LIMIT 1")
    suspend fun getLatestSession(): ChatSession?

    @Update
    suspend fun updateMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun clearMessagesForSession(sessionId: String)

    // Whether any message already shows this task's card (in any conversation)
    @Query("SELECT COUNT(*) FROM chat_messages WHERE linkedTaskId = :taskId")
    suspend fun countMessagesForTask(taskId: String): Int

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun getMessageCount(sessionId: String): Int
}
