package com.termide.android

import android.text.Editable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import java.io.File

object SyntaxHighlighter {

    private const val COLOR_KEYWORD = 0xFFFF7B72.toInt()   // Coral red
    private const val COLOR_STRING = 0xFFA5D6FF.toInt()    // Light blue
    private const val COLOR_COMMENT = 0xFF8B949E.toInt()   // Muted gray
    private const val COLOR_NUMBER = 0xFF79C0FF.toInt()    // Cyan blue
    private const val COLOR_VARIABLE = 0xFFFFA657.toInt()  // Orange
    private const val COLOR_FUNCTION = 0xFFD2A8FF.toInt()  // Purple

    private val KEYWORD_REGEX = Regex(
        "\\b(if|then|else|elif|fi|for|while|do|done|case|esac|function|return|exit|echo|export|local|readonly|" +
        "def|class|import|from|as|pass|break|continue|try|except|finally|with|yield|lambda|async|await|" +
        "fun|val|var|object|interface|package|public|private|protected|internal|override|suspend|data|sealed|when|" +
        "void|int|long|double|float|boolean|char|byte|short|static|final|new|this|super|extends|implements|" +
        "const|let|var|true|false|null|None|True|False)\\b"
    )
    private val NUMBER_REGEX = Regex("\\b\\d+(\\.\\d+)?\\b")
    private val VARIABLE_REGEX = Regex("\\$[a-zA-Z_][a-zA-Z0-9_]*|\\$\\{[^}]+\\}|@[a-zA-Z_][a-zA-Z0-9_]*")
    private val FUNC_CALL_REGEX = Regex("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?=\\()")
    private val STRING_REGEX = Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")
    private val COMMENT_REGEX = Regex("(#.*$)|(//.*$)|(/\\*[\\s\\S]*?\\*/)", RegexOption.MULTILINE)

    fun detectLanguage(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "sh", "bash", "zsh" -> "SHELL"
            "py" -> "PYTHON"
            "kt", "kts" -> "KOTLIN"
            "java" -> "JAVA"
            "js", "mjs" -> "JAVASCRIPT"
            "ts" -> "TYPESCRIPT"
            "c", "h" -> "C"
            "cpp", "hpp", "cc" -> "C++"
            "json" -> "JSON"
            "xml", "html", "htm" -> "MARKUP"
            "md", "txt" -> "MARKDOWN"
            else -> "TEXT"
        }
    }

    fun buildRunCommand(file: File): String {
        val name = file.name
        val ext = name.substringAfterLast('.', "").lowercase()
        val quotedPath = "\"${file.absolutePath}\""
        return when (ext) {
            "sh", "bash" -> "sh $quotedPath"
            "py" -> "if command -v python3 >/dev/null 2>&1; then python3 $quotedPath; elif command -v python >/dev/null 2>&1; then python $quotedPath; else echo 'Python binary not found on device; showing file content:'; cat $quotedPath; fi"
            "js" -> "if command -v node >/dev/null 2>&1; then node $quotedPath; else echo 'Node.js binary not found on device; showing file content:'; cat $quotedPath; fi"
            else -> {
                if (file.canExecute()) {
                    quotedPath
                } else {
                    "sh $quotedPath"
                }
            }
        }
    }

    fun applyHighlighting(editable: Editable) {
        val existingSpans = editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
        for (span in existingSpans) {
            editable.removeSpan(span)
        }

        val text = editable.toString()
        if (text.isEmpty() || text.length > 25_000) return

        applyPattern(editable, text, FUNC_CALL_REGEX, COLOR_FUNCTION)
        applyPattern(editable, text, NUMBER_REGEX, COLOR_NUMBER)
        applyPattern(editable, text, KEYWORD_REGEX, COLOR_KEYWORD)
        applyPattern(editable, text, VARIABLE_REGEX, COLOR_VARIABLE)
        applyPattern(editable, text, STRING_REGEX, COLOR_STRING)
        applyPattern(editable, text, COMMENT_REGEX, COLOR_COMMENT)
    }

    private fun applyPattern(editable: Editable, text: String, regex: Regex, color: Int) {
        for (match in regex.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start >= 0 && end <= editable.length && start < end) {
                editable.setSpan(
                    ForegroundColorSpan(color),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }
}
