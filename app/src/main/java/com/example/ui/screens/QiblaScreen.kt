package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import com.example.ui.theme.AppIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CityPreset
import com.example.ui.theme.AmberStar
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.IceWhite
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyElevated
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SilverText
import com.example.ui.theme.WarmHighlight
import com.example.ui.viewmodel.MuezzinUiState
import com.example.ui.viewmodel.PlaceSearchResult
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun QiblaScreen(
    uiState: MuezzinUiState,
    onRequestLocation: () -> Unit,
    onSelectCityPreset: (CityPreset) -> Unit,
    onSearchPlaces: (String) -> Unit,
    onSelectSearchPlace: (PlaceSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCitySheet by remember { mutableStateOf(false) }

    val qiblaBearing = uiState.qiblaBearing
    val deviceHeading = uiState.deviceHeading

    val targetDialAngle = if (deviceHeading != null) -deviceHeading else 0f
    val targetNeedleAngle = when {
        qiblaBearing == null -> 0f
        deviceHeading != null -> qiblaBearing - deviceHeading
        else -> qiblaBearing
    }

    var continuousDialAngle by remember { mutableFloatStateOf(targetDialAngle) }
    var continuousNeedleAngle by remember { mutableFloatStateOf(targetNeedleAngle) }

    LaunchedEffect(targetDialAngle) {
        continuousDialAngle += shortestAngleDelta(continuousDialAngle, targetDialAngle)
    }
    LaunchedEffect(targetNeedleAngle) {
        continuousNeedleAngle += shortestAngleDelta(continuousNeedleAngle, targetNeedleAngle)
    }

    val animatedDialAngle by animateFloatAsState(
        targetValue = continuousDialAngle,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "compass_dial_angle"
    )
    val animatedNeedleAngle by animateFloatAsState(
        targetValue = continuousNeedleAngle,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "qibla_needle_angle"
    )

    val isAligned = qiblaBearing != null &&
        deviceHeading != null &&
        abs(shortestAngleDelta(deviceHeading, qiblaBearing)) <= 6f

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.qibla_title),
                style = MaterialTheme.typography.headlineLarge,
                color = IceWhite
            )
            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                onClick = { showCitySheet = true },
                shape = RoundedCornerShape(20.dp),
                color = NavyPrimary,
                border = BorderStroke(1.dp, NavyBorder),
                modifier = Modifier.testTag("qibla_location_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = CrimsonBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = uiState.locationName ?: stringResource(R.string.location_choose_city),
                        style = MaterialTheme.typography.labelLarge,
                        color = IceWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier.size(296.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = animatedDialAngle },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = size.minDimension / 2f

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(NavyPrimary, ObsidianBlack),
                                center = center,
                                radius = radius
                            ),
                            radius = radius
                        )

                        drawCircle(
                            color = if (isAligned) CrimsonBright else NavyBorder,
                            radius = radius - 4.dp.toPx(),
                            style = Stroke(width = if (isAligned) 3.5.dp.toPx() else 2.dp.toPx())
                        )

                        drawCircle(
                            color = CrimsonRed.copy(alpha = 0.35f),
                            radius = radius - 16.dp.toPx(),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                            )
                        )

                        for (i in 0 until 36) {
                            val angleRad = Math.toRadians(i * 10.0)
                            val major = i % 9 == 0
                            val innerR = radius - (if (major) 14.dp.toPx() else 9.dp.toPx())
                            val outerR = radius - 4.dp.toPx()
                            drawLine(
                                color = if (major) IceWhite.copy(alpha = 0.85f) else SilverText.copy(alpha = 0.35f),
                                start = Offset(
                                    center.x + (innerR * sin(angleRad)).toFloat(),
                                    center.y - (innerR * cos(angleRad)).toFloat()
                                ),
                                end = Offset(
                                    center.x + (outerR * sin(angleRad)).toFloat(),
                                    center.y - (outerR * cos(angleRad)).toFloat()
                                ),
                                strokeWidth = if (major) 2.2.dp.toPx() else 1.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    Text(
                        text = "N",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = CrimsonBright,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 18.dp)
                    )
                    Text(
                        text = "S",
                        style = MaterialTheme.typography.titleMedium,
                        color = SilverText,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 18.dp)
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "W",
                                style = MaterialTheme.typography.titleMedium,
                                color = SilverText,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 18.dp)
                            )
                            Text(
                                text = "E",
                                style = MaterialTheme.typography.titleMedium,
                                color = SilverText,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 18.dp)
                            )
                        }
                    }
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension / 2f

                    rotate(degrees = animatedNeedleAngle, pivot = center) {
                        val tipY = center.y - radius * 0.72f
                        drawLine(
                            brush = Brush.verticalGradient(
                                colors = listOf(CrimsonBright, NavyElevated),
                                startY = tipY,
                                endY = center.y
                            ),
                            start = center,
                            end = Offset(center.x, tipY + 12.dp.toPx()),
                            strokeWidth = 6.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        val arrowPath = Path().apply {
                            moveTo(center.x, tipY - 4.dp.toPx())
                            lineTo(center.x - 10.dp.toPx(), tipY + 14.dp.toPx())
                            lineTo(center.x + 10.dp.toPx(), tipY + 14.dp.toPx())
                            close()
                        }
                        drawPath(
                            path = arrowPath,
                            color = if (isAligned) AmberStar else CrimsonBright
                        )

                        // Kaaba emblem near tip
                        drawCircle(
                            color = ObsidianBlack,
                            radius = 11.dp.toPx(),
                            center = Offset(center.x, tipY + 26.dp.toPx())
                        )
                        drawCircle(
                            color = AmberStar,
                            radius = 11.dp.toPx(),
                            center = Offset(center.x, tipY + 26.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    drawCircle(
                        color = CrimsonBright,
                        radius = 13.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = ObsidianBlack,
                        radius = 6.dp.toPx(),
                        center = center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isAligned) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CrimsonRed
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.qibla_aligned),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = qiblaBearing?.let { String.format(Locale.US, "%.1f°", it) } ?: "--°",
                    style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                    color = CrimsonBright
                )
            }

            if (qiblaBearing == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.qibla_info_no_location),
                    style = MaterialTheme.typography.bodyLarge,
                    color = SilverText,
                    textAlign = TextAlign.Center
                )
            }

            if (!uiState.hasCompassSensor || (qiblaBearing != null && deviceHeading == null)) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = CrimsonContainer,
                    border = BorderStroke(1.dp, CrimsonRed)
                ) {
                    Text(
                        text = stringResource(R.string.qibla_no_sensor),
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarmHighlight,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            if (qiblaBearing == null) {
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onRequestLocation,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        modifier = Modifier.testTag("qibla_detect_location_button")
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.location_detect))
                    }
                    OutlinedButton(
                        onClick = { showCitySheet = true },
                        border = BorderStroke(1.dp, NavyBorder),
                        modifier = Modifier.testTag("qibla_choose_city_button")
                    ) {
                        Text(stringResource(R.string.location_choose_city), color = IceWhite)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(18.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = NavyDeep),
                    border = BorderStroke(1.dp, NavyBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Mosque,
                            contentDescription = null,
                            tint = AmberStar
                        )
                        Text(
                            text = "الكعبة المشرفة - مكة المكرمة",
                            style = MaterialTheme.typography.titleMedium,
                            color = IceWhite
                        )
                        deviceHeading?.let { heading ->
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = "Heading: ${String.format(Locale.US, "%.0f°", heading)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SilverText
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCitySheet) {
        CityPickerBottomSheet(
            isSearching = uiState.isSearchingPlaces,
            searchResults = uiState.placeSearchResults,
            onSearchQueryChange = onSearchPlaces,
            onSelectSearchPlace = { place ->
                onSelectSearchPlace(place)
                showCitySheet = false
            },
            onDismiss = { showCitySheet = false },
            onDetectGps = {
                showCitySheet = false
                onRequestLocation()
            },
            onSelectCity = { city ->
                onSelectCityPreset(city)
                showCitySheet = false
            }
        )
    }
}

private fun shortestAngleDelta(fromDeg: Float, toDeg: Float): Float {
    var diff = (toDeg - fromDeg) % 360f
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    return diff
}
