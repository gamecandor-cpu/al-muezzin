package com.example.util

import android.icu.util.IslamicCalendar
import com.example.data.model.AladhanTimings
import com.example.data.model.PrayerAdjustments
import com.example.data.model.PrayerTimeItem
import com.example.data.model.PrayerType
import com.example.data.model.ReligiousOccasion
import com.example.data.model.TimeFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

enum class CelestialPhase {
    MORNING_SUNRISE,
    DAY_SUN,
    MAGHRIB_SUNSET,
    NIGHT_MOON
}

data class NextPrayerStatus(
    val nextPrayer: PrayerType,
    val countdownFormatted: String,
    val progress: Float,
    val remainingFraction: Float,
    val millisRemaining: Long,
    val celestialPhase: CelestialPhase
)

data class RamadanStatus(
    val imsakFormatted: String,
    val suhoorFormatted: String,
    val fajrFormatted: String,
    val iftarFormatted: String,
    val imsakTime24: String,
    val iftarTime24: String,
    val isFastingWindow: Boolean,
    val countdownLabel: String,
    val countdownFormatted: String,
    val progress: Float,
    val nextImsakTriggerMillis: Long,
    val nextIftarTriggerMillis: Long
)

data class AthkarScheduleConfig(
    val morningTime24: String = DEFAULT_MORNING_TIME_24,
    val eveningOffsetFromMaghribMinutes: Int = DEFAULT_EVENING_OFFSET_FROM_MAGHRIB
) {
    fun eveningRelationLabelAr(): String {
        val absMin = kotlin.math.abs(eveningOffsetFromMaghribMinutes)
        return when {
            eveningOffsetFromMaghribMinutes == -60 -> "قبل المغرب بساعة"
            eveningOffsetFromMaghribMinutes < 0 -> "قبل المغرب بـ $absMin د"
            eveningOffsetFromMaghribMinutes == 0 -> "عند أذان المغرب"
            eveningOffsetFromMaghribMinutes == 60 -> "بعد المغرب بساعة"
            else -> "بعد المغرب بـ $absMin د"
        }
    }

    companion object {
        const val DEFAULT_MORNING_TIME_24 = "06:00"
        const val DEFAULT_EVENING_OFFSET_FROM_MAGHRIB = -60
    }
}

object PrayerTimeCalculator {

    private const val KAABA_LAT = 21.4225
    private const val KAABA_LNG = 39.8262

    fun currentHijriDayFallback(timeZoneId: String? = null): Int {
        return runCatching {
            val cal = if (!timeZoneId.isNullOrBlank()) {
                IslamicCalendar(android.icu.util.TimeZone.getTimeZone(timeZoneId))
            } else {
                IslamicCalendar()
            }
            cal.get(IslamicCalendar.DAY_OF_MONTH).coerceIn(1, 30)
        }.getOrDefault(14)
    }

    fun currentHijriMonthFallback(timeZoneId: String? = null): Int {
        return runCatching {
            val cal = if (!timeZoneId.isNullOrBlank()) {
                IslamicCalendar(android.icu.util.TimeZone.getTimeZone(timeZoneId))
            } else {
                IslamicCalendar()
            }
            (cal.get(IslamicCalendar.MONTH) + 1).coerceIn(1, 12)
        }.getOrDefault(1)
    }

    fun currentHijriYearFallback(timeZoneId: String? = null): Int {
        return runCatching {
            val cal = if (!timeZoneId.isNullOrBlank()) {
                IslamicCalendar(android.icu.util.TimeZone.getTimeZone(timeZoneId))
            } else {
                IslamicCalendar()
            }
            cal.get(IslamicCalendar.YEAR).coerceAtLeast(1440)
        }.getOrDefault(1447)
    }

    private data class OccasionSpec(
        val id: String,
        val titleAr: String,
        val hijriDateLabel: String,
        val month: Int,
        val day: Int,
        val durationDays: Int = 1,
        val isEid: Boolean = false,
        val greetingTitleAr: String,
        val greetingMessageAr: String,
        val eidDayLabels: List<String> = emptyList()
    )

    private val RELIGIOUS_OCCASION_SPECS = listOf(
        OccasionSpec(
            id = "islamic_new_year",
            titleAr = "رأس السنة الهجرية",
            hijriDateLabel = "١ محرم",
            month = 1,
            day = 1,
            greetingTitleAr = "عام هجري جديد مبارك",
            greetingMessageAr = "كل عام وأنتم بخير، نسأل الله أن يجعله عام خير وبركة وطاعة وأمان."
        ),
        OccasionSpec(
            id = "ashura",
            titleAr = "يوم عاشوراء",
            hijriDateLabel = "١٠ محرم",
            month = 1,
            day = 10,
            greetingTitleAr = "يوم عاشوراء المبارك",
            greetingMessageAr = "صيام يوم عاشوراء يكفّر السنة الماضية بإذن الله؛ تقبّل الله صيامكم وذكركم."
        ),
        OccasionSpec(
            id = "mawlid",
            titleAr = "المولد النبوي الشريف",
            hijriDateLabel = "١٢ ربيع الأول",
            month = 3,
            day = 12,
            greetingTitleAr = "ذكرى المولد النبوي الشريف",
            greetingMessageAr = "اللهم صلِّ وسلّم وبارك على نبينا محمد وعلى آله وصحبه أجمعين."
        ),
        OccasionSpec(
            id = "isra_miraj",
            titleAr = "ليلة الإسراء والمعراج",
            hijriDateLabel = "٢٧ رجب",
            month = 7,
            day = 27,
            greetingTitleAr = "ذكرى الإسراء والمعراج المباركة",
            greetingMessageAr = "ذكرى معجزة الإسراء والمعراج، تقبّل الله طاعاتكم وبارك لكم في أوقاتكم."
        ),
        OccasionSpec(
            id = "mid_shaban",
            titleAr = "ليلة النصف من شعبان",
            hijriDateLabel = "١٥ شعبان",
            month = 8,
            day = 15,
            greetingTitleAr = "ليلة النصف من شعبان",
            greetingMessageAr = "نسأل الله أن يغفر لنا ولكم ويبلغنا وإياكم شهر رمضان المبارك بالخير والبركة."
        ),
        OccasionSpec(
            id = "ramadan_start",
            titleAr = "غرة شهر رمضان المبارك",
            hijriDateLabel = "١ رمضان",
            month = 9,
            day = 1,
            greetingTitleAr = "مبارك عليكم شهر رمضان",
            greetingMessageAr = "أهلّ الله علينا وعليكم الشهر الكريم بالأمن والإيمان وأعاننا على صيامه وقيامه."
        ),
        OccasionSpec(
            id = "laylat_al_qadr",
            titleAr = "ليلة القدر (ليلة ٢٧ رمضان)",
            hijriDateLabel = "٢٧ رمضان",
            month = 9,
            day = 27,
            greetingTitleAr = "ليلة القدر المباركة",
            greetingMessageAr = "ليلة القدر خير من ألف شهر؛ اللهم إنك عفو تحب العفو فاعفُ عنّا."
        ),
        OccasionSpec(
            id = "eid_al_fitr",
            titleAr = "عيد الفطر المبارك",
            hijriDateLabel = "١ - ٣ شوال",
            month = 10,
            day = 1,
            durationDays = 3,
            isEid = true,
            greetingTitleAr = "عيد فطر مبارك",
            greetingMessageAr = "عيد فطر سعيد؛ تقبّل الله منّا ومنكم الصيام والقيام وصالح الأعمال وكل عام وأنتم بخير.",
            eidDayLabels = listOf(
                "أول أيام عيد الفطر المبارك",
                "ثاني أيام عيد الفطر المبارك",
                "ثالث أيام عيد الفطر المبارك"
            )
        ),
        OccasionSpec(
            id = "arafah",
            titleAr = "يوم عرفة",
            hijriDateLabel = "٩ ذو الحجة",
            month = 12,
            day = 9,
            greetingTitleAr = "يوم عرفة المبارك",
            greetingMessageAr = "خير الدعاء دعاء يوم عرفة، وصيامه يكفّر السنة الماضية والباقية؛ تقبّل الله طاعاتكم."
        ),
        OccasionSpec(
            id = "eid_al_adha",
            titleAr = "عيد الأضحى المبارك",
            hijriDateLabel = "١٠ - ١٢ ذو الحجة",
            month = 12,
            day = 10,
            durationDays = 3,
            isEid = true,
            greetingTitleAr = "عيد أضحى مبارك",
            greetingMessageAr = "عيد أضحى سعيد؛ كل عام وأنتم إلى الله أقرب وتقبّل الله طاعاتكم وأضاحيكم.",
            eidDayLabels = listOf(
                "أول أيام عيد الأضحى المبارك",
                "ثاني أيام عيد الأضحى المبارك",
                "ثالث أيام عيد الأضحى المبارك"
            )
        )
    )

    private val HIJRI_DAYS_BEFORE_MONTH = intArrayOf(
        0, 30, 59, 89, 118, 148, 177, 207, 236, 266, 295, 325
    )

    private fun hijriEpochDay(year: Int, month: Int, day: Int): Int {
        val safeYear = year.coerceAtLeast(1)
        val safeMonth = month.coerceIn(1, 12)
        val safeDay = day.coerceIn(1, 30)
        val daysBeforeYear = 354 * (safeYear - 1) + ((3 + 11 * safeYear) / 30)
        return daysBeforeYear + HIJRI_DAYS_BEFORE_MONTH[safeMonth - 1] + safeDay
    }

    fun computeReligiousOccasions(
        hijriYear: Int,
        hijriMonth: Int,
        hijriDay: Int
    ): List<ReligiousOccasion> {
        val todayEpoch = hijriEpochDay(hijriYear, hijriMonth, hijriDay)

        return RELIGIOUS_OCCASION_SPECS.map { spec ->
            val currentYearDiff = hijriEpochDay(hijriYear, spec.month, spec.day) - todayEpoch
            val prevYearDiff = hijriEpochDay(hijriYear - 1, spec.month, spec.day) - todayEpoch
            val activeRange = -(spec.durationDays - 1)..0

            val (isActiveToday, activeDayNum, daysRemaining) = when {
                currentYearDiff in activeRange -> Triple(true, -currentYearDiff + 1, 0)
                prevYearDiff in activeRange -> Triple(true, -prevYearDiff + 1, 0)
                currentYearDiff > 0 -> Triple(false, 0, currentYearDiff)
                else -> {
                    val nextYearDiff = (hijriEpochDay(hijriYear + 1, spec.month, spec.day) - todayEpoch)
                        .coerceAtLeast(1)
                    Triple(false, 0, nextYearDiff)
                }
            }

            ReligiousOccasion(
                id = spec.id,
                titleAr = spec.titleAr,
                hijriDateLabel = spec.hijriDateLabel,
                hijriMonth = spec.month,
                hijriDay = spec.day,
                durationDays = spec.durationDays,
                isEid = spec.isEid,
                greetingTitleAr = spec.greetingTitleAr,
                greetingMessageAr = spec.greetingMessageAr,
                eidDayLabels = spec.eidDayLabels,
                daysRemaining = daysRemaining,
                isActiveToday = isActiveToday,
                activeDayNumber = activeDayNum
            )
        }.sortedWith(
            compareBy<ReligiousOccasion> { !it.isActiveToday }
                .thenBy { it.daysRemaining }
        )
    }

    fun isRamadanMonth(hijriMonthNumber: Int, hijriMonthAr: String? = null): Boolean {
        if (hijriMonthNumber == 9) return true
        val normalized = hijriMonthAr
            ?.replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            ?.trim()
            ?: return false
        return normalized.contains("رمضان")
    }

    fun resolveTimeZone(timeZoneId: String?): TimeZone {
        if (timeZoneId.isNullOrBlank()) return TimeZone.getDefault()
        return TimeZone.getTimeZone(timeZoneId)
    }

    fun cleanTime24(raw: String): String {
        return raw.trim().substringBefore(" ").take(5)
    }

    fun shiftTime24(rawTime24: String, deltaMinutes: Int): String {
        val clean = cleanTime24(rawTime24)
        val parts = clean.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return clean
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return clean
        val total = ((h * 60 + m + deltaMinutes) % 1440 + 1440) % 1440
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
    }

    fun computeAthkarScheduleTimes24(
        adjTimings: AladhanTimings,
        config: AthkarScheduleConfig = AthkarScheduleConfig()
    ): Pair<String, String> {
        val cleanMorning = cleanTime24(config.morningTime24).ifBlank {
            AthkarScheduleConfig.DEFAULT_MORNING_TIME_24
        }
        val evening24 = shiftTime24(adjTimings.maghrib, config.eveningOffsetFromMaghribMinutes)
        return cleanMorning to evening24
    }

    fun formatCurrentCityTime(
        nowMillis: Long,
        timeZoneId: String?,
        format: TimeFormat
    ): String {
        val cal = Calendar.getInstance(resolveTimeZone(timeZoneId)).apply {
            timeInMillis = nowMillis
        }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return formatHourMinute(hour, minute, format)
    }

    fun formatTime(rawTime24: String, format: TimeFormat): String {
        val clean = cleanTime24(rawTime24)
        val parts = clean.split(":")
        if (parts.size != 2) return "--"
        val hour = parts[0].toIntOrNull() ?: return "--"
        val minute = parts[1].toIntOrNull() ?: return "--"
        return formatHourMinute(hour, minute, format)
    }

    private fun formatHourMinute(hour: Int, minute: Int, format: TimeFormat): String {
        return if (format == TimeFormat.HOUR_24) {
            String.format(Locale.US, "%02d:%02d", hour, minute)
        } else {
            val period = if (hour >= 12) "م" else "ص"
            val hour12 = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            String.format(Locale.US, "%d:%02d %s", hour12, minute, period)
        }
    }

    fun adjustedTimings(
        timings: AladhanTimings,
        adjustments: PrayerAdjustments
    ): AladhanTimings {
        val fajrShifted = shiftTime24(timings.fajr, adjustments.totalOffsetFor(PrayerType.FAJR))
        val imsakBase = if (timings.imsak.isNotBlank()) {
            shiftTime24(timings.imsak, adjustments.totalOffsetFor(PrayerType.FAJR))
        } else {
            shiftTime24(fajrShifted, -10)
        }
        return AladhanTimings(
            imsak = imsakBase,
            fajr = fajrShifted,
            sunrise = shiftTime24(timings.sunrise, adjustments.totalOffsetFor(PrayerType.SUNRISE)),
            dhuhr = shiftTime24(timings.dhuhr, adjustments.totalOffsetFor(PrayerType.DHUHR)),
            asr = shiftTime24(timings.asr, adjustments.totalOffsetFor(PrayerType.ASR)),
            maghrib = shiftTime24(timings.maghrib, adjustments.totalOffsetFor(PrayerType.MAGHRIB)),
            isha = shiftTime24(timings.isha, adjustments.totalOffsetFor(PrayerType.ISHA))
        )
    }

    fun buildPrayerItems(
        timings: AladhanTimings,
        timeFormat: TimeFormat,
        nextPrayer: PrayerType?,
        adjustments: PrayerAdjustments = PrayerAdjustments()
    ): List<PrayerTimeItem> {
        val adj = adjustedTimings(timings, adjustments)
        val pairs = listOf(
            PrayerType.FAJR to adj.fajr,
            PrayerType.SUNRISE to adj.sunrise,
            PrayerType.DHUHR to adj.dhuhr,
            PrayerType.ASR to adj.asr,
            PrayerType.MAGHRIB to adj.maghrib,
            PrayerType.ISHA to adj.isha
        )
        return pairs.map { (type, raw) ->
            PrayerTimeItem(
                type = type,
                time24 = cleanTime24(raw),
                formattedTime = formatTime(raw, timeFormat),
                isNext = type == nextPrayer,
                adjustmentMinutes = adjustments.totalOffsetFor(type)
            )
        }
    }

    fun computeNextPrayerStatus(
        timings: AladhanTimings,
        nowMillis: Long = System.currentTimeMillis(),
        timeZoneId: String? = null,
        adjustments: PrayerAdjustments = PrayerAdjustments()
    ): NextPrayerStatus {
        val adj = adjustedTimings(timings, adjustments)
        val ordered = listOf(
            PrayerType.FAJR to cleanTime24(adj.fajr),
            PrayerType.SUNRISE to cleanTime24(adj.sunrise),
            PrayerType.DHUHR to cleanTime24(adj.dhuhr),
            PrayerType.ASR to cleanTime24(adj.asr),
            PrayerType.MAGHRIB to cleanTime24(adj.maghrib),
            PrayerType.ISHA to cleanTime24(adj.isha)
        )

        val tz = resolveTimeZone(timeZoneId)
        val cityCal = Calendar.getInstance(tz).apply { timeInMillis = nowMillis }

        var nextIndex = -1
        var targetMillis = 0L

        for (i in ordered.indices) {
            val candidateMillis = timeOnDateMillis(cityCal, ordered[i].second, addDays = 0)
            if (candidateMillis > nowMillis) {
                nextIndex = i
                targetMillis = candidateMillis
                break
            }
        }

        val prevMillis: Long
        val nextType: PrayerType

        if (nextIndex == -1) {
            nextType = PrayerType.FAJR
            targetMillis = timeOnDateMillis(cityCal, ordered[0].second, addDays = 1)
            prevMillis = timeOnDateMillis(cityCal, ordered.last().second, addDays = 0)
        } else {
            nextType = ordered[nextIndex].first
            prevMillis = if (nextIndex == 0) {
                timeOnDateMillis(cityCal, ordered.last().second, addDays = -1)
            } else {
                timeOnDateMillis(cityCal, ordered[nextIndex - 1].second, addDays = 0)
            }
        }

        val remaining = (targetMillis - nowMillis).coerceAtLeast(0L)
        val totalWindow = (targetMillis - prevMillis).coerceAtLeast(1L)
        val elapsed = (nowMillis - prevMillis).coerceIn(0L, totalWindow)
        val progress = (elapsed.toFloat() / totalWindow.toFloat()).coerceIn(0f, 1f)
        val remainingFraction = (1f - progress).coerceIn(0f, 1f)

        val hours = remaining / 3_600_000
        val minutes = (remaining % 3_600_000) / 60_000
        val seconds = (remaining % 60_000) / 1_000
        val countdown = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

        val celestialPhase = resolveCelestialPhase(adj, cityCal, nowMillis)

        return NextPrayerStatus(
            nextPrayer = nextType,
            countdownFormatted = countdown,
            progress = progress,
            remainingFraction = remainingFraction,
            millisRemaining = remaining,
            celestialPhase = celestialPhase
        )
    }

    fun computeRamadanStatus(
        timings: AladhanTimings,
        nowMillis: Long = System.currentTimeMillis(),
        timeZoneId: String? = null,
        adjustments: PrayerAdjustments = PrayerAdjustments(),
        timeFormat: TimeFormat = TimeFormat.HOUR_12,
        imsakLeadMinutes: Int = 15,
        iftarLeadMinutes: Int = 0
    ): RamadanStatus {
        val adj = adjustedTimings(timings, adjustments)
        val imsak24 = adj.imsak.ifBlank { shiftTime24(adj.fajr, -10) }
        val suhoor24 = shiftTime24(adj.fajr, -45)
        val fajr24 = cleanTime24(adj.fajr)
        val maghrib24 = cleanTime24(adj.maghrib)

        val tz = resolveTimeZone(timeZoneId)
        val cityCal = Calendar.getInstance(tz).apply { timeInMillis = nowMillis }

        val imsakTodayMs = timeOnDateMillis(cityCal, imsak24, 0)
        val fajrTodayMs = timeOnDateMillis(cityCal, fajr24, 0)
        val maghribTodayMs = timeOnDateMillis(cityCal, maghrib24, 0)

        val isFastingWindow = nowMillis in fajrTodayMs until maghribTodayMs

        val (label, targetMs, windowMs) = if (isFastingWindow) {
            Triple(
                "متبقي على أذان المغرب (الإفطار)",
                maghribTodayMs,
                (maghribTodayMs - fajrTodayMs).coerceAtLeast(1L)
            )
        } else {
            val nextImsakMs = if (nowMillis < imsakTodayMs) {
                imsakTodayMs
            } else {
                timeOnDateMillis(cityCal, imsak24, 1)
            }
            val prevMaghribMs = if (nowMillis >= maghribTodayMs) {
                maghribTodayMs
            } else {
                timeOnDateMillis(cityCal, maghrib24, -1)
            }
            Triple(
                "متبقي على وقت الإمساك",
                nextImsakMs,
                (nextImsakMs - prevMaghribMs).coerceAtLeast(1L)
            )
        }

        val remaining = (targetMs - nowMillis).coerceAtLeast(0L)
        val progress = (1f - (remaining.toFloat() / windowMs.toFloat())).coerceIn(0f, 1f)
        val h = remaining / 3_600_000
        val m = (remaining % 3_600_000) / 60_000
        val s = (remaining % 60_000) / 1_000
        val countdown = String.format(Locale.US, "%02d:%02d:%02d", h, m, s)

        // Exact alarm trigger millis (adjusted for lead minutes)
        val todayImsakAlert = imsakTodayMs - imsakLeadMinutes * 60_000L
        val nextImsakAlert = if (todayImsakAlert > nowMillis) {
            todayImsakAlert
        } else {
            timeOnDateMillis(cityCal, imsak24, 1) - imsakLeadMinutes * 60_000L
        }

        val todayIftarAlert = maghribTodayMs - iftarLeadMinutes * 60_000L
        val nextIftarAlert = if (todayIftarAlert > nowMillis) {
            todayIftarAlert
        } else {
            timeOnDateMillis(cityCal, maghrib24, 1) - iftarLeadMinutes * 60_000L
        }

        return RamadanStatus(
            imsakFormatted = formatTime(imsak24, timeFormat),
            suhoorFormatted = formatTime(suhoor24, timeFormat),
            fajrFormatted = formatTime(fajr24, timeFormat),
            iftarFormatted = formatTime(maghrib24, timeFormat),
            imsakTime24 = imsak24,
            iftarTime24 = maghrib24,
            isFastingWindow = isFastingWindow,
            countdownLabel = label,
            countdownFormatted = countdown,
            progress = progress,
            nextImsakTriggerMillis = nextImsakAlert,
            nextIftarTriggerMillis = nextIftarAlert
        )
    }

    fun resolveCelestialPhase(
        timings: AladhanTimings,
        cityCal: Calendar,
        nowMillis: Long
    ): CelestialPhase {
        val fajrMs = timeOnDateMillis(cityCal, cleanTime24(timings.fajr), 0)
        val sunriseMs = timeOnDateMillis(cityCal, cleanTime24(timings.sunrise), 0)
        val dhuhrMs = timeOnDateMillis(cityCal, cleanTime24(timings.dhuhr), 0)
        val maghribMs = timeOnDateMillis(cityCal, cleanTime24(timings.maghrib), 0)
        val ishaMs = timeOnDateMillis(cityCal, cleanTime24(timings.isha), 0)

        val morningEndMs = minOf(sunriseMs + 75 * 60_000L, (sunriseMs + dhuhrMs) / 2L)
        val sunsetStartMs = maghribMs - 20 * 60_000L

        return when {
            nowMillis < fajrMs -> CelestialPhase.NIGHT_MOON
            nowMillis < morningEndMs -> CelestialPhase.MORNING_SUNRISE
            nowMillis < sunsetStartMs -> CelestialPhase.DAY_SUN
            nowMillis < ishaMs -> CelestialPhase.MAGHRIB_SUNSET
            else -> CelestialPhase.NIGHT_MOON
        }
    }

    fun resolveCelestialPhaseByClock(
        nowMillis: Long = System.currentTimeMillis(),
        timeZoneId: String? = null
    ): CelestialPhase {
        val cal = Calendar.getInstance(resolveTimeZone(timeZoneId)).apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..8 -> CelestialPhase.MORNING_SUNRISE
            in 9..16 -> CelestialPhase.DAY_SUN
            in 17..18 -> CelestialPhase.MAGHRIB_SUNSET
            else -> CelestialPhase.NIGHT_MOON
        }
    }

    private fun timeOnDateMillis(baseCal: Calendar, time24: String, addDays: Int): Long {
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

    fun calculateQiblaBearing(latitude: Double, longitude: Double): Float {
        val kaabaLat = Math.toRadians(KAABA_LAT)
        val kaabaLng = Math.toRadians(KAABA_LNG)
        val myLat = Math.toRadians(latitude)
        val myLng = Math.toRadians(longitude)

        val y = sin(kaabaLng - myLng)
        val x = cos(myLat) * tan(kaabaLat) - sin(myLat) * cos(kaabaLng - myLng)
        val bearingDeg = atan2(y, x) * (180.0 / PI)
        return ((bearingDeg + 360.0) % 360.0).toFloat()
    }
}
