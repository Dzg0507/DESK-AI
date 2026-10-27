package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.BridgeConfig
import com.example.data.model.ChatMessage
import com.example.data.remote.AlwaysOnAgentClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Chat send/stop bugs fixed on 2026-10-26. No real services: an in-memory database, and an "agent" that is a local
 * socket which accepts the connection and never answers (so a reply is in progress until it's stopped).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatRepositoryChatTest {

    private lateinit var db: AppDatabase
    private lateinit var silentAgent: ServerSocket
    private val client = AlwaysOnAgentClient()
    private lateinit var repo: ChatRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        silentAgent = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        repo = ChatRepository(null, db, client)
        runBlocking {
            // Only the local socket: never the real LAN or Tailscale addresses
            db.bridgeConfigDao().saveConfig(
                BridgeConfig(serverUrl = "http://127.0.0.1:${silentAgent.localPort}", remoteUrl = "", apiKey = "test")
            )
        }
    }

    @After
    fun tearDown() {
        silentAgent.close()
        db.close()
    }

    private suspend fun messages(sessionId: String) = db.chatDao().getMessagesForSession(sessionId).first()

    @Test
    fun stoppingAReplyMarksItStoppedInsteadOfLeavingItSending() = runBlocking {
        val session = repo.createSession("Mission #1")
        val job = launch(Dispatchers.Default) {
            repo.sendMessageStream(session, "hello", emptyList()).collect { }
        }
        withTimeout(10_000) {
            while (messages(session.id).none { it.role == "assistant" && it.status == "sending" }) delay(20)
        }

        job.cancelAndJoin()      // what Stop does

        val reply = messages(session.id).single { it.role == "assistant" }
        assertEquals("stopped", reply.status)
        assertEquals(ChatRepository.STOPPED_TEXT, reply.content)
    }

    @Test
    fun aStaleTaskIdFromAnEarlierReplyIsNotLinkedToTheNextOne() = runBlocking {
        val session = repo.createSession("Mission #1")
        client.lastReceivedTaskId = "task-999"           // left behind by a reply that was stopped
        // /help is answered inside the app, without the network
        repo.sendMessageStream(session, "/help", emptyList()).collect { }

        val reply = messages(session.id).single { it.role == "assistant" }
        assertEquals("sent", reply.status)
        assertNull(reply.linkedTaskId)
    }

    @Test
    fun interruptedRepliesAreSweptOnOpen() = runBlocking {
        val session = repo.createSession("Mission #1")
        db.chatDao().insertMessage(ChatMessage(sessionId = session.id, role = "assistant", content = "", status = "sending"))
        db.chatDao().insertMessage(ChatMessage(sessionId = session.id, role = "assistant", content = "half a re", status = "sending"))

        assertEquals(2, repo.markInterruptedReplies())

        val all = messages(session.id)
        assertTrue(all.all { it.status == "stopped" })
        assertEquals(setOf(ChatRepository.STOPPED_TEXT, "half a re"), all.map { it.content }.toSet())
    }

    @Test
    fun theAgentOnlyGetsFinishedMessagesWithText() {
        fun msg(role: String, content: String, status: String = "sent") =
            ChatMessage(sessionId = "s", role = role, content = content, status = status)
        val history = listOf(
            msg("user", "hi"),
            msg("assistant", "hello"),
            msg("assistant", "❌ Error communicating with computer bridge", status = "error"),
            msg("assistant", "", status = "sending"),
            msg("assistant", "half", status = "stopped"),
            msg("assistant", "   ")
        )
        assertEquals(listOf("hi", "hello"), ChatRepository.historyForAgent(history).map { it.content })
    }

    @Test
    fun newChatsGetTitledByTheirFirstMessage() {
        assertTrue(ChatRepository.isPlaceholderTitle("Mission Control"))
        assertTrue(ChatRepository.isPlaceholderTitle("Mission #12"))
        assertFalse(ChatRepository.isPlaceholderTitle("Mission #12 notes"))
        assertFalse(ChatRepository.isPlaceholderTitle("Fix the router"))
    }
}
