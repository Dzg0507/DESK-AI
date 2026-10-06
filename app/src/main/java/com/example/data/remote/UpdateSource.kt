package com.example.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Where DeskAI finds its newest version. GitHub's raw file servers keep a copy for up to 5 minutes and ignore
 * `?t=` cache-busting, so right after a release the badge and the update screen could read different copies (the
 * owner saw "update available" and then "up to date", 2026-10-06), and a download could fetch the previous APK.
 * So: ask GitHub's API for the newest commit on main, then read version.json and the APK *at that commit*: a
 * path no cache can have an old copy of. If the API can't be reached (it allows 60 calls an hour), fall back to
 * the plain raw file.
 */
object UpdateSource {
    private const val API_COMMIT = "https://api.github.com/repos/Dzg0507/DESK-AI/commits/main"
    private const val RAW = "https://raw.githubusercontent.com/Dzg0507/DESK-AI"

    data class Latest(val json: JSONObject, val sha: String?) {
        val versionCode: Int get() = json.optInt("versionCode", 1)
        /** The APK of exactly this version (pinned to the commit), or null to use version.json's own link. */
        val apkUrl: String? get() = sha?.let { "$RAW/$it/DeskAI.apk" }
    }

    private fun newestCommit(client: OkHttpClient): String? = try {
        val req = Request.Builder().url(API_COMMIT)
            .header("Accept", "application/vnd.github.sha")
            .header("User-Agent", "DeskAI-Android")
            .build()
        client.newCall(req).execute().use { resp ->
            val sha = resp.body?.string()?.trim().orEmpty()
            if (resp.isSuccessful && Regex("^[0-9a-f]{40}$").matches(sha)) sha else null
        }
    } catch (_: Exception) {
        null
    }

    /** The newest version.json (blocking: call off the main thread), or null if GitHub can't be reached. */
    fun fetch(client: OkHttpClient): Latest? {
        val sha = newestCommit(client)
        val candidates = listOfNotNull(
            sha?.let { "$RAW/$it/web_dist/version.json" to it },
            "$RAW/main/web_dist/version.json?t=${System.currentTimeMillis()}" to null
        )
        for ((url, pinned) in candidates) {
            try {
                val req = Request.Builder().url(url).header("User-Agent", "DeskAI-Android").build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()?.trim().orEmpty()
                    if (resp.isSuccessful && body.startsWith("{") && body.contains("versionCode")) {
                        return Latest(JSONObject(body), pinned)
                    }
                }
            } catch (_: Exception) {
                // try the next one
            }
        }
        return null
    }
}
