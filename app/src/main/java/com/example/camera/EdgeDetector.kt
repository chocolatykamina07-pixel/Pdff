package com.example.camera

import android.graphics.Bitmap
import com.example.domain.model.CropQuad
import com.example.domain.model.NormalizedPoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object EdgeDetector {

    /**
     * Detects document boundary quadrilateral on a static bitmap for "Auto-detect" in Crop screen.
     */
    fun detectEdges(bitmap: Bitmap): CropQuad {
        val targetMax = 180
        val scale = targetMax.toFloat() / max(bitmap.width, bitmap.height).coerceAtLeast(1)
        val w = (bitmap.width * scale).toInt().coerceAtLeast(24)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(24)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== bitmap) scaled.recycle()

        val luma = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            luma[i] = (r * 30 + g * 59 + b * 11) / 100
        }
        return findQuadFromLuma(luma, w, h) ?: CropQuad()
    }

    /**
     * Fast real-time edge detection from CameraX YUV_420_888 Y-plane buffer.
     */
    fun detectEdgesFromLuma(
        yBytes: ByteArray,
        srcWidth: Int,
        srcHeight: Int,
        rowStride: Int,
        rotationDegrees: Int
    ): CropQuad? {
        if (srcWidth <= 0 || srcHeight <= 0) return null
        val step = (max(srcWidth, srcHeight) / 120).coerceAtLeast(2)
        val rawW = srcWidth / step
        val rawH = srcHeight / step
        if (rawW < 16 || rawH < 16) return null

        val rotNorm = ((rotationDegrees % 360) + 360) % 360
        val outW = if (rotNorm == 90 || rotNorm == 270) rawH else rawW
        val outH = if (rotNorm == 90 || rotNorm == 270) rawW else rawH
        val luma = IntArray(outW * outH)

        for (ry in 0 until rawH) {
            val sy = ry * step
            val rowOffset = sy * rowStride
            for (rx in 0 until rawW) {
                val sx = rx * step
                val idx = rowOffset + sx
                val v = if (idx < yBytes.size) (yBytes[idx].toInt() and 0xFF) else 0
                val (ox, oy) = when (rotNorm) {
                    90 -> (rawH - 1 - ry) to rx
                    180 -> (rawW - 1 - rx) to (rawH - 1 - ry)
                    270 -> ry to (rawW - 1 - rx)
                    else -> rx to ry
                }
                if (ox in 0 until outW && oy in 0 until outH) {
                    luma[oy * outW + ox] = v
                }
            }
        }
        return findQuadFromLuma(luma, outW, outH)
    }

    private fun findQuadFromLuma(luma: IntArray, w: Int, h: Int): CropQuad? {
        val grad = IntArray(w * h)
        var maxGrad = 0
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val gx = abs(luma[row + x + 1] - luma[row + x - 1])
                val gy = abs(luma[(y + 1) * w + x] - luma[(y - 1) * w + x])
                val g = gx + gy
                grad[row + x] = g
                if (g > maxGrad) maxGrad = g
            }
        }
        if (maxGrad < 24) return null

        val threshold = (maxGrad * 0.28f).toInt().coerceAtLeast(18)

        // Find dominant horizontal and vertical boundary strips via projection
        val rowEnergy = IntArray(h)
        val colEnergy = IntArray(w)
        for (y in 2 until h - 2) {
            var sum = 0
            for (x in 2 until w - 2) {
                val g = grad[y * w + x]
                if (g >= threshold) {
                    sum += g
                    colEnergy[x] += g
                }
            }
            rowEnergy[y] = sum
        }

        val topY = findStrongestBand(rowEnergy, 2, (h * 0.42f).toInt())
        val bottomY = findStrongestBand(rowEnergy, (h * 0.58f).toInt(), h - 3)
        val leftX = findStrongestBand(colEnergy, 2, (w * 0.42f).toInt())
        val rightX = findStrongestBand(colEnergy, (w * 0.58f).toInt(), w - 3)

        if (rightX - leftX < w * 0.30f || bottomY - topY < h * 0.30f) {
            return null
        }

        // Refine corners locally around intersections
        val tl = refineCorner(grad, w, h, leftX, topY, threshold)
        val tr = refineCorner(grad, w, h, rightX, topY, threshold)
        val br = refineCorner(grad, w, h, rightX, bottomY, threshold)
        val bl = refineCorner(grad, w, h, leftX, bottomY, threshold)

        return CropQuad(
            topLeft = NormalizedPoint((tl.first.toFloat() / w).coerceIn(0.02f, 0.48f), (tl.second.toFloat() / h).coerceIn(0.02f, 0.48f)),
            topRight = NormalizedPoint((tr.first.toFloat() / w).coerceIn(0.52f, 0.98f), (tr.second.toFloat() / h).coerceIn(0.02f, 0.48f)),
            bottomRight = NormalizedPoint((br.first.toFloat() / w).coerceIn(0.52f, 0.98f), (br.second.toFloat() / h).coerceIn(0.52f, 0.98f)),
            bottomLeft = NormalizedPoint((bl.first.toFloat() / w).coerceIn(0.02f, 0.48f), (bl.second.toFloat() / h).coerceIn(0.52f, 0.98f))
        )
    }

    private fun findStrongestBand(energy: IntArray, start: Int, end: Int): Int {
        var bestIdx = (start + end) / 2
        var bestVal = -1
        for (i in start..end) {
            if (energy[i] > bestVal) {
                bestVal = energy[i]
                bestIdx = i
            }
        }
        return bestIdx
    }

    private fun refineCorner(
        grad: IntArray,
        w: Int,
        h: Int,
        cx: Int,
        cy: Int,
        threshold: Int
    ): Pair<Int, Int> {
        val radius = max(4, min(w, h) / 14)
        var sumX = 0L
        var sumY = 0L
        var weightSum = 0L
        for (y in (cy - radius).coerceAtLeast(1)..(cy + radius).coerceAtMost(h - 2)) {
            for (x in (cx - radius).coerceAtLeast(1)..(cx + radius).coerceAtMost(w - 2)) {
                val g = grad[y * w + x]
                if (g >= threshold) {
                    sumX += x.toLong() * g
                    sumY += y.toLong() * g
                    weightSum += g
                }
            }
        }
        return if (weightSum > 0L) {
            (sumX / weightSum).toInt() to (sumY / weightSum).toInt()
        } else {
            cx to cy
        }
    }
}

class QuadStabilityTracker {
    private var lastQuad: CropQuad? = null
    private var stableSinceMs: Long = 0L

    fun update(quad: CropQuad?, nowMs: Long = System.currentTimeMillis()): Float {
        if (quad == null) {
            lastQuad = null
            stableSinceMs = 0L
            return 0f
        }
        val prev = lastQuad
        if (prev == null) {
            lastQuad = quad
            stableSinceMs = nowMs
            return 0f
        }
        val delta = maxCornerDelta(prev, quad)
        return if (delta < 0.035f) {
            lastQuad = lerpQuad(prev, quad, 0.35f)
            ((nowMs - stableSinceMs) / 1000f).coerceIn(0f, 1f)
        } else {
            lastQuad = quad
            stableSinceMs = nowMs
            0f
        }
    }

    fun reset() {
        lastQuad = null
        stableSinceMs = 0L
    }

    private fun maxCornerDelta(a: CropQuad, b: CropQuad): Float {
        val ptsA = a.asList()
        val ptsB = b.asList()
        var maxD = 0f
        for (i in 0..3) {
            val dx = ptsA[i].x - ptsB[i].x
            val dy = ptsA[i].y - ptsB[i].y
            maxD = max(maxD, sqrt(dx * dx + dy * dy))
        }
        return maxD
    }

    private fun lerpQuad(a: CropQuad, b: CropQuad, t: Float): CropQuad {
        fun lerp(p1: NormalizedPoint, p2: NormalizedPoint) = NormalizedPoint(
            x = p1.x + (p2.x - p1.x) * t,
            y = p1.y + (p2.y - p1.y) * t
        )
        return CropQuad(
            topLeft = lerp(a.topLeft, b.topLeft),
            topRight = lerp(a.topRight, b.topRight),
            bottomRight = lerp(a.bottomRight, b.bottomRight),
            bottomLeft = lerp(a.bottomLeft, b.bottomLeft)
        )
    }
}
