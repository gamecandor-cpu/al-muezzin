package com.example.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

/**
 * A foreground service that keeps the notification transport alive so dashboard
 * pushes reach the device even after the user has left (swiped) the app.
 *
 * FCM already covers the fully-closed case, but OEM battery managers (Xiaomi,
 * Huawei, Oppo…) throttle or kill background work aggressively. Holding a
 * foreground service with a persistent notification is the reliable, battery-safe
 * way to keep a live connection and guarantee instant delivery.
 */
class PushListenerService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        // Partial wake lock keeps the network responsive while the screen is off.
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "almuezzin:push").apply {
            setReferenceCounted(false)
            acquire()
        }
        // Keep the Supabase Realtime connection running for live delivery.
        SupabasePushClient.startListening(this) { message ->
            PushMessageHandler.onMessage(applicationContext, message)
        }
        SupabasePushClient.refreshFcmToken(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY // restart if the system ever kills the service
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "اتصال الإشعارات",
            NotificationManager.IMPORTANCE_LOW // silent, persistent status
        ).apply {
            description = "إبقاء اتصال الإشعارات الفورية نشطاً"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_muezzin)
            .setContentTitle("المؤذن يعمل")
            .setContentText("الإشعارات الفورية مفعّلة — ستصلك حتى والتطبيق مقفول")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(launch)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "push_listener_channel"
        private const val NOTIFICATION_ID = 5600

        fun start(context: Context) {
            val intent = Intent(context, PushListenerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
