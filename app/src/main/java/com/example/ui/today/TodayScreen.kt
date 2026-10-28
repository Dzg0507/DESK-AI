package com.example.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.TodayEdition
import com.example.data.model.TodayItem
import com.example.data.remote.MediaAuth
import com.example.ui.components.deskCardSurface
import com.example.ui.components.glass
import com.example.ui.theme.DeskColors
import com.example.ui.theme.DeskShapes
import com.example.ui.theme.DeskSpace
import com.example.ui.theme.DeskType
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Today (2026-10-28, step 2 of docs/FEED_AND_DESIGN_PLAN_2026-10-27.md; the target is docs/design/target_today.png):
 * an edition, not an endless feed. The date and weather sit unboxed on the canvas; stories and videos are full-bleed
 * photos with the headline inside a scrim; settled loops and finished tasks are small glass tiles side by side; a
 * waiting question is answered right on its card; and it ends with "You're caught up".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    onRefresh: () -> Unit,
    onReact: (TodayItem, String) -> Unit,
    onAsk: (TodayItem) -> Unit,
    onAnswer: (TodayItem, String) -> Boolean,
    modifier: Modifier = Modifier
) {
    val page = state.page
    PullToRefreshBox(isRefreshing = state.loading && page != null, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        when {
            page == null && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DeskColors.Emerald, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
            page == null -> Notice(
                title = "Couldn't reach the PC",
                body = state.error ?: "Today comes from the agent on your PC.",
                action = "Try again", onAction = onRefresh
            )
            page.edition == null -> Notice(
                title = "Nothing yet",
                body = "Today fills in as the agent finds things for you while you're away: news on what you follow, " +
                    "what got settled, what finished.",
                action = "Check now", onAction = onRefresh
            )
            else -> Edition(state, page.edition, page.items, onReact, onAsk, onAnswer)
        }
    }
}

@Composable
private fun Edition(
    state: TodayUiState,
    edition: TodayEdition,
    items: List<TodayItem>,
    onReact: (TodayItem, String) -> Unit,
    onAsk: (TodayItem) -> Unit,
    onAnswer: (TodayItem, String) -> Boolean
) {
    val base = state.config?.getResolvedUrl() ?: ""
    val token = state.config?.apiKey ?: ""
    val big = items.filter { it.card == "story" || it.card == "video" }
    val tiles = items.filter { it.card == "settled" || it.card == "done" }
    val questions = items.filter { it.card == "question" }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = DeskSpace.l, end = DeskSpace.l, top = DeskSpace.s, bottom = DeskSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(DeskSpace.l)
    ) {
        item(key = "header") { Header(edition, items.size) }
        items(big, key = { it.id }) { item -> BigCard(item, base, token, onReact, onAsk) }
        items(tiles.chunked(2), key = { row -> "tiles-" + row.joinToString { it.id.toString() } }) { row ->
            // Side by side tiles are the same height, the taller one's
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DeskSpace.m)) {
                row.forEach { Tile(it, onReact, onAsk, Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        items(questions, key = { it.id }) { q -> QuestionCard(q, q.id in state.answered || q.state == "answered", onReact, onAnswer) }
        item(key = "caught-up") { CaughtUp(edition) }
    }
}

@Composable
private fun Header(edition: TodayEdition, shown: Int) {
    Column(Modifier.fillMaxWidth().padding(top = DeskSpace.s)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(longDate(edition.date), style = DeskType.display, color = DeskColors.TextPrimary, modifier = Modifier.weight(1f))
            edition.weather?.let { w ->
                Text("${w.tempF}°F ${w.summary}".trim(), style = DeskType.titleSmall, color = DeskColors.TextSecondary)
            }
        }
        Spacer(Modifier.height(DeskSpace.xs))
        Text(if (shown == 1) "1 thing today" else "$shown things today", style = DeskType.mono, color = DeskColors.TextMuted)
    }
}

/** A story or video: full-bleed with its picture; a designed text card without one (none, broken, or too small). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BigCard(item: TodayItem, base: String, token: String, onReact: (TodayItem, String) -> Unit, onAsk: (TodayItem) -> Unit) {
    val uri = LocalUriHandler.current
    var menu by remember { mutableStateOf(false) }
    val open = { if (item.link.isNotBlank()) runCatching { uri.openUri(item.link) } }
    val headline = item.title.ifBlank { item.body }
    Box {
        if (item.imageUrl != null) {
            val context = LocalContext.current
            val request = remember(item.imageUrl, token) {
                ImageRequest.Builder(context).data(item.imageUrl).crossfade(true).apply {
                    if (token.isNotBlank() && MediaAuth.isOwnServer(item.imageUrl, base)) addHeader("X-HUD-Token", token.trim())
                }.build()
            }
            Box(
                Modifier.fillMaxWidth().aspectRatio(if (item.card == "video") 16f / 10f else 4f / 3.4f).clip(DeskShapes.media)
                    .background(DeskColors.Raised).combinedClickable(onClick = { open() }, onLongClick = { menu = true })
            ) {
                AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color.Transparent,
                    1f to DeskColors.Scrim)))
                if (item.card == "video") {
                    Box(Modifier.align(Alignment.Center).size(56.dp).glass(CircleShape, DeskColors.GlassStrong),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = DeskColors.TextPrimary, modifier = Modifier.size(28.dp))
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).padding(DeskSpace.l)) {
                    Text(headline, style = DeskType.headline, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(DeskSpace.s))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (item.site.isNotBlank()) Text(item.site, style = DeskType.label, color = DeskColors.TextPrimary, maxLines = 1)
                            if (item.why.isNotBlank()) Text(item.why, style = DeskType.mono, color = DeskColors.TextSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(DeskSpace.s))
                        CardActions(item, onReact, onAsk)
                    }
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().deskCardSurface(DeskShapes.media)
                    .combinedClickable(onClick = { open() }, onLongClick = { menu = true }).padding(DeskSpace.l)
            ) {
                if (item.site.isNotBlank()) {
                    Text(item.site, style = DeskType.caption, color = DeskColors.TextSecondary,
                        modifier = Modifier.glass().padding(horizontal = 10.dp, vertical = 4.dp))
                    Spacer(Modifier.height(DeskSpace.m))
                }
                Text(headline, style = DeskType.headline, color = DeskColors.TextPrimary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (item.title.isNotBlank() && item.body.isNotBlank()) {
                    Spacer(Modifier.height(DeskSpace.s))
                    Text(item.body, style = DeskType.bodySmall, color = DeskColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(DeskSpace.m))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.why, style = DeskType.mono, color = DeskColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(DeskSpace.s))
                    CardActions(item, onReact, onAsk)
                }
            }
        }
        CardMenu(item, menu, { menu = false }, onReact)
    }
}

@Composable
private fun CardActions(item: TodayItem, onReact: (TodayItem, String) -> Unit, onAsk: (TodayItem) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(DeskSpace.s)) {
        GlassPill("Ask") { onAsk(item) }
        GlassPill(if (item.saved) "Saved" else "Save", selected = item.saved) { onReact(item, if (item.saved) "unsave" else "save") }
    }
}

/** Long-press: why it's here, and steering what comes next. */
@Composable
private fun CardMenu(item: TodayItem, open: Boolean, onClose: () -> Unit, onReact: (TodayItem, String) -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onClose, containerColor = DeskColors.Raised2) {
        if (item.why.isNotBlank()) {
            DropdownMenuItem(text = { Text("Why: ${item.why}", style = DeskType.bodySmall, color = DeskColors.TextSecondary) },
                onClick = onClose, enabled = false)
        }
        listOf("more" to "More like this", "less" to "Less like this", "dismiss" to "Hide this card").forEach { (action, label) ->
            DropdownMenuItem(text = { Text(label, style = DeskType.body, color = DeskColors.TextPrimary) },
                onClick = { onReact(item, action); onClose() })
        }
    }
}

/** Settled and Done: a square-ish glass tile, two to a row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(item: TodayItem, onReact: (TodayItem, String) -> Unit, onAsk: (TodayItem) -> Unit, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier.fillMaxSize().heightIn(min = 168.dp).glass(DeskShapes.card)
                .combinedClickable(onClick = {}, onLongClick = { menu = true }).padding(DeskSpace.m + DeskSpace.xxs)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (item.card == "done") DeskColors.Signal else DeskColors.Agent))
                Spacer(Modifier.width(DeskSpace.s))
                Text(item.title, style = DeskType.titleSmall, color = DeskColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(DeskSpace.s))
            val lines = if (item.card == "done") item.body.lines().filter { it.isNotBlank() }.joinToString("\n") { "· $it" } else item.body
            Text(lines, style = DeskType.bodySmall, color = DeskColors.TextSecondary, maxLines = 8, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))                       // the action sits at the bottom of the taller tile too
            Spacer(Modifier.height(DeskSpace.m))
            GlassPill("Ask about this", modifier = Modifier.fillMaxWidth()) { onAsk(item) }
        }
        CardMenu(item, menu, { menu = false }, onReact)
    }
}

/** A curious question from the agent, answered right here; the answer goes to the chat like any message. */
@Composable
private fun QuestionCard(item: TodayItem, answered: Boolean, onReact: (TodayItem, String) -> Unit, onAnswer: (TodayItem, String) -> Boolean) {
    var text by rememberSaveable(item.id) { mutableStateOf("") }
    val send = { if (text.isNotBlank() && onAnswer(item, text.trim())) text = "" }
    Column(Modifier.fillMaxWidth().deskCardSurface(DeskShapes.media).padding(DeskSpace.l)) {
        Text("A QUESTION FOR YOU", style = DeskType.overline, color = DeskColors.Agent)
        Spacer(Modifier.height(DeskSpace.s))
        Text(item.body, style = DeskType.headline, color = DeskColors.TextPrimary)
        Spacer(Modifier.height(DeskSpace.m))
        if (answered) {
            Text("Sent. The reply is in Chat.", style = DeskType.bodySmall, color = DeskColors.Emerald)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = text, onValueChange = { text = it },
                    textStyle = DeskType.body.copy(color = DeskColors.TextPrimary),
                    cursorBrush = SolidColor(DeskColors.Emerald),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.weight(1f).glass(DeskShapes.card).padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text("Answer…", style = DeskType.body, color = DeskColors.TextMuted)
                        inner()
                    }
                )
                Spacer(Modifier.width(DeskSpace.s))
                Box(Modifier.size(44.dp).glass(CircleShape, DeskColors.GlassStrong).clickable { send() },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send answer", tint = DeskColors.TextPrimary,
                        modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(DeskSpace.s))
            Text("Not now", style = DeskType.caption, color = DeskColors.TextMuted,
                modifier = Modifier.clickable { onReact(item, "dismiss") }.padding(vertical = DeskSpace.xs))
        }
    }
}

@Composable
private fun CaughtUp(edition: TodayEdition) {
    Column(Modifier.fillMaxWidth().padding(top = DeskSpace.l, bottom = DeskSpace.s), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(DeskColors.Signal))
        Spacer(Modifier.height(DeskSpace.s))
        Text("You're caught up", style = DeskType.title, color = DeskColors.TextPrimary)
        edition.nextAfter?.let { at ->
            Spacer(Modifier.height(DeskSpace.xs))
            Text("More after ${clock(at)}, once there's something new", style = DeskType.mono, color = DeskColors.TextMuted,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Notice(title: String, body: String, action: String, onAction: () -> Unit) {
    // Scrollable, so pull-to-refresh works on it too
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(DeskSpace.xxl), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Text(title, style = DeskType.headline, color = DeskColors.TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(DeskSpace.s))
            Text(body, style = DeskType.body, color = DeskColors.TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(DeskSpace.l))
            GlassPill(action, onClick = onAction)
        }
    }
}

@Composable
fun GlassPill(label: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        label, style = DeskType.label, textAlign = TextAlign.Center,
        color = if (selected) DeskColors.Emerald else DeskColors.TextPrimary,
        modifier = modifier.glass(fill = DeskColors.GlassStrong).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

/** The frosted Chat | Today switch at the top of the screen. */
@Composable
fun ChatTodaySwitch(showingToday: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Row(modifier.glass().padding(DeskSpace.xs)) {
        listOf(false to "Chat", true to "Today").forEach { (today, label) ->
            val on = today == showingToday
            Text(
                label, style = DeskType.label, textAlign = TextAlign.Center,
                color = if (on) DeskColors.TextPrimary else DeskColors.TextSecondary,
                modifier = Modifier.width(84.dp)
                    .then(if (on) Modifier.glass(fill = DeskColors.GlassStrong) else Modifier.clip(DeskShapes.pill))
                    .clickable {
                        if (!on) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onChange(today)
                        }
                    }
                    .padding(vertical = 7.dp)
            )
        }
    }
}

private fun longDate(isoDate: String): String = try {
    val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)
    if (d == null) isoDate else SimpleDateFormat("EEEE, MMM d", Locale.US).format(d)
} catch (_: Exception) {
    isoDate
}

/** "2026-10-28T07:57:08-05:00" -> "7:57 AM" in the phone's time zone. */
internal fun clock(iso: String): String = try {
    val trimmed = iso.replace(Regex("""\.\d+"""), "").replace(Regex("""([+-]\d\d):(\d\d)$"""), "$1$2")
    val d = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(trimmed)
    if (d == null) "" else SimpleDateFormat("h:mm a", Locale.US).format(d)
} catch (_: Exception) {
    ""
}
