package com.example.util

import androidx.annotation.Keep
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationServiceExtension

@Keep
class OneSignalNotificationExtension : INotificationServiceExtension {
    override fun onNotificationReceived(event: INotificationReceivedEvent) {
        PrayerNotificationReceiver.ensureAllChannels(event.context)
        OneSignalManager.initIfConfigured(event.context)
        val notification = event.notification
        OneSignalManager.handleIncomingPush(
            context = event.context,
            notificationId = notification.notificationId,
            title = notification.title,
            body = notification.body,
            additionalData = notification.additionalData,
            triggerImmediateAudio = true
        )
    }
}
