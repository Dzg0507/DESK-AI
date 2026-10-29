package com.example.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.TodayEdition
import com.example.data.model.TodayHeader
import com.example.data.model.TodayItem
import com.example.data.model.TodayPage
import com.example.data.model.TodayRecipe
import com.example.data.remote.MediaAuth
import com.example.ui.components.glass
import com.example.ui.components.todayGlassCard
import com.example.ui.theme.DeskColors
import com.example.ui.theme.DeskShapes
import com.example.ui.theme.DeskSpace
import com.example.ui.theme.DeskType
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Today: an edition of cards over the dynamic weather canvas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    onRefresh: () -> Unit,
    onReact: (TodayItem, String) -> Unit,
    onAsk: (TodayItem) -> Unit,
    onAnswer: (TodayItem, String) -> Boolean,
    onSaveRecipe: (String, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onPlayEdition: (String) -> Unit = {}
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
            else -> Edition(state, page, onReact, onAsk, onAnswer, onSaveRecipe, onRefresh, listState, onPlayEdition)
        }
    }
}

@Composable
private fun Edition(
    state: TodayUiState,
    page: TodayPage,
    onReact: (TodayItem, String) -> Unit,
    onAsk: (TodayItem) -> Unit,
    onAnswer: (TodayItem, String) -> Boolean,
    onSaveRecipe: (String, () -> Unit) -> Unit,
    onRefresh: () -> Unit,
    listState: LazyListState,
    onPlayEdition: (String) -> Unit
) {
    val base = state.config?.getResolvedUrl() ?: ""
    val token = state.config?.apiKey ?: ""
    val items = page.items
    val big = items.filter { it.card == "story" || it.card == "video" }
    // Strict Grid Rule: Only pair compact 2-3 line cards in a 2-column Row.
    // Any card with a list ("done") or multi-line list is rendered full width.
    val compactTiles = items.filter { it.card == "settled" && it.body.lines().count { l -> l.isNotBlank() } <= 3 }
    val fullWidthTiles = items.filter {
        it.card == "done" || (it.card == "settled" && it.body.lines().count { l -> l.isNotBlank() } > 3)
    }
    val questions = items.filter { it.card == "question" }
    var playing by rememberSaveable { mutableStateOf<Long?>(null) }     // the video card playing in place (one at a time)

    val spokenSummary = remember(page) {
        buildString {
            append(longDate(page.header.date)).append(". ")
            page.header.weather?.let { append("${it.tempF} degrees, ${it.summary}. ") }
            page.header.brief?.let { append(forSpeech(it)).append(". ") }
            items.take(4).forEach { item ->
                val headline = item.title.ifBlank { item.body }
                if (headline.isNotBlank()) append(headline).append(". ")
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = DeskSpace.l, end = DeskSpace.l, top = DeskSpace.s, bottom = DeskSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(DeskSpace.m)
    ) {
        item(key = "recipe") { RecipeBar(page.recipe, state.savingRecipe, state.recipeError, onSaveRecipe) }
        item(key = "header") {
            Header(
                header = page.header,
                shown = items.size,
                onPlayEdition = { onPlayEdition(spokenSummary) }
            )
        }
        if (page.edition == null || items.isEmpty()) {
            item(key = "nothing") {
                Column(Modifier.fillMaxWidth().padding(top = DeskSpace.l), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (page.edition == null) "Nothing else yet. Today fills in as the agent finds things for you " +
                            "while you're away: news on what you follow, what got settled, what finished."
                        else "Nothing here fits your recipe right now.",
                        style = DeskType.body, color = DeskColors.TextSecondary, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(DeskSpace.l))
                    GlassPill("Check now", onClick = onRefresh)
                }
            }
            return@LazyColumn
        }
        items(big, key = { it.id }) { item ->
            BigCard(item, base, token, onReact, onAsk, playing == item.id, { playing = it?.let { item.id } })
        }
        // Pair compact 2-3 line cards in a 2-column Row; an unpaired odd card takes full width (never 0.5f orphan)
        items(compactTiles.chunked(2), key = { row -> "tiles-" + row.joinToString { it.id.toString() } }) { row ->
            if (row.size == 2) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(DeskSpace.m)
                ) {
                    row.forEach { Tile(it, onReact, onAsk, Modifier.weight(1f).fillMaxHeight()) }
                }
            } else {
                val single = row.first()
                Tile(single, onReact, onAsk, Modifier.fillMaxWidth())
            }
        }
        // Multi-line list cards (Done while you were away) always render at full width
        items(fullWidthTiles, key = { "done-${it.id}" }) { item ->
            if (item.card == "done") {
                DoneCard(item = item, onReact = onReact, onAsk = onAsk)
            } else {
                Tile(item, onReact, onAsk, Modifier.fillMaxWidth())
            }
        }
        items(questions, key = { it.id }) { q -> QuestionCard(q, q.id in state.answered || q.state == "answered", onReact, onAnswer) }
        page.edition?.let { ed -> item(key = "caught-up") { CaughtUp(ed) } }
    }
}

/** The date, weather, Play edition pill, count, and the 2-column Bento brief row. */
@Composable
private fun Header(header: TodayHeader, shown: Int, onPlayEdition: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = longDate(header.date),
                style = DeskType.display,
                color = DeskColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            header.weather?.let { w ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    skyIcon(skyFor(w))?.let { icon ->
                        Icon(icon, contentDescription = null, tint = DeskColors.TextPrimary.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(DeskSpace.xs + DeskSpace.xxs))
                    }
                    Text(
                        text = "${w.tempF}°F ${w.summary}",
                        style = DeskType.titleSmall,
                        color = DeskColors.TextPrimary.copy(alpha = 0.92f)
                    )
                }
            }
        }
        Spacer(Modifier.height(DeskSpace.s))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DeskSpace.m)
        ) {
            PlayEditionPill(onClick = onPlayEdition)
            Text(
                text = if (shown == 1) "1 thing today" else "$shown things today",
                style = DeskType.mono,
                color = DeskColors.TextMuted
            )
        }
        // The brief as MAIL and UP NEXT tiles, from the facts it was written from. Its text is never shown here
        // (Devin, 2026-10-28: "I never want to see the raw emoji brief text on the Today screen again"); Play
        // edition still reads it out.
        briefBento(header)?.let { bento ->
            Spacer(Modifier.height(DeskSpace.m))
            BriefBentoRow(bento = bento)
        }
    }
}

/** Compact frosted-glass Play edition pill directly under the date. */
@Composable
private fun PlayEditionPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(DeskShapes.pill)
            .background(
                Brush.verticalGradient(
                    listOf(DeskColors.SegmentActiveTop, DeskColors.SegmentActiveBottom)
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(DeskColors.SegmentActiveRim, DeskColors.GlassBorder)
                ),
                shape = DeskShapes.pill
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = DeskColors.TextPrimary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Play edition",
            style = DeskType.label.copy(fontWeight = FontWeight.SemiBold),
            color = DeskColors.TextPrimary
        )
    }
}

internal data class BriefBento(
    val mailBody: String,
    val mailBadge: String?,
    val upNextItems: List<String>
)

private val MONEY = Regex("""\$\d[\d,]*(?:\.\d{2})?""")
private val EMOJI = Regex("""[\uD83C-\uDBFF][\uDC00-\uDFFF]|[☀-➿]|️""")

/** The brief for the phone's voice (Play edition): no emoji, which it would read out by name. */
internal fun forSpeech(text: String): String =
    text.replace(EMOJI, " ").lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString(". ")

/**
 * The MAIL and UP NEXT tiles, from the facts the brief was written from (the agent sends them with it): the senders,
 * any amounts in their subjects, and what's still ahead today. Null when there's neither, and then the brief's own
 * text shows instead. Never filled in from anything else: reading the AI's wording once showed made-up senders.
 */
internal fun briefBento(header: TodayHeader): BriefBento? {
    if (header.mail.isEmpty() && header.upNext.isEmpty()) return null
    val senders = header.mail.map { it.first.trim() }.filter { it.isNotBlank() }.distinct()
    val amounts = header.mail.flatMap { (_, subject) -> MONEY.findAll(subject).map { it.value } }.distinct()
    return BriefBento(
        mailBody = senders.joinToString(", ").ifBlank { "No important mail" },
        mailBadge = amounts.takeIf { it.isNotEmpty() }?.joinToString(" · "),
        upNextItems = header.upNext.map { (time, what) -> "$time · $what" }
    )
}

/**
 * 2-column side-by-side Bento row for MAIL and UP NEXT.
 */
@Composable
private fun BriefBentoRow(bento: BriefBento) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(DeskSpace.m)
    ) {
        // Left Bento Tile: MAIL
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .todayGlassCard()
                .padding(DeskSpace.m + DeskSpace.xxs)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    tint = DeskColors.BentoLabel,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "MAIL",
                    style = DeskType.monoSmall,
                    color = DeskColors.BentoLabel
                )
            }
            Spacer(Modifier.height(DeskSpace.s))
            Text(
                text = bento.mailBody,
                style = DeskType.bodySmall,
                color = DeskColors.TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (!bento.mailBadge.isNullOrBlank()) {
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(DeskSpace.s))
                Box(
                    modifier = Modifier
                        .clip(DeskShapes.small)
                        .background(DeskColors.Emerald.copy(alpha = 0.16f))
                        .border(1.dp, DeskColors.Emerald.copy(alpha = 0.38f), DeskShapes.small)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = bento.mailBadge,
                        style = DeskType.monoSmall,
                        color = DeskColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Right Bento Tile: UP NEXT
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .todayGlassCard()
                .padding(DeskSpace.m + DeskSpace.xxs)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = DeskColors.BentoLabel,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "UP NEXT",
                    style = DeskType.monoSmall,
                    color = DeskColors.BentoLabel
                )
            }
            Spacer(Modifier.height(DeskSpace.s))
            Column(verticalArrangement = Arrangement.spacedBy(DeskSpace.xs)) {
                bento.upNextItems.ifEmpty { listOf("Nothing else scheduled today") }.forEach { itemText ->
                    Text(
                        text = itemText,
                        style = DeskType.bodySmall,
                        color = DeskColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * The recipe: Devin's own words for what Today shows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeBar(recipe: TodayRecipe, saving: Boolean, error: String?, onSave: (String, () -> Unit) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable(recipe.text) { mutableStateOf(recipe.text) }
    val chips = recipeChips(recipe)
    if (!open) {
        Row(
            Modifier.fillMaxWidth().glass().clickable { open = true }.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Edit, contentDescription = null, tint = DeskColors.TextSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(DeskSpace.s))
            Text(
                if (chips.isEmpty()) "Tell Today what you want to see" else "Edit recipe · " + chips.joinToString(", "),
                style = DeskType.label, color = DeskColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        return
    }
    Column(Modifier.fillMaxWidth().todayGlassCard().padding(DeskSpace.l)) {
        Text("YOUR RECIPE", style = DeskType.overline, color = DeskColors.Emerald)
        Spacer(Modifier.height(DeskSpace.s))
        BasicTextField(
            value = text, onValueChange = { text = it.take(1500) },
            textStyle = DeskType.body.copy(color = DeskColors.TextPrimary),
            cursorBrush = SolidColor(DeskColors.Emerald),
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).glass(DeskShapes.small).padding(DeskSpace.m),
            decorationBox = { inner ->
                if (text.isEmpty()) Text(
                    "More Arkansas outdoors and golf, no crypto. Weather for Jonesboro, AR. " +
                        "8 cards max. Hide finished tasks.",
                    style = DeskType.body, color = DeskColors.TextMuted
                )
                inner()
            }
        )
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(DeskSpace.m))
            Text("What the agent took from it", style = DeskType.caption, color = DeskColors.TextMuted)
            Spacer(Modifier.height(DeskSpace.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(DeskSpace.s), verticalArrangement = Arrangement.spacedBy(DeskSpace.s)) {
                chips.forEach {
                    Text(
                        it, style = DeskType.mono, color = DeskColors.TextPrimary,
                        modifier = Modifier.glass().padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
        error?.let {
            Spacer(Modifier.height(DeskSpace.s))
            Text("Not saved: $it", style = DeskType.bodySmall, color = DeskColors.Error)
        }
        Spacer(Modifier.height(DeskSpace.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "You can also just tell the agent in Chat.", style = DeskType.caption, color = DeskColors.TextMuted,
                modifier = Modifier.weight(1f)
            )
            GlassPill("Close") { open = false; text = recipe.text }
            Spacer(Modifier.width(DeskSpace.s))
            if (saving) {
                CircularProgressIndicator(color = DeskColors.Emerald, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                GlassPill("Save", selected = true) { onSave(text.trim()) { open = false } }
            }
        }
    }
}

/** The compiled rules as short chips: ↑ more, ↓ less, ✕ never, ▶ channels, the limit, hidden kinds, the town. */
internal fun recipeChips(r: TodayRecipe): List<String> =
    r.more.map { "↑ $it" } + r.less.map { "↓ $it" } + r.never.map { "✕ $it" } + r.channels.map { "▶ $it" } +
        listOfNotNull(r.maxCards?.let { "≤ $it cards" }) +
        r.hidden.map {
            "hide " + mapOf(
                "story" to "stories", "video" to "videos", "settled" to "settled",
                "done" to "finished", "question" to "questions"
            ).getValue(it)
        } +
        listOfNotNull(r.location.takeIf { it.isNotBlank() }?.let { "☀ $it" })

/** A story or video: full-bleed with its picture; a designed text card without one (none, broken, or too small). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BigCard(
    item: TodayItem, base: String, token: String, onReact: (TodayItem, String) -> Unit, onAsk: (TodayItem) -> Unit,
    playing: Boolean = false, onPlaying: (Boolean?) -> Unit = {}
) {
    val uri = LocalUriHandler.current
    var menu by remember { mutableStateOf(false) }
    val videoId = remember(item.link) { if (item.card == "video") youTubeId(item.link) else null }
    val openLink = { if (item.link.isNotBlank()) runCatching { uri.openUri(item.link) } }
    // A video plays in its own card; anything else (or a video YouTube won't let play here) opens its link
    val open = { if (videoId != null) onPlaying(true) else openLink() }
    val headline = item.title.ifBlank { item.body }
    Box(Modifier.fillMaxWidth()) {
        if (playing && videoId != null) {
            Column(Modifier.fillMaxWidth().todayGlassCard(DeskShapes.media)) {
                InlineYouTube(
                    videoId, Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    onCantPlay = { onPlaying(null); openLink() }
                )
                Row(
                    Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { menu = true }).padding(DeskSpace.l),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(headline, style = DeskType.label, color = DeskColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (item.site.isNotBlank()) Text(item.site, style = DeskType.mono, color = DeskColors.TextSecondary, maxLines = 1)
                    }
                    Spacer(Modifier.width(DeskSpace.s))
                    GlassPill("Close") { onPlaying(null) }
                }
            }
        } else if (item.imageUrl != null) {
            val context = LocalContext.current
            val request = remember(item.imageUrl, token) {
                ImageRequest.Builder(context).data(item.imageUrl).crossfade(true).apply {
                    if (token.isNotBlank() && MediaAuth.isOwnServer(item.imageUrl, base)) addHeader("X-HUD-Token", token.trim())
                }.build()
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)                    // full-bleed photo, cropped, 24 dp corners (DeskShapes.media)
                    .todayGlassCard(DeskShapes.media)
                    .combinedClickable(onClick = { open() }, onLongClick = { menu = true })
            ) {
                AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.38f to Color.Transparent,
                            1f to DeskColors.Scrim
                        )
                    )
                )
                if (item.card == "video") {
                    Box(
                        Modifier.align(Alignment.Center).size(56.dp).glass(CircleShape, DeskColors.GlassStrong),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = DeskColors.TextPrimary, modifier = Modifier.size(28.dp))
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).padding(DeskSpace.l)) {
                    Text(headline, style = DeskType.headline, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(DeskSpace.s))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (item.site.isNotBlank()) Text(item.site, style = DeskType.label, color = DeskColors.TextPrimary, maxLines = 1)
                            if (item.why.isNotBlank()) Text(
                                item.why, style = DeskType.mono, color = DeskColors.TextSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(DeskSpace.s))
                        CardActions(item, onReact, onAsk)
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .todayGlassCard()
                    .combinedClickable(onClick = { open() }, onLongClick = { menu = true })
                    .padding(DeskSpace.l)
            ) {
                if (item.site.isNotBlank()) {
                    Text(
                        item.site, style = DeskType.caption, color = DeskColors.TextSecondary,
                        modifier = Modifier.glass().padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.height(DeskSpace.m))
                }
                Text(headline, style = DeskType.headline, color = DeskColors.TextPrimary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (item.title.isNotBlank() && item.body.isNotBlank()) {
                    Spacer(Modifier.height(DeskSpace.s))
                    Text(item.body, style = DeskType.bodySmall, color = DeskColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(DeskSpace.m))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.why, style = DeskType.mono, color = DeskColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
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
            DropdownMenuItem(
                text = { Text("Why: ${item.why}", style = DeskType.bodySmall, color = DeskColors.TextSecondary) },
                onClick = onClose, enabled = false
            )
        }
        listOf("more" to "More like this", "less" to "Less like this", "dismiss" to "Hide this card").forEach { (action, label) ->
            DropdownMenuItem(
                text = { Text(label, style = DeskType.body, color = DeskColors.TextPrimary) },
                onClick = { onReact(item, action); onClose() }
            )
        }
    }
}

/** Compact 2-3 line Settled card: pairs in a 2-column row or spans full width when unpaired. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(item: TodayItem, onReact: (TodayItem, String) -> Unit, onAsk: (TodayItem) -> Unit, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .todayGlassCard()
                .combinedClickable(onClick = {}, onLongClick = { menu = true })
                .padding(DeskSpace.l)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(DeskColors.Agent))
                Spacer(Modifier.width(DeskSpace.s))
                Text(
                    item.title,
                    style = DeskType.titleSmall,
                    color = DeskColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(DeskSpace.s))
            Text(
                item.body,
                style = DeskType.bodySmall,
                color = DeskColors.TextSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(DeskSpace.m))
            GlassPill("Ask about this", modifier = Modifier.fillMaxWidth()) { onAsk(item) }
        }
        CardMenu(item, menu, { menu = false }, onReact)
    }
}

/**
 * Deduplicates consecutive/repeated tasks in "Done while you were away" and returns (taskTitle, count) pairs.
 */
internal fun deduplicateDoneTasks(rawBody: String): List<Pair<String, Int>> {
    val countSuffix = Regex("""^(.*?)\s*[×x](\d+)$""")
    val counts = LinkedHashMap<String, Int>()
    rawBody.lines()
        .map { it.trim().removePrefix("·").removePrefix("•").removePrefix("-").trim() }
        .filter { it.isNotBlank() }
        .forEach { line ->
            val match = countSuffix.matchEntire(line)
            val title = (match?.groupValues?.get(1) ?: line).trim()
            val n = match?.groupValues?.get(2)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            if (title.isNotEmpty()) {
                counts[title] = (counts[title] ?: 0) + n
            }
        }
    return counts.entries.map { it.key to it.value }
}

/**
 * Full-width "Done while you were away" card with deduplicated tasks (×N monospace badge)
 * and a compact right-aligned header pill ("Ask about these →").
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DoneCard(item: TodayItem, onReact: (TodayItem, String) -> Unit, onAsk: (TodayItem) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val tasks = remember(item.body) { deduplicateDoneTasks(item.body) }
    // The agent's own short title ("4 finished"): the long form was cut off beside the Ask pill
    val headerTitle = item.title
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .todayGlassCard()
                .combinedClickable(onClick = {}, onLongClick = { menu = true })
                .padding(DeskSpace.l)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(DeskColors.Signal))
                Spacer(Modifier.width(DeskSpace.s))
                Text(
                    text = headerTitle,
                    style = DeskType.titleSmall,
                    color = DeskColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(DeskSpace.s))
                Text(
                    text = "Ask about these →",
                    style = DeskType.caption,
                    color = DeskColors.TextPrimary,
                    modifier = Modifier
                        .glass(DeskShapes.pill)
                        .clickable { onAsk(item) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            Spacer(Modifier.height(DeskSpace.m))
            Column(verticalArrangement = Arrangement.spacedBy(DeskSpace.s)) {
                tasks.forEach { (taskTitle, count) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(DeskColors.Signal.copy(alpha = 0.75f))
                        )
                        Spacer(Modifier.width(DeskSpace.s))
                        Text(
                            text = taskTitle,
                            style = DeskType.bodySmall,
                            color = DeskColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (count > 1) {
                            Spacer(Modifier.width(DeskSpace.s))
                            Box(
                                modifier = Modifier
                                    .clip(DeskShapes.small)
                                    .background(DeskColors.Glass)
                                    .border(1.dp, DeskColors.TodayGlassBorderTop, DeskShapes.small)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "×$count",
                                    style = DeskType.monoSmall,
                                    color = DeskColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
        CardMenu(item, menu, { menu = false }, onReact)
    }
}

/** A curious question from the agent, answered right here; the answer goes to the chat like any message. */
@Composable
private fun QuestionCard(item: TodayItem, answered: Boolean, onReact: (TodayItem, String) -> Unit, onAnswer: (TodayItem, String) -> Boolean) {
    var text by rememberSaveable(item.id) { mutableStateOf("") }
    val send = { if (text.isNotBlank() && onAnswer(item, text.trim())) text = "" }
    Column(Modifier.fillMaxWidth().todayGlassCard().padding(DeskSpace.l)) {
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
                Box(
                    Modifier.size(44.dp).glass(CircleShape, DeskColors.GlassStrong).clickable { send() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send, contentDescription = "Send answer", tint = DeskColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(DeskSpace.s))
            Text(
                "Not now", style = DeskType.caption, color = DeskColors.TextMuted,
                modifier = Modifier.clickable { onReact(item, "dismiss") }.padding(vertical = DeskSpace.xs)
            )
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
            Text(
                "More after ${clock(at)}, once there's something new", style = DeskType.mono, color = DeskColors.TextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun Notice(title: String, body: String, action: String, onAction: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(DeskSpace.xxl), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

/**
 * The frosted Chat | Today segmented pill (height = 38 dp, pill shape), with glowing emerald-tinted glass fill
 * and a 1 dp top specular rim on the active segment.
 */
@Composable
fun ChatTodaySwitch(showingToday: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .height(38.dp)
            .glass(DeskShapes.pill)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(false to "Chat", true to "Today").forEach { (today, label) ->
            val on = today == showingToday
            val segmentModifier = if (on) {
                Modifier
                    .fillMaxHeight()
                    .width(72.dp)
                    .clip(DeskShapes.pill)
                    .background(
                        Brush.verticalGradient(
                            listOf(DeskColors.SegmentActiveTop, DeskColors.SegmentActiveBottom)
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(DeskColors.SegmentActiveRim, DeskColors.SegmentActiveRim.copy(alpha = 0.15f))
                        ),
                        shape = DeskShapes.pill
                    )
            } else {
                Modifier
                    .fillMaxHeight()
                    .width(72.dp)
                    .clip(DeskShapes.pill)
            }
            Box(
                modifier = segmentModifier.clickable {
                    if (!on) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onChange(today)
                    }
                },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = if (on) DeskType.label.copy(fontWeight = FontWeight.SemiBold) else DeskType.label,
                    textAlign = TextAlign.Center,
                    color = if (on) DeskColors.TextPrimary else DeskColors.TextSecondary
                )
            }
        }
    }
}

/**
 * Unified single-row top navigation header for Today & Chat:
 * - Left: Clean unboxed Menu/Drawer icon (22 dp, #9BA8A1)
 * - Center: Frosted-glass Chat | Today segmented pill (height = 38 dp)
 * - Right: Compact status pill (● Tasks or ● 1 running when active) + Quick-Action grid icon
 */
@Composable
fun UnifiedTopBar(
    showingToday: Boolean,
    onSelectTab: (Boolean) -> Unit,
    onOpenDrawer: () -> Unit,
    tasksInProgress: Int,
    isRunningTask: Boolean,
    isConnected: Boolean,
    onOpenTasks: () -> Unit,
    onOpenQuickActions: () -> Unit,
    modifier: Modifier = Modifier,
    // Kept from the old top bar, which this row replaced: an update waiting (a dot on the grid; the updater is in
    // there) and the agent working while Devin's away (the pill says so and opens that sheet)
    hasUpdate: Boolean = false,
    awayShiftActive: Boolean = false,
    onOpenBackgroundWork: () -> Unit = {},
    onNewChat: (() -> Unit)? = null
) {
    val activeCount = if (tasksInProgress > 0) tasksInProgress else if (isRunningTask) 1 else 0
    val away = awayShiftActive && activeCount == 0 && isConnected
    val dotColor = when {
        !isConnected -> DeskColors.Error
        activeCount > 0 -> DeskColors.Waiting
        away -> DeskColors.Agent
        else -> DeskColors.Signal
    }
    val statusText = when {
        activeCount > 0 -> "$activeCount running"
        away -> "Looking into things"
        else -> "Tasks"
    }
    val onStatus = if (away) onOpenBackgroundWork else onOpenTasks

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .testTag("drawer_menu_button")
                    .clickable(onClick = onOpenDrawer)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Sessions",
                    tint = DeskColors.TopNavIcon,
                    modifier = Modifier.size(22.dp)
                )
            }
            // New chat in one tap (Devin, 2026-10-28), only on Chat
            if (!showingToday && onNewChat != null) {
                Spacer(Modifier.width(DeskSpace.s))
                Box(
                    modifier = Modifier.testTag("new_chat_button").clickable(onClick = onNewChat).padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New chat", tint = DeskColors.TopNavIcon,
                        modifier = Modifier.size(22.dp))
                }
            }
        }

        ChatTodaySwitch(
            showingToday = showingToday,
            onChange = onSelectTab,
            modifier = Modifier.align(Alignment.Center)
        )

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .glass(DeskShapes.pill)
                    .clickable(onClick = onStatus)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = statusText,
                    style = DeskType.caption,
                    color = DeskColors.TextPrimary,
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier
                    .clickable(onClick = onOpenQuickActions)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = if (hasUpdate) "Quick Actions (an app update is ready)" else "Quick Actions",
                    tint = DeskColors.TopNavIcon,
                    modifier = Modifier.size(22.dp)
                )
                if (hasUpdate) {
                    Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(DeskColors.Agent))
                }
            }
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
