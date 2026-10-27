package com.example.llegadadelbebe.core.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit

object MarkdownParser {

    /**
     * Convierte texto formateado en Markdown (con **negritas**, *cursivas*, viñetas,
     * títulos y código en línea) a un AnnotatedString limpio de Jetpack Compose,
     * eliminando los símbolos crudos "**" y aplicando estilos reales.
     */
    fun parse(rawText: String, baseColor: Color = Color.Unspecified): AnnotatedString {
        if (rawText.isBlank()) return AnnotatedString("")

        return buildAnnotatedString {
            val lines = rawText.lines()
            for ((lineIndex, line) in lines.withIndex()) {
                val trimmed = line.trim()

                when {
                    // Títulos Markdown: ### Header, ## Header, # Header
                    trimmed.startsWith("### ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                            appendLineText(trimmed.removePrefix("### ").trim())
                        }
                    }
                    trimmed.startsWith("## ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = baseColor)) {
                            appendLineText(trimmed.removePrefix("## ").trim())
                        }
                    }
                    trimmed.startsWith("# ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Black, color = baseColor)) {
                            appendLineText(trimmed.removePrefix("# ").trim())
                        }
                    }
                    // Viñetas: - item o * item
                    (trimmed.startsWith("- ") || trimmed.startsWith("* ")) && !trimmed.startsWith("**") -> {
                        append("  • ")
                        appendInlineMarkdown(trimmed.substring(2).trim())
                    }
                    // Listas numeradas: 1. item, 2. item, etc.
                    trimmed.matches(Regex("""^\d+\.\s+.*""")) -> {
                        val dotIndex = trimmed.indexOf('.')
                        val numberPrefix = trimmed.substring(0, dotIndex + 1)
                        val rest = trimmed.substring(dotIndex + 1).trim()
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("$numberPrefix ")
                        }
                        appendInlineMarkdown(rest)
                    }
                    else -> {
                        appendInlineMarkdown(line)
                    }
                }

                if (lineIndex < lines.size - 1) {
                    append("\n")
                }
            }
        }
    }

    private fun AnnotatedString.Builder.appendLineText(text: String) {
        appendInlineMarkdown(text)
    }

    /**
     * Parsea recursivamente negritas (**texto**), cursivas (*texto*) y código (`texto`)
     * en una línea sin dejar los delimitadores crudos en pantalla.
     */
    private fun AnnotatedString.Builder.appendInlineMarkdown(text: String) {
        var cursor = 0
        val length = text.length

        while (cursor < length) {
            val boldStart = text.indexOf("**", cursor)
            val codeStart = text.indexOf("`", cursor)
            val italicStart = findSingleAsterisk(text, cursor)

            val nextDelim = listOfNotNull(
                if (boldStart != -1) boldStart to "**" else null,
                if (codeStart != -1) codeStart to "`" else null,
                if (italicStart != -1) italicStart to "*" else null
            ).minByOrNull { it.first }

            if (nextDelim == null) {
                append(text.substring(cursor))
                break
            }

            val (startIdx, delim) = nextDelim

            if (startIdx > cursor) {
                append(text.substring(cursor, startIdx))
            }

            when (delim) {
                "**" -> {
                    val boldEnd = text.indexOf("**", startIdx + 2)
                    if (boldEnd != -1) {
                        val content = text.substring(startIdx + 2, boldEnd)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(content)
                        }
                        cursor = boldEnd + 2
                    } else {
                        append("**")
                        cursor = startIdx + 2
                    }
                }
                "`" -> {
                    val codeEnd = text.indexOf("`", startIdx + 1)
                    if (codeEnd != -1) {
                        val content = text.substring(startIdx + 1, codeEnd)
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                            append(content)
                        }
                        cursor = codeEnd + 1
                    } else {
                        append("`")
                        cursor = startIdx + 1
                    }
                }
                "*" -> {
                    val italicEnd = findSingleAsterisk(text, startIdx + 1)
                    if (italicEnd != -1) {
                        val content = text.substring(startIdx + 1, italicEnd)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(content)
                        }
                        cursor = italicEnd + 1
                    } else {
                        append("*")
                        cursor = startIdx + 1
                    }
                }
            }
        }
    }

    private fun findSingleAsterisk(text: String, startFrom: Int): Int {
        var i = startFrom
        while (i < text.length) {
            if (text[i] == '*') {
                val prevIsStar = i > 0 && text[i - 1] == '*'
                val nextIsStar = i + 1 < text.length && text[i + 1] == '*'
                if (!prevIsStar && !nextIsStar) {
                    return i
                }
            }
            i++
        }
        return -1
    }
}

/**
 * Composable que traduce automáticamente la sintaxis de Markdown generada por Gemini
 * o changelogs a texto enriquecido con negritas reales, viñetas y títulos limpios.
 */
@Composable
fun FormattedMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    val annotated = remember(text, color) {
        MarkdownParser.parse(text, color)
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight
    )
}
