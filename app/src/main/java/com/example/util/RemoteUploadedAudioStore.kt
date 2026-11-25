package com.example.util

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * يجلب ويُخزّن مؤقتاً مقاطع الصوت المرفوعة من جهاز المدير عبر لوحة التحكم في Firestore،
 * بحيث يشتغل المقطع فورياً وبنفس اللحظة على أجهزة المستخدمين.
 */
object RemoteUploadedAudioStore {
    private const val TAG = "RemoteUploadedAudio"
    private const val COLLECTION_CHUNKS = "broadcast_audio_chunks"
    private val VALID_ASSET_ID = Regex("^[a-zA-Z0-9_\\-]{1,128}$")
    private val VALID_EXT = Regex("^[a-z0-9]{1,8}$")

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inflightJobs = ConcurrentHashMap<String, Deferred<File?>>()

    fun getCachedFileIfReady(
        context: Context,
        assetId: String?,
        fileExt: String?
    ): File? {
        val safeId = sanitizeAssetId(assetId) ?: return null
        val safeExt = sanitizeExt(fileExt)
        val cacheDir = File(context.applicationContext.cacheDir, "uploaded_audio")
        val target = File(cacheDir, "$safeId.$safeExt")
        return if (target.exists() && target.length() > 0L && isConfined(cacheDir, target)) {
            target
        } else {
            null
        }
    }

    suspend fun getOrFetchAudioFile(
        context: Context,
        assetId: String?,
        chunkCount: Int,
        fileExt: String?
    ): File? {
        val appCtx = context.applicationContext
        val safeId = sanitizeAssetId(assetId) ?: return null
        if (chunkCount !in 1..30) return null
        val safeExt = sanitizeExt(fileExt)

        getCachedFileIfReady(appCtx, safeId, safeExt)?.let { return it }

        val jobKey = "${safeId}_$chunkCount"
        val deferred = inflightJobs.computeIfAbsent(jobKey) {
            storeScope.async {
                try {
                    fetchAndAssembleToCache(appCtx, safeId, chunkCount, safeExt)
                } finally {
                    inflightJobs.remove(jobKey)
                }
            }
        }
        return deferred.await()
    }

    fun prefetchInBackground(
        context: Context,
        assetId: String?,
        chunkCount: Int,
        fileExt: String?
    ) {
        val appCtx = context.applicationContext
        val safeId = sanitizeAssetId(assetId) ?: return
        if (chunkCount !in 1..30) return
        if (getCachedFileIfReady(appCtx, safeId, fileExt) != null) return
        storeScope.launchFetchQuietly(appCtx, safeId, chunkCount, fileExt)
    }

    private fun CoroutineScope.launchFetchQuietly(
        appCtx: Context,
        assetId: String,
        chunkCount: Int,
        fileExt: String?
    ) {
        val jobKey = "${assetId}_$chunkCount"
        inflightJobs.computeIfAbsent(jobKey) {
            async {
                try {
                    fetchAndAssembleToCache(appCtx, assetId, chunkCount, sanitizeExt(fileExt))
                } finally {
                    inflightJobs.remove(jobKey)
                }
            }
        }
    }

    private suspend fun fetchAndAssembleToCache(
        appCtx: Context,
        assetId: String,
        chunkCount: Int,
        safeExt: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(appCtx.cacheDir, "uploaded_audio").apply { mkdirs() }
            val finalFile = File(cacheDir, "$assetId.$safeExt")
            val tmpFile = File(cacheDir, "$assetId.$safeExt.tmp")
            if (!isConfined(cacheDir, finalFile) || !isConfined(cacheDir, tmpFile)) {
                return@withContext null
            }
            if (finalFile.exists() && finalFile.length() > 0L) {
                return@withContext finalFile
            }

            val db = FirebaseFirestore.getInstance(
                appCtx.getString(R.string.firestore_database_id)
            )

            val chunkBytesList = coroutineScope {
                (0 until chunkCount).map { idx ->
                    async {
                        val snap = db.collection(COLLECTION_CHUNKS)
                            .document("${assetId}_$idx")
                            .get()
                            .await()
                        if (!snap.exists()) {
                            throw IllegalStateException("Missing audio chunk $idx for asset $assetId")
                        }
                        val b64 = snap.getString("dataBase64")
                            ?.takeIf { it.isNotBlank() }
                            ?: throw IllegalStateException("Empty dataBase64 in chunk $idx")
                        Base64.decode(b64, Base64.DEFAULT)
                    }
                }.awaitAll()
            }

            val combined = ByteArrayOutputStream()
            chunkBytesList.forEach { combined.write(it) }
            val bytes = combined.toByteArray()
            if (bytes.isEmpty()) return@withContext null

            tmpFile.outputStream().use { out ->
                out.write(bytes)
                out.flush()
            }
            if (finalFile.exists()) {
                finalFile.delete()
            }
            if (!tmpFile.renameTo(finalFile)) {
                finalFile.writeBytes(bytes)
                tmpFile.delete()
            }
            finalFile
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download uploaded audio asset=$assetId: ${e.message}", e)
            null
        }
    }

    private fun sanitizeAssetId(assetId: String?): String? {
        val trimmed = assetId?.trim().orEmpty()
        return if (VALID_ASSET_ID.matches(trimmed)) trimmed else null
    }

    private fun sanitizeExt(fileExt: String?): String {
        val cleaned = fileExt?.trim()?.lowercase()?.removePrefix(".").orEmpty()
        return if (VALID_EXT.matches(cleaned)) cleaned else "mp3"
    }

    private fun isConfined(baseDir: File, targetFile: File): Boolean {
        return runCatching {
            val base = baseDir.canonicalPath
            val target = targetFile.canonicalPath
            target.startsWith(base + File.separator)
        }.getOrDefault(false)
    }
}
