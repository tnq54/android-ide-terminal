package com.termide.android

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileAdapter(
    private val context: Context,
    private var files: List<File>,
    private val onActionClick: (File, View) -> Unit
) : BaseAdapter() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun updateFiles(newFiles: List<File>) {
        files = newFiles
        notifyDataSetChanged()
    }

    override fun getCount(): Int = files.size

    override fun getItem(position: Int): File = files[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_file_row, parent, false)

        val file = files[position]
        val tvIcon = view.findViewById<TextView>(R.id.tvFileIcon)
        val tvName = view.findViewById<TextView>(R.id.tvFileName)
        val tvMeta = view.findViewById<TextView>(R.id.tvFileMeta)
        val tvAction = view.findViewById<TextView>(R.id.tvFileActionHint)

        tvIcon.text = getIconForFile(file)
        tvName.text = if (file.isDirectory) "${file.name}/" else file.name
        tvName.setTextColor(
            if (file.isDirectory) 0xFF58A6FF.toInt()
            else if (file.canExecute()) 0xFF3FB950.toInt()
            else 0xFFE6EDF3.toInt()
        )

        val perms = buildPermissionString(file)
        val sizeStr = if (file.isDirectory) {
            val childCount = file.listFiles()?.size ?: 0
            "$childCount items"
        } else {
            formatSize(file.length())
        }
        val dateStr = dateFormat.format(Date(file.lastModified()))
        tvMeta.text = "$perms • $sizeStr • $dateStr"

        tvAction.setOnClickListener {
            onActionClick(file, it)
        }

        return view
    }

    private fun getIconForFile(file: File): String {
        if (file.isDirectory) return "📁"
        return when (file.extension.lowercase()) {
            "sh", "bash" -> "🐚"
            "py" -> "🐍"
            "kt", "kts" -> "🟣"
            "java" -> "☕"
            "js", "ts", "json" -> "📜"
            "md", "txt" -> "📝"
            "xml", "html" -> "🌐"
            else -> if (file.canExecute()) "⚙️" else "📄"
        }
    }

    private fun buildPermissionString(file: File): String {
        val type = if (file.isDirectory) 'd' else '-'
        val r = if (file.canRead()) 'r' else '-'
        val w = if (file.canWrite()) 'w' else '-'
        val x = if (file.canExecute()) 'x' else '-'
        return "$type$r$w$x"
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
