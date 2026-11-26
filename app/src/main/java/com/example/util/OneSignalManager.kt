package com.example.util

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Push plumbing for the Al-Muezzin app.
 *
 * Historically this wrapped the OneSignal SDK. We now use Firebase Cloud
 * Messaging directly (see [com.example.push.MuezzinFirebaseMessagingService]),
 * so the OneSignal SDK calls were removed. The portal‑reminder parsing and the
 * incoming‑push handling below are pure app logic and are kept so reminder
 * scheduling, audio playback and de‑duplication continue to work for FCM pushes.
 */
data class OneSignalStatus(
    val isConfigured: Boolean = false,
    val appId: String? = null
)

object OneSignalManager {
    private const val TAG = "PushManager"
    private const val PREFS_NAME = "muezzin_onesignal_prefs"
    private const val KEY_APP_ID = "synced_onesignal_app_id"
    private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    private val processedPushKeys = ArrayDeque<String>(64)

    private val _status = MutableStateFlow(OneSignalStatus())
    val status: StateFlow<OneSignalStatus> = _status.asStateFlow()

    fun isValidAppId(candidate: String?): Boolean {
        val cleaned = candidate?.trim().orEmpty()
        return UUID_REGEX.matches(cleaned)
    }

    fun getEffectiveAppId(context: Context): String? {
        val buildId = BuildConfig.ONESIGNAL_APP_ID.trim()
        val syncedId = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_APP_ID, null)
            ?.trim()
        return when {
            isValidAppId(syncedId) -> syncedId
            isValidAppId(buildId) -> buildId
            else -> null
        }
    }

    @Synchronized
    fun saveSyncedAppId(context: Context, appId: String?): Boolean {
        val cleaned = appId?.trim().orEmpty()
        if (!isValidAppId(cleaned)) return false
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_ID, cleaned)
            .apply()
        return initIfConfigured(context)
    }

    /** FCM handles delivery now, so there is no SDK to initialise. */
    @Synchronized
    fun initIfConfigured(context: Context): Boolean {
        PrayerNotificationReceiver.ensureAllChannels(context.applicationContext)
        return false
    }

    suspend fun ensureSubscriptionReady(context: Context, allowSystemPrompt: Boolean = true): Boolean = false

    @Synchronized
    private fun markHandledOnce(key: String): Boolean {
        if (key in processedPushKeys) return false
        if (processedPushKeys.size >= 60) {
            processedPushKeys.removeFirst()
        }
        processedPushKeys.addLast(key)
        return true
    }

    fun handleIncomingPush(
        context: Context,
        notificationId: String?,
        title: String?,
        body: String?,
        additionalData: JSONObject?,
        triggerImmediateAudio: Boolean
    ): PortalReminder? {
        val appContext = context.applicationContext
        PrayerNotificationReceiver.ensureAllChannels(appContext)
        val dedupeKey = notificationId?.takeIf { it.isNotBlank() }
            ?: "${title.orEmpty()}|${body.orEmpty()}|${additionalData?.toString().orEmpty()}"

        val isFirstProcess = markHandledOnce(dedupeKey)
        val reminder = parsePortalReminder(title, body, additionalData) ?: return null

        if (isFirstProcess) {
            val all = PortalReminderStore.upsertAll(appContext, listOf(reminder))
            runCatching {
                PrayerNotificationScheduler(appContext).schedulePortalReminders(all)
            }
            if (triggerImmediateAudio &&
                reminder.isActive &&
                !reminder.isExpired() &&
                reminder.deliveryMode == "now" &&
                reminder.hasPlayableAudio()
            ) {
                runCatching {
                    MorningEveningAthkarService.startRemote(
                        context = appContext,
                        title = reminder.title,
                        body = reminder.body.ifBlank { reminder.title },
                        audioUrl = reminder.bestRemoteAudioUrl().orEmpty(),
                        remoteId = reminder.id,
                        localAudioPath = reminder.localAudioPath
                    )
                }.onFailure { e ->
                    Log.w(TAG, "Background remote audio start deferred: ${e.message}")
                }
            }
        } else {
            if (PortalReminderStore.getById(appContext, reminder.id) == null) {
                val all = PortalReminderStore.upsertAll(appContext, listOf(reminder))
                runCatching {
                    PrayerNotificationScheduler(appContext).schedulePortalReminders(all)
                }
            }
        }
        return reminder
    }

    private fun parsePortalReminder(
        notifTitle: String?,
        notifBody: String?,
        additionalData: JSONObject?
    ): PortalReminder? {
        if (additionalData != null) {
            val nested = additionalData.optJSONObject("reminder")
            if (nested != null) {
                if (nested.optString("title").isBlank() && !notifTitle.isNullOrBlank()) {
                    nested.put("title", notifTitle.trim())
                }
                if (nested.optString("body").isBlank() && !notifBody.isNullOrBlank()) {
                    nested.put("body", notifBody.trim())
                }
                PortalReminder.fromJson(nested)?.let { return it }
            }

            if (additionalData.has("title") ||
                additionalData.has("audioUrl") ||
                additionalData.has("normalizedAudioUrl") ||
                additionalData.has("reminderKind")
            ) {
                val merged = JSONObject(additionalData.toString())
                if (merged.optString("title").isBlank() && !notifTitle.isNullOrBlank()) {
                    merged.put("title", notifTitle.trim())
                }
                if (merged.optString("body").isBlank() && !notifBody.isNullOrBlank()) {
                    merged.put("body", notifBody.trim())
                }
                if (merged.optString("id").isBlank()) {
                    val altId = additionalData.optString("reminderId", "").trim()
                    if (altId.isNotBlank()) merged.put("id", altId)
                }
                PortalReminder.fromJson(merged)?.let { return it }
            }
        }

        val cleanTitle = notifTitle?.trim().orEmpty()
        val cleanBody = notifBody?.trim().orEmpty()
        val fallbackTitle = cleanTitle.ifBlank { cleanBody }
        if (fallbackTitle.isBlank()) return null

        return PortalReminder(
            id = "os_${System.currentTimeMillis()}_${fallbackTitle.hashCode()}",
            title = fallbackTitle.take(160),
            body = if (cleanTitle.isNotBlank()) cleanBody else "",
            reminderKind = "text",
            persistenceMode = "pinned",
            deliveryMode = "now",
            isActive = true
        )
    }
}
