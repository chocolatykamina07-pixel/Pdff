package com.example.ocr

import android.graphics.Bitmap
import com.example.domain.model.OcrLanguage
import com.example.domain.model.OcrPageResult
import com.example.domain.model.TextBlockItem
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ScannedBarcodeResult(
    val rawValue: String,
    val displayValue: String,
    val isUrl: Boolean,
    val formatLabel: String
)

object OcrEngine {

    suspend fun recognizeBitmap(
        bitmap: Bitmap,
        pageIndex: Int,
        language: OcrLanguage = OcrLanguage.LATIN
    ): OcrPageResult = suspendCancellableCoroutine { cont ->
        val recognizer = when (language) {
            OcrLanguage.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            OcrLanguage.DEVANAGARI -> TextRecognition.getClient(
                DevanagariTextRecognizerOptions.Builder().build()
            )
        }

        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val blocks = result.textBlocks.mapNotNull { block ->
                    val rect = block.boundingBox ?: return@mapNotNull null
                    TextBlockItem(
                        text = block.text,
                        left = rect.left,
                        top = rect.top,
                        right = rect.right,
                        bottom = rect.bottom
                    )
                }
                recognizer.close()
                if (cont.isActive) {
                    cont.resume(
                        OcrPageResult(
                            pageIndex = pageIndex,
                            imageWidth = bitmap.width,
                            imageHeight = bitmap.height,
                            fullText = result.text,
                            blocks = blocks
                        )
                    )
                }
            }
            .addOnFailureListener { err ->
                recognizer.close()
                if (cont.isActive) cont.resumeWithException(err)
            }
    }

    suspend fun scanBarcodes(bitmap: Bitmap): List<ScannedBarcodeResult> =
        suspendCancellableCoroutine { cont ->
            val scanner = BarcodeScanning.getClient()
            val image = InputImage.fromBitmap(bitmap, 0)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val mapped = barcodes.mapNotNull { bc ->
                        val raw = bc.rawValue ?: return@mapNotNull null
                        ScannedBarcodeResult(
                            rawValue = raw,
                            displayValue = bc.displayValue ?: raw,
                            isUrl = bc.valueType == Barcode.TYPE_URL ||
                                raw.startsWith("http://", ignoreCase = true) ||
                                raw.startsWith("https://", ignoreCase = true),
                            formatLabel = formatBarcodeType(bc.format)
                        )
                    }
                    scanner.close()
                    if (cont.isActive) cont.resume(mapped)
                }
                .addOnFailureListener { err ->
                    scanner.close()
                    if (cont.isActive) cont.resumeWithException(err)
                }
        }

    fun formatBarcodeType(format: Int): String = when (format) {
        Barcode.FORMAT_QR_CODE -> "QR Code"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_CODE_128 -> "Code 128"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "Aztec"
        else -> "Barcode"
    }
}
