package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.model.ExportFormat
import com.example.domain.model.ExportQuality
import com.example.domain.model.ExportRequest
import com.example.domain.model.MarginOption
import com.example.domain.model.OrientationOption
import com.example.domain.model.PageSizeOption
import com.example.pdf.PdfEngine
import com.example.ui.editor.ExportProgressState
import com.example.ui.theme.BottomSheetShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    documentId: Long,
    documentName: String,
    defaultPageSize: PageSizeOption,
    defaultQuality: ExportQuality,
    exportState: ExportProgressState,
    estimateSizeBytes: (ExportRequest) -> Long,
    onStartExport: (ExportRequest) -> Unit,
    onCancelExport: () -> Unit,
    onClearResult: () -> Unit,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var format by remember { mutableStateOf(ExportFormat.PDF) }
    var quality by remember { mutableStateOf(defaultQuality) }
    var pageSize by remember { mutableStateOf(defaultPageSize) }
    var margin by remember { mutableStateOf(MarginOption.SMALL) }
    var orientation by remember { mutableStateOf(OrientationOption.AUTO) }
    var searchablePdf by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var pendingSaveAction by remember { mutableStateOf<String?>(null) }

    val request = remember(documentId, format, quality, pageSize, margin, orientation, password, searchablePdf) {
        ExportRequest(
            documentId = documentId,
            format = format,
            quality = quality,
            pageSize = pageSize,
            margin = margin,
            orientation = orientation,
            password = if (format == ExportFormat.PDF) password.trim() else "",
            searchablePdf = format == ExportFormat.PDF && searchablePdf
        )
    }

    val estimatedSize = remember(request) { estimateSizeBytes(request) }

    // SAF CreateDocument launcher for "Save to Device"
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri ->
        val file = exportState.exportedFile
        if (destUri != null && file != null && file.exists()) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                withContext(Dispatchers.Main) {
                    onMessage("Saved ${file.name} to device")
                    onClearResult()
                    onDismiss()
                }
            }
        }
    }

    // Automatically trigger Share or Save once export completes
    LaunchedEffect(exportState.exportedFile) {
        val file = exportState.exportedFile ?: return@LaunchedEffect
        val fmt = exportState.exportedFormat ?: return@LaunchedEffect
        when (pendingSaveAction) {
            "share" -> {
                pendingSaveAction = null
                FormatUtils.shareFile(context, file, fmt.mimeType, "Share $documentName")
                onClearResult()
                onDismiss()
            }
            "save" -> {
                pendingSaveAction = null
                if (fmt == ExportFormat.JPEG) {
                    scope.launch {
                        val savedUri = PdfEngine.saveImageToMediaStore(context, file, file.name)
                        if (savedUri != null) {
                            onMessage("Saved JPEG to Pictures/ScanNest")
                            onClearResult()
                            onDismiss()
                        } else {
                            createDocLauncher.launch(file.name)
                        }
                    }
                } else {
                    createDocLauncher.launch(file.name)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            onCancelExport()
            onDismiss()
        },
        sheetState = sheetState,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Export & Share",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = documentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "~${FormatUtils.formatBytes(estimatedSize)}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Format chips
            Text("Format", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExportFormat.entries.forEach { fmt ->
                    FilterChip(
                        selected = format == fmt,
                        onClick = { format = fmt },
                        label = { Text(fmt.label) },
                        modifier = Modifier.testTag("export_format_${fmt.name.lowercase()}")
                    )
                }
            }

            // Quality / Size chips
            if (format == ExportFormat.PDF || format == ExportFormat.JPEG) {
                Text("Quality & File Size", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExportQuality.entries.forEach { q ->
                        FilterChip(
                            selected = quality == q,
                            onClick = { quality = q },
                            label = { Text(q.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (format == ExportFormat.PDF) {
                Text("Page Size", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PageSizeOption.entries.forEach { ps ->
                        FilterChip(
                            selected = pageSize == ps,
                            onClick = { pageSize = ps },
                            label = { Text(ps.label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Margins", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MarginOption.entries.forEach { m ->
                                FilterChip(
                                    selected = margin == m,
                                    onClick = { margin = m },
                                    label = { Text(m.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }

                Column {
                    Text("Orientation", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrientationOption.entries.forEach { o ->
                            FilterChip(
                                selected = orientation == o,
                                onClick = { orientation = o },
                                label = { Text(o.label) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Searchable PDF (OCR layer)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Embeds invisible selectable text",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = searchablePdf,
                        onCheckedChange = { searchablePdf = it }
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Optional PDF Password") },
                    placeholder = { Text("Leave blank for no password") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = "Toggle password visibility"
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (exportState.isExporting) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val progress = if (exportState.totalPages > 0) {
                        exportState.currentPage.toFloat() / exportState.totalPages
                    } else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Exporting page ${exportState.currentPage} of ${exportState.totalPages}…",
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = onCancelExport) {
                            Icon(Icons.Outlined.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                }
            }

            exportState.error?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        pendingSaveAction = "save"
                        onStartExport(request)
                    },
                    enabled = !exportState.isExporting,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("export_save_device_button")
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save")
                }

                Button(
                    onClick = {
                        pendingSaveAction = "share"
                        onStartExport(request)
                    },
                    enabled = !exportState.isExporting,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("export_share_button")
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
            }
        }
    }
}
