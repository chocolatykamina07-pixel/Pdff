package com.example.domain.model

import kotlinx.serialization.Serializable

enum class ScanMode(val label: String) {
    DOCUMENT("Document"),
    ID_CARD("ID Card"),
    BOOK("Book"),
    WHITEBOARD("Whiteboard"),
    QR_BARCODE("QR/Barcode"),
    PHOTO("Photo")
}

enum class FilterType(val label: String) {
    ORIGINAL("Original"),
    AUTO_ENHANCE("Auto Enhance"),
    GRAYSCALE("Grayscale"),
    BLACK_WHITE("B&W"),
    CLEAN_DOC("Clean Doc"),
    COLOR_BOOST("Color Boost")
}

enum class PageSizeOption(val label: String, val widthPt: Int, val heightPt: Int) {
    AUTO("Auto", 0, 0),
    A4("A4", 595, 842),
    A3("A3", 842, 1191),
    LETTER("Letter", 612, 792),
    LEGAL("Legal", 612, 1008),
    ID_CARD("ID Card", 243, 153),
    BUSINESS_CARD("Business Card", 252, 144)
}

enum class ExportQuality(val label: String, val jpegQuality: Int, val maxDimensionPx: Int) {
    SMALL("Small", 55, 1200),
    MEDIUM("Medium", 78, 1800),
    LARGE("Large", 94, 2600)
}

enum class ExportFormat(val label: String, val mimeType: String, val extension: String) {
    PDF("PDF", "application/pdf", "pdf"),
    JPEG("JPEG", "image/jpeg", "jpg"),
    TXT("TXT", "text/plain", "txt"),
    DOCX("DOCX", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx")
}

enum class MarginOption(val label: String, val marginPt: Float) {
    NONE("None", 0f),
    SMALL("Small", 18f),
    NORMAL("Normal", 36f)
}

enum class OrientationOption(val label: String) {
    AUTO("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class SortOption(val label: String) {
    DATE("Date"),
    NAME("Name"),
    SIZE("Size")
}

enum class HomeTab(val label: String) {
    ALL("All"),
    RECENT("Recent"),
    FAVORITES("Favorites")
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class AppLanguage(val label: String, val tag: String) {
    ENGLISH("English", "en"),
    HINDI("हिन्दी (Hindi)", "hi")
}

enum class OcrLanguage(val label: String) {
    LATIN("English / Latin"),
    DEVANAGARI("हिन्दी / Devanagari")
}

enum class FlashMode(val label: String) {
    AUTO("Auto"),
    ON("On"),
    OFF("Off")
}

@Serializable
data class NormalizedPoint(val x: Float, val y: Float)

@Serializable
data class CropQuad(
    val topLeft: NormalizedPoint = NormalizedPoint(0.08f, 0.08f),
    val topRight: NormalizedPoint = NormalizedPoint(0.92f, 0.08f),
    val bottomRight: NormalizedPoint = NormalizedPoint(0.92f, 0.92f),
    val bottomLeft: NormalizedPoint = NormalizedPoint(0.08f, 0.92f)
) {
    fun asList(): List<NormalizedPoint> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    companion object {
        val FULL = CropQuad(
            topLeft = NormalizedPoint(0f, 0f),
            topRight = NormalizedPoint(1f, 0f),
            bottomRight = NormalizedPoint(1f, 1f),
            bottomLeft = NormalizedPoint(0f, 1f)
        )
    }
}

data class TextBlockItem(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class OcrPageResult(
    val pageIndex: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val fullText: String,
    val blocks: List<TextBlockItem>
)

data class ExportRequest(
    val documentId: Long,
    val format: ExportFormat = ExportFormat.PDF,
    val quality: ExportQuality = ExportQuality.MEDIUM,
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val margin: MarginOption = MarginOption.SMALL,
    val orientation: OrientationOption = OrientationOption.AUTO,
    val password: String = "",
    val searchablePdf: Boolean = false
)

enum class ToolType(val title: String, val subtitle: String) {
    IMAGE_TO_PDF("Image to PDF", "Convert photos into a crisp PDF"),
    MERGE_PDF("Merge PDFs", "Combine multiple PDFs in order"),
    SPLIT_PDF("Split PDF", "Extract a range or single pages"),
    COMPRESS_PDF("Compress PDF", "Reduce file size offline"),
    REORDER_DELETE("Reorder / Delete Pages", "Arrange or remove PDF pages"),
    PROTECT_UNLOCK("Protect / Unlock PDF", "Add or remove PDF password"),
    PDF_TO_IMAGES("PDF to Images", "Export PDF pages as JPEGs"),
    IMPORT_RENAME("Import & Rename", "Bring PDFs or images into ScanNest"),
    ID_PHOTO_PACK("ID Photo Pack", "Generate printable passport/ID grid"),
    EXTRACT_OCR("Extract Text (OCR)", "Latin & Devanagari text extraction"),
    QR_SCANNER("QR Scanner", "Scan QR codes & barcodes offline")
}
