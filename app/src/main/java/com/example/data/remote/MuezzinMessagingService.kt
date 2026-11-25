package com.example.data.remote

import android.util.Log
import com.example.util.AthkarAudioType
import com.example.util.MorningEveningAthkarService
import com.example.util.PrayerNotificationReceiver
import com.example.util.PrayerNotificationScheduler
import com.google.firebase.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class MuezzinMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token received")
        CloudSyncRepository.saveLocalFcmToken(applicationContext, token)
        serviceScope.launch {
            val messaging = Firebase.messaging
            CloudSyncRepository.DEFAULT_FCM_TOPICS.forEach { topic ->
                runCatching { messaging.subscribeToTopic(topic).await() }
            }
            CloudSyncRepository.syncFcmTokenIfSignedIn(applicationContext, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        PrayerNotificationReceiver.ensureAllChannels(applicationContext)

        val data = remoteMessage.data
        val reminderId = data["reminderId"]?.takeIf { it.isNotBlank() }
        val triggerNonce = data["triggerNonce"]?.toLongOrNull() ?: 1L
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
                val scheduler = PrayerNotificationScheduler(applicationContext)
                // Pull latest Firestore broadcast_reminders in background so scheduled/daily alarms stay synced even when app is closed
                serviceScope.launch {
                    withTimeoutOrNull(8_000L) {
                        val repo = CloudSyncRepository(applicationContext)
                        val result = repo.fetchActiveBroadcastRemindersOnce()
                        val list = result.getOrNull()
                        if (list != null) {
                            scheduler.syncRemoteReminders(list)
                        }
                    }
                }

                // Deduplicate against Firestore snapshot listener if same reminderId + triggerNonce
                val shouldDeliverImmediate = if (reminderId != null) {
                    scheduler.markRemoteTriggerHandledIfNew(reminderId, triggerNonce)
                } else {
                    true
                }

                if (shouldDeliverImmediate) {
                    if (playAudio && !audioUrl.isNullOrBlank()) {
                        MorningEveningAthkarService.startRemote(
                            context = applicationContext,
                            title = title,
                            body = body,
                            audioUrl = audioUrl,
                            remoteId = reminderId
                        )
                    }
                    PrayerNotificationReceiver.showCloudPushNotification(
                        context = applicationContext,
                        title = title,
                        body = body,
                        reminderId = reminderId,
                        audioUrl = audioUrl
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "MuezzinFcmService"
    }
}
