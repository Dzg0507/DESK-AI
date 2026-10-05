package com.example.data.model

/**
 * A file attached to a chat message. The app uploads it first (POST /api/chat/attachments); the agent keeps it
 * and reads its text (documents directly, images and scans by OCR), then the message carries its id.
 */
data class ChatAttachment(
    val localId: String,                 // the chip's key while it uploads
    val name: String,
    val status: String = "uploading",    // "uploading", "ready", "error"
    val id: String? = null,              // the agent's id, e.g. att-1a2b3c4d5e6f (when ready)
    val kind: String? = null,            // pdf, word, text, image, video
    val method: String? = null,          // text, ocr, mixed, video
    val confidence: Double? = null,      // OCR confidence 0-100
    val warnings: List<String> = emptyList(),
    val error: String? = null,
    val thumbUrl: String? = null,
    val thumbB64: String? = null,
    val hasThumb: Boolean = false,
    val localPath: String? = null        // a small copy of a picked image on the phone (chip + chat bubble)
) {
    /** What the chip's thumbnail loads: the phone's copy, else the agent's thumbnail (Coil 2 can't load data: URIs). */
    fun thumbModel(): Any? = localPath?.let { java.io.File(it) }
        ?: thumbB64?.substringAfter("base64,", "")?.takeIf { it.isNotEmpty() }?.let {
            try { java.nio.ByteBuffer.wrap(android.util.Base64.decode(it, android.util.Base64.DEFAULT)) } catch (_: Exception) { null }
        }
        ?: thumbUrl

    /** For the message's attachment list. */
    fun toMessageAttachment() = MessageAttachment(name, kind ?: MessageAttachment.kindOf(name), localPath)

    /** A few words for the chip under the file name. */
    fun statusLine(): String = when (status) {
        "uploading" -> "Reading…"
        "error" -> error ?: "Couldn't be read"
        else -> when {
            warnings.isNotEmpty() -> warnings.first()
            method == "ocr" && confidence != null -> "Read by OCR (${confidence.toInt()}% sure)"
            method == "ocr" -> "Read by OCR"
            method == "mixed" -> "Text + OCR for scanned pages"
            method == "video" -> "Video media"
            else -> "Ready"
        }
    }
}
