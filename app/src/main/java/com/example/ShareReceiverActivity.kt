package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import java.io.File

/**
 * "Share to agent" (2026-10-10, the owner's ask): DeskAI in Android's share menu. Text, links, pictures, PDFs and
 * documents shared from any app open the chat with the text in the message box and the files attached, ready for
 * "summarize this", "remember this" or "what's this?". Nothing is sent until the owner taps Send.
 *
 * This screen has no UI. A shared file can only be read by the screen it was shared to, and only for a while, so
 * every file is copied into the app's cache right away; [SharedInbox] hands the text and the copies to the chat,
 * and MainActivity is brought to the front.
 */
class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        val text = listOfNotNull(
            i?.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()?.takeIf { it.isNotEmpty() },
            i?.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
        ).distinct().joinToString("\n")
        val files = streams(i).take(MAX_FILES).mapNotNull { copy(it) }
        if (text.isNotEmpty() || files.isNotEmpty()) {
            SharedInbox.put(SharedInbox.Payload(text.ifEmpty { null }, files))
        }
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(SharedInbox.EXTRA_SHARED, true)
        })
        finish()
    }

    @Suppress("DEPRECATION")
    private fun streams(i: Intent?): List<Uri> {
        if (i == null) return emptyList()
        return when (i.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else i.getParcelableExtra(Intent.EXTRA_STREAM)
            )
            Intent.ACTION_SEND_MULTIPLE ->
                (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                 else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)) ?: emptyList()
            else -> emptyList()
        }
    }

    /** A copy of a shared file in the cache (its original name kept, for the agent), or null if it can't be read. */
    private fun copy(uri: Uri): File? = try {
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "shared"
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
        val dir = File(cacheDir, "shared/${System.currentTimeMillis()}").apply { mkdirs() }
        val out = File(dir, safe)
        contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
        out.takeIf { it.isFile && it.length() > 0 }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val MAX_FILES = 5           // the agent takes up to 5 attachments per message
    }
}

/** What was shared to DeskAI, waiting for the chat to pick it up (one at a time; a newer share replaces it). */
object SharedInbox {
    const val EXTRA_SHARED = "SHARED_TO_AGENT"

    data class Payload(val text: String?, val files: List<File>)

    @Volatile private var pending: Payload? = null

    fun put(p: Payload) {
        pending = p
    }

    fun take(): Payload? = pending.also { pending = null }
}
