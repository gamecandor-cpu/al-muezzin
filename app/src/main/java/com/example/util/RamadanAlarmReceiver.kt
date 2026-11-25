package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class RamadanAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_ALARM_TYPE) ?: TYPE_IMSAK
        val title = intent.getStringExtra(EXTRA_TITLE) ?: if (type == TYPE_IFTAR) {
            "حان وقت الإفطار"
        } else {
            "تنبيه السحور والإمساك"
        }
        val body = intent.getStringExtra(EXTRA_BODY) ?: if (type == TYPE_IFTAR) {
            "اللهم لك صمت وعلى رزقك أفطرت — تقبل الله صيامكم"
        } else {
            "اقترب وقت الإمساك والفجر — تسحروا فإن في السحور بركة"
        }

        showNotification(context, type, title, body)
    }

    companion object {
        const val CHANNEL_ID = "ramadan_alarms_channel"
        const val EXTRA_ALARM_TYPE = "alarm_type"
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_BODY = "alarm_body"
        const val TYPE_IMSAK = "imsak"
        const val TYPE_IFTAR = "iftar"

        fun showNotification(context: Context, type: String, title: String, body: String) {
            ensureChannel(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                type.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_muezzin)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)
                .build()

            val notificationId = if (type == TYPE_IFTAR) 2002 else 2001
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "منبه الإمساك والإفطار",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "تنبيهات وقت السحور والإمساك والإفطار"
                    setSound(soundUri, audioAttrs)
                    enableVibration(true)
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }
        }
    }
}
