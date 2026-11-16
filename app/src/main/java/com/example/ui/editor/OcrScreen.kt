package com.example.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Extract Text (OCR)") },
                actions = {
                    IconButton(onClick = { editMode = !editMode }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Toggle edit text")
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
            // Scope & Language Selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pageId != null) {
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OcrLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = ocrState.language == lang,
                        onClick = {
                            viewModel.setOcrLanguage(lang)
                            viewModel.runOcr(documentId, pageId, wholeDocument)
                        },
                        label = { Text(lang.label) }
                    )
                }
            }

            if (ocrState.isRunning || exportState.isExporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Text Result Card (Selectable or Editable)
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (editMode) {
                    OutlinedTextField(
                        value = ocrState.extractedText,
                        onValueChange = {
                            viewModel.updateOcrEditableText(it)
                            viewModel.saveOcrEditsToDocument(documentId, pageId)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .testTag("ocr_editable_field")
                    )
                } else {
                    SelectionContainer {
                        Text(
                            text = ocrState.extractedText.ifBlank { "Scanning text on device…" },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                                .testTag("ocr_selectable_text")
                        )
                    }
                }
            }

            // Action Buttons: Copy, Share TXT, Searchable PDF
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("OCR Text", ocrState.extractedText))
                        scope.launch { snackbarHostState.showSnackbar("Copied text to clipboard") }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }

                OutlinedButton(
                    onClick = { FormatUtils.shareText(context, ocrState.extractedText) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share TXT")
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
