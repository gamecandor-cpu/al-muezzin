package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.example.MainActivity
import com.example.data.model.AladhanTimings
import com.example.data.model.AsrSchool
import com.example.data.model.AthanPlayMode
import com.example.data.model.CalculationMethod
import com.example.data.model.MuezzinVoice
import com.example.data.model.PrayerAdjustments
import com.example.data.model.PrayerType
import com.example.data.model.ReligiousOccasion
import com.example.data.model.TimeFormat
import com.example.data.model.UserSettings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PrayerNotificationScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun canScheduleExactAlarms(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    fun getCachedAthanPlayMode(): AthanPlayMode {
        return AthanPlayMode.fromId(prefs.getString(KEY_ATHAN_PLAY_MODE_ID, null))
    }

    fun getOrComputeOfflineTimings(
        latitude: Double,
        longitude: Double,
        timeZoneId: String?,
        method: CalculationMethod,
        asrSchool: AsrSchool
    ): AladhanTimings {
        val todayKey = currentDateKey(timeZoneId)
        val cachedDate = prefs.getString(KEY_TIMINGS_DATE_KEY, null)
        val cachedLat = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LATITUDE_BITS, 0L))
        val cachedLng = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LONGITUDE_BITS, 0L))
        val hasCoords = prefs.getBoolean(KEY_HAS_COORDS, false)
        val sameParams = hasCoords &&
            cachedDate == todayKey &&
            kotlin.math.abs(cachedLat - latitude) < 0.01 &&
            kotlin.math.abs(cachedLng - longitude) < 0.01 &&
            prefs.getInt(KEY_CALC_METHOD_ID, -1) == method.id &&
            prefs.getInt(KEY_ASR_SCHOOL_ID, -1) == asrSchool.id

        if (sameParams) {
            val fajr = prefs.getString(KEY_FAJR, null)
            val sunrise = prefs.getString(KEY_SUNRISE, null)
            val dhuhr = prefs.getString(KEY_DHUHR, null)
            val asr = prefs.getString(KEY_ASR, null)
            val maghrib = prefs.getString(KEY_MAGHRIB, null)
            val isha = prefs.getString(KEY_ISHA, null)
            if (fajr != null && sunrise != null && dhuhr != null && asr != null && maghrib != null && isha != null) {
                return AladhanTimings(
                    imsak = prefs.getString(KEY_IMSAK, "").orEmpty(),
                    fajr = fajr,
                    sunrise = sunrise,
                    dhuhr = dhuhr,
                    asr = asr,
                    maghrib = maghrib,
                    isha = isha
                )
            }
        }

        return PrayerTimeCalculator.computeOfflineTimings(
            latitude = latitude,
            longitude = longitude,
            timeZoneId = timeZoneId,
            method = method,
            asrSchool = asrSchool
        )
    }

    fun scheduleUpcomingPrayers(
        timings: AladhanTimings,
        timeZoneId: String?,
        cityName: String?,
        adjustments: PrayerAdjustments,
        settings: UserSettings,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        saveScheduleCache(
            timings = timings,
            timeZoneId = timeZoneId,
            cityName = cityName,
            adjustments = adjustments,
            settings = settings,
            latitude = latitude,
            longitude = longitude
        )
        PrayerNotificationReceiver.ensureAllChannels(context)

        val adj = PrayerTimeCalculator.adjustedTimings(timings, adjustments)
        scheduleAthkarAutoAlarms(adj = adj, tzId = timeZoneId)
        scheduleDailyMidnightReschedule(timeZoneId)

        if (!settings.athanEnabled) {
            cancelAllPrayerAlarms()
            return
        }

        val tz = PrayerTimeCalculator.resolveTimeZone(timeZoneId)
        val now = System.currentTimeMillis()
        val baseCal = Calendar.getInstance(tz).apply { timeInMillis = now }

        val prayers = listOf(
            PrayerType.FAJR to PrayerTimeCalculator.cleanTime24(adj.fajr),
            PrayerType.DHUHR to PrayerTimeCalculator.cleanTime24(adj.dhuhr),
            PrayerType.ASR to PrayerTimeCalculator.cleanTime24(adj.asr),
            PrayerType.MAGHRIB to PrayerTimeCalculator.cleanTime24(adj.maghrib),
            PrayerType.ISHA to PrayerTimeCalculator.cleanTime24(adj.isha)
        )

        prayers.forEachIndexed { index, (type, time24) ->
            var triggerMs = computeTimeOnDateMillis(baseCal, time24, addDays = 0)
            if (triggerMs <= now + 5_000L) {
                triggerMs = computeTimeOnDateMillis(baseCal, time24, addDays = 1)
            }

            val voice = if (type == PrayerType.FAJR && settings.fajrAthanEnabled) {
                MuezzinVoice.FAJR_SPECIAL
            } else {
                settings.muezzinVoice
            }

            val formattedTime = PrayerTimeCalculator.formatTime(time24, settings.timeFormat)
            val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
                action = PrayerNotificationReceiver.ACTION_PRAYER_ALERT
                putExtra(PrayerNotificationReceiver.EXTRA_PRAYER_KEY, type.apiKey)
                putExtra(PrayerNotificationReceiver.EXTRA_PRAYER_NAME_AR, type.arabicName)
                putExtra(PrayerNotificationReceiver.EXTRA_CITY_NAME, cityName.orEmpty())
                putExtra(PrayerNotificationReceiver.EXTRA_TIME_LABEL, formattedTime)
                putExtra(PrayerNotificationReceiver.EXTRA_VOICE_ID, voice.id)
                putExtra(PrayerNotificationReceiver.EXTRA_ATHAN_ENABLED, settings.athanEnabled)
                putExtra(PrayerNotificationReceiver.EXTRA_ATHAN_PLAY_MODE_ID, settings.athanPlayMode.id)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                BASE_PRAYER_REQUEST_CODE + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            scheduleExactOrInexact(triggerMs, pendingIntent, useAlarmClock = true)
        }
    }

    fun isAthkarAutoPlayEnabled(): Boolean = prefs.getBoolean(KEY_ATHKAR_AUTO_ENABLED, true)

    fun setAthkarAutoPlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ATHKAR_AUTO_ENABLED, enabled).apply()
        if (enabled) {
            rescheduleFromCache()
        } else {
            cancelAthkarAutoAlarms()
        }
    }

    fun getAthkarScheduleConfig(): AthkarScheduleConfig {
        val morning = prefs.getString(
            KEY_MORNING_ATHKAR_TIME_24,
            AthkarScheduleConfig.DEFAULT_MORNING_TIME_24
        ) ?: AthkarScheduleConfig.DEFAULT_MORNING_TIME_24
        val eveningOffset = prefs.getInt(
            KEY_EVENING_ATHKAR_MAGHRIB_OFFSET,
            AthkarScheduleConfig.DEFAULT_EVENING_OFFSET_FROM_MAGHRIB
        )
        return AthkarScheduleConfig(
            morningTime24 = PrayerTimeCalculator.cleanTime24(morning)
                .ifBlank { AthkarScheduleConfig.DEFAULT_MORNING_TIME_24 },
            eveningOffsetFromMaghribMinutes = eveningOffset.coerceIn(-180, 120)
        )
    }

    fun setAthkarScheduleConfig(config: AthkarScheduleConfig) {
        val sanitizedMorning = PrayerTimeCalculator.cleanTime24(config.morningTime24)
            .ifBlank { AthkarScheduleConfig.DEFAULT_MORNING_TIME_24 }
        val sanitizedOffset = config.eveningOffsetFromMaghribMinutes.coerceIn(-180, 120)
        prefs.edit()
            .putString(KEY_MORNING_ATHKAR_TIME_24, sanitizedMorning)
            .putInt(KEY_EVENING_ATHKAR_MAGHRIB_OFFSET, sanitizedOffset)
            .apply()
        if (prefs.getString(KEY_FAJR, null) != null) {
            rescheduleFromCache()
        } else {
            scheduleMorningAthkarOnly(sanitizedMorning, prefs.getString(KEY_TIMEZONE, null))
        }
    }

    fun scheduleMorningAthkarOnlyIfEnabled() {
        val tzId = prefs.getString(KEY_TIMEZONE, null)
        if (prefs.getString(KEY_FAJR, null) != null || prefs.getBoolean(KEY_HAS_COORDS, false)) {
            rescheduleFromCache()
        } else {
            scheduleMorningAthkarOnly(getAthkarScheduleConfig().morningTime24, tzId)
            scheduleDailyMidnightReschedule(tzId)
            scheduleDailyOccasionAlarm(tzId)
        }
    }

    private fun scheduleMorningAthkarOnly(morningTime24: String, tzId: String?) {
        if (!isAthkarAutoPlayEnabled()) {
            cancelAthkarAutoAlarms()
            return
        }
        val tz = PrayerTimeCalculator.resolveTimeZone(tzId)
        val now = System.currentTimeMillis()
        val baseCal = Calendar.getInstance(tz).apply { timeInMillis = now }
        var triggerMs = computeTimeOnDateMillis(baseCal, morningTime24, addDays = 0)
        if (triggerMs <= now + 5_000L) {
            triggerMs = computeTimeOnDateMillis(baseCal, morningTime24, addDays = 1)
        }
        val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
            action = PrayerNotificationReceiver.ACTION_ATHKAR_MORNING
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ATHKAR_MORNING_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExactOrInexact(triggerMs, pendingIntent, useAlarmClock = true)
    }

    fun scheduleAthkarAutoAlarms(
        adj: AladhanTimings,
        tzId: String?
    ) {
        if (!isAthkarAutoPlayEnabled()) {
            cancelAthkarAutoAlarms()
            return
        }
        val tz = PrayerTimeCalculator.resolveTimeZone(tzId)
        val now = System.currentTimeMillis()
        val baseCal = Calendar.getInstance(tz).apply { timeInMillis = now }

        val (morningTime24, eveningTime24) = PrayerTimeCalculator.computeAthkarScheduleTimes24(
            adjTimings = adj,
            config = getAthkarScheduleConfig()
        )

        listOf(
            Triple(ATHKAR_MORNING_REQUEST_CODE, PrayerNotificationReceiver.ACTION_ATHKAR_MORNING, morningTime24),
            Triple(ATHKAR_EVENING_REQUEST_CODE, PrayerNotificationReceiver.ACTION_ATHKAR_EVENING, eveningTime24)
        ).forEach { (reqCode, actionStr, time24) ->
            var triggerMs = computeTimeOnDateMillis(baseCal, time24, addDays = 0)
            if (triggerMs <= now + 5_000L) {
                triggerMs = computeTimeOnDateMillis(baseCal, time24, addDays = 1)
            }
            val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
                action = actionStr
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            scheduleExactOrInexact(triggerMs, pendingIntent, useAlarmClock = true)
        }
    }

    fun cancelAthkarAutoAlarms() {
        val manager = alarmManager ?: return
        listOf(
            ATHKAR_MORNING_REQUEST_CODE to PrayerNotificationReceiver.ACTION_ATHKAR_MORNING,
            ATHKAR_EVENING_REQUEST_CODE to PrayerNotificationReceiver.ACTION_ATHKAR_EVENING
        ).forEach { (reqCode, actionStr) ->
            val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
                action = actionStr
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.cancel(pendingIntent)
        }
    }

    fun cancelAllPrayerAlarms() {
        val manager = alarmManager ?: return
        for (index in 0 until 5) {
            val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
                action = PrayerNotificationReceiver.ACTION_PRAYER_ALERT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                BASE_PRAYER_REQUEST_CODE + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.cancel(pendingIntent)
        }
    }

    fun scheduleDailyOccasionAlarm(timeZoneId: String? = prefs.getString(KEY_TIMEZONE, null)) {
        if (alarmManager == null) return
        val tz = PrayerTimeCalculator.resolveTimeZone(timeZoneId)
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now + 5_000L) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
            action = PrayerNotificationReceiver.ACTION_OCCASION_DAILY_CHECK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            OCCASION_DAILY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactOrInexact(cal.timeInMillis, pendingIntent, useAlarmClock = false)
    }

    fun scheduleDailyMidnightReschedule(timeZoneId: String? = prefs.getString(KEY_TIMEZONE, null)) {
        if (alarmManager == null) return
        val tz = PrayerTimeCalculator.resolveTimeZone(timeZoneId)
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 2)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now + 5_000L) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
            action = PrayerNotificationReceiver.ACTION_MIDNIGHT_RESCHEDULE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            MIDNIGHT_RESCHEDULE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExactOrInexact(cal.timeInMillis, pendingIntent, useAlarmClock = false)
    }

    fun resolveCurrentActiveOccasion(): ReligiousOccasion? {
        val tzId = prefs.getString(KEY_TIMEZONE, null)
        val year = PrayerTimeCalculator.currentHijriYearFallback(tzId)
        val month = PrayerTimeCalculator.currentHijriMonthFallback(tzId)
        val day = PrayerTimeCalculator.currentHijriDayFallback(tzId)
        return PrayerTimeCalculator.computeReligiousOccasions(year, month, day)
            .firstOrNull { it.isActiveToday }
    }

    fun markOccasionNotifiedIfNeeded(
        occasion: ReligiousOccasion,
        hijriYear: Int = PrayerTimeCalculator.currentHijriYearFallback(prefs.getString(KEY_TIMEZONE, null))
    ): Boolean {
        val stamp = "${hijriYear}_${occasion.id}_${occasion.activeDayNumber}"
        val lastStamp = prefs.getString(KEY_LAST_OCCASION_NOTIFIED, null)
        if (lastStamp == stamp) return false
        prefs.edit().putString(KEY_LAST_OCCASION_NOTIFIED, stamp).apply()
        return true
    }

    fun rescheduleFromCache() {
        val tzId = prefs.getString(KEY_TIMEZONE, null)
        val todayKey = currentDateKey(tzId)
        val cachedDateKey = prefs.getString(KEY_TIMINGS_DATE_KEY, null)
        val hasCoords = prefs.getBoolean(KEY_HAS_COORDS, false)
        val calcMethod = CalculationMethod.fromId(prefs.getInt(KEY_CALC_METHOD_ID, CalculationMethod.UMM_AL_QURA.id))
        val asrSchool = AsrSchool.fromId(prefs.getInt(KEY_ASR_SCHOOL_ID, AsrSchool.SHAFI.id))

        val timings: AladhanTimings? = if (hasCoords && cachedDateKey != todayKey) {
            val lat = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LATITUDE_BITS, 0L))
            val lng = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LONGITUDE_BITS, 0L))
            PrayerTimeCalculator.computeOfflineTimings(
                latitude = lat,
                longitude = lng,
                timeZoneId = tzId,
                method = calcMethod,
                asrSchool = asrSchool
            )
        } else {
            val fajr = prefs.getString(KEY_FAJR, null)
            val sunrise = prefs.getString(KEY_SUNRISE, null)
            val dhuhr = prefs.getString(KEY_DHUHR, null)
            val asr = prefs.getString(KEY_ASR, null)
            val maghrib = prefs.getString(KEY_MAGHRIB, null)
            val isha = prefs.getString(KEY_ISHA, null)
            if (fajr != null && sunrise != null && dhuhr != null && asr != null && maghrib != null && isha != null) {
                AladhanTimings(
                    imsak = prefs.getString(KEY_IMSAK, "").orEmpty(),
                    fajr = fajr,
                    sunrise = sunrise,
                    dhuhr = dhuhr,
                    asr = asr,
                    maghrib = maghrib,
                    isha = isha
                )
            } else if (hasCoords) {
                val lat = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LATITUDE_BITS, 0L))
                val lng = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LONGITUDE_BITS, 0L))
                PrayerTimeCalculator.computeOfflineTimings(
                    latitude = lat,
                    longitude = lng,
                    timeZoneId = tzId,
                    method = calcMethod,
                    asrSchool = asrSchool
                )
            } else {
                null
            }
        }

        if (timings != null) {
            val adjustments = PrayerAdjustments(
                globalOffsetMinutes = prefs.getInt(KEY_ADJ_GLOBAL, 0),
                fajrOffset = prefs.getInt(KEY_ADJ_FAJR, 0),
                sunriseOffset = prefs.getInt(KEY_ADJ_SUNRISE, 0),
                dhuhrOffset = prefs.getInt(KEY_ADJ_DHUHR, 0),
                asrOffset = prefs.getInt(KEY_ADJ_ASR, 0),
                maghribOffset = prefs.getInt(KEY_ADJ_MAGHRIB, 0),
                ishaOffset = prefs.getInt(KEY_ADJ_ISHA, 0)
            )
            val settings = UserSettings(
                calcMethod = calcMethod,
                asrSchool = asrSchool,
                athanEnabled = prefs.getBoolean(KEY_ATHAN_ENABLED, true),
                fajrAthanEnabled = prefs.getBoolean(KEY_FAJR_ATHAN_ENABLED, true),
                muezzinVoice = MuezzinVoice.fromId(prefs.getString(KEY_VOICE_ID, null)),
                athanPlayMode = getCachedAthanPlayMode(),
                timeFormat = TimeFormat.fromCode(prefs.getInt(KEY_TIME_FORMAT, 12))
            )
            scheduleUpcomingPrayers(
                timings = timings,
                timeZoneId = tzId,
                cityName = prefs.getString(KEY_CITY_NAME, null),
                adjustments = adjustments,
                settings = settings
            )
        } else {
            scheduleMorningAthkarOnly(getAthkarScheduleConfig().morningTime24, tzId)
            scheduleDailyMidnightReschedule(tzId)
        }

        scheduleDailyOccasionAlarm(tzId)
        schedulePortalReminders(PortalReminderStore.loadAll(context), tzId)
    }

    fun schedulePortalReminders(
        reminders: List<PortalReminder>,
        timeZoneId: String? = prefs.getString(KEY_TIMEZONE, null)
    ) {
        val manager = alarmManager ?: return
        val prevCount = prefs.getInt(KEY_PORTAL_ALARM_COUNT, 0)
        for (i in 0 until prevCount) {
            val cancelIntent = Intent(context, PrayerNotificationReceiver::class.java).apply {
                action = PrayerNotificationReceiver.ACTION_PORTAL_REMINDER_ALARM
            }
            val pi = PendingIntent.getBroadcast(
                context,
                BASE_PORTAL_REMINDER_REQUEST_CODE + i,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.cancel(pi)
        }

        val tz = PrayerTimeCalculator.resolveTimeZone(timeZoneId)
        val now = System.currentTimeMillis()
        val baseCal = Calendar.getInstance(tz).apply { timeInMillis = now }
        val activeScheduled = reminders.filter {
            it.isActive && !it.isExpired(now) && (it.deliveryMode == "scheduled" || it.deliveryMode == "daily")
        }

        activeScheduled.forEachIndexed { index, reminder ->
            val time24 = PrayerTimeCalculator.cleanTime24(reminder.scheduledTimeHHmm).ifBlank { "08:00" }
            var triggerMs = computeTimeOnDateMillis(baseCal, time24, addDays = 0)
            if (triggerMs <= now + 5_000L) {
                triggerMs = computeTimeOnDateMillis(baseCal, time24, addDays = 1)
            }
            val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
                action = PrayerNotificationReceiver.ACTION_PORTAL_REMINDER_ALARM
                putExtra(PrayerNotificationReceiver.EXTRA_PORTAL_REMINDER_ID, reminder.id)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                BASE_PORTAL_REMINDER_REQUEST_CODE + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            scheduleExactOrInexact(triggerMs, pi, useAlarmClock = true)
        }
        prefs.edit().putInt(KEY_PORTAL_ALARM_COUNT, activeScheduled.size).apply()
    }

    private fun saveScheduleCache(
        timings: AladhanTimings,
        timeZoneId: String?,
        cityName: String?,
        adjustments: PrayerAdjustments,
        settings: UserSettings,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        val editor = prefs.edit()
            .putString(KEY_IMSAK, timings.imsak)
            .putString(KEY_FAJR, timings.fajr)
            .putString(KEY_SUNRISE, timings.sunrise)
            .putString(KEY_DHUHR, timings.dhuhr)
            .putString(KEY_ASR, timings.asr)
            .putString(KEY_MAGHRIB, timings.maghrib)
            .putString(KEY_ISHA, timings.isha)
            .putString(KEY_TIMEZONE, timeZoneId)
            .putString(KEY_CITY_NAME, cityName)
            .putInt(KEY_ADJ_GLOBAL, adjustments.globalOffsetMinutes)
            .putInt(KEY_ADJ_FAJR, adjustments.fajrOffset)
            .putInt(KEY_ADJ_SUNRISE, adjustments.sunriseOffset)
            .putInt(KEY_ADJ_DHUHR, adjustments.dhuhrOffset)
            .putInt(KEY_ADJ_ASR, adjustments.asrOffset)
            .putInt(KEY_ADJ_MAGHRIB, adjustments.maghribOffset)
            .putInt(KEY_ADJ_ISHA, adjustments.ishaOffset)
            .putBoolean(KEY_ATHAN_ENABLED, settings.athanEnabled)
            .putBoolean(KEY_FAJR_ATHAN_ENABLED, settings.fajrAthanEnabled)
            .putString(KEY_VOICE_ID, settings.muezzinVoice.id)
            .putString(KEY_ATHAN_PLAY_MODE_ID, settings.athanPlayMode.id)
            .putInt(KEY_TIME_FORMAT, settings.timeFormat.code)
            .putInt(KEY_CALC_METHOD_ID, settings.calcMethod.id)
            .putInt(KEY_ASR_SCHOOL_ID, settings.asrSchool.id)
            .putString(KEY_TIMINGS_DATE_KEY, currentDateKey(timeZoneId))

        if (latitude != null && longitude != null) {
            editor
                .putBoolean(KEY_HAS_COORDS, true)
                .putLong(KEY_LATITUDE_BITS, java.lang.Double.doubleToRawLongBits(latitude))
                .putLong(KEY_LONGITUDE_BITS, java.lang.Double.doubleToRawLongBits(longitude))
        }
        editor.apply()
    }

    private fun currentDateKey(timeZoneId: String?): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = PrayerTimeCalculator.resolveTimeZone(timeZoneId)
        return sdf.format(Date())
    }

    private fun scheduleExactOrInexact(
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
        useAlarmClock: Boolean
    ) {
        val manager = alarmManager ?: return
        val canExact = canScheduleExactAlarms()
        if (useAlarmClock && canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val showIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val showPending = PendingIntent.getActivity(
                context,
                9100,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val scheduledAlarmClock = runCatching {
                manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showPending),
                    pendingIntent
                )
            }.isSuccess
            if (scheduledAlarmClock) return
        }

        if (canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }.onFailure {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            manager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun computeTimeOnDateMillis(baseCal: Calendar, time24: String, addDays: Int): Long {
        val parts = time24.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return (baseCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, addDays)
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    companion object {
        private const val PREFS_NAME = "muezzin_alarm_cache"
        private const val BASE_PRAYER_REQUEST_CODE = 4200
        private const val OCCASION_DAILY_REQUEST_CODE = 4301
        private const val ATHKAR_MORNING_REQUEST_CODE = 4302
        private const val ATHKAR_EVENING_REQUEST_CODE = 4303
        private const val MIDNIGHT_RESCHEDULE_REQUEST_CODE = 4304
        private const val BASE_PORTAL_REMINDER_REQUEST_CODE = 4600
        private const val KEY_PORTAL_ALARM_COUNT = "portal_alarm_count"

        private const val KEY_IMSAK = "imsak"
        private const val KEY_FAJR = "fajr"
        private const val KEY_SUNRISE = "sunrise"
        private const val KEY_DHUHR = "dhuhr"
        private const val KEY_ASR = "asr"
        private const val KEY_MAGHRIB = "maghrib"
        private const val KEY_ISHA = "isha"
        private const val KEY_TIMEZONE = "timezone"
        private const val KEY_CITY_NAME = "city_name"
        private const val KEY_HAS_COORDS = "has_coords"
        private const val KEY_LATITUDE_BITS = "latitude_bits"
        private const val KEY_LONGITUDE_BITS = "longitude_bits"
        private const val KEY_CALC_METHOD_ID = "calc_method_id"
        private const val KEY_ASR_SCHOOL_ID = "asr_school_id"
        private const val KEY_TIMINGS_DATE_KEY = "timings_date_key"
        private const val KEY_ADJ_GLOBAL = "adj_global"
        private const val KEY_ADJ_FAJR = "adj_fajr"
        private const val KEY_ADJ_SUNRISE = "adj_sunrise"
        private const val KEY_ADJ_DHUHR = "adj_dhuhr"
        private const val KEY_ADJ_ASR = "adj_asr"
        private const val KEY_ADJ_MAGHRIB = "adj_maghrib"
        private const val KEY_ADJ_ISHA = "adj_isha"
        private const val KEY_ATHAN_ENABLED = "athan_enabled"
        private const val KEY_FAJR_ATHAN_ENABLED = "fajr_athan_enabled"
        private const val KEY_VOICE_ID = "voice_id"
        private const val KEY_ATHAN_PLAY_MODE_ID = "athan_play_mode_id"
        private const val KEY_TIME_FORMAT = "time_format"
        private const val KEY_LAST_OCCASION_NOTIFIED = "last_occasion_notified"
        private const val KEY_ATHKAR_AUTO_ENABLED = "athkar_auto_enabled"
        private const val KEY_MORNING_ATHKAR_TIME_24 = "morning_athkar_time_24"
        private const val KEY_EVENING_ATHKAR_MAGHRIB_OFFSET = "evening_athkar_maghrib_offset"
    }
}
