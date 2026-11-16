package com.example.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.domain.model.CropQuad
import com.example.domain.model.FilterType
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageProcessor {

    fun decodeSampledBitmap(path: String, maxDimension: Int = 2048): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(path, opts) ?: return null
        val exifOrientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        return applyExifOrientation(bitmap, exifOrientation)
    }

    fun decodeUriSampled(context: Context, uri: Uri, maxDimension: Int = 2200): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        val exifOrientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        return applyExifOrientation(bitmap, exifOrientation)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val maxSide = max(width, height)
        if (maxSide > maxDim) {
            while (maxSide / inSampleSize > maxDim * 1.4f) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed !== bitmap) bitmap.recycle()
        return transformed
    }

    /**
     * Performs 4-point perspective correction using Matrix.setPolyToPoly, followed by rotation/flip.
     */
    fun applyPerspectiveCrop(
        source: Bitmap,
        quad: CropQuad,
        rotationDegrees: Int = 0,
        flipHorizontal: Boolean = false
    ): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()

        val x0 = quad.topLeft.x * w
        val y0 = quad.topLeft.y * h
        val x1 = quad.topRight.x * w
        val y1 = quad.topRight.y * h
        val x2 = quad.bottomRight.x * w
        val y2 = quad.bottomRight.y * h
        val x3 = quad.bottomLeft.x * w
        val y3 = quad.bottomLeft.y * h

        val topWidth = hypot(x1 - x0, y1 - y0)
        val bottomWidth = hypot(x2 - x3, y2 - y3)
        val leftHeight = hypot(x3 - x0, y3 - y0)
        val rightHeight = hypot(x2 - x1, y2 - y1)

        val dstW = max(topWidth, bottomWidth).roundToInt().coerceIn(64, 3200)
        val dstH = max(leftHeight, rightHeight).roundToInt().coerceIn(64, 3200)

        val srcPts = floatArrayOf(x0, y0, x1, y1, x2, y2, x3, y3)
        val dstPts = floatArrayOf(
            0f, 0f,
            dstW.toFloat(), 0f,
            dstW.toFloat(), dstH.toFloat(),
            0f, dstH.toFloat()
        )

        val polyMatrix = Matrix()
        polyMatrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

        val warped = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(warped)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, polyMatrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        val normRot = ((rotationDegrees % 360) + 360) % 360
        if (normRot == 0 && !flipHorizontal) return warped

        val postMatrix = Matrix()
        if (flipHorizontal) postMatrix.postScale(-1f, 1f)
        if (normRot != 0) postMatrix.postRotate(normRot.toFloat())

        val finalBmp = Bitmap.createBitmap(warped, 0, 0, warped.width, warped.height, postMatrix, true)
        if (finalBmp !== warped) warped.recycle()
        return finalBmp
    }

    fun applyFilterAndAdjustments(
        source: Bitmap,
        filter: FilterType,
        brightness: Float = 0f,
        contrast: Float = 1f,
        sharpness: Float = 0f,
        removeShadows: Boolean = false,
        removeBlemishes: Boolean = false
    ): Bitmap {
        var working = source.copy(Bitmap.Config.ARGB_8888, true)

        if (removeShadows) {
            val flattened = normalizeIllumination(working)
            if (flattened !== working) working.recycle()
            working = flattened
        }

        // Apply base filter
        val filtered = when (filter) {
            FilterType.ORIGINAL -> working
            FilterType.GRAYSCALE -> applyColorMatrix(working, ColorMatrix().apply { setSaturation(0f) })
            FilterType.AUTO_ENHANCE -> {
                val cm = ColorMatrix().apply { setSaturation(1.12f) }
                val contrastMat = buildContrastBrightnessMatrix(1.22f, 8f)
                cm.postConcat(contrastMat)
                applyColorMatrix(working, cm)
            }
            FilterType.COLOR_BOOST -> {
                val cm = ColorMatrix().apply { setSaturation(1.45f) }
                cm.postConcat(buildContrastBrightnessMatrix(1.18f, 4f))
                applyColorMatrix(working, cm)
            }
            FilterType.CLEAN_DOC -> applyCleanDocFilter(working)
            FilterType.BLACK_WHITE -> applyAdaptiveBlackAndWhite(working)
        }
        if (filtered !== working) working.recycle()
        working = filtered

        // Apply manual brightness/contrast if changed from defaults
        if (brightness != 0f || contrast != 1f) {
            val adj = applyColorMatrix(working, buildContrastBrightnessMatrix(contrast, brightness))
            if (adj !== working) working.recycle()
            working = adj
        }

        if (removeBlemishes) {
            val cleaned = cleanBlemishes(working)
            if (cleaned !== working) working.recycle()
            working = cleaned
        }

        if (sharpness > 0.05f) {
            val sharpened = applyUnsharpMask(working, sharpness)
            if (sharpened !== working) working.recycle()
            working = sharpened
        }

        return working
    }

    private fun buildContrastBrightnessMatrix(contrast: Float, brightness: Float): ColorMatrix {
        val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
        return ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun applyColorMatrix(src: Bitmap, colorMatrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(colorMatrix) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /**
     * Lifts paper background toward pure white while keeping dark ink crisp and preserving colored stamps.
     */
    private fun applyCleanDocFilter(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (r * 30 + g * 59 + b * 11) / 100
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val chroma = maxC - minC

            if (lum > 155 && chroma < 38) {
                // Bright neutral paper -> push smoothly to pure white
                pixels[i] = Color.WHITE
            } else if (lum < 90 && chroma < 25) {
                // Dark neutral ink -> deepen
                val nr = (r * 0.72f).toInt().coerceIn(0, 255)
                val ng = (g * 0.72f).toInt().coerceIn(0, 255)
                val nb = (b * 0.72f).toInt().coerceIn(0, 255)
                pixels[i] = Color.rgb(nr, ng, nb)
            } else {
                // Midtone / colored stamp -> enhance contrast cleanly
                val nr = ((r - 128) * 1.35f + 135).toInt().coerceIn(0, 255)
                val ng = ((g - 128) * 1.35f + 135).toInt().coerceIn(0, 255)
                val nb = ((b - 128) * 1.35f + 135).toInt().coerceIn(0, 255)
                pixels[i] = Color.rgb(nr, ng, nb)
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Integral-image adaptive thresholding for crisp B&W text even under uneven lighting.
     */
    private fun applyAdaptiveBlackAndWhite(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val luma = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            luma[i] = (r * 30 + g * 59 + b * 11) / 100
        }

        // Prefix sum integral image
        val integral = LongArray((w + 1) * (h + 1))
        val iw = w + 1
        for (y in 1..h) {
            var rowSum = 0L
            for (x in 1..w) {
                rowSum += luma[(y - 1) * w + (x - 1)]
                integral[y * iw + x] = integral[(y - 1) * iw + x] + rowSum
            }
        }

        val radius = max(8, min(w, h) / 28)
        for (y in 0 until h) {
            val y1 = (y - radius).coerceAtLeast(0)
            val y2 = (y + radius).coerceAtMost(h - 1)
            for (x in 0 until w) {
                val x1 = (x - radius).coerceAtLeast(0)
                val x2 = (x + radius).coerceAtMost(w - 1)
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                val sum = integral[(y2 + 1) * iw + (x2 + 1)] -
                    integral[y1 * iw + (x2 + 1)] -
                    integral[(y2 + 1) * iw + x1] +
                    integral[y1 * iw + x1]
                val localMean = (sum / count).toInt()
                val v = luma[y * w + x]
                pixels[y * w + x] = if (v < localMean - 11 && v < 190) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Removes uneven lighting / hand shadows by dividing against a smooth background estimate.
     */
    private fun normalizeIllumination(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val bgW = (w / 16).coerceIn(8, 64)
        val bgH = (h / 16).coerceIn(8, 64)
        val tiny = Bitmap.createScaledBitmap(src, bgW, bgH, true)
        val bg = Bitmap.createScaledBitmap(tiny, w, h, true)
        tiny.recycle()

        val srcPixels = IntArray(w * h)
        val bgPixels = IntArray(w * h)
        src.getPixels(srcPixels, 0, w, 0, 0, w, h)
        bg.getPixels(bgPixels, 0, w, 0, 0, w, h)
        bg.recycle()

        for (i in srcPixels.indices) {
            val sc = srcPixels[i]
            val bc = bgPixels[i]
            val sr = (sc shr 16) and 0xFF
            val sg = (sc shr 8) and 0xFF
            val sb = sc and 0xFF
            val br = ((bc shr 16) and 0xFF).coerceAtLeast(32)
            val bgG = ((bc shr 8) and 0xFF).coerceAtLeast(32)
            val bb = (bc and 0xFF).coerceAtLeast(32)

            val nr = ((sr * 245) / br).coerceIn(0, 255)
            val ng = ((sg * 245) / bgG).coerceIn(0, 255)
            val nb = ((sb * 245) / bb).coerceIn(0, 255)
            srcPixels[i] = Color.rgb(nr, ng, nb)
        }
        return Bitmap.createBitmap(srcPixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun cleanBlemishes(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = pixels.copyOf()

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val c = pixels[y * w + x]
                val lum = (((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)) / 3
                if (lum in 140..230) {
                    var brightNeighbors = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            val nc = pixels[(y + dy) * w + (x + dx)]
                            val nLum = (((nc shr 16) and 0xFF) + ((nc shr 8) and 0xFF) + (nc and 0xFF)) / 3
                            if (nLum > 225) brightNeighbors++
                        }
                    }
                    if (brightNeighbors >= 6) {
                        out[y * w + x] = Color.WHITE
                    }
                }
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun applyUnsharpMask(src: Bitmap, amount: Float): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = pixels.copyOf()
        val k = amount.coerceIn(0f, 1.2f) * 0.8f

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val c = pixels[y * w + x]
                val top = pixels[(y - 1) * w + x]
                val bot = pixels[(y + 1) * w + x]
                val lft = pixels[y * w + (x - 1)]
                val rgt = pixels[y * w + (x + 1)]

                val r = sharpenChannel((c shr 16) and 0xFF, (top shr 16) and 0xFF, (bot shr 16) and 0xFF, (lft shr 16) and 0xFF, (rgt shr 16) and 0xFF, k)
                val g = sharpenChannel((c shr 8) and 0xFF, (top shr 8) and 0xFF, (bot shr 8) and 0xFF, (lft shr 8) and 0xFF, (rgt shr 8) and 0xFF, k)
                val b = sharpenChannel(c and 0xFF, top and 0xFF, bot and 0xFF, lft and 0xFF, rgt and 0xFF, k)
                out[y * w + x] = Color.rgb(r, g, b)
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun sharpenChannel(c: Int, t: Int, b: Int, l: Int, r: Int, k: Float): Int {
        val avg = (t + b + l + r) / 4f
        return (c + (c - avg) * k).toInt().coerceIn(0, 255)
    }

    /**
     * Combines front & back ID card captures neatly on a single A4-proportioned page.
     */
    fun combineIdCardFrontBack(front: Bitmap, back: Bitmap): Bitmap {
        val pageW = 1240
        val pageH = 1754
        val result = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val cardW = pageW * 0.68f
        val cardH = cardW / 1.586f // Standard ISO ID-1 ratio

        val frontLeft = (pageW - cardW) / 2f
        val frontTop = pageH * 0.14f
        val backTop = frontTop + cardH + pageH * 0.09f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.LTGRAY
            strokeWidth = 3f
        }

        val frontRect = RectF(frontLeft, frontTop, frontLeft + cardW, frontTop + cardH)
        canvas.drawBitmap(front, null, frontRect, paint)
        canvas.drawRoundRect(frontRect, 18f, 18f, borderPaint)

        val backRect = RectF(frontLeft, backTop, frontLeft + cardW, backTop + cardH)
        canvas.drawBitmap(back, null, backRect, paint)
        canvas.drawRoundRect(backRect, 18f, 18f, borderPaint)

        return result
    }

    /**
     * Generates a printable 2x4 passport/ID photo sheet on A4 canvas with cut guides.
     */
    fun createIdPhotoPack(photo: Bitmap, cols: Int = 2, rows: Int = 4): Bitmap {
        val pageW = 1240
        val pageH = 1754
        val result = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val marginX = 110f
        val marginY = 100f
        val gap = 42f
        val cellW = (pageW - 2 * marginX - (cols - 1) * gap) / cols
        val cellH = (pageH - 2 * marginY - (rows - 1) * gap) / rows

        val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val guidePaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val left = marginX + c * (cellW + gap)
                val top = marginY + r * (cellH + gap)
                val rect = RectF(left, top, left + cellW, top + cellH)
                canvas.drawBitmap(photo, null, rect, photoPaint)
                canvas.drawRect(rect, guidePaint)
            }
        }
        return result
    }

    fun applyWatermark(
        source: Bitmap,
        text: String,
        alpha: Int = 55,
        scaleFactor: Float = 1f,
        colorInt: Int = Color.rgb(15, 118, 110)
    ): Bitmap {
        if (text.isBlank()) return source
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(
                alpha.coerceIn(15, 180),
                Color.red(colorInt),
                Color.green(colorInt),
                Color.blue(colorInt)
            )
            textSize = ((min(out.width, out.height) / 11f) * scaleFactor.coerceIn(0.6f, 1.8f)).coerceAtLeast(24f)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.save()
        canvas.rotate(-32f, out.width / 2f, out.height / 2f)
        val stepY = paint.textSize * 3.1f
        var y = -out.height * 0.25f
        while (y < out.height * 1.25f) {
            canvas.drawText(text.uppercase(), out.width / 2f, y, paint)
            y += stepY
        }
        canvas.restore()
        return out
    }

    fun saveBitmapJpeg(bitmap: Bitmap, file: File, quality: Int = 92) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(30, 100), out)
        }
    }

    fun saveBitmapPng(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
