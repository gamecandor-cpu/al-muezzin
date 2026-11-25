package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.Brightness5
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import com.example.R
import com.example.data.model.AthanPlayMode
import com.example.data.model.CityPreset
import com.example.data.model.IslamicContent
import com.example.data.model.MuezzinVoice
import com.example.data.model.PrayerAdjustments
import com.example.data.model.PrayerTimeItem
import com.example.data.model.PrayerType
import com.example.data.model.ReligiousOccasion
import com.example.data.model.UserSettings
import com.example.data.remote.BroadcastReminder
import com.example.ui.components.MinaretCountdownGauge
import com.example.ui.components.RealisticHijriMoonIndicator
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
import com.example.ui.viewmodel.PlaceSearchResult
import com.example.ui.viewmodel.PrayersLoadState
import com.example.util.AthkarAudioPlaybackState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: MuezzinUiState,
    onRequestLocationPermissionAndDetect: () -> Unit,
    onSelectCityPreset: (CityPreset) -> Unit,
    onSearchPlaces: (String) -> Unit,
    onSelectSearchPlace: (PlaceSearchResult) -> Unit,
    onRetryFetch: () -> Unit,
    onUpdateGlobalOffset: (Int) -> Unit,
    onUpdatePrayerOffset: (PrayerType, Int) -> Unit,
    onResetAllOffsets: () -> Unit,
    onAthanToggle: (Boolean) -> Unit = {},
    onMuezzinVoiceChange: (MuezzinVoice) -> Unit = {},
    onFajrAthanToggle: (Boolean) -> Unit = {},
    onAthanPlayModeChange: (AthanPlayMode) -> Unit = {},
    onTestAthanSound: (Boolean) -> Unit = {},
    onStopAthan: () -> Unit = {},
    onStopAthkarAudio: () -> Unit = {},
    onPlayRemoteReminderAudio: (BroadcastReminder) -> Unit = {},
    onRequestNotifications: () -> Unit = {},
    onOpenExactAlarmSettings: () -> Unit = {},
    onOpenBatterySettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showCitySheet by remember { mutableStateOf(false) }
    var selectedPrayerForVirtue by remember { mutableStateOf<PrayerTimeItem?>(null) }
    // 0 = مواقيت الصلاة, 1 = تعديل التوقيت
    var homeSectionTab by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Image(
            painter = painterResource(id = R.drawable.bg_home_calligraphy),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.20f),
                            Color.Black.copy(alpha = 0.35f),
                            ObsidianBlack.copy(alpha = 0.65f)
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                NavyHeader(
                    locationLabel = when {
                        uiState.isDetectingLocation -> stringResource(R.string.location_detecting)
                        uiState.locationName != null -> uiState.locationName
                        else -> stringResource(R.string.location_detect)
                    },
                    cityLocalTime = uiState.cityLocalTime,
                    hasLocation = uiState.locationName != null,
                    hijriDate = uiState.hijriDate,
                    hijriDay = uiState.hijriDay,
                    gregorianDate = uiState.gregorianDate,
                    onLocationClick = { showCitySheet = true }
                )
            }

            uiState.activeOccasion?.let { occasion ->
                item {
                    ActiveOccasionHomeBanner(
                        occasion = occasion,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            if (!uiState.hasNotificationPermission ||
                !uiState.canScheduleExactAlarms ||
                !uiState.isIgnoringBatteryOptimizations
            ) {
                item {
                    MandatoryBackgroundSetupBanner(
                        hasNotificationPermission = uiState.hasNotificationPermission,
                        canScheduleExactAlarms = uiState.canScheduleExactAlarms,
                        isIgnoringBatteryOptimizations = uiState.isIgnoringBatteryOptimizations,
                        onFixSetup = {
                            when {
                                !uiState.hasNotificationPermission -> onRequestNotifications()
                                !uiState.canScheduleExactAlarms -> onOpenExactAlarmSettings()
                                else -> onOpenBatterySettings()
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            item {
                NextPrayerHeroCard(
                    uiState = uiState,
                    onLocationClick = onRequestLocationPermissionAndDetect,
                    onChooseCityClick = { showCitySheet = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    if (uiState.isAthanPlaying) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = CrimsonContainer,
                            border = BorderStroke(1.dp, CrimsonRed),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.athan_now_playing_banner),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = IceWhite
                                )
                                Button(
                                    onClick = onStopAthan,
                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("home_stop_athan_button")
                                ) {
                                    Icon(
                                        Icons.Filled.Stop,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.setting_stop_athan))
                                }
                            }
                        }
                    }

                    if (uiState.athkarAudioState.isPlaying) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = NavyElevated,
                            border = BorderStroke(1.dp, AmberStar.copy(alpha = 0.85f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.athkar_now_playing_banner,
                                        uiState.athkarAudioState.displayLabel
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = WarmHighlight,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = onStopAthkarAudio,
                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("home_stop_athkar_button")
                                ) {
                                    Icon(
                                        Icons.Filled.Stop,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.athkar_audio_stop_btn))
                                }
                            }
                        }
                    }

                    if (uiState.broadcastReminders.isNotEmpty()) {
                        BroadcastRemindersHomeSection(
                            reminders = uiState.broadcastReminders,
                            playbackState = uiState.athkarAudioState,
                            onPlayAudio = onPlayRemoteReminderAudio,
                            onStopAudio = onStopAthkarAudio,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

                    // On-page tab switcher: مواقيت الصلاة | تعديل التوقيت
                    HomeSectionTabSwitcher(
                        selectedTab = homeSectionTab,
                        hasOffsets = uiState.adjustments.hasAnyOffset,
                        onSelectTab = { homeSectionTab = it }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (homeSectionTab == 0) {
                        when (val loadState = uiState.prayersState) {
                            is PrayersLoadState.NeedsLocation -> {
                                EmptyOrErrorCard(
                                    message = stringResource(R.string.select_location_prompt),
                                    primaryActionLabel = stringResource(R.string.location_gps_action),
                                    onPrimaryAction = onRequestLocationPermissionAndDetect,
                                    secondaryActionLabel = stringResource(R.string.location_choose_city),
                                    onSecondaryAction = { showCitySheet = true }
                                )
                            }
                            is PrayersLoadState.Loading -> {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(containerColor = NavyDeep),
                                    border = BorderStroke(1.dp, NavyBorder)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator(color = CrimsonBright)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = stringResource(R.string.loading_prayers),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SilverText
                                        )
                                    }
                                }
                            }
                            is PrayersLoadState.Error -> {
                                EmptyOrErrorCard(
                                    message = loadState.message,
                                    primaryActionLabel = stringResource(R.string.retry),
                                    onPrimaryAction = onRetryFetch
                                )
                            }
                            is PrayersLoadState.Success -> {
                                PrayerTimesListCard(
                                    items = loadState.items,
                                    onPrayerClick = { item -> selectedPrayerForVirtue = item }
                                )
                            }
                        }
                    } else {
                        TimeAdjustmentCard(
                            adjustments = uiState.adjustments,
                            prayerItems = (uiState.prayersState as? PrayersLoadState.Success)?.items.orEmpty(),
                            onUpdateGlobalOffset = onUpdateGlobalOffset,
                            onUpdatePrayerOffset = onUpdatePrayerOffset,
                            onResetAll = onResetAllOffsets
                        )
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
                onRequestLocationPermissionAndDetect()
            },
            onSelectCity = { city ->
                onSelectCityPreset(city)
                showCitySheet = false
            }
        )
    }

    selectedPrayerForVirtue?.let { prayerItem ->
        PrayerVirtueBottomSheet(
            item = prayerItem,
            settings = uiState.settings,
            isAthanPlaying = uiState.isAthanPlaying,
            onAthanToggle = onAthanToggle,
            onMuezzinVoiceChange = onMuezzinVoiceChange,
            onFajrAthanToggle = onFajrAthanToggle,
            onAthanPlayModeChange = onAthanPlayModeChange,
            onTestAthanSound = onTestAthanSound,
            onStopAthan = onStopAthan,
            onDismiss = { selectedPrayerForVirtue = null }
        )
    }
}

@Composable
private fun MandatoryBackgroundSetupBanner(
    hasNotificationPermission: Boolean,
    canScheduleExactAlarms: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    onFixSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val detailText = when {
        !hasNotificationPermission -> stringResource(R.string.home_notif_banner_notif_missing)
        !canScheduleExactAlarms -> stringResource(R.string.home_notif_banner_alarm_missing)
        else -> stringResource(R.string.home_notif_banner_battery_missing)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (!hasNotificationPermission) CrimsonContainer else NavyElevated,
        border = BorderStroke(
            1.dp,
            if (!hasNotificationPermission) CrimsonBright else AmberStar.copy(alpha = 0.85f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_background_setup_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = if (!hasNotificationPermission) CrimsonBright else AmberStar,
                modifier = Modifier.size(22.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_notif_banner_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = IceWhite
                )
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodySmall,
                    color = WarmHighlight
                )
            }
            Button(
                onClick = onFixSetup,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("home_fix_notifications_button")
            ) {
                Text(
                    text = stringResource(R.string.home_notif_banner_action),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HomeSectionTabSwitcher(
    selectedTab: Int,
    hasOffsets: Boolean,
    onSelectTab: (Int) -> Unit
) {
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
            HomeTabPill(
                title = stringResource(R.string.tab_prayer_times),
                icon = Icons.Filled.AccessTime,
                selected = selectedTab == 0,
                onClick = { onSelectTab(0) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("home_tab_prayers")
            )
            HomeTabPill(
                title = if (hasOffsets) "${stringResource(R.string.tab_time_adjust)} •" else stringResource(R.string.tab_time_adjust),
                icon = Icons.Filled.Tune,
                selected = selectedTab == 1,
                onClick = { onSelectTab(1) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("home_tab_adjust")
            )
        }
    }
}

@Composable
private fun HomeTabPill(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) CrimsonRed else Color.Transparent,
        modifier = modifier.minimumInteractiveComponentSize()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 10.dp),
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
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (selected) Color.White else SilverText,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TimeAdjustmentCard(
    adjustments: PrayerAdjustments,
    prayerItems: List<PrayerTimeItem>,
    onUpdateGlobalOffset: (Int) -> Unit,
    onUpdatePrayerOffset: (PrayerType, Int) -> Unit,
    onResetAll: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.time_adjust_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = IceWhite,
                    modifier = Modifier.weight(1f)
                )
                if (adjustments.hasAnyOffset) {
                    TextButton(onClick = onResetAll) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = CrimsonBright,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.time_adjust_reset), color = CrimsonBright)
                    }
                }
            }

            // Global adjustment section
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = NavyPrimary,
                border = BorderStroke(1.dp, NavyBorder)
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
                        Text(
                            text = stringResource(R.string.time_adjust_global),
                            style = MaterialTheme.typography.titleMedium,
                            color = IceWhite
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            val sign = if (adjustments.globalOffsetMinutes > 0) "+" else ""
                            Text(
                                text = "$sign${adjustments.globalOffsetMinutes} min",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = if (adjustments.globalOffsetMinutes != 0) CrimsonBright else SilverText
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(-60 to "-1 س", -5 to "-5 د", -1 to "-1 د", 1 to "+1 د", 5 to "+5 د", 60 to "+1 س").forEach { (delta, label) ->
                            Surface(
                                onClick = { onUpdateGlobalOffset(adjustments.globalOffsetMinutes + delta) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (delta > 0) CrimsonContainer else NavyElevated,
                                border = BorderStroke(1.dp, if (delta > 0) CrimsonRed else NavyBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = IceWhite,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.time_adjust_per_prayer),
                style = MaterialTheme.typography.titleMedium,
                color = WarmHighlight
            )

            PrayerType.entries.forEach { type ->
                val specific = adjustments.specificOffsetFor(type)
                val formatted = prayerItems.find { it.type == type }?.formattedTime ?: "--"
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = NavyPrimary.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = type.arabicName,
                                style = MaterialTheme.typography.titleMedium,
                                color = IceWhite
                            )
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = formatted,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                    color = SilverText
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { onUpdatePrayerOffset(type, specific - 1) },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(NavyElevated)
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = null, tint = IceWhite, modifier = Modifier.size(18.dp))
                            }
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                val sign = if (specific > 0) "+" else ""
                                Text(
                                    text = "$sign$specific د",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = if (specific != 0) CrimsonBright else SilverText,
                                    modifier = Modifier.width(46.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                            IconButton(
                                onClick = { onUpdatePrayerOffset(type, specific + 1) },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(CrimsonRed)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrayerVirtueBottomSheet(
    item: PrayerTimeItem,
    settings: UserSettings,
    isAthanPlaying: Boolean,
    onAthanToggle: (Boolean) -> Unit,
    onMuezzinVoiceChange: (MuezzinVoice) -> Unit,
    onFajrAthanToggle: (Boolean) -> Unit,
    onAthanPlayModeChange: (AthanPlayMode) -> Unit,
    onTestAthanSound: (Boolean) -> Unit,
    onStopAthan: () -> Unit,
    onDismiss: () -> Unit
) {
    val info = remember(item.type) { IslamicContent.virtueFor(item.type) }
    val isFajr = item.type == PrayerType.FAJR
    val isSunrise = item.type == PrayerType.SUNRISE
    var voiceMenuExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NavyDeep
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CrimsonRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = prayerIconFor(item.type),
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = info.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = IceWhite
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = item.formattedTime,
                                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                color = WarmHighlight
                            )
                        }
                    }
                }

                if (item.isNext) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CrimsonContainer,
                        border = BorderStroke(1.dp, CrimsonBright)
                    ) {
                        Text(
                            text = stringResource(R.string.next_prayer_badge),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = CrimsonBright,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = NavyBorder)

            // Athan & Muezzin controls inside the prayer sheet
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = NavyPrimary.copy(alpha = 0.75f)),
                border = BorderStroke(1.dp, if (isAthanPlaying) CrimsonRed else NavyBorder)
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = CrimsonBright,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.setting_athan_section_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = IceWhite
                            )
                        }

                        if (!isSunrise) {
                            Switch(
                                checked = settings.athanEnabled,
                                onCheckedChange = onAthanToggle,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = CrimsonRed
                                ),
                                modifier = Modifier.testTag("prayer_sheet_athan_switch")
                            )
                        }
                    }

                    if (isFajr) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFajrAthanToggle(!settings.fajrAthanEnabled) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.setting_fajr_athan),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = WarmHighlight,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = settings.fajrAthanEnabled,
                                onCheckedChange = onFajrAthanToggle,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = CrimsonRed
                                ),
                                modifier = Modifier.testTag("prayer_sheet_fajr_switch")
                            )
                        }
                        HorizontalDivider(color = NavyBorder)
                    }

                    // Muezzin selector row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { voiceMenuExpanded = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.setting_muezzin_voice),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = IceWhite,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box {
                            Surface(
                                color = NavyDeep,
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, NavyBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isFajr && settings.fajrAthanEnabled) {
                                            MuezzinVoice.FAJR_SPECIAL.arabicLabel
                                        } else {
                                            settings.muezzinVoice.arabicLabel
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = WarmHighlight
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = null,
                                        tint = CrimsonBright
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = voiceMenuExpanded,
                                onDismissRequest = { voiceMenuExpanded = false }
                            ) {
                                MuezzinVoice.entries.forEach { voice ->
                                    DropdownMenuItem(
                                        text = { Text(voice.arabicLabel) },
                                        onClick = {
                                            voiceMenuExpanded = false
                                            if (isFajr && voice != MuezzinVoice.FAJR_SPECIAL && settings.fajrAthanEnabled) {
                                                onFajrAthanToggle(false)
                                            }
                                            onMuezzinVoiceChange(voice)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = NavyBorder)

                    // Full vs Partial playback mode
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.setting_athan_play_mode),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = IceWhite
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AthanPlayMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.athanPlayMode == mode,
                                    onClick = { onAthanPlayModeChange(mode) },
                                    label = {
                                        Text(
                                            text = mode.arabicLabel,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CrimsonRed,
                                        selectedLabelColor = Color.White,
                                        containerColor = NavyDeep,
                                        labelColor = SilverText
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("prayer_sheet_mode_${mode.id}")
                                )
                            }
                        }
                    }

                    if (isAthanPlaying) {
                        Button(
                            onClick = onStopAthan,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("prayer_sheet_stop_athan_button")
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.setting_stop_athan), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onTestAthanSound(isFajr) },
                            border = BorderStroke(1.dp, CrimsonRed),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("prayer_sheet_test_athan_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = CrimsonBright,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isFajr && settings.fajrAthanEnabled) {
                                    stringResource(R.string.setting_test_fajr_athan)
                                } else {
                                    stringResource(R.string.setting_test_athan)
                                },
                                color = IceWhite
                            )
                        }
                    }
                }
            }

            Text(
                text = info.virtueSummary,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                color = IceWhite
            )

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = NavyPrimary,
                border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = AmberStar, modifier = Modifier.size(18.dp))
                        Text(
                            text = info.hadithText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 24.sp
                            ),
                            color = IceWhite
                        )
                    }
                    Text(
                        text = info.hadithSource,
                        style = MaterialTheme.typography.labelSmall,
                        color = WarmHighlight
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.virtue_sunnah_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = CrimsonBright
                )
                Text(
                    text = info.sunnahInfo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SilverText
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.virtue_dhikr_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = AmberStar
                )
                Text(
                    text = info.recommendedDhikr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SilverText
                )
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.close_label), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityPickerBottomSheet(
    isSearching: Boolean = false,
    searchResults: List<PlaceSearchResult> = emptyList(),
    onSearchQueryChange: (String) -> Unit = {},
    onSelectSearchPlace: (PlaceSearchResult) -> Unit = {},
    onDismiss: () -> Unit,
    onDetectGps: () -> Unit,
    onSelectCity: (CityPreset) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredPresets = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) CityPreset.MAJOR_CITIES
        else CityPreset.MAJOR_CITIES.filter {
            it.nameAr.contains(q, ignoreCase = true) || it.countryAr.contains(q, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NavyDeep
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.location_choose_city),
                style = MaterialTheme.typography.headlineMedium,
                color = IceWhite,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onSearchQueryChange(it)
                },
                placeholder = { Text(stringResource(R.string.location_search_hint), color = SilverText) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = CrimsonBright) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CrimsonBright,
                    unfocusedBorderColor = NavyBorder,
                    focusedContainerColor = NavyPrimary,
                    unfocusedContainerColor = NavyPrimary,
                    focusedTextColor = IceWhite,
                    unfocusedTextColor = IceWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("city_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onDetectGps,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("detect_gps_location_button"),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.location_gps_action))
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (isSearching) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CrimsonBright, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("جاري البحث...", style = MaterialTheme.typography.bodyMedium, color = SilverText)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (query.trim().length >= 2 && searchResults.isNotEmpty()) {
                    items(searchResults, key = { "${it.latitude},${it.longitude},${it.displayName}" }) { place ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onSelectSearchPlace(place) },
                            color = NavyElevated.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.5f)),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = place.shortName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = IceWhite
                                )
                                Text(
                                    text = place.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SilverText,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }

                items(filteredPresets, key = { it.nameAr }) { city ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onSelectCity(city) }
                            .testTag("city_item_${city.nameAr}"),
                        color = NavyPrimary.copy(alpha = 0.8f),
                        border = BorderStroke(1.dp, NavyBorder),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = city.nameAr,
                                style = MaterialTheme.typography.titleMedium,
                                color = IceWhite
                            )
                            Text(
                                text = city.countryAr,
                                style = MaterialTheme.typography.bodyMedium,
                                color = WarmHighlight
                            )
                        }
                    }
                }

                if (filteredPresets.isEmpty() && searchResults.isEmpty() && !isSearching) {
                    item {
                        Text(
                            text = stringResource(R.string.location_search_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SilverText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveOccasionHomeBanner(
    occasion: ReligiousOccasion,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_active_occasion_banner"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = NavyDeep.copy(alpha = 0.88f)),
        border = BorderStroke(1.5.dp, AmberStar.copy(alpha = 0.85f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            AmberStar.copy(alpha = 0.22f),
                            CrimsonContainer.copy(alpha = 0.45f),
                            NavyPrimary.copy(alpha = 0.70f)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AmberStar.copy(alpha = 0.20f))
                        .border(1.dp, AmberStar, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = AmberStar,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AmberStar
                    ) {
                        Text(
                            text = if (occasion.isEid) {
                                "${occasion.hijriDateLabel} • اليوم ${occasion.activeDayNumber} من ${occasion.durationDays}"
                            } else {
                                stringResource(R.string.occasion_banner_tag)
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = NavyDeep,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = occasion.activeBannerTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = IceWhite
                    )
                    Text(
                        text = occasion.greetingMessageAr,
                        style = MaterialTheme.typography.bodySmall,
                        color = WarmHighlight
                    )
                }
            }
        }
    }
}

@Composable
private fun BroadcastRemindersHomeSection(
    reminders: List<BroadcastReminder>,
    playbackState: AthkarAudioPlaybackState,
    onPlayAudio: (BroadcastReminder) -> Unit,
    onStopAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        reminders.forEach { reminder ->
            val isThisPlaying = playbackState.isPlaying && playbackState.remoteId == reminder.id
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = NavyDeep.copy(alpha = 0.86f)),
                border = BorderStroke(
                    1.dp,
                    if (isThisPlaying) AmberStar else if (reminder.isPinned) CrimsonBright.copy(alpha = 0.7f) else NavyBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_broadcast_reminder_${reminder.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (reminder.isPinned) CrimsonContainer else NavyElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (reminder.isPinned) Icons.Filled.PushPin else Icons.Filled.Campaign,
                            contentDescription = null,
                            tint = if (reminder.isPinned) AmberStar else CrimsonBright,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = reminder.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = IceWhite
                            )
                            if (reminder.isDaily) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = NavyPrimary,
                                    border = BorderStroke(1.dp, NavyBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Schedule,
                                            contentDescription = null,
                                            tint = WarmHighlight,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "يومياً ${reminder.allScheduledTimes.joinToString(" • ")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = WarmHighlight
                                        )
                                    }
                                }
                            }
                        }

                        if (reminder.body.isNotBlank()) {
                            Text(
                                text = reminder.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = SilverText
                            )
                        }
                    }

                    if (reminder.isAudio) {
                        Button(
                            onClick = { if (isThisPlaying) onStopAudio() else onPlayAudio(reminder) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isThisPlaying) CrimsonRed else NavyElevated
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isThisPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = if (isThisPlaying) "إيقاف" else "تشغيل",
                                tint = IceWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isThisPlaying) "إيقاف" else "تشغيل",
                                style = MaterialTheme.typography.labelMedium,
                                color = IceWhite
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavyHeader(
    locationLabel: String,
    cityLocalTime: String,
    hasLocation: Boolean,
    hijriDate: String,
    hijriDay: Int,
    gregorianDate: String,
    onLocationClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)
    ) {
        // Explicit RTL-safe ordering: Right = Title + Dates, Left = Medium Realistic Moon + Phase label
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Physical Left: Realistic Moon + phase text underneath
                RealisticHijriMoonIndicator(
                    hijriDay = hijriDay,
                    modifier = Modifier.padding(end = 10.dp)
                )

                // Physical Right: App branding + Hijri & Gregorian dates (RTL)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(CrimsonRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mosque,
                                    contentDescription = stringResource(R.string.app_name),
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = IceWhite
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = hijriDate,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = WarmHighlight
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = gregorianDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = SilverText
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onLocationClick,
                shape = RoundedCornerShape(16.dp),
                color = NavyElevated.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.7f)),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("location_picker_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = stringResource(R.string.location_detect),
                        tint = CrimsonBright,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = locationLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = IceWhite
                    )
                }
            }

            if (hasLocation && cityLocalTime != "--") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianBlack.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, NavyBorder)
                ) {
                    Text(
                        text = stringResource(R.string.city_local_time_format, locationLabel, cityLocalTime),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = IceWhite,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NextPrayerHeroCard(
    uiState: MuezzinUiState,
    onLocationClick: () -> Unit,
    onChooseCityClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val prayersState = uiState.prayersState) {
            is PrayersLoadState.Success -> {
                val status = prayersState.nextPrayerStatus
                MinaretCountdownGauge(
                    nextPrayerName = status.nextPrayer.arabicName,
                    countdownText = status.countdownFormatted,
                    progressFraction = status.progress
                )
            }
            is PrayersLoadState.Loading -> {
                Spacer(modifier = Modifier.height(10.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = CrimsonBright
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.loading_prayers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SilverText
                )
            }
            else -> {
                MinaretCountdownGauge(
                    nextPrayerName = null,
                    countdownText = "--:--:--",
                    progressFraction = 0.35f
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onLocationClick,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        modifier = Modifier.testTag("hero_gps_button")
                    ) {
                        Text(stringResource(R.string.location_detect))
                    }
                    OutlinedButton(
                        onClick = onChooseCityClick,
                        border = BorderStroke(1.dp, NavyBorder),
                        modifier = Modifier.testTag("hero_choose_city_button")
                    ) {
                        Text(stringResource(R.string.location_choose_city), color = IceWhite)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrayerTimesListCard(
    items: List<PrayerTimeItem>,
    onPrayerClick: (PrayerTimeItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = NavyDeep.copy(alpha = 0.72f)),
        border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.forEach { item ->
                PrayerRow(
                    item = item,
                    onClick = { onPrayerClick(item) }
                )
            }
        }
    }
}

@Composable
private fun PrayerRow(
    item: PrayerTimeItem,
    onClick: () -> Unit
) {
    val active = item.isNext
    val rowShape = MaterialTheme.shapes.medium
    val bgBrush = if (active) {
        Brush.linearGradient(listOf(CrimsonRed, CrimsonDark, NavyPrimary))
    } else {
        Brush.linearGradient(listOf(NavyPrimary.copy(alpha = 0.75f), ObsidianBlack.copy(alpha = 0.65f)))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (active) {
                    Modifier
                        .shadow(elevation = 10.dp, shape = rowShape)
                        .border(width = 2.dp, color = CrimsonBright, shape = rowShape)
                } else {
                    Modifier.border(width = 1.dp, color = NavyBorder.copy(alpha = 0.5f), shape = rowShape)
                }
            )
            .clip(rowShape)
            .background(bgBrush)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = if (active) 12.dp else 9.dp)
            .testTag("prayer_row_${item.type.apiKey}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (active) 38.dp else 34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) Color.White else NavyElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = prayerIconFor(item.type),
                    contentDescription = item.type.arabicName,
                    tint = if (active) CrimsonRed else SilverText,
                    modifier = Modifier.size(22.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = item.type.arabicName,
                    style = if (active) {
                        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    color = IceWhite
                )
                if (active) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianBlack
                    ) {
                        Text(
                            text = stringResource(R.string.next_prayer_badge),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = WarmHighlight,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = item.formattedTime,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = if (active) 21.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                color = if (active) Color.White else WarmHighlight.copy(alpha = 0.9f)
            )
        }
    }
}

private fun prayerIconFor(type: PrayerType): ImageVector = when (type) {
    PrayerType.FAJR -> Icons.Filled.WbTwilight
    PrayerType.SUNRISE -> Icons.Outlined.Brightness5
    PrayerType.DHUHR -> Icons.Filled.WbSunny
    PrayerType.ASR -> Icons.Outlined.WbCloudy
    PrayerType.MAGHRIB -> Icons.Filled.WbTwilight
    PrayerType.ISHA -> Icons.Filled.NightsStay
}

@Composable
private fun EmptyOrErrorCard(
    message: String,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = SilverText,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPrimaryAction,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) {
                    Text(primaryActionLabel)
                }
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    OutlinedButton(
                        onClick = onSecondaryAction,
                        border = BorderStroke(1.dp, NavyBorder)
                    ) {
                        Text(secondaryActionLabel, color = IceWhite)
                    }
                }
            }
        }
    }
}
