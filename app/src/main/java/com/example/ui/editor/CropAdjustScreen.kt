package com.example.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.camera.EdgeDetector
import com.example.domain.model.CropQuad
import com.example.domain.model.NormalizedPoint
import com.example.processing.ImageProcessor
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.DeepTeal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropAdjustScreen(
    viewModel: DocumentEditorViewModel,
    documentId: Long,
    pageId: Long,
    onRetake: () -> Unit,
    onOpenFilters: () -> Unit,
    onDone: () -> Unit
) {
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    var rawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var quad by remember { mutableStateOf(CropQuad()) }
    var rotation by remember { mutableIntStateOf(0) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var activeHandle by remember { mutableIntStateOf(-1) }

    LaunchedEffect(pageId) {
        val page = viewModel.getPage(pageId)
        if (page != null) {
            rotation = page.rotation
            flipHorizontal = page.flipHorizontal
            quad = viewModel.decodeCropQuad(page.cropPointsJson)
            rawBitmap = withContext(Dispatchers.IO) {
                ImageProcessor.decodeSampledBitmap(page.originalImagePath, 1600)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Crop & Adjust") },
                actions = {
                    IconButton(onClick = { rotation = (rotation + 270) % 360 }) {
                        Icon(Icons.AutoMirrored.Outlined.RotateLeft, contentDescription = "Rotate left")
                    }
                    IconButton(onClick = { rotation = (rotation + 90) % 360 }) {
                        Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = "Rotate right")
                    }
                    IconButton(onClick = { flipHorizontal = !flipHorizontal }) {
                        Icon(Icons.Outlined.Flip, contentDescription = "Flip horizontal")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(modifier = Modifier.navigationBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onRetake) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.retake))
                    }
                    TextButton(onClick = { rotation = (rotation + 90) % 360 }) {
                        Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.rotate))
                    }
                    TextButton(
                        onClick = {
                            viewModel.saveCropAndTransform(pageId, quad, rotation, flipHorizontal) {
                                onOpenFilters()
                            }
                        }
                    ) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.filters))
                    }
                    Button(
                        onClick = {
                            viewModel.saveCropAndTransform(pageId, quad, rotation, flipHorizontal) {
                                onDone()
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier.testTag("crop_save_next_button")
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.next))
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F172A))
        ) {
            // Auto-detect & Full image quick buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                OutlinedButton(
                    onClick = {
                        rawBitmap?.let { bmp ->
                            quad = EdgeDetector.detectEdges(bmp)
                        }
                    },
                    modifier = Modifier.testTag("crop_auto_detect")
                ) {
                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.auto_detect), color = Color.White)
                }
                OutlinedButton(
                    onClick = { quad = CropQuad.FULL },
                    modifier = Modifier.testTag("crop_full_image")
                ) {
                    Icon(Icons.Outlined.CropFree, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.full_image), color = Color.White)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val bmp = rawBitmap
                if (bmp == null) {
                    CircularProgressIndicator(color = AmberAccent)
                } else {
                    val imgBmp = remember(bmp) { bmp.asImageBitmap() }
                    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(bmp, canvasSize) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val rect = computeFitRect(bmp.width, bmp.height, canvasSize.width.toFloat(), canvasSize.height.toFloat())
                                        val corners = quad.asList().map { pt ->
                                            Offset(rect.left + pt.x * rect.width, rect.top + pt.y * rect.height)
                                        }
                                        val nearest = corners.withIndex().minByOrNull { hypot(it.value.x - offset.x, it.value.y - offset.y) }
                                        activeHandle = if (nearest != null && hypot(nearest.value.x - offset.x, nearest.value.y - offset.y) < 90f) {
                                            nearest.index
                                        } else -1
                                    },
                                    onDrag = { change, _ ->
                                        if (activeHandle in 0..3) {
                                            change.consume()
                                            val rect = computeFitRect(bmp.width, bmp.height, canvasSize.width.toFloat(), canvasSize.height.toFloat())
                                            val nx = ((change.position.x - rect.left) / rect.width).coerceIn(0f, 1f)
                                            val ny = ((change.position.y - rect.top) / rect.height).coerceIn(0f, 1f)
                                            val pt = NormalizedPoint(nx, ny)
                                            quad = when (activeHandle) {
                                                0 -> quad.copy(topLeft = pt)
                                                1 -> quad.copy(topRight = pt)
                                                2 -> quad.copy(bottomRight = pt)
                                                else -> quad.copy(bottomLeft = pt)
                                            }
                                        }
                                    },
                                    onDragEnd = { activeHandle = -1 },
                                    onDragCancel = { activeHandle = -1 }
                                )
                            }
                    ) {
                        val rect = computeFitRect(bmp.width, bmp.height, size.width, size.height)
                        drawImage(
                            image = imgBmp,
                            dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                            dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt())
                        )

                        val tl = Offset(rect.left + quad.topLeft.x * rect.width, rect.top + quad.topLeft.y * rect.height)
                        val tr = Offset(rect.left + quad.topRight.x * rect.width, rect.top + quad.topRight.y * rect.height)
                        val br = Offset(rect.left + quad.bottomRight.x * rect.width, rect.top + quad.bottomRight.y * rect.height)
                        val bl = Offset(rect.left + quad.bottomLeft.x * rect.width, rect.top + quad.bottomLeft.y * rect.height)

                        val poly = Path().apply {
                            moveTo(tl.x, tl.y); lineTo(tr.x, tr.y); lineTo(br.x, br.y); lineTo(bl.x, bl.y); close()
                        }
                        drawPath(poly, color = DeepTeal.copy(alpha = 0.22f))
                        drawPath(poly, color = AmberAccent, style = Stroke(width = 4f))

                        listOf(tl, tr, br, bl).forEachIndexed { idx, pt ->
                            val isActive = idx == activeHandle
                            drawCircle(color = AmberAccent, radius = if (isActive) 22f else 16f, center = pt)
                            drawCircle(color = Color.White, radius = if (isActive) 13f else 9f, center = pt)
                        }

                        // Magnifier Loupe above active handle
                        if (activeHandle in 0..3) {
                            val pt = listOf(tl, tr, br, bl)[activeHandle]
                            val normPt = quad.asList()[activeHandle]
                            val loupeRadius = 72f
                            val loupeCenter = Offset(
                                x = pt.x.coerceIn(loupeRadius + 12f, size.width - loupeRadius - 12f),
                                y = (pt.y - 125f).coerceAtLeast(loupeRadius + 12f)
                            )
                            val loupeCircle = Path().apply {
                                addOval(
                                    androidx.compose.ui.geometry.Rect(
                                        loupeCenter.x - loupeRadius,
                                        loupeCenter.y - loupeRadius,
                                        loupeCenter.x + loupeRadius,
                                        loupeCenter.y + loupeRadius
                                    )
                                )
                            }
                            clipPath(loupeCircle) {
                                drawRect(Color.Black)
                                val zoom = 2.4f
                                val zoomedW = rect.width * zoom
                                val zoomedH = rect.height * zoom
                                val drawLeft = loupeCenter.x - normPt.x * zoomedW
                                val drawTop = loupeCenter.y - normPt.y * zoomedH
                                drawImage(
                                    image = imgBmp,
                                    dstOffset = IntOffset(drawLeft.roundToInt(), drawTop.roundToInt()),
                                    dstSize = IntSize(zoomedW.roundToInt(), zoomedH.roundToInt())
                                )
                                drawLine(AmberAccent, Offset(loupeCenter.x - 16f, loupeCenter.y), Offset(loupeCenter.x + 16f, loupeCenter.y), 2.5f)
                                drawLine(AmberAccent, Offset(loupeCenter.x, loupeCenter.y - 16f), Offset(loupeCenter.x, loupeCenter.y + 16f), 2.5f)
                            }
                            drawCircle(AmberAccent, radius = loupeRadius, center = loupeCenter, style = Stroke(width = 4f))
                        }
                    }
                }
            }
        }
    }
}

private data class FitRect(val left: Float, val top: Float, val width: Float, val height: Float)

private fun computeFitRect(imgW: Int, imgH: Int, viewW: Float, viewH: Float): FitRect {
    if (imgW <= 0 || imgH <= 0 || viewW <= 0f || viewH <= 0f) return FitRect(0f, 0f, viewW, viewH)
    val scale = min(viewW / imgW, viewH / imgH)
    val w = imgW * scale
    val h = imgH * scale
    return FitRect((viewW - w) / 2f, (viewH - h) / 2f, w, h)
}
