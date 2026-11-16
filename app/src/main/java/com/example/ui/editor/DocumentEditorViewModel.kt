package com.example.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DocumentEntity
import com.example.data.local.PageEntity
import com.example.data.local.SignatureEntity
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.ScanNestRepository
import com.example.domain.model.CropQuad
import com.example.domain.model.ExportFormat
import com.example.domain.model.ExportRequest
import com.example.domain.model.FilterType
import com.example.domain.model.OcrLanguage
import com.example.domain.model.OcrPageResult
import com.example.ocr.OcrEngine
import com.example.pdf.PdfEngine
import com.example.processing.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ExportProgressState(
    val isExporting: Boolean = false,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val exportedFile: File? = null,
    val exportedFormat: ExportFormat? = null,
    val error: String? = null
)

data class OcrUiState(
    val isRunning: Boolean = false,
    val language: OcrLanguage = OcrLanguage.LATIN,
    val extractedText: String = "",
    val blocksCount: Int = 0,
    val error: String? = null
)

class DocumentEditorViewModel(
    private val context: Context,
    private val repository: ScanNestRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val activeDocId = MutableStateFlow<Long?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeDocument: StateFlow<DocumentEntity?> = activeDocId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeDocument(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pages: StateFlow<List<PageEntity>> = activeDocId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observePages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val signatures: StateFlow<List<SignatureEntity>> = repository.allSignatures
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val preferences = preferencesManager.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.preferences.UserPreferences())

    private val _exportState = MutableStateFlow(ExportProgressState())
    val exportState: StateFlow<ExportProgressState> = _exportState.asStateFlow()

    private val _ocrState = MutableStateFlow(OcrUiState())
    val ocrState: StateFlow<OcrUiState> = _ocrState.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private var exportJob: Job? = null

    fun loadDocument(documentId: Long) {
        activeDocId.value = documentId
    }

    suspend fun getPage(pageId: Long): PageEntity? = repository.getPage(pageId)

    fun decodeCropQuad(json: String): CropQuad = repository.decodeCropQuad(json)

    fun renameDocument(documentId: Long, newName: String) {
        viewModelScope.launch { repository.renameDocument(documentId, newName) }
    }

    fun rotatePage(pageId: Long, deltaDegrees: Int = 90) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.rotatePageQuick(pageId, deltaDegrees)
            _isBusy.value = false
        }
    }

    fun deletePage(pageId: Long) {
        viewModelScope.launch { repository.deletePage(pageId) }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val docId = activeDocId.value ?: return
        val current = pages.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        viewModelScope.launch {
            repository.reorderPages(docId, current.map { it.id })
        }
    }

    fun addGalleryPages(documentId: Long, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isBusy.value = true
            val files = withContext(Dispatchers.IO) {
                uris.mapIndexedNotNull { idx, uri ->
                    val bmp = ImageProcessor.decodeUriSampled(context, uri, 2200) ?: return@mapIndexedNotNull null
                    val f = File(context.cacheDir, "add_${System.currentTimeMillis()}_$idx.jpg")
                    ImageProcessor.saveBitmapJpeg(bmp, f, 94)
                    bmp.recycle()
                    f
                }
            }
            repository.appendImageFilesToDocument(documentId, files)
            files.forEach { it.delete() }
            _isBusy.value = false
        }
    }

    fun saveCropAndTransform(
        pageId: Long,
        quad: CropQuad,
        rotationDegrees: Int,
        flipHorizontal: Boolean,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updatePageCropAndTransform(pageId, quad, rotationDegrees, flipHorizontal)
            _isBusy.value = false
            onComplete()
        }
    }

    fun saveFilters(
        pageId: Long,
        filterType: FilterType,
        brightness: Float,
        contrast: Float,
        sharpness: Float,
        removeShadows: Boolean,
        removeBlemishes: Boolean,
        applyToAll: Boolean,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updatePageFilterSettings(
                pageId = pageId,
                filterType = filterType,
                brightness = brightness,
                contrast = contrast,
                sharpness = sharpness,
                removeShadows = removeShadows,
                removeBlemishes = removeBlemishes,
                applyToAllPages = applyToAll
            )
            _isBusy.value = false
            onComplete()
        }
    }

    fun applyWatermark(documentId: Long, text: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.applyWatermarkToDocument(documentId, text)
            _isBusy.value = false
        }
    }

    fun saveSignature(name: String, bitmap: Bitmap) {
        viewModelScope.launch {
            repository.saveSignature(name, bitmap)
        }
    }

    fun deleteSignature(signature: SignatureEntity) {
        viewModelScope.launch { repository.deleteSignature(signature) }
    }

    fun saveAnnotatedPage(pageId: Long, flattenedBitmap: Bitmap, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.saveFlattenedPageBitmap(pageId, flattenedBitmap)
            _isBusy.value = false
            onComplete()
        }
    }

    fun setOcrLanguage(language: OcrLanguage) {
        _ocrState.update { it.copy(language = language) }
    }

    fun updateOcrEditableText(text: String) {
        _ocrState.update { it.copy(extractedText = text) }
    }

    fun runOcr(documentId: Long, pageId: Long?, wholeDocument: Boolean) {
        viewModelScope.launch {
            _ocrState.update { it.copy(isRunning = true, error = null) }
            val lang = _ocrState.value.language
            val resultText = withContext(Dispatchers.IO) {
                val targetPages = if (wholeDocument || pageId == null) {
                    repository.getPages(documentId)
                } else {
                    listOfNotNull(repository.getPage(pageId))
                }
                val builder = StringBuilder()
                var totalBlocks = 0
                for (p in targetPages) {
                    val bmp = ImageProcessor.decodeSampledBitmap(p.processedImagePath, 1800) ?: continue
                    val res: OcrPageResult = OcrEngine.recognizeBitmap(bmp, p.pageIndex, lang)
                    bmp.recycle()
                    totalBlocks += res.blocks.size
                    if (res.fullText.isNotBlank()) {
                        if (targetPages.size > 1) {
                            builder.append("--- Page ${p.pageIndex + 1} ---\n")
                        }
                        builder.append(res.fullText).append("\n\n")
                    }
                    repository.updateOcrText(documentId, p.id, res.fullText)
                }
                builder.toString().trim() to totalBlocks
            }
            _ocrState.update {
                it.copy(
                    isRunning = false,
                    extractedText = resultText.first.ifBlank { "No readable text detected." },
                    blocksCount = resultText.second
                )
            }
        }
    }

    fun saveOcrEditsToDocument(documentId: Long, pageId: Long?) {
        viewModelScope.launch {
            repository.updateOcrText(documentId, pageId, _ocrState.value.extractedText)
        }
    }

    fun estimateExportSize(request: ExportRequest): Long {
        return PdfEngine.estimateExportSizeBytes(
            pages = pages.value,
            request = request,
            ocrText = activeDocument.value?.ocrText
        )
    }

    fun startExport(request: ExportRequest, pageIndexForJpeg: Int = 0) {
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            val doc = repository.getDocument(request.documentId) ?: return@launch
            val docPages = repository.getPages(request.documentId)
            if (docPages.isEmpty()) return@launch

            val safeName = doc.name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "ScanNest_Doc" }
            val outDir = repository.exportsCacheDir()
            val outFile = File(outDir, "${safeName}.${request.format.extension}")

            _exportState.value = ExportProgressState(
                isExporting = true,
                currentPage = 0,
                totalPages = docPages.size
            )

            runCatching {
                when (request.format) {
                    ExportFormat.PDF -> {
                        PdfEngine.createPdfFromPages(
                            context = context,
                            pages = docPages,
                            outputFile = outFile,
                            request = request,
                            ocrLanguage = _ocrState.value.language,
                            onProgress = { cur, tot ->
                                _exportState.update { it.copy(currentPage = cur, totalPages = tot) }
                            }
                        )
                    }
                    ExportFormat.JPEG -> {
                        withContext(Dispatchers.IO) {
                            val page = docPages.getOrElse(pageIndexForJpeg) { docPages.first() }
                            val bmp = ImageProcessor.decodeSampledBitmap(
                                page.processedImagePath,
                                request.quality.maxDimensionPx
                            ) ?: error("Unable to load page image")
                            ImageProcessor.saveBitmapJpeg(bmp, outFile, request.quality.jpegQuality)
                            bmp.recycle()
                            outFile
                        }
                    }
                    ExportFormat.TXT -> {
                        val text = doc.ocrText ?: _ocrState.value.extractedText.ifBlank {
                            runQuickOcrAll(docPages)
                        }
                        PdfEngine.exportTxt(text, outFile)
                    }
                    ExportFormat.DOCX -> {
                        val text = doc.ocrText ?: _ocrState.value.extractedText.ifBlank {
                            runQuickOcrAll(docPages)
                        }
                        PdfEngine.exportDocx(doc.name, text, outFile)
                    }
                }
            }.onSuccess { resultFile ->
                val prefs = preferences.value
                if (prefs.autoBackupEnabled && !prefs.backupFolderUri.isNullOrBlank()) {
                    repository.backupFileToFolderUri(
                        prefs.backupFolderUri,
                        resultFile,
                        request.format.mimeType
                    )
                }
                _exportState.value = ExportProgressState(
                    isExporting = false,
                    currentPage = docPages.size,
                    totalPages = docPages.size,
                    exportedFile = resultFile,
                    exportedFormat = request.format
                )
            }.onFailure { err ->
                _exportState.value = ExportProgressState(
                    isExporting = false,
                    error = err.localizedMessage ?: "Export failed"
                )
            }
        }
    }

    private suspend fun runQuickOcrAll(docPages: List<PageEntity>): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        for (p in docPages) {
            val bmp = ImageProcessor.decodeSampledBitmap(p.processedImagePath, 1600) ?: continue
            val res = runCatching { OcrEngine.recognizeBitmap(bmp, p.pageIndex, _ocrState.value.language) }.getOrNull()
            bmp.recycle()
            if (res != null && res.fullText.isNotBlank()) {
                sb.append(res.fullText).append("\n\n")
            }
        }
        sb.toString().trim().ifBlank { "No text extracted." }
    }

    fun cancelExport() {
        exportJob?.cancel()
        _exportState.value = ExportProgressState()
    }

    fun clearExportResult() {
        _exportState.value = ExportProgressState()
    }
}
