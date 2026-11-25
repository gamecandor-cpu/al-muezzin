package com.example.data.model

data class QuranSurahMeta(
    val number: Int,
    val nameAr: String,
    val revelationType: String,
    val ayahCount: Int,
    val startPage: Int
) {
    val revelationTypeAr: String
        get() = if (revelationType.equals("Medinan", ignoreCase = true)) "مدنية" else "مكية"
}

data class QuranAyah(
    val surahNumber: Int,
    val surahNameAr: String,
    val numberInSurah: Int,
    val text: String,
    val displayText: String,
    val page: Int,
    val juz: Int,
    val hizbQuarter: Int,
    val sajda: Boolean
)

data class QuranSurahPageBlock(
    val surahMeta: QuranSurahMeta,
    val showSurahHeader: Boolean,
    val showBasmalah: Boolean,
    val ayahs: List<QuranAyah>
)

data class QuranPageData(
    val pageNumber: Int,
    val juzNumber: Int,
    val hizbNumber: Int,
    val primarySurahName: String,
    val blocks: List<QuranSurahPageBlock>
)

data class QuranJuzMeta(
    val juzNumber: Int,
    val arabicName: String,
    val startPage: Int,
    val startSurahName: String
)
