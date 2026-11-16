package com.example.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BrandingWatermark
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Public
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.DocumentEntity
import com.example.domain.model.ExportQuality
import com.example.domain.model.OcrLanguage
import com.example.domain.model.ScanMode
import com.example.domain.model.ToolType
import com.example.pdf.PdfEngine
import com.example.ui.components.FormatUtils
import com.example.ui.components.ScanNestLogoMark
import com.example.ui.theme.BottomSheetShape
import com.example.ui.theme.PillShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class QuickToolTile(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val bgColor: Color,
    val tintColor: Color,
    val isNew: Boolean = false,
    val testTag: String? = null,
    val onClick: () -> Unit
)

private enum class DocActionTarget(val title: String, val subtitle: String) {
    SIGN("Sign Document", "Choose a document to add your e-signature"),
    DOODLE("Doodle & Annotate", "Choose a document to highlight, underline, or draw"),
    WATERMARK("Add Watermark", "Choose a document to stamp a custom watermark"),
    SHARE_PDF("Share as PDF", "Choose a document to export or share as PDF")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    viewModel: ToolsViewModel,
    onOpenQrScanner: () -> Unit,
    onOpenScannerMode: (ScanMode) -> Unit,
    onOpenDocument: (Long) -> Unit,
    onOpenSignPage: (Long) -> Unit,
    onExportDocument: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var pendingDocTarget by remember { mutableStateOf<DocActionTarget?>(null) }

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

    val directImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importFilesDirectly(uris, onOpenDocument)
        }
    }

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

    // Featured top row (4 circular pastel tiles)
    val topFeaturedTiles = listOf(
        QuickToolTile(
            id = "ocr",
            label = "Image to text",
            icon = Icons.Outlined.TextFields,
            bgColor = Color(0xFFE0F2FE),
            tintColor = Color(0xFF2563EB),
            testTag = "tool_card_${ToolType.EXTRACT_OCR.name.lowercase()}",
            onClick = { viewModel.openTool(ToolType.EXTRACT_OCR) }
        ),
        QuickToolTile(
            id = "id_card",
            label = "ID card",
            icon = Icons.Outlined.Badge,
            bgColor = Color(0xFFFEF3C7),
            tintColor = Color(0xFFD97706),
            onClick = { onOpenScannerMode(ScanMode.ID_CARD) }
        ),
        QuickToolTile(
            id = "passport",
            label = "Passport",
            icon = Icons.Outlined.Public,
            bgColor = Color(0xFFFCE7F3),
            tintColor = Color(0xFFDB2777),
            onClick = { onOpenScannerMode(ScanMode.PASSPORT) }
        ),
        QuickToolTile(
            id = "share_jpg",
            label = "Share as JPG",
            icon = Icons.Outlined.Image,
            bgColor = Color(0xFFFEF9C3),
            tintColor = Color(0xFFCA8A04),
            testTag = "tool_card_${ToolType.PDF_TO_IMAGES.name.lowercase()}",
            onClick = { viewModel.openTool(ToolType.PDF_TO_IMAGES) }
        )
    )

    // Hero Quick Actions (3 centered large circular icons inside elevated card)
    val heroQuickActions = listOf(
        QuickToolTile(
            id = "share_pdf",
            label = "Share as PDF",
            icon = Icons.Outlined.PictureAsPdf,
            bgColor = Color(0xFFEDE9FE),
            tintColor = Color(0xFF7C3AED),
            onClick = { pendingDocTarget = DocActionTarget.SHARE_PDF }
        ),
        QuickToolTile(
            id = "scan_docs",
            label = "Documents",
            icon = Icons.Outlined.DocumentScanner,
            bgColor = Color(0xFFCCFBF1),
            tintColor = Color(0xFF0F766E),
            onClick = { onOpenScannerMode(ScanMode.DOCUMENT) }
        ),
        QuickToolTile(
            id = "import_files",
            label = "Import files",
            icon = Icons.Outlined.FileOpen,
            bgColor = Color(0xFFFEE2E2),
            tintColor = Color(0xFFDC2626),
            onClick = { directImportLauncher.launch(arrayOf("application/pdf", "image/*")) }
        )
    )

    // 4-Column Utilities Grid
    val utilityTiles = listOf(
        QuickToolTile(
            id = "merge",
            label = "Merge files",
            icon = Icons.AutoMirrored.Outlined.MergeType,
            bgColor = Color(0xFFFFEDD5),
            tintColor = Color(0xFFEA580C),
            testTag = "tool_card_${ToolType.MERGE_PDF.name.lowercase()}",
            onClick = { viewModel.openTool(ToolType.MERGE_PDF) }
        ),
        QuickToolTile(
            id = "sign",
            label = "Sign",
            icon = Icons.Outlined.Draw,
            bgColor = Color(0xFFDCFCE7),
            tintColor = Color(0xFF16A34A),
            onClick = { pendingDocTarget = DocActionTarget.SIGN }
        ),
        QuickToolTile(
            id = "doodle",
            label = "Doodle",
            icon = Icons.Outlined.Brush,
            bgColor = Color(0xFFE0F2FE),
            tintColor = Color(0xFF0284C7),
            onClick = { pendingDocTarget = DocActionTarget.DOODLE }
        ),
        QuickToolTile(
            id = "watermark",
            label = "Watermark",
            icon = Icons.Outlined.BrandingWatermark,
            bgColor = Color(0xFFF3E8FF),
            tintColor = Color(0xFF9333EA),
            onClick = { pendingDocTarget = DocActionTarget.WATERMARK }
        ),
        QuickToolTile(
            id = "pdf_to_img",
            label = "PDF to images",
            icon = Icons.Outlined.Image,
            bgColor = Color(0xFFEDE9FE),
            tintColor = Color(0xFF7C3AED),
            onClick = { viewModel.openTool(ToolType.PDF_TO_IMAGES) }
        ),
        QuickToolTile(
            id = "word_to_pdf",
            label = "Word to PDF",
            icon = Icons.Outlined.Description,
            bgColor = Color(0xFFDBEAFE),
            tintColor = Color(0xFF2563EB),
            isNew = true,
            testTag = "tool_card_${ToolType.WORD_TO_PDF.name.lowercase()}",
            onClick = { viewModel.openTool(ToolType.WORD_TO_PDF) }
        ),
        QuickToolTile(
            id = "compress_pdf",
            label = "Compress PDF",
            icon = Icons.Outlined.Compress,
            bgColor = Color(0xFFFEF3C7),
            tintColor = Color(0xFFD97706),
            isNew = true,
            testTag = "tool_card_${ToolType.COMPRESS_PDF.name.lowercase()}",
            onClick = { viewModel.openTool(ToolType.COMPRESS_PDF) }
        ),
        QuickToolTile(
            id = "pdf_reader",
            label = "PDF Reader",
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            bgColor = Color(0xFFE0E7FF),
            tintColor = Color(0xFF4F46E5),
            testTag = "tool_card_${ToolType.PDF_READER.name.lowercase()}",
            onClick = { viewModel.openTool(ToolType.PDF_READER) }
        ),
        QuickToolTile(
            id = "split_pdf",
            label = "Split PDF",
            icon = Icons.AutoMirrored.Outlined.CallSplit,
            bgColor = Color(0xFFCCFBF1),
            tintColor = Color(0xFF0F766E),
            testTag = "tool_card_${ToolType.SPLIT_PDF.name.lowercase()}",
            onClick = { viewModel.openTool(ToolType.SPLIT_PDF) }
        ),
        QuickToolTile(
            id = "reorder",
            label = "Reorder pages",
            icon = Icons.Outlined.Reorder,
            bgColor = Color(0xFFCFFAFE),
            tintColor = Color(0xFF0891B2),
            testTag = "tool_card_${ToolType.REORDER_DELETE.name.lowercase()}",
            onClick = { viewModel.openTool(ToolType.REORDER_DELETE) }
        ),
        QuickToolTile(
            id = "protect",
            label = "Protect PDF",
            icon = Icons.Outlined.Lock,
            bgColor = Color(0xFFFFE4E6),
            tintColor = Color(0xFFE11D48),
            testTag = "tool_card_${ToolType.PROTECT_UNLOCK.name.lowercase()}",
            onClick = { viewModel.openTool(ToolType.PROTECT_UNLOCK) }
        ),
        QuickToolTile(
            id = "qr_scan",
            label = "QR Scanner",
            icon = Icons.Outlined.QrCodeScanner,
            bgColor = Color(0xFFF1F5F9),
            tintColor = Color(0xFF334155),
            testTag = "tool_card_${ToolType.QR_SCANNER.name.lowercase()}",
            onClick = onOpenQrScanner
        ),
        QuickToolTile(
            id = "img_to_pdf",
            label = "Image to PDF",
            icon = Icons.Outlined.PictureAsPdf,
            bgColor = Color(0xFFCCFBF1),
            tintColor = Color(0xFF0F766E),
            testTag = "tool_card_${ToolType.IMAGE_TO_PDF.name.lowercase()}",
            onClick = { viewModel.openTool(ToolType.IMAGE_TO_PDF) }
        ),
        QuickToolTile(
            id = "id_pack",
            label = "ID Photo Pack",
            icon = Icons.Outlined.Badge,
            bgColor = Color(0xFFFEF3C7),
            tintColor = Color(0xFFB45309),
            testTag = "tool_card_${ToolType.ID_PHOTO_PACK.name.lowercase()}",
            onClick = { viewModel.openTool(ToolType.ID_PHOTO_PACK) }
        ),
        QuickToolTile(
            id = "import_rename",
            label = "Import & Rename",
            icon = Icons.AutoMirrored.Outlined.DriveFileMove,
            bgColor = Color(0xFFF1F5F9),
            tintColor = Color(0xFF0F766E),
            testTag = "tool_card_${ToolType.IMPORT_RENAME.name.lowercase()}",
            onClick = { viewModel.openTool(ToolType.IMPORT_RENAME) }
        )
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 12.dp)) {
                        ScanNestLogoMark(size = 28.dp)
                    }
                },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (state.isProcessing && state.activeTool == null) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            // Top 4 Quick Scan & Export Icons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    topFeaturedTiles.forEach { tile ->
                        CircularToolTile(
                            tile = tile,
                            circleSize = 52.dp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Elevated Hero Card ("Quick Actions")
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Quick Actions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = PillShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "100% Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            heroQuickActions.forEach { tile ->
                                CircularToolTile(
                                    tile = tile,
                                    circleSize = 60.dp,
                                    iconSize = 28.dp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Section Title
            item {
                Text(
                    text = "PDF & Document Utilities",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // 4-Column Circular Pastel Icon Grid
            val rows = utilityTiles.chunked(4)
            items(rows) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowItems.forEach { tile ->
                        CircularToolTile(
                            tile = tile,
                            circleSize = 50.dp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(4 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    // Document Picker Sheet for Sign / Doodle / Watermark / Share as PDF
    pendingDocTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { pendingDocTarget = null },
            shape = BottomSheetShape
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(target.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    target.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (documents.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("No scanned documents yet", style = MaterialTheme.typography.titleSmall)
                            Button(
                                onClick = {
                                    pendingDocTarget = null
                                    onOpenScannerMode(ScanMode.DOCUMENT)
                                },
                                shape = PillShape
                            ) {
                                Text("Scan a Document")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(documents, key = { it.id }) { doc ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val action = target
                                        pendingDocTarget = null
                                        when (action) {
                                            DocActionTarget.SIGN, DocActionTarget.DOODLE -> {
                                                scope.launch {
                                                    val firstPageId = viewModel.getFirstPageId(doc.id)
                                                    if (firstPageId != null) {
                                                        onOpenSignPage(firstPageId)
                                                    } else {
                                                        onOpenDocument(doc.id)
                                                    }
                                                }
                                            }
                                            DocActionTarget.WATERMARK -> onOpenDocument(doc.id)
                                            DocActionTarget.SHARE_PDF -> onExportDocument(doc)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFDC2626),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                "PDF",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            doc.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${doc.pageCount} page(s) · ${FormatUtils.formatTimestamp(doc.updatedAt)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Outlined.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Active PDF / File Tool Bottom Sheet
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
                        Text(
                            tool.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                            ToolType.WORD_TO_PDF -> pickSinglePdf.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "text/plain"
                                )
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
private fun CircularToolTile(
    tile: QuickToolTile,
    circleSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    val taggedModifier = if (tile.testTag != null) {
        modifier.testTag(tile.testTag)
    } else {
        modifier
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = taggedModifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = tile.onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(circleSize)
                    .clip(CircleShape)
                    .background(tile.bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = tile.label,
                    tint = tile.tintColor,
                    modifier = Modifier.size(iconSize)
                )
            }
            if (tile.isNew) {
                Surface(
                    shape = PillShape,
                    color = Color(0xFFDC2626),
                    modifier = Modifier.offset(x = 6.dp, y = (-4).dp)
                ) {
                    Text(
                        text = "NEW",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Text(
            text = tile.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}

private fun toolIcon(tool: ToolType): ImageVector = when (tool) {
    ToolType.IMAGE_TO_PDF -> Icons.Outlined.PictureAsPdf
    ToolType.WORD_TO_PDF -> Icons.Outlined.Description
    ToolType.PDF_READER -> Icons.AutoMirrored.Outlined.MenuBook
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
