package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import com.example.ui.theme.AppIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.DhikrItem
import com.example.data.model.IslamicContent
import com.example.data.model.TasbihState
import com.example.data.model.TimeFormat
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
import com.example.ui.components.PortalRemindersSection
import com.example.util.AthkarAudioPlaybackState
import com.example.util.AthkarAudioType
import com.example.util.AthkarScheduleConfig
import com.example.util.PortalReminder
import com.example.util.PrayerTimeCalculator

@Composable
fun AthkarHubScreen(
    athkarCounts: Map<String, Int>,
    tasbihState: TasbihState,
    athkarAudioState: AthkarAudioPlaybackState = AthkarAudioPlaybackState(),
    athkarAutoEnabled: Boolean = true,
    athkarScheduleConfig: AthkarScheduleConfig = AthkarScheduleConfig(),
    timeFormat: TimeFormat = TimeFormat.HOUR_12,
    morningScheduleLabel: String = "6:00 ص",
    eveningScheduleLabel: String = "قبل المغرب بساعة",
    portalReminders: List<PortalReminder> = emptyList(),
    onIncrementAthkar: (String, Int) -> Unit,
    onResetAthkarItem: (String) -> Unit,
    onResetAllAthkar: () -> Unit,
    onPlayAthkarAudio: (AthkarAudioType) -> Unit = {},
    onStopAthkarAudio: () -> Unit = {},
    onToggleAthkarAuto: (Boolean) -> Unit = {},
    onUpdateAthkarSchedule: (String, Int) -> Unit = { _, _ -> },
    onPlayPortalReminder: (PortalReminder) -> Unit = {},
    onTogglePortalReminder: (String) -> Unit = {},
    onDeletePortalReminder: (String) -> Unit = {},
    onTasbihIncrement: () -> Unit,
    onTasbihReset: () -> Unit,
    onTasbihSelectDhikr: (String) -> Unit,
    onTasbihCycleTarget: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 0 = Athkar list, 1 = Tasbih
    var selectedSubTab by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            // Navy/Red header with sub-tabs: الأذكار & المسبحة
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                NavyPrimary.copy(alpha = 0.70f),
                                ObsidianBlack.copy(alpha = 0.62f)
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.nav_athkar),
                    style = MaterialTheme.typography.headlineLarge,
                    color = IceWhite
                )
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = NavyDeep,
                    border = BorderStroke(1.dp, NavyBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SubTabButton(
                            label = stringResource(R.string.athkar_tab_list),
                            icon = AppIcons.AutoStories,
                            selected = selectedSubTab == 0,
                            onClick = { selectedSubTab = 0 },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("athkar_subtab_list")
                        )
                        SubTabButton(
                            label = stringResource(R.string.athkar_tab_tasbih),
                            icon = AppIcons.TouchApp,
                            selected = selectedSubTab == 1,
                            onClick = { selectedSubTab = 1 },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("athkar_subtab_tasbih")
                        )
                    }
                }
            }

            if (selectedSubTab == 0) {
                AthkarListView(
                    athkarCounts = athkarCounts,
                    athkarAudioState = athkarAudioState,
                    athkarAutoEnabled = athkarAutoEnabled,
                    athkarScheduleConfig = athkarScheduleConfig,
                    timeFormat = timeFormat,
                    morningScheduleLabel = morningScheduleLabel,
                    eveningScheduleLabel = eveningScheduleLabel,
                    portalReminders = portalReminders,
                    onIncrementAthkar = onIncrementAthkar,
                    onResetAthkarItem = onResetAthkarItem,
                    onResetAllAthkar = onResetAllAthkar,
                    onPlayAthkarAudio = onPlayAthkarAudio,
                    onStopAthkarAudio = onStopAthkarAudio,
                    onToggleAthkarAuto = onToggleAthkarAuto,
                    onUpdateAthkarSchedule = onUpdateAthkarSchedule,
                    onPlayPortalReminder = onPlayPortalReminder,
                    onTogglePortalReminder = onTogglePortalReminder,
                    onDeletePortalReminder = onDeletePortalReminder,
                    modifier = Modifier.weight(1f)
                )
            } else {
                TasbihScreen(
                    tasbihState = tasbihState,
                    onIncrement = onTasbihIncrement,
                    onReset = onTasbihReset,
                    onSelectDhikr = onTasbihSelectDhikr,
                    onCycleTarget = onTasbihCycleTarget,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SubTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) CrimsonRed else Color.Transparent,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else SilverText,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (selected) Color.White else SilverText
            )
        }
    }
}

@Composable
private fun AthkarListView(
    athkarCounts: Map<String, Int>,
    athkarAudioState: AthkarAudioPlaybackState,
    athkarAutoEnabled: Boolean,
    athkarScheduleConfig: AthkarScheduleConfig,
    timeFormat: TimeFormat,
    morningScheduleLabel: String,
    eveningScheduleLabel: String,
    portalReminders: List<PortalReminder>,
    onIncrementAthkar: (String, Int) -> Unit,
    onResetAthkarItem: (String) -> Unit,
    onResetAllAthkar: () -> Unit,
    onPlayAthkarAudio: (AthkarAudioType) -> Unit,
    onStopAthkarAudio: () -> Unit,
    onToggleAthkarAuto: (Boolean) -> Unit,
    onUpdateAthkarSchedule: (String, Int) -> Unit,
    onPlayPortalReminder: (PortalReminder) -> Unit,
    onTogglePortalReminder: (String) -> Unit,
    onDeletePortalReminder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("all") }

    val allItems = remember { IslamicContent.ATHKAR_CATEGORIES.flatMap { it.items } }
    val filteredItems = remember(searchQuery, selectedCategoryId) {
        val base = if (selectedCategoryId == "all") {
            allItems
        } else {
            allItems.filter { it.categoryId == selectedCategoryId }
        }
        val q = searchQuery.trim()
        if (q.isEmpty()) base
        else base.filter { it.text.contains(q) || it.benefit.contains(q) }
    }

    val completedTotal = allItems.count { (athkarCounts[it.id] ?: 0) >= it.targetCount }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AthkarAudioPlayerCard(
                audioState = athkarAudioState,
                autoEnabled = athkarAutoEnabled,
                scheduleConfig = athkarScheduleConfig,
                timeFormat = timeFormat,
                morningScheduleLabel = morningScheduleLabel,
                eveningScheduleLabel = eveningScheduleLabel,
                onPlay = onPlayAthkarAudio,
                onStop = onStopAthkarAudio,
                onToggleAuto = onToggleAthkarAuto,
                onUpdateSchedule = onUpdateAthkarSchedule
            )
        }

        if (portalReminders.isNotEmpty()) {
            item {
                PortalRemindersSection(
                    reminders = portalReminders,
                    athkarAudioState = athkarAudioState,
                    onPlayReminder = onPlayPortalReminder,
                    onStopAudio = onStopAthkarAudio,
                    onToggleActive = onTogglePortalReminder,
                    onDeleteReminder = onDeletePortalReminder
                )
            }
        }

        item {
            // Search box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.athkar_search_hint), color = SilverText) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = CrimsonBright) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CrimsonBright,
                    unfocusedBorderColor = NavyBorder,
                    focusedContainerColor = NavyDeep,
                    unfocusedContainerColor = NavyDeep,
                    focusedTextColor = IceWhite,
                    unfocusedTextColor = IceWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("athkar_search_input")
            )
        }

        item {
            // Horizontal category filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategoryId == "all",
                    onClick = { selectedCategoryId = "all" },
                    label = { Text(stringResource(R.string.athkar_all_categories)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CrimsonRed,
                        selectedLabelColor = Color.White,
                        containerColor = NavyDeep,
                        labelColor = SilverText
                    )
                )
                IslamicContent.ATHKAR_CATEGORIES.forEach { cat ->
                    FilterChip(
                        selected = selectedCategoryId == cat.id,
                        onClick = { selectedCategoryId = cat.id },
                        label = { Text(cat.title) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CrimsonRed,
                            selectedLabelColor = Color.White,
                            containerColor = NavyDeep,
                            labelColor = SilverText
                        )
                    )
                }
            }
        }

        item {
            // Progress & reset bar
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = NavyDeep,
                border = BorderStroke(1.dp, NavyBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "أتممت $completedTotal من ${allItems.size} ذكر",
                        style = MaterialTheme.typography.labelLarge,
                        color = WarmHighlight
                    )
                    TextButton(onClick = onResetAllAthkar) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = CrimsonBright,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.athkar_reset_all), color = CrimsonBright)
                    }
                }
            }
        }

        items(filteredItems, key = { it.id }) { item ->
            val currentCount = athkarCounts[item.id] ?: 0
            DhikrCard(
                item = item,
                currentCount = currentCount,
                onTap = { onIncrementAthkar(item.id, item.targetCount) },
                onReset = { onResetAthkarItem(item.id) }
            )
        }
    }
}

@Composable
private fun DhikrCard(
    item: DhikrItem,
    currentCount: Int,
    onTap: () -> Unit,
    onReset: () -> Unit
) {
    val completed = currentCount >= item.targetCount
    val progress = (currentCount.toFloat() / item.targetCount.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable { onTap() }
            .testTag("dhikr_card_${item.id}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (completed) CrimsonContainer.copy(alpha = 0.72f) else NavyDeep
        ),
        border = BorderStroke(
            width = if (completed) 1.5.dp else 1.dp,
            color = if (completed) CrimsonBright else NavyBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 30.sp
                ),
                color = IceWhite,
                textAlign = TextAlign.Start
            )

            Text(
                text = item.benefit,
                style = MaterialTheme.typography.bodyMedium,
                color = WarmHighlight.copy(alpha = 0.88f)
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (completed) CrimsonBright else CrimsonRed,
                trackColor = NavyElevated
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (completed) CrimsonRed else NavyPrimary,
                    border = BorderStroke(1.dp, if (completed) CrimsonBright else NavyBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (completed) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = "$currentCount / ${item.targetCount}",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = Color.White
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (completed) {
                        Text(
                            text = stringResource(R.string.athkar_completed_badge),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = WarmHighlight
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (currentCount > 0) {
                        IconButton(
                            onClick = onReset,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.tasbih_reset),
                                tint = SilverText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AthkarAudioPlayerCard(
    audioState: AthkarAudioPlaybackState,
    autoEnabled: Boolean,
    scheduleConfig: AthkarScheduleConfig,
    timeFormat: TimeFormat,
    morningScheduleLabel: String,
    eveningScheduleLabel: String,
    onPlay: (AthkarAudioType) -> Unit,
    onStop: () -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onUpdateSchedule: (String, Int) -> Unit
) {
    val morningPlaying = audioState.isPlaying && audioState.activeType == AthkarAudioType.MORNING
    val eveningPlaying = audioState.isPlaying && audioState.activeType == AthkarAudioType.EVENING
    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        AthkarScheduleEditDialog(
            initialConfig = scheduleConfig,
            timeFormat = timeFormat,
            onDismiss = { showEditDialog = false },
            onSave = { morning24, offsetMin ->
                onUpdateSchedule(morning24, offsetMin)
                showEditDialog = false
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("athkar_audio_player_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = NavyDeep.copy(alpha = 0.85f)),
        border = BorderStroke(
            1.dp,
            if (audioState.isPlaying) AmberStar else NavyBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = AppIcons.VolumeUp,
                        contentDescription = null,
                        tint = AmberStar,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.athkar_audio_card_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = IceWhite
                        )
                        Text(
                            text = stringResource(
                                R.string.athkar_audio_schedule_format,
                                morningScheduleLabel,
                                eveningScheduleLabel
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = WarmHighlight
                        )
                    }
                }

                Switch(
                    checked = autoEnabled,
                    onCheckedChange = onToggleAuto,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CrimsonRed
                    ),
                    modifier = Modifier.testTag("athkar_audio_auto_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { if (morningPlaying) onStop() else onPlay(AthkarAudioType.MORNING) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (morningPlaying) CrimsonRed else NavyPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("play_morning_athkar_button")
                ) {
                    Icon(
                        imageVector = if (morningPlaying) AppIcons.Stop else AppIcons.WbSunny,
                        contentDescription = null,
                        tint = if (morningPlaying) Color.White else AmberStar,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (morningPlaying) stringResource(R.string.athkar_audio_stop_btn)
                        else stringResource(R.string.athkar_audio_morning_btn),
                        color = IceWhite,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { if (eveningPlaying) onStop() else onPlay(AthkarAudioType.EVENING) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (eveningPlaying) CrimsonRed else NavyPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("play_evening_athkar_button")
                ) {
                    Icon(
                        imageVector = if (eveningPlaying) AppIcons.Stop else AppIcons.NightsStay,
                        contentDescription = null,
                        tint = if (eveningPlaying) Color.White else WarmHighlight,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (eveningPlaying) stringResource(R.string.athkar_audio_stop_btn)
                        else stringResource(R.string.athkar_audio_evening_btn),
                        color = IceWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            OutlinedButton(
                onClick = { showEditDialog = true },
                border = BorderStroke(1.dp, NavyBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("edit_athkar_schedule_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = AmberStar,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تعديل مواعيد أذكار الصباح والمساء",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = WarmHighlight
                )
            }

        }
    }
}

@Composable
private fun AthkarScheduleEditDialog(
    initialConfig: AthkarScheduleConfig,
    timeFormat: TimeFormat,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var morning24 by remember(initialConfig) { mutableStateOf(initialConfig.morningTime24) }
    var eveningOffset by remember(initialConfig) { mutableIntStateOf(initialConfig.eveningOffsetFromMaghribMinutes) }

    val morningPresets = listOf("05:00", "05:30", "06:00", "06:30", "07:00")
    val eveningPresets = listOf(
        -60 to "قبل بساعة",
        -45 to "قبل ٤٥ د",
        -30 to "قبل ٣٠ د",
        0 to "عند المغرب",
        15 to "بعد ١٥ د"
    )

    val eveningSummary = remember(eveningOffset) {
        AthkarScheduleConfig(morning24, eveningOffset).eveningRelationLabelAr()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyDeep,
        titleContentColor = IceWhite,
        textContentColor = SilverText,
        title = {
            Text(
                text = "تعديل مواعيد الأذكار",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = IceWhite
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Morning section
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = NavyPrimary.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, NavyBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "أذكار الصباح",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = AmberStar
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { morning24 = PrayerTimeCalculator.shiftTime24(morning24, -15) },
                                border = BorderStroke(1.dp, NavyBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("− 15 د", color = IceWhite)
                            }
                            Text(
                                text = PrayerTimeCalculator.formatTime(morning24, timeFormat),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = IceWhite
                            )
                            OutlinedButton(
                                onClick = { morning24 = PrayerTimeCalculator.shiftTime24(morning24, 15) },
                                border = BorderStroke(1.dp, NavyBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("+ 15 د", color = IceWhite)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { morning24 = PrayerTimeCalculator.shiftTime24(morning24, -5) }) {
                                Text("− 5 د", color = WarmHighlight)
                            }
                            TextButton(onClick = { morning24 = PrayerTimeCalculator.shiftTime24(morning24, 5) }) {
                                Text("+ 5 د", color = WarmHighlight)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            morningPresets.forEach { preset ->
                                FilterChip(
                                    selected = morning24 == preset,
                                    onClick = { morning24 = preset },
                                    label = { Text(PrayerTimeCalculator.formatTime(preset, timeFormat)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AmberStar,
                                        selectedLabelColor = NavyDeep,
                                        containerColor = NavyDeep,
                                        labelColor = SilverText
                                    )
                                )
                            }
                        }
                    }
                }

                // Evening section (relative to Maghrib)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = NavyPrimary.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, NavyBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "أذكار المساء (بالنسبة لأذان المغرب)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = WarmHighlight
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { eveningOffset = (eveningOffset - 15).coerceIn(-180, 120) },
                                border = BorderStroke(1.dp, NavyBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("أبكر 15 د", color = IceWhite, fontSize = 11.sp)
                            }
                            Text(
                                text = eveningSummary,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = IceWhite
                            )
                            OutlinedButton(
                                onClick = { eveningOffset = (eveningOffset + 15).coerceIn(-180, 120) },
                                border = BorderStroke(1.dp, NavyBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("تأخير 15 د", color = IceWhite, fontSize = 11.sp)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { eveningOffset = (eveningOffset - 5).coerceIn(-180, 120) }) {
                                Text("أبكر 5 د", color = WarmHighlight)
                            }
                            TextButton(onClick = { eveningOffset = (eveningOffset + 5).coerceIn(-180, 120) }) {
                                Text("تأخير 5 د", color = WarmHighlight)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            eveningPresets.forEach { (offsetMin, label) ->
                                FilterChip(
                                    selected = eveningOffset == offsetMin,
                                    onClick = { eveningOffset = offsetMin },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AmberStar,
                                        selectedLabelColor = NavyDeep,
                                        containerColor = NavyDeep,
                                        labelColor = SilverText
                                    )
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = {
                        morning24 = AthkarScheduleConfig.DEFAULT_MORNING_TIME_24
                        eveningOffset = AthkarScheduleConfig.DEFAULT_EVENING_OFFSET_FROM_MAGHRIB
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "استعادة الافتراضي (٦:٠٠ ص • قبل المغرب بساعة)",
                        color = CrimsonBright,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(morning24, eveningOffset) },
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
            ) {
                Text("حفظ المواعيد", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = SilverText)
            }
        }
    )
}
