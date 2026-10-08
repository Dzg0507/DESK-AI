package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AgentSchedule
import com.example.data.model.ScheduleList
import com.example.data.model.SchedulePreview
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RoseError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import com.example.ui.theme.AmberPending
import com.example.ui.theme.DeepCard
import com.example.ui.theme.DeepPanel
import com.example.ui.theme.HotPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.components.deskSheet
import androidx.compose.ui.graphics.Brush
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import com.example.ui.components.SheetHeader
import com.example.ui.components.StatusNote
import com.example.ui.components.StatusPill
import com.example.ui.components.TagChip
import com.example.ui.components.deskCard
import com.example.ui.theme.Cyan400
import com.example.ui.theme.DeskShapes

private val Muted = Slate400
private val Faint = Slate500
private val Card = Slate800
private val TikTokPink = HotPink

/** "Tue, Sep 29 18:00" from the server's ISO time (min SDK 24, so no java.time). */
private fun formatRun(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(iso)
        SimpleDateFormat("EEE, MMM d HH:mm", Locale.getDefault()).format(parsed!!)
    } catch (_: Exception) {
        iso.take(16).replace('T', ' ')
    }
}

private fun kindIcon(kind: String): ImageVector = when (kind) {
    "reminder" -> Icons.Default.Notifications
    "task" -> Icons.Default.Task
    else -> Icons.Default.Videocam
}

private fun kindColor(kind: String): Color = when (kind) {
    "reminder" -> AmberPending
    "task" -> NeonPurple
    else -> ElectricCyan
}

@Composable
fun SchedulesSheet(
    onDismiss: () -> Unit,
    onFetch: suspend () -> Result<ScheduleList>,
    onPreview: suspend (cron: String, kind: String) -> Result<SchedulePreview>,
    onCreate: suspend (fields: JSONObject) -> Result<AgentSchedule>,
    onUpdate: suspend (id: Int, fields: JSONObject) -> Result<AgentSchedule>,
    onDelete: suspend (id: Int) -> Result<Unit>,
    onRunNow: suspend (id: Int) -> Result<String>
) {
    val scope = rememberCoroutineScope()
    var schedules by remember { mutableStateOf<List<AgentSchedule>>(emptyList()) }
    var timezone by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var showForm by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }     // message, success

    fun refresh() {
        scope.launch {
            isLoading = true
            onFetch().onSuccess { schedules = it.schedules; timezone = it.timezone }
                .onFailure { status = "⚠️ ${it.message}" to false }
            isLoading = false
        }
    }

    fun replace(updated: AgentSchedule) {
        schedules = schedules.map { if (it.id == updated.id) updated else it }
    }

    LaunchedEffect(Unit) { refresh() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .deskSheet()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                SheetHeader(
                    title = if (showForm) "New schedule" else "Schedules",
                    icon = Icons.Default.Schedule,
                    subtitle = "Runs by itself" + if (timezone.isNotBlank()) " · times in $timezone" else "",
                    onClose = onDismiss
                ) {
                    if (!showForm) {
                        IconButton(onClick = { refresh() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                status?.let { (msg, ok) ->
                    Spacer(modifier = Modifier.height(8.dp))
                    StatusNote(msg, modifier = Modifier.fillMaxWidth(), color = if (ok) EmeraldConnected else RoseError)
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (showForm) {
                    ScheduleForm(
                        modifier = Modifier.weight(1f),
                        onPreview = onPreview,
                        onCancel = { showForm = false },
                        onSave = { fields ->
                            val res = onCreate(fields)
                            res.onSuccess {
                                schedules = schedules + it
                                status = "✅ \"${it.name}\" set: ${it.description}" to true
                                showForm = false
                            }
                            res.exceptionOrNull()?.message
                        }
                    )
                } else {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            isLoading && schedules.isEmpty() -> LoadingState("Loading schedules…")
                            schedules.isEmpty() -> EmptyState(
                                icon = Icons.Default.Schedule,
                                title = "Nothing scheduled yet",
                                message = "Tap New schedule below, or just ask in chat, like " +
                                        "\"post a TikTok every day at 6pm\" or \"remind me on weekdays at 8:30 to take my vitamins\".",
                                accent = ElectricCyan
                            )
                            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(schedules, key = { it.id }) { s ->
                                    ScheduleCard(
                                        s = s,
                                        onToggle = { on ->
                                            scope.launch {
                                                onUpdate(s.id, JSONObject().put("enabled", on))
                                                    .onSuccess { replace(it); status = "${if (on) "▶️ Resumed" else "⏸️ Paused"} \"${it.name}\"" to true }
                                                    .onFailure { status = "⚠️ ${it.message}" to false }
                                            }
                                        },
                                        onRunNow = {
                                            scope.launch {
                                                onRunNow(s.id)
                                                    .onSuccess { status = "🚀 \"${s.name}\": $it" to true; refresh() }
                                                    .onFailure { status = "⚠️ ${it.message}" to false }
                                            }
                                        },
                                        onDelete = {
                                            scope.launch {
                                                onDelete(s.id)
                                                    .onSuccess { schedules = schedules.filter { it.id != s.id }; status = "🗑️ Deleted \"${s.name}\"" to true }
                                                    .onFailure { status = "⚠️ ${it.message}" to false }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DeskShapes.control)
                            .background(Brush.horizontalGradient(listOf(ElectricCyan, Cyan400)))
                            .clickable { status = null; showForm = true }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("NEW SCHEDULE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(s: AgentSchedule, onToggle: (Boolean) -> Unit, onRunNow: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember(s.id) { mutableStateOf(false) }
    val accent = kindColor(s.kind)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .deskCard(borderColor = if (s.enabled) accent.copy(alpha = 0.35f) else Slate700)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(30.dp).clip(CircleShape).background(accent.copy(alpha = if (s.enabled) 0.18f else 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(kindIcon(s.kind), contentDescription = s.kind, tint = if (s.enabled) accent else Faint, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(s.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (s.enabled) Color.White else Muted,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(s.description.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = if (s.enabled) accent else Faint)
            }
            Switch(
                checked = s.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        when (s.kind) {
            "video" -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎬 A fresh affirmation video", fontSize = 11.sp, color = Slate300)
                if (s.postToTiktok) {
                    Spacer(modifier = Modifier.width(6.dp))
                    StatusPill("Posts to TikTok", TikTokPink, showDot = false)
                }
            }
            "reminder" -> Text("⏰ \"${s.text ?: s.name}\"", fontSize = 11.sp, color = Slate300)
            else -> Text("⚙️ ${s.instruction ?: ""}" + (s.project?.let { " · on $it" } ?: ""), fontSize = 11.sp,
                color = Slate300, maxLines = 2)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (s.enabled) {
                TagChip(text = "Next · ${formatRun(s.nextRunAt)}", color = accent, icon = Icons.Default.Schedule)
            } else {
                StatusPill("Paused", Muted)
            }
        }
        if (s.lastRunAt != null) {
            Text("Last: ${formatRun(s.lastRunAt)} · ${s.lastResult ?: ""}", fontSize = 10.sp, color = Faint,
                fontFamily = FontFamily.Monospace, maxLines = 2)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onRunNow) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Run now", fontSize = 11.sp, color = ElectricCyan)
            }
            TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = RoseError, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (confirmDelete) "Tap again to delete" else "Delete", fontSize = 11.sp, color = RoseError)
            }
        }
    }
}

// Mon first on screen; cron numbers Sunday as 0
private val WEEK = listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 0 to "S")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleForm(
    modifier: Modifier,
    onPreview: suspend (cron: String, kind: String) -> Result<SchedulePreview>,
    onCancel: () -> Unit,
    onSave: suspend (JSONObject) -> String?          // returns an error message, or null when saved
) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("video") }
    var name by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf(9) }
    var minute by remember { mutableStateOf(0) }
    var days by remember { mutableStateOf(setOf(0, 1, 2, 3, 4, 5, 6)) }
    var customCron by remember { mutableStateOf(false) }
    var cronText by remember { mutableStateOf("0 9 * * *") }
    var postToTiktok by remember { mutableStateOf(false) }
    var reminderText by remember { mutableStateOf("") }
    var instruction by remember { mutableStateOf("") }
    var showTimePicker by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<SchedulePreview?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val cron = if (customCron) cronText.trim() else
        "$minute $hour * * " + if (days.size == 7) "*" else days.sorted().joinToString(",")

    LaunchedEffect(cron, kind) {
        preview = null
        if (!customCron && days.isEmpty()) return@LaunchedEffect
        delay(350)                                     // wait for typing to settle
        preview = onPreview(cron, kind).getOrNull()
    }

    val detailOk = when (kind) {
        "reminder" -> reminderText.isNotBlank()
        "task" -> instruction.trim().length >= 8
        else -> true
    }
    val canSave = preview?.valid == true && detailOk && !saving

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = DeepCard, unfocusedContainerColor = DeepPanel,
        focusedBorderColor = ElectricCyan, unfocusedBorderColor = Slate800,
        focusedTextColor = Color.White, unfocusedTextColor = Slate200
    )

    @Composable
    fun label(text: String) = Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Muted, letterSpacing = 0.8.sp)

    @Composable
    fun chip(text: String, selected: Boolean, color: Color = ElectricCyan, onClick: () -> Unit) {
        Text(
            text = text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Color.Black else Slate300,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) color else Card)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            label("WHAT")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chip("🎬 Video", kind == "video", kindColor("video")) { kind = "video" }
                chip("⏰ Reminder", kind == "reminder", kindColor("reminder")) { kind = "reminder" }
                chip("⚙️ Task", kind == "task", kindColor("task")) { kind = "task" }
            }
            when (kind) {
                "video" -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Also post it to TikTok", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(if (postToTiktok) "Each video goes public on @Thevibecheckproject by itself" else "Render only; you post it yourself",
                            fontSize = 10.sp, color = if (postToTiktok) TikTokPink else Muted)
                    }
                    Switch(checked = postToTiktok, onCheckedChange = { postToTiktok = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = TikTokPink, checkedThumbColor = Color.White))
                }
                "reminder" -> OutlinedTextField(
                    value = reminderText, onValueChange = { reminderText = it }, colors = fieldColors,
                    placeholder = { Text("Remind me to…", color = Faint, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
                )
                else -> OutlinedTextField(
                    value = instruction, onValueChange = { instruction = it }, colors = fieldColors,
                    placeholder = { Text("What should the agent do? (e.g. Check the website for broken links)", color = Faint, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), minLines = 2
                )
            }

            label("WHEN")
            if (!customCron) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format(Locale.US, "%02d:%02d", hour, minute),
                        fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Card).clickable { showTimePicker = true }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Tap to change the time", fontSize = 11.sp, color = Muted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    WEEK.forEach { (d, letter) ->
                        val on = d in days
                        Box(
                            modifier = Modifier.size(34.dp).clip(CircleShape).background(if (on) ElectricCyan else Card)
                                .clickable { days = if (on) days - d else days + d },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(letter, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) Color.Black else Muted)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chip("Every day", days.size == 7) { days = setOf(0, 1, 2, 3, 4, 5, 6) }
                    chip("Weekdays", days == setOf(1, 2, 3, 4, 5)) { days = setOf(1, 2, 3, 4, 5) }
                    chip("Weekends", days == setOf(0, 6)) { days = setOf(0, 6) }
                }
            } else {
                OutlinedTextField(
                    value = cronText, onValueChange = { cronText = it }, colors = fieldColors, singleLine = true,
                    placeholder = { Text("minute hour day month weekday, e.g. 0 */3 * * *", color = Faint, fontSize = 12.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, color = Color.White, fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
                )
            }
            Text(
                text = if (customCron) "Use the simple picker" else "Advanced: type a cron schedule",
                fontSize = 11.sp, color = ElectricCyan,
                modifier = Modifier.clickable {
                    if (!customCron) cronText = cron
                    customCron = !customCron
                }
            )

            // What the server makes of it
            val p = preview
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(DeepCard).padding(10.dp)) {
                when {
                    !customCron && days.isEmpty() -> Text("Pick at least one day", fontSize = 12.sp, color = RoseError)
                    p == null -> Text("Checking…", fontSize = 12.sp, color = Muted)
                    !p.valid -> Text("⚠️ ${p.error}", fontSize = 12.sp, color = RoseError)
                    else -> {
                        Text("Runs ${p.description}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EmeraldConnected)
                        Text("Next: " + p.nextRuns.joinToString("  ·  ") { formatRun(it) }, fontSize = 10.sp, color = Muted,
                            fontFamily = FontFamily.Monospace)
                    }
                }
            }

            label("NAME (OPTIONAL)")
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(80) }, colors = fieldColors, singleLine = true,
                placeholder = { Text(when (kind) { "reminder" -> "e.g. Vitamins"; "task" -> "e.g. Weekly link check"; else -> "e.g. Evening TikTok" },
                    color = Faint, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
            )
            error?.let { Text("⚠️ $it", fontSize = 11.sp, color = RoseError) }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Card).clickable(onClick = onCancel).padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("CANCEL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted) }
            Box(
                modifier = Modifier.weight(2f).clip(RoundedCornerShape(10.dp))
                    .background(if (canSave) ElectricCyan else Slate700)
                    .clickable(enabled = canSave) {
                        scope.launch {
                            saving = true
                            error = null
                            val fields = JSONObject().put("kind", kind).put("cron", cron).put("name", name.trim())
                            when (kind) {
                                "video" -> fields.put("post_to_tiktok", postToTiktok)
                                "reminder" -> fields.put("text", reminderText.trim())
                                else -> fields.put("instruction", instruction.trim())
                            }
                            error = onSave(fields)
                            saving = false
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                else Text("SAVE SCHEDULE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (canSave) Color.Black else Muted)
            }
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = { TextButton(onClick = { hour = state.hour; minute = state.minute; showTimePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) }
        )
    }
}
