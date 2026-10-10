package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.example.ui.components.TagChip
import com.example.ui.theme.DeskShapes
import com.example.ui.theme.Slate800
import com.example.ui.theme.Violet400
import com.example.ui.theme.Violet600
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.MemoryCheck
import com.example.data.model.MemoryCheckItem
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import com.example.ui.components.SectionLabel
import com.example.ui.components.SheetHeader
import com.example.ui.components.deskCard
import com.example.ui.components.deskSheet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import kotlinx.coroutines.launch

/**
 * The weekly memory check (agent: core/memory_review.py, GET/POST /api/memory/review; 2026-10-10, the owner:
 * "I want it to remember these things, and clean up what's out of date"). Every item starts ticked: untick what's
 * wrong and Save. Ticked new facts are saved, ticked out-of-date ones retired; nothing changes before Save.
 */
@Composable
fun MemoryCheckSheet(
    onDismiss: () -> Unit,
    onLoad: suspend () -> Result<MemoryCheck?>,
    onSave: suspend (List<Int>) -> Result<String>
) {
    val scope = rememberCoroutineScope()
    var check by remember { mutableStateOf<MemoryCheck?>(null) }
    var kept by remember { mutableStateOf(emptySet<Int>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        onLoad().onSuccess { c ->
            check = c
            kept = c?.items?.map { it.n }?.toSet() ?: emptySet()
        }.onFailure { error = "Couldn't reach the agent: ${it.message}" }
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth().deskSheet().padding(16.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SheetHeader(
                    title = "Memory check",
                    icon = Icons.Default.Memory,
                    subtitle = "Untick anything that's wrong, then Save",
                    onClose = onDismiss
                )
                Spacer(Modifier.height(10.dp))
                val c = check
                when {
                    loading -> LoadingState("Loading this week's check…")
                    done != null -> Text(done!!, color = ElectricCyan, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                    error != null -> Text(error!!, color = RoseError, fontSize = 13.sp)
                    c == null || c.items.isEmpty() -> EmptyState(
                        icon = Icons.Default.Memory,
                        title = "Nothing waiting",
                        message = "This check was already answered. The next one comes on Saturday."
                    )
                    else -> {
                        val adds = c.items.filter { it.op == "add" }
                        val retires = c.items.filter { it.op == "retire" }
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (adds.isNotEmpty()) item { SectionLabel("Remember") }
                            items(adds, key = { it.n }) { item ->
                                CheckRow(item, item.n in kept) { on -> kept = if (on) kept + item.n else kept - item.n }
                            }
                            if (retires.isNotEmpty()) item { SectionLabel("Out of date (retire)") }
                            items(retires, key = { it.n }) { item ->
                                CheckRow(item, item.n in kept) { on -> kept = if (on) kept + item.n else kept - item.n }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                saving = true
                                scope.launch {
                                    onSave(kept.sorted())
                                        .onSuccess { done = it }
                                        .onFailure { error = "Not saved: ${it.message}" }
                                    saving = false
                                }
                            },
                            enabled = !saving,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (saving) "Saving…" else "Save (${kept.size} of ${c.items.size})",
                                color = Color.Black, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/** The card under the agent's memory-check message (like CanvasCard): tapping it opens MemoryCheckSheet. */
@Composable
fun MemoryCheckCard(onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DeskShapes.card)
            .background(Brush.horizontalGradient(listOf(Violet600.copy(alpha = 0.18f), Slate800)))
            .border(1.dp, Violet400.copy(alpha = 0.35f), DeskShapes.card)
            .clickable { onOpen() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🧠", fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("This week's memories", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("Tick what's right · nothing changes until you save", color = Slate400, fontSize = 12.sp)
        }
        TagChip(text = "Review ›", color = Violet400)
    }
}

@Composable
private fun CheckRow(item: MemoryCheckItem, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .deskCard()
            .clickable { onChange(!checked) }
            .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column(Modifier.weight(1f)) {
            Text(item.content, color = Color.White, fontSize = 13.sp)
            if (!item.note.isNullOrBlank()) {
                Text(
                    if (item.op == "add") "From: “${item.note}”" else item.note,
                    color = Slate400, fontSize = 11.sp
                )
            }
        }
    }
}
