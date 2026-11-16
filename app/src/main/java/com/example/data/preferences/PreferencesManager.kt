package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AppLanguage
import com.example.domain.model.ExportQuality
import com.example.domain.model.FilterType
import com.example.domain.model.PageSizeOption
import com.example.domain.model.ScanMode
import com.example.domain.model.SortOption
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scannest_prefs")

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val defaultScanMode: ScanMode = ScanMode.DOCUMENT,
    val defaultFilter: FilterType = FilterType.AUTO_ENHANCE,
    val defaultPageSize: PageSizeOption = PageSizeOption.A4,
    val defaultExportQuality: ExportQuality = ExportQuality.MEDIUM,
    val autoCapture: Boolean = true,
    val soundAndHaptics: Boolean = true,
    val sortOption: SortOption = SortOption.DATE,
    val isGridView: Boolean = false,
    val backupFolderUri: String? = null,
    val autoBackupEnabled: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.ENGLISH,
    val appLockEnabled: Boolean = false,
    val pinHash: String? = null,
    val useBiometric: Boolean = true
)

class PreferencesManager(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEFAULT_SCAN_MODE = stringPreferencesKey("default_scan_mode")
        val DEFAULT_FILTER = stringPreferencesKey("default_filter")
        val DEFAULT_PAGE_SIZE = stringPreferencesKey("default_page_size")
        val DEFAULT_EXPORT_QUALITY = stringPreferencesKey("default_export_quality")
        val AUTO_CAPTURE = booleanPreferencesKey("auto_capture")
        val SOUND_HAPTICS = booleanPreferencesKey("sound_haptics")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val IS_GRID_VIEW = booleanPreferencesKey("is_grid_view")
        val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val USE_BIOMETRIC = booleanPreferencesKey("use_biometric")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            themeMode = parseEnum(prefs[Keys.THEME_MODE], ThemeMode.SYSTEM),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            defaultScanMode = parseEnum(prefs[Keys.DEFAULT_SCAN_MODE], ScanMode.DOCUMENT),
            defaultFilter = parseEnum(prefs[Keys.DEFAULT_FILTER], FilterType.AUTO_ENHANCE),
            defaultPageSize = parseEnum(prefs[Keys.DEFAULT_PAGE_SIZE], PageSizeOption.A4),
            defaultExportQuality = parseEnum(prefs[Keys.DEFAULT_EXPORT_QUALITY], ExportQuality.MEDIUM),
            autoCapture = prefs[Keys.AUTO_CAPTURE] ?: true,
            soundAndHaptics = prefs[Keys.SOUND_HAPTICS] ?: true,
            sortOption = parseEnum(prefs[Keys.SORT_OPTION], SortOption.DATE),
            isGridView = prefs[Keys.IS_GRID_VIEW] ?: false,
            backupFolderUri = prefs[Keys.BACKUP_FOLDER_URI],
            autoBackupEnabled = prefs[Keys.AUTO_BACKUP_ENABLED] ?: false,
            appLanguage = parseEnum(prefs[Keys.APP_LANGUAGE], AppLanguage.ENGLISH),
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            pinHash = prefs[Keys.PIN_HASH],
            useBiometric = prefs[Keys.USE_BIOMETRIC] ?: true
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setDefaultScanMode(mode: ScanMode) {
        context.dataStore.edit { it[Keys.DEFAULT_SCAN_MODE] = mode.name }
    }

    suspend fun setDefaultFilter(filter: FilterType) {
        context.dataStore.edit { it[Keys.DEFAULT_FILTER] = filter.name }
    }

    suspend fun setDefaultPageSize(size: PageSizeOption) {
        context.dataStore.edit { it[Keys.DEFAULT_PAGE_SIZE] = size.name }
    }

    suspend fun setDefaultExportQuality(quality: ExportQuality) {
        context.dataStore.edit { it[Keys.DEFAULT_EXPORT_QUALITY] = quality.name }
    }

    suspend fun setAutoCapture(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CAPTURE] = enabled }
    }

    suspend fun setSoundAndHaptics(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND_HAPTICS] = enabled }
    }

    suspend fun setSortOption(sort: SortOption) {
        context.dataStore.edit { it[Keys.SORT_OPTION] = sort.name }
    }

    suspend fun setGridView(isGrid: Boolean) {
        context.dataStore.edit { it[Keys.IS_GRID_VIEW] = isGrid }
    }

    suspend fun setBackupFolderUri(uri: String?) {
        context.dataStore.edit {
            if (uri == null) it.remove(Keys.BACKUP_FOLDER_URI)
            else it[Keys.BACKUP_FOLDER_URI] = uri
        }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_BACKUP_ENABLED] = enabled }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.APP_LANGUAGE] = language.name }
    }

    suspend fun setAppLock(enabled: Boolean, pin: String? = null, useBiometric: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_ENABLED] = enabled
            prefs[Keys.USE_BIOMETRIC] = useBiometric
            if (pin != null) {
                prefs[Keys.PIN_HASH] = hashPin(pin)
            } else if (!enabled) {
                prefs.remove(Keys.PIN_HASH)
            }
        }
    }

    fun verifyPin(pin: String, storedHash: String?): Boolean {
        if (storedHash == null) return false
        return hashPin(pin) == storedHash
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("scannest_salt_$pin".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String?, default: T): T {
        if (value == null) return default
        return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
    }
}
