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

    fun printDocumentPages(context: Context, jobName: String, imagePaths: List<String>) {
        if (imagePaths.isEmpty()) return
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager ?: return
        val adapter = object : android.print.PrintDocumentAdapter() {
            private var pdfDoc: android.print.pdf.PrintedPdfDocument? = null
            private var attributes: android.print.PrintAttributes? = null

            override fun onLayout(
                oldAttributes: android.print.PrintAttributes?,
                newAttributes: android.print.PrintAttributes,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback,
                extras: android.os.Bundle?
            ) {
                attributes = newAttributes
                pdfDoc = android.print.pdf.PrintedPdfDocument(context, newAttributes)
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = android.print.PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(imagePaths.size)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>,
                destination: android.os.ParcelFileDescriptor,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback
            ) {
                val doc = pdfDoc ?: run {
                    callback.onWriteFailed("Print initialization failed")
                    return
                }
                try {
                    imagePaths.forEachIndexed { idx, path ->
                        if (cancellationSignal?.isCanceled == true) {
                            callback.onWriteCancelled()
                            return
                        }
                        val bmp = com.example.processing.ImageProcessor.decodeSampledBitmap(path, 1800)
                            ?: return@forEachIndexed
                        val page = doc.startPage(idx)
                        val canvas = page.canvas
                        val cw = canvas.width.toFloat()
                        val ch = canvas.height.toFloat()
                        val scale = kotlin.math.min(cw / bmp.width.coerceAtLeast(1), ch / bmp.height.coerceAtLeast(1))
                        val dw = bmp.width * scale
                        val dh = bmp.height * scale
                        val left = (cw - dw) / 2f
                        val top = (ch - dh) / 2f
                        canvas.drawBitmap(bmp, null, android.graphics.RectF(left, top, left + dw, top + dh), null)
                        doc.finishPage(page)
                        bmp.recycle()
                    }
                    java.io.FileOutputStream(destination.fileDescriptor).use { out ->
                        doc.writeTo(out)
                    }
                    callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.localizedMessage)
                } finally {
                    doc.close()
                    pdfDoc = null
                }
            }
        }
        printManager.print(jobName, adapter, android.print.PrintAttributes.Builder().build())
    }
}
