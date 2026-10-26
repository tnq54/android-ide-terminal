package com.termide.android

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import java.io.File

object AnsiParser {

    private val ANSI_COLOR_MAP = mapOf(
        30 to 0xFF484F58.toInt(), // Black / Dark Gray
        31 to 0xFFFF7B72.toInt(), // Red
        32 to 0xFF3FB950.toInt(), // Green
        33 to 0xFFD29922.toInt(), // Yellow
        34 to 0xFF58A6FF.toInt(), // Blue
        35 to 0xFFD2A8FF.toInt(), // Magenta / Purple
        36 to 0xFF79C0FF.toInt(), // Cyan
        37 to 0xFFE6EDF3.toInt(), // White
        90 to 0xFF8B949E.toInt(), // Bright Black (Gray)
        91 to 0xFFFFA198.toInt(), // Bright Red
        92 to 0xFF56D364.toInt(), // Bright Green
        93 to 0xFFE3B341.toInt(), // Bright Yellow
        94 to 0xFF79C0FF.toInt(), // Bright Blue
        95 to 0xFFE2C5FF.toInt(), // Bright Magenta
        96 to 0xFFA5D6FF.toInt(), // Bright Cyan
        97 to 0xFFFFFFFF.toInt()  // Bright White
    )

    private val ESCAPE_SEQ_REGEX = Regex("\u001B\\[([0-9;]*)([a-zA-Z])")

    fun parseToSpannable(rawText: String, defaultColor: Int = 0xFFE6EDF3.toInt()): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        var currentColor: Int? = null
        var isBold = false
        var lastIndex = 0

        for (match in ESCAPE_SEQ_REGEX.findAll(rawText)) {
            val chunk = rawText.substring(lastIndex, match.range.first)
            if (chunk.isNotEmpty()) {
                appendStyledChunk(builder, chunk, currentColor, isBold)
            }

            val params = match.groupValues[1]
            val command = match.groupValues[2]
            if (command == "m") {
                val codes = if (params.isEmpty()) listOf(0) else params.split(';').mapNotNull { it.toIntOrNull() }
                for (code in codes) {
                    when (code) {
                        0 -> {
                            currentColor = null
                            isBold = false
                        }
                        1 -> isBold = true
                        22 -> isBold = false
                        39 -> currentColor = null
                        in ANSI_COLOR_MAP -> currentColor = ANSI_COLOR_MAP[code]
                    }
                }
            }
            lastIndex = match.range.last + 1
        }

        if (lastIndex < rawText.length) {
            appendStyledChunk(builder, rawText.substring(lastIndex), currentColor, isBold)
        }

        // Also highlight prompt lines like "~/workspace $ cmd"
        highlightPromptLines(builder, defaultColor)
        return builder
    }

    private fun appendStyledChunk(
        builder: SpannableStringBuilder,
        chunk: String,
        color: Int?,
        bold: Boolean
    ) {
        val start = builder.length
        builder.append(chunk)
        val end = builder.length
        if (color != null && start < end) {
            builder.setSpan(
                ForegroundColorSpan(color),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (bold && start < end) {
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private val PROMPT_LINE_REGEX = Regex("^(~[^\\n$]*|/[^\\n$]*)(\\$ )(.*)$", RegexOption.MULTILINE)

    private fun highlightPromptLines(builder: SpannableStringBuilder, defaultColor: Int) {
        val text = builder.toString()
        for (match in PROMPT_LINE_REGEX.findAll(text)) {
            val dirRange = match.groups[1]?.range
            val dollarRange = match.groups[2]?.range
            val cmdRange = match.groups[3]?.range

            if (dirRange != null && !dirRange.isEmpty()) {
                builder.setSpan(
                    ForegroundColorSpan(0xFF58A6FF.toInt()),
                    dirRange.first,
                    dirRange.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (dollarRange != null && !dollarRange.isEmpty()) {
                builder.setSpan(
                    ForegroundColorSpan(0xFF3FB950.toInt()),
                    dollarRange.first,
                    dollarRange.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    dollarRange.first,
                    dollarRange.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (cmdRange != null && !cmdRange.isEmpty()) {
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    cmdRange.first,
                    cmdRange.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    /**
     * Completes the last token in [currentInput] against files/directories in [workingDir]
     * or common shell commands if typing the first token.
     */
    fun autoCompleteInput(currentInput: String, workingDir: File): String {
        val trimmedEnd = currentInput
        val lastSpace = trimmedEnd.lastIndexOf(' ')
        val prefix = if (lastSpace == -1) "" else trimmedEnd.substring(0, lastSpace + 1)
        val token = if (lastSpace == -1) trimmedEnd else trimmedEnd.substring(lastSpace + 1)

        if (token.isEmpty()) {
            return "$currentInput    "
        }

        // Check files in workingDir
        val children = workingDir.listFiles()?.map {
            if (it.isDirectory) "${it.name}/" else it.name
        }.orEmpty()

        val builtinCmds = listOf(
            "ls", "cd", "pwd", "sh", "cat", "echo", "clear", "help",
            "history", "export", "mkdir", "rm", "cp", "mv", "chmod", "grep", "find"
        )

        val candidates = if (lastSpace == -1 && !token.contains('/')) {
            (builtinCmds + children).distinct().sorted()
        } else {
            children.sorted()
        }

        val matches = candidates.filter { it.startsWith(token, ignoreCase = true) }
        return when {
            matches.isEmpty() -> currentInput
            matches.size == 1 -> prefix + matches.first()
            else -> {
                val commonPrefix = longestCommonPrefix(matches)
                if (commonPrefix.length > token.length) {
                    prefix + commonPrefix
                } else {
                    prefix + matches.first()
                }
            }
        }
    }

    private fun longestCommonPrefix(strings: List<String>): String {
        if (strings.isEmpty()) return ""
        var prefix = strings.first()
        for (i in 1 until strings.size) {
            while (!strings[i].startsWith(prefix)) {
                prefix = prefix.substring(0, prefix.length - 1)
                if (prefix.isEmpty()) return ""
            }
        }
        return prefix
    }
}
