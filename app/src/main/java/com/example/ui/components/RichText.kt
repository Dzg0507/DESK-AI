package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Rich text for agent output and host logs: a tiny, dependency-free parser plus Compose renderers.
 *
 * Used by TaskDetailsSheet (a mission's RESULT / ERROR) and MaintenanceSheet (the log list). The parser is plain
 * Kotlin (no Compose types) so it is unit-tested on the JVM (RichTextParserTest). Everything is parsed once per
 * text with remember(text) — never per frame.
 *
 * What it understands, line by line: markdown headings, bullets / numbered items, fenced code, "---" rules,
 * "[tag] message" lines (e.g. [agentwork]), git diffstat lines ("readme.md | 2 ++", "1 file changed, 2
 * insertions(+)"), and "[Changes on branch X, not pushed]" badges. Inline: **bold**, `code`, http(s) links (tappable),
 * and file:/// links shown as just the short file name + line ("readme.md · L156", not tappable). Anything else is
 * shown as plain text, one paragraph per line, so plain output looks the same as before, only tidier.
 */

// ---------------------------------------------------------------------------------------------------------------
// Model (pure Kotlin)
// ---------------------------------------------------------------------------------------------------------------

sealed class RichBlock {
    data class Heading(val level: Int, val text: String) : RichBlock()
    data class Paragraph(val text: String) : RichBlock()
    data class Bullet(val text: String, val depth: Int) : RichBlock()
    data class Numbered(val number: String, val text: String, val depth: Int) : RichBlock()
    data class Code(val language: String, val code: String) : RichBlock()
    data class TagLine(val tag: String, val text: String) : RichBlock()
    data class DiffStatFile(val path: String, val count: String, val plus: Int, val minus: Int) : RichBlock()
    data class DiffStatSummary(val files: Int, val insertions: Int, val deletions: Int) : RichBlock()
    /** pushed: true = pushed/merged (green), false = not pushed (amber), null = unknown (cyan). */
    data class BranchBadge(val text: String, val pushed: Boolean?) : RichBlock()
    object Rule : RichBlock()
}

sealed class RichInline {
    data class Plain(val text: String) : RichInline()
    data class Bold(val children: List<RichInline>) : RichInline()
    data class Code(val text: String) : RichInline()
    data class WebLink(val label: String, val url: String) : RichInline()
    /** A local file reference (file:/// link): only the short name and line are shown. */
    data class FileRef(val name: String, val lines: String?) : RichInline()
}

// ---------------------------------------------------------------------------------------------------------------
// Block parser
// ---------------------------------------------------------------------------------------------------------------

private val HEADING = Regex("""^(#{1,6})\s+(.*?)\s*#*$""")
private val BULLET = Regex("""^([-*•+])\s+(.*)$""")
private val NUMBERED = Regex("""^(\d{1,3})[.)]\s+(.*)$""")
private val TAG_LINE = Regex("""^\[([A-Za-z0-9_.:\-]{1,24})]\s+(.*)$""")
private val BRACKETED = Regex("""^\[([^\[\]]+)]$""")
private val DIFFSTAT_FILE = Regex("""^(\S.*?)\s+\|\s+(\d+)\s*(\+*)(-*)\s*$""")
private val DIFFSTAT_BIN = Regex("""^(\S.*?)\s+\|\s+(Bin\b.*)$""")
private val DIFFSTAT_SUMMARY =
    Regex("""^(\d+) files? changed(?:, (\d+) insertions?\(\+\))?(?:, (\d+) deletions?\(-\))?\.?$""")
private val RULE = Regex("""^(-{3,}|\*{3,}|_{3,})$""")

fun parseRichText(text: String): List<RichBlock> {
    val out = mutableListOf<RichBlock>()
    val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trim()
        i++
        if (line.isEmpty()) continue

        if (line.startsWith("```")) {
            val lang = line.removePrefix("```").trim()
            val code = StringBuilder()
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                if (code.isNotEmpty()) code.append('\n')
                code.append(lines[i])
                i++
            }
            i++ // closing fence (or end of text)
            out.add(RichBlock.Code(lang, code.toString()))
            continue
        }

        val indent = raw.length - raw.trimStart().length
        val depth = (indent / 2).coerceAtMost(3)

        val heading = HEADING.matchEntire(line)
        if (heading != null) {
            out.add(RichBlock.Heading(heading.groupValues[1].length, heading.groupValues[2]))
        } else {
            out.add(classifyLine(line, depth))
        }
    }
    return out
}

private fun classifyLine(line: String, depth: Int): RichBlock {
    if (RULE.matches(line)) return RichBlock.Rule

    BRACKETED.matchEntire(line)?.let { m ->
        val inner = m.groupValues[1].trim()
        val lower = inner.lowercase()
        if (lower.contains("branch")) {
            val pushed = when {
                lower.contains("not pushed") || lower.contains("unpushed") || lower.contains("not merged") -> false
                lower.contains("pushed") || lower.contains("merged") -> true
                else -> null
            }
            return RichBlock.BranchBadge(inner, pushed)
        }
    }

    DIFFSTAT_SUMMARY.matchEntire(line)?.let { m ->
        return RichBlock.DiffStatSummary(
            files = m.groupValues[1].toIntOrNull() ?: 0,
            insertions = m.groupValues[2].toIntOrNull() ?: 0,
            deletions = m.groupValues[3].toIntOrNull() ?: 0
        )
    }

    // "[agentwork] Preparing ..." (a tag followed by a space; "[name](url)" links don't match)
    TAG_LINE.matchEntire(line)?.let { m ->
        return RichBlock.TagLine(m.groupValues[1], m.groupValues[2])
    }

    BULLET.matchEntire(line)?.let { m ->
        // "** bold" at the very start isn't a bullet
        if (!(m.groupValues[1] == "*" && line.startsWith("**"))) return RichBlock.Bullet(m.groupValues[2], depth)
    }
    NUMBERED.matchEntire(line)?.let { m ->
        return RichBlock.Numbered(m.groupValues[1], m.groupValues[2], depth)
    }

    if (!line.startsWith("|")) {
        DIFFSTAT_FILE.matchEntire(line)?.let { m ->
            return RichBlock.DiffStatFile(
                path = m.groupValues[1],
                count = m.groupValues[2],
                plus = m.groupValues[3].length,
                minus = m.groupValues[4].length
            )
        }
        DIFFSTAT_BIN.matchEntire(line)?.let { m ->
            return RichBlock.DiffStatFile(path = m.groupValues[1], count = m.groupValues[2], plus = 0, minus = 0)
        }
    }

    return RichBlock.Paragraph(line)
}

// ---------------------------------------------------------------------------------------------------------------
// Inline parser
// ---------------------------------------------------------------------------------------------------------------

// Order matters only for matches starting at the same index: a [label](url) link wins over the `code` in its label.
private val INLINE = Regex(
    """\[([^\]\n]+)]\(([^)\s]+)\)""" +                                // 1,2: [label](target)
        """|\*\*(.+?)\*\*""" +                                        // 3: **bold**
        """|`([^`\n]+)`""" +                                          // 4: `code`
        """|(file:///[^\s)\]>]+)""" +                                 // 5: bare file:/// url
        """|(https?://[^\s<>()\[\]]*[^\s<>()\[\].,;:!?'"])"""         // 6: bare web url
)

fun parseInlines(text: String): List<RichInline> {
    val out = mutableListOf<RichInline>()
    var cursor = 0
    for (m in INLINE.findAll(text)) {
        if (m.range.first > cursor) out.add(RichInline.Plain(text.substring(cursor, m.range.first)))
        val g = m.groups
        when {
            g[2] != null -> {
                val label = g[1]!!.value
                val target = g[2]!!.value
                when {
                    target.startsWith("file:", ignoreCase = true) -> out.add(fileRef(target))
                    target.startsWith("http://") || target.startsWith("https://") ->
                        out.add(RichInline.WebLink(label.trim('`'), target))
                    else -> out.add(RichInline.Code(label.trim('`')))   // relative path: show its name as code
                }
            }
            g[3] != null -> out.add(RichInline.Bold(parseInlines(g[3]!!.value)))
            g[4] != null -> out.add(RichInline.Code(g[4]!!.value))
            g[5] != null -> out.add(fileRef(g[5]!!.value))
            g[6] != null -> out.add(RichInline.WebLink(g[6]!!.value, g[6]!!.value))
        }
        cursor = m.range.last + 1
    }
    if (cursor < text.length) out.add(RichInline.Plain(text.substring(cursor)))
    return out
}

/** "file:///C:/x/readme.md#L154-L157" -> FileRef("readme.md", "L154-157"). */
fun fileRef(url: String): RichInline.FileRef {
    val hash = url.indexOf('#')
    val path = if (hash >= 0) url.substring(0, hash) else url
    val fragment = if (hash >= 0) url.substring(hash + 1) else ""
    val name = path.trimEnd('/', '\\')
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace("%20", " ")
        .ifBlank { path }
    val lines = Regex("""^L(\d+)(?:-L?(\d+))?$""").matchEntire(fragment)?.let { m ->
        val a = m.groupValues[1]
        val b = m.groupValues[2]
        if (b.isNotEmpty() && b != a) "L$a-$b" else "L$a"
    }
    return RichInline.FileRef(name, lines)
}

// ---------------------------------------------------------------------------------------------------------------
// Log lines (MaintenanceSheet)
// ---------------------------------------------------------------------------------------------------------------

enum class LogLevel { ERROR, WARNING, INFO, DEBUG }

data class ParsedLogLine(
    val time: String,
    val level: LogLevel,
    val tags: List<String>,
    val message: String
)

private val CLOCK = Regex("""(\d{1,2}:\d{2}:\d{2})""")
private val LEADING_TAG = Regex("""^\s*\[([A-Za-z0-9_.:\-/ ]{1,24})]\s*""")
private val ERROR_HINT = Regex("""Traceback|Exception\b|\b(ERROR|CRITICAL|FATAL)\b|Error:""")

fun parseLogLine(timestamp: String, level: String, message: String): ParsedLogLine {
    val time = CLOCK.find(timestamp)?.value ?: timestamp.trim().takeLast(12)
    var rest = message.trimEnd()
    val tags = mutableListOf<String>()
    while (tags.size < 3) {
        val m = LEADING_TAG.find(rest) ?: break
        val tag = m.groupValues[1].trim()
        // "[INFO]" style levels repeated inside the message aren't tags
        if (tag.uppercase() in setOf("INFO", "DEBUG", "WARN", "WARNING", "ERROR", "CRITICAL")) {
            rest = rest.substring(m.range.last + 1)
            continue
        }
        tags.add(tag)
        rest = rest.substring(m.range.last + 1)
    }
    val lvl = when (level.trim().uppercase()) {
        "ERROR", "CRITICAL", "FATAL", "EXCEPTION" -> LogLevel.ERROR
        "WARN", "WARNING" -> LogLevel.WARNING
        "DEBUG", "TRACE", "VERBOSE" -> LogLevel.DEBUG
        else -> if (ERROR_HINT.containsMatchIn(message)) LogLevel.ERROR else LogLevel.INFO
    }
    return ParsedLogLine(time, lvl, tags, rest)
}

// ---------------------------------------------------------------------------------------------------------------
// Colors (match ui/theme/Color.kt)
// ---------------------------------------------------------------------------------------------------------------

private val Cyan = Color(0xFF38BDF8)          // ElectricCyan
private val Emerald = Color(0xFF10B981)       // EmeraldConnected
private val Rose = Color(0xFFF43F5E)          // RoseError
private val Amber = Color(0xFFF59E0B)         // AmberPending
private val Slate200 = Color(0xFFE2E8F0)
private val Slate300 = Color(0xFFCBD5E1)
private val Slate400 = Color(0xFF94A3B8)
private val Slate500 = Color(0xFF64748B)
private val Slate700 = Color(0xFF334155)

private val TagPalette = listOf(
    Color(0xFF38BDF8), Color(0xFFA855F7), Color(0xFF10B981), Color(0xFFF59E0B),
    Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFFF97316)
)

/** A stable color per tag name, so [Push] is always the same color. */
fun tagColor(tag: String): Color {
    val h = tag.lowercase().hashCode()
    return TagPalette[((h % TagPalette.size) + TagPalette.size) % TagPalette.size]
}

// ---------------------------------------------------------------------------------------------------------------
// AnnotatedString builders (plain functions: call them inside remember)
// ---------------------------------------------------------------------------------------------------------------

private val LinkStyle = TextLinkStyles(style = SpanStyle(color = Cyan, textDecoration = TextDecoration.Underline))

private fun AnnotatedString.Builder.appendInlines(items: List<RichInline>, base: Color) {
    for (item in items) when (item) {
        is RichInline.Plain -> append(item.text)
        is RichInline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
            appendInlines(item.children, base)
        }
        is RichInline.Code -> withStyle(
            SpanStyle(fontFamily = FontFamily.Monospace, color = Cyan, background = Cyan.copy(alpha = 0.14f))
        ) { append("\u2009${item.text}\u2009") }
        is RichInline.FileRef -> withStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFC4B5FD),
                background = Color(0xFFA855F7).copy(alpha = 0.16f)
            )
        ) {
            append("\u2009\uD83D\uDCC4 ${item.name}")
            if (item.lines != null) withStyle(SpanStyle(color = Slate400)) { append(" · ${item.lines}") }
            append("\u2009")
        }
        is RichInline.WebLink -> withLink(LinkAnnotation.Url(item.url, LinkStyle)) { append(item.label) }
    }
}

fun richInlineString(text: String, base: Color = Slate200): AnnotatedString =
    buildAnnotatedString { appendInlines(parseInlines(text), base) }

fun logLineString(line: ParsedLogLine): AnnotatedString = buildAnnotatedString {
    if (line.time.isNotBlank()) withStyle(SpanStyle(color = Slate500)) { append(line.time); append("  ") }
    val (label, color) = when (line.level) {
        LogLevel.ERROR -> "ERR " to Rose
        LogLevel.WARNING -> "WARN" to Amber
        LogLevel.INFO -> "INFO" to Emerald
        LogLevel.DEBUG -> "DBG " to Slate500
    }
    withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(label) }
    append(" ")
    for (tag in line.tags) {
        val c = tagColor(tag)
        withStyle(SpanStyle(color = c, background = c.copy(alpha = 0.15f), fontWeight = FontWeight.SemiBold)) {
            append("\u2009$tag\u2009")
        }
        append(" ")
    }
    val msgColor = when (line.level) {
        LogLevel.ERROR -> Color(0xFFFDA4AF)
        LogLevel.WARNING -> Color(0xFFFDE68A)
        LogLevel.INFO -> Slate300
        LogLevel.DEBUG -> Slate500
    }
    withStyle(SpanStyle(color = msgColor)) { append(line.message) }
}

// ---------------------------------------------------------------------------------------------------------------
// Composables
// ---------------------------------------------------------------------------------------------------------------

/**
 * Renders agent output (markdown-ish, tagged lines, diffstat) in the app's dark palette.
 * [baseColor] is the paragraph color (e.g. a rose tint for errors).
 */
@Composable
fun RichText(text: String, modifier: Modifier = Modifier, baseColor: Color = Slate200) {
    val blocks = remember(text) { parseRichText(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (blocks.isEmpty()) {
            Text(text, fontSize = 12.sp, color = baseColor, lineHeight = 17.sp)
        }
        blocks.forEachIndexed { index, block -> RichBlockView(block, baseColor, first = index == 0) }
    }
}

@Composable
private fun RichBlockView(block: RichBlock, base: Color, first: Boolean) {
    when (block) {
        is RichBlock.Heading -> {
            val str = remember(block) { richInlineString(block.text, Cyan) }
            if (!first) Spacer(modifier = Modifier.height(2.dp))
            Text(
                str,
                fontSize = when (block.level) { 1 -> 15.sp; 2 -> 14.sp; else -> 13.sp },
                fontWeight = FontWeight.Bold,
                color = Cyan,
                lineHeight = 19.sp
            )
        }
        is RichBlock.Paragraph -> {
            val str = remember(block, base) { richInlineString(block.text, base) }
            Text(str, fontSize = 12.sp, color = base, lineHeight = 17.sp)
        }
        is RichBlock.Bullet -> ListRow(marker = "•", markerColor = Cyan, depth = block.depth, text = block.text, base = base)
        is RichBlock.Numbered -> ListRow(
            marker = "${block.number}.", markerColor = Cyan, depth = block.depth, text = block.text, base = base
        )
        is RichBlock.Code -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF090D16))
                .border(1.dp, Slate700, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Text(block.code, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Slate200, lineHeight = 15.sp)
        }
        is RichBlock.TagLine -> {
            val str = remember(block) { richInlineString(block.text, Slate400) }
            val c = tagColor(block.tag)
            Row(verticalAlignment = Alignment.Top) {
                Pill(block.tag, c, fontSize = 9)
                Spacer(modifier = Modifier.width(6.dp))
                Text(str, fontSize = 10.sp, color = Slate400, lineHeight = 14.sp, modifier = Modifier.weight(1f))
            }
        }
        is RichBlock.DiffStatFile -> {
            val str = remember(block) {
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Slate200)) { append(block.path) }
                    withStyle(SpanStyle(color = Slate500)) { append("  │ ") }
                    withStyle(SpanStyle(color = Slate400)) { append(block.count) }
                    if (block.plus > 0 || block.minus > 0) append(" ")
                    if (block.plus > 0) withStyle(SpanStyle(color = Emerald, fontWeight = FontWeight.Bold)) {
                        append("+".repeat(block.plus))
                    }
                    if (block.minus > 0) withStyle(SpanStyle(color = Rose, fontWeight = FontWeight.Bold)) {
                        append("-".repeat(block.minus))
                    }
                }
            }
            Text(str, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
        }
        is RichBlock.DiffStatSummary -> {
            val str = remember(block) {
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Slate300)) {
                        append("${block.files} file${if (block.files == 1) "" else "s"} changed")
                    }
                    if (block.insertions > 0) {
                        withStyle(SpanStyle(color = Slate500)) { append(" · ") }
                        withStyle(SpanStyle(color = Emerald, fontWeight = FontWeight.Bold)) {
                            append("+${block.insertions}")
                        }
                    }
                    if (block.deletions > 0) {
                        withStyle(SpanStyle(color = Slate500)) { append(" · ") }
                        withStyle(SpanStyle(color = Rose, fontWeight = FontWeight.Bold)) { append("−${block.deletions}") }
                    }
                }
            }
            Text(str, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
        }
        is RichBlock.BranchBadge -> {
            val c = when (block.pushed) { true -> Emerald; false -> Amber; null -> Cyan }
            Pill(block.text, c, fontSize = 10)
        }
        RichBlock.Rule -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Slate700)
        )
    }
}

@Composable
private fun ListRow(marker: String, markerColor: Color, depth: Int, text: String, base: Color) {
    val str = remember(text, base) { richInlineString(text, base) }
    Row(modifier = Modifier.fillMaxWidth().padding(start = (depth * 12).dp), verticalAlignment = Alignment.Top) {
        Text(marker, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = markerColor, lineHeight = 17.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(str, fontSize = 12.sp, color = base, lineHeight = 17.sp, modifier = Modifier.weight(1f))
    }
}

/** A small rounded label: tinted background, colored border and text. */
@Composable
fun Pill(text: String, color: Color, fontSize: Int = 10, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            text,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color,
            lineHeight = (fontSize + 4).sp
        )
    }
}
