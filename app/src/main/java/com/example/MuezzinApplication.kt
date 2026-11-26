package com.example

import android.app.Application
import com.example.util.OneSignalManager
import com.example.util.PrayerNotificationReceiver

class MuezzinApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PrayerNotificationReceiver.ensureAllChannels(this)
        OneSignalManager.initIfConfigured(this)
    }
}
