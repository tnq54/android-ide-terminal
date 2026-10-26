package com.termide.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TerminalSessionTest {

    @Test
    fun testWorkspaceInitializerCreatesStarterFilesAndSearch() {
        val tempDir = Files.createTempDirectory("termide_ws_test").toFile()
        try {
            val helloFile = WorkspaceInitializer.ensureDefaultWorkspace(tempDir)
            assertTrue(helloFile.exists())
            assertTrue(File(tempDir, "sysinfo.sh").exists())
            assertTrue(File(tempDir, "benchmark.sh").exists())
            assertTrue(File(tempDir, "script.py").exists())
            assertTrue(File(tempDir, "Main.kt").exists())
            assertTrue(File(tempDir, "README.md").exists())

            val searchHits = WorkspaceInitializer.searchInWorkspace(tempDir, "Welcome to TermIDE")
            assertTrue(searchHits.isNotEmpty())
            assertEquals("hello.sh", searchHits.first().file.name)
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
    fun testEditorTabDocumentUndoRedo() {
        val dummyFile = File("/tmp/test_undo.sh")
        val doc = EditorTabDocument(dummyFile, "line 1\n")
        assertFalse(doc.canUndo())

        doc.onContentEdited("line 1\nline 2\n")
        assertTrue(doc.isModified)
        assertTrue(doc.canUndo())

        val undone = doc.performUndo()
        assertEquals("line 1\n", undone)
        assertTrue(doc.canRedo())

        val redone = doc.performRedo()
        assertEquals("line 1\nline 2\n", redone)
    }

    @Test
    fun testTabAutoCompletion() {
        val tempDir = Files.createTempDirectory("termide_tab_test").toFile()
        try {
            File(tempDir, "hello_world.sh").writeText("echo hi")
            val completed = AnsiParser.autoCompleteInput("sh hel", tempDir)
            assertEquals("sh hello_world.sh", completed)
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
