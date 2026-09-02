package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Cyan300
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.Pink500
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MarkdownView(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Slate100
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.Header -> {
                    val fontSize = when (block.level) {
                        1 -> 20.sp
                        2 -> 18.sp
                        3 -> 16.sp
                        else -> 15.sp
                    }
                    Text(
                        text = parseInlineMarkdown(block.content, textColor),
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = Slate100,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                is MarkdownBlock.CodeBlock -> {
                    var copied by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate950)
                            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Slate900)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = block.lang.ifEmpty { "code" },
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Cyan400,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Slate800)
                                    .border(1.dp, GlassBorder, RoundedCornerShape(6.dp))
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Code", block.code)
                                        clipboard.setPrimaryClip(clip)
                                        copied = true
                                        scope.launch {
                                            delay(2000)
                                            copied = false
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (copied) Icons.Default.Done else Icons.Default.ContentCopy,
                                    contentDescription = "Copy code",
                                    tint = if (copied) Emerald400 else Slate300,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (copied) "Copied" else "Copy",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (copied) Emerald400 else Slate300
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp)
                        ) {
                            Text(
                                text = block.code,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Slate100,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            color = Cyan400,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = parseInlineMarkdown(block.content, textColor),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = textColor
                        )
                    }
                }
                is MarkdownBlock.NumberedItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${block.number}.",
                            color = Cyan400,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = parseInlineMarkdown(block.content, textColor),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = textColor
                        )
                    }
                }
                is MarkdownBlock.Quote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(GlassSurface)
                            .border(1.dp, GlassBorder, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(Cyan400, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseInlineMarkdown(block.content, textColor),
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            color = Slate300
                        )
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(block.content, textColor),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = textColor
                    )
                }
            }
        }
    }
}

sealed class MarkdownBlock {
    data class Header(val level: Int, val content: String) : MarkdownBlock()
    data class CodeBlock(val lang: String, val code: String) : MarkdownBlock()
    data class BulletItem(val content: String) : MarkdownBlock()
    data class NumberedItem(val number: String, val content: String) : MarkdownBlock()
    data class Quote(val content: String) : MarkdownBlock()
    data class Paragraph(val content: String) : MarkdownBlock()
}

fun parseMarkdownBlocks(src: String): List<MarkdownBlock> {
    val lines = src.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Code block
        if (line.trim().startsWith("```")) {
            val lang = line.trim().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++ // skip closing fence
            blocks.add(MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            continue
        }

        // Headers
        val headerMatch = Regex("^(#{1,4})\\s+(.*)$").find(line)
        if (headerMatch != null) {
            val level = headerMatch.groupValues[1].length
            val text = headerMatch.groupValues[2]
            blocks.add(MarkdownBlock.Header(level, text))
            i++
            continue
        }

        // Bullet lists
        val bulletMatch = Regex("^[-*]\\s+(.*)$").find(line)
        if (bulletMatch != null) {
            blocks.add(MarkdownBlock.BulletItem(bulletMatch.groupValues[1]))
            i++
            continue
        }

        // Numbered lists
        val numberMatch = Regex("^(\\d+)\\.\\s+(.*)$").find(line)
        if (numberMatch != null) {
            blocks.add(MarkdownBlock.NumberedItem(numberMatch.groupValues[1], numberMatch.groupValues[2]))
            i++
            continue
        }

        // Quotes
        if (line.trim().startsWith(">")) {
            val quoteContent = line.trim().removePrefix(">").trim()
            blocks.add(MarkdownBlock.Quote(quoteContent))
            i++
            continue
        }

        // Blank line
        if (line.isBlank()) {
            i++
            continue
        }

        // Paragraph
        val paraLines = mutableListOf<String>()
        while (i < lines.size &&
            lines[i].isNotBlank() &&
            !lines[i].trim().startsWith("```") &&
            !lines[i].trim().startsWith("#") &&
            !lines[i].trim().startsWith("- ") &&
            !lines[i].trim().startsWith("* ") &&
            !Regex("^\\d+\\.").containsMatchIn(lines[i].trim()) &&
            !lines[i].trim().startsWith(">")
        ) {
            paraLines.add(lines[i])
            i++
        }
        if (paraLines.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString(" ")))
        }
    }
    return blocks
}

fun parseInlineMarkdown(text: String, defaultColor: Color): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("(\\*\\*([^*]+)\\*\\*)|(`([^`]+)`)|(\\*([^*]+)\\*)|(\\[([^\\]]+)\\]\\(([^)]+)\\))")
        val matches = regex.findAll(text)

        for (match in matches) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }

            val fullMatch = match.value
            when {
                fullMatch.startsWith("**") && fullMatch.endsWith("**") -> {
                    val inner = fullMatch.substring(2, fullMatch.length - 2)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Slate100)) {
                        append(inner)
                    }
                }
                fullMatch.startsWith("`") && fullMatch.endsWith("`") -> {
                    val inner = fullMatch.substring(1, fullMatch.length - 1)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Slate800,
                            color = Pink500,
                            fontSize = 13.sp
                        )
                    ) {
                        append(" $inner ")
                    }
                }
                fullMatch.startsWith("*") && fullMatch.endsWith("*") -> {
                    val inner = fullMatch.substring(1, fullMatch.length - 1)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Slate300)) {
                        append(inner)
                    }
                }
                fullMatch.startsWith("[") && fullMatch.contains("](") -> {
                    val label = match.groupValues[8]
                    withStyle(SpanStyle(color = Cyan300, fontWeight = FontWeight.Medium)) {
                        append(label)
                    }
                }
                else -> {
                    append(fullMatch)
                }
            }
            cursor = match.range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
