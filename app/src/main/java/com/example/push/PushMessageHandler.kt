package com.example.push

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.MainActivity
import com.example.R
import com.example.util.MorningEveningAthkarService
import com.example.util.PrayerNotificationReceiver

/**
 * Turns an incoming Supabase push into something the user sees and hears.
 *
 *   type = "text"  -> a notification with the message text
 *   type = "audio" -> the same notification plus remote audio playback
 */
object PushMessageHandler {

    private const val TAG = "PushHandler"

    fun onMessage(context: Context, message: PushMessage) {
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