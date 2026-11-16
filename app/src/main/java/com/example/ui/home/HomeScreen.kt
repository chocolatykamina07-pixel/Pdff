package com.example.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.DocumentEntity
import com.example.domain.model.HomeTab
import com.example.domain.model.SortOption
import com.example.ui.components.EmptyNestIllustration
import com.example.ui.components.MoveToFolderDialog
import com.example.ui.components.ScanNestLogoMark
import com.example.ui.components.TextInputDialog
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.PillShape
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    isExpandedWidth: Boolean,
    onOpenScanner: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    onExportDocument: (DocumentEntity) -> Unit,
    onImportUris: (List<android.net.Uri>) -> Unit,
    onOpenTrash: () -> Unit,
    onVerifyDocUnlock: (DocumentEntity, () -> Unit) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var overflowOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var docToRename by remember { mutableStateOf<DocumentEntity?>(null) }
    var docIdsToMove by remember { mutableStateOf<List<Long>?>(null) }
    var showMergeNameDialog by remember { mutableStateOf(false) }

    val galleryImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) onImportUris(uris)
    }

    if (state.isSelectionMode) {
        BackHandler { viewModel.clearSelection() }
    } else if (state.currentFolderId != null) {
        BackHandler { viewModel.openFolder(null) }
    }

    fun triggerDeleteWithUndo(docIds: List<Long>) {
        viewModel.moveToTrash(docIds)
        scope.launch {
            val res = snackbarHostState.showSnackbar(
                message = "Moved ${docIds.size} item(s) to Trash",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (res == SnackbarResult.ActionPerformed) {
                viewModel.restoreFromTrash(docIds)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.isSelectionMode) {
                TopAppBar(
                    title = { Text("${state.selectedDocumentIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::selectAllVisible) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = "Select all")
                        }
                        if (state.selectedDocumentIds.size >= 2) {
                            IconButton(onClick = { showMergeNameDialog = true }) {
                                Icon(Icons.Outlined.Merge, contentDescription = "Merge selected")
                            }
                        }
                        IconButton(onClick = { docIdsToMove = state.selectedDocumentIds.toList() }) {
                            Icon(Icons.Outlined.DriveFileMove, contentDescription = "Move selected")
                        }
                        IconButton(onClick = {
                            triggerDeleteWithUndo(state.selectedDocumentIds.toList())
                        }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete selected")
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    navigationIcon = {
                        if (state.currentFolderId != null) {
                            IconButton(onClick = { viewModel.openFolder(null) }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to root")
                            }
                        } else {
                            Box(modifier = Modifier.padding(start = 12.dp)) {
                                ScanNestLogoMark(size = 30.dp)
                            }
                        }
                    },
                    title = {
                        Text(
                            text = state.currentFolderName ?: stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.setSearchActive(!state.isSearchActive) },
                            modifier = Modifier.testTag("home_search_button")
                        ) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search")
                        }
                        IconButton(
                            onClick = viewModel::toggleViewMode,
                            modifier = Modifier.testTag("home_view_toggle")
                        ) {
                            Icon(
                                if (state.isGridView) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                                contentDescription = "Toggle list or grid view"
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { overflowOpen = true },
                                modifier = Modifier.testTag("home_overflow_button")
                            ) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_by) + " (${state.sortOption.label})") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null) },
                                    onClick = {
                                        overflowOpen = false
                                        sortMenuOpen = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.create_folder)) },
                                    leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                                    onClick = {
                                        overflowOpen = false
                                        showCreateFolderDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.multi_select)) },
                                    leadingIcon = { Icon(Icons.Outlined.SelectAll, contentDescription = null) },
                                    onClick = {
                                        overflowOpen = false
                                        state.documents.firstOrNull()?.let { viewModel.startSelection(it.id) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        val count = state.trashedCount
                                        Text(if (count > 0) "Trash ($count)" else stringResource(R.string.open_trash))
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                    onClick = {
                                        overflowOpen = false
                                        onOpenTrash()
                                    }
                                )
                            }
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                SortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            sortMenuOpen = false
                                            viewModel.setSortOption(option)
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isSearchActive) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { viewModel.setSearchActive(false) }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close search")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("home_search_input")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.example.domain.model.SearchCategory.entries.forEach { cat ->
                        androidx.compose.material3.FilterChip(
                            selected = state.searchCategory == cat,
                            onClick = { viewModel.setSearchCategory(cat) },
                            label = { Text(cat.label) }
                        )
                    }
                }
            }

            TabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                HomeTab.entries.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            val title = when (tab) {
                                HomeTab.ALL -> "All Documents"
                                HomeTab.RECENT -> stringResource(R.string.tab_recent)
                                HomeTab.FAVORITES -> "Favorite"
                            }
                            Text(
                                text = title,
                                fontWeight = if (state.selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.testTag("home_tab_${tab.name.lowercase()}")
                    )
                }
            }

            if (state.documents.isEmpty() && state.folders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        EmptyNestIllustration()
                        Text(
                            text = if (state.searchQuery.isNotBlank()) "No matching documents"
                            else stringResource(R.string.empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = onOpenScanner,
                            shape = PillShape,
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("empty_scan_button")
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.scan_first_doc))
                        }
                        OutlinedButton(
                            onClick = {
                                galleryImportLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = PillShape,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.import_from_gallery))
                        }
                    }
                }
            } else if (isExpandedWidth) {
                // Two-pane tablet / foldable List-Detail layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.weight(0.48f).fillMaxHeight()) {
                        DocumentCollectionList(
                            state = state,
                            isGrid = false,
                            onFolderClick = { viewModel.openFolder(it) },
                            onFolderDelete = { viewModel.deleteFolder(it) },
                            onDocClick = { doc ->
                                if (state.isSelectionMode) {
                                    viewModel.toggleSelection(doc.id)
                                } else {
                                    viewModel.setPreviewDocument(doc.id)
                                }
                            },
                            onDocLongClick = { viewModel.startSelection(it.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(it.id) },
                            onRename = { docToRename = it },
                            onMove = { docIdsToMove = listOf(it.id) },
                            onShare = { doc -> onVerifyDocUnlock(doc) { onExportDocument(doc) } },
                            onExport = { doc -> onVerifyDocUnlock(doc) { onExportDocument(doc) } },
                            onDuplicate = { viewModel.duplicateDocument(it.id) },
                            onToggleLock = { doc -> onVerifyDocUnlock(doc) { viewModel.toggleLocked(doc.id) } },
                            onDelete = { triggerDeleteWithUndo(listOf(it.id)) }
                        )
                    }
                    Box(modifier = Modifier.weight(0.52f).fillMaxHeight()) {
                        val previewDoc = state.documents.firstOrNull { it.id == state.previewDocumentId }
                        TabletDocumentPreviewPane(
                            document = previewDoc,
                            onOpenEditor = { id ->
                                previewDoc?.let { d -> onVerifyDocUnlock(d) { onOpenDocument(id) } }
                            },
                            onExport = { d -> onVerifyDocUnlock(d) { onExportDocument(d) } }
                        )
                    }
                }
            } else {
                DocumentCollectionList(
                    state = state,
                    isGrid = state.isGridView,
                    onFolderClick = { viewModel.openFolder(it) },
                    onFolderDelete = { viewModel.deleteFolder(it) },
                    onDocClick = { doc ->
                        if (state.isSelectionMode) {
                            viewModel.toggleSelection(doc.id)
                        } else {
                            onVerifyDocUnlock(doc) { onOpenDocument(doc.id) }
                        }
                    },
                    onDocLongClick = { viewModel.startSelection(it.id) },
                    onToggleFavorite = { viewModel.toggleFavorite(it.id) },
                    onRename = { docToRename = it },
                    onMove = { docIdsToMove = listOf(it.id) },
                    onShare = { doc -> onVerifyDocUnlock(doc) { onExportDocument(doc) } },
                    onExport = { doc -> onVerifyDocUnlock(doc) { onExportDocument(doc) } },
                    onDuplicate = { viewModel.duplicateDocument(it.id) },
                    onToggleLock = { doc -> onVerifyDocUnlock(doc) { viewModel.toggleLocked(doc.id) } },
                    onDelete = { triggerDeleteWithUndo(listOf(it.id)) }
                )
            }
        }
    }

    if (showCreateFolderDialog) {
        TextInputDialog(
            title = stringResource(R.string.create_folder),
            label = "Folder name",
            confirmText = "Create",
            onConfirm = { viewModel.createFolder(it) },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    docToRename?.let { doc ->
        TextInputDialog(
            title = stringResource(R.string.action_rename),
            label = "Document name",
            initialValue = doc.name,
            confirmText = "Rename",
            onConfirm = { viewModel.renameDocument(doc.id, it) },
            onDismiss = { docToRename = null }
        )
    }

    docIdsToMove?.let { ids ->
        MoveToFolderDialog(
            folders = state.folders,
            onSelectFolder = { folderId -> viewModel.moveDocumentsToFolder(ids, folderId) },
            onDismiss = { docIdsToMove = null }
        )
    }

    if (showMergeNameDialog) {
        TextInputDialog(
            title = "Merge ${state.selectedDocumentIds.size} Documents",
            label = "Merged document name",
            initialValue = "Merged Document",
            confirmText = "Merge",
            onConfirm = { viewModel.mergeSelectedDocuments(it) },
            onDismiss = { showMergeNameDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentCollectionList(
    state: HomeUiState,
    isGrid: Boolean,
    onFolderClick: (Long) -> Unit,
    onFolderDelete: (Long) -> Unit,
    onDocClick: (DocumentEntity) -> Unit,
    onDocLongClick: (DocumentEntity) -> Unit,
    onToggleFavorite: (DocumentEntity) -> Unit,
    onRename: (DocumentEntity) -> Unit,
    onMove: (DocumentEntity) -> Unit,
    onShare: (DocumentEntity) -> Unit,
    onExport: (DocumentEntity) -> Unit,
    onDuplicate: (DocumentEntity) -> Unit,
    onToggleLock: (DocumentEntity) -> Unit,
    onDelete: (DocumentEntity) -> Unit
) {
    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.folders, key = { "folder_${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { folder ->
                FolderCard(
                    folder = folder,
                    itemCount = state.folderItemCounts[folder.id] ?: 0,
                    onClick = { onFolderClick(folder.id) },
                    onDeleteFolder = { onFolderDelete(folder.id) }
                )
            }
            items(state.documents, key = { "doc_${it.id}" }) { doc ->
                DocumentGridCard(
                    doc = doc,
                    isSelected = doc.id in state.selectedDocumentIds,
                    onClick = { onDocClick(doc) },
                    onLongClick = { onDocLongClick(doc) },
                    onToggleFavorite = { onToggleFavorite(doc) },
                    onRename = { onRename(doc) },
                    onMove = { onMove(doc) },
                    onShare = { onShare(doc) },
                    onExport = { onExport(doc) },
                    onDuplicate = { onDuplicate(doc) },
                    onToggleLock = { onToggleLock(doc) },
                    onDelete = { onDelete(doc) }
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.folders, key = { "folder_${it.id}" }) { folder ->
                FolderCard(
                    folder = folder,
                    itemCount = state.folderItemCounts[folder.id] ?: 0,
                    onClick = { onFolderClick(folder.id) },
                    onDeleteFolder = { onFolderDelete(folder.id) }
                )
            }
            items(state.documents, key = { "doc_${it.id}" }) { doc ->
                val dismissState = rememberSwipeToDismissBoxState()
                LaunchedEffect(dismissState.currentValue) {
                    when (dismissState.currentValue) {
                        SwipeToDismissBoxValue.StartToEnd -> {
                            onToggleFavorite(doc)
                            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                        }
                        SwipeToDismissBoxValue.EndToStart -> {
                            onDelete(doc)
                            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                        }
                        SwipeToDismissBoxValue.Settled -> Unit
                    }
                }
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        val isStart = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                        val bg = if (isStart) AmberAccent.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.errorContainer
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(bg, MaterialTheme.shapes.medium)
                                .padding(horizontal = 20.dp),
                            contentAlignment = if (isStart) Alignment.CenterStart else Alignment.CenterEnd
                        ) {
                            Icon(
                                imageVector = if (isStart) Icons.Outlined.Star else Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = if (isStart) AmberAccent else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                ) {
                    DocumentListItem(
                        doc = doc,
                        isSelected = doc.id in state.selectedDocumentIds,
                        isActivePreview = doc.id == state.previewDocumentId,
                        onClick = { onDocClick(doc) },
                        onLongClick = { onDocLongClick(doc) },
                        onToggleFavorite = { onToggleFavorite(doc) },
                        onRename = { onRename(doc) },
                        onMove = { onMove(doc) },
                        onShare = { onShare(doc) },
                        onExport = { onExport(doc) },
                        onDuplicate = { onDuplicate(doc) },
                        onToggleLock = { onToggleLock(doc) },
                        onDelete = { onDelete(doc) }
                    )
                }
            }
        }
    }
}
