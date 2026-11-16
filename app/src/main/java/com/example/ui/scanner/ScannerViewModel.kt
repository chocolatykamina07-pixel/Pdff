package com.example.ui.scanner

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.ScanNestRepository
import com.example.domain.model.CropQuad
import com.example.domain.model.FilterType
import com.example.domain.model.FlashMode
import com.example.domain.model.PageSizeOption
import com.example.domain.model.ScanMode
import com.example.ocr.ScannedBarcodeResult
import com.example.pdf.PdfEngine
import com.example.processing.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ScannerUiState(
    val scanMode: ScanMode = ScanMode.DOCUMENT,
    val flashMode: FlashMode = FlashMode.AUTO,
    val isHdEnabled: Boolean = true,
    val showGrid: Boolean = true,
    val aspectPreset: PageSizeOption = PageSizeOption.A4,
    val isBatchMode: Boolean = false,
    val autoCaptureEnabled: Boolean = true,
    val soundAndHapticsEnabled: Boolean = true,
    val detectedQuad: CropQuad? = null,
    val stabilityProgress: Float = 0f,
    val capturedFiles: List<File> = emptyList(),
    val idCardBothSides: Boolean = true,
    val idFrontFile: File? = null,
    val idBackFile: File? = null,
    val qrResult: ScannedBarcodeResult? = null,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null
)

class ScannerViewModel(
    private val context: Context,
    private val repository: ScanNestRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = preferencesManager.preferencesFlow.first()
            _uiState.update {
                it.copy(
                    scanMode = prefs.defaultScanMode,
                    aspectPreset = prefs.defaultPageSize,
                    autoCaptureEnabled = prefs.autoCapture,
                    soundAndHapticsEnabled = prefs.soundAndHaptics
                )
            }
        }
    }

    fun selectMode(mode: ScanMode) {
        _uiState.update {
            it.copy(
                scanMode = mode,
                idFrontFile = null,
                idBackFile = null,
                qrResult = null
            )
        }
    }

    fun setIdCardBothSides(bothSides: Boolean) {
        _uiState.update { it.copy(idCardBothSides = bothSides, idFrontFile = null, idBackFile = null) }
    }

    fun cycleFlash() {
        _uiState.update {
            val next = when (it.flashMode) {
                FlashMode.AUTO -> FlashMode.ON
                FlashMode.ON -> FlashMode.OFF
                FlashMode.OFF -> FlashMode.AUTO
            }
            it.copy(flashMode = next)
        }
    }

    fun toggleHd() {
        _uiState.update { it.copy(isHdEnabled = !it.isHdEnabled) }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(showGrid = !it.showGrid) }
    }

    fun setAspectPreset(preset: PageSizeOption) {
        _uiState.update { it.copy(aspectPreset = preset) }
    }

    fun setBatchMode(batch: Boolean) {
        _uiState.update { it.copy(isBatchMode = batch) }
    }

    fun updateLiveDetection(quad: CropQuad?, stability: Float) {
        _uiState.update { it.copy(detectedQuad = quad, stabilityProgress = stability) }
    }

    fun setQrResult(result: ScannedBarcodeResult?) {
        _uiState.update { it.copy(qrResult = result) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun resetSession() {
        _uiState.update {
            it.copy(
                capturedFiles = emptyList(),
                idFrontFile = null,
                idBackFile = null,
                qrResult = null,
                isProcessing = false
            )
        }
    }

    fun onPhotoCaptured(
        file: File,
        appendToDocumentId: Long?,
        onNavigateToCrop: (documentId: Long, firstPageId: Long) -> Unit,
        onNavigateToEditor: (documentId: Long) -> Unit
    ) {
        val state = _uiState.value
        if (state.scanMode == ScanMode.ID_CARD && state.idCardBothSides) {
            handleIdCardCapture(file, appendToDocumentId, onNavigateToEditor)
            return
        }

        if (state.isBatchMode && state.scanMode != ScanMode.ID_CARD) {
            _uiState.update { it.copy(capturedFiles = it.capturedFiles + file) }
        } else {
            finalizeCaptures(
                files = listOf(file),
                appendToDocumentId = appendToDocumentId,
                goToCrop = appendToDocumentId == null,
                onNavigateToCrop = onNavigateToCrop,
                onNavigateToEditor = onNavigateToEditor
            )
        }
    }

    private fun handleIdCardCapture(
        file: File,
        appendToDocumentId: Long?,
        onNavigateToEditor: (documentId: Long) -> Unit
    ) {
        val currentFront = _uiState.value.idFrontFile
        if (currentFront == null) {
            _uiState.update { it.copy(idFrontFile = file) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val combinedFile = withContext(Dispatchers.IO) {
                val frontBmp = ImageProcessor.decodeSampledBitmap(currentFront.absolutePath, 1600)
                val backBmp = ImageProcessor.decodeSampledBitmap(file.absolutePath, 1600)
                if (frontBmp == null || backBmp == null) return@withContext null
                val frontQuad = com.example.camera.EdgeDetector.detectEdges(frontBmp)
                val backQuad = com.example.camera.EdgeDetector.detectEdges(backBmp)
                val frontCropped = ImageProcessor.applyPerspectiveCrop(frontBmp, frontQuad)
                val backCropped = ImageProcessor.applyPerspectiveCrop(backBmp, backQuad)
                frontBmp.recycle()
                backBmp.recycle()

                val combined = ImageProcessor.combineIdCardFrontBack(frontCropped, backCropped)
                frontCropped.recycle()
                backCropped.recycle()

                val outFile = File(context.cacheDir, "id_combined_${System.currentTimeMillis()}.jpg")
                ImageProcessor.saveBitmapJpeg(combined, outFile, 94)
                combined.recycle()
                outFile
            }
            currentFront.delete()
            file.delete()
            if (combinedFile != null) {
                val docId = if (appendToDocumentId != null) {
                    repository.appendImageFilesToDocument(appendToDocumentId, listOf(combinedFile), FilterType.ORIGINAL)
                    appendToDocumentId
                } else {
                    repository.createDocumentFromImageFiles(
                        name = repository.defaultDocumentName("ID Card"),
                        sourceFiles = listOf(combinedFile),
                        filterType = FilterType.ORIGINAL,
                        autoDetectCrop = false
                    )
                }
                combinedFile.delete()
                _uiState.update {
                    it.copy(isProcessing = false, idFrontFile = null, idBackFile = null)
                }
                onNavigateToEditor(docId)
            } else {
                _uiState.update { it.copy(isProcessing = false, errorMessage = "Failed to combine ID card images") }
            }
        }
    }

    fun finishBatchCapture(
        appendToDocumentId: Long?,
        onNavigateToCrop: (documentId: Long, firstPageId: Long) -> Unit,
        onNavigateToEditor: (documentId: Long) -> Unit
    ) {
        val files = _uiState.value.capturedFiles
        if (files.isEmpty()) return
        finalizeCaptures(
            files = files,
            appendToDocumentId = appendToDocumentId,
            goToCrop = false,
            onNavigateToCrop = onNavigateToCrop,
            onNavigateToEditor = onNavigateToEditor
        )
    }

    private fun finalizeCaptures(
        files: List<File>,
        appendToDocumentId: Long?,
        goToCrop: Boolean,
        onNavigateToCrop: (documentId: Long, firstPageId: Long) -> Unit,
        onNavigateToEditor: (documentId: Long) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val prefs = preferencesManager.preferencesFlow.first()
            val filter = when (_uiState.value.scanMode) {
                ScanMode.PHOTO -> FilterType.ORIGINAL
                ScanMode.WHITEBOARD -> FilterType.CLEAN_DOC
                else -> prefs.defaultFilter
            }
            val docId = if (appendToDocumentId != null) {
                repository.appendImageFilesToDocument(appendToDocumentId, files, filter)
                appendToDocumentId
            } else {
                repository.createDocumentFromImageFiles(
                    name = repository.defaultDocumentName(_uiState.value.scanMode.label),
                    sourceFiles = files,
                    filterType = filter,
                    autoDetectCrop = _uiState.value.scanMode != ScanMode.PHOTO
                )
            }
            files.forEach { it.delete() }
            val pages = repository.getPages(docId)
            _uiState.update { it.copy(isProcessing = false, capturedFiles = emptyList()) }
            if (goToCrop && pages.isNotEmpty()) {
                onNavigateToCrop(docId, pages.first().id)
            } else {
                onNavigateToEditor(docId)
            }
        }
    }

    fun importGalleryImages(
        uris: List<Uri>,
        appendToDocumentId: Long?,
        onNavigateToCrop: (documentId: Long, firstPageId: Long) -> Unit,
        onNavigateToEditor: (documentId: Long) -> Unit
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val prefs = preferencesManager.preferencesFlow.first()
            val docId = if (appendToDocumentId != null) {
                val files = withContext(Dispatchers.IO) {
                    uris.mapIndexedNotNull { idx, uri ->
                        val bmp = ImageProcessor.decodeUriSampled(context, uri, 2200) ?: return@mapIndexedNotNull null
                        val f = File(context.cacheDir, "gal_${System.currentTimeMillis()}_$idx.jpg")
                        ImageProcessor.saveBitmapJpeg(bmp, f, 94)
                        bmp.recycle()
                        f
                    }
                }
                repository.appendImageFilesToDocument(appendToDocumentId, files, prefs.defaultFilter)
                files.forEach { it.delete() }
                appendToDocumentId
            } else {
                repository.importUrisAsDocument(
                    name = repository.defaultDocumentName("Import"),
                    uris = uris,
                    filterType = prefs.defaultFilter
                )
            }
            _uiState.update { it.copy(isProcessing = false) }
            if (docId != null) {
                val pages = repository.getPages(docId)
                if (uris.size == 1 && appendToDocumentId == null && pages.isNotEmpty()) {
                    onNavigateToCrop(docId, pages.first().id)
                } else {
                    onNavigateToEditor(docId)
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Could not decode selected images") }
            }
        }
    }

    fun importPdfFile(
        uri: Uri,
        appendToDocumentId: Long?,
        onNavigateToEditor: (documentId: Long) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val rendered = runCatching {
                PdfEngine.renderPdfPagesToBitmaps(context, uri, targetWidthPx = 1500)
            }.getOrElse { emptyList() }
            if (rendered.isEmpty()) {
                _uiState.update { it.copy(isProcessing = false, errorMessage = "Unable to read PDF pages") }
                return@launch
            }
            val docId = if (appendToDocumentId != null) {
                repository.appendImageFilesToDocument(appendToDocumentId, rendered, FilterType.ORIGINAL)
                appendToDocumentId
            } else {
                repository.createDocumentFromImageFiles(
                    name = repository.defaultDocumentName("PDF Import"),
                    sourceFiles = rendered,
                    filterType = FilterType.ORIGINAL,
                    autoDetectCrop = false
                )
            }
            rendered.forEach { it.delete() }
            _uiState.update { it.copy(isProcessing = false) }
            onNavigateToEditor(docId)
        }
    }
}
