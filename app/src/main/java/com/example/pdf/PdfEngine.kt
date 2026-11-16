package com.example.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.example.data.local.PageEntity
import com.example.domain.model.ExportFormat
import com.example.domain.model.ExportQuality
import com.example.domain.model.ExportRequest
import com.example.domain.model.MarginOption
import com.example.domain.model.OcrLanguage
import com.example.domain.model.OrientationOption
import com.example.domain.model.PageSizeOption
import com.example.ocr.OcrEngine
import com.example.processing.ImageProcessor
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.roundToInt

object PdfEngine {

    /**
     * Live estimated export file size in bytes before actually generating the file.
     */
    fun estimateExportSizeBytes(
        pages: List<PageEntity>,
        request: ExportRequest,
        ocrText: String?
    ): Long {
        if (pages.isEmpty()) return 0L
        return when (request.format) {
            ExportFormat.TXT -> (ocrText?.toByteArray()?.size ?: (pages.size * 420)).toLong().coerceAtLeast(128L)
            ExportFormat.DOCX -> ((ocrText?.toByteArray()?.size ?: (pages.size * 420)) + 2800L)
            ExportFormat.JPEG -> {
                val basePerPage = when (request.quality) {
                    ExportQuality.SMALL -> 110_000L
                    ExportQuality.MEDIUM -> 260_000L
                    ExportQuality.LARGE -> 580_000L
                }
                basePerPage
            }
            ExportFormat.PDF -> {
                val perPage = when (request.quality) {
                    ExportQuality.SMALL -> 95_000L
                    ExportQuality.MEDIUM -> 225_000L
                    ExportQuality.LARGE -> 510_000L
                }
                perPage * pages.size + 4_096L
            }
        }
    }

    /**
     * Streams multi-page pages into a PDF using Android's PdfDocument (OOM-safe for 100+ pages).
     * Optionally embeds invisible searchable OCR text layer or applies PDFBox password protection.
     */
    suspend fun createPdfFromPages(
        context: Context,
        pages: List<PageEntity>,
        outputFile: File,
        request: ExportRequest,
        ocrLanguage: OcrLanguage = OcrLanguage.LATIN,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val tempPdf = if (request.password.isNotBlank()) {
            File(outputFile.parentFile, "tmp_${outputFile.name}")
        } else outputFile

        val pdfDocument = PdfDocument()
        try {
            pages.forEachIndexed { idx, page ->
                coroutineContext.ensureActive()
                onProgress(idx + 1, pages.size)

                val bmp = ImageProcessor.decodeSampledBitmap(
                    page.processedImagePath,
                    maxDimension = request.quality.maxDimensionPx
                ) ?: return@forEachIndexed

                // Compress/re-decode JPEG bytes at target quality to control final PDF byte size cleanly
                val compressedBmp = reencodeForPdfQuality(bmp, request.quality.jpegQuality)
                if (compressedBmp !== bmp) bmp.recycle()

                val (pageW, pageH) = resolvePageDimensions(
                    bmpW = compressedBmp.width,
                    bmpH = compressedBmp.height,
                    pageSize = request.pageSize,
                    orientation = request.orientation
                )

                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas
                canvas.drawColor(Color.WHITE)

                val margin = request.margin.marginPt
                val availW = (pageW - 2f * margin).coerceAtLeast(32f)
                val availH = (pageH - 2f * margin).coerceAtLeast(32f)
                val scale = min(availW / compressedBmp.width, availH / compressedBmp.height)
                val drawW = compressedBmp.width * scale
                val drawH = compressedBmp.height * scale
                val left = (pageW - drawW) / 2f
                val top = (pageH - drawH) / 2f

                val destRect = RectF(left, top, left + drawW, top + drawH)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(compressedBmp, null, destRect, paint)

                if (request.searchablePdf) {
                    val ocrRes = runCatching {
                        OcrEngine.recognizeBitmap(compressedBmp, idx, ocrLanguage)
                    }.getOrNull()
                    if (ocrRes != null && ocrRes.blocks.isNotEmpty()) {
                        val textPaint = Paint().apply {
                            color = Color.TRANSPARENT
                        }
                        val sx = drawW / compressedBmp.width.coerceAtLeast(1)
                        val sy = drawH / compressedBmp.height.coerceAtLeast(1)
                        for (block in ocrRes.blocks) {
                            val bLeft = left + block.left * sx
                            val bBottom = top + block.bottom * sy
                            val bHeight = ((block.bottom - block.top) * sy).coerceIn(8f, 36f)
                            textPaint.textSize = bHeight * 0.7f
                            canvas.drawText(block.text.replace('\n', ' '), bLeft, bBottom, textPaint)
                        }
                    }
                }

                pdfDocument.finishPage(pdfPage)
                compressedBmp.recycle()
            }

            FileOutputStream(tempPdf).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
        }

        if (request.password.isNotBlank()) {
            PDDocument.load(tempPdf).use { doc ->
                val ap = AccessPermission()
                val policy = StandardProtectionPolicy(request.password, request.password, ap).apply {
                    encryptionKeyLength = 128
                    permissions = ap
                }
                doc.protect(policy)
                doc.save(outputFile)
            }
            tempPdf.delete()
        }

        outputFile
    }

    private fun reencodeForPdfQuality(bmp: Bitmap, jpegQuality: Int): Bitmap {
        if (jpegQuality >= 94) return bmp
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
        val bytes = baos.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: bmp
    }

    private fun resolvePageDimensions(
        bmpW: Int,
        bmpH: Int,
        pageSize: PageSizeOption,
        orientation: OrientationOption
    ): Pair<Int, Int> {
        if (pageSize == PageSizeOption.AUTO) {
            val scale = 595f / bmpW.coerceAtLeast(1)
            val w = 595
            val h = (bmpH * scale).roundToInt().coerceIn(140, 1400)
            return when (orientation) {
                OrientationOption.LANDSCAPE -> maxOf(w, h) to minOf(w, h)
                OrientationOption.PORTRAIT -> minOf(w, h) to maxOf(w, h)
                OrientationOption.AUTO -> w to h
            }
        }
        val baseW = pageSize.widthPt
        val baseH = pageSize.heightPt
        val isBitmapLandscape = bmpW > bmpH
        return when (orientation) {
            OrientationOption.PORTRAIT -> minOf(baseW, baseH) to maxOf(baseW, baseH)
            OrientationOption.LANDSCAPE -> maxOf(baseW, baseH) to minOf(baseW, baseH)
            OrientationOption.AUTO -> if (isBitmapLandscape) {
                maxOf(baseW, baseH) to minOf(baseW, baseH)
            } else {
                minOf(baseW, baseH) to maxOf(baseW, baseH)
            }
        }
    }

    suspend fun getPdfPageCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer -> renderer.pageCount }
        } ?: 0
    }

    suspend fun renderPdfPagesToBitmaps(
        context: Context,
        uri: Uri,
        targetWidthPx: Int = 1400,
        pageIndices: List<Int>? = null
    ): List<File> = withContext(Dispatchers.IO) {
        val outFiles = mutableListOf<File>()
        val cacheDir = File(context.cacheDir, "pdf_render_${System.currentTimeMillis()}").apply { mkdirs() }
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val indices = pageIndices ?: (0 until renderer.pageCount).toList()
                for (index in indices) {
                    if (index !in 0 until renderer.pageCount) continue
                    coroutineContext.ensureActive()
                    renderer.openPage(index).use { page ->
                        val scale = targetWidthPx.toFloat() / page.width.coerceAtLeast(1)
                        val w = (page.width * scale).roundToInt().coerceAtLeast(100)
                        val h = (page.height * scale).roundToInt().coerceAtLeast(100)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        canvas.drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val outFile = File(cacheDir, "page_${index + 1}.jpg")
                        ImageProcessor.saveBitmapJpeg(bmp, outFile, 90)
                        bmp.recycle()
                        outFiles.add(outFile)
                    }
                }
            }
        }
        outFiles
    }

    suspend fun mergePdfs(
        context: Context,
        uris: List<Uri>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val merger = PDFMergerUtility()
        merger.destinationFileName = outputFile.absolutePath
        val tempInputs = mutableListOf<File>()
        try {
            uris.forEachIndexed { idx, uri ->
                val tmp = File(context.cacheDir, "merge_in_${idx}_${System.currentTimeMillis()}.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tmp).use { input.copyTo(it) }
                }
                tempInputs.add(tmp)
                merger.addSource(tmp)
            }
            merger.mergeDocuments(null)
        } finally {
            tempInputs.forEach { it.delete() }
        }
        outputFile
    }

    suspend fun splitOrReorderPdf(
        context: Context,
        uri: Uri,
        selectedPageNumbersOneBased: List<Int>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val rendered = renderPdfPagesToBitmaps(
            context = context,
            uri = uri,
            targetWidthPx = 1600,
            pageIndices = selectedPageNumbersOneBased.map { it - 1 }
        )
        val pdfDocument = PdfDocument()
        try {
            rendered.forEachIndexed { idx, imgFile ->
                coroutineContext.ensureActive()
                val bmp = ImageProcessor.decodeSampledBitmap(imgFile.absolutePath, 1800) ?: return@forEachIndexed
                val pageInfo = PdfDocument.PageInfo.Builder(595, (595f * bmp.height / bmp.width).roundToInt(), idx + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(bmp, null, RectF(0f, 0f, pageInfo.pageWidth.toFloat(), pageInfo.pageHeight.toFloat()), null)
                pdfDocument.finishPage(page)
                bmp.recycle()
                imgFile.delete()
            }
            FileOutputStream(outputFile).use { pdfDocument.writeTo(it) }
        } finally {
            pdfDocument.close()
        }
        outputFile
    }

    suspend fun compressPdf(
        context: Context,
        uri: Uri,
        quality: ExportQuality,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val rendered = renderPdfPagesToBitmaps(context, uri, targetWidthPx = quality.maxDimensionPx)
        val pdfDocument = PdfDocument()
        try {
            rendered.forEachIndexed { idx, imgFile ->
                coroutineContext.ensureActive()
                val bmp = ImageProcessor.decodeSampledBitmap(imgFile.absolutePath, quality.maxDimensionPx) ?: return@forEachIndexed
                val reencoded = reencodeForPdfQuality(bmp, quality.jpegQuality)
                if (reencoded !== bmp) bmp.recycle()
                val w = 595
                val h = (595f * reencoded.height / reencoded.width).roundToInt().coerceAtLeast(140)
                val pageInfo = PdfDocument.PageInfo.Builder(w, h, idx + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(reencoded, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), null)
                pdfDocument.finishPage(page)
                reencoded.recycle()
                imgFile.delete()
            }
            FileOutputStream(outputFile).use { pdfDocument.writeTo(it) }
        } finally {
            pdfDocument.close()
        }
        outputFile
    }

    suspend fun protectPdf(
        context: Context,
        uri: Uri,
        password: String,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc ->
                val ap = AccessPermission()
                val policy = StandardProtectionPolicy(password, password, ap).apply {
                    encryptionKeyLength = 128
                    permissions = ap
                }
                doc.protect(policy)
                doc.save(outputFile)
            }
        }
        outputFile
    }

    suspend fun unlockPdf(
        context: Context,
        uri: Uri,
        password: String,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input, password).use { doc ->
                doc.isAllSecurityToBeRemoved = true
                doc.save(outputFile)
            }
        }
        outputFile
    }

    suspend fun exportTxt(text: String, outputFile: File): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(text)
        outputFile
    }

    /**
     * Creates a valid OpenXML .docx document (ZIP container with minimal WordprocessingML).
     */
    suspend fun exportDocx(title: String, bodyText: String, outputFile: File): File =
        withContext(Dispatchers.IO) {
            outputFile.parentFile?.mkdirs()
            val paragraphs = (listOf(title) + bodyText.lines()).joinToString("") { line ->
                val escaped = line
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                "<w:p><w:r><w:t xml:space=\"preserve\">$escaped</w:t></w:r></w:p>"
            }

            val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

            val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

            val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>$paragraphs<w:sectPr/></w:body>
</w:document>"""

            ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
                zip.putNextEntry(ZipEntry("[Content_Types].xml"))
                zip.write(contentTypes.toByteArray())
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("_rels/.rels"))
                zip.write(rels.toByteArray())
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(documentXml.toByteArray())
                zip.closeEntry()
            }
            outputFile
        }

    suspend fun saveImageToMediaStore(context: Context, imageFile: File, displayName: String): Uri? =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScanNest")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
            resolver.openOutputStream(uri)?.use { out ->
                imageFile.inputStream().use { it.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        }
}
