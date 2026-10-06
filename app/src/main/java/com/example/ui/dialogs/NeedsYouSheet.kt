package com.example.ui.dialogs

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
    onScreenshot: suspend (path: String) -> ByteArray?
) {
    LaunchedEffect(Unit) { onRefresh() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                     containerColor = Color(0xFF0F172A)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("⚠️ Needs you", color = Color(0xFFFCD34D), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("A computer task on the Mini is waiting for your answer.", color = Color(0xFF94A3B8), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            if (requests.isEmpty()) {
                Text("Nothing is waiting right now.", color = Color(0xFFCBD5E1), fontSize = 14.sp,
                     modifier = Modifier.padding(vertical = 24.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(requests, key = { it.id }) { req -> RequestCard(req, onAnswer, onScreenshot) }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(
    req: ComputerRequest,
    onAnswer: suspend (String, String) -> Result<String>,
    onScreenshot: suspend (String) -> ByteArray?
) {
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reply by remember { mutableStateOf("") }
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1E293B))
            .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(14.dp)).padding(12.dp)
    ) {
        req.site()?.let {
            Text("🌐 $it", color = Color(0xFF7DD3FC), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
        }
        if (bitmap != null) {
            Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "What the task sees",
                  contentScale = ContentScale.FillWidth,
                  modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.height(8.dp))
        } else if (req.screenshotPath != null) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        }
        Text(req.question, color = Color(0xFFF8FAFC), fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        when (req.kind) {
            "approve" -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { send("Approve") }, enabled = !sending,
                       colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))) { Text("✅ Approve") }
                OutlinedButton(onClick = { send("Deny") }, enabled = !sending) { Text("✋ Deny", color = Color(0xFFFCA5A5)) }
            }
            "choice" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                req.choices.forEach { c ->
                    OutlinedButton(onClick = { send(c) }, enabled = !sending, modifier = Modifier.fillMaxWidth()) {
                        Text(c, color = Color(0xFFE2E8F0))
                    }
                }
            }
            else -> Column {
                OutlinedTextField(value = reply, onValueChange = { reply = it }, modifier = Modifier.fillMaxWidth(),
                                  placeholder = { Text("Your answer") }, enabled = !sending)
                Spacer(Modifier.height(6.dp))
                Button(onClick = { send(reply.trim()) }, enabled = !sending && reply.isNotBlank(),
                       modifier = Modifier.align(Alignment.End)) { Text("Send") }
            }
        }
        if (sending) Text("Sending…", color = Color(0xFF94A3B8), fontSize = 12.sp)
        error?.let { Text("Couldn't send: $it", color = Color(0xFFFCA5A5), fontSize = 12.sp) }
    }
}
