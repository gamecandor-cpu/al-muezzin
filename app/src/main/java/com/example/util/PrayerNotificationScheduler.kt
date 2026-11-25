package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.model.AladhanTimings
import com.example.data.model.MuezzinVoice
import com.example.data.model.PrayerAdjustments
import com.example.data.model.PrayerType
import com.example.data.model.ReligiousOccasion
import com.example.data.model.TimeFormat
import com.example.data.model.UserSettings
import com.example.data.remote.BroadcastReminder
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class PrayerNotificationScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun scheduleUpcomingPrayers(
        timings: AladhanTimings,
        timeZoneId: String?,
        cityName: String?,
        adjustments: PrayerAdjustments,
        settings: UserSettings
    ) {
        saveScheduleCache(timings, timeZoneId, cityName, adjustments, settings)
        PrayerNotificationReceiver.ensureAllChannels(context)

        val adj = PrayerTimeCalculator.adjustedTimings(timings, adjustments)
        scheduleAthkarAutoAlarms(adj = adj, tzId = timeZoneId)

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
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                BASE_PRAYER_REQUEST_CODE + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            scheduleExactOrInexact(triggerMs, pendingIntent)
        }

        scheduleAthkarAutoAlarms(adj = adj, tzId = timeZoneId)
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
        scheduleExactOrInexact(triggerMs, pendingIntent)
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
            scheduleExactOrInexact(triggerMs, pendingIntent)
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
        val manager = alarmManager ?: return
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

        scheduleExactOrInexact(cal.timeInMillis, pendingIntent)
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
        val fajr = prefs.getString(KEY_FAJR, null) ?: return
        val sunrise = prefs.getString(KEY_SUNRISE, null) ?: return
        val dhuhr = prefs.getString(KEY_DHUHR, null) ?: return
        val asr = prefs.getString(KEY_ASR, null) ?: return
        val maghrib = prefs.getString(KEY_MAGHRIB, null) ?: return
        val isha = prefs.getString(KEY_ISHA, null) ?: return

        val timings = AladhanTimings(
            imsak = prefs.getString(KEY_IMSAK, "").orEmpty(),
            fajr = fajr,
            sunrise = sunrise,
            dhuhr = dhuhr,
            asr = asr,
            maghrib = maghrib,
            isha = isha
        )
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
            athanEnabled = prefs.getBoolean(KEY_ATHAN_ENABLED, true),
            fajrAthanEnabled = prefs.getBoolean(KEY_FAJR_ATHAN_ENABLED, true),
            muezzinVoice = MuezzinVoice.fromId(prefs.getString(KEY_VOICE_ID, null)),
            timeFormat = TimeFormat.fromCode(prefs.getInt(KEY_TIME_FORMAT, 12))
        )
        scheduleUpcomingPrayers(
            timings = timings,
            timeZoneId = prefs.getString(KEY_TIMEZONE, null),
            cityName = prefs.getString(KEY_CITY_NAME, null),
            adjustments = adjustments,
            settings = settings
        )
        scheduleDailyOccasionAlarm()
        rescheduleRemoteRemindersFromCache()
    }

    fun markRemoteTriggerHandledIfNew(reminderId: String, triggerNonce: Long): Boolean {
        if (reminderId.isBlank()) return false
        val key = "remote_nonce_$reminderId"
        val prev = prefs.getLong(key, -1L)
        if (prev >= triggerNonce) return false
        prefs.edit().putLong(key, triggerNonce).apply()
        return true
    }

    fun syncRemoteReminders(reminders: List<BroadcastReminder>) {
        val now = System.currentTimeMillis()
        val array = JSONArray()
        reminders
            .filter {
                it.isActive &&
                    it.isNotExpired(now) &&
                    (it.deliveryMode == "daily" || it.deliveryMode == "scheduled")
            }
            .flatMap { r ->
                r.allScheduledTimes.map { timeHHmm -> r to timeHHmm }
            }
            .take(MAX_REMOTE_SLOTS)
            .forEach { (r, timeHHmm) ->
                val obj = JSONObject().apply {
                    put("id", r.id)
                    put("title", r.title)
                    put("body", r.body)
                    put("kind", r.reminderKind)
                    put("deliveryMode", r.deliveryMode)
                    put("timeHHmm", timeHHmm)
                    put("audioUrl", r.effectiveAudioUrl.orEmpty())
                    put("expiresAtMillis", r.expiresAtMillis ?: 0L)
                }
                array.put(obj)
            }
        prefs.edit().putString(KEY_REMOTE_REMINDERS_JSON, array.toString()).apply()
        rescheduleRemoteRemindersFromCache()
    }

    fun rescheduleRemoteRemindersFromCache() {
        val manager = alarmManager ?: return
        // Cancel all previous remote slots first
        for (slot in 0 until MAX_REMOTE_SLOTS) {
            val cancelIntent = Intent(context, PrayerNotificationReceiver::class.java).apply {
                action = PrayerNotificationReceiver.ACTION_REMOTE_REMINDER_ALARM
            }
            val pending = PendingIntent.getBroadcast(
                context,
                BASE_REMOTE_REQUEST_CODE + slot,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.cancel(pending)
        }

        val rawJson = prefs.getString(KEY_REMOTE_REMINDERS_JSON, null) ?: return
        val array = runCatching { JSONArray(rawJson) }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        val tz = PrayerTimeCalculator.resolveTimeZone(prefs.getString(KEY_TIMEZONE, null))
        val baseCal = Calendar.getInstance(tz).apply { timeInMillis = now }

        for (i in 0 until minOf(array.length(), MAX_REMOTE_SLOTS)) {
            val item = array.optJSONObject(i) ?: continue
            val expiresAt = item.optLong("expiresAtMillis", 0L)
            if (expiresAt > 0L && now > expiresAt) continue

            val deliveryMode = item.optString("deliveryMode", "daily")
            val timeHHmm = item.optString("timeHHmm", "08:00")
            var triggerMs = computeTimeOnDateMillis(baseCal, timeHHmm, addDays = 0)
            if (triggerMs <= now + 5_000L) {
                if (deliveryMode == "daily") {
                    triggerMs = computeTimeOnDateMillis(baseCal, timeHHmm, addDays = 1)
                } else {
                    continue
                }
            }
            if (expiresAt > 0L && triggerMs > expiresAt) continue

            val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
                action = PrayerNotificationReceiver.ACTION_REMOTE_REMINDER_ALARM
                putExtra(PrayerNotificationReceiver.EXTRA_REMINDER_ID, item.optString("id"))
                putExtra(PrayerNotificationReceiver.EXTRA_REMINDER_TITLE, item.optString("title"))
                putExtra(PrayerNotificationReceiver.EXTRA_REMINDER_BODY, item.optString("body"))
                putExtra(PrayerNotificationReceiver.EXTRA_REMINDER_KIND, item.optString("kind"))
                putExtra(PrayerNotificationReceiver.EXTRA_REMINDER_AUDIO_URL, item.optString("audioUrl"))
                putExtra(PrayerNotificationReceiver.EXTRA_REMINDER_EXPIRES_AT, expiresAt)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                BASE_REMOTE_REQUEST_CODE + i,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            scheduleExactOrInexact(triggerMs, pending)
        }
    }

    private fun saveScheduleCache(
        timings: AladhanTimings,
        timeZoneId: String?,
        cityName: String?,
        adjustments: PrayerAdjustments,
        settings: UserSettings
    ) {
        prefs.edit()
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
            .putInt(KEY_TIME_FORMAT, settings.timeFormat.code)
            .apply()
    }

    private fun scheduleExactOrInexact(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val manager = alarmManager ?: return
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
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
        private const val BASE_REMOTE_REQUEST_CODE = 4600
        private const val MAX_REMOTE_SLOTS = 30

        private const val KEY_IMSAK = "imsak"
        private const val KEY_FAJR = "fajr"
        private const val KEY_SUNRISE = "sunrise"
        private const val KEY_DHUHR = "dhuhr"
        private const val KEY_ASR = "asr"
        private const val KEY_MAGHRIB = "maghrib"
        private const val KEY_ISHA = "isha"
        private const val KEY_TIMEZONE = "timezone"
        private const val KEY_CITY_NAME = "city_name"
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
        private const val KEY_TIME_FORMAT = "time_format"
        private const val KEY_LAST_OCCASION_NOTIFIED = "last_occasion_notified"
        private const val KEY_ATHKAR_AUTO_ENABLED = "athkar_auto_enabled"
        private const val KEY_MORNING_ATHKAR_TIME_24 = "morning_athkar_time_24"
        private const val KEY_EVENING_ATHKAR_MAGHRIB_OFFSET = "evening_athkar_maghrib_offset"
        private const val KEY_REMOTE_REMINDERS_JSON = "remote_reminders_json"
    }
}
