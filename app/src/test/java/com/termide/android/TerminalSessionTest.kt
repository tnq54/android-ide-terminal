package com.termide.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TerminalSessionTest {

    @Test
    fun testWorkspaceInitializerCreatesStarterFiles() {
        val tempDir = Files.createTempDirectory("termide_ws_test").toFile()
        try {
            val helloFile = WorkspaceInitializer.ensureDefaultWorkspace(tempDir)
            assertTrue(helloFile.exists())
            assertTrue(File(tempDir, "sysinfo.sh").exists())
            assertTrue(File(tempDir, "script.py").exists())
            assertTrue(File(tempDir, "Main.kt").exists())
            assertTrue(File(tempDir, "README.md").exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testTerminalSessionShellExecutionAndCdTracking() {
        val tempDir = Files.createTempDirectory("termide_shell_test").toFile()
        try {
            val subDir = File(tempDir, "projects").apply { mkdirs() }
            val session = TerminalSession(
                id = 1,
                title = "test",
                initialDir = tempDir,
                homeDir = tempDir
            )

            var lastExitCode = -1
            session.executeCommand(
                rawCommand = "echo 'Hello TermIDE' && cd projects",
                onOutputUpdate = {},
                onComplete = { code -> lastExitCode = code }
            )

            assertEquals(0, lastExitCode)
            assertTrue(session.getTranscript().contains("Hello TermIDE"))
            assertEquals(subDir.canonicalPath, session.workingDir.canonicalPath)
            assertEquals("~/projects", session.formatPath(session.workingDir))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testSyntaxHighlighterLanguageAndRunCommand() {
        assertEquals("SHELL", SyntaxHighlighter.detectLanguage("hello.sh"))
        assertEquals("PYTHON", SyntaxHighlighter.detectLanguage("script.py"))
        assertEquals("KOTLIN", SyntaxHighlighter.detectLanguage("Main.kt"))
        assertEquals("JAVA", SyntaxHighlighter.detectLanguage("App.java"))

        val shFile = File("/tmp/hello.sh")
        assertEquals("sh \"/tmp/hello.sh\"", SyntaxHighlighter.buildRunCommand(shFile))
    }

    @Test
    fun testStripAnsiCodes() {
        val raw = "\u001B[32mSUCCESS\u001B[0m: Build complete"
        assertEquals("SUCCESS: Build complete", TerminalSession.stripAnsiCodes(raw))
    }
}
