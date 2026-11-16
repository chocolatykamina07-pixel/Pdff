package com.example.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.SignatureEntity
import com.example.processing.ImageProcessor
import com.example.ui.theme.BottomSheetShape
import com.example.ui.theme.PillShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

private enum class AnnotationKind {
    SIGNATURE, TEXT, DATE, CHECKMARK, HIGHLIGHT
}

private data class OverlayAnnotation(
    val id: Long,
    val kind: AnnotationKind,
    val normX: Float = 0.5f,
    val normY: Float = 0.6f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val text: String = "",
    val colorInt: Int = AndroidColor.parseColor("#0F172A"),
    val signaturePath: String? = null
)

private data class FreehandStroke(
    val points: List<Offset>, // Normalized 0..1 relative to image fit rect
    val colorInt: Int,
    val widthNorm: Float = 0.006f
)

private data class AnnotationSnapshot(
    val overlays: List<OverlayAnnotation>,
    val strokes: List<FreehandStroke>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignAnnotateScreen(
    viewModel: DocumentEditorViewModel,
    pageId: Long,
    onDone: () -> Unit
) {
    val signatures by viewModel.signatures.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var overlays by remember { mutableStateOf<List<OverlayAnnotation>>(emptyList()) }
    var strokes by remember { mutableStateOf<List<FreehandStroke>>(emptyList()) }
    val undoStack = remember { mutableStateListOf<AnnotationSnapshot>() }
    val redoStack = remember { mutableStateListOf<AnnotationSnapshot>() }

    var selectedOverlayId by remember { mutableStateOf<Long?>(null) }
    var freehandMode by remember { mutableStateOf(false) }
    var activeInkColor by remember { mutableStateOf(Color(0xFF0F172A)) }

    var showSignaturePad by remember { mutableStateOf(false) }
    var showSavedSignatures by remember { mutableStateOf(false) }
    var showAddTextDialog by remember { mutableStateOf(false) }

    LaunchedEffect(pageId) {
        val page = viewModel.getPage(pageId)
        if (page != null) {
            baseBitmap = withContext(Dispatchers.IO) {
                ImageProcessor.decodeSampledBitmap(page.processedImagePath, 1800)
            }
        }
    }

    fun pushUndoSnapshot() {
        undoStack.add(AnnotationSnapshot(overlays, strokes))
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(AnnotationSnapshot(overlays, strokes))
        val prev = undoStack.removeAt(undoStack.lastIndex)
        overlays = prev.overlays
        strokes = prev.strokes
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(AnnotationSnapshot(overlays, strokes))
        val next = redoStack.removeAt(redoStack.lastIndex)
        overlays = next.overlays
        strokes = next.strokes
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Sign & Annotate") },
                actions = {
                    IconButton(onClick = ::undo, enabled = undoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = ::redo, enabled = redoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo")
                    }
                    Button(
                        onClick = {
                            val bmp = baseBitmap ?: return@Button
                            scope.launch {
                                val flattened = withContext(Dispatchers.IO) {
                                    flattenAnnotationsOntoBitmap(bmp, overlays, strokes)
                                }
                                viewModel.saveAnnotatedPage(pageId, flattened) {
                                    flattened.recycle()
                                    onDone()
                                }
                            }
                        },
                        enabled = !isBusy && baseBitmap != null,
                        shape = PillShape,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("sign_flatten_save")
                    ) {
                        Text(stringResource(R.string.flatten_save))
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp)
            ) {
                // Selected overlay controls (Resize, Rotate, Delete)
                selectedOverlayId?.let { selId ->
                    val sel = overlays.firstOrNull { it.id == selId }
                    if (sel != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Selected:", style = MaterialTheme.typography.labelMedium)
                            AssistChip(
                                onClick = {
                                    pushUndoSnapshot()
                                    overlays = overlays.map {
                                        if (it.id == selId) it.copy(scale = (it.scale + 0.2f).coerceAtMost(2.5f)) else it
                                    }
                                },
                                label = { Text("+Size") },
                                leadingIcon = { Icon(Icons.Outlined.ZoomIn, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            AssistChip(
                                onClick = {
                                    pushUndoSnapshot()
                                    overlays = overlays.map {
                                        if (it.id == selId) it.copy(scale = (it.scale - 0.2f).coerceAtLeast(0.4f)) else it
                                    }
                                },
                                label = { Text("-Size") }
                            )
                            AssistChip(
                                onClick = {
                                    pushUndoSnapshot()
                                    overlays = overlays.map {
                                        if (it.id == selId) it.copy(rotationDeg = (it.rotationDeg + 15f) % 360f) else it
                                    }
                                },
                                label = { Text("Rotate") },
                                leadingIcon = { Icon(Icons.Outlined.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            IconButton(
                                onClick = {
                                    pushUndoSnapshot()
                                    overlays = overlays.filterNot { it.id == selId }
                                    selectedOverlayId = null
                                }
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Remove annotation", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { showSavedSignatures = true },
                        label = { Text("Signature") },
                        leadingIcon = { Icon(Icons.Outlined.Draw, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("annotate_signature_btn")
                    )
                    FilterChip(
                        selected = false,
                        onClick = { showAddTextDialog = true },
                        label = { Text("Text") },
                        leadingIcon = { Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            pushUndoSnapshot()
                            val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
                            val item = OverlayAnnotation(
                                id = System.currentTimeMillis(),
                                kind = AnnotationKind.DATE,
                                text = dateStr,
                                colorInt = activeInkColor.toArgb()
                            )
                            overlays = overlays + item
                            selectedOverlayId = item.id
                        },
                        label = { Text("Date") },
                        leadingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            pushUndoSnapshot()
                            val item = OverlayAnnotation(
                                id = System.currentTimeMillis(),
                                kind = AnnotationKind.CHECKMARK,
                                text = "✓",
                                colorInt = AndroidColor.parseColor("#16A34A")
                            )
                            overlays = overlays + item
                            selectedOverlayId = item.id
                        },
                        label = { Text("Check") },
                        leadingIcon = { Icon(Icons.Outlined.CheckBox, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            pushUndoSnapshot()
                            val item = OverlayAnnotation(
                                id = System.currentTimeMillis(),
                                kind = AnnotationKind.HIGHLIGHT
                            )
                            overlays = overlays + item
                            selectedOverlayId = item.id
                        },
                        label = { Text("Highlight") },
                        leadingIcon = { Icon(Icons.Outlined.Highlight, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = freehandMode,
                        onClick = { freehandMode = !freehandMode },
                        label = { Text("Pen") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )

                    // Ink Color Dots
                    listOf(Color(0xFF0F172A), Color(0xFF1D4ED8), Color(0xFFDC2626)).forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (activeInkColor == c) 3.dp else 1.dp,
                                    color = if (activeInkColor == c) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = CircleShape
                                )
                                .clickable { activeInkColor = c }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val bmp = baseBitmap
            if (bmp == null) {
                CircularProgressIndicator()
            } else {
                val imgBmp = remember(bmp) { bmp.asImageBitmap() }
                val sigBitmapCache = remember(overlays) {
                    overlays.mapNotNull { it.signaturePath }.distinct().associateWith { path ->
                        BitmapFactory.decodeFile(path)?.asImageBitmap()
                    }
                }
                var canvasSize by remember { mutableStateOf(IntSize.Zero) }
                var activeStrokePts by remember { mutableStateOf<List<Offset>>(emptyList()) }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(freehandMode, bmp, canvasSize, overlays) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val fit = fitBox(bmp.width, bmp.height, canvasSize.width.toFloat(), canvasSize.height.toFloat())
                                    if (freehandMode) {
                                        val nx = ((offset.x - fit.left) / fit.w).coerceIn(0f, 1f)
                                        val ny = ((offset.y - fit.top) / fit.h).coerceIn(0f, 1f)
                                        activeStrokePts = listOf(Offset(nx, ny))
                                    } else {
                                        val hit = overlays.lastOrNull { ov ->
                                            val ox = fit.left + ov.normX * fit.w
                                            val oy = fit.top + ov.normY * fit.h
                                            kotlin.math.hypot(ox - offset.x, oy - offset.y) < 110f * ov.scale
                                        }
                                        selectedOverlayId = hit?.id
                                        if (hit != null) pushUndoSnapshot()
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val fit = fitBox(bmp.width, bmp.height, canvasSize.width.toFloat(), canvasSize.height.toFloat())
                                    if (freehandMode) {
                                        val nx = ((change.position.x - fit.left) / fit.w).coerceIn(0f, 1f)
                                        val ny = ((change.position.y - fit.top) / fit.h).coerceIn(0f, 1f)
                                        activeStrokePts = activeStrokePts + Offset(nx, ny)
                                    } else {
                                        val selId = selectedOverlayId ?: return@detectDragGestures
                                        overlays = overlays.map { ov ->
                                            if (ov.id == selId) {
                                                ov.copy(
                                                    normX = (ov.normX + dragAmount.x / fit.w).coerceIn(0.05f, 0.95f),
                                                    normY = (ov.normY + dragAmount.y / fit.h).coerceIn(0.05f, 0.95f)
                                                )
                                            } else ov
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (freehandMode && activeStrokePts.size >= 2) {
                                        pushUndoSnapshot()
                                        strokes = strokes + FreehandStroke(activeStrokePts, activeInkColor.toArgb())
                                    }
                                    activeStrokePts = emptyList()
                                }
                            )
                        }
                ) {
                    val fit = fitBox(bmp.width, bmp.height, size.width, size.height)
                    drawImage(
                        image = imgBmp,
                        dstOffset = IntOffset(fit.left.roundToInt(), fit.top.roundToInt()),
                        dstSize = IntSize(fit.w.roundToInt(), fit.h.roundToInt())
                    )

                    // Draw committed + active freehand strokes
                    val allStrokes = if (activeStrokePts.size >= 2) {
                        strokes + FreehandStroke(activeStrokePts, activeInkColor.toArgb())
                    } else strokes

                    allStrokes.forEach { st ->
                        if (st.points.size >= 2) {
                            val path = Path().apply {
                                val first = st.points.first()
                                moveTo(fit.left + first.x * fit.w, fit.top + first.y * fit.h)
                                for (i in 1 until st.points.size) {
                                    val p = st.points[i]
                                    lineTo(fit.left + p.x * fit.w, fit.top + p.y * fit.h)
                                }
                            }
                            drawPath(
                                path,
                                color = Color(st.colorInt),
                                style = Stroke(width = st.widthNorm * fit.w, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }

                    // Draw overlay annotations
                    overlays.forEach { ov ->
                        val cx = fit.left + ov.normX * fit.w
                        val cy = fit.top + ov.normY * fit.h
                        val baseSize = fit.w * 0.24f * ov.scale
                        rotate(ov.rotationDeg, pivot = Offset(cx, cy)) {
                            when (ov.kind) {
                                AnnotationKind.SIGNATURE -> {
                                    val sigImg = ov.signaturePath?.let { sigBitmapCache[it] }
                                    if (sigImg != null) {
                                        val sw = baseSize * 1.4f
                                        val sh = sw * (sigImg.height.toFloat() / sigImg.width.coerceAtLeast(1))
                                        drawImage(
                                            image = sigImg,
                                            dstOffset = IntOffset((cx - sw / 2f).roundToInt(), (cy - sh / 2f).roundToInt()),
                                            dstSize = IntSize(sw.roundToInt().coerceAtLeast(24), sh.roundToInt().coerceAtLeast(16))
                                        )
                                    }
                                }
                                AnnotationKind.HIGHLIGHT -> {
                                    drawRect(
                                        color = Color(0x66FACC15),
                                        topLeft = Offset(cx - baseSize * 0.8f, cy - baseSize * 0.18f),
                                        size = Size(baseSize * 1.6f, baseSize * 0.36f)
                                    )
                                }
                                else -> {
                                    // Draw crisp vector badge for Text/Date/Check preview
                                    val boxW = baseSize * 1.25f
                                    val boxH = baseSize * 0.36f
                                    drawRoundRect(
                                        color = Color(ov.colorInt).copy(alpha = 0.14f),
                                        topLeft = Offset(cx - boxW / 2f, cy - boxH / 2f),
                                        size = Size(boxW, boxH),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                                    )
                                    drawRoundRect(
                                        color = Color(ov.colorInt),
                                        topLeft = Offset(cx - boxW / 2f, cy - boxH / 2f),
                                        size = Size(boxW, boxH),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                                        style = Stroke(width = 2.5f)
                                    )
                                }
                            }
                            if (ov.id == selectedOverlayId) {
                                drawRect(
                                    color = Color(0xFFF59E0B),
                                    topLeft = Offset(cx - baseSize * 0.78f, cy - baseSize * 0.36f),
                                    size = Size(baseSize * 1.56f, baseSize * 0.72f),
                                    style = Stroke(width = 3f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Saved Signatures Sheet
    if (showSavedSignatures) {
        ModalBottomSheet(
            onDismissRequest = { showSavedSignatures = false },
            shape = BottomSheetShape
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Signatures", style = MaterialTheme.typography.titleLarge)
                    Button(
                        onClick = {
                            showSavedSignatures = false
                            showSignaturePad = true
                        },
                        shape = PillShape
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Signature")
                    }
                }

                if (signatures.isEmpty()) {
                    Text(
                        "Draw your signature once and reuse it across any document.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    signatures.forEach { sig ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pushUndoSnapshot()
                                    val item = OverlayAnnotation(
                                        id = System.currentTimeMillis(),
                                        kind = AnnotationKind.SIGNATURE,
                                        signaturePath = sig.pngPath
                                    )
                                    overlays = overlays + item
                                    selectedOverlayId = item.id
                                    showSavedSignatures = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val bmp = remember(sig.pngPath) { BitmapFactory.decodeFile(sig.pngPath) }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = sig.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(width = 110.dp, height = 46.dp)
                                            .background(Color.White, RoundedCornerShape(8.dp))
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(sig.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                IconButton(onClick = { viewModel.deleteSignature(sig) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete signature")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Smooth Bezier Signature Pad Dialog
    if (showSignaturePad) {
        SignaturePadDialog(
            onSaveSignature = { sigBitmap ->
                val sigName = "Signature ${signatures.size + 1}"
                viewModel.saveSignature(sigName, sigBitmap)
                showSignaturePad = false
                showSavedSignatures = true
            },
            onDismiss = { showSignaturePad = false }
        )
    }

    if (showAddTextDialog) {
        var textInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTextDialog = false },
            title = { Text("Add Text Annotation") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Enter text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (textInput.isNotBlank()) {
                        pushUndoSnapshot()
                        val item = OverlayAnnotation(
                            id = System.currentTimeMillis(),
                            kind = AnnotationKind.TEXT,
                            text = textInput.trim(),
                            colorInt = activeInkColor.toArgb()
                        )
                        overlays = overlays + item
                        selectedOverlayId = item.id
                    }
                    showAddTextDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTextDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SignaturePadDialog(
    onSaveSignature: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    var inkColor by remember { mutableStateOf(Color(0xFF0F172A)) }
    val completedStrokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var padSize by remember { mutableStateOf(IntSize(600, 320)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_signature)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ink:", style = MaterialTheme.typography.labelMedium)
                    listOf(
                        Color(0xFF0F172A) to "Black",
                        Color(0xFF1D4ED8) to "Blue",
                        Color(0xFFDC2626) to "Red"
                    ).forEach { (col, name) ->
                        Surface(
                            shape = CircleShape,
                            color = col,
                            border = if (inkColor == col) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .size(30.dp)
                                .clickable { inkColor = col }
                        ) {}
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        completedStrokes.clear()
                        currentStroke = emptyList()
                    }) { Text("Clear") }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .onSizeChanged { padSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> currentStroke = listOf(offset) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    if (currentStroke.size >= 2) completedStrokes.add(currentStroke)
                                    currentStroke = emptyList()
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val all = if (currentStroke.size >= 2) completedStrokes + listOf(currentStroke) else completedStrokes
                        all.forEach { pts ->
                            if (pts.size >= 2) {
                                val path = Path().apply {
                                    moveTo(pts.first().x, pts.first().y)
                                    for (i in 1 until pts.size) {
                                        val prev = pts[i - 1]
                                        val cur = pts[i]
                                        quadraticTo(prev.x, prev.y, (prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
                                    }
                                }
                                drawPath(
                                    path,
                                    color = inkColor,
                                    style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = padSize.width.coerceAtLeast(300)
                    val h = padSize.height.coerceAtLeast(180)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = inkColor.toArgb()
                        style = Paint.Style.STROKE
                        strokeWidth = 8f
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    completedStrokes.forEach { pts ->
                        if (pts.size >= 2) {
                            val p = AndroidPath().apply {
                                moveTo(pts.first().x, pts.first().y)
                                for (i in 1 until pts.size) {
                                    val prev = pts[i - 1]
                                    val cur = pts[i]
                                    quadTo(prev.x, prev.y, (prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
                                }
                            }
                            canvas.drawPath(p, paint)
                        }
                    }
                    onSaveSignature(bmp)
                },
                enabled = completedStrokes.isNotEmpty()
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Signature")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private data class FitBox(val left: Float, val top: Float, val w: Float, val h: Float)

private fun fitBox(imgW: Int, imgH: Int, viewW: Float, viewH: Float): FitBox {
    val s = min(viewW / imgW.coerceAtLeast(1), viewH / imgH.coerceAtLeast(1))
    val w = imgW * s
    val h = imgH * s
    return FitBox((viewW - w) / 2f, (viewH - h) / 2f, w, h)
}

private fun flattenAnnotationsOntoBitmap(
    source: Bitmap,
    overlays: List<OverlayAnnotation>,
    strokes: List<FreehandStroke>
): Bitmap {
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)
    val w = out.width.toFloat()
    val h = out.height.toFloat()

    // Draw freehand strokes
    strokes.forEach { st ->
        if (st.points.size >= 2) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = st.colorInt
                style = Paint.Style.STROKE
                strokeWidth = st.widthNorm * w
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val p = AndroidPath().apply {
                moveTo(st.points.first().x * w, st.points.first().y * h)
                for (i in 1 until st.points.size) {
                    lineTo(st.points[i].x * w, st.points[i].y * h)
                }
            }
            canvas.drawPath(p, paint)
        }
    }

    // Draw overlays
    overlays.forEach { ov ->
        val cx = ov.normX * w
        val cy = ov.normY * h
        val baseSize = w * 0.24f * ov.scale
        canvas.save()
        canvas.rotate(ov.rotationDeg, cx, cy)
        when (ov.kind) {
            AnnotationKind.SIGNATURE -> {
                val sigBmp = ov.signaturePath?.let { BitmapFactory.decodeFile(it) }
                if (sigBmp != null) {
                    val sw = baseSize * 1.4f
                    val sh = sw * (sigBmp.height.toFloat() / sigBmp.width.coerceAtLeast(1))
                    val rect = android.graphics.RectF(cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f)
                    canvas.drawBitmap(sigBmp, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    sigBmp.recycle()
                }
            }
            AnnotationKind.HIGHLIGHT -> {
                val p = Paint().apply { color = AndroidColor.argb(100, 250, 204, 21) }
                canvas.drawRect(cx - baseSize * 0.8f, cy - baseSize * 0.18f, cx + baseSize * 0.8f, cy + baseSize * 0.18f, p)
            }
            else -> {
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ov.colorInt
                    textSize = (baseSize * 0.26f).coerceAtLeast(28f)
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }
                canvas.drawText(ov.text, cx, cy + tp.textSize * 0.35f, tp)
            }
        }
        canvas.restore()
    }
    return out
}
