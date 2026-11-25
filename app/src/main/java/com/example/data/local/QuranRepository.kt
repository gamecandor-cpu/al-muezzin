package com.example.data.local

import android.content.Context
import android.util.JsonReader
import com.example.data.model.QuranAyah
import com.example.data.model.QuranJuzMeta
import com.example.data.model.QuranPageData
import com.example.data.model.QuranSurahMeta
import com.example.data.model.QuranSurahPageBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader

data class QuranDataset(
    val surahs: List<QuranSurahMeta>,
    val pages: Map<Int, QuranPageData>,
    val juzList: List<QuranJuzMeta>,
    val allAyahs: List<QuranAyah>
)

class QuranRepository(private val context: Context) {

    private val loadMutex = Mutex()
    @Volatile
    private var cachedDataset: QuranDataset? = null

    suspend fun getDataset(): QuranDataset {
        cachedDataset?.let { return it }
        return loadMutex.withLock {
            cachedDataset?.let { return@withLock it }
            val loaded = withContext(Dispatchers.IO) {
                parseCompressedAsset()
            }
            cachedDataset = loaded
            loaded
        }
    }

    suspend fun searchAyahs(query: String, limit: Int = 50): List<QuranAyah> {
        val dataset = getDataset()
        val normalizedQuery = normalizeArabicForSearch(query)
        if (normalizedQuery.length < 2) return emptyList()
        return withContext(Dispatchers.Default) {
            val matches = ArrayList<QuranAyah>(limit)
            for (ayah in dataset.allAyahs) {
                if (normalizeArabicForSearch(ayah.displayText).contains(normalizedQuery) ||
                    normalizeArabicForSearch(ayah.surahNameAr).contains(normalizedQuery)
                ) {
                    matches.add(ayah)
                    if (matches.size >= limit) break
                }
            }
            matches
        }
    }

    private fun parseCompressedAsset(): QuranDataset {
        return context.assets.open("quran_uthmani.json").use { rawStream ->
            parseJsonStream(rawStream)
        }
    }

    internal fun parseJsonStream(rawStream: InputStream): QuranDataset {
        val surahs = ArrayList<QuranSurahMeta>(114)
        val allAyahs = ArrayList<QuranAyah>(6236)
        val ayahsByPage = HashMap<Int, MutableList<QuranAyah>>(604)
        val surahMap = HashMap<Int, QuranSurahMeta>(114)

        BufferedInputStream(rawStream, 32 * 1024).use { buf ->
            JsonReader(InputStreamReader(buf, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "surahs" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                readSurah(reader, surahs, allAyahs, ayahsByPage, surahMap)
                            }
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }

        val pages = HashMap<Int, QuranPageData>(604)
        for (pageNum in 1..604) {
            val pageAyahs = ayahsByPage[pageNum].orEmpty()
            if (pageAyahs.isEmpty()) continue
            val blocks = ArrayList<QuranSurahPageBlock>()
            var currentSurahId = -1
            var currentGroup = ArrayList<QuranAyah>()

            fun flushBlock() {
                if (currentGroup.isEmpty()) return
                val meta = surahMap[currentSurahId] ?: return
                val firstAyah = currentGroup.first()
                val showHeader = firstAyah.numberInSurah == 1
                val showBasmalah = showHeader && meta.number != 1 && meta.number != 9
                blocks.add(
                    QuranSurahPageBlock(
                        surahMeta = meta,
                        showSurahHeader = showHeader,
                        showBasmalah = showBasmalah,
                        ayahs = currentGroup.toList()
                    )
                )
            }

            for (ayah in pageAyahs) {
                if (ayah.surahNumber != currentSurahId) {
                    flushBlock()
                    currentSurahId = ayah.surahNumber
                    currentGroup = ArrayList()
                }
                currentGroup.add(ayah)
            }
            flushBlock()

            val first = pageAyahs.first()
            pages[pageNum] = QuranPageData(
                pageNumber = pageNum,
                juzNumber = first.juz,
                hizbNumber = ((first.hizbQuarter - 1) / 4) + 1,
                primarySurahName = first.surahNameAr,
                blocks = blocks
            )
        }

        val juzFirstAyah = HashMap<Int, QuranAyah>(30)
        for (ayah in allAyahs) {
            if (!juzFirstAyah.containsKey(ayah.juz)) {
                juzFirstAyah[ayah.juz] = ayah
            }
        }
        val juzList = (1..30).mapNotNull { j ->
            val first = juzFirstAyah[j] ?: return@mapNotNull null
            QuranJuzMeta(
                juzNumber = j,
                arabicName = JUZ_ARABIC_NAMES.getOrElse(j - 1) { "الجزء $j" },
                startPage = first.page,
                startSurahName = first.surahNameAr
            )
        }

        return QuranDataset(
            surahs = surahs,
            pages = pages,
            juzList = juzList,
            allAyahs = allAyahs
        )
    }

    private fun readSurah(
        reader: JsonReader,
        surahs: MutableList<QuranSurahMeta>,
        allAyahs: MutableList<QuranAyah>,
        ayahsByPage: MutableMap<Int, MutableList<QuranAyah>>,
        surahMap: MutableMap<Int, QuranSurahMeta>
    ) {
        var number = 0
        var nameAr = ""
        var revelationType = "Meccan"
        val rawAyahs = ArrayList<RawAyah>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "n" -> number = reader.nextInt()
                "ar" -> nameAr = reader.nextString()
                "rt" -> revelationType = reader.nextString()
                "ayahs" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        rawAyahs.add(readRawAyah(reader))
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val startPage = rawAyahs.firstOrNull()?.page ?: 1
        val meta = QuranSurahMeta(
            number = number,
            nameAr = nameAr,
            revelationType = revelationType,
            ayahCount = rawAyahs.size,
            startPage = startPage
        )
        surahs.add(meta)
        surahMap[number] = meta

        for (raw in rawAyahs) {
            val displayText = if (raw.numberInSurah == 1 && number != 1 && number != 9) {
                stripBasmalahPrefix(raw.text)
            } else {
                raw.text
            }
            val ayah = QuranAyah(
                surahNumber = number,
                surahNameAr = nameAr,
                numberInSurah = raw.numberInSurah,
                text = raw.text,
                displayText = displayText,
                page = raw.page,
                juz = raw.juz,
                hizbQuarter = raw.hizbQuarter,
                sajda = raw.sajda
            )
            allAyahs.add(ayah)
            ayahsByPage.getOrPut(raw.page) { ArrayList() }.add(ayah)
        }
    }

    private fun readRawAyah(reader: JsonReader): RawAyah {
        var numberInSurah = 0
        var text = ""
        var page = 1
        var juz = 1
        var hizbQuarter = 1
        var sajda = false

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "a" -> numberInSurah = reader.nextInt()
                "t" -> text = reader.nextString()
                "p" -> page = reader.nextInt()
                "j" -> juz = reader.nextInt()
                "h" -> hizbQuarter = reader.nextInt()
                "sj" -> sajda = reader.nextBoolean()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return RawAyah(numberInSurah, text, page, juz, hizbQuarter, sajda)
    }

    private data class RawAyah(
        val numberInSurah: Int,
        val text: String,
        val page: Int,
        val juz: Int,
        val hizbQuarter: Int,
        val sajda: Boolean
    )

    companion object {
        const val BASMALAH_UTHMANI = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"
        private const val BASMALAH_UTHMANI_SHADDAH = "بِّسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"

        fun stripBasmalahPrefix(text: String): String {
            return when {
                text.startsWith(BASMALAH_UTHMANI) -> text.removePrefix(BASMALAH_UTHMANI).trimStart()
                text.startsWith(BASMALAH_UTHMANI_SHADDAH) -> text.removePrefix(BASMALAH_UTHMANI_SHADDAH).trimStart()
                else -> text
            }
        }

        fun toArabicIndicNumber(number: Int): String {
            val digits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
            return number.toString().map { ch ->
                if (ch in '0'..'9') digits[ch - '0'] else ch
            }.joinToString("")
        }

        fun normalizeArabicForSearch(input: String): String {
            val sb = StringBuilder(input.length)
            for (ch in input) {
                when (ch) {
                    in '\u064B'..'\u065F', '\u0670', in '\u06D6'..'\u06ED', '\u0640' -> Unit
                    'ٱ', 'أ', 'إ', 'آ' -> sb.append('ا')
                    'ى' -> sb.append('ي')
                    'ة' -> sb.append('ه')
                    else -> sb.append(ch)
                }
            }
            return sb.toString().trim()
        }

        private val JUZ_ARABIC_NAMES = listOf(
            "الجزء الأول", "الجزء الثاني", "الجزء الثالث", "الجزء الرابع", "الجزء الخامس",
            "الجزء السادس", "الجزء السابع", "الجزء الثامن", "الجزء التاسع", "الجزء العاشر",
            "الجزء الحادي عشر", "الجزء الثاني عشر", "الجزء الثالث عشر", "الجزء الرابع عشر", "الجزء الخامس عشر",
            "الجزء السادس عشر", "الجزء السابع عشر", "الجزء الثامن عشر", "الجزء التاسع عشر", "الجزء العشرون",
            "الجزء الحادي والعشرون", "الجزء الثاني والعشرون", "الجزء الثالث والعشرون", "الجزء الرابع والعشرون", "الجزء الخامس والعشرون",
            "الجزء السادس والعشرون", "الجزء السابع والعشرون", "الجزء الثامن والعشرون", "الجزء التاسع والعشرون", "الجزء الثلاثون"
        )
    }
}
