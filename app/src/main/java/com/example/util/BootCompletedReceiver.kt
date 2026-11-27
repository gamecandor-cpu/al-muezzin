package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action in HANDLED_ACTIONS) {
            PrayerNotificationReceiver.ensureAllChannels(context)
            PrayerNotificationScheduler(context).rescheduleFromCache()
            // Restart the live notification connection so pushes keep arriving
            // after a reboot.
            com.example.push.PushListenerService.start(context)
        }
    }

    companion object {
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.intent.action.QUICKBOOT_POWERON"
        )
    }
}

