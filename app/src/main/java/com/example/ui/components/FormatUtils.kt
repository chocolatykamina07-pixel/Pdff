package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        return when {
            kb < 1024.0 -> String.format(Locale.getDefault(), "%.0f KB", kb.coerceAtLeast(1.0))
            kb < 1024.0 * 1024.0 -> String.format(Locale.getDefault(), "%.1f MB", kb / 1024.0)
            else -> String.format(Locale.getDefault(), "%.2f GB", kb / (1024.0 * 1024.0))
        }
    }

    fun formatTimestamp(timestamp: Long): String {
        val fmt = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
        return fmt.format(Date(timestamp))
    }

    fun formatShortDate(timestamp: Long): String {
        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return fmt.format(Date(timestamp))
    }

    fun daysRemainingInTrash(trashedAt: Long?): Int {
        if (trashedAt == null) return 30
        val elapsedDays = ((System.currentTimeMillis() - trashedAt) / (24L * 60L * 60L * 1000L)).toInt()
        return (30 - elapsedDays).coerceIn(0, 30)
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String = "Share via ScanNest") {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun shareText(context: Context, text: String, title: String = "Share Extracted Text") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
