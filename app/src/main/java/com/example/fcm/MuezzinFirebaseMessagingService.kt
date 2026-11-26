package com.example.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.util.MorningEveningAthkarService
import com.example.util.PrayerNotificationReceiver
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives admin dashboard pushes over Firebase Cloud Messaging.
 *
 * Supported payload (data message):
 *   type      = "text" | "audio"
 *   title     = notification title
 *   body      = notification body
 *   audioUrl  = https url of the audio to stream (only for type = "audio")
 *   reminderId= optional id used to de-duplicate replays
 */
class MuezzinFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "FCM token refreshed")
        DeviceRegistrar.register(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val type = (data["type"] ?: "text").lowercase()
        val title = data["title"]?.takeIf { it.isNotBlank() }
            ?: message.notification?.title
            ?: "تنبيه من لوحة التحكم"
        val body = data["body"]?.takeIf { it.isNotBlank() }
            ?: message.notification?.body
            ?: ""
        val reminderId = data["reminderId"].orEmpty()

        if (type == "audio") {
            val audioUrl = data["audioUrl"].orEmpty()
            if (audioUrl.isNotBlank()) {
                playRemoteAudio(audioUrl, title, body, reminderId)
            } else {
                Log.w(TAG, "Audio push received without audioUrl")
            }
        }

        showPushNotification(title, body, reminderId, isAudio = type == "audio")
    }

    private fun playRemoteAudio(audioUrl: String, title: String, body: String, reminderId: String) {
        try {
            val started = MorningEveningAthkarService.startRemote(
                context = applicationContext,
                title = title,
                body = body.ifBlank { title },
                audioUrl = audioUrl,
                remoteId = reminderId.ifBlank { "push_${System.currentTimeMillis()}" },
                localAudioPath = null
            )
            Log.i(TAG, "Remote audio playback requested (started=$started)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start remote audio", e)
        }
    }

    private fun showPushNotification(
        title: String,
        body: String,
        reminderId: String,
        isAudio: Boolean
    ) {
        ensureChannels(applicationContext)
        if (!PrayerNotificationReceiver.hasNotificationPermission(applicationContext)) {
            Log.w(TAG, "Notification permission missing — push not shown")
            return
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            (reminderId.ifBlank { title }).hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            this,
            if (isAudio) CHANNEL_PUSH_AUDIO else CHANNEL_PUSH_TEXT
        )
            .setSmallIcon(R.drawable.ic_stat_muezzin)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val notificationId = if (reminderId.isNotBlank()) {
            7200 + (reminderId.hashCode() and 0x7FFF)
        } else {
            7299
        }
        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    companion object {
        private const val TAG = "MuezzinFCM"
        private const val CHANNEL_PUSH_TEXT = "push_text_channel"
        private const val CHANNEL_PUSH_AUDIO = "push_audio_channel"

        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            val textChannel = NotificationChannel(
                CHANNEL_PUSH_TEXT,
                "إشعارات لوحة التحكم",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "التنبيهات النصية المرسلة من لوحة التحكم"
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            manager.createNotificationChannel(textChannel)

            // Silent channel: the audio is streamed by the foreground service itself.
            val audioChannel = NotificationChannel(
                CHANNEL_PUSH_AUDIO,
                "التنبيهات الصوتية من لوحة التحكم",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "الأذان والتذكيرات الصوتية المرسلة من لوحة التحكم"
                setSound(null, null)
                enableVibration(true)
            }
            manager.createNotificationChannel(audioChannel)
        }
    }
}
