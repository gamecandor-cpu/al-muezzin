package com.example.util

import java.util.Locale

data class ResolvedAudioLink(
    val originalUrl: String,
    val directPlayUrl: String,
    val sourceTypeLabel: String,
    val isValidHttps: Boolean
)

object AudioUrlResolver {

    private val DRIVE_FILE_REGEX = Regex("drive\\.google\\.com/file/d/([a-zA-Z0-9_-]+)")
    private val DRIVE_ID_REGEX = Regex("[?&]id=([a-zA-Z0-9_-]+)")

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
                    isValidHttps = true
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
                isValidHttps = true
            )
        }

        val detectedExt = listOf(".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".flac")
            .firstOrNull { lower.substringBefore("?").endsWith(it) }

        val label = when (detectedExt) {
            ".mp3" -> "ملف صوت MP3"
            ".m4a" -> "ملف صوت M4A"
            ".aac" -> "ملف صوت AAC"
            ".ogg" -> "ملف صوت OGG"
            ".opus" -> "ملف صوت OPUS"
            ".wav" -> "ملف صوت WAV"
            ".flac" -> "ملف صوت FLAC"
            else -> "بث صوتي مباشر"
        }

        return ResolvedAudioLink(
            originalUrl = trimmed,
            directPlayUrl = trimmed,
            sourceTypeLabel = label,
            isValidHttps = true
        )
    }
}
