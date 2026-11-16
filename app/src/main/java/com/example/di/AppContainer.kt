package com.example.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.ScanNestDatabase
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.ScanNestRepository
import com.example.ui.editor.DocumentEditorViewModel
import com.example.ui.home.HomeViewModel
import com.example.ui.scanner.ScannerViewModel
import com.example.ui.settings.SettingsViewModel
import com.example.ui.tools.ToolsViewModel

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: ScanNestDatabase by lazy { ScanNestDatabase.getInstance(appContext) }
    val preferencesManager: PreferencesManager by lazy { PreferencesManager(appContext) }

    val repository: ScanNestRepository by lazy {
        ScanNestRepository(
            context = appContext,
            documentDao = database.documentDao(),
            pageDao = database.pageDao(),
            folderDao = database.folderDao(),
            signatureDao = database.signatureDao()
        )
    }

    val viewModelFactory: ViewModelProvider.Factory by lazy {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                        HomeViewModel(repository, preferencesManager) as T
                    modelClass.isAssignableFrom(ScannerViewModel::class.java) ->
                        ScannerViewModel(appContext, repository, preferencesManager) as T
                    modelClass.isAssignableFrom(DocumentEditorViewModel::class.java) ->
                        DocumentEditorViewModel(appContext, repository, preferencesManager) as T
                    modelClass.isAssignableFrom(ToolsViewModel::class.java) ->
                        ToolsViewModel(appContext, repository) as T
                    modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                        SettingsViewModel(appContext, repository, preferencesManager) as T
                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
