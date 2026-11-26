package com.example

import android.app.Application
import com.example.fcm.FirebasePushConfig
import com.example.fcm.MuezzinFirebaseMessagingService
import com.example.util.OneSignalManager
import com.example.util.PrayerNotificationReceiver

class MuezzinApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PrayerNotificationReceiver.ensureAllChannels(this)
        OneSignalManager.initIfConfigured(this)
        MuezzinFirebaseMessagingService.ensureChannels(this)
        if (FirebasePushConfig.initialize(this)) {
            FirebasePushConfig.requestTokenAndRegister(this)
        }
    }
}
