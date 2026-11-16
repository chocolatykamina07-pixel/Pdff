package com.example.ui.navigation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.data.local.DocumentEntity
import com.example.di.AppContainer
import com.example.domain.model.ScanMode
import com.example.ui.components.ExportBottomSheet
import com.example.ui.editor.CropAdjustScreen
import com.example.ui.editor.DocumentEditorScreen
import com.example.ui.editor.DocumentEditorViewModel
import com.example.ui.editor.FilterEnhanceScreen
import com.example.ui.editor.OcrScreen
import com.example.ui.editor.SignAnnotateScreen
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.scanner.ScannerScreen
import com.example.ui.scanner.ScannerViewModel
import com.example.ui.settings.AppLockScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.settings.TrashScreen
import com.example.ui.tools.ToolsScreen
import com.example.ui.tools.ToolsViewModel
import com.example.ui.theme.AmberAccent
import kotlinx.coroutines.launch

@Composable
fun ScanNestNavHost(
    container: AppContainer,
    widthSizeClass: WindowWidthSizeClass
) {
    val settingsViewModel: SettingsViewModel = viewModel(factory = container.viewModelFactory)
    val homeViewModel: HomeViewModel = viewModel(factory = container.viewModelFactory)
    val scannerViewModel: ScannerViewModel = viewModel(factory = container.viewModelFactory)
    val editorViewModel: DocumentEditorViewModel = viewModel(factory = container.viewModelFactory)
    val toolsViewModel: ToolsViewModel = viewModel(factory = container.viewModelFactory)

    val prefs by settingsViewModel.preferences.collectAsStateWithLifecycle()
    val exportState by editorViewModel.exportState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    val isExpanded = widthSizeClass != WindowWidthSizeClass.Compact
    var appUnlocked by remember(prefs.appLockEnabled) { mutableStateOf(!prefs.appLockEnabled) }
    var pendingDocUnlockAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var exportSheetDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (!appUnlocked && prefs.appLockEnabled) {
        AppLockScreen(
            title = "ScanNest Locked",
            subtitle = "Unlock to access your offline documents",
            canDismiss = false,
            onVerifyPin = settingsViewModel::verifyPin,
            onUnlocked = { appUnlocked = true }
        )
        return
    }

    pendingDocUnlockAction?.let { action ->
        AppLockScreen(
            title = "Locked Document",
            subtitle = "Verify identity to open or export this document",
            canDismiss = true,
            onVerifyPin = settingsViewModel::verifyPin,
            onUnlocked = {
                pendingDocUnlockAction = null
                action()
            },
            onDismiss = { pendingDocUnlockAction = null }
        )
        return
    }

    val showTopLevelChrome = currentDest?.let { dest ->
        dest.hasRoute<HomeRoute>() || dest.hasRoute<ToolsRoute>() || dest.hasRoute<SettingsRoute>()
    } ?: false

    val startDest: Any = if (prefs.onboardingCompleted) HomeRoute else OnboardingRoute

    fun navigateTopTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openScanner(mode: ScanMode? = null, appendToDocId: Long? = null) {
        navController.navigate(ScannerRoute(initialModeName = mode?.name, appendToDocumentId = appendToDocId))
    }

    val navContent: @Composable (Modifier) -> Unit = { contentModifier ->
        NavHost(
            navController = navController,
            startDestination = startDest,
            modifier = contentModifier
        ) {
            composable<OnboardingRoute> {
                OnboardingScreen(
                    onComplete = {
                        settingsViewModel.completeOnboarding()
                        navController.navigate(HomeRoute) {
                            popUpTo<OnboardingRoute> { inclusive = true }
                        }
                    }
                )
            }

            composable<HomeRoute> {
                HomeScreen(
                    viewModel = homeViewModel,
                    isExpandedWidth = isExpanded,
                    onOpenScanner = { openScanner() },
                    onOpenDocument = { docId -> navController.navigate(DocumentEditorRoute(docId)) },
                    onExportDocument = { doc ->
                        editorViewModel.loadDocument(doc.id)
                        exportSheetDoc = doc
                    },
                    onImportUris = { uris ->
                        scannerViewModel.importGalleryImages(
                            uris = uris,
                            appendToDocumentId = null,
                            onNavigateToCrop = { dId, pId ->
                                navController.navigate(CropAdjustRoute(dId, pId))
                            },
                            onNavigateToEditor = { dId ->
                                navController.navigate(DocumentEditorRoute(dId))
                            }
                        )
                    },
                    onOpenTrash = { navController.navigate(TrashRoute) },
                    onVerifyDocUnlock = { doc, action ->
                        if (doc.isLocked && prefs.pinHash != null) {
                            pendingDocUnlockAction = action
                        } else {
                            action()
                        }
                    }
                )
            }

            composable<ToolsRoute> {
                ToolsScreen(
                    viewModel = toolsViewModel,
                    onOpenQrScanner = { openScanner(mode = ScanMode.QR_BARCODE) },
                    onOpenDocument = { docId -> navController.navigate(DocumentEditorRoute(docId)) }
                )
            }

            composable<SettingsRoute> {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onOpenTrash = { navController.navigate(TrashRoute) }
                )
            }

            composable<TrashRoute> {
                TrashScreen(
                    viewModel = settingsViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<ScannerRoute> { entry ->
                val args = entry.toRoute<ScannerRoute>()
                val mode = args.initialModeName?.let { runCatching { ScanMode.valueOf(it) }.getOrNull() }
                ScannerScreen(
                    viewModel = scannerViewModel,
                    initialMode = mode,
                    appendToDocumentId = args.appendToDocumentId,
                    onClose = { navController.popBackStack() },
                    onNavigateToCrop = { docId, firstPageId ->
                        navController.navigate(CropAdjustRoute(docId, firstPageId)) {
                            popUpTo<ScannerRoute> { inclusive = true }
                        }
                    },
                    onNavigateToEditor = { docId ->
                        navController.navigate(DocumentEditorRoute(docId)) {
                            popUpTo<ScannerRoute> { inclusive = true }
                        }
                    }
                )
            }

            composable<CropAdjustRoute> { entry ->
                val args = entry.toRoute<CropAdjustRoute>()
                CropAdjustScreen(
                    viewModel = editorViewModel,
                    documentId = args.documentId,
                    pageId = args.pageId,
                    onRetake = {
                        navController.navigate(ScannerRoute()) {
                            popUpTo<CropAdjustRoute> { inclusive = true }
                        }
                    },
                    onOpenFilters = {
                        navController.navigate(FilterEnhanceRoute(args.documentId, args.pageId)) {
                            popUpTo<CropAdjustRoute> { inclusive = true }
                        }
                    },
                    onDone = {
                        navController.navigate(DocumentEditorRoute(args.documentId)) {
                            popUpTo<CropAdjustRoute> { inclusive = true }
                        }
                    }
                )
            }

            composable<FilterEnhanceRoute> { entry ->
                val args = entry.toRoute<FilterEnhanceRoute>()
                FilterEnhanceScreen(
                    viewModel = editorViewModel,
                    documentId = args.documentId,
                    pageId = args.pageId,
                    onDone = {
                        navController.navigate(DocumentEditorRoute(args.documentId)) {
                            popUpTo<FilterEnhanceRoute> { inclusive = true }
                        }
                    }
                )
            }

            composable<DocumentEditorRoute> { entry ->
                val args = entry.toRoute<DocumentEditorRoute>()
                DocumentEditorScreen(
                    viewModel = editorViewModel,
                    documentId = args.documentId,
                    onBack = { navController.popBackStack() },
                    onAddPageCamera = { openScanner(appendToDocId = args.documentId) },
                    onOpenCrop = { pageId -> navController.navigate(CropAdjustRoute(args.documentId, pageId)) },
                    onOpenFilter = { pageId -> navController.navigate(FilterEnhanceRoute(args.documentId, pageId)) },
                    onOpenSign = { pageId -> navController.navigate(SignAnnotateRoute(pageId)) },
                    onOpenOcr = { pageId -> navController.navigate(OcrRoute(args.documentId, pageId)) }
                )
            }

            composable<SignAnnotateRoute> { entry ->
                val args = entry.toRoute<SignAnnotateRoute>()
                SignAnnotateScreen(
                    viewModel = editorViewModel,
                    pageId = args.pageId,
                    onDone = { navController.popBackStack() }
                )
            }

            composable<OcrRoute> { entry ->
                val args = entry.toRoute<OcrRoute>()
                OcrScreen(
                    viewModel = editorViewModel,
                    documentId = args.documentId,
                    pageId = args.pageId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    if (showTopLevelChrome && isExpanded) {
        // Tablet / Foldable NavigationRail layout
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                NavigationRail(
                    header = {
                        Spacer(Modifier.height(12.dp))
                        FloatingActionButton(
                            onClick = { openScanner() },
                            shape = CircleShape,
                            containerColor = AmberAccent,
                            contentColor = Color(0xFF0F172A),
                            modifier = Modifier
                                .size(56.dp)
                                .border(2.dp, AmberAccent.copy(alpha = 0.45f), CircleShape)
                                .testTag("scan_fab")
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = stringResource(R.string.cd_scan_fab))
                        }
                    },
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(Modifier.height(20.dp))
                    NavigationRailItem(
                        selected = currentDest?.hasRoute<HomeRoute>() == true,
                        onClick = { navigateTopTab(HomeRoute) },
                        icon = { Icon(Icons.Outlined.FolderCopy, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_home)) }
                    )
                    NavigationRailItem(
                        selected = currentDest?.hasRoute<ToolsRoute>() == true,
                        onClick = { navigateTopTab(ToolsRoute) },
                        icon = { Icon(Icons.Outlined.Build, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_tools)) }
                    )
                    NavigationRailItem(
                        selected = currentDest?.hasRoute<SettingsRoute>() == true,
                        onClick = { navigateTopTab(SettingsRoute) },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_settings)) }
                    )
                }
                navContent(Modifier.weight(1f).fillMaxHeight())
            }
        }
    } else if (showTopLevelChrome) {
        // Phone BottomBar + Centered Amber Scan FAB
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { openScanner() },
                    shape = CircleShape,
                    containerColor = AmberAccent,
                    contentColor = Color(0xFF0F172A),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .size(62.dp)
                        .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .testTag("scan_fab")
                ) {
                    Icon(
                        Icons.Outlined.CameraAlt,
                        contentDescription = stringResource(R.string.cd_scan_fab),
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            floatingActionButtonPosition = FabPosition.Center,
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentDest?.hasRoute<HomeRoute>() == true,
                        onClick = { navigateTopTab(HomeRoute) },
                        icon = { Icon(Icons.Outlined.FolderCopy, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_home)) },
                        modifier = Modifier.testTag("nav_tab_home")
                    )
                    NavigationBarItem(
                        selected = currentDest?.hasRoute<ToolsRoute>() == true,
                        onClick = { navigateTopTab(ToolsRoute) },
                        icon = { Icon(Icons.Outlined.Build, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_tools)) },
                        modifier = Modifier.testTag("nav_tab_tools")
                    )
                    NavigationBarItem(
                        selected = currentDest?.hasRoute<SettingsRoute>() == true,
                        onClick = { navigateTopTab(SettingsRoute) },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_settings)) },
                        modifier = Modifier.testTag("nav_tab_settings")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                navContent(Modifier.fillMaxSize())
            }
        }
    } else {
        navContent(Modifier.fillMaxSize())
    }

    exportSheetDoc?.let { doc ->
        ExportBottomSheet(
            documentId = doc.id,
            documentName = doc.name,
            defaultPageSize = prefs.defaultPageSize,
            defaultQuality = prefs.defaultExportQuality,
            exportState = exportState,
            estimateSizeBytes = editorViewModel::estimateExportSize,
            onStartExport = { req -> editorViewModel.startExport(req) },
            onCancelExport = editorViewModel::cancelExport,
            onClearResult = editorViewModel::clearExportResult,
            onDismiss = { exportSheetDoc = null },
            onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
        )
    }
}
