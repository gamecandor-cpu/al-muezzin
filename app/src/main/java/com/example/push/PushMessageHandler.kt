package com.example.push

import android.content.Context
import android.util.Log
import com.example.util.MorningEveningAthkarService
import com.example.util.PrayerNotificationReceiver

/**
 * Turns an incoming push into something the user sees and hears.
 *
 *   type = "text"  -> a notification with the message text
 *   type = "audio" -> the same notification plus remote audio playback
 *
 * Every notification is shown exactly once. The first time an id is seen we
 * record it in a small persistent store; any later attempt to show the same id
 * (from Realtime, FCM, or the offline catch-up) is dropped. That way a dismissed
 * text notification or a stopped audio notification never re-appears — neither
 * in the notification shade nor inside the app.
 */
object PushMessageHandler {

    private const val TAG = "PushHandler"
    private const val SEEN_PREFS = "muezzin_push_seen_ids"
    private const val SEEN_CAP = 300

    fun onMessage(context: Context, message: PushMessage) {
        if (!markSeenOnce(context, message.id)) {
            Log.i(TAG, "notification ${message.id} already shown — dropping re-show")
            return
        }
        Log.i(TAG, "push received: ${message.title}")

        if (message.type == "audio" && !message.audioUrl.isNullOrBlank()) {
            try {
                MorningEveningAthkarService.startRemote(
                    context = context,
                    title = message.title,
                    body = message.body.ifBlank { message.title },
                    audioUrl = message.audioUrl,
                    remoteId = "push_${message.id}",
                    localAudioPath = null,
                    allowLocalFallback = false  // play exactly what the dashboard sent
                )
            } catch (e: Exception) {
                Log.w(TAG, "audio playback failed: ${e.message}")
            }
        }

        showNotification(context, message)
    }

    /** True only the first time a given id is seen; false on every later attempt. */
    private fun markSeenOnce(context: Context, id: Long): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences(SEEN_PREFS, Context.MODE_PRIVATE)
        val key = "n_$id"
        if (prefs.getBoolean(key, false)) return false
        val edit = prefs.edit().putBoolean(key, true)
        // Keep the store bounded so it cannot grow forever.
        val existing = prefs.all.keys
        if (existing.size > SEEN_CAP) {
            existing.filter { it != key }
                .take(existing.size - SEEN_CAP)
                .forEach { edit.remove(it) }
        }
        edit.apply()
        return true
    }

    private fun showNotification(context: Context, message: PushMessage) {
        PrayerNotificationReceiver.ensureAllChannels(context)
        PrayerNotificationReceiver.showAthkarNotification(
            context = context,
            title = message.title,
            body = message.body.ifBlank {
                if (message.type == "audio") "تنبيه صوتي من لوحة التحكم" else "تنبيه من لوحة التحكم"
            },
            reminderId = "push_${message.id}"
        )
    }
}
