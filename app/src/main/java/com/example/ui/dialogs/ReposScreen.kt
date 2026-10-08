package com.example.ui.dialogs

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.height
import com.example.ui.components.EmptyState
import com.example.ui.components.StatusNote
import com.example.ui.components.TagChip
import com.example.ui.components.deskCard
import com.example.ui.theme.DeskShapes
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import com.example.ui.theme.AmberPending
import com.example.ui.theme.Cyan400
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.DeepTeal
import com.example.ui.theme.Gray900
import com.example.ui.theme.Green400
import com.example.ui.theme.Red400
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500

/**
 * Repos: the owner's GitHub repos (both accounts) browsed like a file explorer, read-only (docs/API.md "Repos").
 * Repo list -> folders -> a file's text. "Work on this" sends an instruction for the repo (and the folder or file
 * being viewed) as an AgentWork job; the agent sets the repo up as a project by itself if it isn't one yet, so
 * nothing has to be added by hand. The job runs on its own branch and shows up as a task card like any other.
 *
 * Drawn in the activity, not a pop-up window: pop-ups don't get the keyboard's size on the owner's phone (the
 * Take over lesson, builds 45-47). Back goes up one level, and closes from the repo list.
 */
@Composable
fun ReposScreen(
    onClose: () -> Unit,
    call: suspend (method: String, path: String, body: JSONObject?, idempotencyKey: String?) -> Result<JSONObject>
) {
    val scope = rememberCoroutineScope()
    var repos by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf<JSONObject?>(null) }       // the open repo (null: the repo list)
    var path by remember { mutableStateOf("") }                      // the open folder, "" = the repo's top
    var entries by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var file by remember { mutableStateOf<JSONObject?>(null) }       // the open file
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }                 // the "Work on this" box is open
    var instruction by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }

    fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    fun list(o: JSONObject?, key: String): List<JSONObject> {
        val a = o?.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    // The repo list, once (and on refresh)
    LaunchedEffect(refresh) {
        loading = true; error = null
        val r = call("GET", "/api/github/repos" + if (refresh > 0) "?refresh=true" else "", null, null)
        repos = list(r.getOrNull(), "repos")
        error = r.exceptionOrNull()?.message
        loading = false
    }
    // A folder whenever the repo or the path changes
    LaunchedEffect(repo, path) {
        val name = repo?.optString("repo") ?: return@LaunchedEffect
        loading = true; error = null; entries = emptyList()
        val r = call("GET", "/api/github/tree?repo=${enc(name)}&path=${enc(path)}", null, null)
        entries = list(r.getOrNull(), "entries")
        error = r.exceptionOrNull()?.message
        loading = false
    }

    fun openFile(p: String) {
        val name = repo?.optString("repo") ?: return
        scope.launch {
            loading = true; error = null
            val r = call("GET", "/api/github/file?repo=${enc(name)}&path=${enc(p)}", null, null)
            file = r.getOrNull()
            error = r.exceptionOrNull()?.message
            loading = false
        }
    }

    fun back() {
        when {
            working -> working = false
            file != null -> file = null
            repo != null && path.isNotEmpty() -> path = path.substringBeforeLast("/", "")
            repo != null -> { repo = null; sent = null }
            else -> onClose()
        }
    }

    fun startWork() {
        val name = repo?.optString("repo") ?: return
        val where = file?.optString("path") ?: path
        sending = true
        scope.launch {
            val body = JSONObject().put("repo", name).put("instruction", instruction.trim())
            if (where.isNotEmpty()) body.put("path", where)
            val r = call("POST", "/api/github/work", body, UUID.randomUUID().toString())
            sending = false
            r.onSuccess { o ->
                sent = "Started ${o.optString("task_id")}" +
                    (if (o.optBoolean("registered")) " (set up as project '${o.optString("project")}')" else "") +
                    ". Its card shows the progress; nothing goes live without your tap."
                working = false
                instruction = ""
            }.onFailure { error = it.message ?: "Couldn't start the work" }
        }
    }

    androidx.activity.compose.BackHandler { back() }
    Surface(modifier = Modifier.fillMaxSize(), color = DeepNavy) {
      Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Slate900, DeepNavy)))) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            // Header: back, where we are, close
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { back() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Brush.radialGradient(listOf(Cyan400.copy(alpha = 0.30f), Cyan400.copy(alpha = 0.08f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (file != null) Icons.Default.Description else Icons.Default.Folder,
                        contentDescription = null, tint = Cyan400, modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(repo?.optString("repo")?.substringAfter("/") ?: "Repos", color = Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = when {
                        file != null -> file!!.optString("path")
                        repo != null -> if (path.isEmpty()) repo!!.optString("repo") else path
                        else -> "Your GitHub repos"
                    }
                    Text(sub, color = Slate400, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                }
            }
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp).clip(DeskShapes.pill),
                    color = Cyan400,
                    trackColor = Slate800
                )
            }
            error?.let {
                StatusNote(it, color = Red400, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
            }
            sent?.let {
                StatusNote(it, color = Green400, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    // A file's text
                    file != null -> {
                        val f = file!!
                        val text = if (f.isNull("text")) null else f.optString("text")
                        if (text == null) {
                            Text(f.optString("note", "Can't show this file") + "\n" + f.optString("url"),
                                color = Slate400, modifier = Modifier.padding(16.dp))
                        } else {
                            SelectionContainer {
                                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState()).padding(12.dp)) {
                                    Text(text, color = Slate200, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    // A folder
                    repo != null -> LazyColumn(Modifier.fillMaxSize()) {
                        items(entries) { e ->
                            val isDir = e.optString("type") == "dir"
                            Row(modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 1.dp)
                                .clip(DeskShapes.control)
                                .clickable { if (isDir) path = e.optString("path") else openFile(e.optString("path")) }
                                .padding(horizontal = 8.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (isDir) Icons.Default.Folder else Icons.Default.Description, contentDescription = null,
                                    tint = if (isDir) AmberPending else Slate400, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(e.optString("name"), color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!isDir) Text(size(e.optLong("size")), color = Slate500, fontSize = 12.sp)
                            }
                        }
                    }
                    // The repo list
                    else -> Column(Modifier.fillMaxSize()) {
                        OutlinedTextField(value = filter, onValueChange = { filter = it }, singleLine = true,
                            placeholder = { Text("Filter repos") },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
                        val shown = repos.filter {
                            filter.isBlank() || it.optString("repo").contains(filter.trim(), ignoreCase = true) ||
                                it.optString("description").contains(filter.trim(), ignoreCase = true)
                        }
                        if (shown.isEmpty() && !loading) {
                            EmptyState(
                                icon = Icons.Default.Folder,
                                title = if (filter.isBlank()) "No repos to show" else "No repos match \"${filter.trim()}\"",
                                message = if (filter.isBlank()) "Tap Refresh below to load your GitHub repos." else null,
                                accent = Cyan400
                            )
                        }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(shown) { r ->
                                Column(modifier = Modifier.fillMaxWidth()
                                    .deskCard()
                                    .clickable { repo = r; path = ""; file = null; sent = null }
                                    .padding(horizontal = 14.dp, vertical = 11.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(r.optString("repo").substringAfter("/"), color = Color.White,
                                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        if (r.optBoolean("private")) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.Default.Lock, contentDescription = "Private", tint = Slate500,
                                                modifier = Modifier.size(13.dp))
                                        }
                                        Spacer(Modifier.weight(1f))
                                        if (!r.isNull("project")) {
                                            TagChip(text = "project", color = Cyan400)
                                        }
                                    }
                                    val desc = r.optString("description")
                                    Text((r.optString("repo").substringBefore("/")) + (if (desc.isNotBlank()) " · $desc" else ""),
                                        color = Slate400, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom: "Work on this" for the open repo, or refresh for the list
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Gray900, DeepNavy)))
                    .border(1.dp, Slate800, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(12.dp)
            ) {
                if (repo == null) {
                    OutlinedButton(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) { Text("Refresh") }
                } else if (!working) {
                    Button(onClick = { working = true; sent = null; error = null }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan400, contentColor = DeepNavy)) {
                        Text("Work on this", fontWeight = FontWeight.Bold)
                    }
                } else {
                    val where = file?.optString("path") ?: path
                    Text("What should change" + (if (where.isNotEmpty()) " (looking at $where)" else "") + "?",
                        color = Slate400, fontSize = 12.sp)
                    OutlinedTextField(value = instruction, onValueChange = { instruction = it },
                        placeholder = { Text("e.g. Fix the typo in the header") }, minLines = 2, maxLines = 5,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { working = false }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(onClick = { startWork() }, enabled = !sending && instruction.trim().length >= 8,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Cyan400, contentColor = DeepNavy)) {
                            Text(if (sending) "Starting…" else "Start", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
      }
    }
}

private fun size(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}
