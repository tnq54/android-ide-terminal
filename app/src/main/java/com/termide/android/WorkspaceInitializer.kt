package com.termide.android

import java.io.File

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
                # TermIDE Studio - Welcome Script
                echo "=========================================="
                echo "🚀 Welcome to TermIDE Studio for Android!"
                echo "=========================================="
                echo "Date      : $(date)"
                echo "User      : $(whoami 2>/dev/null || id -un 2>/dev/null || echo 'android')"
                echo "Directory : $(pwd)"
                echo "Kernel    : $(uname -s -r -m)"
                echo ""
                echo "Listing workspace files:"
                ls -la
                echo "=========================================="
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
                echo "=== [1] KERNEL & ARCHITECTURE ==="
                uname -a
                echo ""
                echo "=== [2] ANDROID BUILD PROPERTIES ==="
                if command -v getprop >/dev/null 2>&1; then
                    echo "Device Model : $(getprop ro.product.model)"
                    echo "Brand        : $(getprop ro.product.brand)"
                    echo "Android Ver  : $(getprop ro.build.version.release) (SDK $(getprop ro.build.version.sdk))"
                    echo "ABI          : $(getprop ro.product.cpu.abi)"
                else
                    echo "Running in standard POSIX environment"
                fi
                echo ""
                echo "=== [3] MEMORY INFO ==="
                head -n 6 /proc/meminfo 2>/dev/null || free -h 2>/dev/null
                echo ""
                echo "=== [4] STORAGE USAGE ==="
                df -h . 2>/dev/null || df .
                """.trimIndent() + "\n"
            )
            sysInfoScript.setExecutable(true)
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
                        version = "1.0.0",
                        features = listOf("Interactive Shell", "Code Editor", "File Explorer")
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
                # TermIDE Studio (Android IDE & Terminal)

                ## Features
                1. **Terminal Emulator**: Multi-session POSIX/Android shell with extra key bar (`ESC`, `TAB`, `CTRL+C`, history arrows).
                2. **Code Editor**: Syntax highlighting, line numbers, quick code symbols, Find & Replace, and 1-click `▶ Run`.
                3. **File Manager**: Browse directories, create files/folders, toggle `+x` executable permissions, and open terminal in any folder.
                """.trimIndent() + "\n"
            )
        }

        return helloScript
    }
}
