package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * أنواع الأصوات التي يمكن التأكد منها فعلياً عبر فحص الترويسات والبصمة الأولى للملف.
 */
enum class AudioKind(val labelAr: String) {
    MP3("MP3"),
    M4A("M4A/AAC"),
    AAC("AAC"),
    OGG("OGG/OPUS"),
    WAV("WAV"),
    FLAC("FLAC"),
    HLS("HLS"),
    UNKNOWN("")
}

data class AudioInspection(
    val ok: Boolean,
    val kind: AudioKind,
    val contentType: String,
    val isCloudHost: Boolean,
    val detail: String
)

/**
 * فحص البصمات السحرية (Magic Bytes) للأوّل القصير من الملف — لا يحتاج شبكة، قابل للاختبار.
 */
object AudioMagicSniffer {

    fun sniff(bytes: ByteArray): AudioKind {
        if (bytes.isEmpty()) return AudioKind.UNKNOWN
        // ADTS AAC frame (0xFFF1 / 0xFFF9) يُفحص قبل مزامنة MP3 لأنه يبدأ بـ 0xFF أيضاً
        if (bytes.size >= 2 &&
            bytes[0] == 0xFF.toByte() &&
            (bytes[1] == 0xF1.toByte() || bytes[1] == 0xF9.toByte())
        ) return AudioKind.AAC

        // ID3v2 => MP3
        if (bytes.size >= 3 && bytes[0] == 0x49 && bytes[1] == 0x44 && bytes[2] == 0x33) return AudioKind.MP3

        // MPEG audio frame sync 0xFFE0..0xFFEF
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0) return AudioKind.MP3

        // ISO BMFF ftyp (M4A / MP4 audio)
        if (bytes.size >= 12 &&
            bytes[4] == 0x66 && bytes[5] == 0x74 && bytes[6] == 0x79 && bytes[7] == 0x70
        ) {
            val brand = String(bytes.copyOfRange(8, 12), Charsets.US_ASCII).lowercase()
            if (Regex("m4a|m4b|mp4a|mp4|isom|qt").containsMatchIn(brand)) return AudioKind.M4A
        }

        // OggS => OGG / OPUS
        if (bytes.size >= 4 &&
            bytes[0] == 0x4F && bytes[1] == 0x67 && bytes[2] == 0x67 && bytes[3] == 0x53
        ) return AudioKind.OGG

        // RIFF....WAVE
        if (bytes.size >= 12 &&
            bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46 &&
            bytes[8] == 0x57 && bytes[9] == 0x41 && bytes[10] == 0x56 && bytes[11] == 0x45
        ) return AudioKind.WAV

        // fLaC
        if (bytes.size >= 4 &&
            bytes[0] == 0x66 && bytes[1] == 0x4C && bytes[2] == 0x61 && bytes[3] == 0x43
        ) return AudioKind.FLAC

        // ADIF header
        if (bytes.size >= 4 &&
            bytes[0] == 0x41 && bytes[1] == 0x44 && bytes[2] == 0x49 && bytes[3] == 0x46
        ) return AudioKind.AAC

        // HLS playlist نصي
        val head = String(bytes.copyOfRange(0, minOf(256, bytes.size)), Charsets.US_ASCII).trimStart()
        if (head.startsWith("#EXTM3U")) return AudioKind.HLS

        return AudioKind.UNKNOWN
    }

    fun looksAudioContentType(contentType: String?): Boolean {
        val ct = (contentType ?: "").lowercase()
        if (ct.isEmpty()) return false
        return ct.startsWith("audio/") ||
            ct.contains("application/ogg") ||
            ct.contains("application/x-ogg") ||
            ct.contains("application/vnd.apple.mpegurl") ||
            ct.contains("application/x-mpegurl") ||
            ct.contains("audio/mpegurl")
    }

    fun kindFromContentType(ct: String): AudioKind {
        val lower = ct.lowercase()
        return when {
            lower.contains("mpegurl") || lower.contains("m3u8") -> AudioKind.HLS
            lower.contains("mp3") || lower.contains("mpeg") -> AudioKind.MP3
            lower.contains("m4a") || lower.contains("mp4") || lower.contains("aac") -> AudioKind.M4A
            lower.contains("ogg") || lower.contains("opus") -> AudioKind.OGG
            lower.contains("wav") || lower.contains("wave") -> AudioKind.WAV
            lower.contains("flac") -> AudioKind.FLAC
            else -> AudioKind.UNKNOWN
        }
    }
}

/**
 * تحقق فعلي من رابط صوتي:
 * 1) طلب HEAD لقراءة Content-Type إن أمكن.
 * 2) إن لم يكن صريحاً، طلب GET بمدى صغير (Range) لقراءة البصمة الأولى للملف.
 * 3) قبول روابط التخزين السحابي (Drive/Dropbox) مع تنبيه مشاركتها العامة.
 */
object RemoteAudioVerifier {
    private const val CONNECT_TIMEOUT_MS = 7000
    private const val READ_TIMEOUT_MS = 8000
    private const val BYTE_LIMIT = 4096

    suspend fun inspect(rawUrl: String): AudioInspection = withContext(Dispatchers.IO) {
        if (!rawUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext AudioInspection(
                ok = false,
                kind = AudioKind.UNKNOWN,
                contentType = "",
                isCloudHost = false,
                detail = "الرابط يجب أن يبدأ بـ https://"
            )
        }

        val isCloud = rawUrl.contains("drive.google.com", true) ||
            rawUrl.contains("docs.google.com", true) ||
            rawUrl.contains("dropbox.com", true)

        var conn: HttpURLConnection? = null
        try {
            val url = URL(rawUrl)

            // المرحلة 1: HEAD لقراءة الترويسة
            var status = -1
            var contentType = ""
            var headReachable = false
            runCatching {
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Al-Muezzin/1.0")
                    setRequestProperty("Accept", "audio/*, application/ogg, application/vnd.apple.mpegurl, */*")
                }
                status = conn!!.responseCode
                contentType = conn!!.contentType ?: ""
                headReachable = status in 200..399
            }

            // المرحلة 2: GET Range لقراءة البصمة عند الحاجة
            val magic = if (headReachable && AudioMagicSniffer.looksAudioContentType(contentType)) {
                AudioKind.UNKNOWN
            } else {
                conn?.disconnect()
                conn = null
                var observed = ByteArray(0)
                runCatching {
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = CONNECT_TIMEOUT_MS
                        readTimeout = READ_TIMEOUT_MS
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "Al-Muezzin/1.0")
                        setRequestProperty("Range", "bytes=0-$BYTE_LIMIT")
                        setRequestProperty("Accept", "audio/*, */*")
                    }
                    status = conn!!.responseCode
                    contentType = conn!!.contentType ?: contentType
                    val stream = if (status in 200..299) conn!!.inputStream else conn!!.errorStream
                    observed = stream?.use { readLimited(it, BYTE_LIMIT) } ?: ByteArray(0)
                }
                AudioMagicSniffer.sniff(observed)
            }

            val mimeOk = AudioMagicSniffer.looksAudioContentType(contentType)
            when {
                magic != AudioKind.UNKNOWN -> AudioInspection(
                    ok = true, kind = magic, contentType = contentType, isCloudHost = isCloud,
                    detail = "تم التأكد من البصمة: ${magic.labelAr}"
                )
                mimeOk -> AudioInspection(
                    ok = true,
                    kind = AudioMagicSniffer.kindFromContentType(contentType),
                    contentType = contentType,
                    isCloudHost = isCloud,
                    detail = "تم التأكد من نوع المحتوى الصوتي"
                )
                isCloud && status in 200..399 -> AudioInspection(
                    ok = true, kind = AudioKind.UNKNOWN, contentType = contentType, isCloudHost = true,
                    detail = "رابط سحابي قابل للوصول — تأكد أن الملف عام «لأي شخص لديه الرابط»"
                )
                else -> AudioInspection(
                    ok = false, kind = AudioKind.UNKNOWN, contentType = contentType, isCloudHost = isCloud,
                    detail = "الرابط لا يقدّم ملفاً صوتياً (status=$status, contentType=$contentType)"
                )
            }
        } catch (e: Exception) {
            AudioInspection(
                ok = false, kind = AudioKind.UNKNOWN, contentType = "", isCloudHost = isCloud,
                detail = "تعذّر فحص الرابط: ${e.message ?: "خطأ شبكة"}"
            )
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun readLimited(stream: java.io.InputStream, limit: Int): ByteArray {
        val buffer = ByteArray(4096)
        val out = ByteArrayOutputStream()
        var total = 0
        while (total < limit) {
            val read = stream.read(buffer, 0, minOf(buffer.size, limit - total))
            if (read == -1) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray()
    }
}
