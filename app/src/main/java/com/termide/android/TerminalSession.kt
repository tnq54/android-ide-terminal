package com.termide.android

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicBoolean

class TerminalSession(
    val id: Int,
    var title: String,
    initialDir: File,
    private val homeDir: File
) {
    var workingDir: File = initialDir
        private set

    val commandHistory: MutableList<String> = mutableListOf()
    var historyCursor: Int = 0

    private val outputBuffer = StringBuilder()
    private var currentProcess: Process? = null
    val isRunning = AtomicBoolean(false)

    private val customEnv = mutableMapOf(
        "TERM" to "xterm-256color",
        "HOME" to homeDir.absolutePath,
        "COLORTERM" to "truecolor"
    )

    init {
        appendLine("╔══════════════════════════════════════════════════╗")
        appendLine("║  TermIDE Studio Shell • Session #$id ($title)     ║")
        appendLine("║  Type 'help' for built-in IDE terminal commands  ║")
        appendLine("╚══════════════════════════════════════════════════╝")
        appendLine("Working directory: ${formatPath(workingDir)}")
        appendLine("")
    }

    fun getTranscript(): String = synchronized(outputBuffer) {
        outputBuffer.toString()
    }

    fun appendText(text: String) {
        val cleaned = stripAnsiCodes(text)
        synchronized(outputBuffer) {
            outputBuffer.append(cleaned)
            if (outputBuffer.length > MAX_BUFFER_CHARS) {
                outputBuffer.delete(0, outputBuffer.length - MAX_BUFFER_CHARS)
            }
        }
    }

    fun appendLine(line: String) {
        appendText("$line\n")
    }

    fun clearTranscript() {
        synchronized(outputBuffer) {
            outputBuffer.clear()
        }
    }

    fun formatPath(dir: File): String {
        val abs = dir.absolutePath
        val homeAbs = homeDir.absolutePath
        return if (abs == homeAbs) {
            "~"
        } else if (abs.startsWith("$homeAbs/")) {
            "~" + abs.removePrefix(homeAbs)
        } else {
            abs
        }
    }

    fun changeDirectory(targetDir: File): Boolean {
        return if (targetDir.exists() && targetDir.isDirectory) {
            workingDir = targetDir.canonicalFile
            true
        } else {
            false
        }
    }

    fun interruptCurrentProcess(): Boolean {
        val proc = currentProcess
        return if (proc != null && proc.isAlive) {
            proc.destroyForcibly()
            isRunning.set(false)
            appendLine("^C [Process interrupted]")
            true
        } else {
            appendLine("^C")
            false
        }
    }

    fun executeCommand(
        rawCommand: String,
        onOutputUpdate: () -> Unit,
        onComplete: (Int) -> Unit
    ) {
        val cmd = rawCommand.trim()
        appendLine("${formatPath(workingDir)} $ $cmd")
        onOutputUpdate()

        if (cmd.isEmpty()) {
            onComplete(0)
            return
        }

        if (commandHistory.isEmpty() || commandHistory.last() != cmd) {
            commandHistory.add(cmd)
        }
        historyCursor = commandHistory.size

        // Handle built-in commands
        when {
            cmd == "clear" || cmd == "cls" -> {
                clearTranscript()
                onOutputUpdate()
                onComplete(0)
                return
            }
            cmd == "help" -> {
                appendLine("Built-in TermIDE Shell Commands:")
                appendLine("  help              Show this help message")
                appendLine("  clear / cls       Clear terminal screen")
                appendLine("  cd <path>         Change working directory (~ for workspace)")
                appendLine("  pwd               Print working directory")
                appendLine("  export KEY=VAL    Set session environment variable")
                appendLine("  history           Show command history")
                appendLine("  sh <file.sh>      Execute shell script in workspace")
                appendLine("")
                onOutputUpdate()
                onComplete(0)
                return
            }
            cmd == "history" -> {
                commandHistory.forEachIndexed { index, item ->
                    appendLine("  ${index + 1}  $item")
                }
                onOutputUpdate()
                onComplete(0)
                return
            }
            cmd == "pwd" -> {
                appendLine(workingDir.absolutePath)
                onOutputUpdate()
                onComplete(0)
                return
            }
            cmd == "cd" || cmd.startsWith("cd ") -> {
                val arg = cmd.removePrefix("cd").trim()
                val target = when {
                    arg.isEmpty() || arg == "~" -> homeDir
                    arg.startsWith("~/") -> File(homeDir, arg.removePrefix("~/"))
                    arg.startsWith("/") -> File(arg)
                    else -> File(workingDir, arg)
                }
                if (changeDirectory(target)) {
                    onOutputUpdate()
                    onComplete(0)
                } else {
                    appendLine("cd: no such directory: $arg")
                    onOutputUpdate()
                    onComplete(1)
                }
                return
            }
            cmd.startsWith("export ") -> {
                val pair = cmd.removePrefix("export ").trim()
                val eqIdx = pair.indexOf('=')
                if (eqIdx > 0) {
                    val key = pair.substring(0, eqIdx).trim()
                    val value = pair.substring(eqIdx + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                    customEnv[key] = value
                    onOutputUpdate()
                    onComplete(0)
                } else {
                    appendLine("export: usage: export KEY=VALUE")
                    onOutputUpdate()
                    onComplete(1)
                }
                return
            }
        }

        // Execute OS shell command via ProcessBuilder
        isRunning.set(true)
        try {
            val shellBinary = if (File("/system/bin/sh").exists()) "/system/bin/sh" else "sh"
            // Append pwd marker so if user chains `cd subdir && ...` we track the final directory
            val wrappedCmd = "$cmd\n__EC=\$?\necho \"__TERMIDE_PWD__:\$(pwd)\"\nexit \$__EC"
            val pb = ProcessBuilder(shellBinary, "-c", wrappedCmd)
            pb.directory(workingDir)
            pb.redirectErrorStream(true)

            val env = pb.environment()
            env.putAll(customEnv)
            val existingPath = env["PATH"] ?: "/system/bin:/system/xbin:/sbin:/vendor/bin"
            env["PATH"] = "${homeDir.absolutePath}:$existingPath"

            val process = pb.start()
            currentProcess = process

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    if (currentLine.startsWith("__TERMIDE_PWD__:")) {
                        val newPwd = currentLine.removePrefix("__TERMIDE_PWD__:").trim()
                        val newDir = File(newPwd)
                        if (newDir.exists() && newDir.isDirectory) {
                            workingDir = newDir.canonicalFile
                        }
                    } else {
                        appendLine(currentLine)
                        onOutputUpdate()
                    }
                }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                appendLine("[Exit code: $exitCode]")
            }
            isRunning.set(false)
            currentProcess = null
            onOutputUpdate()
            onComplete(exitCode)
        } catch (e: Exception) {
            isRunning.set(false)
            currentProcess = null
            appendLine("Error executing command: ${e.message}")
            onOutputUpdate()
            onComplete(1)
        }
    }

    fun sendStdinLine(input: String): Boolean {
        val proc = currentProcess
        return if (proc != null && proc.isAlive) {
            try {
                val writer = OutputStreamWriter(proc.outputStream)
                writer.write("$input\n")
                writer.flush()
                appendLine(input)
                true
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
    }

    fun navigateHistoryUp(): String? {
        if (commandHistory.isEmpty()) return null
        if (historyCursor > 0) {
            historyCursor--
        }
        return commandHistory.getOrNull(historyCursor)
    }

    fun navigateHistoryDown(): String {
        if (commandHistory.isEmpty()) return ""
        if (historyCursor < commandHistory.size - 1) {
            historyCursor++
            return commandHistory[historyCursor]
        } else {
            historyCursor = commandHistory.size
            return ""
        }
    }

    companion object {
        private const val MAX_BUFFER_CHARS = 64_000
        private val ANSI_REGEX = Regex("\u001B\\[[;\\d]*[ -/]*[@-~]")

        fun stripAnsiCodes(input: String): String {
            return ANSI_REGEX.replace(input, "")
        }
    }
}
