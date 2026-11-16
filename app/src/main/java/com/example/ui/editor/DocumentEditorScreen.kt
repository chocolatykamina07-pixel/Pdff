package com.example.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.BrandingWatermark
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.data.local.PageEntity
import com.example.ui.components.ExportBottomSheet
import com.example.ui.components.FormatUtils
import com.example.ui.components.SkeletonShimmerBox
import com.example.ui.components.TextInputDialog
import com.example.ui.theme.BottomSheetShape
import com.example.ui.theme.PillShape
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditorScreen(
    viewModel: DocumentEditorViewModel,
    documentId: Long,
    onBack: () -> Unit,
    onAddPageCamera: () -> Unit,
    onOpenCrop: (pageId: Long) -> Unit,
    onOpenFilter: (pageId: Long) -> Unit,
    onOpenSign: (pageId: Long) -> Unit,
    onOpenOcr: (pageId: Long?) -> Unit
) {
    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }

    val document by viewModel.activeDocument.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedPageIndex by remember { mutableStateOf(0) }
    var focusedPage by remember { mutableStateOf<PageEntity?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    val galleryAddLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addGalleryPages(documentId, uris)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_button")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = document?.name ?: "Document",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${pages.size} pages · ${FormatUtils.formatBytes(document?.sizeBytes ?: 0L)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = "Rename")
                    }
                    IconButton(onClick = onAddPageCamera, modifier = Modifier.testTag("editor_add_camera")) {
                        Icon(Icons.Outlined.AddAPhoto, contentDescription = "Scan additional page")
                    }
                    IconButton(
                        onClick = {
                            galleryAddLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Add pages from gallery")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(modifier = Modifier.navigationBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { pages.getOrNull(selectedPageIndex)?.let { onOpenSign(it.id) } },
                            label = { Text(stringResource(R.string.sign_annotate)) },
                            leadingIcon = { Icon(Icons.Outlined.Draw, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            enabled = pages.isNotEmpty()
                        )
                        AssistChip(
                            onClick = { onOpenOcr(pages.getOrNull(selectedPageIndex)?.id) },
                            label = { Text(stringResource(R.string.ocr_extract)) },
                            leadingIcon = { Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            enabled = pages.isNotEmpty()
                        )
                        AssistChip(
                            onClick = { showWatermarkDialog = true },
                            label = { Text(stringResource(R.string.watermark)) },
                            leadingIcon = { Icon(Icons.Outlined.BrandingWatermark, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            enabled = pages.isNotEmpty()
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { showExportSheet = true },
                        shape = PillShape,
                        enabled = pages.isNotEmpty(),
                        modifier = Modifier.testTag("editor_export_button")
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_export))
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(pages, key = { _, p -> p.id }) { index, page ->
                    val isSelected = index == selectedPageIndex
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPageIndex = index
                                focusedPage = page
                            }
                            .testTag("editor_page_card_${index + 1}")
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.74f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                SubcomposeAsyncImage(
                                    model = File(page.processedImagePath),
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    loading = { SkeletonShimmerBox(Modifier.fillMaxSize()) },
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(bottomEnd = 10.dp),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // Reorder & Quick Rotate/Delete row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.movePage(index, index - 1) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Move earlier", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.rotatePage(page.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = "Rotate page", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.deletePage(page.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete page", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.movePage(index, index + 1) },
                                    enabled = index < pages.lastIndex,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Move later", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Page-level Detail & Tools Sheet
    focusedPage?.let { page ->
        ModalBottomSheet(
            onDismissRequest = { focusedPage = null },
            shape = BottomSheetShape
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Page ${page.pageIndex + 1} Tools", style = MaterialTheme.typography.titleLarge)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    SubcomposeAsyncImage(
                        model = File(page.processedImagePath),
                        contentDescription = "Page preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PageToolButton(Icons.Outlined.Crop, "Crop") {
                        focusedPage = null
                        onOpenCrop(page.id)
                    }
                    PageToolButton(Icons.Outlined.Tune, "Filter") {
                        focusedPage = null
                        onOpenFilter(page.id)
                    }
                    PageToolButton(Icons.AutoMirrored.Outlined.RotateRight, "Rotate") {
                        viewModel.rotatePage(page.id)
                        focusedPage = null
                    }
                    PageToolButton(Icons.Outlined.Draw, "Annotate") {
                        focusedPage = null
                        onOpenSign(page.id)
                    }
                    PageToolButton(Icons.Outlined.TextFields, "OCR") {
                        focusedPage = null
                        onOpenOcr(page.id)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showRenameDialog) {
        TextInputDialog(
            title = stringResource(R.string.action_rename),
            label = "Document name",
            initialValue = document?.name.orEmpty(),
            confirmText = "Rename",
            onConfirm = { viewModel.renameDocument(documentId, it) },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showWatermarkDialog) {
        TextInputDialog(
            title = "Add Diagonal Watermark",
            label = "Watermark text (e.g. CONFIDENTIAL)",
            initialValue = "CONFIDENTIAL",
            confirmText = "Apply",
            onConfirm = { viewModel.applyWatermark(documentId, it) },
            onDismiss = { showWatermarkDialog = false }
        )
    }

    if (showExportSheet) {
        ExportBottomSheet(
            documentId = documentId,
            documentName = document?.name ?: "ScanNest_Doc",
            defaultPageSize = prefs.defaultPageSize,
            defaultQuality = prefs.defaultExportQuality,
            exportState = exportState,
            estimateSizeBytes = viewModel::estimateExportSize,
            onStartExport = { req -> viewModel.startExport(req, selectedPageIndex) },
            onCancelExport = viewModel::cancelExport,
            onClearResult = viewModel::clearExportResult,
            onDismiss = { showExportSheet = false },
            onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
        )
    }
}

@Composable
private fun PageToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
