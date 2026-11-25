package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class PrayerType(
    val apiKey: String,
    val arabicName: String,
    val englishName: String
) {
    FAJR("Fajr", "الفجر", "Fajr"),
    SUNRISE("Sunrise", "الشروق", "Sunrise"),
    DHUHR("Dhuhr", "الظهر", "Dhuhr"),
    ASR("Asr", "العصر", "Asr"),
    MAGHRIB("Maghrib", "المغرب", "Maghrib"),
    ISHA("Isha", "العشاء", "Isha")
}

data class PrayerTimeItem(
    val type: PrayerType,
    val time24: String,
    val formattedTime: String,
    val isNext: Boolean,
    val adjustmentMinutes: Int = 0
)

data class PrayerAdjustments(
    val globalOffsetMinutes: Int = 0,
    val fajrOffset: Int = 0,
    val sunriseOffset: Int = 0,
    val dhuhrOffset: Int = 0,
    val asrOffset: Int = 0,
    val maghribOffset: Int = 0,
    val ishaOffset: Int = 0
) {
    fun totalOffsetFor(type: PrayerType): Int {
        val specific = when (type) {
            PrayerType.FAJR -> fajrOffset
            PrayerType.SUNRISE -> sunriseOffset
            PrayerType.DHUHR -> dhuhrOffset
            PrayerType.ASR -> asrOffset
            PrayerType.MAGHRIB -> maghribOffset
            PrayerType.ISHA -> ishaOffset
        }
        return globalOffsetMinutes + specific
    }

    fun specificOffsetFor(type: PrayerType): Int = when (type) {
        PrayerType.FAJR -> fajrOffset
        PrayerType.SUNRISE -> sunriseOffset
        PrayerType.DHUHR -> dhuhrOffset
        PrayerType.ASR -> asrOffset
        PrayerType.MAGHRIB -> maghribOffset
        PrayerType.ISHA -> ishaOffset
    }

    val hasAnyOffset: Boolean
        get() = globalOffsetMinutes != 0 || fajrOffset != 0 || sunriseOffset != 0 ||
            dhuhrOffset != 0 || asrOffset != 0 || maghribOffset != 0 || ishaOffset != 0
}

data class RamadanSettings(
    val ramadanTabEnabled: Boolean = false,
    val ramadanAutoInMonth: Boolean = true,
    val imsakAlarmEnabled: Boolean = false,
    val imsakLeadMinutes: Int = 15,
    val iftarAlarmEnabled: Boolean = false,
    val iftarLeadMinutes: Int = 0
)

data class QadaState(
    val remainingDays: Int = 0,
    val completedDays: Int = 0
)

data class ReligiousOccasion(
    val id: String,
    val titleAr: String,
    val hijriDateLabel: String,
    val hijriMonth: Int,
    val hijriDay: Int,
    val durationDays: Int = 1,
    val isEid: Boolean = false,
    val greetingTitleAr: String,
    val greetingMessageAr: String,
    val eidDayLabels: List<String> = emptyList(),
    val daysRemaining: Int,
    val isActiveToday: Boolean,
    val activeDayNumber: Int = 0
) {
    /**
     * Upcoming occasions are highlighted in red; as the day draws nearer,
     * the red intensity decreases smoothly until it fades out on the day itself.
     */
    val redIntensity: Float
        get() {
            if (isActiveToday || daysRemaining <= 0) return 0f
            return ((daysRemaining.coerceIn(1, 180).toFloat() / 180f) * 0.88f + 0.12f)
                .coerceIn(0.12f, 1.0f)
        }

    val activeBannerTitle: String
        get() = if (isActiveToday && isEid && activeDayNumber in 1..eidDayLabels.size) {
            eidDayLabels[activeDayNumber - 1]
        } else {
            greetingTitleAr
        }
}

enum class CalculationMethod(val id: Int, val arabicLabel: String) {
    UMM_AL_QURA(4, "أم القرى"),
    MWL(3, "رابطة العالم الإسلامي"),
    EGYPTIAN(5, "الهيئة المصرية"),
    ISNA(2, "الجمعية الإسلامية لأمريكا الشمالية"),
    KARACHI(1, "جامعة العلوم الإسلامية بكراتشي"),
    GULF(8, "منطقة الخليج"),
    KUWAIT(9, "الكويت"),
    QATAR(10, "قطر"),
    TURKEY(13, "ديانت التركية");

    companion object {
        fun fromId(id: Int): CalculationMethod = entries.find { it.id == id } ?: UMM_AL_QURA
    }
}

enum class AsrSchool(val id: Int, val arabicLabel: String) {
    SHAFI(0, "الشافعي / المالكي"),
    HANAFI(1, "الحنفي");

    companion object {
        fun fromId(id: Int): AsrSchool = entries.find { it.id == id } ?: SHAFI
    }
}

enum class TimeFormat(val code: Int, val arabicLabel: String) {
    HOUR_12(12, "12 ساعة"),
    HOUR_24(24, "24 ساعة");

    companion object {
        fun fromCode(code: Int): TimeFormat = entries.find { it.code == code } ?: HOUR_12
    }
}

enum class MuezzinVoice(
    val id: String,
    val arabicLabel: String,
    val rawResId: Int
) {
    NASSER_AL_QUTAMI("nasser", "ناصر القطامي (الافتراضي)", com.example.R.raw.athan_nasser_alqutami),
    AHMED_AL_HADDAD("haddad", "أحمد الحداد (مصر)", com.example.R.raw.athan_ahmed_alhaddad),
    QATAR("qatar", "أذان قطر", com.example.R.raw.athan_qatar),
    MUHAMMAD_RIFAAT("rifaat", "الشيخ محمد رفعت", com.example.R.raw.athan_muhammad_rifaat),
    MANSOUR_AL_ZAHRANI("zahrani", "منصور الزهراني", com.example.R.raw.athan_mansour_alzahrani),
    FAJR_SPECIAL("fajr", "أذان الفجر", com.example.R.raw.athan_fajr);

    companion object {
        fun fromId(id: String?): MuezzinVoice = entries.find { it.id == id } ?: NASSER_AL_QUTAMI
    }
}

enum class AthanPlayMode(
    val id: String,
    val arabicLabel: String,
    val descriptionAr: String
) {
    FULL("full", "الأذان كاملاً", "تشغيل الأذان بالكامل من بدايته حتى نهايته"),
    PARTIAL("partial", "جزء من الأذان", "تشغيل التكبيرات والمقطع الأول فقط (٢٠ ثانية)");

    companion object {
        fun fromId(id: String?): AthanPlayMode = entries.find { it.id == id } ?: FULL
    }
}

data class UserSettings(
    val calcMethod: CalculationMethod = CalculationMethod.UMM_AL_QURA,
    val asrSchool: AsrSchool = AsrSchool.SHAFI,
    val timeFormat: TimeFormat = TimeFormat.HOUR_12,
    val athanEnabled: Boolean = true,
    val vibrationEnabled: Boolean = false,
    val muezzinVoice: MuezzinVoice = MuezzinVoice.NASSER_AL_QUTAMI,
    val fajrAthanEnabled: Boolean = true,
    val athanPlayMode: AthanPlayMode = AthanPlayMode.FULL
)

data class TasbihState(
    val count: Int = 0,
    val dhikr: String = "سبحان الله",
    val target: Int = 33
) {
    companion object {
        val DHIKR_OPTIONS = listOf(
            "سبحان الله",
            "الحمد لله",
            "الله أكبر",
            "لا إله إلا الله",
            "أستغفر الله",
            "سبحان الله وبحمده",
            "لا حول ولا قوة إلا بالله",
            "اللهم صل على محمد"
        )
        val TARGET_OPTIONS = listOf(33, 66, 99, 100, 500, 1000)
    }
}

data class CityPreset(
    val nameAr: String,
    val countryAr: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String
) {
    companion object {
        val MAJOR_CITIES = listOf(
            CityPreset("مكة المكرمة", "السعودية", 21.4225, 39.8262, "Asia/Riyadh"),
            CityPreset("المدينة المنورة", "السعودية", 24.4672, 39.6111, "Asia/Riyadh"),
            CityPreset("الرياض", "السعودية", 24.7136, 46.6753, "Asia/Riyadh"),
            CityPreset("جدة", "السعودية", 21.4858, 39.1925, "Asia/Riyadh"),
            CityPreset("القاهرة", "مصر", 30.0444, 31.2357, "Africa/Cairo"),
            CityPreset("الإسكندرية", "مصر", 31.2001, 29.9187, "Africa/Cairo"),
            CityPreset("دبي", "الإمارات", 25.2048, 55.2708, "Asia/Dubai"),
            CityPreset("أبوظبي", "الإمارات", 24.4539, 54.3773, "Asia/Dubai"),
            CityPreset("الدوحة", "قطر", 25.2854, 51.5310, "Asia/Qatar"),
            CityPreset("الكويت", "الكويت", 29.3759, 47.9774, "Asia/Kuwait"),
            CityPreset("المنامة", "البحرين", 26.2285, 50.5860, "Asia/Bahrain"),
            CityPreset("مسقط", "عُمان", 23.5880, 58.3829, "Asia/Muscat"),
            CityPreset("عمّان", "الأردن", 31.9454, 35.9284, "Asia/Amman"),
            CityPreset("القدس", "فلسطين", 31.7683, 35.2137, "Asia/Jerusalem"),
            CityPreset("بيروت", "لبنان", 33.8938, 35.5018, "Asia/Beirut"),
            CityPreset("دمشق", "سوريا", 33.5138, 36.2765, "Asia/Damascus"),
            CityPreset("بغداد", "العراق", 33.3152, 44.3661, "Asia/Baghdad"),
            CityPreset("صنعاء", "اليمن", 15.3694, 44.1910, "Asia/Aden"),
            CityPreset("الخرطوم", "السودان", 15.5007, 32.5599, "Africa/Khartoum"),
            CityPreset("طرابلس", "ليبيا", 32.8872, 13.1913, "Africa/Tripoli"),
            CityPreset("تونس", "تونس", 36.8065, 10.1815, "Africa/Tunis"),
            CityPreset("الجزائر", "الجزائر", 36.7538, 3.0588, "Africa/Algiers"),
            CityPreset("الرباط", "المغرب", 34.0209, -6.8416, "Africa/Casablanca"),
            CityPreset("الدار البيضاء", "المغرب", 33.5731, -7.5898, "Africa/Casablanca"),
            CityPreset("نواكشوط", "موريتانيا", 18.0735, -15.9582, "Africa/Nouakchott"),
            CityPreset("إسطنبول", "تركيا", 41.0082, 28.9784, "Europe/Istanbul"),
            CityPreset("أنقرة", "تركيا", 39.9334, 32.8597, "Europe/Istanbul"),
            CityPreset("لندن", "المملكة المتحدة", 51.5072, -0.1276, "Europe/London"),
            CityPreset("باريس", "فرنسا", 48.8566, 2.3522, "Europe/Paris"),
            CityPreset("برلين", "ألمانيا", 52.5200, 13.4050, "Europe/Berlin"),
            CityPreset("نيويورك", "الولايات المتحدة", 40.7128, -74.0060, "America/New_York"),
            CityPreset("تورونتو", "كندا", 43.6532, -79.3832, "America/Toronto"),
            CityPreset("كوالالمبور", "ماليزيا", 3.1390, 101.6869, "Asia/Kuala_Lumpur"),
            CityPreset("جاكرتا", "إندونيسيا", -6.2088, 106.8456, "Asia/Jakarta"),
            CityPreset("إسلام آباد", "باكستان", 33.6844, 73.0479, "Asia/Karachi")
        )
    }
}

@JsonClass(generateAdapter = true)
data class AladhanResponse(
    val code: Int,
    val status: String,
    val data: AladhanData
)

@JsonClass(generateAdapter = true)
data class AladhanData(
    val timings: AladhanTimings,
    val date: AladhanDate,
    val meta: AladhanMeta? = null
)

@JsonClass(generateAdapter = true)
data class AladhanMeta(
    val timezone: String? = null
)

@JsonClass(generateAdapter = true)
data class AladhanTimings(
    @Json(name = "Imsak") val imsak: String = "",
    @Json(name = "Fajr") val fajr: String,
    @Json(name = "Sunrise") val sunrise: String,
    @Json(name = "Dhuhr") val dhuhr: String,
    @Json(name = "Asr") val asr: String,
    @Json(name = "Maghrib") val maghrib: String,
    @Json(name = "Isha") val isha: String
)

@JsonClass(generateAdapter = true)
data class AladhanDate(
    val readable: String,
    val hijri: HijriDate
)

@JsonClass(generateAdapter = true)
data class HijriDate(
    val day: String,
    val year: String,
    val month: HijriMonth
)

@JsonClass(generateAdapter = true)
data class HijriMonth(
    val number: Int = 0,
    val ar: String
)

@JsonClass(generateAdapter = true)
data class NominatimResponse(
    val address: NominatimAddress?
)

@JsonClass(generateAdapter = true)
data class NominatimAddress(
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val state: String? = null,
    val country: String? = null
)

@JsonClass(generateAdapter = true)
data class NominatimSearchItem(
    @Json(name = "display_name") val displayName: String,
    val lat: String,
    val lon: String,
    val address: NominatimAddress? = null
)
