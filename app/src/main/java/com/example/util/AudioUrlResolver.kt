package com.example.util
import java.util.Locale

data class ResolvedAudioLink(
    val originalUrl: String,
    val directPlayUrl: String,
    val sourceTypeLabel: String,
    val isValidHttps: Boolean,
    val likelyDirectAudio: Boolean = false,
    val isCloud: Boolean = false
)

object AudioUrlResolver {
    private val DRIVE_FILE_REGEX = Regex("drive\\.google\\.com/file/d/([a-zA-Z0-9_-]+)")
    private val DRIVE_ID_REGEX = Regex("[?&]id=([a-zA-Z0-9_-]+)")

    private val AUDIO_EXT_LABELS = linkedMapOf(
        ".mp3" to "ملف صوت MP3",
        ".m4a" to "ملف صوت M4A",
        ".aac" to "ملف صوت AAC",
        ".ogg" to "ملف صوت OGG",
        ".opus" to "ملف صوت OPUS",
        ".wav" to "ملف صوت WAV",
        ".flac" to "ملف صوت FLAC",
        ".m3u8" to "بث HLS صوتي",
        ".m3u" to "قائمة تشغيل صوتي M3U"
    )

    fun resolve(rawUrl: String): ResolvedAudioLink {
        val trimmed = rawUrl.trim()
        val isHttps = trimmed.startsWith("https://", ignoreCase = true)
        if (!isHttps) {
            return ResolvedAudioLink(
                originalUrl = trimmed,
                directPlayUrl = trimmed,
                sourceTypeLabel = "رابط غير مدعوم (يتطلب https)",
                isValidHttps = false
            )
        }
        val lower = trimmed.lowercase(Locale.US)

        if (lower.contains("drive.google.com") || lower.contains("docs.google.com")) {
            val fileMatch = DRIVE_FILE_REGEX.find(trimmed)?.groupValues?.getOrNull(1)
                ?: DRIVE_ID_REGEX.find(trimmed)?.groupValues?.getOrNull(1)
            if (!fileMatch.isNullOrBlank()) {
                return ResolvedAudioLink(
                    originalUrl = trimmed,
                    directPlayUrl = "https://drive.google.com/uc?export=download&id=$fileMatch",
                    sourceTypeLabel = "Google Drive صوت",
                    isValidHttps = true,
                    likelyDirectAudio = true,
                    isCloud = true
                )
            }
        }

        if (lower.contains("dropbox.com")) {
            val directDropbox = when {
                lower.contains("dl=0") -> trimmed.replace("dl=0", "dl=1")
                lower.contains("dl=1") -> trimmed
                lower.contains("?") -> "$trimmed&dl=1"
                else -> "$trimmed?dl=1"
            }
            return ResolvedAudioLink(
                originalUrl = trimmed,
                directPlayUrl = directDropbox,
                sourceTypeLabel = "Dropbox صوت",
                isValidHttps = true,
                likelyDirectAudio = true,
                isCloud = true
            )
        }

        val cleanPath = lower.substringBefore("?").substringBefore("#")
        val detectedExt = AUDIO_EXT_LABELS.entries.firstOrNull { (ext, _) -> cleanPath.endsWith(ext) }
        val label = detectedExt?.value ?: "بث صوتي مباشر"

        return ResolvedAudioLink(
            originalUrl = trimmed,
            directPlayUrl = trimmed,
            sourceTypeLabel = label,
            isValidHttps = true,
            likelyDirectAudio = detectedExt != null,
            isCloud = false
        )
    }
}
