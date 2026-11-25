package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AsrSchool
import com.example.data.model.AthanPlayMode
import com.example.data.model.CalculationMethod
import com.example.data.model.MuezzinVoice
import com.example.data.model.PrayerAdjustments
import com.example.data.model.PrayerType
import com.example.data.model.QadaState
import com.example.data.model.RamadanSettings
import com.example.data.model.TasbihState
import com.example.data.model.TimeFormat
import com.example.data.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.TimeZone

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "muezzin_prefs")

data class SavedLocation(
    val latitude: Double,
    val longitude: Double,
    val cityName: String,
    val timeZoneId: String = TimeZone.getDefault().id
)

data class QuranPrefs(
    val lastPage: Int = 1,
    val bookmarkPage: Int = 1,
    val fontSizeSp: Float = 24f,
    val paperMode: Boolean = true
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val CITY_NAME = stringPreferencesKey("city_name")
        val TIMEZONE_ID = stringPreferencesKey("timezone_id")
        val CALC_METHOD = intPreferencesKey("calc_method")
        val ASR_SCHOOL = intPreferencesKey("asr_school")
        val TIME_FORMAT = intPreferencesKey("time_format")
        val ATHAN_ENABLED = booleanPreferencesKey("athan_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val MUEZZIN_VOICE = stringPreferencesKey("muezzin_voice")
        val FAJR_ATHAN_ENABLED = booleanPreferencesKey("fajr_athan_enabled")
        val ATHAN_PLAY_MODE = stringPreferencesKey("athan_play_mode")
        val TASBIH_COUNT = intPreferencesKey("tasbih_count")
        val TASBIH_DHIKR = stringPreferencesKey("tasbih_dhikr")
        val TASBIH_TARGET = intPreferencesKey("tasbih_target")

        // Prayer time adjustments (minutes)
        val ADJ_GLOBAL = intPreferencesKey("adj_global")
        val ADJ_FAJR = intPreferencesKey("adj_fajr")
        val ADJ_SUNRISE = intPreferencesKey("adj_sunrise")
        val ADJ_DHUHR = intPreferencesKey("adj_dhuhr")
        val ADJ_ASR = intPreferencesKey("adj_asr")
        val ADJ_MAGHRIB = intPreferencesKey("adj_maghrib")
        val ADJ_ISHA = intPreferencesKey("adj_isha")

        // Ramadan settings & Qada
        val RAMADAN_TAB_ENABLED = booleanPreferencesKey("ramadan_tab_enabled")
        val RAMADAN_AUTO_IN_MONTH = booleanPreferencesKey("ramadan_auto_in_month")
        val RAMADAN_IMSAK_ALARM = booleanPreferencesKey("ramadan_imsak_alarm")
        val RAMADAN_IMSAK_LEAD = intPreferencesKey("ramadan_imsak_lead")
        val RAMADAN_IFTAR_ALARM = booleanPreferencesKey("ramadan_iftar_alarm")
        val RAMADAN_IFTAR_LEAD = intPreferencesKey("ramadan_iftar_lead")
        val QADA_REMAINING = intPreferencesKey("qada_remaining")
        val QADA_COMPLETED = intPreferencesKey("qada_completed")

        // Quran / Mushaf preferences
        val QURAN_LAST_PAGE = intPreferencesKey("quran_last_page")
        val QURAN_BOOKMARK_PAGE = intPreferencesKey("quran_bookmark_page")
        val QURAN_FONT_SIZE = floatPreferencesKey("quran_font_size")
        val QURAN_PAPER_MODE = booleanPreferencesKey("quran_paper_mode")
    }

    val savedLocation: Flow<SavedLocation?> = context.dataStore.data.map { prefs ->
        val lat = prefs[Keys.LATITUDE]
        val lng = prefs[Keys.LONGITUDE]
        if (lat != null && lng != null) {
            SavedLocation(
                latitude = lat,
                longitude = lng,
                cityName = prefs[Keys.CITY_NAME] ?: "موقعي",
                timeZoneId = prefs[Keys.TIMEZONE_ID] ?: TimeZone.getDefault().id
            )
        } else {
            null
        }
    }

    val userSettings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            calcMethod = CalculationMethod.fromId(prefs[Keys.CALC_METHOD] ?: CalculationMethod.UMM_AL_QURA.id),
            asrSchool = AsrSchool.fromId(prefs[Keys.ASR_SCHOOL] ?: AsrSchool.SHAFI.id),
            timeFormat = TimeFormat.HOUR_12,
            athanEnabled = prefs[Keys.ATHAN_ENABLED] ?: true,
            vibrationEnabled = false,
            muezzinVoice = MuezzinVoice.fromId(prefs[Keys.MUEZZIN_VOICE]),
            fajrAthanEnabled = prefs[Keys.FAJR_ATHAN_ENABLED] ?: true,
            athanPlayMode = AthanPlayMode.fromId(prefs[Keys.ATHAN_PLAY_MODE])
        )
    }

    val tasbihState: Flow<TasbihState> = context.dataStore.data.map { prefs ->
        TasbihState(
            count = prefs[Keys.TASBIH_COUNT] ?: 0,
            dhikr = prefs[Keys.TASBIH_DHIKR] ?: "سبحان الله",
            target = prefs[Keys.TASBIH_TARGET] ?: 33
        )
    }

    val prayerAdjustments: Flow<PrayerAdjustments> = context.dataStore.data.map { prefs ->
        PrayerAdjustments(
            globalOffsetMinutes = prefs[Keys.ADJ_GLOBAL] ?: 0,
            fajrOffset = prefs[Keys.ADJ_FAJR] ?: 0,
            sunriseOffset = prefs[Keys.ADJ_SUNRISE] ?: 0,
            dhuhrOffset = prefs[Keys.ADJ_DHUHR] ?: 0,
            asrOffset = prefs[Keys.ADJ_ASR] ?: 0,
            maghribOffset = prefs[Keys.ADJ_MAGHRIB] ?: 0,
            ishaOffset = prefs[Keys.ADJ_ISHA] ?: 0
        )
    }

    val ramadanSettings: Flow<RamadanSettings> = context.dataStore.data.map { prefs ->
        RamadanSettings(
            ramadanTabEnabled = prefs[Keys.RAMADAN_TAB_ENABLED] ?: false,
            ramadanAutoInMonth = prefs[Keys.RAMADAN_AUTO_IN_MONTH] ?: true,
            imsakAlarmEnabled = prefs[Keys.RAMADAN_IMSAK_ALARM] ?: false,
            imsakLeadMinutes = prefs[Keys.RAMADAN_IMSAK_LEAD] ?: 15,
            iftarAlarmEnabled = prefs[Keys.RAMADAN_IFTAR_ALARM] ?: false,
            iftarLeadMinutes = prefs[Keys.RAMADAN_IFTAR_LEAD] ?: 0
        )
    }

    val qadaState: Flow<QadaState> = context.dataStore.data.map { prefs ->
        QadaState(
            remainingDays = (prefs[Keys.QADA_REMAINING] ?: 0).coerceAtLeast(0),
            completedDays = (prefs[Keys.QADA_COMPLETED] ?: 0).coerceAtLeast(0)
        )
    }

    val quranPrefs: Flow<QuranPrefs> = context.dataStore.data.map { prefs ->
        QuranPrefs(
            lastPage = (prefs[Keys.QURAN_LAST_PAGE] ?: 1).coerceIn(1, 604),
            bookmarkPage = (prefs[Keys.QURAN_BOOKMARK_PAGE] ?: 1).coerceIn(1, 604),
            fontSizeSp = (prefs[Keys.QURAN_FONT_SIZE] ?: 24f).coerceIn(18f, 36f),
            paperMode = prefs[Keys.QURAN_PAPER_MODE] ?: true
        )
    }

    suspend fun saveLocation(
        latitude: Double,
        longitude: Double,
        cityName: String,
        timeZoneId: String? = null
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LATITUDE] = latitude
            prefs[Keys.LONGITUDE] = longitude
            prefs[Keys.CITY_NAME] = cityName
            if (!timeZoneId.isNullOrBlank()) {
                prefs[Keys.TIMEZONE_ID] = timeZoneId
            }
        }
    }

    suspend fun updateTimeZoneId(timeZoneId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TIMEZONE_ID] = timeZoneId
        }
    }

    suspend fun updateCalcMethod(method: CalculationMethod) {
        context.dataStore.edit { prefs -> prefs[Keys.CALC_METHOD] = method.id }
    }

    suspend fun updateAsrSchool(school: AsrSchool) {
        context.dataStore.edit { prefs -> prefs[Keys.ASR_SCHOOL] = school.id }
    }

    suspend fun updateTimeFormat(format: TimeFormat) {
        context.dataStore.edit { prefs -> prefs[Keys.TIME_FORMAT] = format.code }
    }

    suspend fun updateAthanEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.ATHAN_ENABLED] = enabled }
    }

    suspend fun updateVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.VIBRATION_ENABLED] = enabled }
    }

    suspend fun updateMuezzinVoice(voice: MuezzinVoice) {
        context.dataStore.edit { prefs -> prefs[Keys.MUEZZIN_VOICE] = voice.id }
    }

    suspend fun updateFajrAthanEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.FAJR_ATHAN_ENABLED] = enabled }
    }

    suspend fun updateAthanPlayMode(mode: AthanPlayMode) {
        context.dataStore.edit { prefs -> prefs[Keys.ATHAN_PLAY_MODE] = mode.id }
    }

    suspend fun saveTasbih(state: TasbihState) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TASBIH_COUNT] = state.count
            prefs[Keys.TASBIH_DHIKR] = state.dhikr
            prefs[Keys.TASBIH_TARGET] = state.target
        }
    }

    suspend fun updateGlobalOffset(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ADJ_GLOBAL] = minutes.coerceIn(-180, 180)
        }
    }

    suspend fun updatePrayerOffset(type: PrayerType, minutes: Int) {
        val clamped = minutes.coerceIn(-60, 60)
        context.dataStore.edit { prefs ->
            val key = when (type) {
                PrayerType.FAJR -> Keys.ADJ_FAJR
                PrayerType.SUNRISE -> Keys.ADJ_SUNRISE
                PrayerType.DHUHR -> Keys.ADJ_DHUHR
                PrayerType.ASR -> Keys.ADJ_ASR
                PrayerType.MAGHRIB -> Keys.ADJ_MAGHRIB
                PrayerType.ISHA -> Keys.ADJ_ISHA
            }
            prefs[key] = clamped
        }
    }

    suspend fun resetAllOffsets() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ADJ_GLOBAL] = 0
            prefs[Keys.ADJ_FAJR] = 0
            prefs[Keys.ADJ_SUNRISE] = 0
            prefs[Keys.ADJ_DHUHR] = 0
            prefs[Keys.ADJ_ASR] = 0
            prefs[Keys.ADJ_MAGHRIB] = 0
            prefs[Keys.ADJ_ISHA] = 0
        }
    }

    suspend fun updateRamadanSettings(settings: RamadanSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.RAMADAN_TAB_ENABLED] = settings.ramadanTabEnabled
            prefs[Keys.RAMADAN_AUTO_IN_MONTH] = settings.ramadanAutoInMonth
            prefs[Keys.RAMADAN_IMSAK_ALARM] = settings.imsakAlarmEnabled
            prefs[Keys.RAMADAN_IMSAK_LEAD] = settings.imsakLeadMinutes
            prefs[Keys.RAMADAN_IFTAR_ALARM] = settings.iftarAlarmEnabled
            prefs[Keys.RAMADAN_IFTAR_LEAD] = settings.iftarLeadMinutes
        }
    }

    suspend fun saveQadaState(qada: QadaState) {
        context.dataStore.edit { prefs ->
            prefs[Keys.QADA_REMAINING] = qada.remainingDays.coerceAtLeast(0)
            prefs[Keys.QADA_COMPLETED] = qada.completedDays.coerceAtLeast(0)
        }
    }

    suspend fun updateQuranLastPage(page: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.QURAN_LAST_PAGE] = page.coerceIn(1, 604)
        }
    }

    suspend fun updateQuranBookmarkPage(page: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.QURAN_BOOKMARK_PAGE] = page.coerceIn(1, 604)
        }
    }

    suspend fun updateQuranFontSize(sizeSp: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.QURAN_FONT_SIZE] = sizeSp.coerceIn(18f, 36f)
        }
    }

    suspend fun updateQuranPaperMode(paperMode: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.QURAN_PAPER_MODE] = paperMode
        }
    }
}
