package com.termide.android

import android.annotation.SuppressLint
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
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
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
    private var autoSaveEnabled: Boolean = true

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
    private lateinit var tabBtnGit: TextView

    // Panes
    private lateinit var paneTerminal: LinearLayout
    private lateinit var paneEditor: LinearLayout
    private lateinit var paneFiles: LinearLayout
    private lateinit var paneGit: LinearLayout
    private lateinit var splitPaneDivider: View

    // Terminal Views
    private lateinit var sessionTabsContainer: LinearLayout
    private lateinit var btnSshConnect: TextView
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
    private lateinit var btnEditorSnippets: TextView
    private lateinit var btnEditorPreview: TextView
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

    // Git Views
    private lateinit var btnGitStatus: TextView
    private lateinit var btnGitDiff: TextView
    private lateinit var btnGitLog: TextView
    private lateinit var btnGitBranch: TextView
    private lateinit var tvGitOutput: TextView
    private lateinit var etGitCommitMsg: EditText
    private lateinit var btnGitCommitNow: TextView

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
        setupGitUi()
        restoreSavedPreferences()

        // Create initial terminal session
        createNewTerminalSession(workspaceDir)
        // Pre-open starter files in multi-tab editor
        openFileInEditor(starterScript, switchToEditorTab = false)
        listOf("index.html", "sysinfo.sh", "script.py").forEach { name ->
            val f = File(workspaceDir, name)
            if (f.exists()) openFileInEditor(f, switchToEditorTab = false)
        }
        switchToEditorTab(0)

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
        tabBtnGit = findViewById(R.id.tabBtnGit)

        paneTerminal = findViewById(R.id.paneTerminal)
        paneEditor = findViewById(R.id.paneEditor)
        paneFiles = findViewById(R.id.paneFiles)
        paneGit = findViewById(R.id.paneGit)
        splitPaneDivider = findViewById(R.id.splitPaneDivider)

        sessionTabsContainer = findViewById(R.id.sessionTabsContainer)
        btnSshConnect = findViewById(R.id.btnSshConnect)
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
        btnEditorSnippets = findViewById(R.id.btnEditorSnippets)
        btnEditorPreview = findViewById(R.id.btnEditorPreview)
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

        btnGitStatus = findViewById(R.id.btnGitStatus)
        btnGitDiff = findViewById(R.id.btnGitDiff)
        btnGitLog = findViewById(R.id.btnGitLog)
        btnGitBranch = findViewById(R.id.btnGitBranch)
        tvGitOutput = findViewById(R.id.tvGitOutput)
        etGitCommitMsg = findViewById(R.id.etGitCommitMsg)
        btnGitCommitNow = findViewById(R.id.btnGitCommitNow)
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
            if (autoSaveEnabled) saveCurrentEditorFile(showToast = false)
            isSplitMode = false
            switchTab(TabMode.TERMINAL)
        }
        tabBtnEditor.setOnClickListener {
            isSplitMode = false
            switchTab(TabMode.EDITOR)
        }
        tabBtnFiles.setOnClickListener {
            if (autoSaveEnabled) saveCurrentEditorFile(showToast = false)
            isSplitMode = false
            hideSoftKeyboard()
            refreshFileExplorer()
            switchTab(TabMode.FILES)
        }
        tabBtnGit.setOnClickListener {
            if (autoSaveEnabled) saveCurrentEditorFile(showToast = false)
            isSplitMode = false
            hideSoftKeyboard()
            switchTab(TabMode.GIT)
            refreshGitStatusView()
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
            paneGit.visibility = View.GONE

            (paneEditor.layoutParams as? LinearLayout.LayoutParams)?.weight = 1.15f
            (paneTerminal.layoutParams as? LinearLayout.LayoutParams)?.weight = 0.85f
            paneEditor.requestLayout()
            paneTerminal.requestLayout()

            styleTabButton(tabBtnTerminal, true)
            styleTabButton(tabBtnEditor, true)
            styleTabButton(tabBtnFiles, false)
            styleTabButton(tabBtnGit, false)
        } else {
            splitPaneDivider.visibility = View.GONE
            paneTerminal.visibility = if (activeTabMode == TabMode.TERMINAL) View.VISIBLE else View.GONE
            paneEditor.visibility = if (activeTabMode == TabMode.EDITOR) View.VISIBLE else View.GONE
            paneFiles.visibility = if (activeTabMode == TabMode.FILES) View.VISIBLE else View.GONE
            paneGit.visibility = if (activeTabMode == TabMode.GIT) View.VISIBLE else View.GONE

            (paneEditor.layoutParams as? LinearLayout.LayoutParams)?.weight = 1f
            (paneTerminal.layoutParams as? LinearLayout.LayoutParams)?.weight = 1f

            styleTabButton(tabBtnTerminal, activeTabMode == TabMode.TERMINAL)
            styleTabButton(tabBtnEditor, activeTabMode == TabMode.EDITOR)
            styleTabButton(tabBtnFiles, activeTabMode == TabMode.FILES)
            styleTabButton(tabBtnGit, activeTabMode == TabMode.GIT)
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

    // ==================== TERMINAL & SSH LOGIC ====================

    private fun setupTerminalUi() {
        btnSshConnect.setOnClickListener {
            showSshManagerDialog()
        }

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

    private fun showSshManagerDialog() {
        val session = currentSession() ?: return
        val activeSsh = session.sshManager
        if (activeSsh != null && activeSsh.isConnected()) {
            val prof = activeSsh.activeProfile
            AlertDialog.Builder(this)
                .setTitle("🌐 Đang kết nối SSH")
                .setMessage("Máy chủ: ${prof?.displayLabel()}\nThư mục từ xa: ${activeSsh.remoteWorkingDir}")
                .setPositiveButton("Ngắt kết nối SSH") { _, _ ->
                    activeSsh.disconnect()
                    session.title = "sh-${session.id}"
                    session.appendLine("\u001B[33m[Đã ngắt kết nối SSH]\u001B[0m")
                    rebuildSessionTabs()
                    renderTerminalTranscript()
                    updateSshButtonBadge()
                    updateTopSubtitle()
                }
                .setNeutralButton("Tạo phiên SSH mới") { _, _ ->
                    showSshProfileListOrConnectDialog(createNewSession = true)
                }
                .setNegativeButton("Đóng", null)
                .show()
            return
        }

        showSshProfileListOrConnectDialog(createNewSession = false)
    }

    private fun showSshProfileListOrConnectDialog(createNewSession: Boolean) {
        val savedProfiles = loadSavedSshProfiles().toMutableList()
        if (savedProfiles.isEmpty()) {
            savedProfiles.add(
                SshProfile(
                    name = "Termux Local sshd",
                    host = "127.0.0.1",
                    port = 8022,
                    username = "u0_a000"
                )
            )
        }

        val items = (savedProfiles.map { "🔗 ${it.displayLabel()}" } +
            listOf("➕ Thêm kết nối SSH / VPS mới...", "🗑️ Xóa các cấu hình SSH đã lưu")).toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("🌐 Kết nối SSH / Remote Server")
            .setItems(items) { _, which ->
                when {
                    which < savedProfiles.size -> {
                        val chosen = savedProfiles[which]
                        promptSshConnectDialog(chosen, createNewSession)
                    }
                    which == savedProfiles.size -> {
                        promptSshConnectDialog(null, createNewSession)
                    }
                    else -> {
                        getPreferences(Context.MODE_PRIVATE).edit().remove("ssh_profiles").apply()
                        Toast.makeText(this, "Đã xóa danh sách SSH đã lưu", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun promptSshConnectDialog(prefill: SshProfile?, createNewSession: Boolean) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(4))
        }
        val etName = EditText(this).apply {
            hint = "Tên gợi nhớ (vd: VPS Ubuntu / Termux)"
            setText(prefill?.name ?: "")
            setSingleLine(true)
        }
        val etHost = EditText(this).apply {
            hint = "Host / IP (vd: 192.168.1.100 hoặc 127.0.0.1)"
            setText(prefill?.host ?: "")
            setSingleLine(true)
        }
        val etPort = EditText(this).apply {
            hint = "Port (mặc định: 22, Termux: 8022)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText((prefill?.port ?: 22).toString())
            setSingleLine(true)
        }
        val etUser = EditText(this).apply {
            hint = "Username (vd: root, ubuntu)"
            setText(prefill?.username ?: "root")
            setSingleLine(true)
        }
        val etPass = EditText(this).apply {
            hint = "Password (để trống nếu dùng Private Key)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefill?.password ?: "")
            setSingleLine(true)
        }
        val etKey = EditText(this).apply {
            hint = "Hoặc dán PEM/OpenSSH Private Key (tùy chọn)"
            maxLines = 4
            setText(prefill?.privateKey ?: "")
        }

        container.addView(etName)
        container.addView(etHost)
        container.addView(etPort)
        container.addView(etUser)
        container.addView(etPass)
        container.addView(etKey)

        AlertDialog.Builder(this)
            .setTitle("🌐 Thông tin kết nối SSH")
            .setView(container)
            .setPositiveButton("Lưu & Kết nối") { _, _ ->
                val host = etHost.text.toString().trim()
                if (host.isEmpty()) {
                    Toast.makeText(this, "Vui lòng nhập địa chỉ Host/IP", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val profile = SshProfile(
                    name = etName.text.toString().trim().ifEmpty { host },
                    host = host,
                    port = etPort.text.toString().toIntOrNull() ?: 22,
                    username = etUser.text.toString().trim().ifEmpty { "root" },
                    password = etPass.text.toString(),
                    privateKey = etKey.text.toString().trim()
                )
                saveSshProfile(profile)
                connectSessionToSsh(profile, createNewSession)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun connectSessionToSsh(profile: SshProfile, createNewSession: Boolean) {
        if (createNewSession) {
            createNewTerminalSession(workspaceDir)
        }
        val session = currentSession() ?: return
        session.appendLine("\u001B[36m⟳ Connecting to SSH ${profile.username}@${profile.host}:${profile.port}...\u001B[0m")
        renderTerminalTranscript()

        ioExecutor.execute {
            val manager = SshConnectionManager()
            val result = manager.connect(profile)
            mainHandler.post {
                result.onSuccess { msg ->
                    session.sshManager = manager
                    session.title = "ssh:${profile.name}"
                    session.appendLine("\u001B[1;32m✓ $msg\u001B[0m")
                    rebuildSessionTabs()
                    renderTerminalTranscript()
                    updateSshButtonBadge()
                    updateTopSubtitle()
                    executeCommandInActiveSession("uname -a && ls -la")
                }.onFailure { err ->
                    session.appendLine("\u001B[31m✗ SSH Connection Failed: ${err.message}\u001B[0m")
                    renderTerminalTranscript()
                    updateSshButtonBadge()
                }
            }
        }
    }

    private fun updateSshButtonBadge() {
        val active = currentSession()?.isSshActive() == true
        btnSshConnect.text = if (active) "🌐 SSH: ON" else "🌐 SSH"
        btnSshConnect.setTextColor(if (active) 0xFF3FB950.toInt() else 0xFFFFA657.toInt())
    }

    private fun loadSavedSshProfiles(): List<SshProfile> {
        val raw = getPreferences(Context.MODE_PRIVATE).getString("ssh_profiles", "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split("\u001E").mapNotNull { SshProfile.deserialize(it) }
    }

    private fun saveSshProfile(profile: SshProfile) {
        val existing = loadSavedSshProfiles().filterNot {
            it.host == profile.host && it.port == profile.port && it.username == profile.username
        }.toMutableList()
        existing.add(0, profile)
        val serialized = existing.take(10).joinToString("\u001E") { it.serialize() }
        getPreferences(Context.MODE_PRIVATE).edit().putString("ssh_profiles", serialized).apply()
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
            "git status" to {
                isSplitMode = false
                switchTab(TabMode.GIT)
                refreshGitStatusView()
            }
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
        updateSshButtonBadge()
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
        removed.sshManager?.disconnect()
        if (activeSessionIndex >= sessions.size) {
            activeSessionIndex = sessions.lastIndex
        }
        rebuildSessionTabs()
        renderTerminalTranscript()
        updateSshButtonBadge()
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
                    updateSshButtonBadge()
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
                        updateSshButtonBadge()
                        updateTopSubtitle()
                    }
                },
                onComplete = {
                    mainHandler.post {
                        renderTerminalTranscript()
                        updateSshButtonBadge()
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

    // ==================== MULTI-TAB CODE EDITOR & WEB PREVIEW LOGIC ====================

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

        btnEditorSnippets.setOnClickListener {
            showCodeSnippetsDialog()
        }

        btnEditorPreview.setOnClickListener {
            showLiveWebPreviewDialog()
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

    private fun showCodeSnippetsDialog() {
        val snippets = WorkspaceInitializer.getCodeSnippets()
        val titles = snippets.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("🧩 Chèn Mẫu Code Nhanh (Snippets)")
            .setItems(titles) { _, idx ->
                val chosen = snippets[idx]
                val start = etCodeEditor.selectionStart.coerceAtLeast(0)
                val end = etCodeEditor.selectionEnd.coerceAtLeast(0)
                etCodeEditor.text.replace(minOf(start, end), maxOf(start, end), chosen.code)
                etCodeEditor.requestFocus()
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showLiveWebPreviewDialog() {
        val savedFile = saveCurrentEditorFile(showToast = false)
        val rawCode = etCodeEditor.text.toString()
        val ext = savedFile?.extension?.lowercase().orEmpty()

        val htmlContent = when (ext) {
            "html", "htm", "svg" -> rawCode
            "js" -> """
                <!DOCTYPE html>
                <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                <style>body{background:#0D1117;color:#3FB950;font-family:monospace;padding:14px;white-space:pre-wrap;}</style></head>
                <body><h3>⚡ JS Sandbox Output:</h3><div id="out"></div>
                <script>
                const out = document.getElementById('out');
                console.log = (...args) => { out.innerText += args.join(' ') + '\n'; };
                console.error = (...args) => { out.innerText += '[ERR] ' + args.join(' ') + '\n'; };
                try { $rawCode } catch(e) { console.error(e.message); }
                </script></body></html>
            """.trimIndent()
            else -> """
                <!DOCTYPE html>
                <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                <style>body{background:#0D1117;color:#E6EDF3;font-family:monospace;padding:14px;white-space:pre-wrap;}</style></head>
                <body>${rawCode.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</body></html>
            """.trimIndent()
        }

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadDataWithBaseURL(
                "file://${workspaceDir.absolutePath}/",
                htmlContent,
                "text/html",
                "UTF-8",
                null
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(420)
            )
        }

        AlertDialog.Builder(this)
            .setTitle("🌐 Live Preview: ${savedFile?.name ?: "preview"}")
            .setView(webView)
            .setPositiveButton("Đóng", null)
            .show()
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
        val ext = savedFile.extension.lowercase()
        if (ext in listOf("html", "htm", "svg")) {
            showLiveWebPreviewDialog()
            return
        }

        hideSoftKeyboard()
        val session = currentSession()
        if (session != null && session.isSshActive()) {
            // Upload script to remote SSH server and execute remotely
            val ssh = session.sshManager ?: return
            if (!isSplitMode) switchTab(TabMode.TERMINAL)
            session.appendLine("\u001B[36m⟳ Uploading ${savedFile.name} to remote SSH server...\u001B[0m")
            renderTerminalTranscript()
            ioExecutor.execute {
                val upRes = ssh.uploadFileToRemote(savedFile)
                mainHandler.post {
                    upRes.onSuccess { remotePath ->
                        session.appendLine("\u001B[32m✓ Uploaded to $remotePath\u001B[0m")
                        executeCommandInActiveSession("sh \"$remotePath\"")
                    }.onFailure { err ->
                        session.appendLine("\u001B[31m✗ Upload failed: ${err.message}\u001B[0m")
                        renderTerminalTranscript()
                    }
                }
            }
            return
        }

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

    // ==================== FILE EXPLORER & SFTP LOGIC ====================

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
        val children = currentExplorerDir.listFiles()
            ?.filter { it.name != ".termide_vcs" }
            ?.toList().orEmpty()
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
            if (currentSession()?.isSshActive() == true) {
                popup.menu.add(0, 7, 3, "☁️ Upload file lên SSH Server (SFTP)")
            }
        } else {
            popup.menu.add(0, 4, 0, ">_ Mở Terminal tại đây")
        }
        popup.menu.add(0, 5, 4, "🏷️ Đổi tên")
        popup.menu.add(0, 6, 5, "🗑️ Xóa")

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
                7 -> {
                    val ssh = currentSession()?.sshManager ?: return@setOnMenuItemClickListener true
                    ioExecutor.execute {
                        val res = ssh.uploadFileToRemote(file)
                        mainHandler.post {
                            res.onSuccess { remotePath ->
                                Toast.makeText(this, "Đã upload lên $remotePath", Toast.LENGTH_LONG).show()
                            }.onFailure { err ->
                                Toast.makeText(this, "Lỗi SFTP: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
            true
        }
        popup.show()
    }

    private fun promptCreateNewFile(openImmediately: Boolean) {
        val input = EditText(this).apply {
            hint = "Ví dụ: script.sh, app.py, index.html"
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
                            "html" -> "<!DOCTYPE html>\n<html>\n<body>\n  <h2>$name</h2>\n</body>\n</html>\n"
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

    // ==================== GIT & SOURCE CONTROL LOGIC ====================

    private fun setupGitUi() {
        btnGitStatus.setOnClickListener {
            refreshGitStatusView()
        }

        btnGitDiff.setOnClickListener {
            ioExecutor.execute {
                val raw = GitSourceControlManager.runGitDiff(
                    currentSession()?.workingDir ?: workspaceDir,
                    currentSession()?.sshManager
                )
                mainHandler.post {
                    tvGitOutput.text = AnsiParser.parseToSpannable(raw)
                }
            }
        }

        btnGitLog.setOnClickListener {
            ioExecutor.execute {
                val raw = GitSourceControlManager.runGitLog(
                    currentSession()?.workingDir ?: workspaceDir,
                    currentSession()?.sshManager
                )
                mainHandler.post {
                    tvGitOutput.text = AnsiParser.parseToSpannable(raw)
                }
            }
        }

        btnGitBranch.setOnClickListener {
            showGitRemoteOperationsDialog()
        }

        btnGitCommitNow.setOnClickListener {
            val msg = etGitCommitMsg.text.toString().trim().ifEmpty { "chore: update workspace files" }
            etGitCommitMsg.setText("")
            hideSoftKeyboard()
            ioExecutor.execute {
                val res = GitSourceControlManager.runGitCommit(
                    currentSession()?.workingDir ?: workspaceDir,
                    msg,
                    currentSession()?.sshManager
                )
                val statusAfter = GitSourceControlManager.runGitStatus(
                    currentSession()?.workingDir ?: workspaceDir,
                    currentSession()?.sshManager
                )
                mainHandler.post {
                    tvGitOutput.text = AnsiParser.parseToSpannable("$res\n\n$statusAfter")
                    Toast.makeText(this, "Đã Commit thành công!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshGitStatusView() {
        ioExecutor.execute {
            val raw = GitSourceControlManager.runGitStatus(
                currentSession()?.workingDir ?: workspaceDir,
                currentSession()?.sshManager
            )
            mainHandler.post {
                tvGitOutput.text = AnsiParser.parseToSpannable(raw)
            }
        }
    }

    private fun showGitRemoteOperationsDialog() {
        val actions = arrayOf(
            "🌿 Xem danh sách Branch (git branch -a)",
            "⬇️ Git Pull (Kéo cập nhật từ Remote)",
            "⬆️ Git Push (Đẩy commit lên Remote)",
            "🔗 Clone Git Repository về thư mục hiện tại"
        )
        AlertDialog.Builder(this)
            .setTitle("🌿 Git Branch & Remote")
            .setItems(actions) { _, idx ->
                when (idx) {
                    0 -> {
                        switchTab(TabMode.TERMINAL)
                        executeCommandInActiveSession("git branch -a")
                    }
                    1 -> {
                        switchTab(TabMode.TERMINAL)
                        executeCommandInActiveSession("git pull")
                    }
                    2 -> {
                        switchTab(TabMode.TERMINAL)
                        executeCommandInActiveSession("git push")
                    }
                    3 -> {
                        val input = EditText(this).apply {
                            hint = "https://github.com/user/repo.git"
                            setSingleLine(true)
                        }
                        AlertDialog.Builder(this)
                            .setTitle("Clone Repository")
                            .setView(input)
                            .setPositiveButton("Clone") { _, _ ->
                                val url = input.text.toString().trim()
                                if (url.isNotEmpty()) {
                                    switchTab(TabMode.TERMINAL)
                                    executeCommandInActiveSession("git clone \"$url\"")
                                }
                            }
                            .setNegativeButton("Hủy", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    // ==================== QUICK TOOLS & THEME SWITCHER ====================

    private fun showQuickToolsDialog() {
        val autoSaveLabel = if (autoSaveEnabled) "💾 Tự động lưu khi chuyển tab: BẬT" else "💾 Tự động lưu khi chuyển tab: TẮT"
        val options = arrayOf(
            "🌐 Kết nối SSH / Quản lý VPS từ xa",
            "🚀 Chạy thông tin hệ thống (sysinfo.sh)",
            "⚡ Chạy Benchmark tốc độ Shell (benchmark.sh)",
            "🌐 Xem trước Web / HTML / JS (Live Preview)",
            "🔎 Tìm kiếm toàn bộ Project (Global Search)",
            "⬒ Bật/Tắt chia đôi màn hình (Split Editor + Terminal)",
            "🎨 Đổi giao diện màu sắc (6 IDE Color Themes)",
            autoSaveLabel,
            "📊 Danh sách tiến trình đang chạy (ps)",
            "🔍 Phóng to cỡ chữ (+1sp)",
            "🔎 Thu nhỏ cỡ chữ (-1sp)",
            "🧹 Xóa màn hình Terminal"
        )

        AlertDialog.Builder(this)
            .setTitle("⚡ TermIDE Studio v3.0 Full Tools")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSshManagerDialog()
                    1 -> {
                        if (!isSplitMode) switchTab(TabMode.TERMINAL)
                        executeCommandInActiveSession("sh \"${File(workspaceDir, "sysinfo.sh").absolutePath}\"")
                    }
                    2 -> {
                        if (!isSplitMode) switchTab(TabMode.TERMINAL)
                        executeCommandInActiveSession("sh \"${File(workspaceDir, "benchmark.sh").absolutePath}\"")
                    }
                    3 -> showLiveWebPreviewDialog()
                    4 -> showGlobalWorkspaceSearchDialog()
                    5 -> {
                        isSplitMode = !isSplitMode
                        applyPaneVisibility()
                    }
                    6 -> showThemePickerDialog()
                    7 -> {
                        autoSaveEnabled = !autoSaveEnabled
                        getPreferences(Context.MODE_PRIVATE).edit()
                            .putBoolean("auto_save", autoSaveEnabled).apply()
                        Toast.makeText(
                            this,
                            if (autoSaveEnabled) "Đã bật Tự động lưu" else "Đã tắt Tự động lưu",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    8 -> {
                        if (!isSplitMode) switchTab(TabMode.TERMINAL)
                        executeCommandInActiveSession("ps -A 2>/dev/null || ps")
                    }
                    9 -> adjustFontSize(1f)
                    10 -> adjustFontSize(-1f)
                    11 -> {
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
            "💻 Matrix OLED Black",
            "🌊 Solarized Deep Ocean",
            "☀️ VS Code Clean Dark+"
        )
        AlertDialog.Builder(this)
            .setTitle("🎨 Chọn Giao Diện IDE (6 Themes)")
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
            4 -> 0xFF002B36.toInt() to 0xFF073642.toInt() // Solarized Ocean
            5 -> 0xFF1E1E1E.toInt() to 0xFF252526.toInt() // VS Code Dark+
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
        autoSaveEnabled = prefs.getBoolean("auto_save", true)
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
            tvGitOutput.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
        }
    }

    private fun adjustFontSize(delta: Float) {
        fontSizeSp = (fontSizeSp + delta).coerceIn(10f, 22f)
        tvTerminalOutput.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
        etCodeEditor.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
        tvLineNumbers.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
        tvGitOutput.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
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
            TabMode.EDITOR, TabMode.GIT -> {
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
        val dirDisplay = session?.getPromptLabel() ?: "~/workspace"
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
        sessions.forEach {
            it.interruptCurrentProcess()
            it.sshManager?.disconnect()
        }
        ioExecutor.shutdownNow()
    }

    private enum class TabMode {
        TERMINAL,
        EDITOR,
        FILES,
        GIT
    }
}
