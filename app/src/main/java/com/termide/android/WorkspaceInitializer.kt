package com.termide.android

import java.io.File

data class WorkspaceSearchResult(
    val file: File,
    val lineNumber: Int,
    val linePreview: String
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
                # TermIDE Studio v2.0 - Welcome Script
                printf "\033[1;36m==========================================\033[0m\n"
                printf "\033[1;32m🚀 Welcome to TermIDE Studio v2.0!\033[0m\n"
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
                        version = "2.0.0",
                        features = listOf("ANSI Shell", "Multi-Tab Code Editor", "Split View", "File Explorer")
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
                # TermIDE Studio v2.0 (Android IDE & Terminal)

                ## What's New in v2.0
                1. **ANSI Color Terminal + Tab Auto-Completion**: Colored terminal output, clipboard copy (`📋`), and filename/command completion (`TAB`).
                2. **Multi-Tab Code Editor + Undo/Redo**: Open multiple files in tabs, Undo (`↶`) / Redo (`↷`), and smart auto-indentation.
                3. **Split-Screen Mode (`⬒ Split`)**: View and run terminal commands right underneath the Code Editor on one screen.
                4. **Global Workspace Search & Themes**: Search across all project files and switch between IDE color themes.
                """.trimIndent() + "\n"
            )
        }

        return helloScript
    }

    fun searchInWorkspace(rootDir: File, query: String, maxResults: Int = 50): List<WorkspaceSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || !rootDir.exists()) return emptyList()

        val results = mutableListOf<WorkspaceSearchResult>()
        rootDir.walkTopDown()
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
