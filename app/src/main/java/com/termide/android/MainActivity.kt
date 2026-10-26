package com.termide.android

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val ioExecutor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Workspace & State
    private lateinit var workspaceDir: File
    private lateinit var currentExplorerDir: File
    private var isApplyingSyntax: Boolean = false
    private var fontSizeSp: Float = 13f
    private var activeTabMode: TabMode = TabMode.TERMINAL
    private var isSplitMode: Boolean = false

    // Multi-Tab Editor State
    private val editorTabs = mutableListOf<EditorTabDocument>()
    private var activeEditorTabIndex: Int = 0

    // Terminal Sessions
    private val sessions = mutableListOf<TerminalSession>()
    private var activeSessionIndex = 0
    private var nextSessionId = 1

    // Root & Top Bar Views
    private lateinit var rootLayout: LinearLayout
    private lateinit var topHeaderBar: LinearLayout
    private lateinit var tvSubtitleStatus: TextView
    private lateinit var btnToggleSplit: TextView
    private lateinit var btnQuickRun: TextView
    private lateinit var btnQuickTools: TextView
    private lateinit var tabBtnTerminal: TextView
    private lateinit var tabBtnEditor: TextView
    private lateinit var tabBtnFiles: TextView

    // Panes
    private lateinit var paneTerminal: LinearLayout
    private lateinit var paneEditor: LinearLayout
    private lateinit var paneFiles: LinearLayout
    private lateinit var splitPaneDivider: View

    // Terminal Views
    private lateinit var sessionTabsContainer: LinearLayout
    private lateinit var btnCopyTerminal: TextView
    private lateinit var btnNewSession: TextView
    private lateinit var btnCloseSession: TextView
    private lateinit var terminalScrollView: ScrollView
    private lateinit var tvTerminalOutput: TextView
    private lateinit var terminalExtraKeysContainer: LinearLayout
    private lateinit var etCommandInput: EditText
    private lateinit var btnToggleKeyboard: TextView
    private lateinit var btnSendCommand: TextView

    // Editor Views
    private lateinit var editorTabsContainer: LinearLayout
    private lateinit var btnCloseEditorTab: TextView
    private lateinit var tvEditorFileName: TextView
    private lateinit var btnEditorUndo: TextView
    private lateinit var btnEditorRedo: TextView
    private lateinit var btnEditorNew: TextView
    private lateinit var btnEditorFind: TextView
    private lateinit var btnEditorSave: TextView
    private lateinit var btnEditorRun: TextView
    private lateinit var editorScrollView: ScrollView
    private lateinit var tvLineNumbers: TextView
    private lateinit var etCodeEditor: EditText
    private lateinit var editorSymbolsContainer: LinearLayout
    private lateinit var tvEditorStats: TextView
    private lateinit var tvEditorLang: TextView

    // File Explorer Views
    private lateinit var btnFileUp: TextView
    private lateinit var tvCurrentDirPath: TextView
    private lateinit var btnOpenTermInDir: TextView
    private lateinit var btnCreateFile: TextView
    private lateinit var btnCreateFolder: TextView
    private lateinit var btnGlobalSearch: TextView
    private lateinit var btnHomeWorkspace: TextView
    private lateinit var btnRefreshFiles: TextView
    private lateinit var lvFiles: ListView
    private lateinit var fileAdapter: FileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        workspaceDir = File(filesDir, "workspace")
        val starterScript = WorkspaceInitializer.ensureDefaultWorkspace(workspaceDir)
        currentExplorerDir = workspaceDir

        bindViews()
        setupWindowInsetsForMobile()
        setupNavigationTabs()
        setupTerminalUi()
        setupEditorUi()
        setupFileExplorerUi()
        restoreSavedPreferences()

        // Create initial terminal session
        createNewTerminalSession(workspaceDir)
        // Pre-open starter files in multi-tab editor
        openFileInEditor(starterScript, switchToEditorTab = false)
        val sysInfo = File(workspaceDir, "sysinfo.sh")
        if (sysInfo.exists()) {
            openFileInEditor(sysInfo, switchToEditorTab = false)
            switchToEditorTab(0)
        }
        // Load file explorer list
        refreshFileExplorer()
    }

    private fun bindViews() {
        rootLayout = findViewById(R.id.rootLayout)
        topHeaderBar = findViewById(R.id.topHeaderBar)
        tvSubtitleStatus = findViewById(R.id.tvSubtitleStatus)
        btnToggleSplit = findViewById(R.id.btnToggleSplit)
        btnQuickRun = findViewById(R.id.btnQuickRun)
        btnQuickTools = findViewById(R.id.btnQuickTools)

        tabBtnTerminal = findViewById(R.id.tabBtnTerminal)
        tabBtnEditor = findViewById(R.id.tabBtnEditor)
        tabBtnFiles = findViewById(R.id.tabBtnFiles)

        paneTerminal = findViewById(R.id.paneTerminal)
        paneEditor = findViewById(R.id.paneEditor)
        paneFiles = findViewById(R.id.paneFiles)
        splitPaneDivider = findViewById(R.id.splitPaneDivider)

        sessionTabsContainer = findViewById(R.id.sessionTabsContainer)
        btnCopyTerminal = findViewById(R.id.btnCopyTerminal)
        btnNewSession = findViewById(R.id.btnNewSession)
        btnCloseSession = findViewById(R.id.btnCloseSession)
        terminalScrollView = findViewById(R.id.terminalScrollView)
        tvTerminalOutput = findViewById(R.id.tvTerminalOutput)
        terminalExtraKeysContainer = findViewById(R.id.terminalExtraKeysContainer)
        etCommandInput = findViewById(R.id.etCommandInput)
        btnToggleKeyboard = findViewById(R.id.btnToggleKeyboard)
        btnSendCommand = findViewById(R.id.btnSendCommand)

        editorTabsContainer = findViewById(R.id.editorTabsContainer)
        btnCloseEditorTab = findViewById(R.id.btnCloseEditorTab)
        tvEditorFileName = findViewById(R.id.tvEditorFileName)
        btnEditorUndo = findViewById(R.id.btnEditorUndo)
        btnEditorRedo = findViewById(R.id.btnEditorRedo)
        btnEditorNew = findViewById(R.id.btnEditorNew)
        btnEditorFind = findViewById(R.id.btnEditorFind)
        btnEditorSave = findViewById(R.id.btnEditorSave)
        btnEditorRun = findViewById(R.id.btnEditorRun)
        editorScrollView = findViewById(R.id.editorScrollView)
        tvLineNumbers = findViewById(R.id.tvLineNumbers)
        etCodeEditor = findViewById(R.id.etCodeEditor)
        editorSymbolsContainer = findViewById(R.id.editorSymbolsContainer)
        tvEditorStats = findViewById(R.id.tvEditorStats)
        tvEditorLang = findViewById(R.id.tvEditorLang)

        btnFileUp = findViewById(R.id.btnFileUp)
        tvCurrentDirPath = findViewById(R.id.tvCurrentDirPath)
        btnOpenTermInDir = findViewById(R.id.btnOpenTermInDir)
        btnCreateFile = findViewById(R.id.btnCreateFile)
        btnCreateFolder = findViewById(R.id.btnCreateFolder)
        btnGlobalSearch = findViewById(R.id.btnGlobalSearch)
        btnHomeWorkspace = findViewById(R.id.btnHomeWorkspace)
        btnRefreshFiles = findViewById(R.id.btnRefreshFiles)
        lvFiles = findViewById(R.id.lvFiles)
    }

    private fun setupWindowInsetsForMobile() {
        rootLayout.setOnApplyWindowInsetsListener { view, insets ->
            var topInset = 0
            var bottomInset = 0
            var leftInset = 0
            var rightInset = 0
            var imeVisible = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sysBars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                val imeInsets = insets.getInsets(WindowInsets.Type.ime())
                imeVisible = insets.isVisible(WindowInsets.Type.ime())
                topInset = sysBars.top
                leftInset = sysBars.left
                rightInset = sysBars.right
                bottomInset = maxOf(sysBars.bottom, imeInsets.bottom)
            } else {
                @Suppress("DEPRECATION")
                run {
                    topInset = insets.systemWindowInsetTop
                    leftInset = insets.systemWindowInsetLeft
                    rightInset = insets.systemWindowInsetRight
                    bottomInset = insets.systemWindowInsetBottom
                    imeVisible = bottomInset > dp(120)
                }
            }

            view.setPadding(leftInset, topInset, rightInset, bottomInset)

            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            topHeaderBar.visibility = if (imeVisible && isLandscape) View.GONE else View.VISIBLE

            if (imeVisible && (activeTabMode == TabMode.TERMINAL || isSplitMode)) {
                terminalScrollView.post {
                    terminalScrollView.fullScroll(View.FOCUS_DOWN)
                }
            }

            insets
        }
    }

    private fun setupNavigationTabs() {
        tabBtnTerminal.setOnClickListener {
            isSplitMode = false
            switchTab(TabMode.TERMINAL)
        }
        tabBtnEditor.setOnClickListener {
            isSplitMode = false
            switchTab(TabMode.EDITOR)
        }
        tabBtnFiles.setOnClickListener {
            isSplitMode = false
            hideSoftKeyboard()
            refreshFileExplorer()
            switchTab(TabMode.FILES)
        }

        btnToggleSplit.setOnClickListener {
            isSplitMode = !isSplitMode
            applyPaneVisibility()
            Toast.makeText(
                this,
                if (isSplitMode) "Đã bật chế độ Chia đôi (Editor + Terminal)" else "Đã tắt chế độ Chia đôi",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnQuickRun.setOnClickListener {
            runCurrentEditorFileInTerminal()
        }

        btnQuickTools.setOnClickListener {
            showQuickToolsDialog()
        }
    }

    private fun switchTab(mode: TabMode) {
        activeTabMode = mode
        applyPaneVisibility()
        updateTopSubtitle()
    }

    private fun applyPaneVisibility() {
        btnToggleSplit.setBackgroundResource(
            if (isSplitMode) R.drawable.bg_button_primary else R.drawable.bg_button_surface
        )
        btnToggleSplit.setTextColor(
            if (isSplitMode) 0xFFFFFFFF.toInt() else 0xFF79C0FF.toInt()
        )

        if (isSplitMode) {
            paneEditor.visibility = View.VISIBLE
            splitPaneDivider.visibility = View.VISIBLE
            paneTerminal.visibility = View.VISIBLE
            paneFiles.visibility = View.GONE

            (paneEditor.layoutParams as? LinearLayout.LayoutParams)?.weight = 1.15f
            (paneTerminal.layoutParams as? LinearLayout.LayoutParams)?.weight = 0.85f
            paneEditor.requestLayout()
            paneTerminal.requestLayout()

            styleTabButton(tabBtnTerminal, true)
            styleTabButton(tabBtnEditor, true)
            styleTabButton(tabBtnFiles, false)
        } else {
            splitPaneDivider.visibility = View.GONE
            paneTerminal.visibility = if (activeTabMode == TabMode.TERMINAL) View.VISIBLE else View.GONE
            paneEditor.visibility = if (activeTabMode == TabMode.EDITOR) View.VISIBLE else View.GONE
            paneFiles.visibility = if (activeTabMode == TabMode.FILES) View.VISIBLE else View.GONE

            (paneEditor.layoutParams as? LinearLayout.LayoutParams)?.weight = 1f
            (paneTerminal.layoutParams as? LinearLayout.LayoutParams)?.weight = 1f

            styleTabButton(tabBtnTerminal, activeTabMode == TabMode.TERMINAL)
            styleTabButton(tabBtnEditor, activeTabMode == TabMode.EDITOR)
            styleTabButton(tabBtnFiles, activeTabMode == TabMode.FILES)
        }
    }

    private fun styleTabButton(tabView: TextView, isSelected: Boolean) {
        tabView.setBackgroundResource(
            if (isSelected) R.drawable.bg_button_primary else R.drawable.bg_button_surface
        )
        tabView.setTextColor(
            if (isSelected) 0xFFE6EDF3.toInt() else 0xFF8B949E.toInt()
        )
        tabView.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
    }

    // ==================== TERMINAL LOGIC ====================

    private fun setupTerminalUi() {
        btnCopyTerminal.setOnClickListener {
            val transcript = currentSession()?.getTranscript().orEmpty()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("TermIDE Transcript", transcript))
            Toast.makeText(this, "Đã sao chép nội dung Terminal vào Clipboard", Toast.LENGTH_SHORT).show()
        }

        btnNewSession.setOnClickListener {
            createNewTerminalSession(currentSession()?.workingDir ?: workspaceDir)
        }

        btnCloseSession.setOnClickListener {
            closeCurrentTerminalSession()
        }

        btnSendCommand.setOnClickListener {
            submitTerminalInput()
        }

        btnToggleKeyboard.setOnClickListener {
            etCommandInput.requestFocus()
            toggleSoftKeyboard(etCommandInput)
        }

        tvTerminalOutput.setOnClickListener {
            etCommandInput.requestFocus()
        }

        etCommandInput.setOnEditorActionListener { _, actionId, event ->
            val isEnter = event != null &&
                event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                isEnter
            ) {
                submitTerminalInput()
                true
            } else {
                false
            }
        }

        populateTerminalExtraKeys()
    }

    private fun populateTerminalExtraKeys() {
        terminalExtraKeysContainer.removeAllViews()
        val keys = listOf(
            "CTRL+C" to {
                currentSession()?.interruptCurrentProcess()
                renderTerminalTranscript()
            },
            "TAB" to {
                val session = currentSession()
                if (session != null) {
                    val current = etCommandInput.text.toString()
                    val completed = AnsiParser.autoCompleteInput(current, session.workingDir)
                    etCommandInput.setText(completed)
                    etCommandInput.setSelection(etCommandInput.text.length)
                }
            },
            "CLEAR" to {
                currentSession()?.clearTranscript()
                renderTerminalTranscript()
            },
            "↑" to {
                val prev = currentSession()?.navigateHistoryUp()
                if (prev != null) {
                    etCommandInput.setText(prev)
                    etCommandInput.setSelection(etCommandInput.text.length)
                }
            },
            "↓" to {
                val next = currentSession()?.navigateHistoryDown() ?: ""
                etCommandInput.setText(next)
                etCommandInput.setSelection(etCommandInput.text.length)
            },
            "/" to { insertIntoCommandInput("/") },
            "-" to { insertIntoCommandInput("-") },
            "|" to { insertIntoCommandInput(" | ") },
            "~" to { insertIntoCommandInput("~") },
            "$" to { insertIntoCommandInput("$") },
            "&&" to { insertIntoCommandInput(" && ") },
            ">" to { insertIntoCommandInput(" > ") },
            ".." to { executeCommandInActiveSession("cd .. && pwd") },
            "ls -la" to { executeCommandInActiveSession("ls -la") },
            "pwd" to { executeCommandInActiveSession("pwd") }
        )

        for ((label, action) in keys) {
            val chip = makeKeyChipButton(label) { action() }
            terminalExtraKeysContainer.addView(chip)
        }
    }

    private fun insertIntoCommandInput(text: String) {
        val start = etCommandInput.selectionStart.coerceAtLeast(0)
        val end = etCommandInput.selectionEnd.coerceAtLeast(0)
        etCommandInput.text.replace(minOf(start, end), maxOf(start, end), text)
        etCommandInput.requestFocus()
    }

    private fun createNewTerminalSession(startDir: File) {
        val id = nextSessionId++
        val session = TerminalSession(
            id = id,
            title = "sh-$id",
            initialDir = startDir,
            homeDir = workspaceDir
        )
        sessions.add(session)
        activeSessionIndex = sessions.lastIndex
        rebuildSessionTabs()
        renderTerminalTranscript()
        updateTopSubtitle()
    }

    private fun closeCurrentTerminalSession() {
        if (sessions.size <= 1) {
            currentSession()?.clearTranscript()
            renderTerminalTranscript()
            Toast.makeText(this, "Đã xóa màn hình phiên hiện tại", Toast.LENGTH_SHORT).show()
            return
        }
        val removed = sessions.removeAt(activeSessionIndex)
        removed.interruptCurrentProcess()
        if (activeSessionIndex >= sessions.size) {
            activeSessionIndex = sessions.lastIndex
        }
        rebuildSessionTabs()
        renderTerminalTranscript()
        updateTopSubtitle()
    }

    private fun rebuildSessionTabs() {
        sessionTabsContainer.removeAllViews()
        sessions.forEachIndexed { index, session ->
            val tabChip = TextView(this).apply {
                val active = index == activeSessionIndex
                text = "#${session.id} ${session.title}"
                includeFontPadding = false
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setSingleLine(true)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(Typeface.MONOSPACE, if (active) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (active) 0xFFE6EDF3.toInt() else 0xFF8B949E.toInt())
                setBackgroundResource(
                    if (active) R.drawable.bg_button_primary else R.drawable.bg_button_surface
                )
                setPadding(dp(10), 0, dp(10), 0)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(28)
                )
                lp.marginEnd = dp(6)
                layoutParams = lp
                setOnClickListener {
                    activeSessionIndex = index
                    rebuildSessionTabs()
                    renderTerminalTranscript()
                    updateTopSubtitle()
                }
            }
            sessionTabsContainer.addView(tabChip)
        }
    }

    private fun currentSession(): TerminalSession? = sessions.getOrNull(activeSessionIndex)

    private fun submitTerminalInput() {
        val cmd = etCommandInput.text.toString()
        etCommandInput.setText("")
        executeCommandInActiveSession(cmd)
    }

    private fun executeCommandInActiveSession(command: String) {
        val session = currentSession() ?: return
        if (session.isRunning.get()) {
            val sent = session.sendStdinLine(command)
            if (sent) {
                renderTerminalTranscript()
                return
            }
        }

        ioExecutor.execute {
            session.executeCommand(
                rawCommand = command,
                onOutputUpdate = {
                    mainHandler.post {
                        renderTerminalTranscript()
                        updateTopSubtitle()
                    }
                },
                onComplete = {
                    mainHandler.post {
                        renderTerminalTranscript()
                        updateTopSubtitle()
                        refreshFileExplorer()
                    }
                }
            )
        }
    }

    private fun renderTerminalTranscript() {
        val session = currentSession() ?: return
        tvTerminalOutput.text = AnsiParser.parseToSpannable(session.getRawTranscript())
        terminalScrollView.post {
            terminalScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    // ==================== MULTI-TAB CODE EDITOR LOGIC ====================

    private fun currentEditorDoc(): EditorTabDocument? = editorTabs.getOrNull(activeEditorTabIndex)

    private fun setupEditorUi() {
        populateEditorSymbolBar()

        etCodeEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s == null || isApplyingSyntax) return
                val doc = currentEditorDoc()
                doc?.onContentEdited(s.toString())
                doc?.cursorPosition = etCodeEditor.selectionStart.coerceAtLeast(0)
                updateEditorHeaderTitle()
                rebuildEditorTabsBar()
                updateLineNumbersAndStats(s)

                isApplyingSyntax = true
                SyntaxHighlighter.applyHighlighting(s)
                isApplyingSyntax = false
            }
        })

        btnEditorUndo.setOnClickListener {
            val doc = currentEditorDoc() ?: return@setOnClickListener
            val prev = doc.performUndo()
            if (prev != null) {
                applyDocumentContentToEditor(doc)
            } else {
                Toast.makeText(this, "Không còn bước hoàn tác (Undo)", Toast.LENGTH_SHORT).show()
            }
        }

        btnEditorRedo.setOnClickListener {
            val doc = currentEditorDoc() ?: return@setOnClickListener
            val next = doc.performRedo()
            if (next != null) {
                applyDocumentContentToEditor(doc)
            } else {
                Toast.makeText(this, "Không còn bước làm lại (Redo)", Toast.LENGTH_SHORT).show()
            }
        }

        btnCloseEditorTab.setOnClickListener {
            closeCurrentEditorTab()
        }

        btnEditorSave.setOnClickListener {
            saveCurrentEditorFile(showToast = true)
        }

        btnEditorRun.setOnClickListener {
            runCurrentEditorFileInTerminal()
        }

        btnEditorNew.setOnClickListener {
            promptCreateNewFile(openImmediately = true)
        }

        btnEditorFind.setOnClickListener {
            showFindReplaceDialog()
        }
    }

    private fun populateEditorSymbolBar() {
        editorSymbolsContainer.removeAllViews()
        val symbols = listOf(
            "TAB" to "    ",
            "{" to "{",
            "}" to "}",
            "(" to "(",
            ")" to ")",
            "[" to "[",
            "]" to "]",
            ";" to ";",
            "\"" to "\"",
            "'" to "'",
            "=" to " = ",
            "$" to "$",
            "#" to "# ",
            "|" to " | ",
            "->" to " -> ",
            "!/" to "#!/system/bin/sh\n"
        )
        for ((label, insertText) in symbols) {
            val chip = makeKeyChipButton(label) {
                val start = etCodeEditor.selectionStart.coerceAtLeast(0)
                val end = etCodeEditor.selectionEnd.coerceAtLeast(0)
                etCodeEditor.text.replace(minOf(start, end), maxOf(start, end), insertText)
                etCodeEditor.requestFocus()
            }
            editorSymbolsContainer.addView(chip)
        }
    }

    private fun openFileInEditor(file: File, switchToEditorTab: Boolean, highlightLine: Int? = null) {
        try {
            // Save cursor of current tab
            currentEditorDoc()?.cursorPosition = etCodeEditor.selectionStart.coerceAtLeast(0)

            val existingIdx = editorTabs.indexOfFirst { it.file.canonicalPath == file.canonicalPath }
            if (existingIdx >= 0) {
                switchToEditorTab(existingIdx)
            } else {
                val content = if (file.exists() && file.isFile) file.readText() else ""
                val newDoc = EditorTabDocument(file, content)
                editorTabs.add(newDoc)
                switchToEditorTab(editorTabs.lastIndex)
            }

            if (switchToEditorTab && !isSplitMode) {
                switchTab(TabMode.EDITOR)
            }

            if (highlightLine != null && highlightLine > 1) {
                jumpToEditorLine(highlightLine)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể mở file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchToEditorTab(index: Int) {
        if (index !in editorTabs.indices) return
        activeEditorTabIndex = index
        val doc = editorTabs[index]
        applyDocumentContentToEditor(doc)
        rebuildEditorTabsBar()
    }

    private fun applyDocumentContentToEditor(doc: EditorTabDocument) {
        isApplyingSyntax = true
        etCodeEditor.setText(doc.content)
        etCodeEditor.text?.let { SyntaxHighlighter.applyHighlighting(it) }
        val safeCursor = doc.cursorPosition.coerceIn(0, etCodeEditor.text.length)
        etCodeEditor.setSelection(safeCursor)
        isApplyingSyntax = false

        updateEditorHeaderTitle()
        etCodeEditor.text?.let { updateLineNumbersAndStats(it) }
        tvEditorLang.text = "${SyntaxHighlighter.detectLanguage(doc.file.name)} • UTF-8"
    }

    private fun closeCurrentEditorTab() {
        if (editorTabs.size <= 1) {
            Toast.makeText(this, "Giữ ít nhất 1 tab trong trình sửa code", Toast.LENGTH_SHORT).show()
            return
        }
        editorTabs.removeAt(activeEditorTabIndex)
        if (activeEditorTabIndex >= editorTabs.size) {
            activeEditorTabIndex = editorTabs.lastIndex
        }
        switchToEditorTab(activeEditorTabIndex)
    }

    private fun rebuildEditorTabsBar() {
        editorTabsContainer.removeAllViews()
        editorTabs.forEachIndexed { index, doc ->
            val active = index == activeEditorTabIndex
            val label = if (doc.isModified) "${doc.file.name} ●" else doc.file.name
            val tabChip = TextView(this).apply {
                text = label
                includeFontPadding = false
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setSingleLine(true)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(Typeface.MONOSPACE, if (active) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(
                    when {
                        active -> 0xFFFFFFFF.toInt()
                        doc.isModified -> 0xFFFFA657.toInt()
                        else -> 0xFF8B949E.toInt()
                    }
                )
                setBackgroundResource(
                    if (active) R.drawable.bg_button_primary else R.drawable.bg_button_surface
                )
                setPadding(dp(10), 0, dp(10), 0)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(26)
                )
                lp.marginEnd = dp(5)
                layoutParams = lp
                setOnClickListener {
                    currentEditorDoc()?.cursorPosition = etCodeEditor.selectionStart.coerceAtLeast(0)
                    switchToEditorTab(index)
                }
            }
            editorTabsContainer.addView(tabChip)
        }
    }

    private fun jumpToEditorLine(targetLine: Int) {
        val text = etCodeEditor.text.toString()
        var currentLine = 1
        var charIndex = 0
        while (charIndex < text.length && currentLine < targetLine) {
            if (text[charIndex] == '\n') {
                currentLine++
            }
            charIndex++
        }
        val safeIdx = charIndex.coerceIn(0, text.length)
        etCodeEditor.requestFocus()
        etCodeEditor.setSelection(safeIdx)
    }

    private fun saveCurrentEditorFile(showToast: Boolean): File? {
        val doc = currentEditorDoc() ?: return null
        val target = doc.file
        return try {
            target.parentFile?.mkdirs()
            val textToSave = etCodeEditor.text.toString()
            target.writeText(textToSave)
            if (target.extension.lowercase() in listOf("sh", "bash")) {
                target.setExecutable(true)
            }
            doc.markSaved(textToSave)
            updateEditorHeaderTitle()
            rebuildEditorTabsBar()
            refreshFileExplorer()
            if (showToast) {
                Toast.makeText(this, "Đã lưu ${target.name}", Toast.LENGTH_SHORT).show()
            }
            target
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi khi lưu: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun runCurrentEditorFileInTerminal() {
        val savedFile = saveCurrentEditorFile(showToast = false) ?: return
        hideSoftKeyboard()
        val session = currentSession()
        if (session != null && savedFile.parentFile != null) {
            session.changeDirectory(savedFile.parentFile!!)
        }
        val runCmd = SyntaxHighlighter.buildRunCommand(savedFile)
        if (!isSplitMode) {
            switchTab(TabMode.TERMINAL)
        }
        executeCommandInActiveSession(runCmd)
    }

    private fun updateEditorHeaderTitle() {
        val doc = currentEditorDoc()
        val name = doc?.file?.name ?: "untitled.sh"
        tvEditorFileName.text = if (doc?.isModified == true) "$name ●" else name
        updateTopSubtitle()
    }

    private fun updateLineNumbersAndStats(editable: Editable) {
        val text = editable.toString()
        val lineCount = text.count { it == '\n' } + 1
        val sb = StringBuilder()
        for (i in 1..lineCount) {
            sb.append(i)
            if (i < lineCount) sb.append('\n')
        }
        tvLineNumbers.text = sb.toString()

        val cursor = etCodeEditor.selectionStart.coerceAtLeast(0).coerceAtMost(text.length)
        val beforeCursor = text.substring(0, cursor)
        val currentLine = beforeCursor.count { it == '\n' } + 1
        val lastNewline = beforeCursor.lastIndexOf('\n')
        val currentCol = if (lastNewline == -1) cursor + 1 else cursor - lastNewline
        tvEditorStats.text = "Ln $currentLine, Col $currentCol  |  ${text.length} chars ($lineCount lines)"
    }

    private fun showFindReplaceDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        val etFind = EditText(this).apply {
            hint = "Tìm chuỗi..."
            setSingleLine(true)
        }
        val etReplace = EditText(this).apply {
            hint = "Thay thế bằng (để trống nếu chỉ tìm)..."
            setSingleLine(true)
        }
        container.addView(etFind)
        container.addView(etReplace)

        AlertDialog.Builder(this)
            .setTitle("Tìm & Thay thế trong File")
            .setView(container)
            .setPositiveButton("Thay thế tất cả") { _, _ ->
                val findStr = etFind.text.toString()
                val replaceStr = etReplace.text.toString()
                if (findStr.isNotEmpty()) {
                    val current = etCodeEditor.text.toString()
                    val occurrences = current.split(findStr).size - 1
                    if (occurrences > 0) {
                        etCodeEditor.setText(current.replace(findStr, replaceStr))
                        Toast.makeText(this, "Đã thay thế $occurrences vị trí", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Không tìm thấy '$findStr'", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNeutralButton("Tìm vị trí đầu") { _, _ ->
                val findStr = etFind.text.toString()
                if (findStr.isNotEmpty()) {
                    val idx = etCodeEditor.text.toString().indexOf(findStr)
                    if (idx >= 0) {
                        etCodeEditor.requestFocus()
                        etCodeEditor.setSelection(idx, idx + findStr.length)
                    } else {
                        Toast.makeText(this, "Không tìm thấy '$findStr'", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    // ==================== FILE EXPLORER & GLOBAL SEARCH LOGIC ====================

    private fun setupFileExplorerUi() {
        fileAdapter = FileAdapter(this, emptyList()) { file, anchorView ->
            showFileActionMenu(file, anchorView)
        }
        lvFiles.adapter = fileAdapter

        lvFiles.setOnItemClickListener { _, _, position, _ ->
            val clicked = fileAdapter.getItem(position)
            if (clicked.isDirectory) {
                currentExplorerDir = clicked
                refreshFileExplorer()
            } else {
                openFileInEditor(clicked, switchToEditorTab = true)
            }
        }

        lvFiles.setOnItemLongClickListener { _, view, position, _ ->
            val clicked = fileAdapter.getItem(position)
            showFileActionMenu(clicked, view)
            true
        }

        btnFileUp.setOnClickListener {
            val parent = currentExplorerDir.parentFile
            if (parent != null && parent.canRead()) {
                currentExplorerDir = parent
                refreshFileExplorer()
            } else {
                Toast.makeText(this, "Đã ở thư mục gốc có quyền đọc", Toast.LENGTH_SHORT).show()
            }
        }

        btnHomeWorkspace.setOnClickListener {
            currentExplorerDir = workspaceDir
            refreshFileExplorer()
        }

        btnRefreshFiles.setOnClickListener {
            refreshFileExplorer()
        }

        btnCreateFile.setOnClickListener {
            promptCreateNewFile(openImmediately = true)
        }

        btnCreateFolder.setOnClickListener {
            promptCreateNewFolder()
        }

        btnGlobalSearch.setOnClickListener {
            showGlobalWorkspaceSearchDialog()
        }

        btnOpenTermInDir.setOnClickListener {
            currentSession()?.changeDirectory(currentExplorerDir)
            isSplitMode = false
            switchTab(TabMode.TERMINAL)
            executeCommandInActiveSession("pwd && ls -la")
        }
    }

    private fun showGlobalWorkspaceSearchDialog() {
        val input = EditText(this).apply {
            hint = "Nhập từ khóa cần tìm trong toàn bộ Workspace..."
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("🔎 Tìm kiếm toàn bộ Project")
            .setView(input)
            .setPositiveButton("Tìm kiếm") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    val matches = WorkspaceInitializer.searchInWorkspace(workspaceDir, query)
                    if (matches.isEmpty()) {
                        Toast.makeText(this, "Không tìm thấy '$query' trong Workspace", Toast.LENGTH_SHORT).show()
                    } else {
                        val items = matches.map {
                            "${it.file.name}:${it.lineNumber}  →  ${it.linePreview}"
                        }.toTypedArray()
                        AlertDialog.Builder(this)
                            .setTitle("Kết quả cho '$query' (${matches.size})")
                            .setItems(items) { _, idx ->
                                val chosen = matches[idx]
                                openFileInEditor(
                                    chosen.file,
                                    switchToEditorTab = true,
                                    highlightLine = chosen.lineNumber
                                )
                            }
                            .setNegativeButton("Đóng", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun refreshFileExplorer() {
        val session = currentSession()
        tvCurrentDirPath.text = session?.formatPath(currentExplorerDir) ?: currentExplorerDir.absolutePath
        val children = currentExplorerDir.listFiles()?.toList().orEmpty()
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        fileAdapter.updateFiles(children)
    }

    private fun showFileActionMenu(file: File, anchor: View) {
        val popup = PopupMenu(this, anchor)
        if (!file.isDirectory) {
            popup.menu.add(0, 1, 0, "✏️ Mở trong Code Editor")
            popup.menu.add(0, 2, 1, "▶ Chạy trong Terminal")
            val toggleExecLabel = if (file.canExecute()) "🔒 Bỏ quyền thực thi (-x)" else "⚡ Cấp quyền thực thi (+x)"
            popup.menu.add(0, 3, 2, toggleExecLabel)
        } else {
            popup.menu.add(0, 4, 0, ">_ Mở Terminal tại đây")
        }
        popup.menu.add(0, 5, 3, "🏷️ Đổi tên")
        popup.menu.add(0, 6, 4, "🗑️ Xóa")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> openFileInEditor(file, switchToEditorTab = true)
                2 -> {
                    currentSession()?.changeDirectory(file.parentFile ?: workspaceDir)
                    if (!isSplitMode) switchTab(TabMode.TERMINAL)
                    executeCommandInActiveSession(SyntaxHighlighter.buildRunCommand(file))
                }
                3 -> {
                    val newExec = !file.canExecute()
                    file.setExecutable(newExec)
                    refreshFileExplorer()
                    Toast.makeText(
                        this,
                        if (newExec) "Đã cấp quyền +x cho ${file.name}" else "Đã gỡ quyền +x của ${file.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                4 -> {
                    currentSession()?.changeDirectory(file)
                    if (!isSplitMode) switchTab(TabMode.TERMINAL)
                    executeCommandInActiveSession("pwd && ls -la")
                }
                5 -> promptRenameFile(file)
                6 -> confirmDeleteFile(file)
            }
            true
        }
        popup.show()
    }

    private fun promptCreateNewFile(openImmediately: Boolean) {
        val input = EditText(this).apply {
            hint = "Ví dụ: script.sh, app.py, Main.kt"
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Tạo File Mới")
            .setView(input)
            .setPositiveButton("Tạo") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newFile = File(currentExplorerDir, name)
                    if (!newFile.exists()) {
                        val starter = when (newFile.extension.lowercase()) {
                            "sh" -> "#!/system/bin/sh\necho \"Running $name...\"\n"
                            "py" -> "# Python script: $name\nprint(\"Hello from $name\")\n"
                            "kt" -> "fun main() {\n    println(\"Hello from $name\")\n}\n"
                            else -> ""
                        }
                        newFile.writeText(starter)
                        if (newFile.extension.lowercase() == "sh") {
                            newFile.setExecutable(true)
                        }
                    }
                    refreshFileExplorer()
                    if (openImmediately) {
                        openFileInEditor(newFile, switchToEditorTab = true)
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun promptCreateNewFolder() {
        val input = EditText(this).apply {
            hint = "Tên thư mục mới..."
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Tạo Thư Mục Mới")
            .setView(input)
            .setPositiveButton("Tạo") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val folder = File(currentExplorerDir, name)
                    folder.mkdirs()
                    refreshFileExplorer()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun promptRenameFile(file: File) {
        val input = EditText(this).apply {
            setText(file.name)
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Đổi tên ${file.name}")
            .setView(input)
            .setPositiveButton("Lưu") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != file.name) {
                    val dest = File(file.parentFile, newName)
                    if (file.renameTo(dest)) {
                        editorTabs.find { it.file == file }?.file = dest
                        updateEditorHeaderTitle()
                        rebuildEditorTabsBar()
                        refreshFileExplorer()
                    } else {
                        Toast.makeText(this, "Không thể đổi tên", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun confirmDeleteFile(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Xóa ${file.name}?")
            .setMessage("Bạn có chắc muốn xóa vĩnh viễn '${file.name}' không?")
            .setPositiveButton("Xóa") { _, _ ->
                val deleted = file.deleteRecursively()
                if (deleted) {
                    refreshFileExplorer()
                    Toast.makeText(this, "Đã xóa ${file.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Không thể xóa ${file.name}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // ==================== QUICK TOOLS & THEME SWITCHER ====================

    private fun showQuickToolsDialog() {
        val options = arrayOf(
            "🚀 Chạy thông tin hệ thống (sysinfo.sh)",
            "⚡ Chạy Benchmark tốc độ Shell (benchmark.sh)",
            "🔎 Tìm kiếm toàn bộ Project (Global Search)",
            "⬒ Bật/Tắt chia đôi màn hình (Split Editor + Terminal)",
            "🎨 Đổi giao diện màu sắc (IDE Color Theme)",
            "📊 Danh sách tiến trình đang chạy (ps)",
            "🌐 Kiểm tra giao diện mạng (ip addr / netstat)",
            "💾 Kiểm tra dung lượng bộ nhớ (df -h)",
            "🔍 Phóng to cỡ chữ (+1sp)",
            "🔎 Thu nhỏ cỡ chữ (-1sp)",
            "🧹 Xóa màn hình Terminal"
        )

        AlertDialog.Builder(this)
            .setTitle("⚡ TermIDE Studio v2.0 Tools")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (!isSplitMode) switchTab(TabMode.TERMINAL)
                        executeCommandInActiveSession("sh \"${File(workspaceDir, "sysinfo.sh").absolutePath}\"")
                    }
                    1 -> {
                        if (!isSplitMode) switchTab(TabMode.TERMINAL)
                        executeCommandInActiveSession("sh \"${File(workspaceDir, "benchmark.sh").absolutePath}\"")
                    }
                    2 -> showGlobalWorkspaceSearchDialog()
                    3 -> {
                        isSplitMode = !isSplitMode
                        applyPaneVisibility()
                    }
                    4 -> showThemePickerDialog()
                    5 -> {
                        if (!isSplitMode) switchTab(TabMode.TERMINAL)
                        executeCommandInActiveSession("ps -A 2>/dev/null || ps")
                    }
                    6 -> {
                        if (!isSplitMode) switchTab(TabMode.TERMINAL)
                        executeCommandInActiveSession("ip addr 2>/dev/null || ifconfig 2>/dev/null || netstat -rn")
                    }
                    7 -> {
                        if (!isSplitMode) switchTab(TabMode.TERMINAL)
                        executeCommandInActiveSession("df -h 2>/dev/null || df")
                    }
                    8 -> adjustFontSize(1f)
                    9 -> adjustFontSize(-1f)
                    10 -> {
                        currentSession()?.clearTranscript()
                        renderTerminalTranscript()
                    }
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun showThemePickerDialog() {
        val themes = arrayOf(
            "🌑 GitHub Dark (Mặc định)",
            "🧛 Dracula Purple",
            "🌅 Monokai Warm",
            "💻 Matrix OLED Black"
        )
        AlertDialog.Builder(this)
            .setTitle("🎨 Chọn Giao Diện IDE")
            .setItems(themes) { _, index ->
                applyColorTheme(index, savePref = true)
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun applyColorTheme(themeIndex: Int, savePref: Boolean) {
        val (bgPrimary, bgSecondary) = when (themeIndex) {
            1 -> 0xFF1E1F29.toInt() to 0xFF282A36.toInt() // Dracula
            2 -> 0xFF1E1F1C.toInt() to 0xFF272822.toInt() // Monokai
            3 -> 0xFF000000.toInt() to 0xFF0A0F0A.toInt() // Matrix OLED Black
            else -> 0xFF0D1117.toInt() to 0xFF161B22.toInt() // GitHub Dark
        }
        terminalScrollView.setBackgroundColor(bgPrimary)
        editorScrollView.setBackgroundColor(bgPrimary)
        lvFiles.setBackgroundColor(bgPrimary)
        topHeaderBar.setBackgroundColor(bgSecondary)
        tvLineNumbers.setBackgroundColor(bgSecondary)

        if (savePref) {
            getPreferences(Context.MODE_PRIVATE).edit().putInt("theme_index", themeIndex).apply()
        }
    }

    private fun restoreSavedPreferences() {
        val prefs = getPreferences(Context.MODE_PRIVATE)
        val savedTheme = prefs.getInt("theme_index", 0)
        if (savedTheme != 0) {
            applyColorTheme(savedTheme, savePref = false)
        }
        val savedFont = prefs.getFloat("font_size_sp", 13f)
        if (savedFont != 13f) {
            fontSizeSp = savedFont.coerceIn(10f, 22f)
            tvTerminalOutput.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
            etCodeEditor.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
            tvLineNumbers.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
        }
    }

    private fun adjustFontSize(delta: Float) {
        fontSizeSp = (fontSizeSp + delta).coerceIn(10f, 22f)
        tvTerminalOutput.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
        etCodeEditor.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
        tvLineNumbers.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
        getPreferences(Context.MODE_PRIVATE).edit().putFloat("font_size_sp", fontSizeSp).apply()
        Toast.makeText(this, "Cỡ chữ: ${fontSizeSp.toInt()}sp", Toast.LENGTH_SHORT).show()
    }

    // ==================== MOBILE KEYBOARD & BACK NAVIGATION HELPERS ====================

    private fun toggleSoftKeyboard(targetView: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        if (!imm.showSoftInput(targetView, InputMethodManager.SHOW_IMPLICIT)) {
            imm.hideSoftInputFromWindow(targetView.windowToken, 0)
        }
    }

    private fun hideSoftKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val focused = currentFocus ?: rootLayout
        imm.hideSoftInputFromWindow(focused.windowToken, 0)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isSplitMode) {
            isSplitMode = false
            applyPaneVisibility()
            return
        }
        when (activeTabMode) {
            TabMode.FILES -> {
                if (currentExplorerDir.canonicalPath != workspaceDir.canonicalPath &&
                    currentExplorerDir.parentFile?.canRead() == true
                ) {
                    currentExplorerDir = currentExplorerDir.parentFile!!
                    refreshFileExplorer()
                } else {
                    switchTab(TabMode.TERMINAL)
                }
            }
            TabMode.EDITOR -> {
                switchTab(TabMode.TERMINAL)
            }
            TabMode.TERMINAL -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    private fun updateTopSubtitle() {
        val session = currentSession()
        val dirDisplay = session?.let { it.formatPath(it.workingDir) } ?: "~/workspace"
        val fileDisplay = currentEditorDoc()?.file?.name ?: "none"
        tvSubtitleStatus.text = "$dirDisplay  •  $fileDisplay"
    }

    private fun makeKeyChipButton(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            includeFontPadding = false
            isClickable = true
            isFocusable = true
            setSingleLine(true)
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0xFFE6EDF3.toInt())
            setBackgroundResource(R.drawable.bg_key_chip)
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(32)
            )
            lp.marginEnd = dp(6)
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        sessions.forEach { it.interruptCurrentProcess() }
        ioExecutor.shutdownNow()
    }

    private enum class TabMode {
        TERMINAL,
        EDITOR,
        FILES
    }
}
