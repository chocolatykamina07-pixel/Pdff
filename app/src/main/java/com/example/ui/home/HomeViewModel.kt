package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DocumentEntity
import com.example.data.local.FolderEntity
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.ScanNestRepository
import com.example.domain.model.HomeTab
import com.example.domain.model.SearchCategory
import com.example.domain.model.SortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val folderItemCounts: Map<Long, Int> = emptyMap(),
    val currentFolderId: Long? = null,
    val currentFolderName: String? = null,
    val selectedTab: HomeTab = HomeTab.ALL,
    val searchQuery: String = "",
    val searchCategory: SearchCategory = SearchCategory.ALL,
    val isSearchActive: Boolean = false,
    val sortOption: SortOption = SortOption.DATE,
    val isGridView: Boolean = false,
    val selectedDocumentIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val previewDocumentId: Long? = null,
    val trashedCount: Int = 0
)

private data class HomeFilterInputs(
    val tab: HomeTab,
    val query: String,
    val searchCategory: SearchCategory,
    val searchActive: Boolean,
    val folderId: Long?,
    val selectedIds: Set<Long>,
    val previewDocId: Long?
)

class HomeViewModel(
    private val repository: ScanNestRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val selectedTab = MutableStateFlow(HomeTab.ALL)
    private val searchQuery = MutableStateFlow("")
    private val searchCategory = MutableStateFlow(SearchCategory.ALL)
    private val isSearchActive = MutableStateFlow(false)
    private val currentFolderId = MutableStateFlow<Long?>(null)
    private val selectedDocIds = MutableStateFlow<Set<Long>>(emptySet())
    private val previewDocId = MutableStateFlow<Long?>(null)

    private val filterInputs = combine(
        selectedTab,
        searchQuery,
        searchCategory,
        isSearchActive,
        currentFolderId
    ) { tab, query, category, searchActive, folderId ->
        HomeFilterInputs(
            tab = tab,
            query = query,
            searchCategory = category,
            searchActive = searchActive,
            folderId = folderId,
            selectedIds = emptySet(),
            previewDocId = null
        )
    }.combine(selectedDocIds) { inputs, selectedIds ->
        inputs.copy(selectedIds = selectedIds)
    }.combine(previewDocId) { inputs, previewId ->
        inputs.copy(previewDocId = previewId)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.activeDocuments,
        repository.allFolders,
        repository.trashedCount,
        preferencesManager.preferencesFlow,
        filterInputs
    ) { docs, folders, trashCount, prefs, inputs ->
        val folderCounts = docs.filter { it.folderId != null }
            .groupingBy { it.folderId!! }
            .eachCount()

        val activeFolder = folders.firstOrNull { it.id == inputs.folderId }
        val q = inputs.query.trim()

        val filteredByTab = when (inputs.tab) {
            HomeTab.ALL -> docs
            HomeTab.RECENT -> docs.sortedByDescending { it.updatedAt }.take(15)
            HomeTab.FAVORITES -> docs.filter { it.isFavorite }
        }

        val filteredByFolder = if (q.isNotEmpty() || inputs.tab != HomeTab.ALL) {
            filteredByTab
        } else {
            filteredByTab.filter { it.folderId == inputs.folderId }
        }

        val filteredDocs = if (q.isBlank()) {
            if (inputs.isSearchingFoldersOnly()) emptyList() else filteredByFolder
        } else {
            when (inputs.searchCategory) {
                SearchCategory.FOLDERS -> emptyList()
                SearchCategory.DOCS -> filteredByFolder.filter { it.name.contains(q, ignoreCase = true) }
                SearchCategory.OCR_TEXT -> filteredByFolder.filter { it.ocrText?.contains(q, ignoreCase = true) == true }
                SearchCategory.ALL -> filteredByFolder.filter { doc ->
                    doc.name.contains(q, ignoreCase = true) ||
                        (doc.ocrText?.contains(q, ignoreCase = true) == true) ||
                        doc.tags.contains(q, ignoreCase = true)
                }
            }
        }

        val sorted = when (prefs.sortOption) {
            SortOption.DATE -> filteredDocs.sortedByDescending { it.updatedAt }
            SortOption.NAME -> filteredDocs.sortedBy { it.name.lowercase() }
            SortOption.SIZE -> filteredDocs.sortedByDescending { it.sizeBytes }
        }

        val visibleFolders = when {
            q.isNotBlank() && (inputs.searchCategory == SearchCategory.ALL || inputs.searchCategory == SearchCategory.FOLDERS) -> {
                folders.filter { it.name.contains(q, ignoreCase = true) }
            }
            q.isNotBlank() -> emptyList()
            inputs.searchActive && inputs.searchCategory == SearchCategory.FOLDERS -> folders
            inputs.folderId == null && inputs.tab == HomeTab.ALL -> folders
            else -> emptyList()
        }

        HomeUiState(
            documents = sorted,
            folders = visibleFolders,
            folderItemCounts = folderCounts,
            currentFolderId = inputs.folderId,
            currentFolderName = activeFolder?.name,
            selectedTab = inputs.tab,
            searchQuery = inputs.query,
            searchCategory = inputs.searchCategory,
            isSearchActive = inputs.searchActive,
            sortOption = prefs.sortOption,
            isGridView = prefs.isGridView,
            selectedDocumentIds = inputs.selectedIds,
            isSelectionMode = inputs.selectedIds.isNotEmpty(),
            previewDocumentId = inputs.previewDocId ?: sorted.firstOrNull()?.id,
            trashedCount = trashCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    private fun HomeFilterInputs.isSearchingFoldersOnly(): Boolean =
        searchActive && searchCategory == SearchCategory.FOLDERS

    fun selectTab(tab: HomeTab) {
        selectedTab.value = tab
    }

    fun setSearchActive(active: Boolean) {
        isSearchActive.value = active
        if (!active) {
            searchQuery.value = ""
            searchCategory.value = SearchCategory.ALL
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setSearchCategory(category: SearchCategory) {
        searchCategory.value = category
    }

    fun openFolder(folderId: Long?) {
        currentFolderId.value = folderId
        clearSelection()
    }

    fun setPreviewDocument(documentId: Long?) {
        previewDocId.value = documentId
    }

    fun setSortOption(sort: SortOption) {
        viewModelScope.launch { preferencesManager.setSortOption(sort) }
    }

    fun toggleViewMode() {
        viewModelScope.launch { preferencesManager.setGridView(!uiState.value.isGridView) }
    }

    fun toggleSelection(documentId: Long) {
        val current = selectedDocIds.value.toMutableSet()
        if (!current.add(documentId)) current.remove(documentId)
        selectedDocIds.value = current
    }

    fun startSelection(documentId: Long) {
        selectedDocIds.value = setOf(documentId)
    }

    fun clearSelection() {
        selectedDocIds.value = emptySet()
    }

    fun selectAllVisible() {
        selectedDocIds.value = uiState.value.documents.map { it.id }.toSet()
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createFolder(name) }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            if (currentFolderId.value == folderId) currentFolderId.value = null
            repository.deleteFolder(folderId)
        }
    }

    fun toggleFavorite(documentId: Long) {
        viewModelScope.launch { repository.toggleFavorite(documentId) }
    }

    fun toggleLocked(documentId: Long) {
        viewModelScope.launch { repository.toggleLocked(documentId) }
    }

    fun renameDocument(documentId: Long, newName: String) {
        viewModelScope.launch { repository.renameDocument(documentId, newName) }
    }

    fun duplicateDocument(documentId: Long) {
        viewModelScope.launch { repository.duplicateDocument(documentId) }
    }

    fun moveDocumentsToFolder(docIds: List<Long>, folderId: Long?) {
        viewModelScope.launch {
            repository.moveToFolder(docIds, folderId)
            clearSelection()
        }
    }

    fun moveToTrash(docIds: List<Long>) {
        viewModelScope.launch {
            repository.moveToTrash(docIds)
            clearSelection()
        }
    }

    fun restoreFromTrash(docIds: List<Long>) {
        viewModelScope.launch { repository.restoreFromTrash(docIds) }
    }

    fun mergeSelectedDocuments(name: String) {
        val ids = selectedDocIds.value.toList()
        if (ids.size < 2) return
        viewModelScope.launch {
            repository.mergeDocuments(ids, name.ifBlank { repository.defaultDocumentName("Merged") })
            clearSelection()
        }
    }
}
