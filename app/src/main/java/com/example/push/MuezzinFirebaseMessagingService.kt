package com.example.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives Firebase Cloud Messaging pushes and turns them into the same
 * [PushMessage] the Supabase transport uses, so a dashboard send is handled
 * identically whether it arrived over Realtime (app alive) or FCM (app closed).
 *
 * FCM is what lets a notification reach the device after the user has swiped
 * the app away — something a plain WebSocket cannot do.
 */
class MuezzinFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.i(TAG, "FCM token refreshed")
        SupabasePushClient.registerFcmToken(this, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        Log.i(TAG, "FCM message received: ${data["title"] ?: remoteMessage.notification?.title}")

        val message = PushMessage(
            id = data["id"]?.toLongOrNull() ?: System.currentTimeMillis(),
            type = data["type"]?.ifBlank { null } ?: "text",
            title = data["title"]?.ifBlank { null }
                ?: remoteMessage.notification?.title
                ?: "تنبيه",
            body = data["body"]?.ifBlank { null }
                ?: remoteMessage.notification?.body
                ?: "",
            audioUrl = data["audio_url"]?.ifBlank { null }
        )
        PushMessageHandler.onMessage(applicationContext, message)
    }

    private companion object {
        const val TAG = "MuezzinFcm"
    }
}
