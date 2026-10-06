package com.example.ui.dialogs

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Take over: the Mini's screen, live, and the owner's taps and typing replayed there with the real mouse and keyboard
 * (docs/API.md "Take over"). For CAPTCHAs (the computer tool never solves them) and anything unusual. A running task
 * waits meanwhile; Hand back (or leaving this screen) gives control back, and a task waiting on [requestId] looks at
 * the screen again.
 *
 * Tap = click, hold = right-click, pinch to zoom and drag to move around when zoomed; Double and Drag are one-shot
 * modes. The picture refreshes about every 0.6 s.
 */
@Composable
fun TakeOverScreen(
    requestId: String?,
    onClose: () -> Unit,
    call: suspend (action: String, body: JSONObject?) -> Result<JSONObject>,
    fetchFrame: suspend () -> ByteArray?
) {
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var mode by remember { mutableStateOf("tap") }              // tap | double | drag
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var lastSpot by remember { mutableStateOf<Offset?>(null) }
    var text by remember { mutableStateOf("") }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var closing by remember { mutableStateOf(false) }

    fun body(): JSONObject = JSONObject().apply { if (requestId != null) put("request_id", requestId) }

    fun send(ev: JSONObject, label: String) {
        scope.launch {
            val r = call("input", ev)
            note = if (r.isSuccess) label else "Not sent: ${r.exceptionOrNull()?.message ?: "no connection"}"
        }
    }

    fun handBack() {
        if (closing) return
        closing = true
        active = false
        scope.launch {
            call("stop", body())
            onClose()
        }
    }

    fun spotJson(type: String, s: Offset) = JSONObject().put("type", type).put("x", s.x.toDouble()).put("y", s.y.toDouble())

    LaunchedEffect(Unit) {
        val r = call("start", body())
        if (r.isSuccess) active = true else error = r.exceptionOrNull()?.message ?: "Couldn't take over"
    }
    LaunchedEffect(active) {
        while (active) {
            fetchFrame()?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { frame = it.asImageBitmap() }
            }
            delay(600)
        }
    }

    Dialog(onDismissRequest = { handBack() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B1120)) {
            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                // Who's in control, and Hand back
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF111A2E)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            error != null -> "Couldn't take over: $error"
                            active -> "🖐 You're in control"
                            else -> "Connecting…"
                        },
                        color = if (error != null) Color(0xFFFCA5A5) else Color(0xFFF1F5F9),
                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { handBack() },
                           colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))) {
                        Text(if (error != null) "Close" else "Hand back")
                    }
                }
                Text(
                    text = if (note.isNotBlank()) note else "Tap to click · hold to right-click · pinch to zoom",
                    color = Color(0xFF94A3B8), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                // The screen. Pinch and pan on the frame; taps on the picture land where tapped (the picture's
                // own coordinates, so zooming doesn't throw them off).
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, panBy, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                pan = if (scale <= 1.01f) Offset.Zero else pan + panBy
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = frame
                    if (bmp == null) {
                        if (error == null) CircularProgressIndicator(color = Color(0xFFF59E0B))
                    } else {
                        Image(
                            bitmap = bmp, contentDescription = "The computer's screen", contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height)
                                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = pan.x, translationY = pan.y)
                                .pointerInput(mode, active) {
                                    detectTapGestures(
                                        onTap = { p ->
                                            if (!active) return@detectTapGestures
                                            val s = Offset((p.x / size.width).coerceIn(0f, 1f), (p.y / size.height).coerceIn(0f, 1f))
                                            lastSpot = s
                                            when (mode) {
                                                "double" -> { send(spotJson("double_tap", s), "Double-clicked"); mode = "tap" }
                                                "drag" -> {
                                                    val from = dragStart
                                                    if (from == null) {
                                                        dragStart = s
                                                        note = "Drag: now tap where it should end"
                                                    } else {
                                                        send(spotJson("drag", from).put("x2", s.x.toDouble()).put("y2", s.y.toDouble()), "Dragged")
                                                        dragStart = null
                                                        mode = "tap"
                                                    }
                                                }
                                                else -> send(spotJson("tap", s), "Clicked")
                                            }
                                        },
                                        onLongPress = { p ->
                                            if (!active) return@detectTapGestures
                                            val s = Offset((p.x / size.width).coerceIn(0f, 1f), (p.y / size.height).coerceIn(0f, 1f))
                                            lastSpot = s
                                            send(spotJson("long_press", s), "Right-clicked")
                                        }
                                    )
                                }
                        )
                    }
                }

                // Modes and scrolling
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ModeButton("Double", mode == "double") { mode = if (mode == "double") "tap" else "double"; dragStart = null }
                    ModeButton("Drag", mode == "drag") { mode = if (mode == "drag") "tap" else "drag"; dragStart = null }
                    KeyButton("Scroll ▲") {
                        val ev = JSONObject().put("type", "scroll").put("dy", 5)
                        lastSpot?.let { ev.put("x", it.x.toDouble()).put("y", it.y.toDouble()) }
                        send(ev, "Scrolled up")
                    }
                    KeyButton("Scroll ▼") {
                        val ev = JSONObject().put("type", "scroll").put("dy", -5)
                        lastSpot?.let { ev.put("x", it.x.toDouble()).put("y", it.y.toDouble()) }
                        send(ev, "Scrolled down")
                    }
                    KeyButton("Zoom 1×") { scale = 1f; pan = Offset.Zero }
                }
                // Typing
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it.take(500) }, singleLine = true,
                        placeholder = { Text("Type here, then Send") }, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = {
                        val t = text
                        if (t.isNotEmpty()) { send(JSONObject().put("type", "text").put("text", t), "Typed ${t.length} characters"); text = "" }
                    }, enabled = active && text.isNotEmpty()) { Text("Send") }
                }
                // Keys
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Enter" to "enter", "⌫" to "backspace", "Tab" to "tab", "Esc" to "esc", "↑" to "up", "↓" to "down",
                           "←" to "left", "→" to "right", "Ctrl+A" to "ctrl+a", "Ctrl+C" to "ctrl+c", "Ctrl+V" to "ctrl+v")
                        .forEach { (label, key) ->
                            KeyButton(label) { send(JSONObject().put("type", "key").put("key", key), "Pressed $label") }
                        }
                }
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, on: Boolean, onClick: () -> Unit) {
    if (on) {
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))) {
            Text(label, color = Color(0xFF111111), fontWeight = FontWeight.SemiBold)
        }
    } else {
        KeyButton(label, onClick)
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(10.dp)) { Text(label, color = Color(0xFFE2E8F0)) }
}
