package com.termide.android

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LocalFullStackServer(
    private val workspaceDir: File,
    private val commandRunner: (String) -> Pair<Int, String>
) {
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()
    var boundPort: Int = 8088
        private set

    fun start(preferredPort: Int = 8088): Int {
        if (isRunning.get()) return boundPort
        val portsToTry = listOf(preferredPort, 8089, 8090, 18080, 0)
        for (p in portsToTry) {
            try {
                val sock = ServerSocket(p, 50, InetAddress.getByName("127.0.0.1"))
                serverSocket = sock
                boundPort = sock.localPort
                isRunning.set(true)
                executor.execute { acceptLoop(sock) }
                return boundPort
            } catch (_: Exception) {
            }
        }
        return -1
    }

    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }

    fun getBaseUrl(): String = "http://127.0.0.1:$boundPort"

    private fun acceptLoop(sock: ServerSocket) {
        while (isRunning.get() && !sock.isClosed) {
            try {
                val client = sock.accept()
                executor.execute { handleClient(client) }
            } catch (_: Exception) {
                if (!isRunning.get()) break
            }
        }
    }

    private fun handleClient(client: Socket) {
        client.use { sock ->
            sock.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
            val out = sock.getOutputStream()

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val rawPath = parts[1]
            val pathOnly = URLDecoder.decode(rawPath.substringBefore('?'), "UTF-8")

            var contentLength = 0
            var headerLine: String?
            while (reader.readLine().also { headerLine = it } != null) {
                val h = headerLine ?: break
                if (h.isEmpty()) break
                if (h.lowercase().startsWith("content-length:")) {
                    contentLength = h.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }

            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength.coerceAtMost(1_048_576))
                var totalRead = 0
                while (totalRead < buf.size) {
                    val r = reader.read(buf, totalRead, buf.size - totalRead)
                    if (r == -1) break
                    totalRead += r
                }
                String(buf, 0, totalRead)
            } else {
                ""
            }

            if (method == "OPTIONS") {
                sendResponse(out, 200, "text/plain", "")
                return
            }

            when {
                pathOnly == "/api/status" -> {
                    val fileCount = workspaceDir.listFiles()?.size ?: 0
                    val json = """{"status":"online","server":"TermIDE Full-Stack Embedded Server v4.0","port":$boundPort,"workspace":"${escapeJson(workspaceDir.absolutePath)}","fileCount":$fileCount,"timestamp":${System.currentTimeMillis()}}"""
                    sendResponse(out, 200, "application/json; charset=utf-8", json)
                }

                pathOnly == "/api/files" -> {
                    val items = workspaceDir.listFiles()
                        ?.filter { it.name != ".termide_vcs" }
                        ?.sortedBy { it.name }
                        ?.joinToString(",") { f ->
                            """{"name":"${escapeJson(f.name)}","isDir":${f.isDirectory},"size":${f.length()},"executable":${f.canExecute()}}"""
                        } ?: ""
                    val resp = """{"files":[$items]}"""
                    sendResponse(out, 200, "application/json; charset=utf-8", resp)
                }

                pathOnly == "/api/exec" && method == "POST" -> {
                    val cmd = extractJsonField(body, "command") ?: body
                    val (exitCode, output) = commandRunner(cmd)
                    val resp = """{"exitCode":$exitCode,"output":"${escapeJson(output)}"}"""
                    sendResponse(out, 200, "application/json; charset=utf-8", resp)
                }

                pathOnly == "/api/save" && method == "POST" -> {
                    val rawName = extractJsonField(body, "name")
                    val content = extractJsonField(body, "content")
                    if (!rawName.isNullOrBlank() && content != null) {
                        val name = rawName.substringAfterLast('/')
                        val target = File(workspaceDir, name)
                        target.writeText(content)
                        if (target.extension.lowercase() == "sh") {
                            target.setExecutable(true)
                        }
                        val resp = """{"saved":true,"name":"${escapeJson(name)}"}"""
                        sendResponse(out, 200, "application/json; charset=utf-8", resp)
                    } else {
                        val err = """{"saved":false,"error":"Invalid request payload"}"""
                        sendResponse(out, 400, "application/json; charset=utf-8", err)
                    }
                }

                else -> {
                    val cleanRel = pathOnly.trimStart('/').ifEmpty { "index.html" }
                    val targetFile = File(workspaceDir, cleanRel).canonicalFile
                    if (targetFile.path.startsWith(workspaceDir.canonicalPath) &&
                        targetFile.exists() && targetFile.isFile
                    ) {
                        val mime = mimeTypeFor(targetFile.extension.lowercase())
                        sendByteResponse(out, 200, mime, targetFile.readBytes())
                    } else {
                        val notFound = """{"error":"Not found: ${escapeJson(pathOnly)}"}"""
                        sendResponse(out, 404, "application/json; charset=utf-8", notFound)
                    }
                }
            }
        }
    }

    companion object {
        fun escapeJson(value: String): String = buildString(value.length + 16) {
            for (ch in value) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }

        fun extractJsonField(json: String, key: String): String? {
            val regex = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
            val match = regex.find(json) ?: return null
            val raw = match.groupValues[1]
            return buildString(raw.length) {
                var i = 0
                while (i < raw.length) {
                    val c = raw[i]
                    if (c == '\\' && i + 1 < raw.length) {
                        when (val next = raw[i + 1]) {
                            'n' -> append('\n')
                            'r' -> append('\r')
                            't' -> append('\t')
                            '"' -> append('"')
                            '\\' -> append('\\')
                            else -> append(next)
                        }
                        i += 2
                    } else {
                        append(c)
                        i++
                    }
                }
            }
        }
    }

    private fun mimeTypeFor(ext: String): String = when (ext) {
        "html", "htm" -> "text/html; charset=utf-8"
        "js", "mjs" -> "application/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "text/plain; charset=utf-8"
    }

    private fun sendResponse(out: OutputStream, status: Int, contentType: String, body: String) {
        sendByteResponse(out, status, contentType, body.toByteArray(Charsets.UTF_8))
    }

    private fun sendByteResponse(out: OutputStream, status: Int, contentType: String, bytes: ByteArray) {
        val statusText = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "Error"
        }
        val headers = buildString {
            append("HTTP/1.1 $status $statusText\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(headers.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }
}
