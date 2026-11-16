package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.DeepTeal
import com.example.ui.theme.TealContainerLight

/**
 * Original ScanNest brand mark: stylized document with folded corner + curved amber nest swoosh.
 */
@Composable
fun ScanNestLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Deep teal rounded badge
        drawRoundRect(
            color = DeepTeal,
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = CornerRadius(w * 0.26f, w * 0.26f)
        )

        // Document silhouette with folded corner
        val docLeft = w * 0.26f
        val docTop = h * 0.16f
        val docRight = w * 0.74f
        val docBottom = h * 0.70f
        val fold = w * 0.14f

        val docPath = Path().apply {
            moveTo(docLeft, docTop)
            lineTo(docRight - fold, docTop)
            lineTo(docRight, docTop + fold)
            lineTo(docRight, docBottom)
            lineTo(docLeft, docBottom)
            close()
        }
        drawPath(docPath, color = Color.White)

        // Folded corner cutout accent
        val foldPath = Path().apply {
            moveTo(docRight - fold, docTop)
            lineTo(docRight - fold, docTop + fold)
            lineTo(docRight, docTop + fold)
            close()
        }
        drawPath(foldPath, color = TealContainerLight)

        // Amber nest swoosh cradling bottom of document
        val swoosh = Path().apply {
            moveTo(w * 0.17f, h * 0.63f)
            cubicTo(
                w * 0.28f, h * 0.87f,
                w * 0.72f, h * 0.87f,
                w * 0.83f, h * 0.63f
            )
        }
        drawPath(
            path = swoosh,
            color = AmberAccent,
            style = Stroke(width = w * 0.10f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun OnboardingIllustration(
    pageIndex: Int,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val infinite = rememberInfiniteTransition(label = "onboarding_anim")
    val scanLineProgress by infinite.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.76f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_line"
    )

    Canvas(modifier = modifier.size(240.dp)) {
        val w = size.width
        val h = size.height

        // Soft circular backdrop
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryContainer.copy(alpha = 0.7f), primaryContainer.copy(alpha = 0.12f))
            ),
            radius = w * 0.45f,
            center = Offset(w / 2f, h / 2f)
        )

        when (pageIndex) {
            0 -> {
                // "Scan anything": Document card + amber corner brackets + animated teal scan-line
                val left = w * 0.27f
                val top = h * 0.18f
                val docW = w * 0.46f
                val docH = h * 0.62f

                drawRoundRect(
                    color = surface,
                    topLeft = Offset(left, top),
                    size = Size(docW, docH),
                    cornerRadius = CornerRadius(16f, 16f)
                )
                drawRoundRect(
                    color = primary.copy(alpha = 0.25f),
                    topLeft = Offset(left, top),
                    size = Size(docW, docH),
                    cornerRadius = CornerRadius(16f, 16f),
                    style = Stroke(width = 3f)
                )

                // Document lines
                for (i in 0..4) {
                    val ly = top + docH * (0.24f + i * 0.13f)
                    val lineW = if (i == 4) docW * 0.45f else docW * 0.68f
                    drawLine(
                        color = onSurface.copy(alpha = if (i == 0) 0.45f else 0.18f),
                        start = Offset(left + docW * 0.16f, ly),
                        end = Offset(left + docW * 0.16f + lineW, ly),
                        strokeWidth = if (i == 0) 10f else 7f,
                        cap = StrokeCap.Round
                    )
                }

                // Animated teal scan-line
                val sy = h * scanLineProgress
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, primary, Color.Transparent)
                    ),
                    start = Offset(left - 18f, sy),
                    end = Offset(left + docW + 18f, sy),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )

                // Amber viewfinder brackets
                val bPad = 14f
                val bLen = 28f
                val bStroke = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val bLeft = left - bPad
                val bTop = top - bPad
                val bRight = left + docW + bPad
                val bBottom = top + docH + bPad

                drawPath(Path().apply {
                    moveTo(bLeft, bTop + bLen); lineTo(bLeft, bTop); lineTo(bLeft + bLen, bTop)
                }, AmberAccent, style = bStroke)
                drawPath(Path().apply {
                    moveTo(bRight - bLen, bTop); lineTo(bRight, bTop); lineTo(bRight, bTop + bLen)
                }, AmberAccent, style = bStroke)
                drawPath(Path().apply {
                    moveTo(bLeft, bBottom - bLen); lineTo(bLeft, bBottom); lineTo(bLeft + bLen, bBottom)
                }, AmberAccent, style = bStroke)
                drawPath(Path().apply {
                    moveTo(bRight - bLen, bBottom); lineTo(bRight, bBottom); lineTo(bRight, bBottom - bLen)
                }, AmberAccent, style = bStroke)
            }

            1 -> {
                // "Edit & sign": Crisp sheet + filter chips + smooth Bezier signature
                val left = w * 0.24f
                val top = h * 0.20f
                val docW = w * 0.52f
                val docH = h * 0.56f

                drawRoundRect(
                    color = surface,
                    topLeft = Offset(left, top),
                    size = Size(docW, docH),
                    cornerRadius = CornerRadius(18f, 18f)
                )
                drawRoundRect(
                    color = primary.copy(alpha = 0.25f),
                    topLeft = Offset(left, top),
                    size = Size(docW, docH),
                    cornerRadius = CornerRadius(18f, 18f),
                    style = Stroke(width = 3f)
                )

                // Filter swatch dots at top
                val swatchColors = listOf(primary, AmberAccent, Color(0xFF64748B), Color(0xFF0F172A))
                swatchColors.forEachIndexed { idx, col ->
                    drawCircle(
                        color = col,
                        radius = 12f,
                        center = Offset(left + 32f + idx * 34f, top + 34f)
                    )
                }

                // Signature Bezier wave
                val sigPath = Path().apply {
                    moveTo(left + 26f, top + docH * 0.72f)
                    cubicTo(
                        left + docW * 0.30f, top + docH * 0.48f,
                        left + docW * 0.48f, top + docH * 0.85f,
                        left + docW * 0.65f, top + docH * 0.56f
                    )
                    cubicTo(
                        left + docW * 0.75f, top + docH * 0.42f,
                        left + docW * 0.80f, top + docH * 0.76f,
                        left + docW * 0.88f, top + docH * 0.68f
                    )
                }
                drawPath(
                    sigPath,
                    color = primary,
                    style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Amber check badge
                drawCircle(color = AmberAccent, radius = 28f, center = Offset(left + docW, top + docH * 0.84f))
                drawPath(
                    Path().apply {
                        moveTo(left + docW - 11f, top + docH * 0.84f)
                        lineTo(left + docW - 3f, top + docH * 0.84f + 9f)
                        lineTo(left + docW + 12f, top + docH * 0.84f - 9f)
                    },
                    color = Color.White,
                    style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            else -> {
                // "Share instantly": Layered PDF / IMG / TXT cards fanning cleanly
                // Back card (amber)
                drawRoundRect(
                    color = AmberAccent.copy(alpha = 0.85f),
                    topLeft = Offset(w * 0.36f, h * 0.20f),
                    size = Size(w * 0.38f, h * 0.48f),
                    cornerRadius = CornerRadius(18f, 18f)
                )
                // Middle card (teal)
                drawRoundRect(
                    color = primary,
                    topLeft = Offset(w * 0.28f, h * 0.25f),
                    size = Size(w * 0.40f, h * 0.50f),
                    cornerRadius = CornerRadius(18f, 18f)
                )
                // Front crisp document card
                drawRoundRect(
                    color = surface,
                    topLeft = Offset(w * 0.20f, h * 0.31f),
                    size = Size(w * 0.42f, h * 0.50f),
                    cornerRadius = CornerRadius(18f, 18f)
                )
                drawRoundRect(
                    color = primary.copy(alpha = 0.25f),
                    topLeft = Offset(w * 0.20f, h * 0.31f),
                    size = Size(w * 0.42f, h * 0.50f),
                    cornerRadius = CornerRadius(18f, 18f),
                    style = Stroke(width = 3f)
                )
                // Upward share arrow motif
                val cx = w * 0.41f
                val cy = h * 0.56f
                drawLine(primary, Offset(cx, cy + 28f), Offset(cx, cy - 28f), strokeWidth = 8f, cap = StrokeCap.Round)
                drawPath(
                    Path().apply {
                        moveTo(cx - 20f, cy - 10f)
                        lineTo(cx, cy - 30f)
                        lineTo(cx + 20f, cy - 10f)
                    },
                    color = primary,
                    style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

@Composable
fun EmptyNestIllustration(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val surface = MaterialTheme.colorScheme.surface

    Canvas(modifier = modifier.size(180.dp)) {
        val w = size.width
        val h = size.height

        drawCircle(
            color = primaryContainer.copy(alpha = 0.5f),
            radius = w * 0.42f,
            center = Offset(w / 2f, h / 2f)
        )

        val left = w * 0.30f
        val top = h * 0.22f
        val docW = w * 0.40f
        val docH = h * 0.48f

        drawRoundRect(
            color = surface,
            topLeft = Offset(left, top),
            size = Size(docW, docH),
            cornerRadius = CornerRadius(16f, 16f)
        )
        drawRoundRect(
            color = primary.copy(alpha = 0.3f),
            topLeft = Offset(left, top),
            size = Size(docW, docH),
            cornerRadius = CornerRadius(16f, 16f),
            style = Stroke(width = 3.5f)
        )

        // Nest swoosh at base
        val nestPath = Path().apply {
            moveTo(w * 0.22f, h * 0.68f)
            cubicTo(w * 0.34f, h * 0.86f, w * 0.66f, h * 0.86f, w * 0.78f, h * 0.68f)
        }
        drawPath(
            nestPath,
            color = AmberAccent,
            style = Stroke(width = 11f, cap = StrokeCap.Round)
        )

        // Small camera lens dot in center of doc
        drawCircle(color = primary, radius = 16f, center = Offset(w * 0.50f, h * 0.45f))
        drawCircle(color = Color.White, radius = 7f, center = Offset(w * 0.50f, h * 0.45f))
    }
}

@Composable
fun SkeletonShimmerBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp)
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}
