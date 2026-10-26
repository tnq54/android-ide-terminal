package com.termide.android

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Properties

data class SshProfile(
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String = "root",
    val password: String = "",
    val privateKey: String = ""
) {
    fun displayLabel(): String = "$name ($username@$host:$port)"

    fun serialize(): String {
        val parts = listOf(name, host, port.toString(), username, password, privateKey)
        return parts.joinToString("\u001F") { it.replace("\u001F", " ").replace("\u001E", " ") }
    }

    companion object {
        fun deserialize(raw: String): SshProfile? {
            val parts = raw.split("\u001F")
            if (parts.size < 4) return null
            return SshProfile(
                name = parts[0].ifBlank { parts[1] },
                host = parts[1],
                port = parts[2].toIntOrNull() ?: 22,
                username = parts[3].ifBlank { "root" },
                password = parts.getOrNull(4).orEmpty(),
                privateKey = parts.getOrNull(5).orEmpty()
            )
        }
    }
}

class SshConnectionManager {

    private var activeSession: Session? = null
    var activeProfile: SshProfile? = null
        private set
    var remoteWorkingDir: String = "~"
        private set

    fun isConnected(): Boolean {
        val s = activeSession
        return s != null && s.isConnected
    }

    fun connect(profile: SshProfile, timeoutMs: Int = 10_000): Result<String> {
        disconnect()
        return try {
            val jsch = JSch()
            if (profile.privateKey.isNotBlank()) {
                val keyBytes = profile.privateKey.trim().toByteArray(Charsets.UTF_8)
                jsch.addIdentity("termide-key", keyBytes, null, null)
            }

            val session = jsch.getSession(profile.username, profile.host, profile.port)
            if (profile.password.isNotBlank()) {
                session.setPassword(profile.password)
            }

            val config = Properties().apply {
                put("StrictHostKeyChecking", "no")
                put("PreferredAuthentications", "publickey,password,keyboard-interactive")
            }
            session.setConfig(config)
            session.connect(timeoutMs)

            activeSession = session
            activeProfile = profile
            remoteWorkingDir = queryRemoteHomeDir(session) ?: "/"
            Result.success("Connected to ${profile.username}@${profile.host}:${profile.port} (dir: $remoteWorkingDir)")
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }

    fun disconnect() {
        try {
            activeSession?.disconnect()
        } catch (_: Exception) {
        } finally {
            activeSession = null
            activeProfile = null
            remoteWorkingDir = "~"
        }
    }

    private fun queryRemoteHomeDir(session: Session): String? {
        var channel: ChannelExec? = null
        return try {
            channel = session.openChannel("exec") as ChannelExec
            channel.setCommand("pwd")
            val reader = BufferedReader(InputStreamReader(channel.inputStream))
            channel.connect(5000)
            val line = reader.readLine()?.trim()
            line
        } catch (_: Exception) {
            null
        } finally {
            channel?.disconnect()
        }
    }

    fun executeRemoteCommand(
        command: String,
        onLine: (String) -> Unit
    ): Int {
        val session = activeSession ?: throw IllegalStateException("SSH session is not connected")
        if (!session.isConnected) throw IllegalStateException("SSH session disconnected")

        var channel: ChannelExec? = null
        try {
            val cdPrefix = if (remoteWorkingDir.isNotBlank() && remoteWorkingDir != "~") {
                "cd \"$remoteWorkingDir\" 2>/dev/null || true\n"
            } else {
                ""
            }
            val wrapped = "${cdPrefix}$command\n__EC=\$?\necho \"__TERMIDE_PWD__:\$(pwd)\"\nexit \$__EC"

            channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(wrapped)
            channel.setErrStream(System.err, true)
            val input = channel.inputStream
            val extErr = channel.extInputStream
            channel.connect(8000)

            BufferedReader(InputStreamReader(input)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val current = line ?: continue
                    if (current.startsWith("__TERMIDE_PWD__:")) {
                        val newDir = current.removePrefix("__TERMIDE_PWD__:").trim()
                        if (newDir.isNotEmpty()) {
                            remoteWorkingDir = newDir
                        }
                    } else {
                        onLine(current)
                    }
                }
            }

            BufferedReader(InputStreamReader(extErr)).use { errReader ->
                var errLine: String?
                while (errReader.readLine().also { errLine = it } != null) {
                    val current = errLine ?: continue
                    onLine("\u001B[31m$current\u001B[0m")
                }
            }

            while (!channel.isClosed) {
                Thread.sleep(30)
            }
            return channel.exitStatus
        } finally {
            channel?.disconnect()
        }
    }

    fun uploadFileToRemote(localFile: File, remoteFileName: String = localFile.name): Result<String> {
        val session = activeSession ?: return Result.failure(IllegalStateException("Not connected to SSH"))
        var sftp: ChannelSftp? = null
        return try {
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(8000)
            val destDir = if (remoteWorkingDir.isNotBlank() && remoteWorkingDir != "~") remoteWorkingDir else sftp.pwd()
            val destPath = "${destDir.trimEnd('/')}/$remoteFileName"
            localFile.inputStream().use { input ->
                sftp.put(input, destPath)
            }
            Result.success(destPath)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            sftp?.disconnect()
        }
    }

    fun downloadFileFromRemote(remotePath: String, localDestFile: File): Result<File> {
        val session = activeSession ?: return Result.failure(IllegalStateException("Not connected to SSH"))
        var sftp: ChannelSftp? = null
        return try {
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect(8000)
            val resolvedPath = if (remotePath.startsWith("/")) {
                remotePath
            } else {
                val base = if (remoteWorkingDir.isNotBlank() && remoteWorkingDir != "~") remoteWorkingDir else sftp.pwd()
                "${base.trimEnd('/')}/$remotePath"
            }
            localDestFile.parentFile?.mkdirs()
            localDestFile.outputStream().use { out ->
                sftp.get(resolvedPath, out)
            }
            Result.success(localDestFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            sftp?.disconnect()
        }
    }
}
