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
    val detail: String,
    val isHtmlContent: Boolean = false,
    val finalUrl: String = "",
    val extractedAudioUrl: String? = null
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
        if (bytes.size >= 3 && bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte()) return AudioKind.MP3

        // MPEG audio frame sync 0xFFE0..0xFFEF
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0) return AudioKind.MP3

        // ISO BMFF ftyp (M4A / MP4 audio)
        if (bytes.size >= 12 &&
            bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() && bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()
        ) {
            val brand = String(bytes.copyOfRange(8, 12), Charsets.US_ASCII).lowercase()
            if (Regex("m4a|m4b|mp4a|mp4|isom|qt").containsMatchIn(brand)) return AudioKind.M4A
        }

        // OggS => OGG / OPUS
        if (bytes.size >= 4 &&
            bytes[0] == 0x4F.toByte() && bytes[1] == 0x67.toByte() && bytes[2] == 0x67.toByte() && bytes[3] == 0x53.toByte()
        ) return AudioKind.OGG

        // RIFF....WAVE
        if (bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x41.toByte() && bytes[10] == 0x56.toByte() && bytes[11] == 0x45.toByte()
        ) return AudioKind.WAV

        // fLaC
        if (bytes.size >= 4 &&
            bytes[0] == 0x66.toByte() && bytes[1] == 0x4C.toByte() && bytes[2] == 0x61.toByte() && bytes[3] == 0x43.toByte()
        ) return AudioKind.FLAC

        // ADIF header
        if (bytes.size >= 4 &&
            bytes[0] == 0x41.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x49.toByte() && bytes[3] == 0x46.toByte()
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
 * 1) تتبع التحويلات (301/302/303/307/308) حتى 5 خطوات.
 * 2) طلب HEAD لقراءة Content-Type إن أمكن.
 * 3) إن لم يكن صريحاً، طلب GET بمدى صغير (Range) لقراءة البصمة الأولى للملف.
 * 4) إن كانت النتيجة صفحة HTML، يحاول استخراج رابط الصوت أو رابط التأكيد منها.
 */
object RemoteAudioVerifier {
    private const val CONNECT_TIMEOUT_MS = 7000
    private const val READ_TIMEOUT_MS = 8000
    private const val BYTE_LIMIT = 4096
    private const val HTML_LIMIT = 65536
    private const val MAX_REDIRECTS = 5
    const val USER_AGENT = "Al-Muezzin/1.0"

    suspend fun inspect(rawUrl: String): AudioInspection = withContext(Dispatchers.IO) {
        if (!rawUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext AudioInspection(
                ok = false,
                kind = AudioKind.UNKNOWN,
                contentType = "",
                isCloudHost = false,
                detail = "الرابط يجب أن يبدأ بـ https://",
                finalUrl = rawUrl
            )
        }

        val isCloud = rawUrl.contains("drive.google.com", true) ||
            rawUrl.contains("docs.google.com", true) ||
            rawUrl.contains("dropbox.com", true)

        var conn: HttpURLConnection? = null
        try {
            var currentUrl = rawUrl
            var status = -1
            var contentType = ""
            var headReachable = false

            // المرحلة 1: HEAD مع تتبع التحويلات
            runCatching {
                val (headConn, finalHeadUrl, headCode) = openFollowingRedirects(
                    startUrl = rawUrl,
                    method = "HEAD",
                    extraHeaders = mapOf("Accept" to "audio/*, application/ogg, application/vnd.apple.mpegurl, */*")
                )
                conn = headConn
                currentUrl = finalHeadUrl
                status = headCode
                contentType = headConn.contentType ?: ""
                headReachable = status in 200..299
            }

            // المرحلة 2: GET Range لقراءة البصمة أو HTML عند الحاجة
            val headMimeOk = headReachable && AudioMagicSniffer.looksAudioContentType(contentType)
            var observed = ByteArray(0)
            var htmlFlag = looksHtmlContentType(contentType)
            var extractedAudio: String? = null

            val magic = if (headMimeOk) {
                AudioKind.UNKNOWN
            } else {
                conn?.disconnect()
                conn = null
                runCatching {
                    val (getConn, finalGetUrl, getCode) = openFollowingRedirects(
                        startUrl = currentUrl,
                        method = "GET",
                        extraHeaders = mapOf(
                            "Range" to "bytes=0-${BYTE_LIMIT - 1}",
                            "Accept" to "audio/*, text/html, */*"
                        )
                    )
                    conn = getConn
                    currentUrl = finalGetUrl
                    status = getCode
                    contentType = getConn.contentType ?: contentType
                    htmlFlag = looksHtmlContentType(contentType)
                    val stream = if (status in 200..299) getConn.inputStream else getConn.errorStream
                    val limit = if (htmlFlag) HTML_LIMIT else BYTE_LIMIT
                    observed = stream?.use { readLimited(it, limit) } ?: ByteArray(0)
                    if (!htmlFlag && observed.isNotEmpty()) {
                        val prefix = String(observed.copyOfRange(0, minOf(256, observed.size)), Charsets.UTF_8).trimStart().lowercase()
                        if (prefix.startsWith("<!doctype") || prefix.startsWith("<html")) {
                            htmlFlag = true
                        }
                    }
                    if (htmlFlag && observed.isNotEmpty()) {
                        extractedAudio = extractAudioFromHtml(String(observed, Charsets.UTF_8), currentUrl)
                    }
                }
                if (htmlFlag) AudioKind.UNKNOWN else AudioMagicSniffer.sniff(observed)
            }

            val mimeOk = AudioMagicSniffer.looksAudioContentType(contentType)
            when {
                magic != AudioKind.UNKNOWN -> AudioInspection(
                    ok = true,
                    kind = magic,
                    contentType = contentType,
                    isCloudHost = isCloud,
                    detail = "تم التأكد من البصمة: ${magic.labelAr}",
                    finalUrl = currentUrl
                )
                mimeOk -> AudioInspection(
                    ok = true,
                    kind = AudioMagicSniffer.kindFromContentType(contentType),
                    contentType = contentType,
                    isCloudHost = isCloud,
                    detail = "تم التأكد من نوع المحتوى الصوتي",
                    finalUrl = currentUrl
                )
                !extractedAudio.isNullOrBlank() -> AudioInspection(
                    ok = true,
                    kind = AudioMagicSniffer.kindFromContentType(extractedAudio),
                    contentType = contentType,
                    isCloudHost = isCloud,
                    detail = "تم استخراج رابط صوتي مباشر من الصفحة",
                    isHtmlContent = true,
                    finalUrl = currentUrl,
                    extractedAudioUrl = extractedAudio
                )
                isCloud && status in 200..399 -> AudioInspection(
                    ok = true,
                    kind = AudioKind.UNKNOWN,
                    contentType = contentType,
                    isCloudHost = true,
                    detail = "رابط سحابي قابل للوصول — تأكد أن الملف عام «لأي شخص لديه الرابط»",
                    isHtmlContent = htmlFlag,
                    finalUrl = currentUrl
                )
                else -> AudioInspection(
                    ok = false,
                    kind = AudioKind.UNKNOWN,
                    contentType = contentType,
                    isCloudHost = isCloud,
                    detail = "الرابط لا يقدّم ملفاً صوتياً صريحاً (status=$status, contentType=$contentType)",
                    isHtmlContent = htmlFlag,
                    finalUrl = currentUrl
                )
            }
        } catch (e: Exception) {
            AudioInspection(
                ok = false,
                kind = AudioKind.UNKNOWN,
                contentType = "",
                isCloudHost = isCloud,
                detail = "تعذّر فحص الرابط: ${e.message ?: "خطأ شبكة"}",
                finalUrl = rawUrl
            )
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun openFollowingRedirects(
        startUrl: String,
        method: String,
        extraHeaders: Map<String, String>
    ): Triple<HttpURLConnection, String, Int> {
        var current = startUrl
        var redirects = 0
        while (true) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = connection.responseCode
            if (code in listOf(301, 302, 303, 307, 308) && redirects < MAX_REDIRECTS) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (!location.isNullOrBlank()) {
                    val resolved = URL(URL(current), location).toString()
                    if (resolved.startsWith("https://", ignoreCase = true)) {
                        current = resolved
                        redirects++
                        continue
                    }
                }
            }
            return Triple(connection, current, code)
        }
    }

    private fun looksHtmlContentType(ct: String?): Boolean {
        val lower = (ct ?: "").lowercase()
        return lower.contains("text/html") || lower.contains("application/xhtml")
    }

    private fun extractAudioFromHtml(html: String, baseUrl: String): String? {
        val patterns = listOf(
            Regex("""<audio[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<source[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+property=["']og:audio(?::secure_url)?["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""(https://[^\s"'<>\\]+\.(?:mp3|m4a|aac|ogg|opus|wav|flac|m3u8)(?:\?[^\s"'<>\\]*)?)""", RegexOption.IGNORE_CASE)
        )
        for (regex in patterns) {
            val match = regex.find(html)?.groupValues?.getOrNull(1)?.trim() ?: continue
            val resolved = runCatching { URL(URL(baseUrl), match).toString() }.getOrNull() ?: continue
            if (resolved.startsWith("https://", ignoreCase = true)) {
                return resolved
            }
        }
        return null
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
