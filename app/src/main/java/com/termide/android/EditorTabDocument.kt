package com.termide.android

import java.io.File

class EditorTabDocument(
    var file: File,
    initialContent: String
) {
    var content: String = initialContent
    var isModified: Boolean = false
    var cursorPosition: Int = 0

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()

    init {
        undoStack.addLast(initialContent)
    }

    fun onContentEdited(newText: String) {
        if (newText == content) return
        content = newText
        isModified = true
        if (undoStack.isEmpty() || undoStack.last() != newText) {
            undoStack.addLast(newText)
            if (undoStack.size > MAX_HISTORY) {
                undoStack.removeFirst()
            }
        }
        redoStack.clear()
    }

    fun canUndo(): Boolean = undoStack.size > 1

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun performUndo(): String? {
        if (!canUndo()) return null
        val current = undoStack.removeLast()
        redoStack.addLast(current)
        val previous = undoStack.last()
        content = previous
        return previous
    }

    fun performRedo(): String? {
        if (!canRedo()) return null
        val next = redoStack.removeLast()
        undoStack.addLast(next)
        content = next
        return next
    }

    fun markSaved(savedContent: String) {
        content = savedContent
        isModified = false
    }

    companion object {
        private const val MAX_HISTORY = 40
    }
}
