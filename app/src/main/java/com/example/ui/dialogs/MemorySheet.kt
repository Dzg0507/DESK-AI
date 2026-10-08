package com.example.ui.dialogs

import com.example.ui.components.SectionLabel
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import com.example.ui.components.TagChip
import com.example.ui.components.deskTinted
import com.example.ui.components.tagColor
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.AmberPending
import com.example.ui.components.SheetHeader
import com.example.ui.theme.DeskShapes
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.MemoryFactItem
import com.example.data.model.MemoryOverview
import com.example.data.model.RecipeItem
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.RoseError
import kotlinx.coroutines.launch
import com.example.ui.theme.ElectricCyanGlow
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.components.deskSheet
import com.example.ui.components.deskCard

@Composable
fun MemorySheet(
    onDismiss: () -> Unit,
    onLoad: suspend () -> MemoryOverview?,
    onAddFact: suspend (String) -> Result<Int>,
    onDeleteFact: suspend (Int) -> Result<Boolean>,
    onUpdateRecipe: suspend (Int, Boolean?, String?) -> Result<Boolean> = { _, _, _ -> Result.success(false) },
    onDeleteRecipe: suspend (Int) -> Result<Boolean> = { Result.success(false) }
) {
    val scope = rememberCoroutineScope()
    var newFactText by remember { mutableStateOf("") }
    // The agent's real memories. This sheet used to start from four hard-coded sample facts with ids 1-4 and
    // never load the real ones, so it always showed the same four, and "forget" on a sample erased the real
    // fact with that id (2026-10-04).
    var facts by remember { mutableStateOf(emptyList<MemoryFactItem>()) }
    var profile by remember { mutableStateOf("") }
    var recipes by remember { mutableStateOf(emptyList<RecipeItem>()) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }

    suspend fun reload() {
        val overview = onLoad()
        if (overview != null) {
            facts = overview.facts
            profile = overview.profile
            recipes = overview.recipes
        }
        loadFailed = overview == null
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .deskSheet()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                SheetHeader(
                    title = "AlwaysOnAgent Memory",
                    icon = Icons.Default.Memory,
                    subtitle = "What the agent remembers about you",
                    onClose = onDismiss
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Add New Fact Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newFactText,
                        onValueChange = { newFactText = it },
                        placeholder = { Text("Remember something (e.g. My preferred editor is VS Code)...", color = Slate500, fontSize = 12.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Slate800,
                            unfocusedContainerColor = Slate800,
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = Slate700
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(DeskShapes.control)
                            .background(Brush.linearGradient(listOf(ElectricCyan, ElectricCyanGlow)))
                            .clickable {
                                if (newFactText.isNotBlank()) {
                                    scope.launch {
                                        if (onAddFact(newFactText).isSuccess) {
                                            newFactText = ""
                                            reload()        // the saved fact, with its real id, from the agent
                                        } else {
                                            loadFailed = true
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // The profile, in full: the agent writes it as a few short paragraphs about the owner and
                    // sends it with every message. It scrolls with the facts so nothing is cut off.
                    if (profile.isNotBlank()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .deskTinted(ElectricCyan, fillAlpha = 0.06f)
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "🧠 PROFILE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = profile,
                                    fontSize = 13.sp,
                                    color = Slate200,
                                    lineHeight = 19.sp
                                )
                            }
                        }
                    }
                    // Learned recipes ("when X, do Y"): how the agent does a kind of job, with where each came
                    // from and its track record. Pin keeps one in use for good; retired ones kept failing.
                    if (recipes.isNotEmpty()) {
                        item {
                            SectionLabel(
                                text = "📘 Recipes",
                                count = recipes.count { !it.retired },
                                color = NeonPurple,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        items(recipes, key = { "recipe-${it.id}" }) { recipe ->
                            RecipeCard(
                                recipe = recipe,
                                onTogglePin = {
                                    scope.launch {
                                        if (onUpdateRecipe(recipe.id, !recipe.pinned, null).isSuccess) {
                                            recipes = recipes.map { if (it.id == recipe.id) it.copy(pinned = !it.pinned) else it }
                                        } else loadFailed = true
                                    }
                                },
                                onRestore = {
                                    scope.launch {
                                        if (onUpdateRecipe(recipe.id, null, "active").isSuccess) {
                                            recipes = recipes.map { if (it.id == recipe.id) it.copy(status = "active") else it }
                                        } else loadFailed = true
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        if (onDeleteRecipe(recipe.id).isSuccess) {
                                            recipes = recipes.filter { it.id != recipe.id }
                                        } else loadFailed = true
                                    }
                                }
                            )
                        }
                    }
                    item {
                        Text(
                            text = when {
                                loading -> "LOADING MEMORIES…"
                                loadFailed -> "COULDN'T REACH THE AGENT — SHOWING WHAT WAS LOADED (${facts.size})"
                                else -> "STORED FACTS (${facts.size})"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (loadFailed) AmberPending else Slate400,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    if (facts.isEmpty() && profile.isBlank() && recipes.isEmpty()) {
                        item {
                            if (loading) {
                                LoadingState("Loading memories…")
                            } else {
                                EmptyState(
                                    icon = Icons.Default.Memory,
                                    title = if (loadFailed) "Couldn't reach the agent" else "Nothing remembered yet",
                                    message = if (loadFailed) "Check the connection and open this again."
                                    else "Add a fact above, or tell the agent in chat to remember something.",
                                    accent = if (loadFailed) AmberPending else ElectricCyan
                                )
                            }
                        }
                    }
                    items(facts) { fact ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .deskCard()
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (fact.pinned) {
                                    Text(text = "📌", fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp))
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 2.dp, end = 10.dp)
                                            .size(6.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(tagColor(fact.category))
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fact.content,
                                        fontSize = 13.sp,
                                        color = Slate100,
                                        lineHeight = 18.sp
                                    )
                                    fact.origin()?.let { origin ->
                                        Text(
                                            text = origin,
                                            fontSize = 11.sp,
                                            color = Slate400,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            lineHeight = 15.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.padding(top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (fact.category.isNotBlank()) {
                                            TagChip(text = fact.category, color = tagColor(fact.category))
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = "#${fact.id}",
                                            fontSize = 10.sp,
                                            color = Slate500,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            if (onDeleteFact(fact.id).isSuccess) {
                                                facts = facts.filter { it.id != fact.id }
                                            } else {
                                                loadFailed = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Forget",
                                        tint = RoseError.copy(alpha = 0.7f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(recipe: RecipeItem, onTogglePin: () -> Unit, onRestore: () -> Unit, onDelete: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .deskCard(borderColor = if (recipe.pinned) ElectricCyan.copy(alpha = 0.5f) else Slate700)
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "When ${recipe.whenText.trimEnd('.')}:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (recipe.retired) Slate500 else ElectricCyan
                )
                Text(
                    text = recipe.doText,
                    fontSize = 12.sp,
                    color = if (recipe.retired) Slate500 else Slate100
                )
                Text(
                    text = recipe.origin(),
                    fontSize = 11.sp,
                    color = Slate400,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = "${recipe.area} • ${recipe.track()}",
                    fontSize = 10.sp,
                    color = Slate500,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (recipe.retired) {
                Text(
                    text = "↺",
                    fontSize = 15.sp,
                    color = Slate400,
                    modifier = Modifier.clickable(onClick = onRestore).padding(6.dp)
                )
            } else {
                Text(
                    text = "📌",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = if (recipe.pinned) 1f else 0.35f),
                    modifier = Modifier.clickable(onClick = onTogglePin).padding(6.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete recipe",
                    tint = RoseError.copy(alpha = 0.7f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
