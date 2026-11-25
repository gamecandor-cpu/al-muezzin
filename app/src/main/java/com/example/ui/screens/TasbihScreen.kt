package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.TasbihState
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.IceWhite
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyElevated
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SilverText
import com.example.ui.theme.WarmHighlight
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasbihScreen(
    tasbihState: TasbihState,
    onIncrement: () -> Unit,
    onReset: () -> Unit,
    onSelectDhikr: (String) -> Unit,
    onCycleTarget: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressedPulse by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressedPulse) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tasbih_scale"
    )
    val progressRatio = (tasbihState.count.toFloat() / tasbihState.target.coerceAtLeast(1).toFloat())
        .coerceIn(0f, 1f)
    val animatedSweep by animateFloatAsState(
        targetValue = progressRatio * 360f,
        label = "tasbih_sweep"
    )

    LaunchedEffect(pressedPulse) {
        if (pressedPulse) {
            delay(110)
            pressedPulse = false
        }
    }

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
                text = stringResource(R.string.tasbih_title),
                style = MaterialTheme.typography.headlineMedium,
                color = IceWhite
            )

            Spacer(modifier = Modifier.height(22.dp))

            Box(
                modifier = Modifier
                    .size(252.dp)
                    .scale(scale),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx()
                    drawArc(
                        color = NavyElevated,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = CrimsonBright,
                        startAngle = -90f,
                        sweepAngle = animatedSweep,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(214.dp)
                        .clip(CircleShape)
                        .border(2.dp, NavyBorder, CircleShape)
                        .background(
                            Brush.radialGradient(listOf(NavyPrimary, ObsidianBlack))
                        )
                        .clickable {
                            pressedPulse = true
                            onIncrement()
                        }
                        .testTag("tasbih_counter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = tasbihState.count.toString(),
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            color = IceWhite,
                            style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = tasbihState.dhikr,
                style = MaterialTheme.typography.displayMedium,
                color = WarmHighlight,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TasbihState.DHIKR_OPTIONS.forEach { dhikr ->
                    val selected = tasbihState.dhikr == dhikr
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectDhikr(dhikr) },
                        label = {
                            Text(
                                text = dhikr,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CrimsonRed,
                            selectedLabelColor = Color.White,
                            containerColor = NavyDeep,
                            labelColor = SilverText
                        ),
                        modifier = Modifier.testTag("dhikr_chip_$dhikr")
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onReset,
                    border = BorderStroke(1.dp, NavyBorder),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("tasbih_reset_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = IceWhite, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.tasbih_reset), color = IceWhite)
                }

                Button(
                    onClick = onCycleTarget,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("tasbih_target_button")
                ) {
                    Text(stringResource(R.string.tasbih_target_format, tasbihState.target))
                }
            }
        }
    }
}
