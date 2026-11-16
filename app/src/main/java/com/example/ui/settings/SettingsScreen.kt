package com.example.ui.settings

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.AppLanguage
import com.example.domain.model.ExportQuality
import com.example.domain.model.FilterType
import com.example.domain.model.PageSizeOption
import com.example.domain.model.ScanMode
import com.example.domain.model.ThemeMode
import com.example.ui.components.AboutLicensesDialog
import com.example.ui.components.FormatUtils
import com.example.ui.components.PinSetupDialog
import com.example.ui.components.PrivacyPolicyDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenTrash: () -> Unit
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val trashedCount by viewModel.trashedCount.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.refreshCacheSize()
    }

    var showThemePicker by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }
    var showFilterPicker by remember { mutableStateOf(false) }
    var showSizePicker by remember { mutableStateOf(false) }
    var showQualityPicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showPinSetup by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val backupTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            viewModel.setBackupFolder(uri.toString())
            viewModel.setAutoBackupEnabled(true)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Privacy Promise Banner
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            stringResource(R.string.privacy_promise),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "100% offline scanning, OCR & PDF processing. Zero trackers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            SettingsSectionCard(title = "Appearance") {
                SettingsValueRow(
                    icon = Icons.Outlined.Palette,
                    title = "Theme",
                    subtitle = prefs.themeMode.label,
                    onClick = { showThemePicker = true },
                    modifier = Modifier.testTag("settings_theme_row")
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider()
                    SettingsSwitchRow(
                        icon = Icons.Outlined.ColorLens,
                        title = "Dynamic Color",
                        subtitle = "Match wallpaper colors (Android 12+)",
                        checked = prefs.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor
                    )
                }
                HorizontalDivider()
                SettingsValueRow(
                    icon = Icons.Outlined.Language,
                    title = "Language",
                    subtitle = prefs.appLanguage.label,
                    onClick = { showLanguagePicker = true }
                )
            }

            SettingsSectionCard(title = "Scanning & Defaults") {
                SettingsValueRow(
                    icon = Icons.Outlined.Description,
                    title = "Default Scan Mode",
                    subtitle = prefs.defaultScanMode.label,
                    onClick = { showModePicker = true }
                )
                HorizontalDivider()
                SettingsValueRow(
                    icon = Icons.Outlined.AutoFixHigh,
                    title = "Default Filter",
                    subtitle = prefs.defaultFilter.label,
                    onClick = { showFilterPicker = true }
                )
                HorizontalDivider()
                SettingsValueRow(
                    icon = Icons.Outlined.Tune,
                    title = "Default Page Size",
                    subtitle = prefs.defaultPageSize.label,
                    onClick = { showSizePicker = true }
                )
                HorizontalDivider()
                SettingsValueRow(
                    icon = Icons.Outlined.Tune,
                    title = "Default Export Quality",
                    subtitle = prefs.defaultExportQuality.label,
                    onClick = { showQualityPicker = true }
                )
                HorizontalDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.AutoFixHigh,
                    title = "Auto-Capture",
                    subtitle = "Capture automatically when document edges stabilize",
                    checked = prefs.autoCapture,
                    onCheckedChange = viewModel::setAutoCapture
                )
                HorizontalDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.VolumeUp,
                    title = "Shutter Sound & Haptics",
                    subtitle = "Respects device silent mode",
                    checked = prefs.soundAndHaptics,
                    onCheckedChange = viewModel::setSoundAndHaptics
                )
            }

            SettingsSectionCard(title = "Storage & Security") {
                SettingsValueRow(
                    icon = Icons.Outlined.DeleteOutline,
                    title = stringResource(R.string.trash_title),
                    subtitle = if (trashedCount == 0) "Empty · 30-day retention" else "$trashedCount document(s) in Trash",
                    onClick = onOpenTrash,
                    modifier = Modifier.testTag("settings_trash_row")
                )
                HorizontalDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.Lock,
                    title = "App Lock (Biometric / PIN)",
                    subtitle = if (prefs.appLockEnabled) "Protected on launch & locked docs" else "Off",
                    checked = prefs.appLockEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) showPinSetup = true
                        else viewModel.setAppLock(false, null)
                    }
                )
                HorizontalDivider()
                SettingsValueRow(
                    icon = Icons.Outlined.Folder,
                    title = "Auto-Backup Folder",
                    subtitle = if (prefs.backupFolderUri != null) "Folder selected (SAF)" else "Tap to choose local/cloud SAF folder",
                    onClick = { backupTreeLauncher.launch(null) }
                )
                if (prefs.backupFolderUri != null) {
                    HorizontalDivider()
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Folder,
                        title = "Auto-Backup Exports",
                        subtitle = "Copy exported files to backup folder automatically",
                        checked = prefs.autoBackupEnabled,
                        onCheckedChange = viewModel::setAutoBackupEnabled
                    )
                }
                HorizontalDivider()
                SettingsValueRow(
                    icon = Icons.Outlined.CleaningServices,
                    title = "Clear Cache",
                    subtitle = "Temporary cache: ${FormatUtils.formatBytes(cacheBytes)}",
                    onClick = {
                        viewModel.clearCache()
                        scope.launch { snackbarHostState.showSnackbar("Cache cleared") }
                    }
                )
            }

            SettingsSectionCard(title = "About & Privacy") {
                SettingsValueRow(
                    icon = Icons.Outlined.PrivacyTip,
                    title = "Privacy Policy",
                    subtitle = "Read our offline-first privacy commitment",
                    onClick = { showPrivacyDialog = true }
                )
                HorizontalDivider()
                SettingsValueRow(
                    icon = Icons.Outlined.Share,
                    title = "Share ScanNest",
                    subtitle = "Recommend this offline scanner to friends",
                    onClick = {
                        com.example.ui.components.FormatUtils.shareText(
                            context,
                            "ScanNest — fast, private, 100% offline Android document scanner."
                        )
                    }
                )
                HorizontalDivider()
                SettingsValueRow(
                    icon = Icons.Outlined.Info,
                    title = "About & Open Source Licenses",
                    subtitle = "ScanNest v1.0.0",
                    onClick = { showAboutDialog = true }
                )
            }
        }
    }

    if (showThemePicker) {
        OptionPickerDialog(
            title = "Select Theme",
            options = ThemeMode.entries,
            selected = prefs.themeMode,
            labelOf = { it.label },
            onSelect = { viewModel.setThemeMode(it); showThemePicker = false },
            onDismiss = { showThemePicker = false }
        )
    }
    if (showModePicker) {
        OptionPickerDialog(
            title = "Default Scan Mode",
            options = ScanMode.entries,
            selected = prefs.defaultScanMode,
            labelOf = { it.label },
            onSelect = { viewModel.setDefaultScanMode(it); showModePicker = false },
            onDismiss = { showModePicker = false }
        )
    }
    if (showFilterPicker) {
        OptionPickerDialog(
            title = "Default Filter",
            options = FilterType.entries,
            selected = prefs.defaultFilter,
            labelOf = { it.label },
            onSelect = { viewModel.setDefaultFilter(it); showFilterPicker = false },
            onDismiss = { showFilterPicker = false }
        )
    }
    if (showSizePicker) {
        OptionPickerDialog(
            title = "Default Page Size",
            options = PageSizeOption.entries,
            selected = prefs.defaultPageSize,
            labelOf = { it.label },
            onSelect = { viewModel.setDefaultPageSize(it); showSizePicker = false },
            onDismiss = { showSizePicker = false }
        )
    }
    if (showQualityPicker) {
        OptionPickerDialog(
            title = "Default Export Quality",
            options = ExportQuality.entries,
            selected = prefs.defaultExportQuality,
            labelOf = { it.label },
            onSelect = { viewModel.setDefaultExportQuality(it); showQualityPicker = false },
            onDismiss = { showQualityPicker = false }
        )
    }
    if (showLanguagePicker) {
        OptionPickerDialog(
            title = "App Language",
            options = AppLanguage.entries,
            selected = prefs.appLanguage,
            labelOf = { it.label },
            onSelect = { viewModel.setLanguage(it); showLanguagePicker = false },
            onDismiss = { showLanguagePicker = false }
        )
    }
    if (showPinSetup) {
        PinSetupDialog(
            onConfirmPin = { pin -> viewModel.setAppLock(true, pin, true) },
            onDismiss = { showPinSetup = false }
        )
    }
    if (showPrivacyDialog) {
        PrivacyPolicyDialog { showPrivacyDialog = false }
    }
    if (showAboutDialog) {
        AboutLicensesDialog { showAboutDialog = false }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsValueRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> OptionPickerDialog(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = { onSelect(option) })
                        Spacer(Modifier.width(8.dp))
                        Text(labelOf(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
