package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.remote.AlwaysOnAgentClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * "Needs you": a computer task on the Mini is waiting for the owner (the owner's rule: anything needed from a
 * person comes to the app, loud, because they may not be watching). Its own loud channel; the screenshot shown
 * large (the spot in question outlined); answers right in the notification: Approve/Deny, the pop-up's own
 * choices, or a reply box. Reminders replace the same notification and alert again.
 */
object NeedsYou {
    const val CHANNEL = "deskai_needs_you_channel"
    const val EXTRA_OPEN = "OPEN_NEEDS_YOU"
    const val ACTION_ANSWER = "com.example.NEEDS_YOU_ANSWER"
    const val KEY_REPLY = "needs_you_reply"
    private val VIBRATION = longArrayOf(0, 600, 250, 600, 250, 900)

    fun notificationId(requestId: String) = ("needs_you:$requestId").hashCode()

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(CHANNEL, "Needs you", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "A computer task is waiting for you: an approval, a code, a choice or a question"
            enableLights(true)
            enableVibration(true)
            vibrationPattern = VIBRATION
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
        }
        nm.createNotificationChannel(channel)
    }

    /** Builds and shows the alert from the push's data (downloads the screenshot first). */
    suspend fun show(context: Context, data: Map<String, String>) {
        val requestId = data["request_id"] ?: return
        val kind = data["request_kind"] ?: "text"
        val title = data["title"] ?: "The computer task needs you"
        val body = data["body"] ?: ""
        val choices = try {
            val arr = JSONArray(data["choices"] ?: "[]"); (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
        ensureChannel(context)

        val bitmap = data["screenshot"]?.takeIf { it.isNotBlank() }?.let { path ->
            try {
                val config = AppDatabase.getDatabase(context).bridgeConfigDao().getConfigSync()
                config?.let { AlwaysOnAgentClient().fetchBytes(it, path) }
                    ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            } catch (_: Exception) { null }
        }

        val open = PendingIntent.getActivity(
            context, notificationId(requestId),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN, requestId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⚠️ $title")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(VIBRATION)
            .setAutoCancel(false)                       // it stays until answered
            .setOnlyAlertOnce(false)                    // reminders alert again
            .setContentIntent(open)
        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap).setSummaryText(body))
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        when (kind) {
            "approve" -> {
                builder.addAction(0, "✅ Approve", answerIntent(context, requestId, "Approve", 1))
                builder.addAction(0, "✋ Deny", answerIntent(context, requestId, "Deny", 2))
            }
            "choice" -> choices.take(3).forEachIndexed { i, c ->
                builder.addAction(0, c, answerIntent(context, requestId, c, 10 + i))
            }
            // A password or code: typed only on DeskAI's own screen, in a hidden field (a notification's reply
            // box shows what's typed and can't block screenshots). Tapping the alert opens that screen.
            "secret" -> builder.addAction(0, "🔒 Type it in DeskAI", open)
            else -> {
                val remote = RemoteInput.Builder(KEY_REPLY).setLabel("Your answer").build()
                val reply = NotificationCompat.Action.Builder(0, "Reply", answerIntent(context, requestId, null, 3, mutable = true))
                    .addRemoteInput(remote).build()
                builder.addAction(reply)
            }
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId(requestId), builder.build())
    }

    private fun answerIntent(context: Context, requestId: String, answer: String?, slot: Int,
                             mutable: Boolean = false): PendingIntent {
        val intent = Intent(context, NeedsYouReceiver::class.java).apply {
            action = ACTION_ANSWER
            putExtra("request_id", requestId)
            if (answer != null) putExtra("answer", answer)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (mutable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE
             else PendingIntent.FLAG_IMMUTABLE)
        return PendingIntent.getBroadcast(context, notificationId(requestId) * 31 + slot, intent, flags)
    }

    fun cancel(context: Context, requestId: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationId(requestId))
    }

    /** A short note in place of the alert: sent, or what went wrong (the app's screen can answer instead). */
    fun note(context: Context, requestId: String, text: String) {
        ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId(requestId), NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle(text).setSilent(true).setAutoCancel(true)
            .setTimeoutAfter(15_000).build())
    }
}

/** An answer tapped (or typed) in the notification: sent to the agent, which hands it to the waiting task. */
class NeedsYouReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NeedsYou.ACTION_ANSWER) return
        val requestId = intent.getStringExtra("request_id") ?: return
        val answer = intent.getStringExtra("answer")
            ?: RemoteInput.getResultsFromIntent(intent)?.getCharSequence(NeedsYou.KEY_REPLY)?.toString()
            ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val config = AppDatabase.getDatabase(context).bridgeConfigDao().getConfigSync()
                val result = config?.let { AlwaysOnAgentClient().answerComputerRequest(it, requestId, answer) }
                if (result?.isSuccess == true) NeedsYou.note(context, requestId, "Sent: $answer")
                else NeedsYou.note(context, requestId, "Couldn't send (${result?.exceptionOrNull()?.message ?: "no connection"}): open DeskAI to answer")
            } finally {
                pending.finish()
            }
        }
    }
}
