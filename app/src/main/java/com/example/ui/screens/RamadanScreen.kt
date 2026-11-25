package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.QadaState
import com.example.data.model.RamadanSettings
import com.example.ui.theme.AmberStar
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonDark
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

@Composable
fun RamadanScreen(
    uiState: MuezzinUiState,
    onUpdateImsakAlarm: (Boolean, Int) -> Unit,
    onUpdateIftarAlarm: (Boolean, Int) -> Unit,
    onTestImsakAlarm: () -> Unit,
    onTestIftarAlarm: () -> Unit,
    onAdjustQadaRemaining: (Int) -> Unit,
    onMarkQadaDone: () -> Unit,
    onResetQada: () -> Unit,
    showSettingsHeaderBar: Boolean = false,
    onBackToSettings: () -> Unit = {},
    onActivateRamadanTab: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = showSettingsHeaderBar, onBack = onBackToSettings)
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission granted or denied; alarms remain stored */ }

    val ensureNotificationPermission: (() -> Unit) -> Unit = { action ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        action()
    }

    val ramadanStatus = uiState.ramadanStatus
    val ramadanSettings = uiState.ramadanSettings
    val qada = uiState.qadaState

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (showSettingsHeaderBar) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onBackToSettings,
                            modifier = Modifier.testTag("ramadan_back_to_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.ramadan_back_to_settings),
                                tint = IceWhite
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.ramadan_back_to_settings),
                                color = IceWhite
                            )
                        }

                        Button(
                            onClick = onActivateRamadanTab,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("ramadan_pin_to_tabs_button")
                        ) {
                            Text(
                                text = stringResource(R.string.ramadan_pin_to_tabs),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            item {
                // Hero banner for Ramadan / Fasting countdown
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.6f)),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        NavyPrimary.copy(alpha = 0.74f),
                                        ObsidianBlack.copy(alpha = 0.66f)
                                    )
                                )
                            )
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NightsStay,
                                contentDescription = null,
                                tint = AmberStar,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = stringResource(R.string.ramadan_title),
                                style = MaterialTheme.typography.headlineLarge,
                                color = IceWhite
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.hijriDate,
                            style = MaterialTheme.typography.titleMedium,
                            color = WarmHighlight
                        )

                        if (ramadanStatus != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = ramadanStatus.countdownLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                color = SilverText
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = ramadanStatus.countdownFormatted,
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = CrimsonBright
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { ramadanStatus.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = CrimsonBright,
                                trackColor = NavyElevated
                            )
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.select_location_prompt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SilverText,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Suhoor, Imsak, Fajr, Iftar timings grid
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = NavyDeep),
                    border = BorderStroke(1.dp, NavyBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ramadan_suhoor_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = IceWhite
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RamadanTimeBox(
                                title = stringResource(R.string.ramadan_suhoor_label),
                                time = ramadanStatus?.suhoorFormatted ?: "--",
                                icon = Icons.Filled.Restaurant,
                                accentColor = AmberStar,
                                modifier = Modifier.weight(1f)
                            )
                            RamadanTimeBox(
                                title = stringResource(R.string.ramadan_imsak_label),
                                time = ramadanStatus?.imsakFormatted ?: "--",
                                icon = Icons.Filled.NightsStay,
                                accentColor = CrimsonBright,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RamadanTimeBox(
                                title = stringResource(R.string.ramadan_fajr_label),
                                time = ramadanStatus?.fajrFormatted ?: "--",
                                icon = Icons.Filled.WbTwilight,
                                accentColor = WarmHighlight,
                                modifier = Modifier.weight(1f)
                            )
                            RamadanTimeBox(
                                title = stringResource(R.string.ramadan_iftar_label),
                                time = ramadanStatus?.iftarFormatted ?: "--",
                                icon = Icons.Filled.WbTwilight,
                                accentColor = CrimsonBright,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Suhoor/Imsak & Iftar Alarms Card
            item {
                RamadanAlarmsCard(
                    settings = ramadanSettings,
                    onToggleImsak = { enabled ->
                        ensureNotificationPermission {
                            onUpdateImsakAlarm(enabled, ramadanSettings.imsakLeadMinutes)
                        }
                    },
                    onSelectImsakLead = { mins ->
                        onUpdateImsakAlarm(ramadanSettings.imsakAlarmEnabled, mins)
                    },
                    onToggleIftar = { enabled ->
                        ensureNotificationPermission {
                            onUpdateIftarAlarm(enabled, ramadanSettings.iftarLeadMinutes)
                        }
                    },
                    onSelectIftarLead = { mins ->
                        onUpdateIftarAlarm(ramadanSettings.iftarAlarmEnabled, mins)
                    },
                    onTestImsak = { ensureNotificationPermission(onTestImsakAlarm) },
                    onTestIftar = { ensureNotificationPermission(onTestIftarAlarm) }
                )
            }

            // Qada Fasting Days Tracker
            item {
                QadaTrackerCard(
                    qada = qada,
                    onAdjustRemaining = onAdjustQadaRemaining,
                    onMarkDone = onMarkQadaDone,
                    onReset = onResetQada
                )
            }

            // Authentic Fasting Duas Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = NavyDeep),
                    border = BorderStroke(1.dp, NavyBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ramadan_dua_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = WarmHighlight
                        )
                        Text(
                            text = "عند الإفطار: «ذَهَبَ الظَّمَأُ وَابْتَلَّتِ الْعُرُوقُ، وَثَبَتَ الأَجْرُ إِنْ شَاءَ اللَّهُ».",
                            style = MaterialTheme.typography.bodyLarge,
                            color = IceWhite
                        )
                        HorizontalDivider(color = NavyBorder)
                        Text(
                            text = "فضل السحور: قال رسول الله ﷺ: «تَسَحَّرُوا فَإِنَّ فِي السَّحُورِ بَرَكَةً».",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SilverText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RamadanTimeBox(
    title: String,
    time: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = NavyPrimary,
        border = BorderStroke(1.dp, NavyBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = SilverText,
                textAlign = TextAlign.Center
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = IceWhite
                )
            }
        }
    }
}

@Composable
private fun RamadanAlarmsCard(
    settings: RamadanSettings,
    onToggleImsak: (Boolean) -> Unit,
    onSelectImsakLead: (Int) -> Unit,
    onToggleIftar: (Boolean) -> Unit,
    onSelectIftarLead: (Int) -> Unit,
    onTestImsak: () -> Unit,
    onTestIftar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = NavyDeep),
        border = BorderStroke(1.dp, NavyBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Alarm, contentDescription = null, tint = CrimsonBright)
                Text(
                    text = stringResource(R.string.ramadan_alarms_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = IceWhite
                )
            }

            // Imsak / Suhoor alarm row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.ramadan_imsak_alarm),
                    style = MaterialTheme.typography.titleMedium,
                    color = IceWhite,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = settings.imsakAlarmEnabled,
                    onCheckedChange = onToggleImsak,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CrimsonRed
                    ),
                    modifier = Modifier.testTag("ramadan_imsak_alarm_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0 to "عند الإمساك", 15 to "قبل 15 د", 30 to "قبل 30 د", 45 to "قبل 45 د").forEach { (mins, label) ->
                    FilterChip(
                        selected = settings.imsakLeadMinutes == mins,
                        onClick = { onSelectImsakLead(mins) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CrimsonRed,
                            selectedLabelColor = Color.White,
                            containerColor = NavyPrimary,
                            labelColor = SilverText
                        )
                    )
                }
            }

            HorizontalDivider(color = NavyBorder)

            // Iftar alarm row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.ramadan_iftar_alarm),
                    style = MaterialTheme.typography.titleMedium,
                    color = IceWhite,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = settings.iftarAlarmEnabled,
                    onCheckedChange = onToggleIftar,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CrimsonRed
                    ),
                    modifier = Modifier.testTag("ramadan_iftar_alarm_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0 to "عند الأذان", 5 to "قبل 5 د", 10 to "قبل 10 د", 15 to "قبل 15 د").forEach { (mins, label) ->
                    FilterChip(
                        selected = settings.iftarLeadMinutes == mins,
                        onClick = { onSelectIftarLead(mins) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CrimsonRed,
                            selectedLabelColor = Color.White,
                            containerColor = NavyPrimary,
                            labelColor = SilverText
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onTestImsak,
                    border = BorderStroke(1.dp, NavyBorder),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.ramadan_test_imsak), color = WarmHighlight, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onTestIftar,
                    border = BorderStroke(1.dp, CrimsonRed),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.ramadan_test_iftar), color = CrimsonBright, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun QadaTrackerCard(
    qada: QadaState,
    onAdjustRemaining: (Int) -> Unit,
    onMarkDone: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = NavyDeep),
        border = BorderStroke(1.dp, NavyBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.ramadan_qada_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = IceWhite
                )
                TextButton(onClick = onReset) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = SilverText, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.tasbih_reset), color = SilverText)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = CrimsonContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, CrimsonRed),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.ramadan_qada_remaining),
                            style = MaterialTheme.typography.labelMedium,
                            color = WarmHighlight
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = { onAdjustRemaining(-1) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(NavyPrimary)
                                    .testTag("qada_decrement_button")
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = null, tint = IceWhite)
                            }
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = "${qada.remainingDays}",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = IceWhite
                                )
                            }
                            IconButton(
                                onClick = { onAdjustRemaining(1) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CrimsonRed)
                                    .testTag("qada_increment_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = NavyPrimary,
                    border = BorderStroke(1.dp, NavyBorder),
                    modifier = Modifier.weight(0.8f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.ramadan_qada_completed),
                            style = MaterialTheme.typography.labelMedium,
                            color = SilverText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = "${qada.completedDays}",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = AmberStar
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onMarkDone,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("qada_mark_done_button")
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.ramadan_qada_mark_done), fontWeight = FontWeight.Bold)
            }
        }
    }
}
