package com.example.data.model

/** A file a sent message carried, as the chat bubble shows it (stored in ChatMessage.attachmentsJson). */
data class MessageAttachment(
    val name: String,
    val kind: String,              // pdf, word, text, image, video
    val localPath: String? = null  // the phone's copy of an image, drawn in the bubble
) {
    val isImage: Boolean get() = kind == "image"

    companion object {
        private val IMAGE = Regex("\\.(png|jpe?g|webp|gif|bmp|heic|heif|tiff?)$", RegexOption.IGNORE_CASE)
        private val VIDEO = Regex("\\.(mp4|mov|webm|avi|mkv|m4v)$", RegexOption.IGNORE_CASE)

        fun kindOf(name: String): String = when {
            IMAGE.containsMatchIn(name) -> "image"
            VIDEO.containsMatchIn(name) -> "video"
            name.endsWith(".pdf", ignoreCase = true) -> "pdf"
            name.endsWith(".doc", ignoreCase = true) || name.endsWith(".docx", ignoreCase = true) -> "word"
            else -> "text"
        }

        fun toJson(list: List<MessageAttachment>): String = org.json.JSONArray().apply {
            list.forEach { a ->
                put(org.json.JSONObject().apply {
                    put("name", a.name)
                    put("kind", a.kind)
                    if (a.localPath != null) put("path", a.localPath)
                })
            }
        }.toString()

        fun fromJson(json: String?): List<MessageAttachment> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val arr = org.json.JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    MessageAttachment(o.optString("name"), o.optString("kind", "text"),
                        o.optString("path").ifBlank { null })
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
