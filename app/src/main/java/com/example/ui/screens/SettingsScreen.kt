package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ReligiousOccasion
import com.example.ui.theme.AmberStar
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.IceWhite
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SilverText
import com.example.ui.theme.WarmHighlight

@Composable
fun SettingsScreen(
    isRamadanTabActive: Boolean,
    religiousOccasions: List<ReligiousOccasion>,
    hasNotificationPermission: Boolean,
    canScheduleExactAlarms: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    onToggleRamadanTab: (Boolean) -> Unit,
    onOpenRamadanSection: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onSendTestNotification: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 0 = الإعدادات العامة, 1 = المناسبات الدينية
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val hasActiveOccasionToday = religiousOccasions.any { it.isActiveToday }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineLarge,
                color = IceWhite
            )

            SettingsTabSwitcher(
                selectedTab = selectedTab,
                hasActiveOccasionToday = hasActiveOccasionToday,
                onSelectTab = { selectedTab = it }
            )

            if (selectedTab == 0) {
                BackgroundNotificationsCard(
                    hasNotificationPermission = hasNotificationPermission,
                    canScheduleExactAlarms = canScheduleExactAlarms,
                    isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                    onRequestNotifications = onRequestNotifications,
                    onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                    onOpenBatterySettings = onOpenBatterySettings,
                    onSendTestNotification = onSendTestNotification
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = NavyDeep.copy(alpha = 0.76f)),
                    border = BorderStroke(
                        1.dp,
                        if (isRamadanTabActive) CrimsonRed else NavyBorder
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NightsStay,
                                contentDescription = null,
                                tint = AmberStar,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = stringResource(R.string.setting_ramadan_section_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = IceWhite
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!isRamadanTabActive) {
                                Button(
                                    onClick = { onToggleRamadanTab(true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("ramadan_activate_tab_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.setting_ramadan_activate_btn),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onToggleRamadanTab(false) },
                                    border = BorderStroke(1.dp, CrimsonRed),
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("ramadan_deactivate_tab_button")
                                ) {
                                    Text(
                                        text = stringResource(R.string.setting_ramadan_deactivate_btn),
                                        color = CrimsonBright,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = onOpenRamadanSection,
                                border = BorderStroke(1.dp, NavyBorder),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("open_ramadan_from_settings_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.setting_ramadan_open_btn),
                                    color = WarmHighlight,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            } else {
                UpcomingOccasionsCard(occasions = religiousOccasions)
            }
        }
    }
}

@Composable
private fun BackgroundNotificationsCard(
    hasNotificationPermission: Boolean,
    canScheduleExactAlarms: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onSendTestNotification: () -> Unit
) {
    val allReady = hasNotificationPermission && canScheduleExactAlarms && isIgnoringBatteryOptimizations
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_background_notifications_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = NavyDeep.copy(alpha = 0.80f)),
        border = BorderStroke(1.dp, if (allReady) AmberStar.copy(alpha = 0.65f) else CrimsonRed)
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
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = if (allReady) AmberStar else CrimsonBright,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.setting_background_section_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = IceWhite
                    )
                    Text(
                        text = stringResource(R.string.setting_background_section_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverText
                    )
                }
            }

            BackgroundCapabilityRow(
                icon = Icons.Filled.NotificationsActive,
                title = stringResource(R.string.setting_bg_notif_row),
                isReady = hasNotificationPermission,
                onActionClick = onRequestNotifications,
                buttonTag = "settings_grant_notifications_button"
            )
            BackgroundCapabilityRow(
                icon = Icons.Filled.Schedule,
                title = stringResource(R.string.setting_bg_exact_alarm_row),
                isReady = canScheduleExactAlarms,
                onActionClick = onOpenExactAlarmSettings,
                buttonTag = "settings_grant_exact_alarm_button"
            )
            BackgroundCapabilityRow(
                icon = Icons.Filled.BatteryChargingFull,
                title = stringResource(R.string.setting_bg_battery_row),
                isReady = isIgnoringBatteryOptimizations,
                onActionClick = onOpenBatterySettings,
                buttonTag = "settings_grant_battery_button"
            )

            OutlinedButton(
                onClick = onSendTestNotification,
                border = BorderStroke(1.dp, WarmHighlight.copy(alpha = 0.55f)),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("settings_test_notification_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = WarmHighlight,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.setting_notifications_test_btn),
                    color = WarmHighlight,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BackgroundCapabilityRow(
    icon: ImageVector,
    title: String,
    isReady: Boolean,
    onActionClick: () -> Unit,
    buttonTag: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = NavyPrimary.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, if (isReady) AmberStar.copy(alpha = 0.45f) else NavyBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isReady) AmberStar else CrimsonBright,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = IceWhite,
                modifier = Modifier.weight(1f)
            )
            if (isReady) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = AmberStar.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, AmberStar.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = AmberStar,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.setting_bg_status_ready),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = AmberStar
                        )
                    }
                }
            } else {
                Button(
                    onClick = onActionClick,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag(buttonTag)
                ) {
                    Text(
                        text = stringResource(R.string.setting_bg_status_action),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTabSwitcher(
    selectedTab: Int,
    hasActiveOccasionToday: Boolean,
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
            SettingsTabPill(
                title = stringResource(R.string.settings_tab_general),
                icon = Icons.Filled.Settings,
                selected = selectedTab == 0,
                highlightDot = false,
                onClick = { onSelectTab(0) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("settings_tab_general")
            )
            SettingsTabPill(
                title = stringResource(R.string.settings_tab_occasions),
                icon = Icons.Filled.Event,
                selected = selectedTab == 1,
                highlightDot = hasActiveOccasionToday,
                onClick = { onSelectTab(1) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("settings_tab_occasions")
            )
        }
    }
}

@Composable
private fun SettingsTabPill(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    highlightDot: Boolean,
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
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else if (highlightDot) AmberStar else SilverText,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (selected) Color.White else SilverText,
                maxLines = 1
            )
            if (highlightDot && !selected) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(AmberStar)
                )
            }
        }
    }
}

@Composable
private fun UpcomingOccasionsCard(occasions: List<ReligiousOccasion>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_religious_occasions_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = NavyDeep.copy(alpha = 0.78f)),
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
                Icon(
                    imageVector = Icons.Filled.Event,
                    contentDescription = null,
                    tint = CrimsonBright,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.setting_occasions_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = IceWhite
                    )
                    Text(
                        text = stringResource(R.string.setting_occasions_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverText
                    )
                }
            }

            occasions.forEach { occasion ->
                ReligiousOccasionRow(occasion = occasion)
            }
        }
    }
}

@Composable
private fun ReligiousOccasionRow(occasion: ReligiousOccasion) {
    val isToday = occasion.isActiveToday
    val redStrength = occasion.redIntensity

    val containerColor = if (isToday) {
        AmberStar.copy(alpha = 0.16f)
    } else {
        lerp(
            start = NavyPrimary.copy(alpha = 0.65f),
            stop = CrimsonContainer.copy(alpha = 0.94f),
            fraction = redStrength
        )
    }

    val borderColor = if (isToday) {
        AmberStar.copy(alpha = 0.85f)
    } else {
        lerp(
            start = CrimsonRed.copy(alpha = 0.24f),
            stop = CrimsonBright.copy(alpha = 0.95f),
            fraction = redStrength
        )
    }

    val badgeContainerColor = if (isToday) {
        AmberStar
    } else {
        CrimsonRed.copy(alpha = (0.16f + 0.78f * redStrength).coerceIn(0.16f, 0.94f))
    }

    val badgeBorderColor = if (isToday) {
        AmberStar
    } else {
        CrimsonBright.copy(alpha = (0.28f + 0.68f * redStrength).coerceIn(0.28f, 0.96f))
    }

    val badgeTextColor = if (isToday) {
        NavyDeep
    } else {
        lerp(WarmHighlight, Color.White, redStrength)
    }

    val dotColor = if (isToday) {
        AmberStar
    } else {
        lerp(WarmHighlight.copy(alpha = 0.45f), CrimsonBright, redStrength)
    }

    val countdownText = when {
        isToday && occasion.isEid -> "${stringResource(R.string.occasion_today_badge)} (${occasion.activeDayNumber}/${occasion.durationDays})"
        isToday -> stringResource(R.string.occasion_today_badge)
        occasion.daysRemaining == 1 -> stringResource(R.string.occasion_tomorrow_badge)
        occasion.daysRemaining == 2 -> "باقي يومان"
        occasion.daysRemaining in 3..10 -> "باقي ${occasion.daysRemaining} أيام"
        else -> "باقي ${occasion.daysRemaining} يوماً"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("occasion_item_${occasion.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (isToday) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = AmberStar,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }

                Column {
                    Text(
                        text = if (isToday && occasion.isEid) occasion.activeBannerTitle else occasion.titleAr,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = IceWhite
                    )
                    Text(
                        text = occasion.hijriDateLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isToday) AmberStar else SilverText
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = badgeContainerColor,
                border = BorderStroke(1.dp, badgeBorderColor)
            ) {
                Text(
                    text = countdownText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = badgeTextColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

