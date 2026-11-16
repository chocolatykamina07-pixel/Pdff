package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.camera.EdgeDetector
import com.example.domain.model.CropQuad
import com.example.domain.model.ExportFormat
import com.example.domain.model.ExportRequest
import com.example.domain.model.FilterType
import com.example.pdf.PdfEngine
import com.example.processing.ImageProcessor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name resolves to ScanNest`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ScanNest", appName)
    }

    @Test
    fun `edge detector and perspective crop produce valid output bitmap`() {
        val bmp = Bitmap.createBitmap(200, 280, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.DKGRAY)
        for (y in 40 until 240) {
            for (x in 30 until 170) {
                bmp.setPixel(x, y, Color.WHITE)
            }
        }
        val quad = EdgeDetector.detectEdges(bmp)
        assertTrue(quad.topRight.x > quad.topLeft.x)
        assertTrue(quad.bottomLeft.y > quad.topLeft.y)

        val cropped = ImageProcessor.applyPerspectiveCrop(bmp, CropQuad())
        assertTrue(cropped.width > 0 && cropped.height > 0)

        val bw = ImageProcessor.applyFilterAndAdjustments(cropped, FilterType.BLACK_WHITE)
        assertEquals(cropped.width, bw.width)
        bmp.recycle()
        cropped.recycle()
        bw.recycle()
    }

    @Test
    fun `docx and txt export generate valid non-empty files`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val txtFile = File(context.cacheDir, "test_out.txt")
        val docxFile = File(context.cacheDir, "test_out.docx")

        PdfEngine.exportTxt("Sample Invoice\nTotal: $42.00", txtFile)
        assertTrue(txtFile.exists() && txtFile.readText().contains("Sample Invoice"))

        PdfEngine.exportDocx("Sample Invoice", "Line 1\nLine 2", docxFile)
        assertTrue(docxFile.exists() && docxFile.length() > 100L)

        val est = PdfEngine.estimateExportSizeBytes(
            pages = emptyList(),
            request = ExportRequest(1L, ExportFormat.PDF),
            ocrText = null
        )
        assertEquals(0L, est)
    }
}
