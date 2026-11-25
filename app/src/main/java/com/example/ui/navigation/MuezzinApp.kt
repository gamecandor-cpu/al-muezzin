package com.example.ui.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.screens.AthkarHubScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.QiblaScreen
import com.example.ui.screens.QuranScreen
import com.example.ui.screens.RamadanScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AmberStar
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.IceWhite
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SilverText
import com.example.ui.theme.VoidBlack
import com.example.ui.theme.WarmHighlight
import com.example.ui.viewmodel.MuezzinViewModel
import kotlinx.coroutines.delay

enum class MuezzinDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    QURAN("quran", R.string.nav_quran, Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
    QIBLA("qibla", R.string.nav_qibla, Icons.Filled.Explore, Icons.Outlined.Explore),
    ATHKAR("athkar", R.string.nav_athkar, Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    RAMADAN("ramadan", R.string.nav_ramadan, Icons.Filled.NightsStay, Icons.Outlined.NightsStay),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun MuezzinApp(
    launchIntent: Intent? = null,
    muezzinViewModel: MuezzinViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: MuezzinDestination.HOME.route

    val uiState by muezzinViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var autoLocationAttempted by remember { mutableStateOf(false) }
    var notificationsPromptResolved by remember { mutableStateOf(false) }
    var showMandatoryNotifDialog by remember { mutableStateOf(false) }
    var showLaunchSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1450L)
        showLaunchSplash = false
    }

    LaunchedEffect(Unit) {
        muezzinViewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(launchIntent) {
        muezzinViewModel.handleLaunchIntent(launchIntent)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        muezzinViewModel.refreshBackgroundCapabilities()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        muezzinViewModel.onNotificationPermissionResult(isGranted)
        notificationsPromptResolved = true
        showMandatoryNotifDialog = !isGranted
    }

    val openAppNotificationSettings: () -> Unit = {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                )
            }
        }
    }

    val requestOrOpenNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val alreadyGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (alreadyGranted) {
                muezzinViewModel.refreshBackgroundCapabilities()
                showMandatoryNotifDialog = false
            } else {
                val launchPrefs = context.getSharedPreferences("muezzin_launch_prefs", Context.MODE_PRIVATE)
                val attempts = launchPrefs.getInt("notif_prompt_attempts", 0)
                if (attempts < 2) {
                    launchPrefs.edit().putInt("notif_prompt_attempts", attempts + 1).apply()
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openAppNotificationSettings()
                }
            }
        } else {
            openAppNotificationSettings()
        }
    }

    val openExactAlarmSettings: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            runCatching { context.startActivity(intent) }.onFailure {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                }
            }
        }
    }

    val openBatterySettings: () -> Unit = {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                )
            }
        }
    }

    LaunchedEffect(showLaunchSplash) {
        if (!showLaunchSplash && !notificationsPromptResolved) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val alreadyGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!alreadyGranted) {
                    val launchPrefs = context.getSharedPreferences("muezzin_launch_prefs", Context.MODE_PRIVATE)
                    val attempts = launchPrefs.getInt("notif_prompt_attempts", 0)
                    if (attempts < 2) {
                        launchPrefs.edit().putInt("notif_prompt_attempts", attempts + 1).apply()
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        muezzinViewModel.refreshBackgroundCapabilities()
                        notificationsPromptResolved = true
                        showMandatoryNotifDialog = true
                    }
                } else {
                    muezzinViewModel.refreshBackgroundCapabilities()
                    notificationsPromptResolved = true
                    showMandatoryNotifDialog = false
                }
            } else {
                muezzinViewModel.refreshBackgroundCapabilities()
                notificationsPromptResolved = true
                showMandatoryNotifDialog = !uiState.hasNotificationPermission
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            muezzinViewModel.detectCurrentLocation()
        }
    }

    val requestLocationAndDetect: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            muezzinViewModel.detectCurrentLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    LaunchedEffect(uiState.coordinates, notificationsPromptResolved) {
        if (notificationsPromptResolved && !autoLocationAttempted && uiState.coordinates == null) {
            autoLocationAttempted = true
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                muezzinViewModel.detectCurrentLocation()
            }
        }
    }

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val visibleDestinations = remember(uiState.isRamadanActive) {
        if (uiState.isRamadanActive) {
            MuezzinDestination.entries
        } else {
            MuezzinDestination.entries.filter { it != MuezzinDestination.RAMADAN }
        }
    }

    val openRamadanFromSettings: () -> Unit = {
        navController.navigate(MuezzinDestination.RAMADAN.route) {
            launchSingleTop = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
    ) {
        val useNavRail = maxWidth >= 600.dp
        val isHomeScreen = currentRoute == MuezzinDestination.HOME.route

        // Apply mandala background across all secondary pages (except HomeScreen)
        if (!isHomeScreen) {
            Image(
                painter = painterResource(id = R.drawable.bg_mandala_navy),
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
                                Color.Black.copy(alpha = 0.14f),
                                ObsidianBlack.copy(alpha = 0.34f),
                                ObsidianBlack.copy(alpha = 0.52f)
                            )
                        )
                    )
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = NavyDeep.copy(alpha = 0.90f)
                    ) {
                        visibleDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route || (
                                dest == MuezzinDestination.SETTINGS &&
                                    currentRoute == MuezzinDestination.RAMADAN.route &&
                                    !uiState.isRamadanActive
                                )
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        fontSize = if (visibleDestinations.size > 5) 9.5.sp else 11.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CrimsonBright,
                                    selectedTextColor = CrimsonBright,
                                    unselectedIconColor = SilverText,
                                    unselectedTextColor = SilverText,
                                    indicatorColor = CrimsonContainer
                                ),
                                modifier = Modifier.testTag("nav_tab_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = NavyDeep.copy(alpha = 0.90f)
                    ) {
                        visibleDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route || (
                                dest == MuezzinDestination.SETTINGS &&
                                    currentRoute == MuezzinDestination.RAMADAN.route &&
                                    !uiState.isRamadanActive
                                )
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("nav_rail_${dest.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = MuezzinDestination.HOME.route,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(MuezzinDestination.HOME.route) {
                        HomeScreen(
                            uiState = uiState,
                            onRequestLocationPermissionAndDetect = requestLocationAndDetect,
                            onSelectCityPreset = muezzinViewModel::selectCityPreset,
                            onSearchPlaces = muezzinViewModel::searchPlaces,
                            onSelectSearchPlace = muezzinViewModel::selectSearchPlace,
                            onRetryFetch = muezzinViewModel::retryFetchPrayerTimes,
                            onUpdateGlobalOffset = muezzinViewModel::updateGlobalOffset,
                            onUpdatePrayerOffset = muezzinViewModel::updatePrayerOffset,
                            onResetAllOffsets = muezzinViewModel::resetAllOffsets,
                            onAthanToggle = muezzinViewModel::toggleAthanEnabled,
                            onMuezzinVoiceChange = muezzinViewModel::updateMuezzinVoice,
                            onFajrAthanToggle = muezzinViewModel::toggleFajrAthanEnabled,
                            onAthanPlayModeChange = muezzinViewModel::updateAthanPlayMode,
                            onTestAthanSound = muezzinViewModel::triggerAthanAlert,
                            onStopAthan = muezzinViewModel::stopAthanPlayback,
                            onStopAthkarAudio = muezzinViewModel::stopAthkarAudio,
                            onPlayRemoteReminderAudio = muezzinViewModel::playRemoteReminderAudio,
                            onRequestNotifications = requestOrOpenNotifications,
                            onOpenExactAlarmSettings = openExactAlarmSettings,
                            onOpenBatterySettings = openBatterySettings
                        )
                    }
                    composable(MuezzinDestination.QURAN.route) {
                        QuranScreen(
                            quranState = uiState.quranState,
                            quranPrefs = uiState.quranPrefs,
                            onEnsureLoaded = muezzinViewModel::ensureQuranLoaded,
                            onPageSettled = muezzinViewModel::updateQuranLastPage,
                            onToggleBookmark = muezzinViewModel::toggleQuranBookmark,
                            onFontSizeChange = muezzinViewModel::updateQuranFontSize,
                            onPaperModeChange = muezzinViewModel::updateQuranPaperMode,
                            onSearchQueryChange = muezzinViewModel::searchQuran
                        )
                    }
                    composable(MuezzinDestination.QIBLA.route) {
                        QiblaScreen(
                            uiState = uiState,
                            onRequestLocation = requestLocationAndDetect,
                            onSelectCityPreset = muezzinViewModel::selectCityPreset,
                            onSearchPlaces = muezzinViewModel::searchPlaces,
                            onSelectSearchPlace = muezzinViewModel::selectSearchPlace
                        )
                    }
                    composable(MuezzinDestination.ATHKAR.route) {
                        AthkarHubScreen(
                            athkarCounts = uiState.athkarCounts,
                            tasbihState = uiState.tasbihState,
                            athkarAudioState = uiState.athkarAudioState,
                            athkarAutoEnabled = uiState.athkarAutoEnabled,
                            athkarScheduleConfig = uiState.athkarScheduleConfig,
                            timeFormat = uiState.settings.timeFormat,
                            morningScheduleLabel = uiState.morningAthkarScheduleLabel,
                            eveningScheduleLabel = uiState.eveningAthkarScheduleLabel,
                            broadcastReminders = uiState.broadcastReminders,
                            onIncrementAthkar = muezzinViewModel::incrementAthkar,
                            onResetAthkarItem = muezzinViewModel::resetAthkarItem,
                            onResetAllAthkar = muezzinViewModel::resetAllAthkar,
                            onPlayAthkarAudio = muezzinViewModel::playAthkarAudio,
                            onPlayRemoteReminderAudio = muezzinViewModel::playRemoteReminderAudio,
                            onStopAthkarAudio = muezzinViewModel::stopAthkarAudio,
                            onToggleAthkarAuto = muezzinViewModel::toggleAthkarAutoPlay,
                            onUpdateAthkarSchedule = muezzinViewModel::updateAthkarScheduleConfig,
                            onTasbihIncrement = muezzinViewModel::incrementTasbih,
                            onTasbihReset = muezzinViewModel::resetTasbih,
                            onTasbihSelectDhikr = muezzinViewModel::selectDhikr,
                            onTasbihCycleTarget = muezzinViewModel::cycleTasbihTarget
                        )
                    }
                    composable(MuezzinDestination.RAMADAN.route) {
                        RamadanScreen(
                            uiState = uiState,
                            onUpdateImsakAlarm = muezzinViewModel::updateRamadanImsakAlarm,
                            onUpdateIftarAlarm = muezzinViewModel::updateRamadanIftarAlarm,
                            onTestImsakAlarm = muezzinViewModel::testImsakAlarm,
                            onTestIftarAlarm = muezzinViewModel::testIftarAlarm,
                            onAdjustQadaRemaining = muezzinViewModel::adjustQadaRemaining,
                            onMarkQadaDone = muezzinViewModel::markQadaDayDone,
                            onResetQada = muezzinViewModel::resetQada,
                            showSettingsHeaderBar = !uiState.isRamadanActive,
                            onBackToSettings = { navController.popBackStack() },
                            onActivateRamadanTab = { muezzinViewModel.setRamadanTabEnabled(true) }
                        )
                    }
                    composable(MuezzinDestination.SETTINGS.route) {
                        SettingsScreen(
                            isRamadanTabActive = uiState.isRamadanActive,
                            religiousOccasions = uiState.religiousOccasions,
                            hasNotificationPermission = uiState.hasNotificationPermission,
                            canScheduleExactAlarms = uiState.canScheduleExactAlarms,
                            isIgnoringBatteryOptimizations = uiState.isIgnoringBatteryOptimizations,
                            onToggleRamadanTab = muezzinViewModel::setRamadanTabEnabled,
                            onOpenRamadanSection = openRamadanFromSettings,
                            onRequestNotifications = requestOrOpenNotifications,
                            onOpenExactAlarmSettings = openExactAlarmSettings,
                            onOpenBatterySettings = openBatterySettings,
                            onSendTestNotification = muezzinViewModel::sendTestNotification
                        )
                    }
                }
            }
        }

        if (showMandatoryNotifDialog && !uiState.hasNotificationPermission && !showLaunchSplash) {
            AlertDialog(
                onDismissRequest = { showMandatoryNotifDialog = false },
                containerColor = NavyDeep,
                titleContentColor = IceWhite,
                textContentColor = SilverText,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = CrimsonBright,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.mandatory_notif_dialog_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.mandatory_notif_dialog_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showMandatoryNotifDialog = false
                            requestOrOpenNotifications()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonBright),
                        modifier = Modifier.testTag("mandatory_enable_notifications_button")
                    ) {
                        Text(
                            text = stringResource(R.string.mandatory_notif_dialog_btn),
                            color = IceWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showMandatoryNotifDialog = false },
                        modifier = Modifier.testTag("mandatory_notifications_later_button")
                    ) {
                        Text(
                            text = stringResource(R.string.mandatory_notif_dialog_later),
                            color = SilverText
                        )
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = showLaunchSplash,
            exit = fadeOut(animationSpec = tween(durationMillis = 450))
        ) {
            LaunchSplashOverlay()
        }
    }
}

@Composable
private fun LaunchSplashOverlay() {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        revealed = true
    }

    val iconScale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.88f,
        animationSpec = tween(durationMillis = 650),
        label = "splash_icon_scale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 550),
        label = "splash_content_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("launch_splash_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.bg_mandala_navy),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            ObsidianBlack.copy(alpha = 0.45f)
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(iconScale)
                .alpha(contentAlpha)
        ) {
            // Luminous circular badge so the full dark-navy crescent & warm sunset mosque icon shines clearly on entry
            Box(
                modifier = Modifier.size(248.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    AmberStar.copy(alpha = 0.34f),
                                    CrimsonBright.copy(alpha = 0.16f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Surface(
                    shape = CircleShape,
                    color = IceWhite,
                    shadowElevation = 20.dp,
                    modifier = Modifier.size(196.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_muezzin_logo_fg),
                            contentDescription = stringResource(R.string.app_name),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = IceWhite
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 3.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                WarmHighlight.copy(alpha = 0.85f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}
