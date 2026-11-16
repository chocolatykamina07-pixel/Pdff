package com.example.ui.scanner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.media.MediaActionSound
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.GridOff
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.camera.EdgeDetector
import com.example.camera.QuadStabilityTracker
import com.example.domain.model.CropQuad
import com.example.domain.model.FlashMode
import com.example.domain.model.PageSizeOption
import com.example.domain.model.ScanMode
import com.example.ocr.OcrEngine
import com.example.ocr.ScannedBarcodeResult
import com.example.ui.components.FormatUtils
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.DeepTeal
import com.example.ui.theme.PillShape
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    initialMode: ScanMode?,
    appendToDocumentId: Long?,
    onClose: () -> Unit,
    onNavigateToCrop: (documentId: Long, firstPageId: Long) -> Unit,
    onNavigateToEditor: (documentId: Long) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialMode) {
        viewModel.resetSession()
        if (initialMode != null) viewModel.selectMode(initialMode)
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importGalleryImages(uris, appendToDocumentId, onNavigateToCrop, onNavigateToEditor)
        }
    }

    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            if (mime.contains("pdf")) {
                viewModel.importPdfFile(uri, appendToDocumentId, onNavigateToEditor)
            } else {
                viewModel.importGalleryImages(listOf(uri), appendToDocumentId, onNavigateToCrop, onNavigateToEditor)
            }
        }
    }

    val imageCapture = remember(state.isHdEnabled, state.flashMode) {
        ImageCapture.Builder()
            .setCaptureMode(
                if (state.isHdEnabled) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            )
            .setFlashMode(
                when (state.flashMode) {
                    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                    FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                    FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                }
            )
            .build()
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val stabilityTracker = remember { QuadStabilityTracker() }
    val mediaActionSound = remember { runCatching { MediaActionSound() }.getOrNull() }
    var lastAutoCaptureMs by remember { mutableLongStateOf(0L) }
    var shutterPulse by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            mediaActionSound?.release()
        }
    }

    fun playShutterFeedback() {
        if (state.soundAndHapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager?.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                runCatching { mediaActionSound?.play(MediaActionSound.SHUTTER_CLICK) }
            }
        }
    }

    fun capturePhoto() {
        if (state.isProcessing) return
        shutterPulse = true
        playShutterFeedback()
        val outFile = File(context.cacheDir, "scan_cap_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    shutterPulse = false
                    stabilityTracker.reset()
                    viewModel.onPhotoCaptured(
                        file = outFile,
                        appendToDocumentId = appendToDocumentId,
                        onNavigateToCrop = onNavigateToCrop,
                        onNavigateToEditor = onNavigateToEditor
                    )
                }

                override fun onError(exception: ImageCaptureException) {
                    shutterPulse = false
                }
            }
        )
    }

    // Auto-capture when stability reaches 1.0f
    LaunchedEffect(state.stabilityProgress, state.autoCaptureEnabled, state.scanMode) {
        if (state.autoCaptureEnabled &&
            state.scanMode != ScanMode.QR_BARCODE &&
            state.scanMode != ScanMode.PHOTO &&
            state.stabilityProgress >= 1f
        ) {
            val now = System.currentTimeMillis()
            if (now - lastAutoCaptureMs > 2200L) {
                lastAutoCaptureMs = now
                capturePhoto()
            }
        }
    }

    var aspectMenuOpen by remember { mutableStateOf(false) }
    var cameraAvailable by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission && cameraAvailable) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        runCatching {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setTargetResolution(Size(640, 480))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            val barcodeScanner = BarcodeScanning.getClient()
                            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                if (state.scanMode == ScanMode.QR_BARCODE) {
                                    analyzeBarcodeFrame(imageProxy, barcodeScanner) { result ->
                                        if (state.qrResult == null) {
                                            viewModel.setQrResult(result)
                                        }
                                    }
                                } else if (state.scanMode != ScanMode.PHOTO) {
                                    val yPlane = imageProxy.planes.firstOrNull()
                                    if (yPlane != null) {
                                        val buf = yPlane.buffer
                                        val bytes = ByteArray(buf.remaining())
                                        buf.get(bytes)
                                        val quad = EdgeDetector.detectEdgesFromLuma(
                                            yBytes = bytes,
                                            srcWidth = imageProxy.width,
                                            srcHeight = imageProxy.height,
                                            rowStride = yPlane.rowStride,
                                            rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                        )
                                        val stability = stabilityTracker.update(quad)
                                        viewModel.updateLiveDetection(quad, stability)
                                    }
                                    imageProxy.close()
                                } else {
                                    viewModel.updateLiveDetection(null, 0f)
                                    imageProxy.close()
                                }
                            }

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture,
                                imageAnalysis
                            )
                        }.onFailure {
                            cameraAvailable = false
                        }
                    }, ContextCompat.getMainExecutor(context))
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Permission denied or emulator without camera hardware fallback
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    tint = AmberAccent,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (!hasCameraPermission) "Camera access needed for live scanning"
                    else "Camera preview unavailable on this device",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "You can grant camera permission or import photos and PDFs directly from your device.",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(20.dp))
                if (!hasCameraPermission) {
                    Button(
                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        shape = PillShape
                    ) {
                        Text("Allow Camera")
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = PillShape
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Import from Gallery", color = Color.White)
                }
            }
        }

        // Animated Edge Detection + Grid Overlay
        ScanFrameOverlay(
            detectedQuad = state.detectedQuad,
            showGrid = state.showGrid,
            stabilityProgress = state.stabilityProgress,
            scanMode = state.scanMode
        )

        // ID Card Mode Banner
        if (state.scanMode == ScanMode.ID_CARD) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = PillShape,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp)
            ) {
                Text(
                    text = if (state.idFrontFile == null) {
                        stringResource(R.string.id_front_prompt)
                    } else {
                        stringResource(R.string.id_back_prompt)
                    },
                    color = AmberAccent,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Top Bar Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("scanner_close_button")
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close scanner", tint = Color.White)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::cycleFlash) {
                    Icon(
                        imageVector = when (state.flashMode) {
                            FlashMode.AUTO -> Icons.Outlined.FlashAuto
                            FlashMode.ON -> Icons.Outlined.FlashOn
                            FlashMode.OFF -> Icons.Outlined.FlashOff
                        },
                        contentDescription = "Flash mode",
                        tint = if (state.flashMode == FlashMode.ON) AmberAccent else Color.White
                    )
                }
                IconButton(onClick = viewModel::toggleHd) {
                    Icon(
                        Icons.Outlined.Hd,
                        contentDescription = "Toggle HD quality",
                        tint = if (state.isHdEnabled) AmberAccent else Color.White.copy(alpha = 0.7f)
                    )
                }
                Box {
                    IconButton(onClick = { aspectMenuOpen = true }) {
                        Icon(Icons.Outlined.AspectRatio, contentDescription = "Page aspect", tint = Color.White)
                    }
                    DropdownMenu(expanded = aspectMenuOpen, onDismissRequest = { aspectMenuOpen = false }) {
                        listOf(PageSizeOption.AUTO, PageSizeOption.A4, PageSizeOption.LETTER, PageSizeOption.ID_CARD).forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.label) },
                                onClick = {
                                    viewModel.setAspectPreset(preset)
                                    aspectMenuOpen = false
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = viewModel::toggleGrid) {
                    Icon(
                        if (state.showGrid) Icons.Outlined.GridOn else Icons.Outlined.GridOff,
                        contentDescription = "Toggle grid",
                        tint = if (state.showGrid) AmberAccent else Color.White
                    )
                }
            }
        }

        // Bottom / Landscape-Right Controls
        val controlsModifier = if (isLandscape) {
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(220.dp)
                .background(Color.Black.copy(alpha = 0.65f))
                .navigationBarsPadding()
                .padding(12.dp)
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f), Color.Black.copy(alpha = 0.94f))
                    )
                )
                .navigationBarsPadding()
                .padding(bottom = 14.dp, top = 12.dp)
        }

        Column(
            modifier = controlsModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isLandscape) Arrangement.SpaceEvenly else Arrangement.spacedBy(10.dp)
        ) {
            // Mode Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScanMode.entries.forEach { mode ->
                    val selected = state.scanMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectMode(mode) },
                        label = { Text(mode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberAccent,
                            selectedLabelColor = Color(0xFF0F172A),
                            containerColor = Color.White.copy(alpha = 0.14f),
                            labelColor = Color.White
                        ),
                        border = null
                    )
                }
            }

            // Single / Batch Toggle
            if (state.scanMode != ScanMode.QR_BARCODE && state.scanMode != ScanMode.ID_CARD) {
                Surface(
                    shape = PillShape,
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        listOf(false to stringResource(R.string.single_mode), true to stringResource(R.string.batch_mode)).forEach { (batch, label) ->
                            val active = state.isBatchMode == batch
                            Surface(
                                shape = PillShape,
                                color = if (active) DeepTeal else Color.Transparent,
                                modifier = Modifier.clickable { viewModel.setBatchMode(batch) }
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Main Action Row: Import Gallery / Import Files / Shutter / Last-Capture Thumb
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("scanner_import_gallery")
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Import from gallery", tint = Color.White)
                }

                IconButton(
                    onClick = { fileImportLauncher.launch(arrayOf("application/pdf", "image/*")) },
                    modifier = Modifier.testTag("scanner_import_files")
                ) {
                    Icon(Icons.Outlined.FileOpen, contentDescription = "Import files", tint = Color.White)
                }

                // Shutter Button with Scale-Pulse
                val shutterScale by animateFloatAsState(
                    targetValue = if (shutterPulse) 0.86f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "shutter_scale"
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .scale(shutterScale)
                        .clip(CircleShape)
                        .border(4.dp, AmberAccent, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(enabled = hasCameraPermission && cameraAvailable && !state.isProcessing) {
                            capturePhoto()
                        }
                        .testTag("scanner_shutter_button")
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = DeepTeal)
                    }
                }

                // Batch Thumbnail + Done
                if (state.capturedFiles.isNotEmpty()) {
                    BadgedBox(
                        badge = {
                            Badge(containerColor = AmberAccent, contentColor = Color.Black) {
                                Text("${state.capturedFiles.size}")
                            }
                        }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(2.dp, Color.White),
                            modifier = Modifier
                                .size(48.dp)
                                .clickable {
                                    viewModel.finishBatchCapture(
                                        appendToDocumentId,
                                        onNavigateToCrop,
                                        onNavigateToEditor
                                    )
                                }
                                .testTag("scanner_batch_done")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = state.capturedFiles.last(),
                                    contentDescription = "Finish batch",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = "Finish batch",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .background(DeepTeal.copy(alpha = 0.7f), CircleShape)
                                        .padding(4.dp)
                                        .size(18.dp)
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }
        }

        // QR / Barcode Result Bottom Sheet
        state.qrResult?.let { qr ->
            ModalBottomSheet(onDismissRequest = { viewModel.setQrResult(null) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.QrCode2, contentDescription = null, tint = DeepTeal)
                        Spacer(Modifier.width(10.dp))
                        Text(qr.formatLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = qr.displayValue,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("QR Result", qr.rawValue))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy")
                        }
                        if (qr.isUrl) {
                            Button(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(qr.rawValue)))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open")
                            }
                        }
                        OutlinedButton(
                            onClick = { FormatUtils.shareText(context, qr.rawValue) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanFrameOverlay(
    detectedQuad: CropQuad?,
    showGrid: Boolean,
    stabilityProgress: Float,
    scanMode: ScanMode
) {
    val infinite = rememberInfiniteTransition(label = "scanner_sweep")
    val scanLineY by infinite.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.80f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "scan_line_y"
    )

    // Spring-morph detected quad corners
    val q = detectedQuad ?: CropQuad(
        com.example.domain.model.NormalizedPoint(0.12f, 0.16f),
        com.example.domain.model.NormalizedPoint(0.88f, 0.16f),
        com.example.domain.model.NormalizedPoint(0.88f, 0.78f),
        com.example.domain.model.NormalizedPoint(0.12f, 0.78f)
    )

    val tlX by animateFloatAsState(q.topLeft.x, spring(stiffness = Spring.StiffnessMediumLow), label = "tlx")
    val tlY by animateFloatAsState(q.topLeft.y, spring(stiffness = Spring.StiffnessMediumLow), label = "tly")
    val trX by animateFloatAsState(q.topRight.x, spring(stiffness = Spring.StiffnessMediumLow), label = "trx")
    val trY by animateFloatAsState(q.topRight.y, spring(stiffness = Spring.StiffnessMediumLow), label = "try")
    val brX by animateFloatAsState(q.bottomRight.x, spring(stiffness = Spring.StiffnessMediumLow), label = "brx")
    val brY by animateFloatAsState(q.bottomRight.y, spring(stiffness = Spring.StiffnessMediumLow), label = "bry")
    val blX by animateFloatAsState(q.bottomLeft.x, spring(stiffness = Spring.StiffnessMediumLow), label = "blx")
    val blY by animateFloatAsState(q.bottomLeft.y, spring(stiffness = Spring.StiffnessMediumLow), label = "bly")

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        if (showGrid) {
            val gridColor = Color.White.copy(alpha = 0.22f)
            drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.5f)
            drawLine(gridColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = 1.5f)
            drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.5f)
            drawLine(gridColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = 1.5f)
        }

        if (scanMode != ScanMode.PHOTO) {
            val tl = Offset(tlX * w, tlY * h)
            val tr = Offset(trX * w, trY * h)
            val br = Offset(brX * w, brY * h)
            val bl = Offset(blX * w, blY * h)

            val poly = Path().apply {
                moveTo(tl.x, tl.y)
                lineTo(tr.x, tr.y)
                lineTo(br.x, br.y)
                lineTo(bl.x, bl.y)
                close()
            }

            if (detectedQuad != null) {
                drawPath(poly, color = DeepTeal.copy(alpha = 0.18f + stabilityProgress * 0.14f))
                drawPath(poly, color = AmberAccent, style = Stroke(width = 4.5f, join = StrokeJoin.Round))
            } else {
                // Soft viewfinder brackets
                val bracketLen = 36f
                val stroke = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(Path().apply {
                    moveTo(tl.x, tl.y + bracketLen); lineTo(tl.x, tl.y); lineTo(tl.x + bracketLen, tl.y)
                    moveTo(tr.x - bracketLen, tr.y); lineTo(tr.x, tr.y); lineTo(tr.x, tr.y + bracketLen)
                    moveTo(br.x, br.y - bracketLen); lineTo(br.x, br.y); lineTo(br.x - bracketLen, br.y)
                    moveTo(bl.x + bracketLen, bl.y); lineTo(bl.x, bl.y); lineTo(bl.x, bl.y - bracketLen)
                }, color = AmberAccent.copy(alpha = 0.9f), style = stroke)
            }

            // Soft teal scan-line sweep
            val sy = h * scanLineY
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color(0xFF14B8A6).copy(alpha = 0.75f), Color.Transparent)
                ),
                start = Offset(w * 0.1f, sy),
                end = Offset(w * 0.9f, sy),
                strokeWidth = 4f
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun analyzeBarcodeFrame(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onDetected: (ScannedBarcodeResult) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            val first = barcodes.firstOrNull()
            val raw = first?.rawValue
            if (first != null && raw != null) {
                onDetected(
                    ScannedBarcodeResult(
                        rawValue = raw,
                        displayValue = first.displayValue ?: raw,
                        isUrl = raw.startsWith("http://", true) || raw.startsWith("https://", true),
                        formatLabel = OcrEngine.formatBarcodeType(first.format)
                    )
                )
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
