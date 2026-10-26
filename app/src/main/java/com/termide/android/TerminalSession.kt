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

    private val rawOutputBuffer = StringBuilder()
    private var currentProcess: Process? = null
    val isRunning = AtomicBoolean(false)

    private val customEnv = mutableMapOf(
        "TERM" to "xterm-256color",
        "HOME" to homeDir.absolutePath,
        "COLORTERM" to "truecolor"
    )

    init {
        appendLine("\u001B[36m╔══════════════════════════════════════════════════╗\u001B[0m")
        appendLine("\u001B[36m║\u001B[0m  \u001B[1;32mTermIDE Studio v2.0\u001B[0m • Session #$id ($title)        \u001B[36m║\u001B[0m")
        appendLine("\u001B[36m║\u001B[0m  Type \u001B[33m'help'\u001B[0m for built-in IDE terminal commands  \u001B[36m║\u001B[0m")
        appendLine("\u001B[36m╚══════════════════════════════════════════════════╝\u001B[0m")
        appendLine("Working directory: \u001B[34m${formatPath(workingDir)}\u001B[0m")
        appendLine("")
    }

    fun getRawTranscript(): String = synchronized(rawOutputBuffer) {
        rawOutputBuffer.toString()
    }

    fun getTranscript(): String = synchronized(rawOutputBuffer) {
        stripAnsiCodes(rawOutputBuffer.toString())
    }

    fun appendText(text: String) {
        synchronized(rawOutputBuffer) {
            rawOutputBuffer.append(text)
            if (rawOutputBuffer.length > MAX_BUFFER_CHARS) {
                rawOutputBuffer.delete(0, rawOutputBuffer.length - MAX_BUFFER_CHARS)
            }
        }
    }

    fun appendLine(line: String) {
        appendText("$line\n")
    }

    fun clearTranscript() {
        synchronized(rawOutputBuffer) {
            rawOutputBuffer.clear()
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
            appendLine("\u001B[31m^C [Process interrupted]\u001B[0m")
            true
        } else {
            appendLine("\u001B[31m^C\u001B[0m")
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
                appendLine("\u001B[1;36mBuilt-in TermIDE Studio v2.0 Commands:\u001B[0m")
                appendLine("  \u001B[32mhelp\u001B[0m              Show this help message")
                appendLine("  \u001B[32mclear / cls\u001B[0m       Clear terminal screen")
                appendLine("  \u001B[32mcd <path>\u001B[0m         Change working directory (~ for workspace)")
                appendLine("  \u001B[32mpwd\u001B[0m               Print working directory")
                appendLine("  \u001B[32mexport KEY=VAL\u001B[0m    Set session environment variable")
                appendLine("  \u001B[32mhistory\u001B[0m           Show command history")
                appendLine("  \u001B[32msh <file.sh>\u001B[0m      Execute shell script in workspace")
                appendLine("")
                onOutputUpdate()
                onComplete(0)
                return
            }
            cmd == "history" -> {
                commandHistory.forEachIndexed { index, item ->
                    appendLine("  \u001B[90m${index + 1}\u001B[0m  $item")
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
                    appendLine("\u001B[31mcd: no such directory: $arg\u001B[0m")
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
                    appendLine("\u001B[31mexport: usage: export KEY=VALUE\u001B[0m")
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
                appendLine("\u001B[31m[Exit code: $exitCode]\u001B[0m")
            }
            isRunning.set(false)
            currentProcess = null
            onOutputUpdate()
            onComplete(exitCode)
        } catch (e: Exception) {
            isRunning.set(false)
            currentProcess = null
            appendLine("\u001B[31mError executing command: ${e.message}\u001B[0m")
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
