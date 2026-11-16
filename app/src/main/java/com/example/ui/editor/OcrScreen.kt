package com.example.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.ExportFormat
import com.example.domain.model.ExportRequest
import com.example.domain.model.OcrLanguage
import com.example.ui.components.FormatUtils
import com.example.ui.theme.PillShape
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    viewModel: DocumentEditorViewModel,
    documentId: Long,
    pageId: Long?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val ocrState by viewModel.ocrState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var wholeDocument by remember { mutableStateOf(pageId == null) }
    var editMode by remember { mutableStateOf(false) }
    var langMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(documentId, pageId) {
        viewModel.loadDocument(documentId)
        viewModel.runOcr(documentId, pageId, wholeDocument)
    }

    LaunchedEffect(exportState.exportedFile) {
        val file = exportState.exportedFile ?: return@LaunchedEffect
        if (exportState.exportedFormat == ExportFormat.PDF) {
            FormatUtils.shareFile(context, file, ExportFormat.PDF.mimeType, "Share Searchable PDF")
            viewModel.clearExportResult()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        "Extract Text",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Box {
                        Surface(
                            shape = PillShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clickable { langMenuOpen = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (ocrState.language == OcrLanguage.LATIN) "EN" else "EN+HI",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    Icons.Outlined.ArrowDropDown,
                                    contentDescription = "Select OCR language",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        DropdownMenu(expanded = langMenuOpen, onDismissRequest = { langMenuOpen = false }) {
                            OcrLanguage.entries.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang.label) },
                                    onClick = {
                                        langMenuOpen = false
                                        viewModel.setOcrLanguage(lang)
                                        viewModel.runOcr(documentId, pageId, wholeDocument)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.runOcr(documentId, pageId, wholeDocument) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Re-run OCR")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pageId != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !wholeDocument,
                        onClick = {
                            wholeDocument = false
                            viewModel.runOcr(documentId, pageId, false)
                        },
                        label = { Text("This Page") }
                    )
                    FilterChip(
                        selected = wholeDocument,
                        onClick = {
                            wholeDocument = true
                            viewModel.runOcr(documentId, pageId, true)
                        },
                        label = { Text("Whole Doc") }
                    )
                }
            }

            if (ocrState.isRunning || exportState.isExporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Document-styled Text Card with Teal Highlight Accent + Floating Dark Action Pill
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 2.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (editMode) {
                        OutlinedTextField(
                            value = ocrState.extractedText,
                            onValueChange = {
                                viewModel.updateOcrEditableText(it)
                                viewModel.saveOcrEditsToDocument(documentId, pageId)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 76.dp)
                                .testTag("ocr_editable_field")
                        )
                    } else {
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        text = ocrState.extractedText.ifBlank { "Scanning text on device…" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.testTag("ocr_selectable_text")
                                    )
                                }
                            }
                        }
                    }

                    // Dark floating contextual pill toolbar (Copy | Edit text | Export)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF18181B),
                        contentColor = Color.White,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FloatingOcrPillButton(
                                icon = Icons.Outlined.ContentCopy,
                                label = "Copy",
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("OCR Text", ocrState.extractedText))
                                    scope.launch { snackbarHostState.showSnackbar("Copied text to clipboard") }
                                }
                            )
                            FloatingOcrPillButton(
                                icon = Icons.Outlined.Edit,
                                label = if (editMode) "Done edit" else "Edit text",
                                onClick = { editMode = !editMode }
                            )
                            FloatingOcrPillButton(
                                icon = Icons.Outlined.Share,
                                label = "Export",
                                onClick = { FormatUtils.shareText(context, ocrState.extractedText) }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.startExport(
                        ExportRequest(
                            documentId = documentId,
                            format = ExportFormat.PDF,
                            searchablePdf = true
                        )
                    )
                },
                enabled = !exportState.isExporting,
                shape = PillShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("ocr_searchable_pdf_button")
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.searchable_pdf))
            }
        }
    }
}

@Composable
private fun FloatingOcrPillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
