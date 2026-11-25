package com.example

import com.example.data.model.AladhanTimings
import com.example.data.model.PrayerAdjustments
import com.example.data.model.PrayerType
import com.example.data.model.TimeFormat
import com.example.util.CelestialPhase
import com.example.util.PrayerTimeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ExampleUnitTest {

    @Test
    fun formatTime_handles12And24HourModes() {
        assertEquals("05:12", PrayerTimeCalculator.formatTime("05:12 (EEST)", TimeFormat.HOUR_24))
        assertEquals("5:12 ص", PrayerTimeCalculator.formatTime("05:12", TimeFormat.HOUR_12))
        assertEquals("3:45 م", PrayerTimeCalculator.formatTime("15:45", TimeFormat.HOUR_12))
        assertEquals("12:00 م", PrayerTimeCalculator.formatTime("12:00", TimeFormat.HOUR_12))
    }

    @Test
    fun qiblaBearing_fromRiyadhPointsWestSouthwest() {
        val bearing = PrayerTimeCalculator.calculateQiblaBearing(24.7136, 46.6753)
        assertTrue("Expected Riyadh Qibla near ~244 deg, got $bearing", bearing in 235f..255f)
    }

    @Test
    fun computeNextPrayer_respectsSelectedCityTimeZone() {
        val timings = AladhanTimings(
            imsak = "04:20",
            fajr = "04:30",
            sunrise = "05:55",
            dhuhr = "12:00",
            asr = "15:25",
            maghrib = "18:05",
            isha = "19:35"
        )
        // 13:00 in Asia/Riyadh regardless of host timezone
        val riyadhTz = TimeZone.getTimeZone("Asia/Riyadh")
        val cal = Calendar.getInstance(riyadhTz).apply {
            set(Calendar.HOUR_OF_DAY, 13)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val status = PrayerTimeCalculator.computeNextPrayerStatus(
            timings = timings,
            nowMillis = cal.timeInMillis,
            timeZoneId = "Asia/Riyadh"
        )
        assertEquals(PrayerType.ASR, status.nextPrayer)
        assertEquals("02:25:00", status.countdownFormatted)
        assertTrue(status.remainingFraction in 0f..1f)
    }

    @Test
    fun computeNextPrayer_appliesManualTimeAdjustments() {
        val timings = AladhanTimings(
            imsak = "04:20",
            fajr = "04:30",
            sunrise = "05:55",
            dhuhr = "12:00",
            asr = "15:25",
            maghrib = "18:05",
            isha = "19:35"
        )
        val adjustments = PrayerAdjustments(globalOffsetMinutes = 5, asrOffset = -10)
        val items = PrayerTimeCalculator.buildPrayerItems(
            timings = timings,
            timeFormat = TimeFormat.HOUR_24,
            nextPrayer = PrayerType.ASR,
            adjustments = adjustments
        )
        val asrItem = items.first { it.type == PrayerType.ASR }
        assertEquals("15:20", asrItem.time24)
        val dhuhrItem = items.first { it.type == PrayerType.DHUHR }
        assertEquals("12:05", dhuhrItem.time24)
    }

    @Test
    fun computeNextPrayer_resolvesCelestialPhasesForAllPeriods() {
        val timings = AladhanTimings(
            imsak = "04:20",
            fajr = "04:30",
            sunrise = "05:55",
            dhuhr = "12:00",
            asr = "15:25",
            maghrib = "18:05",
            isha = "19:35"
        )
        val tz = TimeZone.getTimeZone("Asia/Riyadh")

        fun millisAt(hour: Int, minute: Int): Long {
            return Calendar.getInstance(tz).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        assertEquals(
            CelestialPhase.MORNING_SUNRISE,
            PrayerTimeCalculator.computeNextPrayerStatus(timings, millisAt(6, 15), "Asia/Riyadh").celestialPhase
        )
        assertEquals(
            CelestialPhase.DAY_SUN,
            PrayerTimeCalculator.computeNextPrayerStatus(timings, millisAt(12, 30), "Asia/Riyadh").celestialPhase
        )
        assertEquals(
            CelestialPhase.MAGHRIB_SUNSET,
            PrayerTimeCalculator.computeNextPrayerStatus(timings, millisAt(18, 15), "Asia/Riyadh").celestialPhase
        )
        assertEquals(
            CelestialPhase.NIGHT_MOON,
            PrayerTimeCalculator.computeNextPrayerStatus(timings, millisAt(21, 0), "Asia/Riyadh").celestialPhase
        )
    }

    @Test
    fun isRamadanMonth_detectsRamadanByNumberOrArabicName() {
        assertTrue(PrayerTimeCalculator.isRamadanMonth(9, "رَمَضَان"))
        assertTrue(PrayerTimeCalculator.isRamadanMonth(0, "رمضان"))
        assertTrue(!PrayerTimeCalculator.isRamadanMonth(8, "شَعْبَان"))
        assertTrue(!PrayerTimeCalculator.isRamadanMonth(10, "شَوَّال"))
    }

    @Test
    fun quranHelpers_formatNumeralsAndNormalizeUthmaniSearch() {
        assertEquals("٦٠٤", com.example.data.local.QuranRepository.toArabicIndicNumber(604))
        assertEquals(
            "الحمد لله رب العلمين",
            com.example.data.local.QuranRepository.normalizeArabicForSearch("ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ")
        )
        assertEquals(
            "الٓمٓ",
            com.example.data.local.QuranRepository.stripBasmalahPrefix("بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ الٓمٓ")
        )
    }

    @Test
    fun athanDefaults_useNasserAlQutamiAndFajrAndFullMode() {
        val defaults = com.example.data.model.UserSettings()
        assertEquals(com.example.data.model.MuezzinVoice.NASSER_AL_QUTAMI, defaults.muezzinVoice)
        assertTrue(defaults.fajrAthanEnabled)
        assertEquals(com.example.data.model.AthanPlayMode.FULL, defaults.athanPlayMode)
        assertEquals(6, com.example.data.model.MuezzinVoice.entries.size)
        assertEquals(
            com.example.data.model.MuezzinVoice.AHMED_AL_HADDAD,
            com.example.data.model.MuezzinVoice.fromId("haddad")
        )
        assertEquals(
            com.example.data.model.AthanPlayMode.PARTIAL,
            com.example.data.model.AthanPlayMode.fromId("partial")
        )
    }

    @Test
    fun computeReligiousOccasions_handlesSingleDayEidsAndRedIntensityDecay() {
        // 1 Muharram 1448 -> active single-day occasion
        val day1Muharram = PrayerTimeCalculator.computeReligiousOccasions(1448, 1, 1)
        assertTrue(day1Muharram.first().isActiveToday)
        assertEquals("islamic_new_year", day1Muharram.first().id)

        // 2 Muharram 1448 -> 1 Muharram occasion disappears immediately
        val day2Muharram = PrayerTimeCalculator.computeReligiousOccasions(1448, 1, 2)
        assertTrue(day2Muharram.none { it.isActiveToday })

        // Eid al-Fitr stays active for 3 days (1, 2, 3 Shawwal) with distinct titles, then vanishes on day 4
        for (shawwalDay in 1..3) {
            val eidFitrList = PrayerTimeCalculator.computeReligiousOccasions(1447, 10, shawwalDay)
            val active = eidFitrList.firstOrNull { it.isActiveToday }
            assertEquals("eid_al_fitr", active?.id)
            assertEquals(shawwalDay, active?.activeDayNumber)
            assertTrue(active?.activeBannerTitle?.contains("عيد الفطر") == true)
            assertTrue(active?.greetingMessageAr?.contains("عيد فطر سعيد") == true)
        }
        val shawwalDay4 = PrayerTimeCalculator.computeReligiousOccasions(1447, 10, 4)
        assertTrue(shawwalDay4.none { it.isActiveToday })

        // Eid al-Adha stays active for 3 days (10, 11, 12 Dhul-Hijjah), then vanishes on day 13
        for (dhDay in 10..12) {
            val eidAdhaList = PrayerTimeCalculator.computeReligiousOccasions(1447, 12, dhDay)
            val active = eidAdhaList.firstOrNull { it.isActiveToday }
            assertEquals("eid_al_adha", active?.id)
            assertEquals(dhDay - 9, active?.activeDayNumber)
            assertTrue(active?.greetingMessageAr?.contains("عيد أضحى سعيد") == true)
        }
        val dhDay13 = PrayerTimeCalculator.computeReligiousOccasions(1447, 12, 13)
        assertTrue(dhDay13.none { it.isActiveToday })

        // Upcoming red intensity decreases monotonically as daysRemaining gets smaller
        val upcoming = shawwalDay4.filter { !it.isActiveToday }
        assertTrue(upcoming.isNotEmpty())
        for (i in 0 until upcoming.size - 1) {
            assertTrue(upcoming[i].daysRemaining <= upcoming[i + 1].daysRemaining)
            assertTrue(upcoming[i].redIntensity <= upcoming[i + 1].redIntensity)
        }
    }

    @Test
    fun audioUrlResolver_normalizesDriveDropboxAndRejectsHttp() {
        val drive = com.example.util.AudioUrlResolver.resolve(
            "https://drive.google.com/file/d/1lVBSrdRxA55fQGG3GLlzkAZJeFG6_9Qf/view?usp=sharing"
        )
        assertTrue(drive.isValidHttps)
        assertEquals(
            "https://drive.google.com/uc?export=download&id=1lVBSrdRxA55fQGG3GLlzkAZJeFG6_9Qf",
            drive.directPlayUrl
        )
        assertEquals("Google Drive صوت", drive.sourceTypeLabel)

        val mp3 = com.example.util.AudioUrlResolver.resolve("https://example.com/athkar.mp3")
        assertTrue(mp3.isValidHttps)
        assertEquals("ملف صوت MP3", mp3.sourceTypeLabel)

        val insecure = com.example.util.AudioUrlResolver.resolve("http://example.com/sound.mp3")
        assertTrue(!insecure.isValidHttps)
    }

    @Test
    fun computeAthkarScheduleTimes_defaultsToSixAmAndOneHourBeforeMaghribAndSupportsCustom() {
        val timings = AladhanTimings(
            imsak = "04:20",
            fajr = "04:30",
            sunrise = "05:55",
            dhuhr = "12:00",
            asr = "15:25",
            maghrib = "18:05",
            isha = "19:35"
        )
        // Defaults: 06:00 AM for morning, 1 hour before Maghrib (17:05) for evening
        val (defMorning, defEvening) = PrayerTimeCalculator.computeAthkarScheduleTimes24(timings)
        assertEquals("06:00", defMorning)
        assertEquals("17:05", defEvening)

        // Custom edited schedule: e.g., 05:30 AM and 30 min before Maghrib (17:35)
        val (customMorning, customEvening) = PrayerTimeCalculator.computeAthkarScheduleTimes24(
            adjTimings = timings,
            config = com.example.util.AthkarScheduleConfig(
                morningTime24 = "05:30",
                eveningOffsetFromMaghribMinutes = -30
            )
        )
        assertEquals("05:30", customMorning)
        assertEquals("17:35", customEvening)
    }
}


