package com.example.ui.tools

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ScanNestRepository
import com.example.domain.model.ExportQuality
import com.example.domain.model.FilterType
import com.example.domain.model.OcrLanguage
import com.example.domain.model.ToolType
import com.example.ocr.OcrEngine
import com.example.pdf.PdfEngine
import com.example.processing.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ToolWorkspaceState(
    val activeTool: ToolType? = null,
    val selectedUris: List<Uri> = emptyList(),
    val pdfPageCount: Int = 0,
    val splitRangeText: String = "1",
    val reorderPagesList: List<Int> = emptyList(),
    val compressQuality: ExportQuality = ExportQuality.MEDIUM,
    val pdfPassword: String = "",
    val protectModeEncrypt: Boolean = true,
    val importDocName: String = "",
    val ocrLanguage: OcrLanguage = OcrLanguage.LATIN,
    val ocrResultText: String? = null,
    val isProcessing: Boolean = false,
    val resultFile: File? = null,
    val resultImages: List<File> = emptyList(),
    val createdDocId: Long? = null,
    val originalSizeBytes: Long = 0L,
    val resultSizeBytes: Long = 0L,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class ToolsViewModel(
    private val context: Context,
    private val repository: ScanNestRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ToolWorkspaceState())
    val state: StateFlow<ToolWorkspaceState> = _state.asStateFlow()

    fun openTool(tool: ToolType) {
        _state.value = ToolWorkspaceState(activeTool = tool)
    }

    fun closeTool() {
        _state.value = ToolWorkspaceState()
    }

    fun setSelectedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val firstUri = uris.first()
            val size = queryUriSize(firstUri)
            val tool = _state.value.activeTool
            val pageCount = if (tool in setOf(
                    ToolType.SPLIT_PDF,
                    ToolType.COMPRESS_PDF,
                    ToolType.REORDER_DELETE,
                    ToolType.PROTECT_UNLOCK,
                    ToolType.PDF_TO_IMAGES
                )
            ) {
                runCatching { PdfEngine.getPdfPageCount(context, firstUri) }.getOrDefault(0)
            } else 0

            val initialRange = if (pageCount > 1) "1-$pageCount" else "1"
            val initialOrder = (1..pageCount).toList()

            _state.update {
                it.copy(
                    selectedUris = uris,
                    pdfPageCount = pageCount,
                    splitRangeText = initialRange,
                    reorderPagesList = initialOrder,
                    originalSizeBytes = size,
                    importDocName = repository.defaultDocumentName("Imported"),
                    resultFile = null,
                    resultImages = emptyList(),
                    createdDocId = null,
                    ocrResultText = null,
                    errorMessage = null,
                    statusMessage = null
                )
            }
        }
    }

    fun updateSplitRange(text: String) {
        _state.update { it.copy(splitRangeText = text) }
    }

    fun updateCompressQuality(q: ExportQuality) {
        _state.update { it.copy(compressQuality = q) }
    }

    fun updatePassword(pw: String) {
        _state.update { it.copy(pdfPassword = pw) }
    }

    fun setProtectModeEncrypt(encrypt: Boolean) {
        _state.update { it.copy(protectModeEncrypt = encrypt) }
    }

    fun updateImportDocName(name: String) {
        _state.update { it.copy(importDocName = name) }
    }

    fun setOcrLanguage(lang: OcrLanguage) {
        _state.update { it.copy(ocrLanguage = lang) }
    }

    fun moveReorderPage(fromIndex: Int, toIndex: Int) {
        val list = _state.value.reorderPagesList.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _state.update { it.copy(reorderPagesList = list) }
    }

    fun removeReorderPage(index: Int) {
        val list = _state.value.reorderPagesList.toMutableList()
        if (list.size <= 1 || index !in list.indices) return
        list.removeAt(index)
        _state.update { it.copy(reorderPagesList = list) }
    }

    fun executeActiveTool(onOpenDocument: (Long) -> Unit) {
        val current = _state.value
        val tool = current.activeTool ?: return
        if (current.selectedUris.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, errorMessage = null, statusMessage = null) }
            val exportDir = repository.exportsCacheDir()
            val ts = System.currentTimeMillis()

            runCatching {
                when (tool) {
                    ToolType.IMAGE_TO_PDF -> {
                        val docId = repository.importUrisAsDocument(
                            name = repository.defaultDocumentName("Images PDF"),
                            uris = current.selectedUris,
                            filterType = FilterType.AUTO_ENHANCE
                        ) ?: error("Could not import images")
                        val pages = repository.getPages(docId)
                        val outFile = File(exportDir, "ScanNest_Images_$ts.pdf")
                        PdfEngine.createPdfFromPages(
                            context = context,
                            pages = pages,
                            outputFile = outFile,
                            request = com.example.domain.model.ExportRequest(docId)
                        )
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                resultFile = outFile,
                                resultSizeBytes = outFile.length(),
                                createdDocId = docId,
                                statusMessage = "Created ${pages.size}-page PDF"
                            )
                        }
                    }

                    ToolType.MERGE_PDF -> {
                        if (current.selectedUris.size < 2) error("Select at least 2 PDFs to merge")
                        val outFile = File(exportDir, "ScanNest_Merged_$ts.pdf")
                        PdfEngine.mergePdfs(context, current.selectedUris, outFile)
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                resultFile = outFile,
                                resultSizeBytes = outFile.length(),
                                statusMessage = "Merged ${current.selectedUris.size} PDFs"
                            )
                        }
                    }

                    ToolType.SPLIT_PDF -> {
                        val pages = parsePageRange(current.splitRangeText, current.pdfPageCount)
                        if (pages.isEmpty()) error("Enter valid page numbers (1–${current.pdfPageCount})")
                        val outFile = File(exportDir, "ScanNest_Split_$ts.pdf")
                        PdfEngine.splitOrReorderPdf(context, current.selectedUris.first(), pages, outFile)
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                resultFile = outFile,
                                resultSizeBytes = outFile.length(),
                                statusMessage = "Extracted ${pages.size} pages"
                            )
                        }
                    }

                    ToolType.COMPRESS_PDF -> {
                        val outFile = File(exportDir, "ScanNest_Compressed_$ts.pdf")
                        PdfEngine.compressPdf(
                            context,
                            current.selectedUris.first(),
                            current.compressQuality,
                            outFile
                        )
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                resultFile = outFile,
                                resultSizeBytes = outFile.length(),
                                statusMessage = "Compressed successfully"
                            )
                        }
                    }

                    ToolType.REORDER_DELETE -> {
                        val order = current.reorderPagesList
                        if (order.isEmpty()) error("At least one page required")
                        val outFile = File(exportDir, "ScanNest_Reordered_$ts.pdf")
                        PdfEngine.splitOrReorderPdf(context, current.selectedUris.first(), order, outFile)
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                resultFile = outFile,
                                resultSizeBytes = outFile.length(),
                                statusMessage = "Saved ${order.size} reordered pages"
                            )
                        }
                    }

                    ToolType.PROTECT_UNLOCK -> {
                        val pw = current.pdfPassword
                        if (pw.isBlank()) error("Enter a password")
                        val outFile = if (current.protectModeEncrypt) {
                            File(exportDir, "ScanNest_Protected_$ts.pdf").also {
                                PdfEngine.protectPdf(context, current.selectedUris.first(), pw, it)
                            }
                        } else {
                            File(exportDir, "ScanNest_Unlocked_$ts.pdf").also {
                                PdfEngine.unlockPdf(context, current.selectedUris.first(), pw, it)
                            }
                        }
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                resultFile = outFile,
                                resultSizeBytes = outFile.length(),
                                statusMessage = if (current.protectModeEncrypt) "PDF password-protected" else "PDF unlocked"
                            )
                        }
                    }

                    ToolType.PDF_TO_IMAGES -> {
                        val imgs = PdfEngine.renderPdfPagesToBitmaps(context, current.selectedUris.first(), 1600)
                        if (imgs.isEmpty()) error("No pages rendered")
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                resultImages = imgs,
                                statusMessage = "Converted ${imgs.size} pages to JPEG"
                            )
                        }
                    }

                    ToolType.IMPORT_RENAME -> {
                        val uri = current.selectedUris.first()
                        val mime = context.contentResolver.getType(uri).orEmpty()
                        val name = current.importDocName.ifBlank { repository.defaultDocumentName("Imported") }
                        val docId = if (mime.contains("pdf")) {
                            val rendered = PdfEngine.renderPdfPagesToBitmaps(context, uri, 1500)
                            val id = repository.createDocumentFromImageFiles(name, rendered, filterType = FilterType.ORIGINAL, autoDetectCrop = false)
                            rendered.forEach { it.delete() }
                            id
                        } else {
                            repository.importUrisAsDocument(name, current.selectedUris, filterType = FilterType.ORIGINAL)
                                ?: error("Failed to import")
                        }
                        _state.update {
                            it.copy(isProcessing = false, createdDocId = docId, statusMessage = "Imported \"$name\"")
                        }
                        onOpenDocument(docId)
                    }

                    ToolType.ID_PHOTO_PACK -> {
                        val bmp = withContext(Dispatchers.IO) {
                            ImageProcessor.decodeUriSampled(context, current.selectedUris.first(), 1200)
                        } ?: error("Cannot decode image")
                        val pack = withContext(Dispatchers.IO) {
                            val p = ImageProcessor.createIdPhotoPack(bmp)
                            bmp.recycle()
                            val outFile = File(exportDir, "ScanNest_ID_Pack_$ts.jpg")
                            ImageProcessor.saveBitmapJpeg(p, outFile, 94)
                            p.recycle()
                            outFile
                        }
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                resultFile = pack,
                                resultSizeBytes = pack.length(),
                                statusMessage = "8-photo A4 sheet ready"
                            )
                        }
                    }

                    ToolType.EXTRACT_OCR -> {
                        val bmp = withContext(Dispatchers.IO) {
                            ImageProcessor.decodeUriSampled(context, current.selectedUris.first(), 1800)
                        } ?: error("Cannot decode image")
                        val res = OcrEngine.recognizeBitmap(bmp, 0, current.ocrLanguage)
                        bmp.recycle()
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                ocrResultText = res.fullText.ifBlank { "No text detected." },
                                statusMessage = "Extracted ${res.blocks.size} text blocks"
                            )
                        }
                    }

                    ToolType.QR_SCANNER -> Unit
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = err.localizedMessage ?: "Operation failed"
                    )
                }
            }
        }
    }

    private fun queryUriSize(uri: Uri): Long {
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        }.getOrDefault(0L)
    }

    private fun parsePageRange(input: String, maxPages: Int): List<Int> {
        if (maxPages <= 0) return emptyList()
        val result = mutableListOf<Int>()
        input.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
            if (part.contains("-")) {
                val bounds = part.split("-")
                val start = bounds.getOrNull(0)?.trim()?.toIntOrNull()
                val end = bounds.getOrNull(1)?.trim()?.toIntOrNull()
                if (start != null && end != null) {
                    val s = start.coerceIn(1, maxPages)
                    val e = end.coerceIn(1, maxPages)
                    if (s <= e) for (p in s..e) result.add(p)
                }
            } else {
                part.toIntOrNull()?.takeIf { it in 1..maxPages }?.let { result.add(it) }
            }
        }
        return result.distinct()
    }
}
