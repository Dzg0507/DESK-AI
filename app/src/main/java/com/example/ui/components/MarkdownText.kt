package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.ui.dialogs.ImageViewerDialog
import com.example.ui.theme.CodeBlockBackground
import com.example.ui.theme.ElectricCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class MarkdownElement {
    data class Paragraph(val text: String) : MarkdownElement()
    data class Header(val level: Int, val text: String) : MarkdownElement()
    data class CodeBlock(val language: String, val code: String) : MarkdownElement()
    data class BulletItem(val text: String) : MarkdownElement()
    data class Image(val url: String, val alt: String = "") : MarkdownElement()
}

@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    serverBaseUrl: String = "",
    authToken: String = ""
) {
    val elements = remember(content) { parseMarkdown(content) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        elements.forEach { element ->
            when (element) {
                is MarkdownElement.Header -> {
                    Text(
                        text = buildStyledInlineMarkdown(element.text, textColor),
                        fontSize = when (element.level) {
                            1 -> 20.sp
                            2 -> 18.sp
                            else -> 16.sp
                        },
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        lineHeight = 24.sp
                    )
                }
                is MarkdownElement.Paragraph -> {
                    Text(
                        text = buildStyledInlineMarkdown(element.text, textColor),
                        fontSize = 15.sp,
                        color = textColor,
                        lineHeight = 22.sp
                    )
                }
                is MarkdownElement.BulletItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = buildStyledInlineMarkdown(element.text, textColor),
                            fontSize = 15.sp,
                            color = textColor,
                            lineHeight = 22.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownElement.CodeBlock -> {
                    CodeBlockCard(language = element.language, code = element.code)
                }
                is MarkdownElement.Image -> {
                    MarkdownImage(
                        url = element.url,
                        alt = element.alt,
                        serverBaseUrl = serverBaseUrl,
                        authToken = authToken
                    )
                }
            }
        }
    }
}

@Composable
fun MarkdownImage(
    url: String,
    alt: String,
    serverBaseUrl: String = "",
    authToken: String = ""
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    val resolvedUrl = remember(url, serverBaseUrl, authToken) {
        val cleanUrl = url.trim().replace("\n", "").replace(" ", "")
        val fullUrl = if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            cleanUrl
        } else {
            val cleanBase = serverBaseUrl.trimEnd('/')
            val path = if (cleanUrl.startsWith("/")) cleanUrl else "/$cleanUrl"
            if (cleanBase.isNotBlank()) "$cleanBase$path" else cleanUrl
        }
        if (authToken.isNotBlank() && !fullUrl.contains("token=") && (fullUrl.startsWith("http://") || fullUrl.startsWith("https://"))) {
            val sep = if (fullUrl.contains("?")) "&" else "?"
            "$fullUrl${sep}token=${authToken.trim()}"
        } else {
            fullUrl
        }
    }

    val imageRequest = remember(resolvedUrl, authToken) {
        coil.request.ImageRequest.Builder(context)
            .data(resolvedUrl)
            .crossfade(true)
            .apply {
                if (authToken.isNotBlank()) {
                    addHeader("X-HUD-Token", authToken.trim())
                    addHeader("Authorization", "Bearer ${authToken.trim()}")
                }
            }
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = true }
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = alt.ifBlank { "Generated Image" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop
                )
                // Tap to Zoom Overlay Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🔍 Tap to Zoom",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricCyan
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = alt.ifBlank { "Generated Image" },
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "🔍 Zoom & Pan",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricCyan,
                        modifier = Modifier
                            .clickable { isExpanded = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Text(
                        text = "🌐 Browser",
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier
                            .clickable {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(resolvedUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }

    if (isExpanded) {
        ImageViewerDialog(
            imageUrl = resolvedUrl,
            altText = alt,
            authToken = authToken,
            onDismiss = { isExpanded = false }
        )
    }
}

@Composable
fun CodeBlockCard(language: String, code: String) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CodeBlockBackground)
            .border(1.dp, Color(0xFF2E3A52), RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131B2E))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (if (language.isBlank()) "CODE" else language.uppercase()),
                    color = ElectricCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("code", code)
                        clipboard.setPrimaryClip(clip)
                        copied = true
                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            delay(2000)
                            copied = false
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy Code",
                        tint = if (copied) Color(0xFF10B981) else Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Code Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFFE2E8F0),
                    lineHeight = 19.sp
                )
            }
        }
    }
}

/**
 * Basic lightweight Markdown parser that extracts code fences, headers, bullets, and paragraphs.
 */
fun parseMarkdown(text: String): List<MarkdownElement> {
    val elements = mutableListOf<MarkdownElement>()
    val lines = text.split("\n")
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Fenced code block: ```language
        if (line.trim().startsWith("```")) {
            val language = line.trim().removePrefix("```").trim()
            val codeBuilder = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeBuilder.append(lines[i]).append("\n")
                i++
            }
            elements.add(MarkdownElement.CodeBlock(language, codeBuilder.toString().trimEnd()))
            i++
            continue
        }

        // Headers: #, ##, ###
        val trimmed = line.trim()
        if (trimmed.startsWith("### ")) {
            elements.add(MarkdownElement.Header(3, trimmed.removePrefix("### ")))
            i++
            continue
        } else if (trimmed.startsWith("## ")) {
            elements.add(MarkdownElement.Header(2, trimmed.removePrefix("## ")))
            i++
            continue
        } else if (trimmed.startsWith("# ")) {
            elements.add(MarkdownElement.Header(1, trimmed.removePrefix("# ")))
            i++
            continue
        }

        // Bullet point: •, -, *
        if (trimmed.startsWith("• ") || trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            val clean = trimmed.removePrefix("• ").removePrefix("- ").removePrefix("* ")
            elements.add(MarkdownElement.BulletItem(clean))
            i++
            continue
        }

        // Image: ![alt](url)
        val imageRegex = Regex("""!\[([^\]]*)\]\(([^)]+)\)""")
        val imageMatch = imageRegex.find(trimmed)
        if (imageMatch != null) {
            val alt = imageMatch.groupValues[1]
            val url = imageMatch.groupValues[2].trim().replace("\n", "").replace(" ", "")
            elements.add(MarkdownElement.Image(url, alt))
            i++
            continue
        }

        // Multi-line wrapped image: ![alt](... \n ...)
        if (trimmed.contains("![") && !trimmed.contains(")")) {
            var combined = trimmed
            var j = i + 1
            while (j < lines.size && !combined.contains(")")) {
                combined += lines[j].trim()
                j++
            }
            val multiMatch = imageRegex.find(combined)
            if (multiMatch != null) {
                val alt = multiMatch.groupValues[1]
                val url = multiMatch.groupValues[2].trim().replace("\n", "").replace(" ", "")
                elements.add(MarkdownElement.Image(url, alt))
                i = j
                continue
            }
        }

        // Standard paragraph line (or empty line)
        if (trimmed.isNotEmpty()) {
            elements.add(MarkdownElement.Paragraph(line))
        }

        i++
    }

    if (elements.isEmpty() && text.isNotEmpty()) {
        elements.add(MarkdownElement.Paragraph(text))
    }

    return elements
}

/**
 * Formats inline bold (`**text**`), italic (`*text*`), and inline code (` `code` `)
 */
fun buildStyledInlineMarkdown(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val length = text.length

        while (cursor < length) {
            val nextBold = text.indexOf("**", cursor)
            val nextCode = text.indexOf("`", cursor)

            // Find closest token
            val validTokens = listOfNotNull(
                if (nextBold != -1) "bold" to nextBold else null,
                if (nextCode != -1) "code" to nextCode else null
            ).sortedBy { it.second }

            if (validTokens.isEmpty()) {
                append(text.substring(cursor))
                break
            }

            val (tokenType, tokenIndex) = validTokens.first()
            if (tokenIndex > cursor) {
                append(text.substring(cursor, tokenIndex))
                cursor = tokenIndex
            }

            if (tokenType == "bold") {
                val endBold = text.indexOf("**", cursor + 2)
                if (endBold != -1) {
                    val boldContent = text.substring(cursor + 2, endBold)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(boldContent)
                    }
                    cursor = endBold + 2
                } else {
                    append("**")
                    cursor += 2
                }
            } else if (tokenType == "code") {
                val endCode = text.indexOf("`", cursor + 1)
                if (endCode != -1) {
                    val codeContent = text.substring(cursor + 1, endCode)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x3338BDF8),
                            color = ElectricCyan,
                            fontSize = 13.sp
                        )
                    ) {
                        append(" $codeContent ")
                    }
                    cursor = endCode + 1
                } else {
                    append("`")
                    cursor += 1
                }
            }
        }
    }
}
