package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.QuranPrefs
import com.example.data.local.QuranRepository
import com.example.data.local.SavedLocation
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.AladhanTimings
import com.example.data.model.AsrSchool
import com.example.data.model.AthanPlayMode
import com.example.data.model.CalculationMethod
import com.example.data.model.CityPreset
import com.example.data.model.IslamicContent
import com.example.data.model.MuezzinVoice
import com.example.data.model.PrayerAdjustments
import com.example.data.model.PrayerTimeItem
import com.example.data.model.PrayerType
import com.example.data.model.QadaState
import com.example.data.model.QuranAyah
import com.example.data.model.QuranJuzMeta
import com.example.data.model.QuranPageData
import com.example.data.model.QuranSurahMeta
import com.example.data.model.RamadanSettings
import com.example.data.model.ReligiousOccasion
import com.example.data.model.TasbihState
import com.example.data.model.TimeFormat
import com.example.data.model.UserSettings
import com.example.data.remote.AladhanApiService
import com.example.data.remote.BroadcastReminder
import com.example.data.remote.CloudSyncRepository
import com.example.data.remote.NominatimApiService
import com.example.util.AthanPlayer
import com.example.util.AthkarAudioPlaybackState
import com.example.util.AthkarAudioType
import com.example.util.AthkarScheduleConfig
import com.example.util.CelestialPhase
import com.example.util.Coordinates
import com.example.util.LocationHelper
import com.example.util.MorningEveningAthkarService
import com.example.util.NextPrayerStatus
import com.example.util.PrayerNotificationReceiver
import com.example.util.PrayerNotificationScheduler
import com.example.util.PrayerTimeCalculator
import com.example.util.QiblaSensorManager
import com.example.util.RamadanAlarmReceiver
import com.example.util.RamadanAlarmScheduler
import com.example.util.RamadanStatus
import com.example.util.RemoteUploadedAudioStore
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val TAG = "MuezzinViewModel"

sealed interface PrayersLoadState {
    data object NeedsLocation : PrayersLoadState
    data object Loading : PrayersLoadState
    data class Success(
        val items: List<PrayerTimeItem>,
        val nextPrayerStatus: NextPrayerStatus
    ) : PrayersLoadState
    data class Error(val message: String) : PrayersLoadState
}

data class PlaceSearchResult(
    val displayName: String,
    val shortName: String,
    val countryName: String,
    val latitude: Double,
    val longitude: Double
)

data class QuranUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val surahs: List<QuranSurahMeta> = emptyList(),
    val pages: Map<Int, QuranPageData> = emptyMap(),
    val juzList: List<QuranJuzMeta> = emptyList(),
    val searchResults: List<QuranAyah> = emptyList()
)

data class MuezzinUiState(
    val locationName: String? = null,
    val coordinates: Coordinates? = null,
    val timeZoneId: String? = null,
    val cityLocalTime: String = "--",
    val isDetectingLocation: Boolean = false,
    val isSearchingPlaces: Boolean = false,
    val placeSearchResults: List<PlaceSearchResult> = emptyList(),
    val hijriDate: String = "--",
    val hijriDay: Int = PrayerTimeCalculator.currentHijriDayFallback(),
    val hijriMonth: Int = PrayerTimeCalculator.currentHijriMonthFallback(),
    val hijriYear: Int = PrayerTimeCalculator.currentHijriYearFallback(),
    val religiousOccasions: List<ReligiousOccasion> = PrayerTimeCalculator.computeReligiousOccasions(
        PrayerTimeCalculator.currentHijriYearFallback(),
        PrayerTimeCalculator.currentHijriMonthFallback(),
        PrayerTimeCalculator.currentHijriDayFallback()
    ),
    val isRamadanMonth: Boolean = PrayerTimeCalculator.isRamadanMonth(
        PrayerTimeCalculator.currentHijriMonthFallback()
    ),
    val gregorianDate: String = "--",
    val fallbackCelestialPhase: CelestialPhase = PrayerTimeCalculator.resolveCelestialPhaseByClock(),
    val prayersState: PrayersLoadState = PrayersLoadState.NeedsLocation,
    val adjustments: PrayerAdjustments = PrayerAdjustments(),
    val settings: UserSettings = UserSettings(),
    val tasbihState: TasbihState = TasbihState(),
    val athkarCounts: Map<String, Int> = emptyMap(),
    val ramadanSettings: RamadanSettings = RamadanSettings(),
    val ramadanStatus: RamadanStatus? = null,
    val qadaState: QadaState = QadaState(),
    val quranState: QuranUiState = QuranUiState(),
    val quranPrefs: QuranPrefs = QuranPrefs(),
    val qiblaBearing: Float? = null,
    val deviceHeading: Float? = null,
    val hasCompassSensor: Boolean = true,
    val isAthanPlaying: Boolean = false,
    val athkarAutoEnabled: Boolean = true,
    val athkarAudioState: AthkarAudioPlaybackState = AthkarAudioPlaybackState(),
    val athkarScheduleConfig: AthkarScheduleConfig = AthkarScheduleConfig(),
    val morningAthkarScheduleLabel: String = "6:00 ص",
    val eveningAthkarScheduleLabel: String = "قبل المغرب بساعة",
    val hasNotificationPermission: Boolean = false,
    val canScheduleExactAlarms: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val fcmToken: String? = null,
    val cloudSyncBusy: Boolean = false,
    val cloudSyncStatusAr: String? = null,
    val lastCloudSyncMillis: Long? = null,
    val broadcastReminders: List<BroadcastReminder> = emptyList(),
    val isPortalConnected: Boolean = false,
    val lastPortalSyncMillis: Long? = null,
    val isRefreshingPortal: Boolean = false
) {
    val isRamadanActive: Boolean
        get() = ramadanSettings.ramadanTabEnabled || (ramadanSettings.ramadanAutoInMonth && isRamadanMonth)

    val activeOccasion: ReligiousOccasion?
        get() = religiousOccasions.firstOrNull { it.isActiveToday }
}

class MuezzinViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application
    private val prefsRepo = UserPreferencesRepository(application)
    private val quranRepo = QuranRepository(application)
    private val aladhanApi = AladhanApiService.create()
    private val nominatimApi = NominatimApiService.create()
    private val locationHelper = LocationHelper(application)
    private val athanPlayer = AthanPlayer(application)
    private val qiblaSensor = QiblaSensorManager(application)
    private val ramadanScheduler = RamadanAlarmScheduler(application)
    private val notificationScheduler = PrayerNotificationScheduler(application)
    val cloudSyncRepo = CloudSyncRepository(application)

    private val _uiState = MutableStateFlow(
        MuezzinUiState(
            hasCompassSensor = qiblaSensor.hasCompassSensor(),
            athkarAutoEnabled = notificationScheduler.isAthkarAutoPlayEnabled(),
            athkarScheduleConfig = notificationScheduler.getAthkarScheduleConfig(),
            morningAthkarScheduleLabel = PrayerTimeCalculator.formatTime(
                notificationScheduler.getAthkarScheduleConfig().morningTime24,
                TimeFormat.HOUR_12
            ),
            eveningAthkarScheduleLabel = notificationScheduler.getAthkarScheduleConfig().eveningRelationLabelAr(),
            hasNotificationPermission = PrayerNotificationReceiver.hasNotificationPermission(application),
            canScheduleExactAlarms = notificationScheduler.canScheduleExactAlarms(),
            isIgnoringBatteryOptimizations = notificationScheduler.isIgnoringBatteryOptimizations(),
            broadcastReminders = notificationScheduler.getOfflineBroadcasts(),
            fcmToken = CloudSyncRepository.getLocalFcmToken(application)
        )
    )
    val uiState: StateFlow<MuezzinUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var currentTimings: AladhanTimings? = null
    private var lastLoadedKey: String? = null
    private var lastAthanTriggeredMinute: String? = null
    private var lastAthkarAutoTriggeredKey: String? = null
    private var searchJob: Job? = null
    private var quranSearchJob: Job? = null
    private var athanPlaybackJob: Job? = null

    init {
        PrayerNotificationReceiver.ensureAllChannels(appContext)
        notificationScheduler.scheduleDailyOccasionAlarm()
        notificationScheduler.scheduleDailyMidnightReschedule()
        notificationScheduler.scheduleMorningAthkarOnlyIfEnabled()
        refreshBackgroundCapabilities()
        observePreferences()
        observeQuranPreferences()
        observeCompassSensor()
        observeAthkarAudioPlayback()
        observeBroadcastReminders()
        initializeFcmMessaging()
        startCountdownTicker()
        ensureQuranLoaded()
    }

    private fun observeBroadcastReminders() {
        viewModelScope.launch {
            cloudSyncRepo.observeActiveBroadcastReminders().collect { reminders ->
                applyBroadcastRemindersSnapshot(reminders)
            }
        }
    }

    private fun applyBroadcastRemindersSnapshot(reminders: List<BroadcastReminder>) {
        val now = System.currentTimeMillis()
        val activeList = reminders.filter { it.isActive && it.isNotExpired(now) }
        val hasNotifPerm = PrayerNotificationReceiver.hasNotificationPermission(appContext)
        _uiState.update {
            it.copy(
                broadcastReminders = activeList,
                hasNotificationPermission = hasNotifPerm,
                isPortalConnected = true,
                lastPortalSyncMillis = now
            )
        }
        notificationScheduler.syncRemoteReminders(activeList)

        // Pre-warm cache for uploaded audio clips so repeated/scheduled pushes fire at the exact same instant
        activeList.forEach { reminder ->
            if (reminder.isUploadedAudio) {
                RemoteUploadedAudioStore.prefetchInBackground(
                    context = appContext,
                    assetId = reminder.audioAssetId,
                    chunkCount = reminder.audioChunkCount?.toInt() ?: 0,
                    fileExt = reminder.audioFileExt
                )
            }
        }

        val newlyTriggered = mutableListOf<BroadcastReminder>()
        activeList.forEach { reminder ->
            val shouldTriggerNow = reminder.deliveryMode == "now" || reminder.triggerNonce > 1L
            val updatedAtMs = reminder.updatedAt?.toDate()?.time
            val isFreshPush = updatedAtMs == null || (now - updatedAtMs) <= 15 * 60 * 1000L
            if (shouldTriggerNow &&
                isFreshPush &&
                notificationScheduler.markRemoteTriggerHandledIfNew(reminder.id, reminder.triggerNonce)
            ) {
                newlyTriggered.add(reminder)
                val msgBody = reminder.body.ifBlank { reminder.title }
                if (hasNotifPerm) {
                    PrayerNotificationReceiver.showCloudPushNotification(
                        context = appContext,
                        title = reminder.title,
                        body = msgBody,
                        reminderId = reminder.id,
                        audioUrl = reminder.effectiveAudioUrl,
                        audioAssetId = reminder.audioAssetId,
                        audioChunkCount = reminder.audioChunkCount?.toInt() ?: 0,
                        audioFileExt = reminder.audioFileExt
                    )
                }
            }
        }

        val latestAudio = newlyTriggered
            .filter { it.isAudio }
            .maxByOrNull { it.updatedAt?.toDate()?.time ?: 0L }
        if (latestAudio != null) {
            MorningEveningAthkarService.startRemote(
                context = appContext,
                title = latestAudio.title,
                body = latestAudio.body.ifBlank { latestAudio.title },
                audioUrl = latestAudio.effectiveAudioUrl.orEmpty(),
                remoteId = latestAudio.id,
                audioAssetId = latestAudio.audioAssetId,
                audioChunkCount = latestAudio.audioChunkCount?.toInt() ?: 0,
                audioFileExt = latestAudio.audioFileExt
            )
        }

        newlyTriggered.maxByOrNull { it.updatedAt?.toDate()?.time ?: 0L }?.let { newest ->
            _events.tryEmit(newest.title)
        }
    }

    fun refreshPortalSync() {
        if (_uiState.value.isRefreshingPortal) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingPortal = true) }
            val token = CloudSyncRepository.initializeFcmAndSubscribe(appContext)
            if (!token.isNullOrBlank()) {
                _uiState.update { it.copy(fcmToken = token) }
            }
            val result = cloudSyncRepo.fetchActiveBroadcastRemindersOnce()
            result.onSuccess { reminders ->
                applyBroadcastRemindersSnapshot(reminders)
                _uiState.update { it.copy(isRefreshingPortal = false) }
                _events.tryEmit("تمت المزامنة مع موقع الإدارة (${reminders.size})")
            }.onFailure {
                _uiState.update { it.copy(isRefreshingPortal = false, isPortalConnected = false) }
                _events.tryEmit("تعذر الاتصال بموقع الإدارة حالياً")
            }
        }
    }

    fun handleLaunchIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == PrayerNotificationReceiver.ACTION_OPEN_REMOTE_REMINDER) {
            val title = intent.getStringExtra(PrayerNotificationReceiver.EXTRA_REMINDER_TITLE).orEmpty()
            val body = intent.getStringExtra(PrayerNotificationReceiver.EXTRA_REMINDER_BODY).orEmpty()
            val audioUrl = intent.getStringExtra(PrayerNotificationReceiver.EXTRA_REMINDER_AUDIO_URL).orEmpty()
            val audioAssetId = intent.getStringExtra(PrayerNotificationReceiver.EXTRA_REMINDER_AUDIO_ASSET_ID)
            val audioChunkCount = intent.getIntExtra(PrayerNotificationReceiver.EXTRA_REMINDER_AUDIO_CHUNK_COUNT, 0)
            val audioFileExt = intent.getStringExtra(PrayerNotificationReceiver.EXTRA_REMINDER_AUDIO_FILE_EXT)
            val reminderId = intent.getStringExtra(PrayerNotificationReceiver.EXTRA_REMINDER_ID)
            if (title.isNotBlank()) {
                _events.tryEmit(title)
            }
            val hasAudioTarget = audioUrl.isNotBlank() || (!audioAssetId.isNullOrBlank() && audioChunkCount > 0)
            if (hasAudioTarget && !MorningEveningAthkarService.playbackState.value.isPlaying) {
                MorningEveningAthkarService.startRemote(
                    context = appContext,
                    title = title.ifBlank { "تذكير من موقع الإدارة" },
                    body = body.ifBlank { title },
                    audioUrl = audioUrl,
                    remoteId = reminderId,
                    audioAssetId = audioAssetId,
                    audioChunkCount = audioChunkCount,
                    audioFileExt = audioFileExt
                )
            }
            intent.action = Intent.ACTION_MAIN
        }
    }

    private fun observeAthkarAudioPlayback() {
        viewModelScope.launch {
            MorningEveningAthkarService.playbackState.collect { audioState ->
                _uiState.update { it.copy(athkarAudioState = audioState) }
            }
        }
    }

    private fun initializeFcmMessaging() {
        viewModelScope.launch {
            val token = CloudSyncRepository.initializeFcmAndSubscribe(appContext)
            if (!token.isNullOrBlank()) {
                _uiState.update { it.copy(fcmToken = token) }
            }
        }
    }

    private fun observeQuranPreferences() {
        viewModelScope.launch {
            prefsRepo.quranPrefs.collect { qPrefs ->
                _uiState.update { it.copy(quranPrefs = qPrefs) }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            val baseFlow = combine(
                prefsRepo.savedLocation,
                prefsRepo.userSettings,
                prefsRepo.tasbihState
            ) { savedLoc, settings, tasbih ->
                Triple(savedLoc, settings, tasbih)
            }
            val extraFlow = combine(
                prefsRepo.prayerAdjustments,
                prefsRepo.ramadanSettings,
                prefsRepo.qadaState
            ) { adj, ramadan, qada ->
                Triple(adj, ramadan, qada)
            }

            combine(baseFlow, extraFlow) { base, extra ->
                Pair(base, extra)
            }.collect { (base, extra) ->
                val (savedLoc, settings, tasbih) = base
                val (adjustments, ramadanSettings, qadaState) = extra

                val coords = savedLoc?.let { Coordinates(it.latitude, it.longitude) }
                val bearing = coords?.let {
                    PrayerTimeCalculator.calculateQiblaBearing(it.latitude, it.longitude)
                }

                _uiState.update { state ->
                    state.copy(
                        locationName = savedLoc?.cityName ?: state.locationName,
                        coordinates = coords,
                        timeZoneId = savedLoc?.timeZoneId ?: state.timeZoneId,
                        settings = settings,
                        tasbihState = tasbih,
                        adjustments = adjustments,
                        ramadanSettings = ramadanSettings,
                        qadaState = qadaState,
                        qiblaBearing = bearing
                    )
                }

                if (savedLoc != null) {
                    val fetchKey = "${savedLoc.latitude}:${savedLoc.longitude}:${settings.calcMethod.id}:${settings.asrSchool.id}"
                    if (fetchKey != lastLoadedKey) {
                        lastLoadedKey = fetchKey
                        fetchPrayerTimes(savedLoc, settings)
                    } else {
                        recomputePrayerDisplay()
                        resyncScheduledNotifications()
                    }
                } else {
                    _uiState.update { it.copy(prayersState = PrayersLoadState.NeedsLocation) }
                }
            }
        }
    }

    private fun observeCompassSensor() {
        if (!qiblaSensor.hasCompassSensor()) return
        viewModelScope.launch {
            qiblaSensor.observeHeadingDegrees().collect { heading ->
                _uiState.update { it.copy(deviceHeading = heading) }
            }
        }
    }

    private fun startCountdownTicker() {
        viewModelScope.launch {
            while (isActive) {
                recomputePrayerDisplay()
                delay(1_000L)
            }
        }
    }

    private fun recomputePrayerDisplay() {
        val stateSnapshot = _uiState.value
        val settings = stateSnapshot.settings
        val adjustments = stateSnapshot.adjustments
        val ramadanPrefs = stateSnapshot.ramadanSettings
        val tzId = stateSnapshot.timeZoneId
        val now = System.currentTimeMillis()
        val cityClock = PrayerTimeCalculator.formatCurrentCityTime(now, tzId, settings.timeFormat)

        val timings = currentTimings
        if (timings == null) {
            val fallbackYear = PrayerTimeCalculator.currentHijriYearFallback(tzId)
            val fallbackMonth = PrayerTimeCalculator.currentHijriMonthFallback(tzId)
            val fallbackDay = PrayerTimeCalculator.currentHijriDayFallback(tzId)
            val cfg = stateSnapshot.athkarScheduleConfig
            _uiState.update {
                it.copy(
                    cityLocalTime = cityClock,
                    hijriYear = fallbackYear,
                    hijriMonth = fallbackMonth,
                    hijriDay = fallbackDay,
                    religiousOccasions = PrayerTimeCalculator.computeReligiousOccasions(
                        fallbackYear,
                        fallbackMonth,
                        fallbackDay
                    ),
                    isRamadanMonth = PrayerTimeCalculator.isRamadanMonth(fallbackMonth),
                    fallbackCelestialPhase = PrayerTimeCalculator.resolveCelestialPhaseByClock(now, tzId),
                    morningAthkarScheduleLabel = PrayerTimeCalculator.formatTime(cfg.morningTime24, settings.timeFormat),
                    eveningAthkarScheduleLabel = cfg.eveningRelationLabelAr()
                )
            }
            if (stateSnapshot.athkarAutoEnabled) {
                val cityHHmm = PrayerTimeCalculator.formatCurrentCityTime(now, tzId, TimeFormat.HOUR_24)
                val dayStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = PrayerTimeCalculator.resolveTimeZone(tzId)
                }.format(Date(now))
                if (cityHHmm == cfg.morningTime24 && lastAthkarAutoTriggeredKey != "$dayStamp:morning") {
                    lastAthkarAutoTriggeredKey = "$dayStamp:morning"
                    MorningEveningAthkarService.start(appContext, AthkarAudioType.MORNING)
                    _events.tryEmit("حان الآن موعد أذكار الصباح")
                }
            }
            return
        }

        val status = PrayerTimeCalculator.computeNextPrayerStatus(
            timings = timings,
            nowMillis = now,
            timeZoneId = tzId,
            adjustments = adjustments
        )
        val items = PrayerTimeCalculator.buildPrayerItems(
            timings = timings,
            timeFormat = settings.timeFormat,
            nextPrayer = status.nextPrayer,
            adjustments = adjustments
        )
        val ramadanStatus = PrayerTimeCalculator.computeRamadanStatus(
            timings = timings,
            nowMillis = now,
            timeZoneId = tzId,
            adjustments = adjustments,
            timeFormat = settings.timeFormat,
            imsakLeadMinutes = ramadanPrefs.imsakLeadMinutes,
            iftarLeadMinutes = ramadanPrefs.iftarLeadMinutes
        )

        val adjusted = PrayerTimeCalculator.adjustedTimings(timings, adjustments)
        val cfg = stateSnapshot.athkarScheduleConfig
        val (morning24, evening24) = PrayerTimeCalculator.computeAthkarScheduleTimes24(
            adjTimings = adjusted,
            config = cfg
        )
        val morningLabel = PrayerTimeCalculator.formatTime(morning24, settings.timeFormat)
        val eveningLabel = "${PrayerTimeCalculator.formatTime(evening24, settings.timeFormat)} (${cfg.eveningRelationLabelAr()})"

        _uiState.update { state ->
            state.copy(
                cityLocalTime = cityClock,
                prayersState = PrayersLoadState.Success(
                    items = items,
                    nextPrayerStatus = status
                ),
                ramadanStatus = ramadanStatus,
                morningAthkarScheduleLabel = morningLabel,
                eveningAthkarScheduleLabel = eveningLabel
            )
        }

        notifyActiveOccasionIfNeeded()

        if (stateSnapshot.athkarAutoEnabled) {
            val cityHHmm = PrayerTimeCalculator.formatCurrentCityTime(now, tzId, TimeFormat.HOUR_24)
            val dayStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = PrayerTimeCalculator.resolveTimeZone(tzId)
            }.format(Date(now))

            if (cityHHmm == morning24 && lastAthkarAutoTriggeredKey != "$dayStamp:morning") {
                lastAthkarAutoTriggeredKey = "$dayStamp:morning"
                MorningEveningAthkarService.start(appContext, AthkarAudioType.MORNING)
                PrayerNotificationReceiver.showCloudPushNotification(
                    context = appContext,
                    title = AthkarAudioType.MORNING.titleAr,
                    body = AthkarAudioType.MORNING.subtitleAr,
                    reminderId = "athkar_${AthkarAudioType.MORNING.id}"
                )
                _events.tryEmit("حان الآن موعد أذكار الصباح")
            } else if (cityHHmm == evening24 && lastAthkarAutoTriggeredKey != "$dayStamp:evening") {
                lastAthkarAutoTriggeredKey = "$dayStamp:evening"
                MorningEveningAthkarService.start(appContext, AthkarAudioType.EVENING)
                PrayerNotificationReceiver.showCloudPushNotification(
                    context = appContext,
                    title = AthkarAudioType.EVENING.titleAr,
                    body = AthkarAudioType.EVENING.subtitleAr,
                    reminderId = "athkar_${AthkarAudioType.EVENING.id}"
                )
                _events.tryEmit("حان الآن موعد أذكار المساء")
            }
        }

        if (status.millisRemaining in 0..999 &&
            settings.athanEnabled &&
            status.nextPrayer != PrayerType.SUNRISE
        ) {
            val triggerKey = "${status.nextPrayer.apiKey}:${SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.US).format(Date())}"
            if (lastAthanTriggeredMinute != triggerKey) {
                lastAthanTriggeredMinute = triggerKey
                val isFajr = (status.nextPrayer == PrayerType.FAJR)
                val voice = if (isFajr && settings.fajrAthanEnabled) MuezzinVoice.FAJR_SPECIAL else settings.muezzinVoice
                val prayerTimeFormatted = items.firstOrNull { it.type == status.nextPrayer }?.formattedTime.orEmpty()
                PrayerNotificationReceiver.showPrayerNotification(
                    context = appContext,
                    prayerKey = status.nextPrayer.apiKey,
                    prayerNameAr = status.nextPrayer.arabicName,
                    cityName = stateSnapshot.locationName.orEmpty(),
                    formattedTime = prayerTimeFormatted,
                    voice = voice,
                    withChannelSound = false
                )
                triggerAthanAlert(forFajr = isFajr)
            }
        }
    }

    private fun resyncScheduledNotifications() {
        val timings = currentTimings
        val snapshot = _uiState.value
        if (timings == null) {
            notificationScheduler.scheduleMorningAthkarOnlyIfEnabled()
            return
        }
        notificationScheduler.scheduleUpcomingPrayers(
            timings = timings,
            timeZoneId = snapshot.timeZoneId,
            cityName = snapshot.locationName,
            adjustments = snapshot.adjustments,
            settings = snapshot.settings,
            latitude = snapshot.coordinates?.latitude,
            longitude = snapshot.coordinates?.longitude
        )
        notificationScheduler.scheduleDailyOccasionAlarm(snapshot.timeZoneId)
    }

    private fun notifyActiveOccasionIfNeeded() {
        val snapshot = _uiState.value
        val active = snapshot.activeOccasion ?: return
        if (!snapshot.hasNotificationPermission) return
        if (notificationScheduler.markOccasionNotifiedIfNeeded(active, snapshot.hijriYear)) {
            PrayerNotificationReceiver.showOccasionNotification(appContext, active)
        }
    }

    fun detectCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetectingLocation = true) }
            try {
                val coords = locationHelper.getCurrentCoordinates()
                if (coords == null) {
                    _events.tryEmit("تعذر تحديد الموقع تلقائياً. يمكنك البحث عن دولتك أو اختيار مدينة.")
                    _uiState.update { it.copy(isDetectingLocation = false) }
                    return@launch
                }

                val cityName = resolveCityName(coords)
                prefsRepo.saveLocation(
                    latitude = coords.latitude,
                    longitude = coords.longitude,
                    cityName = cityName,
                    timeZoneId = TimeZone.getDefault().id
                )
                _events.tryEmit("تم تحديد الموقع: $cityName")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Location detection failed: ${e.message}")
                _events.tryEmit("فشل تحديد الموقع")
            } finally {
                _uiState.update { it.copy(isDetectingLocation = false) }
            }
        }
    }

    fun selectCityPreset(city: CityPreset) {
        viewModelScope.launch {
            prefsRepo.saveLocation(
                latitude = city.latitude,
                longitude = city.longitude,
                cityName = city.nameAr,
                timeZoneId = city.timeZoneId
            )
            _events.tryEmit("تم اختيار ${city.nameAr}")
        }
    }

    fun selectSearchPlace(place: PlaceSearchResult) {
        viewModelScope.launch {
            prefsRepo.saveLocation(
                latitude = place.latitude,
                longitude = place.longitude,
                cityName = place.shortName,
                timeZoneId = null
            )
            _events.tryEmit("تم اختيار ${place.shortName}")
        }
    }

    fun searchPlaces(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.update { it.copy(placeSearchResults = emptyList(), isSearchingPlaces = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350L)
            _uiState.update { it.copy(isSearchingPlaces = true) }
            try {
                val results = nominatimApi.searchPlaces(trimmed).mapNotNull { item ->
                    val lat = item.lat.toDoubleOrNull() ?: return@mapNotNull null
                    val lon = item.lon.toDoubleOrNull() ?: return@mapNotNull null
                    val addr = item.address
                    val city = addr?.city ?: addr?.town ?: addr?.village ?: addr?.state
                    val country = addr?.country.orEmpty()
                    val short = city ?: country.ifBlank { item.displayName.substringBefore(",") }
                    PlaceSearchResult(
                        displayName = item.displayName,
                        shortName = short,
                        countryName = country,
                        latitude = lat,
                        longitude = lon
                    )
                }
                _uiState.update { it.copy(placeSearchResults = results, isSearchingPlaces = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Place search failed: ${e.message}")
                _uiState.update { it.copy(isSearchingPlaces = false) }
            }
        }
    }

    fun retryFetchPrayerTimes() {
        val state = _uiState.value
        val coords = state.coordinates ?: return
        val name = state.locationName ?: "موقعي"
        val tz = state.timeZoneId ?: TimeZone.getDefault().id
        viewModelScope.launch {
            fetchPrayerTimes(SavedLocation(coords.latitude, coords.longitude, name, tz), state.settings)
        }
    }

    private suspend fun resolveCityName(coords: Coordinates): String {
        return try {
            val response = nominatimApi.reverseGeocode(coords.latitude, coords.longitude)
            val addr = response.address
            addr?.city ?: addr?.town ?: addr?.village ?: addr?.state ?: "موقعي"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocoding failed, using default label", e)
            "موقعي"
        }
    }

    private fun dateStringForTimeZone(timeZoneId: String): String {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.US)
        sdf.timeZone = PrayerTimeCalculator.resolveTimeZone(timeZoneId)
        return sdf.format(Date())
    }

    private suspend fun fetchPrayerTimes(location: SavedLocation, settings: UserSettings) {
        val initialTz = location.timeZoneId.ifBlank { TimeZone.getDefault().id }
        val offlineTimings = notificationScheduler.getOrComputeOfflineTimings(
            latitude = location.latitude,
            longitude = location.longitude,
            timeZoneId = initialTz,
            method = settings.calcMethod,
            asrSchool = settings.asrSchool
        )
        currentTimings = offlineTimings
        val fallbackDay = PrayerTimeCalculator.currentHijriDayFallback(initialTz)
        val fallbackMonth = PrayerTimeCalculator.currentHijriMonthFallback(initialTz)
        val fallbackYear = PrayerTimeCalculator.currentHijriYearFallback(initialTz)
        val fallbackOccasions = PrayerTimeCalculator.computeReligiousOccasions(
            hijriYear = fallbackYear,
            hijriMonth = fallbackMonth,
            hijriDay = fallbackDay
        )
        _uiState.update {
            it.copy(
                timeZoneId = initialTz,
                hijriDate = PrayerTimeCalculator.offlineHijriDateFormatted(initialTz),
                hijriDay = fallbackDay,
                hijriMonth = fallbackMonth,
                hijriYear = fallbackYear,
                religiousOccasions = fallbackOccasions,
                isRamadanMonth = PrayerTimeCalculator.isRamadanMonth(fallbackMonth),
                gregorianDate = PrayerTimeCalculator.offlineGregorianReadable(initialTz)
            )
        }
        recomputePrayerDisplay()
        resyncScheduledNotifications()
        syncRamadanAlarms()

        try {
            val initialDateStr = dateStringForTimeZone(initialTz)
            var response = aladhanApi.getTimings(
                date = initialDateStr,
                latitude = location.latitude,
                longitude = location.longitude,
                method = settings.calcMethod.id,
                school = settings.asrSchool.id
            )

            val resolvedTz = response.data.meta?.timezone?.takeIf { it.isNotBlank() } ?: initialTz
            if (resolvedTz != location.timeZoneId) {
                prefsRepo.updateTimeZoneId(resolvedTz)
                val targetDateStr = dateStringForTimeZone(resolvedTz)
                if (targetDateStr != initialDateStr) {
                    response = aladhanApi.getTimings(
                        date = targetDateStr,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        method = settings.calcMethod.id,
                        school = settings.asrSchool.id
                    )
                }
            }

            currentTimings = response.data.timings
            val hijri = response.data.date.hijri
            val hijriDayInt = hijri.day.toIntOrNull()?.coerceIn(1, 30)
                ?: PrayerTimeCalculator.currentHijriDayFallback(resolvedTz)
            val hijriMonthNum = hijri.month.number.takeIf { it in 1..12 }
                ?: PrayerTimeCalculator.currentHijriMonthFallback(resolvedTz)
            val hijriYearInt = hijri.year.toIntOrNull()?.coerceAtLeast(1440)
                ?: PrayerTimeCalculator.currentHijriYearFallback(resolvedTz)
            val ramadanMonthNow = PrayerTimeCalculator.isRamadanMonth(hijriMonthNum, hijri.month.ar)
            val occasions = PrayerTimeCalculator.computeReligiousOccasions(
                hijriYear = hijriYearInt,
                hijriMonth = hijriMonthNum,
                hijriDay = hijriDayInt
            )
            val hijriFormatted = "${hijri.day} ${hijri.month.ar} ${hijri.year} هـ"
            _uiState.update {
                it.copy(
                    timeZoneId = resolvedTz,
                    hijriDate = hijriFormatted,
                    hijriDay = hijriDayInt,
                    hijriMonth = hijriMonthNum,
                    hijriYear = hijriYearInt,
                    religiousOccasions = occasions,
                    isRamadanMonth = ramadanMonthNow,
                    gregorianDate = response.data.date.readable
                )
            }
            recomputePrayerDisplay()
            resyncScheduledNotifications()
            syncRamadanAlarms()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Offline mode: keeping astronomical calculation (${e.message})")
        }
    }

    // Prayer time adjustment actions
    fun updateGlobalOffset(minutes: Int) {
        viewModelScope.launch {
            prefsRepo.updateGlobalOffset(minutes)
        }
    }

    fun updatePrayerOffset(type: PrayerType, minutes: Int) {
        viewModelScope.launch {
            prefsRepo.updatePrayerOffset(type, minutes)
        }
    }

    fun resetAllOffsets() {
        viewModelScope.launch {
            prefsRepo.resetAllOffsets()
            _events.tryEmit("تمت إعادة ضبط فرق التوقيت")
        }
    }

    // Athkar actions
    fun incrementAthkar(id: String, targetCount: Int) {
        val currentMap = _uiState.value.athkarCounts.toMutableMap()
        val prev = currentMap[id] ?: 0
        if (prev >= targetCount) return
        val next = prev + 1
        currentMap[id] = next
        _uiState.update { it.copy(athkarCounts = currentMap) }

        val vibrate = _uiState.value.settings.vibrationEnabled
        if (next == targetCount) {
            if (vibrate) athanPlayer.vibratePattern()
        } else {
            if (vibrate) athanPlayer.vibrateClick()
        }
    }

    fun resetAthkarItem(id: String) {
        val currentMap = _uiState.value.athkarCounts.toMutableMap()
        currentMap.remove(id)
        _uiState.update { it.copy(athkarCounts = currentMap) }
    }

    fun resetAllAthkar() {
        _uiState.update { it.copy(athkarCounts = emptyMap()) }
        _events.tryEmit("تم تصفير عدادات الأذكار")
    }

    fun totalCompletedAthkar(): Int {
        val counts = _uiState.value.athkarCounts
        return IslamicContent.ATHKAR_CATEGORIES
            .flatMap { it.items }
            .count { item -> (counts[item.id] ?: 0) >= item.targetCount }
    }

    // Tasbih actions
    fun incrementTasbih() {
        val current = _uiState.value.tasbihState
        val nextCount = current.count + 1
        val vibrate = _uiState.value.settings.vibrationEnabled
        if (nextCount >= current.target && current.count < current.target) {
            if (vibrate) athanPlayer.vibratePattern()
            _events.tryEmit("أتممت ${current.target} مرة - ${current.dhikr}")
        } else {
            if (vibrate) athanPlayer.vibrateClick()
        }
        viewModelScope.launch {
            prefsRepo.saveTasbih(current.copy(count = nextCount))
        }
    }

    fun resetTasbih() {
        viewModelScope.launch {
            prefsRepo.saveTasbih(_uiState.value.tasbihState.copy(count = 0))
            _events.tryEmit("تم إعادة تعيين المسبحة")
        }
    }

    fun selectDhikr(dhikr: String) {
        viewModelScope.launch {
            prefsRepo.saveTasbih(_uiState.value.tasbihState.copy(dhikr = dhikr, count = 0))
        }
    }

    fun cycleTasbihTarget() {
        val current = _uiState.value.tasbihState
        val targets = TasbihState.TARGET_OPTIONS
        val nextIdx = (targets.indexOf(current.target) + 1) % targets.size
        viewModelScope.launch {
            prefsRepo.saveTasbih(current.copy(target = targets[nextIdx]))
        }
    }

    // Ramadan alarms & Qada actions
    fun setRamadanTabEnabled(enabled: Boolean) {
        val current = _uiState.value
        val updated = if (enabled) {
            current.ramadanSettings.copy(ramadanTabEnabled = true)
        } else {
            current.ramadanSettings.copy(
                ramadanTabEnabled = false,
                ramadanAutoInMonth = if (current.isRamadanMonth) false else current.ramadanSettings.ramadanAutoInMonth
            )
        }
        viewModelScope.launch {
            prefsRepo.updateRamadanSettings(updated)
            syncRamadanAlarms(updated)
            _events.tryEmit(
                if (enabled) "تم تفعيل تبويب رمضان في الشريط السفلي"
                else "تم إعادة تبويب رمضان إلى الإعدادات"
            )
        }
    }

    fun setRamadanAutoInMonth(auto: Boolean) {
        val updated = _uiState.value.ramadanSettings.copy(ramadanAutoInMonth = auto)
        viewModelScope.launch {
            prefsRepo.updateRamadanSettings(updated)
            syncRamadanAlarms(updated)
            _events.tryEmit(
                if (auto) "تم تفعيل الظهور التلقائي في شهر رمضان"
                else "تم إيقاف الظهور التلقائي في شهر رمضان"
            )
        }
    }

    fun updateRamadanImsakAlarm(enabled: Boolean, leadMinutes: Int = _uiState.value.ramadanSettings.imsakLeadMinutes) {
        val updated = _uiState.value.ramadanSettings.copy(
            imsakAlarmEnabled = enabled,
            imsakLeadMinutes = leadMinutes
        )
        viewModelScope.launch {
            prefsRepo.updateRamadanSettings(updated)
            syncRamadanAlarms(updated)
            _events.tryEmit(
                if (enabled) "تم تفعيل منبه السحور والإمساك"
                else "تم إيقاف منبه السحور والإمساك"
            )
        }
    }

    fun updateRamadanIftarAlarm(enabled: Boolean, leadMinutes: Int = _uiState.value.ramadanSettings.iftarLeadMinutes) {
        val updated = _uiState.value.ramadanSettings.copy(
            iftarAlarmEnabled = enabled,
            iftarLeadMinutes = leadMinutes
        )
        viewModelScope.launch {
            prefsRepo.updateRamadanSettings(updated)
            syncRamadanAlarms(updated)
            _events.tryEmit(
                if (enabled) "تم تفعيل منبه الإفطار"
                else "تم إيقاف منبه الإفطار"
            )
        }
    }

    private fun syncRamadanAlarms(overrideSettings: RamadanSettings? = null) {
        val state = _uiState.value
        val settings = overrideSettings ?: state.ramadanSettings
        val timings = currentTimings ?: return
        val status = PrayerTimeCalculator.computeRamadanStatus(
            timings = timings,
            nowMillis = System.currentTimeMillis(),
            timeZoneId = state.timeZoneId,
            adjustments = state.adjustments,
            timeFormat = state.settings.timeFormat,
            imsakLeadMinutes = settings.imsakLeadMinutes,
            iftarLeadMinutes = settings.iftarLeadMinutes
        )
        val ramadanVisible = settings.ramadanTabEnabled || (settings.ramadanAutoInMonth && state.isRamadanMonth)
        if (!ramadanVisible) {
            ramadanScheduler.cancelImsakAlarm()
            ramadanScheduler.cancelIftarAlarm()
            return
        }
        if (settings.imsakAlarmEnabled) {
            ramadanScheduler.scheduleImsakAlarm(status.nextImsakTriggerMillis, settings.imsakLeadMinutes)
        } else {
            ramadanScheduler.cancelImsakAlarm()
        }
        if (settings.iftarAlarmEnabled) {
            ramadanScheduler.scheduleIftarAlarm(status.nextIftarTriggerMillis, settings.iftarLeadMinutes)
        } else {
            ramadanScheduler.cancelIftarAlarm()
        }
    }

    fun testImsakAlarm() {
        RamadanAlarmReceiver.showNotification(
            appContext,
            RamadanAlarmReceiver.TYPE_IMSAK,
            "تجربة منبه السحور والإمساك",
            "تسحروا فإن في السحور بركة — وقت الإمساك يقترب"
        )
        triggerAthanAlert(forFajr = true)
    }

    fun testIftarAlarm() {
        RamadanAlarmReceiver.showNotification(
            appContext,
            RamadanAlarmReceiver.TYPE_IFTAR,
            "تجربة منبه الإفطار",
            "ذهب الظمأ وابتلت العروق وثبت الأجر إن شاء الله"
        )
        triggerAthanAlert(forFajr = false)
    }

    fun adjustQadaRemaining(delta: Int) {
        val current = _uiState.value.qadaState
        val nextRemaining = (current.remainingDays + delta).coerceAtLeast(0)
        viewModelScope.launch {
            prefsRepo.saveQadaState(current.copy(remainingDays = nextRemaining))
        }
    }

    fun markQadaDayDone() {
        val current = _uiState.value.qadaState
        if (current.remainingDays <= 0) {
            _events.tryEmit("لا توجد أيام قضاء متبقية — تقبل الله منك")
            return
        }
        val next = QadaState(
            remainingDays = (current.remainingDays - 1).coerceAtLeast(0),
            completedDays = current.completedDays + 1
        )
        viewModelScope.launch {
            prefsRepo.saveQadaState(next)
            if (_uiState.value.settings.vibrationEnabled) athanPlayer.vibratePattern()
            _events.tryEmit("تقبل الله صيامك! المتبقي: ${next.remainingDays} يوم")
        }
    }

    fun resetQada() {
        viewModelScope.launch {
            prefsRepo.saveQadaState(QadaState(remainingDays = 0, completedDays = 0))
            _events.tryEmit("تمت إعادة تعيين سجل القضاء")
        }
    }

    // Settings actions
    fun updateCalcMethod(method: CalculationMethod) {
        viewModelScope.launch {
            prefsRepo.updateCalcMethod(method)
            _events.tryEmit("تم تحديث طريقة الحساب")
        }
    }

    fun updateAsrSchool(school: AsrSchool) {
        viewModelScope.launch {
            prefsRepo.updateAsrSchool(school)
            _events.tryEmit("تم تحديث المذهب")
        }
    }

    fun updateTimeFormat(format: TimeFormat) {
        viewModelScope.launch {
            prefsRepo.updateTimeFormat(format)
        }
    }

    fun toggleAthanEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateAthanEnabled(enabled)
        }
    }

    fun toggleVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateVibrationEnabled(enabled)
        }
    }

    fun updateMuezzinVoice(voice: MuezzinVoice) {
        viewModelScope.launch {
            prefsRepo.updateMuezzinVoice(voice)
            _events.tryEmit("تم اختيار صوت المؤذن: ${voice.arabicLabel}")
        }
    }

    fun toggleFajrAthanEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateFajrAthanEnabled(enabled)
            _events.tryEmit(
                if (enabled) "تم تفعيل أذان الفجر الخاص لصلاة الفجر"
                else "سيستخدم الفجر صوت المؤذن المختار"
            )
        }
    }

    fun updateAthanPlayMode(mode: AthanPlayMode) {
        viewModelScope.launch {
            prefsRepo.updateAthanPlayMode(mode)
            _events.tryEmit("وضع التشغيل: ${mode.arabicLabel}")
        }
    }

    fun triggerAthanAlert(forFajr: Boolean = false) {
        val currentSettings = _uiState.value.settings
        val voiceToPlay = if (forFajr && currentSettings.fajrAthanEnabled) {
            MuezzinVoice.FAJR_SPECIAL
        } else {
            currentSettings.muezzinVoice
        }
        val label = if (forFajr && currentSettings.fajrAthanEnabled) {
            "حان وقت أذان الفجر"
        } else {
            "حان الآن وقت الأذان — ${voiceToPlay.arabicLabel}"
        }
        _events.tryEmit(label)
        athanPlaybackJob?.cancel()
        athanPlaybackJob = viewModelScope.launch {
            athanPlayer.playAthan(
                rawResId = voiceToPlay.rawResId,
                playMode = currentSettings.athanPlayMode,
                vibrateEnabled = currentSettings.vibrationEnabled,
                onPlayingChanged = { playing ->
                    _uiState.update { it.copy(isAthanPlaying = playing) }
                }
            )
        }
    }

    fun stopAthanPlayback() {
        athanPlaybackJob?.cancel()
        athanPlaybackJob = null
        athanPlayer.stop()
        _uiState.update { it.copy(isAthanPlaying = false) }
    }

    override fun onCleared() {
        super.onCleared()
        athanPlayer.stop()
    }

    // Notifications, Athkar Audio & Cloud Sync actions
    fun refreshBackgroundCapabilities() {
        val notifGranted = PrayerNotificationReceiver.hasNotificationPermission(appContext)
        val exactGranted = notificationScheduler.canScheduleExactAlarms()
        val batteryIgnored = notificationScheduler.isIgnoringBatteryOptimizations()
        _uiState.update {
            it.copy(
                hasNotificationPermission = notifGranted,
                canScheduleExactAlarms = exactGranted,
                isIgnoringBatteryOptimizations = batteryIgnored
            )
        }
        PrayerNotificationReceiver.ensureAllChannels(appContext)
        resyncScheduledNotifications()
        syncRamadanAlarms()
        if (notifGranted) {
            notifyActiveOccasionIfNeeded()
        }
    }

    fun refreshNotificationPermissionStatus() {
        refreshBackgroundCapabilities()
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        refreshBackgroundCapabilities()
        if (granted) {
            _events.tryEmit("تم تفعيل إشعارات الأذان والأذكار والمناسبات بنجاح")
        } else {
            _events.tryEmit("الإشعارات غير مفعّلة — يرجى تفعيلها لضمان سماع الأذان والأذكار")
        }
    }

    fun sendTestNotification() {
        val granted = PrayerNotificationReceiver.hasNotificationPermission(appContext)
        refreshBackgroundCapabilities()
        if (!granted) {
            _events.tryEmit("يرجى السماح بالإشعارات أولاً لإرسال التنبيه")
            return
        }
        PrayerNotificationReceiver.showTestNotification(appContext, _uiState.value.settings.muezzinVoice)
        _events.tryEmit("تم إرسال إشعار تجريبي إلى النظام")
    }

    fun toggleAthkarAutoPlay(enabled: Boolean) {
        notificationScheduler.setAthkarAutoPlayEnabled(enabled)
        _uiState.update { it.copy(athkarAutoEnabled = enabled) }
        resyncScheduledNotifications()
        _events.tryEmit(
            if (enabled) "تم تفعيل التشغيل التلقائي لأذكار الصباح والمساء"
            else "تم إيقاف التشغيل التلقائي للأذكار"
        )
    }

    fun updateAthkarScheduleConfig(morningTime24: String, eveningOffsetFromMaghribMinutes: Int) {
        val updated = AthkarScheduleConfig(
            morningTime24 = PrayerTimeCalculator.cleanTime24(morningTime24)
                .ifBlank { AthkarScheduleConfig.DEFAULT_MORNING_TIME_24 },
            eveningOffsetFromMaghribMinutes = eveningOffsetFromMaghribMinutes.coerceIn(-180, 120)
        )
        notificationScheduler.setAthkarScheduleConfig(updated)
        _uiState.update {
            it.copy(
                athkarScheduleConfig = updated,
                morningAthkarScheduleLabel = PrayerTimeCalculator.formatTime(
                    updated.morningTime24,
                    it.settings.timeFormat
                ),
                eveningAthkarScheduleLabel = updated.eveningRelationLabelAr()
            )
        }
        recomputePrayerDisplay()
        resyncScheduledNotifications()
        _events.tryEmit("تم تحديث مواعيد الأذكار بنجاح")
    }

    fun playAthkarAudio(type: AthkarAudioType) {
        val started = MorningEveningAthkarService.start(appContext, type)
        _events.tryEmit(
            if (started) "جاري تشغيل ${type.titleAr}"
            else "تعذر تشغيل الصوت الآن"
        )
    }

    fun playRemoteReminderAudio(reminder: BroadcastReminder) {
        val url = reminder.effectiveAudioUrl.orEmpty()
        if (url.isBlank() && !reminder.isUploadedAudio) return
        val started = MorningEveningAthkarService.startRemote(
            context = appContext,
            title = reminder.title,
            body = reminder.body.ifBlank { reminder.title },
            audioUrl = url,
            remoteId = reminder.id,
            audioAssetId = reminder.audioAssetId,
            audioChunkCount = reminder.audioChunkCount?.toInt() ?: 0,
            audioFileExt = reminder.audioFileExt
        )
        _events.tryEmit(
            if (started) "جاري تشغيل: ${reminder.title}"
            else "تعذر تشغيل المقطع الصوتي"
        )
    }

    fun stopAthkarAudio() {
        MorningEveningAthkarService.stop(appContext)
    }

    fun syncProfileToCloud() {
        if (Firebase.auth.currentUser == null) {
            _events.tryEmit("سجّل الدخول بحساب Google أولاً للمزامنة السحابية")
            return
        }
        val snapshot = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(cloudSyncBusy = true, cloudSyncStatusAr = "جاري المزامنة مع السحابة...") }
            val result = cloudSyncRepo.saveOrUpdateProfile(
                fcmToken = snapshot.fcmToken ?: CloudSyncRepository.getLocalFcmToken(appContext),
                cityName = snapshot.locationName,
                athanEnabled = snapshot.settings.athanEnabled,
                athkarAutoEnabled = snapshot.athkarAutoEnabled,
                qadaRemaining = snapshot.qadaState.remainingDays,
                qadaCompleted = snapshot.qadaState.completedDays
            )
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        cloudSyncBusy = false,
                        cloudSyncStatusAr = "تمت مزامنة الإعدادات ورمز الإشعارات بنجاح",
                        lastCloudSyncMillis = System.currentTimeMillis()
                    )
                }
                _events.tryEmit("تمت المزامنة السحابية بنجاح")
            } else {
                _uiState.update {
                    it.copy(
                        cloudSyncBusy = false,
                        cloudSyncStatusAr = "تعذرت المزامنة حالياً، تحقق من الشبكة"
                    )
                }
                _events.tryEmit("تعذرت المزامنة مع السحابة")
            }
        }
    }

    // Quran / Mushaf actions
    fun ensureQuranLoaded() {
        val current = _uiState.value.quranState
        if (current.pages.isNotEmpty() || current.isLoading) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(quranState = it.quranState.copy(isLoading = true, errorMessage = null))
            }
            try {
                val dataset = quranRepo.getDataset()
                _uiState.update {
                    it.copy(
                        quranState = it.quranState.copy(
                            isLoading = false,
                            errorMessage = null,
                            surahs = dataset.surahs,
                            pages = dataset.pages,
                            juzList = dataset.juzList
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Quran dataset", e)
                _uiState.update {
                    it.copy(
                        quranState = it.quranState.copy(
                            isLoading = false,
                            errorMessage = "تعذر تحميل المصحف الشريف"
                        )
                    )
                }
                _events.tryEmit("تعذر تحميل المصحف الشريف")
            }
        }
    }

    fun updateQuranLastPage(page: Int) {
        viewModelScope.launch {
            prefsRepo.updateQuranLastPage(page)
        }
    }

    fun toggleQuranBookmark(page: Int) {
        viewModelScope.launch {
            prefsRepo.updateQuranBookmarkPage(page)
            _events.tryEmit("تم حفظ علامة الصفحة $page")
        }
    }

    fun updateQuranFontSize(sizeSp: Float) {
        viewModelScope.launch {
            prefsRepo.updateQuranFontSize(sizeSp)
        }
    }

    fun updateQuranPaperMode(paperMode: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateQuranPaperMode(paperMode)
        }
    }

    fun searchQuran(query: String) {
        quranSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.update { it.copy(quranState = it.quranState.copy(searchResults = emptyList())) }
            return
        }
        quranSearchJob = viewModelScope.launch {
            delay(250L)
            val results = quranRepo.searchAyahs(trimmed)
            _uiState.update { it.copy(quranState = it.quranState.copy(searchResults = results)) }
        }
    }
}
