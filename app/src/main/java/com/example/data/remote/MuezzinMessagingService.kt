package com.example.data.remote

import android.util.Log
import com.example.util.AthkarAudioType
import com.example.util.MorningEveningAthkarService
import com.example.util.PrayerNotificationReceiver
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MuezzinMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token received")
        CloudSyncRepository.saveLocalFcmToken(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        val title = remoteMessage.notification?.title
            ?: data["title"]
            ?: "المؤذن | Muezzin"
        val body = remoteMessage.notification?.body
            ?: data["body"]
            ?: data["message"]
            ?: "تنبيه من تطبيق المؤذن"

        val audioUrl = data["audioUrl"] ?: data["normalizedAudioUrl"]
        val playAudio = data["playAudio"]?.equals("true", ignoreCase = true) ?: !audioUrl.isNullOrBlank()

        when (data["type"]) {
            "athkar_morning" -> {
                if (!MorningEveningAthkarService.start(applicationContext, AthkarAudioType.MORNING)) {
                    PrayerNotificationReceiver.showCloudPushNotification(applicationContext, title, body)
                }
            }
            "athkar_evening" -> {
                if (!MorningEveningAthkarService.start(applicationContext, AthkarAudioType.EVENING)) {
                    PrayerNotificationReceiver.showCloudPushNotification(applicationContext, title, body)
                }
            }
            else -> {
                if (playAudio && !audioUrl.isNullOrBlank()) {
                    MorningEveningAthkarService.startRemote(
                        context = applicationContext,
                        title = title,
                        body = body,
                        audioUrl = audioUrl,
                        remoteId = data["reminderId"]
                    )
                }
                PrayerNotificationReceiver.showCloudPushNotification(applicationContext, title, body)
            }
        }
    }

    companion object {
        private const val TAG = "MuezzinFcmService"
    }
}
