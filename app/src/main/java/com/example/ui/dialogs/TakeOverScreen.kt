package com.example.ui.dialogs

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import androidx.compose.foundation.layout.systemBarsPadding
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.example.ui.theme.AmberPending
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.NearBlack
import com.example.ui.theme.Red300
import com.example.ui.theme.Red400
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

/**
 * Take over: the Mini's screen, live, and the owner's taps and typing replayed there with the real mouse and keyboard
 * (docs/API.md "Take over"). For CAPTCHAs (the computer tool never solves them) and anything unusual. A running task
 * waits meanwhile; Hand back (or leaving this screen) gives control back, and a task waiting on [requestId] looks at
 * the screen again.
 *
 * Tap = click, hold = right-click, pinch to zoom and drag to move around when zoomed; Double and Drag are one-shot
 * modes. The picture refreshes about every 0.6 s.
 *
 * [viewOnly] (the "View only" box on the hub card): the picture only. Control is never taken, nothing is sent, and a
 * running task carries on while the owner watches (2026-10-08: watching through Take over stopped a task).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TakeOverScreen(
    requestId: String?,
    viewOnly: Boolean = false,
    onClose: () -> Unit,
    call: suspend (action: String, body: JSONObject?) -> Result<JSONObject>,
    fetchFrame: suspend (machine: String) -> ByteArray?,
    fetchActivity: suspend (machine: String) -> JSONObject? = { null }
) {
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf(false) }             // in control (never in view only)
    var watching by remember { mutableStateOf(false) }           // view only: showing the picture
    // What a computer-use run is doing (the banner): goal, step, plain words; read-only, every 1.5 s
    var activity by remember { mutableStateOf<JSONObject?>(null) }
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
    // Which computer: the Mini, or the owner's laptop (a request is always the Mini's)
    var machine by remember { mutableStateOf("mini") }
    LaunchedEffect(machine) {
        activity = null
        while (true) {
            activity = fetchActivity(machine)
            delay(1500)
        }
    }
    // Typing: the text bar shows only after ⌨, gets the focus (so the keyboard opens with it), and goes away when
    // the keyboard is closed
    var typing by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val imeOpen = WindowInsets.isImeVisible
    var imeWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(typing) {
        if (typing) { delay(80); focus.requestFocus(); keyboard?.show() }
    }
    LaunchedEffect(imeOpen) {
        if (imeOpen) imeWasOpen = true
        else if (imeWasOpen && typing) { typing = false; imeWasOpen = false }
    }

    fun body(m: String = machine): JSONObject = JSONObject().apply {
        put("machine", m)
        if (requestId != null && m == "mini") put("request_id", requestId)
    }

    fun send(ev: JSONObject, label: String) {
        scope.launch {
            val r = call("input", ev.put("machine", machine))
            note = if (r.isSuccess) label else "Not sent: ${r.exceptionOrNull()?.message ?: "no connection"}"
        }
    }

    fun handBack() {
        if (closing) return
        closing = true
        active = false
        watching = false
        scope.launch {
            if (!viewOnly) call("stop", body())               // view only never took control
            onClose()
        }
    }

    fun spotJson(type: String, s: Offset) = JSONObject().put("type", type).put("x", s.x.toDouble()).put("y", s.y.toDouble())

    LaunchedEffect(machine) {
        active = false
        error = null
        frame = null
        scale = 1f
        pan = Offset.Zero
        if (viewOnly) {
            watching = true                                 // the picture only: no "start", nothing taken over
            return@LaunchedEffect
        }
        val r = call("start", body())
        if (r.isSuccess) active = true else error = r.exceptionOrNull()?.message ?: "Couldn't take over"
    }
    fun switchTo(m: String) {
        if (m == machine || closing) return
        val old = machine
        if (!viewOnly) scope.launch { call("stop", body(old)) }   // hand the one we're leaving back
        machine = m
    }
    LaunchedEffect(active, watching, machine) {
        while (active || watching) {
            fetchFrame(machine)?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { frame = it.asImageBitmap() }
            }
            delay(600)
        }
    }

    // Drawn in the app's own screen, not a pop-up window: a pop-up window never got the keyboard's size on the
    // owner's phone, so the keyboard covered the controls (builds 45-46). The back button hands back.
    androidx.activity.compose.BackHandler { handBack() }
    run {
        Surface(modifier = Modifier.fillMaxSize(), color = DeepNavy) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                // Top: which computer, who's in control (and the last action), and ✓ to hand back
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (requestId == null) {
                        Row(Modifier.width(96.dp)) {
                            listOf("mini" to "Mini", "laptop" to "Laptop").forEach { (id, label) ->
                                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                     color = if (machine == id) NearBlack else Slate300,
                                     modifier = Modifier.padding(end = 4.dp).clip(RoundedCornerShape(8.dp))
                                         .background(if (machine == id) AmberPending else Slate800)
                                         .clickable { switchTo(id) }.padding(horizontal = 7.dp, vertical = 4.dp))
                            }
                        }
                    } else {
                        Spacer(Modifier.width(96.dp))
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when {
                                error != null -> "Couldn't take over"
                                viewOnly -> "Watching (view only)"
                                active -> "You're in control"
                                else -> "Connecting…"
                            },
                            color = if (error != null) Red300 else Slate100,
                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                        )
                        Text(
                            text = error ?: if (viewOnly) "Taps aren't sent · tasks keep running · pinch to zoom"
                                            else note.ifBlank { "Tap to click · hold to right-click · pinch to zoom" },
                            color = Slate400, fontSize = 11.sp, maxLines = 1
                        )
                    }
                    Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterEnd) {
                        IconButton(onClick = { handBack() },
                                   modifier = Modifier.size(42.dp).clip(CircleShape).background(Slate700)) {
                            Icon(Icons.Default.Check, contentDescription = if (viewOnly) "Close" else "Hand back",
                                 tint = Color.White)
                        }
                    }
                }

                // The task banner: what the computer-use run is doing right now (2026-10-08, the owner's ask)
                activity?.let { a ->
                    val goal = a.optString("goal").takeIf { it.isNotBlank() && it != "null" }
                    val doing = a.optString("doing").takeIf { it.isNotBlank() && it != "null" }
                    val step = a.opt("step")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                    val running = a.optBoolean("running", false)
                    if (goal != null || running) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp)).background(Slate800)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = (if (running) "🤖 " else "✓ ") + (goal ?: "A task"),
                                color = if (running) AmberPending else Slate100,
                                fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2
                            )
                            if (doing != null) {
                                Text(
                                    text = (if (running && step != null) "Step $step · " else "") + doing,
                                    color = Slate300, fontSize = 12.sp, maxLines = 2
                                )
                            }
                        }
                    }
                }

                // The screen. Pinch and pan on the frame; taps on the picture land where tapped (the picture's
                // own coordinates, so zooming doesn't throw them off).
                // Container 1: the computer's screen. It takes whatever room is left, so it shrinks when the keyboard
                // opens and the controls never move under it.
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(10.dp)).background(Slate950).clipToBounds()
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
                        if (error == null) CircularProgressIndicator(color = AmberPending)
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

                // Container 2: a slim icon bar (like a remote-desktop app). Typing is hidden until ⌨ is tapped: then
                // the special keys and the text bar appear right on top of the phone's keyboard, and closing the
                // keyboard hides them again (2026-10-06, the owner: the keyboard "pops up and is all in the way").
                // View only: no controls to send anything, just close (and zoom out)
                if (viewOnly) Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).background(Slate900)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
                ) {
                    BarIcon(Icons.Default.StopCircle, "Close", tint = Red400) { handBack() }
                    if (scale > 1.01f) BarIcon(Icons.Default.ZoomOutMap, "Zoom out") { scale = 1f; pan = Offset.Zero }
                }
                if (!viewOnly) Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp).background(Slate900)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
                    ) {
                        BarIcon(Icons.Default.StopCircle, "Hand back", tint = Red400) { handBack() }
                        BarIcon(Icons.Default.Keyboard, "Keyboard", on = typing) {
                            if (typing) { typing = false; keyboard?.hide() } else typing = true
                        }
                        BarIcon(Icons.Default.TouchApp, "Tap", on = mode == "tap") { mode = "tap"; dragStart = null }
                        BarText("2×", "Double-click", on = mode == "double") {
                            mode = if (mode == "double") "tap" else "double"; dragStart = null
                        }
                        BarIcon(Icons.Default.OpenWith, "Drag", on = mode == "drag") {
                            mode = if (mode == "drag") "tap" else "drag"; dragStart = null
                        }
                        BarIcon(Icons.Default.KeyboardArrowUp, "Scroll up") {
                            val ev = JSONObject().put("type", "scroll").put("dy", 5)
                            lastSpot?.let { ev.put("x", it.x.toDouble()).put("y", it.y.toDouble()) }
                            send(ev, "Scrolled up")
                        }
                        BarIcon(Icons.Default.KeyboardArrowDown, "Scroll down") {
                            val ev = JSONObject().put("type", "scroll").put("dy", -5)
                            lastSpot?.let { ev.put("x", it.x.toDouble()).put("y", it.y.toDouble()) }
                            send(ev, "Scrolled down")
                        }
                        if (scale > 1.01f) BarIcon(Icons.Default.ZoomOutMap, "Zoom out") { scale = 1f; pan = Offset.Zero }
                    }
                    if (typing) {
                        // Special keys, then the text bar: the last thing above the keyboard
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Enter" to "enter", "⌫" to "backspace", "Tab" to "tab", "Esc" to "esc", "↑" to "up",
                                   "↓" to "down", "←" to "left", "→" to "right", "Ctrl+A" to "ctrl+a", "Ctrl+C" to "ctrl+c",
                                   "Ctrl+V" to "ctrl+v")
                                .forEach { (label, key) ->
                                    KeyButton(label) { send(JSONObject().put("type", "key").put("key", key), "Pressed $label") }
                                }
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = text, onValueChange = { text = it.take(500) }, singleLine = true,
                                placeholder = { Text("Type, then Send") },
                                modifier = Modifier.weight(1f).focusRequester(focus)
                            )
                            Spacer(Modifier.width(6.dp))
                            Button(onClick = {
                                val t = text
                                if (t.isNotEmpty()) { send(JSONObject().put("type", "text").put("text", t), "Typed ${t.length} characters"); text = "" }
                            }, enabled = active && text.isNotEmpty()) { Text("Send") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean = false,
                    tint: Color = Slate200, onClick: () -> Unit) {
    IconButton(onClick = onClick,
               modifier = Modifier.size(44.dp).clip(CircleShape).background(if (on) Slate100 else Color.Transparent)) {
        Icon(icon, contentDescription = label, tint = if (on) Slate900 else tint)
    }
}

@Composable
private fun BarText(text: String, label: String, on: Boolean = false, onClick: () -> Unit) {
    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(if (on) Slate100 else Color.Transparent)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(text, color = if (on) Slate900 else Slate200, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(10.dp)) { Text(label, color = Slate200) }
}
