package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.remote.AlwaysOnAgentClient
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DeskAIMessagingService : FirebaseMessagingService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received: $token")
        registerTokenWithHost(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}, data: ${remoteMessage.data}")

        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val type = data["type"] ?: "alert"
            val taskId = data["task_id"] ?: ""
            val title = data["title"] ?: when (type) {
                "task_completed" -> "✅ Task Finished"
                "task_failed" -> "⚠️ Task Failed"
                "test" -> "🔔 Push Test Received"
                else -> "⚡ DeskAI Alert"
            }
            val body = data["body"] ?: if (taskId.isNotBlank()) "Task [$taskId] updated" else "Important alert from AlwaysOnAgent"
            val channel = data["channel"] ?: if (type.startsWith("task")) "tasks" else "alerts"

            if (type == "needs_you") {
                // A computer task is waiting for the owner: the loud alert with its screenshot and answer buttons
                scope.launch {
                    try {
                        NeedsYou.show(applicationContext, data)
                    } catch (e: Exception) {
                        Log.e(TAG, "Needs-you alert failed: ${e.message}")
                        showNotification(title, body, "alerts", taskId, type)
                    }
                }
                return
            }

            if (type == "agent_message") {
                // The push carries no text (it passes through Google): fetch the message into the chat
                scope.launch {
                    try {
                        val db = AppDatabase.getDatabase(applicationContext)
                        com.example.data.repository.ChatRepository(applicationContext, db).syncInbox()
                    } catch (e: Exception) {
                        Log.e(TAG, "Inbox sync after push failed: ${e.message}")
                    }
                }
            }
            showNotification(title, body, channel, taskId, type)
        }
    }

    private fun showNotification(
        title: String,
        body: String,
        channelKey: String,
        taskId: String,
        type: String
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = when (channelKey) {
            "tasks" -> CHANNEL_TASKS; "reminders" -> CHANNEL_REMINDERS; "assistant" -> CHANNEL_ASSISTANT; else -> CHANNEL_ALERTS
        }
        val channelName = when (channelKey) {
            "tasks" -> "Tasks Updates"; "reminders" -> "Reminders"; "assistant" -> "Assistant messages"; else -> "System Alerts"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // The agent's own messages (brief, questions) are normal, not heads-up alerts
            val calm = channelKey == "tasks" || channelKey == "assistant"
            val importance = if (calm) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = when (channelKey) {
                    "tasks" -> "Notifications when tasks complete or fail"
                    "reminders" -> "Reminders you scheduled with the agent"
                    "assistant" -> "Your morning brief, heads-ups, suggestions and questions from the agent"
                    else -> "Critical warnings, backups and supervisor alerts"
                }
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_TASK_ID", taskId)
            putExtra("NOTIFICATION_TYPE", type)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setPriority(if (channelKey == "tasks" || channelKey == "assistant") NotificationCompat.PRIORITY_DEFAULT
                         else NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationId = if (taskId.isNotBlank()) taskId.hashCode() else System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun registerTokenWithHost(token: String) {
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val config = db.bridgeConfigDao().getConfigSync() ?: return@launch
                val client = AlwaysOnAgentClient()
                val result = client.registerPushToken(config, token, Build.MODEL ?: "Android Companion")
                Log.d(TAG, "Push token registration response: status=${result.status}, pushReady=${result.pushReady}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed registering push token to host: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    companion object {
        private const val TAG = "DeskAIMessagingService"
        const val CHANNEL_TASKS = "deskai_tasks_channel"
        const val CHANNEL_ALERTS = "deskai_alerts_channel"
        const val CHANNEL_REMINDERS = "deskai_reminders_channel"
        const val CHANNEL_ASSISTANT = "deskai_assistant_channel"
    }
}
