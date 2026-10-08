package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CanvasRef
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Violet400
import com.example.ui.theme.Violet600

/** The card under a chat reply that wrote or changed a draft; tapping it opens the canvas. */
@Composable
fun CanvasCard(ref: CanvasRef, onOpen: (CanvasRef) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Slate800)
            .clickable { onOpen(ref) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (ref.kind == "page") "🌐" else "📄", fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(ref.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (ref.kind == "page") "Page preview" else "Draft") + " · version ${ref.version}",
                color = Slate400, fontSize = 12.sp
            )
        }
        Text("Open", color = Violet400, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

/**
 * The canvas: a draft shown full screen, with its versions. The page comes from the agent's
 * /api/canvas/{id}/preview, which blocks scripts itself; JavaScript is off here too, and the token
 * travels in a header, never in the URL.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CanvasDialog(
    ref: CanvasRef,
    serverBaseUrl: String,
    authToken: String,
    loadVersions: suspend (Long) -> List<Int>,
    onDiscuss: (CanvasRef) -> Unit,
    onDismiss: () -> Unit
) {
    var versions by remember { mutableStateOf(listOf(ref.version)) }
    var shown by remember { mutableIntStateOf(ref.version) }
    LaunchedEffect(ref.id) {
        val loaded = loadVersions(ref.id)
        if (loaded.isNotEmpty()) {
            versions = loaded
            shown = loaded.last()
        }
    }
    val base = serverBaseUrl.trimEnd('/')
    val url = "$base/api/canvas/${ref.id}/preview?version=$shown"
    val headers = if (authToken.isNotBlank()) mapOf("X-HUD-Token" to authToken.trim()) else emptyMap()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Slate900)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✕", color = Slate300, fontSize = 20.sp, modifier = Modifier.clickable { onDismiss() }.padding(6.dp))
                Spacer(Modifier.width(8.dp))
                Text(ref.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("💬 Discuss", color = Violet400, fontSize = 13.sp,
                    modifier = Modifier.clickable { onDiscuss(ref); onDismiss() }.padding(6.dp))
            }
            if (versions.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    versions.forEach { v ->
                        val selected = v == shown
                        Text(
                            "v$v",
                            color = if (selected) Color.White else Slate400,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) Violet600 else Slate800)
                                .clickable { shown = v }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            webViewClient = WebViewClient()
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            loadUrl(url, headers)
                        }
                    },
                    update = { web -> if (web.url != url) web.loadUrl(url, headers) }
                )
            }
        }
    }
}
