package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity

class RamadanAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun scheduleImsakAlarm(triggerAtMillis: Long, leadMinutes: Int) {
        val body = if (leadMinutes == 0) {
            "حان الآن وقت الإمساك — نسأل الله لك صياماً مقبولاً"
        } else {
            "متبقي $leadMinutes دقيقة على وقت الإمساك — بادر بالسحور وشرب الماء"
        }
        schedule(
            requestCode = REQUEST_IMSAK,
            type = RamadanAlarmReceiver.TYPE_IMSAK,
            title = "منبه السحور والإمساك",
            body = body,
            triggerAtMillis = triggerAtMillis
        )
    }

    fun scheduleIftarAlarm(triggerAtMillis: Long, leadMinutes: Int) {
        val body = if (leadMinutes == 0) {
            "حان الآن موعد الإفطار وأذان المغرب — ذهب الظمأ وابتلت العروق وثبت الأجر إن شاء الله"
        } else {
            "متبقي $leadMinutes دقيقة على أذان المغرب والإفطار — استثمر هذه الدقائق بالدعاء"
        }
        schedule(
            requestCode = REQUEST_IFTAR,
            type = RamadanAlarmReceiver.TYPE_IFTAR,
            title = "منبه الإفطار",
            body = body,
            triggerAtMillis = triggerAtMillis
        )
    }

    fun cancelImsakAlarm() {
        cancel(REQUEST_IMSAK, RamadanAlarmReceiver.TYPE_IMSAK)
    }

    fun cancelIftarAlarm() {
        cancel(REQUEST_IFTAR, RamadanAlarmReceiver.TYPE_IFTAR)
    }

    private fun schedule(
        requestCode: Int,
        type: String,
        title: String,
        body: String,
        triggerAtMillis: Long
    ) {
        val manager = alarmManager ?: return
        val intent = Intent(context, RamadanAlarmReceiver::class.java).apply {
            putExtra(RamadanAlarmReceiver.EXTRA_ALARM_TYPE, type)
            putExtra(RamadanAlarmReceiver.EXTRA_TITLE, title)
            putExtra(RamadanAlarmReceiver.EXTRA_BODY, body)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canExact) {
            runCatching {
                val showIntent = PendingIntent.getActivity(
                    context,
                    requestCode + 100,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                    pendingIntent
                )
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            manager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancel(requestCode: Int, type: String) {
        val manager = alarmManager ?: return
        val intent = Intent(context, RamadanAlarmReceiver::class.java).apply {
            putExtra(RamadanAlarmReceiver.EXTRA_ALARM_TYPE, type)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.cancel(pendingIntent)
    }

    companion object {
        private const val REQUEST_IMSAK = 3101
        private const val REQUEST_IFTAR = 3102
    }
}

