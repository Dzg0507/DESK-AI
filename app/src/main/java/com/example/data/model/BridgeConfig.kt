package com.example.data.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bridge_config")
data class BridgeConfig(
    @PrimaryKey val id: Int = 1,
    val serverUrl: String = "http://192.168.12.153:8080", // Home / Local LAN URL
    val remoteUrl: String = "",                     // Away-from-home URL (Tailscale, Cloudflare, Ngrok)
    val useRemoteWhenAway: Boolean = true,
    val protocol: String = BridgeProtocol.ALWAYSON_AGENT.name,
    val apiKey: String = "",                         // Matches HUD_AUTH_TOKEN
    val selectedModel: String = "auto",
    val customPath: String = "",
    val streamResponse: Boolean = true,
    val temperature: Float = 0.7f,
    val lastPingSuccess: Boolean = false,
    val lastPingMs: Long = 0L,
    val lastPingTimestamp: Long = 0L
) {
    /**
     * Returns the active endpoint URL. If remoteUrl is set and enabled, or if target is remote,
     * it chooses the appropriate URL with trailing slashes stripped.
     */
    fun getResolvedUrl(preferRemote: Boolean = false): String {
        return if (preferRemote && remoteUrl.isNotBlank()) {
            remoteUrl.trim().removeSuffix("/")
        } else if (serverUrl.isNotBlank()) {
            serverUrl.trim().removeSuffix("/")
        } else if (remoteUrl.isNotBlank()) {
            remoteUrl.trim().removeSuffix("/")
        } else {
            "http://192.168.12.153:8080"
        }
    }

    companion object {
        /**
         * Parses a quick-import link like the one output by the Telegram bot's /hud:
         * "http://192.168.1.45:8080/?token=abc123xyz" -> ("http://192.168.1.45:8080", "abc123xyz")
         */
        fun parseImportUrl(raw: String): Pair<String, String?> {
            val trimmed = raw.trim()
            return try {
                val uri = Uri.parse(trimmed)
                val token = uri.getQueryParameter("token")
                val cleanUrl = if (uri.scheme != null && uri.host != null) {
                    val portPart = if (uri.port != -1) ":${uri.port}" else ""
                    "${uri.scheme}://${uri.host}$portPart"
                } else {
                    trimmed.substringBefore("?")
                }
                Pair(cleanUrl, token)
            } catch (_: Exception) {
                Pair(trimmed.substringBefore("?"), null)
            }
        }
        fun normalizeUrl(raw: String, defaultPort: Int = 8080): String {
            var s = raw.trim()
            if (s.isBlank()) return ""
            // Automatically correct accidental 5-octet typo 192.168.12.2.246 -> 192.168.12.246
            if (s.contains("192.168.12.2.246")) {
                s = s.replace("192.168.12.2.246", "192.168.12.246")
            }
            if (!s.startsWith("http://") && !s.startsWith("https://")) {
                s = "http://$s"
            }
            val scheme = s.substringBefore("://")
            val withoutScheme = s.substringAfter("://")
            val hostPart = withoutScheme.substringBefore("/")
            val pathPart = if (withoutScheme.contains("/")) "/" + withoutScheme.substringAfter("/") else ""
            val finalHost = if (!hostPart.contains(":") && !hostPart.contains("trycloudflare.com")) {
                "$hostPart:$defaultPort"
            } else {
                hostPart
            }
            return "$scheme://$finalHost$pathPart".removeSuffix("/")
        }
    }
}
