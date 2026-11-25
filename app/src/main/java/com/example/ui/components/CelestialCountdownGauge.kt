package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.IceWhite
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyElevated
import com.example.ui.theme.SilverText
import com.example.ui.theme.WarmHighlight
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

@Composable
fun MinaretCountdownGauge(
    nextPrayerName: String?,
    countdownText: String,
    progressFraction: Float,
    modifier: Modifier = Modifier
) {
    val minaretBitmap = ImageBitmap.imageResource(id = R.drawable.minaret_illustration)

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 650),
        label = "minaret_fill_progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "minaret_shimmer_transition")
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_phase"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Muted engraved bronze-slate matrix matching the embossed calligraphy background
    val engravedColorFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.18f, 0.34f, 0.08f, 0f, 10f,
                    0.16f, 0.30f, 0.07f, 0f, 8f,
                    0.14f, 0.25f, 0.07f, 0f, 8f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    // Vivid glowing crimson matrix preserving architectural outlines and tile contrast
    val crimsonColorFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.42f, 0.68f, 0.20f, 0f, 22f,
                    0.06f, 0.11f, 0.03f, 0f, 2f,
                    0.07f, 0.12f, 0.04f, 0f, 4f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .testTag("minaret_countdown_gauge"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // User's minaret engraved into the dark background with rising luminous red progress
        Box(
            modifier = Modifier.size(width = 76.dp, height = 224.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawEngravedMinaret(
                    bitmap = minaretBitmap,
                    progress = animatedProgress,
                    shimmerPhase = shimmerPhase,
                    pulseAlpha = pulseAlpha,
                    engravedColorFilter = engravedColorFilter,
                    crimsonColorFilter = crimsonColorFilter
                )
            }
        }

        Spacer(modifier = Modifier.width(18.dp))

        // Prayer name, label, countdown timer, and sky phase beside the minaret
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.next_prayer_label),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = SilverText
            )

            if (nextPrayerName != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = nextPrayerName,
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = CrimsonBright
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = countdownText,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                        fontSize = 31.sp,
                        letterSpacing = 1.4.sp
                    ),
                    color = IceWhite
                )
            }
        }
    }
}

private fun DrawScope.drawEngravedMinaret(
    bitmap: ImageBitmap,
    progress: Float,
    shimmerPhase: Float,
    pulseAlpha: Float,
    engravedColorFilter: ColorFilter,
    crimsonColorFilter: ColorFilter
) {
    val imgAspect = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
    val canvasAspect = size.width / size.height.coerceAtLeast(1f)
    val dstW: Float
    val dstH: Float
    if (canvasAspect > imgAspect) {
        dstH = size.height
        dstW = dstH * imgAspect
    } else {
        dstW = size.width
        dstH = dstW / imgAspect
    }
    val dstLeft = (size.width - dstW) / 2f
    val dstTop = (size.height - dstH) / 2f
    val dstOffset = IntOffset(dstLeft.roundToInt(), dstTop.roundToInt())
    val dstSize = IntSize(dstW.roundToInt().coerceAtLeast(1), dstH.roundToInt().coerceAtLeast(1))

    val bottomY = dstTop + dstH
    val crescentReachY = dstTop + dstH * 0.015f
    val clampedProgress = progress.coerceIn(0f, 1f)
    val fillTopY = bottomY - (bottomY - crescentReachY) * clampedProgress

    // Subtle soft ambient crimson aura behind the rising minaret edge
    if (clampedProgress > 0.02f) {
        val glowCenterY = fillTopY.coerceIn(dstTop + dstH * 0.06f, bottomY)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    CrimsonRed.copy(alpha = 0.24f * pulseAlpha),
                    Color.Transparent
                ),
                center = Offset(size.width / 2f, glowCenterY),
                radius = dstW * 0.88f
            ),
            radius = dstW * 0.88f,
            center = Offset(size.width / 2f, glowCenterY)
        )
    }

    // 1. Base faded engraved minaret (above the red level, carved seamlessly into the dark background)
    drawImage(
        image = bitmap,
        dstOffset = dstOffset,
        dstSize = dstSize,
        alpha = 0.44f,
        colorFilter = engravedColorFilter
    )

    // 2. Rising crimson red layer with gentle moving upward light shimmer
    if (clampedProgress > 0.005f) {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            clipRect(left = 0f, top = fillTopY, right = size.width, bottom = size.height) {
                drawImage(
                    image = bitmap,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    alpha = 0.96f,
                    colorFilter = crimsonColorFilter
                )

                // Soft depth gradient inside the red portion
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            CrimsonBright.copy(alpha = 0.22f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.24f)
                        ),
                        startY = fillTopY,
                        endY = bottomY
                    ),
                    blendMode = BlendMode.SrcAtop
                )

                // Gentle moving light wave rising upward toward the top edge
                val filledSpan = (bottomY - fillTopY).coerceAtLeast(24.dp.toPx())
                val bandHeight = (filledSpan * 0.42f).coerceIn(22.dp.toPx(), 64.dp.toPx())
                val travelStart = bottomY + bandHeight * 0.5f
                val travelEnd = fillTopY - bandHeight * 0.5f
                val shimmerCenterY = travelStart + (travelEnd - travelStart) * shimmerPhase

                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFFFB4B4).copy(alpha = 0.26f),
                            Color.White.copy(alpha = 0.42f),
                            Color(0xFFFFB4B4).copy(alpha = 0.26f),
                            Color.Transparent
                        ),
                        startY = shimmerCenterY - bandHeight * 0.5f,
                        endY = shimmerCenterY + bandHeight * 0.5f
                    ),
                    blendMode = BlendMode.SrcAtop
                )

                // Soft glowing top frontier line where the rising red meets the faded stone
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFE4E6).copy(alpha = 0.72f * pulseAlpha),
                            CrimsonBright.copy(alpha = 0.38f * pulseAlpha),
                            Color.Transparent
                        ),
                        startY = fillTopY,
                        endY = fillTopY + 14.dp.toPx()
                    ),
                    blendMode = BlendMode.SrcAtop
                )
            }
            canvas.restore()
        }
    }
}

/**
 * Displays a photorealistic Moon in the top-left of the header at a clear medium size,
 * dynamically shaded to match the current Hijri lunar day (1..30) with its phase name underneath.
 */
@Composable
fun RealisticHijriMoonIndicator(
    hijriDay: Int,
    modifier: Modifier = Modifier
) {
    val moonBitmap = ImageBitmap.imageResource(id = R.drawable.img_realistic_moon)
    val day = hijriDay.coerceIn(1, 30)
    val isFullMoon = day in 13..16
    val phaseLabel = stringResource(moonPhaseLabelRes(day))

    // Bright silvery-warm moonlight lift on sunlit craters
    val sunlitMoonFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    1.10f, 0f, 0f, 0f, 10f,
                    0f, 1.09f, 0f, 0f, 10f,
                    0f, 0f, 1.05f, 0f, 8f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "moon_aura_pulse")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "moon_glow_pulse"
    )

    Column(
        modifier = modifier.testTag("top_left_realistic_moon"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(86.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val moonRadius = 36.dp.toPx()
                val center = Offset(size.width / 2f, size.height / 2f)

                val closenessToFull = when {
                    isFullMoon -> 1f
                    day < 13 -> ((day - 1) / 12f).coerceIn(0f, 1f)
                    else -> ((30 - day) / 13f).coerceIn(0f, 1f)
                }

                val topLeft = IntOffset(
                    (center.x - moonRadius).roundToInt(),
                    (center.y - moonRadius).roundToInt()
                )
                val sidePx = (moonRadius * 2f).roundToInt().coerceAtLeast(1)
                val dstSize = IntSize(sidePx, sidePx)

                if (isFullMoon) {
                    // Full circular moonlight halo + glowing realistic full Moon ("بدر كامل")
                    val haloAlpha = 0.34f * glowPulse
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFDF8E7).copy(alpha = haloAlpha),
                                Color(0xFFD6C7A3).copy(alpha = haloAlpha * 0.42f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = moonRadius * 1.38f
                        ),
                        radius = moonRadius * 1.38f,
                        center = center
                    )

                    drawImage(
                        image = moonBitmap,
                        dstOffset = topLeft,
                        dstSize = dstSize,
                        colorFilter = sunlitMoonFilter
                    )
                } else {
                    // Lit-side soft glow anchored to the illuminated hemisphere so dark side stays transparent sky
                    val isWaxing = day <= 15
                    val glowOffsetX = if (isWaxing) moonRadius * 0.35f else -moonRadius * 0.35f
                    val glowCenter = Offset(center.x + glowOffsetX, center.y)
                    val haloAlpha = (0.14f + 0.18f * closenessToFull) * glowPulse
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFDF8E7).copy(alpha = haloAlpha),
                                Color.Transparent
                            ),
                            center = glowCenter,
                            radius = moonRadius * 1.15f
                        ),
                        radius = moonRadius * 1.15f,
                        center = glowCenter
                    )

                    // Clip strictly to the Hijri lunar phase (crescent / quarter / gibbous)
                    // with zero full-circle earthshine underneath so crescents clearly look like a true Hilal
                    val litPath = buildLunarPhasePath(center = center, radius = moonRadius, hijriDay = day)
                    val tiltDeg = if (isWaxing) 18f else -18f

                    rotate(degrees = tiltDeg, pivot = center) {
                        clipPath(litPath) {
                            // Keep the real lunar surface upright inside the clipped crescent/phase shape
                            rotate(degrees = -tiltDeg, pivot = center) {
                                drawImage(
                                    image = moonBitmap,
                                    dstOffset = topLeft,
                                    dstSize = dstSize,
                                    colorFilter = sunlitMoonFilter
                                )
                            }

                            // Subtle 3D spherical terminator shading near the shadow edge
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = if (isWaxing) {
                                        listOf(
                                            Color.Black.copy(alpha = 0.28f),
                                            Color.Transparent,
                                            Color.Transparent
                                        )
                                    } else {
                                        listOf(
                                            Color.Transparent,
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.28f)
                                        )
                                    },
                                    startX = center.x - moonRadius,
                                    endX = center.x + moonRadius
                                )
                            )
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = NavyElevated.copy(alpha = 0.78f),
            border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.7f))
        ) {
            Text(
                text = "$phaseLabel • $day هـ",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = WarmHighlight,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

private fun buildLunarPhasePath(
    center: Offset,
    radius: Float,
    hijriDay: Int
): Path {
    val day = hijriDay.coerceIn(1, 30)
    val moonRect = Rect(
        left = center.x - radius,
        top = center.y - radius,
        right = center.x + radius,
        bottom = center.y + radius
    )

    if (day in 13..16) {
        return Path().apply { addOval(moonRect) }
    }

    val isWaxing = day <= 15
    // Map Hijri day cleanly to illumination width ratio (0f = new, 0.5f = quarter, 1f = full)
    val illumRatio = if (isWaxing) {
        // Day 1 -> 0.20 (crisp clear crescent), Day 8 -> 0.52 (first quarter), Day 12 -> 0.88 (gibbous)
        when (day) {
            1 -> 0.20f
            2 -> 0.25f
            3 -> 0.30f
            4 -> 0.36f
            5 -> 0.42f
            6 -> 0.48f
            7 -> 0.53f
            8 -> 0.58f
            9 -> 0.65f
            10 -> 0.73f
            11 -> 0.81f
            else -> 0.89f
        }
    } else {
        // Symmetrical waning from day 17 -> 0.89 down to day 30 -> 0.20
        when (day) {
            17 -> 0.89f
            18 -> 0.82f
            19 -> 0.74f
            20 -> 0.66f
            21 -> 0.58f
            22 -> 0.52f
            23 -> 0.47f
            24 -> 0.41f
            25 -> 0.36f
            26 -> 0.32f
            27 -> 0.28f
            28 -> 0.25f
            29 -> 0.22f
            else -> 0.20f
        }
    }

    // Inner terminator ellipse horizontal half-axis:
    // At illumRatio < 0.5 (crescent): Difference between semicircle (width R) and inner oval (width R*(1 - 2*illumRatio))
    // At illumRatio >= 0.5 (gibbous): Union of semicircle (width R) and inner oval (width R*(2*illumRatio - 1))
    val innerRx = if (illumRatio < 0.5f) {
        (radius * (1f - 2f * illumRatio)).coerceAtLeast(1f)
    } else {
        (radius * (2f * illumRatio - 1f)).coerceAtLeast(1f)
    }

    val innerRect = Rect(
        left = center.x - innerRx,
        top = center.y - radius,
        right = center.x + innerRx,
        bottom = center.y + radius
    )

    val semicircle = Path().apply {
        if (isWaxing) {
            arcTo(moonRect, -90f, 180f, true)
        } else {
            arcTo(moonRect, 90f, 180f, true)
        }
        close()
    }
    val innerOval = Path().apply { addOval(innerRect) }

    return if (illumRatio < 0.5f) {
        Path.combine(PathOperation.Difference, semicircle, innerOval)
    } else {
        Path.combine(PathOperation.Union, semicircle, innerOval)
    }
}

private fun moonPhaseLabelRes(hijriDay: Int): Int {
    return when (hijriDay.coerceIn(1, 30)) {
        1, 29, 30 -> R.string.moon_waxing_crescent
        in 2..6 -> R.string.moon_waxing_crescent
        in 7..9 -> R.string.moon_first_quarter
        in 10..12 -> R.string.moon_waxing_gibbous
        in 13..16 -> R.string.moon_full
        in 17..20 -> R.string.moon_waning_gibbous
        in 21..23 -> R.string.moon_last_quarter
        else -> R.string.moon_waning_crescent
    }
}
