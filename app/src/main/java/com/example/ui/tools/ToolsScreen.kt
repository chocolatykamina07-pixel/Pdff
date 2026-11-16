package com.example.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.ExportQuality
import com.example.domain.model.OcrLanguage
import com.example.domain.model.ToolType
import com.example.pdf.PdfEngine
import com.example.ui.components.FormatUtils
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BottomSheetShape
import com.example.ui.theme.PillShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    viewModel: ToolsViewModel,
    onOpenQrScanner: () -> Unit,
    onOpenDocument: (Long) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val pickSingleImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.setSelectedUris(listOf(it)) } }

    val pickMultipleImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris -> if (uris.isNotEmpty()) viewModel.setSelectedUris(uris) }

    val pickSinglePdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.setSelectedUris(listOf(it)) } }

    val pickMultiplePdfs = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) viewModel.setSelectedUris(uris) }

    val saveResultSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri ->
        val file = state.resultFile
        if (destUri != null && file != null && file.exists()) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar("Saved ${file.name}")
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.tools_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(ToolType.entries) { tool ->
                ToolGridCard(
                    tool = tool,
                    icon = toolIcon(tool),
                    onClick = {
                        if (tool == ToolType.QR_SCANNER) {
                            onOpenQrScanner()
                        } else {
                            viewModel.openTool(tool)
                        }
                    }
                )
            }
        }
    }

    state.activeTool?.let { tool ->
        ModalBottomSheet(
            onDismissRequest = viewModel::closeTool,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = BottomSheetShape
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(toolIcon(tool), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(tool.title, style = MaterialTheme.typography.titleLarge)
                        Text(tool.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // File Picker Button
                OutlinedButton(
                    onClick = {
                        when (tool) {
                            ToolType.IMAGE_TO_PDF -> pickMultipleImages.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                            ToolType.ID_PHOTO_PACK, ToolType.EXTRACT_OCR -> pickSingleImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                            ToolType.MERGE_PDF -> pickMultiplePdfs.launch(arrayOf("application/pdf"))
                            ToolType.IMPORT_RENAME -> pickSinglePdf.launch(arrayOf("application/pdf", "image/*"))
                            else -> pickSinglePdf.launch(arrayOf("application/pdf"))
                        }
                    },
                    shape = PillShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("tool_pick_files_button")
                ) {
                    Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.selectedUris.isEmpty()) "Select File(s)"
                        else "Selected ${state.selectedUris.size} file(s) · Change"
                    )
                }

                if (state.selectedUris.isNotEmpty()) {
                    if (state.pdfPageCount > 0) {
                        Text(
                            "Detected ${state.pdfPageCount} PDF page(s) · ${FormatUtils.formatBytes(state.originalSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Tool-specific options
                    when (tool) {
                        ToolType.SPLIT_PDF -> {
                            OutlinedTextField(
                                value = state.splitRangeText,
                                onValueChange = viewModel::updateSplitRange,
                                label = { Text("Pages or range (e.g. 1-3, 5)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        ToolType.COMPRESS_PDF -> {
                            Text("Compression Level", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExportQuality.entries.forEach { q ->
                                    FilterChip(
                                        selected = state.compressQuality == q,
                                        onClick = { viewModel.updateCompressQuality(q) },
                                        label = { Text(q.label) }
                                    )
                                }
                            }
                        }
                        ToolType.REORDER_DELETE -> {
                            Text("Tap arrows to reorder or trash to remove pages:", style = MaterialTheme.typography.labelMedium)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.reorderPagesList.forEachIndexed { idx, pageNum ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Page $pageNum", modifier = Modifier.weight(1f))
                                            IconButton(onClick = { viewModel.moveReorderPage(idx, idx - 1) }, enabled = idx > 0) {
                                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Move up")
                                            }
                                            IconButton(onClick = { viewModel.moveReorderPage(idx, idx + 1) }, enabled = idx < state.reorderPagesList.lastIndex) {
                                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Move down")
                                            }
                                            IconButton(onClick = { viewModel.removeReorderPage(idx) }, enabled = state.reorderPagesList.size > 1) {
                                                Icon(Icons.Outlined.Delete, contentDescription = "Remove page", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        ToolType.PROTECT_UNLOCK -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = state.protectModeEncrypt,
                                    onClick = { viewModel.setProtectModeEncrypt(true) },
                                    label = { Text("Protect (Lock)") }
                                )
                                FilterChip(
                                    selected = !state.protectModeEncrypt,
                                    onClick = { viewModel.setProtectModeEncrypt(false) },
                                    label = { Text("Unlock (Decrypt)") }
                                )
                            }
                            OutlinedTextField(
                                value = state.pdfPassword,
                                onValueChange = viewModel::updatePassword,
                                label = { Text(if (state.protectModeEncrypt) "Set PDF Password" else "Enter Current PDF Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        ToolType.IMPORT_RENAME -> {
                            OutlinedTextField(
                                value = state.importDocName,
                                onValueChange = viewModel::updateImportDocName,
                                label = { Text("Document Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        ToolType.EXTRACT_OCR -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OcrLanguage.entries.forEach { lang ->
                                    FilterChip(
                                        selected = state.ocrLanguage == lang,
                                        onClick = { viewModel.setOcrLanguage(lang) },
                                        label = { Text(lang.label) }
                                    )
                                }
                            }
                        }
                        else -> Unit
                    }

                    if (state.isProcessing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Button(
                        onClick = {
                            viewModel.executeActiveTool { createdDocId ->
                                viewModel.closeTool()
                                onOpenDocument(createdDocId)
                            }
                        },
                        enabled = !state.isProcessing,
                        shape = PillShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("tool_run_button")
                    ) {
                        Text("Run ${tool.title}")
                    }
                }

                state.errorMessage?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                state.statusMessage?.let { msg ->
                    Text(msg, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.titleSmall)
                }

                // Result File Actions
                state.resultFile?.let { file ->
                    val mime = if (file.extension == "jpg") "image/jpeg" else "application/pdf"
                    if (state.originalSizeBytes > 0 && state.resultSizeBytes > 0) {
                        Text(
                            "Size: ${FormatUtils.formatBytes(state.originalSizeBytes)} → ${FormatUtils.formatBytes(state.resultSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { saveResultSafLauncher.launch(file.name) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save")
                        }
                        Button(
                            onClick = { FormatUtils.shareFile(context, file, mime) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                    state.createdDocId?.let { docId ->
                        OutlinedButton(
                            onClick = {
                                viewModel.closeTool()
                                onOpenDocument(docId)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open in Document Editor")
                        }
                    }
                }

                if (state.resultImages.isNotEmpty()) {
                    Button(
                        onClick = {
                            scope.launch {
                                state.resultImages.forEach { img ->
                                    PdfEngine.saveImageToMediaStore(context, img, img.name)
                                }
                                snackbarHostState.showSnackbar("Saved ${state.resultImages.size} images to Pictures/ScanNest")
                                viewModel.closeTool()
                            }
                        },
                        shape = PillShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save All ${state.resultImages.size} JPEGs to Gallery")
                    }
                }

                state.ocrResultText?.let { text ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("OCR", text))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy")
                        }
                        Button(
                            onClick = { FormatUtils.shareText(context, text) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ToolGridCard(
    tool: ToolType,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("tool_card_${tool.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (tool == ToolType.QR_SCANNER || tool == ToolType.ID_PHOTO_PACK) {
                    AmberAccent.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (tool == ToolType.QR_SCANNER || tool == ToolType.ID_PHOTO_PACK) {
                            AmberAccent
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = tool.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun toolIcon(tool: ToolType): ImageVector = when (tool) {
    ToolType.IMAGE_TO_PDF -> Icons.Outlined.PictureAsPdf
    ToolType.MERGE_PDF -> Icons.AutoMirrored.Outlined.MergeType
    ToolType.SPLIT_PDF -> Icons.AutoMirrored.Outlined.CallSplit
    ToolType.COMPRESS_PDF -> Icons.Outlined.Compress
    ToolType.REORDER_DELETE -> Icons.Outlined.Reorder
    ToolType.PROTECT_UNLOCK -> Icons.Outlined.Lock
    ToolType.PDF_TO_IMAGES -> Icons.Outlined.Image
    ToolType.IMPORT_RENAME -> Icons.AutoMirrored.Outlined.DriveFileMove
    ToolType.ID_PHOTO_PACK -> Icons.Outlined.Badge
    ToolType.EXTRACT_OCR -> Icons.Outlined.TextFields
    ToolType.QR_SCANNER -> Icons.Outlined.QrCodeScanner
}
