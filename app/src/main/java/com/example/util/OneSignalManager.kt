package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.onesignal.OneSignal
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationWillDisplayEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class OneSignalStatus(
    val isConfigured: Boolean = false,
    val appId: String? = null
)

object OneSignalManager {
    private const val TAG = "OneSignalManager"
    private const val PREFS_NAME = "muezzin_onesignal_prefs"
    private const val KEY_APP_ID = "synced_onesignal_app_id"
    private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    private var initializedAppId: String? = null
    private var listenersRegistered: Boolean = false
    private var permissionRequestedThisSession: Boolean = false
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

    @Synchronized
    fun initIfConfigured(context: Context): Boolean {
        val appContext = context.applicationContext
        PrayerNotificationReceiver.ensureAllChannels(appContext)
        val appId = getEffectiveAppId(appContext)
        if (appId.isNullOrBlank()) {
            _status.value = OneSignalStatus(isConfigured = false, appId = null)
            return false
        }

        if (initializedAppId == appId) {
            runCatching { OneSignal.User.pushSubscription.optIn() }
            _status.value = OneSignalStatus(isConfigured = true, appId = appId)
            return true
        }

        return runCatching {
            OneSignal.initWithContext(appContext, appId)
            initializedAppId = appId
            _status.value = OneSignalStatus(isConfigured = true, appId = appId)

            if (!listenersRegistered) {
                registerNotificationListeners(appContext)
                listenersRegistered = true
            }
            runCatching { OneSignal.User.pushSubscription.optIn() }
            Log.i(TAG, "OneSignal initialized with App ID: ${appId.take(8)}...")
            true
        }.getOrElse { e ->
            Log.e(TAG, "Failed to initialize OneSignal: ${e.message}")
            false
        }
    }

    suspend fun ensureSubscriptionReady(context: Context, allowSystemPrompt: Boolean = true): Boolean {
        val appContext = context.applicationContext
        if (!initIfConfigured(appContext)) return false

        val hasOsPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (!hasOsPermission && allowSystemPrompt && !permissionRequestedThisSession) {
            permissionRequestedThisSession = true
            runCatching {
                OneSignal.Notifications.requestPermission(false)
            }
        }

        return runCatching {
            OneSignal.User.pushSubscription.optIn()
            true
        }.getOrDefault(false)
    }

    private fun registerNotificationListeners(appContext: Context) {
        OneSignal.Notifications.addForegroundLifecycleListener(object : INotificationLifecycleListener {
            override fun onWillDisplay(event: INotificationWillDisplayEvent) {
                val n = event.notification
                handleIncomingPush(
                    context = appContext,
                    notificationId = n.notificationId,
                    title = n.title,
                    body = n.body,
                    additionalData = n.additionalData,
                    triggerImmediateAudio = true
                )
            }
        })

        OneSignal.Notifications.addClickListener(object : INotificationClickListener {
            override fun onClick(event: INotificationClickEvent) {
                val n = event.notification
                val reminder = handleIncomingPush(
                    context = appContext,
                    notificationId = n.notificationId,
                    title = n.title,
                    body = n.body,
                    additionalData = n.additionalData,
                    triggerImmediateAudio = false
                )
                if (reminder != null && reminder.isActive && !reminder.isExpired() && reminder.hasPlayableAudio()) {
                    runCatching {
                        MorningEveningAthkarService.startRemote(
                            context = appContext,
                            title = reminder.title,
                            body = reminder.body.ifBlank { reminder.title },
                            audioUrl = reminder.bestRemoteAudioUrl().orEmpty(),
                            remoteId = reminder.id,
                            localAudioPath = reminder.localAudioPath
                        )
                    }
                }
            }
        })
    }

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
