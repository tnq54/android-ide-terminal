package com.termide.android

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GitSourceControlManager {

    private const val SNAPSHOT_DIR_NAME = ".termide_vcs"

    fun hasSystemGit(workingDir: File): Boolean {
        return try {
            val shell = if (File("/system/bin/sh").exists()) "/system/bin/sh" else "sh"
            val proc = ProcessBuilder(shell, "-c", "command -v git").directory(workingDir).start()
            proc.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun runGitStatus(workingDir: File, sshManager: SshConnectionManager?): String {
        if (sshManager != null && sshManager.isConnected()) {
            val sb = StringBuilder()
            sb.appendLine("\u001B[1;36m=== [Remote SSH Git Status: ${sshManager.remoteWorkingDir}] ===\u001B[0m")
            sshManager.executeRemoteCommand("git status -sb 2>&1 || git status 2>&1") { line ->
                sb.appendLine(line)
            }
            return sb.toString()
        }

        if (hasSystemGit(workingDir) && File(workingDir, ".git").exists()) {
            return executeLocalGitCommand(workingDir, "git status -sb")
        }

        return computeLocalSnapshotStatus(workingDir)
    }

    fun runGitDiff(workingDir: File, sshManager: SshConnectionManager?): String {
        if (sshManager != null && sshManager.isConnected()) {
            val sb = StringBuilder()
            sb.appendLine("\u001B[1;36m=== [Remote SSH Git Diff] ===\u001B[0m")
            sshManager.executeRemoteCommand("git diff --color=always 2>&1") { line ->
                sb.appendLine(line)
            }
            return sb.toString().ifBlank { "No unstaged changes on remote repository." }
        }

        if (hasSystemGit(workingDir) && File(workingDir, ".git").exists()) {
            val out = executeLocalGitCommand(workingDir, "git diff")
            return colorizeUnifiedDiff(out.ifBlank { "No unstaged changes." })
        }

        return computeLocalSnapshotDiff(workingDir)
    }

    fun runGitCommit(workingDir: File, message: String, sshManager: SshConnectionManager?): String {
        val cleanMsg = message.replace("\"", "\\\"").ifBlank { "Update workspace files" }
        if (sshManager != null && sshManager.isConnected()) {
            val sb = StringBuilder()
            sshManager.executeRemoteCommand("git add -A && git commit -m \"$cleanMsg\" 2>&1") { line ->
                sb.appendLine(line)
            }
            return sb.toString()
        }

        if (hasSystemGit(workingDir)) {
            if (!File(workingDir, ".git").exists()) {
                executeLocalGitCommand(workingDir, "git init -b main || git init")
                executeLocalGitCommand(workingDir, "git config user.name \"TermIDE User\" && git config user.email \"user@termide.local\"")
            }
            return executeLocalGitCommand(workingDir, "git add -A && git commit -m \"$cleanMsg\"")
        }

        return saveLocalSnapshotCommit(workingDir, cleanMsg)
    }

    fun runGitLog(workingDir: File, sshManager: SshConnectionManager?): String {
        if (sshManager != null && sshManager.isConnected()) {
            val sb = StringBuilder()
            sb.appendLine("\u001B[1;36m=== [Remote SSH Git Log] ===\u001B[0m")
            sshManager.executeRemoteCommand("git log --oneline --graph --decorate -n 20 2>&1") { line ->
                sb.appendLine(line)
            }
            return sb.toString()
        }

        if (hasSystemGit(workingDir) && File(workingDir, ".git").exists()) {
            return executeLocalGitCommand(workingDir, "git log --oneline --graph -n 20")
        }

        val vcsDir = File(workingDir, SNAPSHOT_DIR_NAME)
        val logFile = File(vcsDir, "commits.log")
        return if (logFile.exists()) {
            "\u001B[1;36m=== [Workspace Commit History] ===\u001B[0m\n" + logFile.readText()
        } else {
            "No commits yet. Tap 'Commit' to create the initial snapshot."
        }
    }

    private fun executeLocalGitCommand(workingDir: File, cmd: String): String {
        return try {
            val shell = if (File("/system/bin/sh").exists()) "/system/bin/sh" else "sh"
            val pb = ProcessBuilder(shell, "-c", cmd)
            pb.directory(workingDir)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val out = BufferedReader(InputStreamReader(proc.inputStream)).readText()
            proc.waitFor()
            out.trimEnd()
        } catch (e: Exception) {
            "Error running git: ${e.message}"
        }
    }

    private fun listTrackableFiles(workingDir: File): List<File> {
        return workingDir.walkTopDown()
            .onEnter { it.name != ".git" && it.name != SNAPSHOT_DIR_NAME }
            .filter { it.isFile && it.length() < 512_000 }
            .toList()
            .sortedBy { it.relativeTo(workingDir).path }
    }

    private fun computeLocalSnapshotStatus(workingDir: File): String {
        val vcsHeadDir = File(workingDir, "$SNAPSHOT_DIR_NAME/head")
        val currentFiles = listTrackableFiles(workingDir)
        val sb = StringBuilder()
        sb.appendLine("\u001B[1;36m## main (TermIDE Workspace VCS)\u001B[0m")

        var changes = 0
        for (file in currentFiles) {
            val rel = file.relativeTo(workingDir).path
            val snapFile = File(vcsHeadDir, rel)
            when {
                !snapFile.exists() -> {
                    sb.appendLine("\u001B[32m?? $rel (untracked / new)\u001B[0m")
                    changes++
                }
                snapFile.readText() != file.readText() -> {
                    sb.appendLine("\u001B[33m M $rel (modified)\u001B[0m")
                    changes++
                }
            }
        }

        if (vcsHeadDir.exists()) {
            vcsHeadDir.walkTopDown().filter { it.isFile }.forEach { snapFile ->
                val rel = snapFile.relativeTo(vcsHeadDir).path
                val liveFile = File(workingDir, rel)
                if (!liveFile.exists()) {
                    sb.appendLine("\u001B[31m D $rel (deleted)\u001B[0m")
                    changes++
                }
            }
        }

        if (changes == 0) {
            sb.appendLine("\u001B[32m✓ Working tree clean (all files match latest commit)\u001B[0m")
        }
        return sb.toString().trimEnd()
    }

    private fun computeLocalSnapshotDiff(workingDir: File): String {
        val vcsHeadDir = File(workingDir, "$SNAPSHOT_DIR_NAME/head")
        val currentFiles = listTrackableFiles(workingDir)
        val sb = StringBuilder()

        for (file in currentFiles) {
            val rel = file.relativeTo(workingDir).path
            val snapFile = File(vcsHeadDir, rel)
            val newLines = file.readLines()
            val oldLines = if (snapFile.exists()) snapFile.readLines() else emptyList()

            if (newLines != oldLines) {
                sb.appendLine("\u001B[1;36mdiff --git a/$rel b/$rel\u001B[0m")
                oldLines.filterNot { it in newLines }.forEach { removed ->
                    sb.appendLine("\u001B[31m- $removed\u001B[0m")
                }
                newLines.filterNot { it in oldLines }.forEach { added ->
                    sb.appendLine("\u001B[32m+ $added\u001B[0m")
                }
                sb.appendLine("")
            }
        }

        return sb.toString().ifBlank { "\u001B[32mNo changes detected between working tree and latest commit.\u001B[0m" }
    }

    private fun saveLocalSnapshotCommit(workingDir: File, message: String): String {
        val vcsDir = File(workingDir, SNAPSHOT_DIR_NAME)
        val vcsHeadDir = File(vcsDir, "head")
        vcsHeadDir.deleteRecursively()
        vcsHeadDir.mkdirs()

        val files = listTrackableFiles(workingDir)
        for (file in files) {
            val rel = file.relativeTo(workingDir).path
            val dest = File(vcsHeadDir, rel)
            dest.parentFile?.mkdirs()
            file.copyTo(dest, overwrite = true)
        }

        val shortHash = Integer.toHexString((System.currentTimeMillis() and 0xFFFFFFF).toInt()).padStart(7, '0')
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val logEntry = "\u001B[33m$shortHash\u001B[0m ($dateStr) $message [${files.size} files]\n"
        val logFile = File(vcsDir, "commits.log")
        val existing = if (logFile.exists()) logFile.readText() else ""
        logFile.writeText(logEntry + existing)

        return "\u001B[32m[main $shortHash] $message\n ${files.size} files snapshotted.\u001B[0m"
    }

    private fun colorizeUnifiedDiff(rawDiff: String): String {
        return rawDiff.lines().joinToString("\n") { line ->
            when {
                line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") ->
                    "\u001B[1;36m$line\u001B[0m"
                line.startsWith("+") -> "\u001B[32m$line\u001B[0m"
                line.startsWith("-") -> "\u001B[31m$line\u001B[0m"
                line.startsWith("@@") -> "\u001B[35m$line\u001B[0m"
                else -> line
            }
        }
    }
}
