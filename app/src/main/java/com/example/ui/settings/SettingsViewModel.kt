package com.example.ui.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DocumentEntity
import com.example.data.preferences.PreferencesManager
import com.example.data.preferences.UserPreferences
import com.example.data.repository.ScanNestRepository
import com.example.domain.model.AppLanguage
import com.example.domain.model.ExportQuality
import com.example.domain.model.FilterType
import com.example.domain.model.PageSizeOption
import com.example.domain.model.ScanMode
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val context: Context,
    private val repository: ScanNestRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesManager.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val trashedDocuments: StateFlow<List<DocumentEntity>> = repository.trashedDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedCount: StateFlow<Int> = repository.trashedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _cacheSizeBytes = MutableStateFlow(repository.getCacheSizeBytes())
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    fun refreshCacheSize() {
        _cacheSizeBytes.value = repository.getCacheSizeBytes()
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCache()
            _cacheSizeBytes.value = repository.getCacheSizeBytes()
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { preferencesManager.setOnboardingCompleted(true) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesManager.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setDynamicColor(enabled) }
    }

    fun setDefaultScanMode(mode: ScanMode) {
        viewModelScope.launch { preferencesManager.setDefaultScanMode(mode) }
    }

    fun setDefaultFilter(filter: FilterType) {
        viewModelScope.launch { preferencesManager.setDefaultFilter(filter) }
    }

    fun setDefaultPageSize(size: PageSizeOption) {
        viewModelScope.launch { preferencesManager.setDefaultPageSize(size) }
    }

    fun setDefaultExportQuality(quality: ExportQuality) {
        viewModelScope.launch { preferencesManager.setDefaultExportQuality(quality) }
    }

    fun setAutoCapture(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setAutoCapture(enabled) }
    }

    fun setSoundAndHaptics(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setSoundAndHaptics(enabled) }
    }

    fun setBackupFolder(uriString: String?) {
        viewModelScope.launch { preferencesManager.setBackupFolderUri(uriString) }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setAutoBackupEnabled(enabled) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            preferencesManager.setAppLanguage(language)
            val locales = LocaleListCompat.forLanguageTags(language.tag)
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    fun setAppLock(enabled: Boolean, pin: String?, useBiometric: Boolean = true) {
        viewModelScope.launch {
            preferencesManager.setAppLock(enabled, pin, useBiometric)
        }
    }

    fun verifyPin(pin: String): Boolean {
        return preferencesManager.verifyPin(pin, preferences.value.pinHash)
    }

    fun restoreDocument(docId: Long) {
        viewModelScope.launch { repository.restoreFromTrash(listOf(docId)) }
    }

    fun deleteForever(docId: Long) {
        viewModelScope.launch { repository.deleteDocumentPermanently(docId) }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            trashedDocuments.value.forEach { repository.deleteDocumentPermanently(it.id) }
        }
    }
}
