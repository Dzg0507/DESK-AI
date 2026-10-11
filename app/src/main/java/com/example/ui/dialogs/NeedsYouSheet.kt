package com.example.ui.dialogs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.graphics.Brush
import com.example.ui.components.SheetHeader
import com.example.ui.components.EmptyState
import com.example.ui.theme.DeskShapes
import com.example.ui.theme.EmeraldConnected
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ComputerRequest
import kotlinx.coroutines.launch
import com.example.ui.theme.Amber300
import com.example.ui.theme.AmberPending
import com.example.ui.theme.Green600
import com.example.ui.theme.Red300
import com.example.ui.theme.Sky300
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

/**
 * What computer tasks on the Mini are waiting for: each with its screenshot (the spot outlined in red), the
 * site in words, the question, and the answer: Approve/Deny, one of the pop-up's choices, or a typed reply.
 * The task carries on as soon as the answer arrives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeedsYouSheet(
    requests: List<ComputerRequest>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onAnswer: suspend (requestId: String, answer: String) -> Result<String>,
    onScreenshot: suspend (path: String) -> ByteArray?,
    onTakeOver: (requestId: String?) -> Unit = {}
) {
    LaunchedEffect(Unit) { onRefresh() }
    // A password or code on screen: no screenshots, screen recording or recent-apps preview of this sheet
    val secure = requests.any { it.kind == "secret" }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                     containerColor = Slate900,
                     properties = androidx.compose.material3.ModalBottomSheetProperties(
                         securePolicy = if (secure) androidx.compose.ui.window.SecureFlagPolicy.SecureOn
                                        else androidx.compose.ui.window.SecureFlagPolicy.Inherit)) {
        // Sideways (only View only turns the phone): above the keyboard. Upright, unchanged.
        val landscape = isLandscape()
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)
            .then(if (landscape) Modifier.imePadding() else Modifier)) {
            SheetHeader(
                title = "Needs you",
                icon = Icons.Default.Warning,
                accent = AmberPending,
                subtitle = if (requests.isEmpty()) "Nothing waiting on the Mini"
                           else "${requests.size} computer task${if (requests.size == 1) " is" else "s are"} waiting for your answer"
            )
            Spacer(Modifier.height(10.dp))
            // Use the Mini yourself from here, any time (a running task waits until you hand back)
            OutlinedButton(onClick = { onTakeOver(null) }) { Text("🖐 Take over the computer", color = Slate200) }
            Spacer(Modifier.height(12.dp))
            if (requests.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.CheckCircle,
                    title = "Nothing is waiting right now",
                    message = "When a computer task needs an answer, it shows up here.",
                    accent = EmeraldConnected
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(requests, key = { it.id }) { req -> RequestCard(req, onAnswer, onScreenshot, onTakeOver) }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(
    req: ComputerRequest,
    onAnswer: suspend (String, String) -> Result<String>,
    onScreenshot: suspend (String) -> ByteArray?,
    onTakeOver: (String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reply by remember { mutableStateOf("") }
    // Sideways (View only): a short screenshot, and Send inside the answer box and on the keyboard, so what's
    // typed and Send stay in view (2026-10-11, the owner: in View only "I can barely scroll and see what I'm typing,
    // and sometimes I can't hit send"). Upright, the card is as before.
    val landscape = isLandscape()
    val shotMax = if (landscape) (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.34f).dp
                  else 360.dp
    val bitmap by produceState<android.graphics.Bitmap?>(null, req.screenshotPath) {
        value = req.screenshotPath?.let { path ->
            onScreenshot(path)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
    }

    fun send(answer: String) {
        if (sending) return
        sending = true
        error = null
        scope.launch {
            val r = onAnswer(req.id, answer)
            sending = false
            if (r.isFailure) error = r.exceptionOrNull()?.message ?: "Couldn't send"
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().clip(DeskShapes.card)
            .background(Brush.verticalGradient(listOf(AmberPending.copy(alpha = 0.08f), Slate800)))
            .border(1.dp, AmberPending.copy(alpha = 0.7f), DeskShapes.card).padding(14.dp)
    ) {
        req.site()?.let {
            Text("🌐 $it", color = Sky300, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
        }
        if (bitmap != null) {
            Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "What the task sees",
                  contentScale = ContentScale.FillWidth,
                  modifier = Modifier.fillMaxWidth().heightIn(max = shotMax).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.height(8.dp))
        } else if (req.screenshotPath != null) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        }
        Text(req.question, color = Slate50, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        when (req.kind) {
            "approve" -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { send("Approve") }, enabled = !sending,
                       colors = ButtonDefaults.buttonColors(containerColor = Green600)) { Text("✅ Approve") }
                OutlinedButton(onClick = { send("Deny") }, enabled = !sending) { Text("✋ Deny", color = Red300) }
            }
            "secret" -> Column {
                var shown by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = reply, onValueChange = { reply = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Password or code") }, enabled = !sending, singleLine = true,
                    visualTransformation = if (shown) androidx.compose.ui.text.input.VisualTransformation.None
                                           else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        imeAction = if (landscape) androidx.compose.ui.text.input.ImeAction.Send
                                    else androidx.compose.ui.text.input.ImeAction.Default),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = { if (reply.isNotEmpty()) { val v = reply; reply = ""; send(v) } }),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.TextButton(onClick = { shown = !shown }) {
                                Text(if (shown) "Hide" else "Show", color = Slate400, fontSize = 12.sp)
                            }
                            if (landscape) SendArrow(!sending && reply.isNotEmpty()) { val v = reply; reply = ""; send(v) }
                        }
                    }
                )
                Text("🔒 Typed straight into the outlined box on the Mini. The AI never sees it, and it isn't saved.",
                     color = Slate400, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                if (!landscape) {
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { val v = reply; reply = ""; send(v) }, enabled = !sending && reply.isNotEmpty(),
                           modifier = Modifier.align(Alignment.End)) { Text("Send") }
                }
            }
            "choice" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                req.choices.forEach { c ->
                    OutlinedButton(onClick = { send(c) }, enabled = !sending, modifier = Modifier.fillMaxWidth()) {
                        Text(c, color = Slate200)
                    }
                }
            }
            else -> Column {
                if (landscape) {
                    OutlinedTextField(value = reply, onValueChange = { reply = it }, modifier = Modifier.fillMaxWidth(),
                                      placeholder = { Text("Your answer") }, enabled = !sending, maxLines = 3,
                                      keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                          imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                                      keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                          onSend = { if (reply.isNotBlank()) send(reply.trim()) }),
                                      trailingIcon = { SendArrow(!sending && reply.isNotBlank()) { send(reply.trim()) } })
                } else {
                    OutlinedTextField(value = reply, onValueChange = { reply = it }, modifier = Modifier.fillMaxWidth(),
                                      placeholder = { Text("Your answer") }, enabled = !sending)
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { send(reply.trim()) }, enabled = !sending && reply.isNotBlank(),
                           modifier = Modifier.align(Alignment.End)) { Text("Send") }
                }
            }
        }
        // Or do it yourself on the Mini's screen (a CAPTCHA, anything odd); the task looks again when you hand back
        androidx.compose.material3.TextButton(onClick = { onTakeOver(req.id) }, enabled = !sending) {
            Text("🖐 Take over and do it yourself", color = Sky300)
        }
        if (sending) Text("Sending…", color = Slate400, fontSize = 12.sp)
        error?.let { Text("Couldn't send: $it", color = Red300, fontSize = 12.sp) }
    }
}


@Composable
private fun isLandscape(): Boolean =
    androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE


/** Send inside an answer box (sideways, in View only): next to what's typed, never pushed off by the keyboard. */
@Composable
private fun SendArrow(enabled: Boolean, onSend: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onSend, enabled = enabled) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send", tint = if (enabled) Sky300 else Slate400)
    }
}
