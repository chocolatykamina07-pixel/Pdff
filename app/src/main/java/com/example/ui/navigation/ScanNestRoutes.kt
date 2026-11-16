package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object OnboardingRoute

@Serializable
object HomeRoute

@Serializable
object ToolsRoute

@Serializable
object SettingsRoute

@Serializable
object TrashRoute

@Serializable
data class ScannerRoute(
    val initialModeName: String? = null,
    val appendToDocumentId: Long? = null
)

@Serializable
data class CropAdjustRoute(
    val documentId: Long,
    val pageId: Long
)

@Serializable
data class FilterEnhanceRoute(
    val documentId: Long,
    val pageId: Long
)

@Serializable
data class DocumentEditorRoute(
    val documentId: Long
)

@Serializable
data class SignAnnotateRoute(
    val pageId: Long
)

@Serializable
data class OcrRoute(
    val documentId: Long,
    val pageId: Long? = null
)
