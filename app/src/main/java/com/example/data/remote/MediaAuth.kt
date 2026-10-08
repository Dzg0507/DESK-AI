package com.example.data.remote

import android.net.Uri

/**
 * Keeps the HUD token out of URLs (2026-10-07). Before this, every image, video and thumbnail URL carried
 * `?token=<HUD token>`, so the token sat in the chat history, in "Copy" and "Share link", and in server logs.
 * Media now loads with the token in a header (Coil / OkHttp); a link that has to leave the app (an external
 * player, a shared link) is a short-lived signed link from the agent (POST /api/media/link), no token in it.
 */
object MediaAuth {
    // `?token=x&a=b` -> `?a=b`, then any remaining `?token=x` / `&token=x` -> removed. Stops at characters that end
    // a URL inside markdown or prose.
    private val LEADING_TOKEN = Regex("""\?token=[^&#\s)\]"'`<>]*&""")
    private val ANY_TOKEN = Regex("""[?&]token=[^&#\s)\]"'`<>]*""")

    /** The URL without its `token` query parameter. */
    fun stripToken(url: String): String {
        if (!url.contains("token=")) return url
        return ANY_TOKEN.replace(LEADING_TOKEN.replace(url, "?"), "")
    }

    /** Text (a chat message, markdown) with every URL's `token` parameter removed. For old messages saved
     *  before this fix, so the token is never shown, copied or shared again. */
    fun stripTokensInText(text: String): String = if (text.contains("token=")) stripToken(text) else text

    /**
     * Whether a URL may get the HUD token in a header: our own agent (the configured server's host) or a
     * private/Tailscale address. A picture from a public website in a chat reply must never receive it.
     */
    fun isOwnServer(url: String, serverBaseUrl: String): Boolean {
        val host = (try { Uri.parse(url).host?.lowercase() } catch (_: Exception) { null }) ?: return true  // relative
        val ownHost = (try { Uri.parse(serverBaseUrl).host?.lowercase() } catch (_: Exception) { null })
        if (ownHost != null && host == ownHost) return true
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".ts.net")) return true
        val parts = host.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        val (a, b) = parts[0] to parts[1]
        return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b in 16..31) || (a == 100 && b in 64..127)
    }
}

/**
 * Turns a media URL into one that's safe to hand to another app: a signed, expiring link from the agent. Set by
 * ChatViewModel (it knows the connection); without it, or if the agent can't sign, the URL without its token.
 */
object MediaLinks {
    @Volatile
    var signer: (suspend (String) -> String?)? = null

    suspend fun shareable(url: String): String {
        val clean = MediaAuth.stripToken(url)
        val signed = try { signer?.invoke(clean) } catch (_: Exception) { null }
        return signed?.takeIf { it.isNotBlank() } ?: clean
    }
}
