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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.SlashCommand
import com.example.data.model.SlashCommands
import com.example.ui.components.SheetHeader
import com.example.ui.components.deskSheet
import com.example.ui.theme.AmberPending
import com.example.ui.theme.DeepCard
import com.example.ui.theme.DeepPanel
import com.example.ui.theme.DeskShapes
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.HotPink
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate800

private fun categoryColor(category: String): Color = when (category) {
    "Tasks" -> ElectricCyan
    "Videos & images" -> HotPink
    "Memory" -> EmeraldConnected
    "The agent" -> AmberPending
    else -> Slate300
}

/**
 * The slash commands by what they're for (2026-10-11, the owner: off the hub, into a pop-up of their own). Opened
 * by /?, /help or /commands in chat, or the hub's Commands button. A tap runs a command that needs nothing more, or
 * puts it in the chat box to finish ("/remember " ...).
 */
@Composable
fun SlashCommandsSheet(onDismiss: () -> Unit, onPick: (SlashCommand) -> Unit) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shown = SlashCommands.all.filter {
        it.command != "/?" && (q.isBlank() || it.usage.lowercase().contains(q) || it.description.lowercase().contains(q)
            || it.category.lowercase().contains(q))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .deskSheet()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SheetHeader(
                    title = "Commands",
                    icon = Icons.Default.Terminal,
                    subtitle = "Shortcuts for what you do most. Tap RUN to do it now, USE to finish it in the chat box",
                    onClose = onDismiss
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search commands…", color = Slate500, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Slate400, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DeepCard, unfocusedContainerColor = DeepPanel,
                        focusedBorderColor = ElectricCyan, unfocusedBorderColor = Slate800,
                        focusedTextColor = Color.White, unfocusedTextColor = Slate200
                    ),
                    shape = DeskShapes.card,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SlashCommands.categories.forEach { cat ->
                        val inCat = shown.filter { it.category == cat }
                        if (inCat.isNotEmpty()) {
                            item(key = "h-$cat") {
                                Text(cat.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                                     color = categoryColor(cat), modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                            }
                            items(inCat, key = { it.command }) { c -> CommandRow(c, categoryColor(cat)) { onPick(c) } }
                        }
                    }
                    if (shown.isEmpty()) {
                        item {
                            Text("Nothing matches that. You can always just say what you want in plain words.",
                                 fontSize = 12.sp, color = Slate400, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    item {
                        Text("You never need a command: just say what you want in plain words, and it works it out.", fontSize = 11.sp, color = Slate500,
                             modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandRow(c: SlashCommand, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DeskShapes.control)
            .background(DeepPanel)
            .border(1.dp, Slate800, DeskShapes.control)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(c.usage, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(c.description, fontSize = 12.sp, color = Slate300)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(DeskShapes.chip)
                .background(color.copy(alpha = 0.12f))
                .border(1.dp, color.copy(alpha = 0.35f), DeskShapes.chip)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(if (c.run) "RUN" else "USE", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = color)
        }
    }
}

/**
 * The pop-up above the chat box while a slash command is being typed (the owner, 2026-10-11: "I want them to pop
 * up when someone types /"). "/" shows them all by group; "/re" narrows to /remember, /restart, /retry. Gone once
 * there's a space (the command is chosen) or nothing matches.
 */
@Composable
fun SlashSuggestions(
    typed: String,
    onPick: (SlashCommand) -> Unit,
    modifier: Modifier = Modifier,
    retasks: List<com.example.data.model.Retask> = emptyList(),
    onPickRetask: (com.example.data.model.Retask) -> Unit = {}
) {
    // "/retask " then the saved retasks, narrowing as words are typed (the owner, 2026-10-11: no need to
    // remember a retask's wording, pick it for a quick run)
    if (typed.lowercase().startsWith("/retask ")) {
        val words = typed.substring(8)
        val shown = if (words.isBlank()) retasks else com.example.data.model.matchRetasks(retasks, words)
        RetaskSuggestions(shown, onPickRetask, modifier, loading = retasks.isEmpty())
        return
    }
    val q = typed.trim().lowercase()
    if (!q.startsWith("/") || typed.contains(' ') || typed.contains('\n')) return
    val matches = SlashCommands.all.filter { it.command.startsWith(q) || (q == "/" && it.command == "/?") }
    if (matches.isEmpty() || (matches.size == 1 && matches[0].command == q && matches[0].run)) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(DeskShapes.card)
            .background(DeepCard)
            .border(1.dp, ElectricCyan.copy(alpha = 0.35f), DeskShapes.card)
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).padding(vertical = 6.dp)) {
            SlashCommands.categories.forEach { cat ->
                val inCat = matches.filter { it.category == cat }
                if (inCat.isNotEmpty()) {
                    item(key = "h-$cat") {
                        Text(cat.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                             color = categoryColor(cat), modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp))
                    }
                    items(inCat, key = { "s-" + it.command }) { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onPick(c) }.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(c.usage, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                                 color = categoryColor(cat))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(c.description, fontSize = 12.sp, color = Slate300, maxLines = 1,
                                 overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun RetaskSuggestions(shown: List<com.example.data.model.Retask>, onPick: (com.example.data.model.Retask) -> Unit,
                              modifier: Modifier, loading: Boolean) {
    val violet = com.example.ui.theme.Violet300
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(DeskShapes.card)
            .background(DeepCard)
            .border(1.dp, violet.copy(alpha = 0.4f), DeskShapes.card)
    ) {
        Text("↻ RETASKS · tap one to run it", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
             color = violet, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp))
        if (shown.isEmpty()) {
            Text(if (loading) "Loading your retasks… (none saved yet if this stays empty)" else "No retask matches those words.",
                 fontSize = 12.sp, color = Slate400, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).padding(bottom = 6.dp)) {
            items(shown, key = { "r-" + it.id }) { r ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(r) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(r.goal, fontSize = 13.sp, color = Color.White, maxLines = 2,
                         overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (r.kind == "info") "reads fresh" else "${r.steps} step${if (r.steps == 1) "" else "s"}",
                         fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = violet)
                }
            }
        }
    }
}
