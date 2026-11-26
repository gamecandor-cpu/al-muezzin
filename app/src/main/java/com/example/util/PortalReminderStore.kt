package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PortalReminder(
    val id: String,
    val title: String,
    val body: String = "",
    val reminderKind: String = "text", // "audio" | "text"
    val persistenceMode: String = "pinned", // "pinned" | "temporary"
    val deliveryMode: String = "now", // "now" | "scheduled" | "daily"
    val scheduledTimeHHmm: String = "08:00",
    val isActive: Boolean = true,
    val triggerNonce: Int = 1,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val expiresAtMillis: Long? = null,
    val audioUrl: String? = null,
    val normalizedAudioUrl: String? = null,
    val audioSourceType: String? = null,
    val localAudioPath: String? = null,
    val audioFileExt: String? = null,
    val audioFileName: String? = null,
    val audioTotalBytes: Long = 0L
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return expiresAtMillis != null && nowMillis > expiresAtMillis
    }

    fun hasLocalAudioFile(): Boolean {
        val path = localAudioPath ?: return false
        return File(path).let { it.exists() && it.length() > 0L }
    }

    fun bestRemoteAudioUrl(): String? {
        return normalizedAudioUrl?.takeIf { it.isNotBlank() }
            ?: audioUrl?.takeIf { it.isNotBlank() }
    }

    fun hasPlayableAudio(): Boolean {
        return reminderKind == "audio" && (hasLocalAudioFile() || bestRemoteAudioUrl() != null)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("body", body)
        put("reminderKind", reminderKind)
        put("persistenceMode", persistenceMode)
        put("deliveryMode", deliveryMode)
        put("scheduledTimeHHmm", scheduledTimeHHmm)
        put("isActive", isActive)
        put("triggerNonce", triggerNonce)
        put("createdAtMs", createdAtMs)
        put("updatedAtMs", updatedAtMs)
        if (expiresAtMillis != null) put("expiresAtMillis", expiresAtMillis) else put("expiresAtMillis", JSONObject.NULL)
        if (audioUrl != null) put("audioUrl", audioUrl) else put("audioUrl", JSONObject.NULL)
        if (normalizedAudioUrl != null) put("normalizedAudioUrl", normalizedAudioUrl) else put("normalizedAudioUrl", JSONObject.NULL)
        if (audioSourceType != null) put("audioSourceType", audioSourceType) else put("audioSourceType", JSONObject.NULL)
        if (localAudioPath != null) put("localAudioPath", localAudioPath) else put("localAudioPath", JSONObject.NULL)
        if (audioFileExt != null) put("audioFileExt", audioFileExt) else put("audioFileExt", JSONObject.NULL)
        if (audioFileName != null) put("audioFileName", audioFileName) else put("audioFileName", JSONObject.NULL)
        put("audioTotalBytes", audioTotalBytes)
    }

    companion object {
        fun fromJson(obj: JSONObject, fallbackLocalPath: String? = null): PortalReminder? {
            val title = obj.optString("title", "").trim()
            if (title.isBlank()) return null
            val rawId = obj.optString("id", "").trim()
            val id = rawId.ifBlank { "rem_${System.currentTimeMillis()}_${title.hashCode()}" }
            val expires = if (obj.has("expiresAtMillis") && !obj.isNull("expiresAtMillis")) {
                obj.optLong("expiresAtMillis", 0L).takeIf { it > 0L }
            } else {
                null
            }
            val rawLocalPath = obj.optString("localAudioPath", "").trim().ifBlank { fallbackLocalPath.orEmpty() }
            val safeLocalPath = rawLocalPath.takeIf { it.isNotBlank() && File(it).exists() }
            val rawAudioUrl = obj.optString("audioUrl", "").trim().ifBlank { null }
            val rawNormUrl = obj.optString("normalizedAudioUrl", "").trim().ifBlank { null }
            val rawSourceType = obj.optString("audioSourceType", "").trim().ifBlank { null }
            val rawExt = obj.optString("audioFileExt", "").trim().ifBlank { null }
            val rawFileName = obj.optString("audioFileName", "").trim().ifBlank { null }
            val rawKind = obj.optString("reminderKind", "text").trim().ifBlank { "text" }
            val normalizedKind = if (rawKind == "audio" || safeLocalPath != null || rawNormUrl != null || rawAudioUrl != null) {
                "audio"
            } else {
                "text"
            }
            return PortalReminder(
                id = id,
                title = title,
                body = obj.optString("body", "").trim(),
                reminderKind = normalizedKind,
                persistenceMode = obj.optString("persistenceMode", "pinned").let {
                    if (it == "temporary") "temporary" else "pinned"
                },
                deliveryMode = obj.optString("deliveryMode", "now").let {
                    if (it == "scheduled" || it == "daily") it else "now"
                },
                scheduledTimeHHmm = PrayerTimeCalculator.cleanTime24(
                    obj.optString("scheduledTimeHHmm", "08:00")
                ).ifBlank { "08:00" },
                isActive = obj.optBoolean("isActive", true),
                triggerNonce = obj.optInt("triggerNonce", 1),
                createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis()),
                updatedAtMs = obj.optLong("updatedAtMs", System.currentTimeMillis()),
                expiresAtMillis = expires,
                audioUrl = rawAudioUrl,
                normalizedAudioUrl = rawNormUrl,
                audioSourceType = rawSourceType,
                localAudioPath = safeLocalPath,
                audioFileExt = rawExt,
                audioFileName = rawFileName,
                audioTotalBytes = obj.optLong("audioTotalBytes", 0L)
            )
        }
    }
}

object PortalReminderStore {
    private const val PREFS_NAME = "muezzin_portal_reminders_store"
    private const val KEY_REMINDERS_JSON = "reminders_json_v1"
    private const val PORTAL_AUDIO_DIR = "portal_audio"
    private const val MAX_META_BYTES = 4 * 1024 * 1024
    private const val MAX_AUDIO_ASSET_BYTES = 210L * 1024L * 1024L // 200 MB + safety margin
    private val MAGIC_V1 = "MUEZZIN1".toByteArray(Charsets.US_ASCII)
    private val MAGIC_V2 = "MUEZZIN2".toByteArray(Charsets.US_ASCII)

    private val _remindersFlow = MutableStateFlow<List<PortalReminder>>(emptyList())
    val remindersFlow: StateFlow<List<PortalReminder>> = _remindersFlow.asStateFlow()

    @Synchronized
    fun loadAll(context: Context): List<PortalReminder> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_REMINDERS_JSON, null)
        if (raw.isNullOrBlank()) {
            _remindersFlow.value = emptyList()
            return emptyList()
        }
        val now = System.currentTimeMillis()
        val parsed = runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val item = PortalReminder.fromJson(obj) ?: continue
                    if (!item.isExpired(now)) {
                        add(item)
                    }
                }
            }.sortedByDescending { it.updatedAtMs }
        }.getOrDefault(emptyList())
        _remindersFlow.value = parsed
        return parsed
    }

    @Synchronized
    fun getById(context: Context, id: String): PortalReminder? {
        return loadAll(context).firstOrNull { it.id == id }
    }

    @Synchronized
    fun upsertAll(context: Context, incoming: List<PortalReminder>): List<PortalReminder> {
        if (incoming.isEmpty()) return loadAll(context)
        val existing = loadAll(context).associateBy { it.id }.toMutableMap()
        val now = System.currentTimeMillis()
        for (item in incoming) {
            if (!item.isExpired(now)) {
                val prev = existing[item.id]
                val mergedLocalPath = item.localAudioPath ?: prev?.localAudioPath
                val mergedBytes = if (item.audioTotalBytes > 0L) item.audioTotalBytes else (prev?.audioTotalBytes ?: 0L)
                existing[item.id] = item.copy(
                    localAudioPath = mergedLocalPath,
                    audioTotalBytes = mergedBytes
                )
            }
        }
        val result = existing.values.sortedByDescending { it.updatedAtMs }
        saveList(context, result)
        return result
    }

    @Synchronized
    fun toggleActive(context: Context, id: String): List<PortalReminder> {
        val updated = loadAll(context).map { item ->
            if (item.id == id) {
                item.copy(isActive = !item.isActive, updatedAtMs = System.currentTimeMillis())
            } else {
                item
            }
        }
        saveList(context, updated)
        return updated
    }

    @Synchronized
    fun deleteById(context: Context, id: String): List<PortalReminder> {
        val current = loadAll(context)
        val target = current.firstOrNull { it.id == id }
        val remaining = current.filterNot { it.id == id }
        saveList(context, remaining)
        val path = target?.localAudioPath
        if (!path.isNullOrBlank() && remaining.none { it.localAudioPath == path }) {
            runCatching { File(path).delete() }
        }
        return remaining
    }

    private fun saveList(context: Context, list: List<PortalReminder>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REMINDERS_JSON, arr.toString())
            .apply()
        _remindersFlow.value = list
    }

    fun importFromUri(context: Context, uri: Uri): List<PortalReminder> {
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme == "almuezzin") {
            return importFromDeepLinkUri(context, uri)
        }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            importFromInputStream(context, stream)
        }.orEmpty()
    }

    fun importFromDeepLinkUri(context: Context, uri: Uri): List<PortalReminder> {
        (uri.getQueryParameter("appId")
            ?: uri.getQueryParameter("onesignalAppId")
            ?: uri.getQueryParameter("oneSignalAppId"))
            ?.let { OneSignalManager.saveSyncedAppId(context, it) }

        val encoded = uri.getQueryParameter("d")
            ?: uri.getQueryParameter("payload")
            ?: uri.getQueryParameter("data")
            ?: return emptyList()
        val jsonStr = decodeBase64Flexible(encoded) ?: return emptyList()
        return importFromJsonString(context, jsonStr)
    }

    fun importFromText(context: Context, text: String): List<PortalReminder> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()
        if (trimmed.startsWith("almuezzin://", ignoreCase = true)) {
            return importFromDeepLinkUri(context, Uri.parse(trimmed))
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return importFromJsonString(context, trimmed)
        }
        val decoded = decodeBase64Flexible(trimmed)
        if (!decoded.isNullOrBlank() && (decoded.startsWith("{") || decoded.startsWith("["))) {
            return importFromJsonString(context, decoded)
        }
        return emptyList()
    }

    fun importFromJsonString(context: Context, raw: String): List<PortalReminder> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return emptyList()
        val list = mutableListOf<PortalReminder>()
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { PortalReminder.fromJson(it)?.let(list::add) }
            }
        } else {
            val root = JSONObject(trimmed)
            val embeddedAppId = root.optString("oneSignalAppId", "")
                .ifBlank { root.optString("onesignalAppId", "") }
                .trim()
            if (embeddedAppId.isNotBlank()) {
                OneSignalManager.saveSyncedAppId(context, embeddedAppId)
            }
            if (root.has("reminders")) {
                val arr = root.optJSONArray("reminders")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { PortalReminder.fromJson(it)?.let(list::add) }
                    }
                }
            } else if (root.has("reminder")) {
                root.optJSONObject("reminder")?.let { PortalReminder.fromJson(it)?.let(list::add) }
            } else {
                PortalReminder.fromJson(root)?.let(list::add)
            }
        }
        if (list.isNotEmpty()) {
            upsertAll(context, list)
        }
        return list
    }

    fun importFromInputStream(context: Context, inputStream: InputStream): List<PortalReminder> {
        val buffered = BufferedInputStream(inputStream, 64 * 1024)
        buffered.mark(16)
        val header = ByteArray(8)
        val readCount = readFullyUpTo(buffered, header)
        if (readCount == 8 && header.contentEquals(MAGIC_V2)) {
            return importV2Bundle(context, buffered)
        }
        if (readCount == 8 && header.contentEquals(MAGIC_V1)) {
            return importV1Bundle(context, buffered)
        }
        buffered.reset()
        val jsonBytes = readCappedBytes(buffered, MAX_META_BYTES)
        return importFromJsonString(context, jsonBytes.toString(Charsets.UTF_8))
    }

    private fun importV1Bundle(context: Context, buffered: BufferedInputStream): List<PortalReminder> {
        val lenBytes = ByteArray(4)
        readExact(buffered, lenBytes)
        val metaLen = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).int
        require(metaLen in 2..MAX_META_BYTES) { "Invalid bundle metadata length" }
        val metaBytes = ByteArray(metaLen)
        readExact(buffered, metaBytes)
        val obj = JSONObject(metaBytes.toString(Charsets.UTF_8))
        val reminderObj = if (obj.has("reminder")) obj.getJSONObject("reminder") else obj
        val id = reminderObj.optString("id", "rem_${System.currentTimeMillis()}")
        val ext = sanitizeExt(reminderObj.optString("audioFileExt", "mp3"))
        val audioFile = prepareAudioFile(context, id, ext)
        val bytesWritten = copyStreamToFile(buffered, audioFile, null)
        val parsed = PortalReminder.fromJson(reminderObj, fallbackLocalPath = if (bytesWritten > 0L) audioFile.absolutePath else null)
            ?.let { r ->
                if (bytesWritten > 0L) r.copy(localAudioPath = audioFile.absolutePath, audioTotalBytes = bytesWritten)
                else {
                    runCatching { audioFile.delete() }
                    r
                }
            }
        return if (parsed != null) {
            upsertAll(context, listOf(parsed))
            listOf(parsed)
        } else {
            emptyList()
        }
    }

    private fun importV2Bundle(context: Context, buffered: BufferedInputStream): List<PortalReminder> {
        val lenBytes = ByteArray(4)
        readExact(buffered, lenBytes)
        val metaLen = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).int
        require(metaLen in 2..MAX_META_BYTES) { "Invalid bundle metadata length" }
        val metaBytes = ByteArray(metaLen)
        readExact(buffered, metaBytes)
        val meta = JSONObject(metaBytes.toString(Charsets.UTF_8))

        val assetPathMap = mutableMapOf<String, Pair<String, Long>>()
        val assetsArr = meta.optJSONArray("assets")
        if (assetsArr != null) {
            for (i in 0 until assetsArr.length()) {
                val assetMeta = assetsArr.optJSONObject(i) ?: continue
                val assetId = assetMeta.optString("assetId", "").trim()
                val byteLength = assetMeta.optLong("byteLength", 0L)
                val ext = sanitizeExt(assetMeta.optString("fileExt", assetMeta.optString("ext", "mp3")))
                if (assetId.isBlank() || byteLength <= 0L || byteLength > MAX_AUDIO_ASSET_BYTES) {
                    continue
                }
                val targetFile = prepareAudioFile(context, assetId, ext)
                val written = copyStreamToFile(buffered, targetFile, byteLength)
                if (written == byteLength) {
                    assetPathMap[assetId] = targetFile.absolutePath to written
                } else {
                    runCatching { targetFile.delete() }
                }
            }
        }

        val remindersArr = meta.optJSONArray("reminders") ?: return emptyList()
        val imported = mutableListOf<PortalReminder>()
        for (i in 0 until remindersArr.length()) {
            val remObj = remindersArr.optJSONObject(i) ?: continue
            val assetId = remObj.optString("audioAssetId", "").trim()
            val mapped = if (assetId.isNotBlank()) assetPathMap[assetId] else null
            val parsed = PortalReminder.fromJson(remObj, fallbackLocalPath = mapped?.first)?.let { r ->
                if (mapped != null) {
                    r.copy(localAudioPath = mapped.first, audioTotalBytes = mapped.second)
                } else {
                    r
                }
            }
            if (parsed != null) {
                imported.add(parsed)
            }
        }

        if (imported.isNotEmpty()) {
            upsertAll(context, imported)
        }
        return imported
    }

    private fun prepareAudioFile(context: Context, key: String, ext: String): File {
        val dir = File(context.applicationContext.filesDir, PORTAL_AUDIO_DIR)
        if (!dir.exists()) dir.mkdirs()
        val safeKey = key.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64).ifBlank { "audio" }
        return File(dir, "$safeKey.$ext")
    }

    private fun sanitizeExt(raw: String): String {
        val cleaned = raw.lowercase().replace(Regex("[^a-z0-9]"), "")
        return cleaned.takeIf { it in setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "webm") } ?: "mp3"
    }

    private fun copyStreamToFile(input: InputStream, dest: File, exactBytes: Long?): Long {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        FileOutputStream(dest).use { out ->
            while (true) {
                val toRead = if (exactBytes != null) {
                    val remaining = exactBytes - total
                    if (remaining <= 0L) break
                    minOf(buf.size.toLong(), remaining).toInt()
                } else {
                    buf.size
                }
                val n = input.read(buf, 0, toRead)
                if (n <= 0) break
                total += n
                if (total > MAX_AUDIO_ASSET_BYTES) {
                    throw IllegalStateException("Audio file exceeds 200 MB limit")
                }
                out.write(buf, 0, n)
            }
        }
        return total
    }

    private fun readFullyUpTo(input: InputStream, dest: ByteArray): Int {
        var offset = 0
        while (offset < dest.size) {
            val n = input.read(dest, offset, dest.size - offset)
            if (n <= 0) break
            offset += n
        }
        return offset
    }

    private fun readExact(input: InputStream, dest: ByteArray) {
        val n = readFullyUpTo(input, dest)
        if (n < dest.size) throw EOFException("Unexpected end of stream")
    }

    private fun readCappedBytes(input: InputStream, maxBytes: Int): ByteArray {
        val buf = ByteArray(16 * 1024)
        val out = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            total += n
            if (total > maxBytes) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun decodeBase64Flexible(encoded: String): String? {
        val cleaned = encoded.trim()
        if (cleaned.isBlank()) return null
        val flags = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        return runCatching {
            String(Base64.decode(cleaned, flags), Charsets.UTF_8)
        }.getOrElse {
            runCatching {
                String(Base64.decode(cleaned, Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull()
        }
    }
}
