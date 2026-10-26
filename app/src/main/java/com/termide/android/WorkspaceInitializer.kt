package com.termide.android

import java.io.File

data class WorkspaceSearchResult(
    val file: File,
    val lineNumber: Int,
    val linePreview: String
)

data class CodeSnippet(
    val title: String,
    val code: String
)

object WorkspaceInitializer {

    fun ensureDefaultWorkspace(workspaceDir: File): File {
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs()
        }

        val helloScript = File(workspaceDir, "hello.sh")
        if (!helloScript.exists()) {
            helloScript.writeText(
                """
                #!/system/bin/sh
                # TermIDE Studio v3.0 Full - Welcome Script
                printf "\033[1;36m==========================================\033[0m\n"
                printf "\033[1;32m🚀 Welcome to TermIDE Studio v3.0 Full!\033[0m\n"
                printf "\033[1;36m==========================================\033[0m\n"
                echo "Date      : $(date)"
                echo "User      : $(whoami 2>/dev/null || id -un 2>/dev/null || echo 'android')"
                echo "Directory : $(pwd)"
                echo "Kernel    : $(uname -s -r -m)"
                echo ""
                printf "\033[1;33mListing workspace files:\033[0m\n"
                ls -la
                printf "\033[1;36m==========================================\033[0m\n"
                """.trimIndent() + "\n"
            )
            helloScript.setExecutable(true)
        }

        val sysInfoScript = File(workspaceDir, "sysinfo.sh")
        if (!sysInfoScript.exists()) {
            sysInfoScript.writeText(
                """
                #!/system/bin/sh
                # System Diagnostics & Hardware Report
                printf "\033[1;32m=== [1] KERNEL & ARCHITECTURE ===\033[0m\n"
                uname -a
                echo ""
                printf "\033[1;32m=== [2] ANDROID BUILD PROPERTIES ===\033[0m\n"
                if command -v getprop >/dev/null 2>&1; then
                    echo "Device Model : $(getprop ro.product.model)"
                    echo "Brand        : $(getprop ro.product.brand)"
                    echo "Android Ver  : $(getprop ro.build.version.release) (SDK $(getprop ro.build.version.sdk))"
                    echo "ABI          : $(getprop ro.product.cpu.abi)"
                else
                    echo "Running in standard POSIX environment"
                fi
                echo ""
                printf "\033[1;32m=== [3] MEMORY INFO ===\033[0m\n"
                head -n 6 /proc/meminfo 2>/dev/null || free -h 2>/dev/null
                echo ""
                printf "\033[1;32m=== [4] STORAGE USAGE ===\033[0m\n"
                df -h . 2>/dev/null || df .
                """.trimIndent() + "\n"
            )
            sysInfoScript.setExecutable(true)
        }

        val benchmarkScript = File(workspaceDir, "benchmark.sh")
        if (!benchmarkScript.exists()) {
            benchmarkScript.writeText(
                """
                #!/system/bin/sh
                # Quick Shell Loop & I/O Benchmark
                printf "\033[1;35m⚡ Running TermIDE Quick Benchmark...\033[0m\n"
                START_TS=$(date +%s)
                i=0
                while [ ${'$'}i -lt 5000 ]; do
                    i=$((i + 1))
                done
                END_TS=$(date +%s)
                ELAPSED=$((END_TS - START_TS))
                printf "\033[1;32m✓ Completed 5,000 shell arithmetic iterations in %ds\033[0m\n" "${'$'}ELAPSED"
                """.trimIndent() + "\n"
            )
            benchmarkScript.setExecutable(true)
        }

        val webPreviewSample = File(workspaceDir, "index.html")
        if (!webPreviewSample.exists()) {
            webPreviewSample.writeText(
                """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>TermIDE Live Web Preview</title>
                    <style>
                        body { background:#0D1117; color:#E6EDF3; font-family:monospace; padding:18px; }
                        .card { background:#161B22; border:1px solid #30363D; border-radius:10px; padding:16px; }
                        h2 { color:#3FB950; margin-top:0; }
                        button { background:#238636; color:#fff; border:none; padding:10px 16px; border-radius:6px; font-weight:bold; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <h2>🚀 TermIDE Studio Web Preview</h2>
                        <p id="status">Tap the button below to test JavaScript execution:</p>
                        <button onclick="runDemo()">Run JS Action</button>
                    </div>
                    <script>
                        function runDemo() {
                            const now = new Date().toLocaleTimeString();
                            document.getElementById('status').innerText = '✅ JS Executed at ' + now;
                        }
                    </script>
                </body>
                </html>
                """.trimIndent() + "\n"
            )
        }

        val pythonSample = File(workspaceDir, "script.py")
        if (!pythonSample.exists()) {
            pythonSample.writeText(
                """
                # Sample Python Script in TermIDE Studio
                import os
                import platform

                def main():
                    print("Hello from Python in TermIDE!")
                    print(f"Platform: {platform.system()} {platform.release()}")
                    print(f"Files: {os.listdir('.')}")

                if __name__ == "__main__":
                    main()
                """.trimIndent() + "\n"
            )
        }

        val kotlinSample = File(workspaceDir, "Main.kt")
        if (!kotlinSample.exists()) {
            kotlinSample.writeText(
                """
                package workspace

                // Sample Kotlin Code in TermIDE Studio
                data class ProjectInfo(
                    val name: String,
                    val version: String,
                    val features: List<String>
                )

                fun main() {
                    val info = ProjectInfo(
                        name = "TermIDE Studio",
                        version = "3.0.0",
                        features = listOf("Local & SSH Shell", "Multi-Tab Editor", "Git VCS", "Live Web Preview")
                    )
                    println("Loaded: ${'$'}{info.name} v${'$'}{info.version}")
                }
                """.trimIndent() + "\n"
            )
        }

        val readmeFile = File(workspaceDir, "README.md")
        if (!readmeFile.exists()) {
            readmeFile.writeText(
                """
                # TermIDE Studio v3.0 Full Edition

                ## Complete Feature Suite
                1. **Terminal Emulator + Real SSH/SFTP Client**: Local Android shell + remote SSH server connection (`🌐 SSH`) with saved host profiles, ANSI 16/256 colors, and `TAB` auto-completion.
                2. **Multi-Tab Code Editor + Live Web Preview**: Open multiple files in tabs, Undo/Redo, Code Snippets (`🧩`), Split-Screen mode (`⬒ Split`), and built-in HTML/JS live preview (`🌐 Preview`).
                3. **Git & Source Control (`🌿 Git`)**: Built-in Git status, unified colored diff (`+`/`-`), commit snapshots, and commit log (works both with local/SSH git and offline snapshot VCS).
                4. **File Manager + SFTP Sync + Global Search**: Browse directories, search across all project files, and upload/download files over SFTP.
                """.trimIndent() + "\n"
            )
        }

        return helloScript
    }

    fun getCodeSnippets(): List<CodeSnippet> = listOf(
        CodeSnippet(
            "🐚 Shell: For Loop & Conditional",
            "#!/system/bin/sh\nfor item in *.sh; do\n    if [ -f \"\$item\" ]; then\n        echo \"Found script: \$item\"\n    fi\ndone\n"
        ),
        CodeSnippet(
            "🐚 Shell: Network Port & Ping Check",
            "#!/system/bin/sh\nHOST=\"8.8.8.8\"\nping -c 3 \"\$HOST\" 2>&1 || echo \"Ping unavailable\"\n"
        ),
        CodeSnippet(
            "🐍 Python: HTTP JSON Request",
            "import urllib.request\nimport json\n\nurl = 'https://httpbin.org/get'\nwith urllib.request.urlopen(url, timeout=5) as resp:\n    data = json.loads(resp.read().decode())\n    print(json.dumps(data, indent=2))\n"
        ),
        CodeSnippet(
            "🌐 HTML5: Responsive Dark Card Template",
            "<!DOCTYPE html>\n<html>\n<head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>\n<body style=\"background:#0D1117;color:#E6EDF3;font-family:sans-serif;padding:16px;\">\n  <h2>Hello from TermIDE</h2>\n</body>\n</html>\n"
        ),
        CodeSnippet(
            "🟣 Kotlin: Coroutine / Data Class Template",
            "data class TaskResult(val id: Int, val success: Boolean, val output: String)\n\nfun main() {\n    val res = TaskResult(1, true, \"OK\")\n    println(res)\n}\n"
        )
    )

    fun searchInWorkspace(rootDir: File, query: String, maxResults: Int = 50): List<WorkspaceSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || !rootDir.exists()) return emptyList()

        val results = mutableListOf<WorkspaceSearchResult>()
        rootDir.walkTopDown()
            .onEnter { it.name != ".git" && it.name != ".termide_vcs" }
            .filter { it.isFile && it.length() < 512_000 }
            .forEach { file ->
                if (results.size >= maxResults) return@forEach
                try {
                    file.useLines { lines ->
                        lines.forEachIndexed { idx, line ->
                            if (results.size < maxResults && line.contains(trimmed, ignoreCase = true)) {
                                results.add(
                                    WorkspaceSearchResult(
                                        file = file,
                                        lineNumber = idx + 1,
                                        linePreview = line.trim().take(80)
                                    )
                                )
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        return results
    }
}
