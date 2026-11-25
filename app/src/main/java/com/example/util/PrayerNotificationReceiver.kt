package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.MuezzinVoice
import com.example.data.model.PrayerType
import com.example.data.model.ReligiousOccasion

class PrayerNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_OCCASION_DAILY_CHECK -> {
                val scheduler = PrayerNotificationScheduler(context)
                val todayOccasion = scheduler.resolveCurrentActiveOccasion()
                if (todayOccasion != null && scheduler.markOccasionNotifiedIfNeeded(todayOccasion)) {
                    showOccasionNotification(context, todayOccasion)
                }
                scheduler.scheduleDailyOccasionAlarm()
            }
            ACTION_ATHKAR_MORNING, ACTION_ATHKAR_EVENING -> {
                val scheduler = PrayerNotificationScheduler(context)
                if (scheduler.isAthkarAutoPlayEnabled()) {
                    val athkarType = if (intent.action == ACTION_ATHKAR_MORNING) {
                        AthkarAudioType.MORNING
                    } else {
                        AthkarAudioType.EVENING
                    }
                    val started = MorningEveningAthkarService.start(context, athkarType)
                    if (!started) {
                        showCloudPushNotification(
                            context = context,
                            title = athkarType.titleAr,
                            body = athkarType.subtitleAr
                        )
                    }
                }
                scheduler.rescheduleFromCache()
            }
            ACTION_REMOTE_REMINDER_ALARM -> {
                val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID).orEmpty()
                val title = intent.getStringExtra(EXTRA_REMINDER_TITLE).orEmpty().ifBlank { "تذكير من المؤذن" }
                val body = intent.getStringExtra(EXTRA_REMINDER_BODY).orEmpty().ifBlank { title }
                val kind = intent.getStringExtra(EXTRA_REMINDER_KIND).orEmpty()
                val audioUrl = intent.getStringExtra(EXTRA_REMINDER_AUDIO_URL).orEmpty()
                val expiresAt = intent.getLongExtra(EXTRA_REMINDER_EXPIRES_AT, 0L)

                if (expiresAt <= 0L || System.currentTimeMillis() <= expiresAt) {
                    if (kind == "audio" && audioUrl.isNotBlank()) {
                        MorningEveningAthkarService.startRemote(
                            context = context,
                            title = title,
                            body = body,
                            audioUrl = audioUrl,
                            remoteId = reminderId.ifBlank { null }
                        )
                    }
                    showCloudPushNotification(context, title, body)
                }
                PrayerNotificationScheduler(context).rescheduleRemoteRemindersFromCache()
            }
            else -> {
                val prayerKey = intent.getStringExtra(EXTRA_PRAYER_KEY) ?: PrayerType.FAJR.apiKey
                val prayerNameAr = intent.getStringExtra(EXTRA_PRAYER_NAME_AR) ?: "الصلاة"
                val cityName = intent.getStringExtra(EXTRA_CITY_NAME).orEmpty()
                val formattedTime = intent.getStringExtra(EXTRA_TIME_LABEL).orEmpty()
                val voiceId = intent.getStringExtra(EXTRA_VOICE_ID) ?: MuezzinVoice.NASSER_AL_QUTAMI.id
                val athanEnabled = intent.getBooleanExtra(EXTRA_ATHAN_ENABLED, true)

                if (athanEnabled) {
                    val voice = MuezzinVoice.fromId(voiceId)
                    showPrayerNotification(
                        context = context,
                        prayerKey = prayerKey,
                        prayerNameAr = prayerNameAr,
                        cityName = cityName,
                        formattedTime = formattedTime,
                        voice = voice,
                        withChannelSound = true
                    )
                }

                // Re-schedule tomorrow's prayers so alarms stay continuous in background
                PrayerNotificationScheduler(context).rescheduleFromCache()
            }
        }
    }

    companion object {
        const val ACTION_PRAYER_ALERT = "com.example.ACTION_PRAYER_ALERT"
        const val ACTION_OCCASION_DAILY_CHECK = "com.example.ACTION_OCCASION_DAILY_CHECK"
        const val ACTION_ATHKAR_MORNING = "com.example.ACTION_ATHKAR_MORNING"
        const val ACTION_ATHKAR_EVENING = "com.example.ACTION_ATHKAR_EVENING"
        const val ACTION_REMOTE_REMINDER_ALARM = "com.example.ACTION_REMOTE_REMINDER_ALARM"
        const val ACTION_OPEN_REMOTE_REMINDER = "com.example.ACTION_OPEN_REMOTE_REMINDER"

        const val EXTRA_PRAYER_KEY = "extra_prayer_key"
        const val EXTRA_PRAYER_NAME_AR = "extra_prayer_name_ar"
        const val EXTRA_CITY_NAME = "extra_city_name"
        const val EXTRA_TIME_LABEL = "extra_time_label"
        const val EXTRA_VOICE_ID = "extra_voice_id"
        const val EXTRA_ATHAN_ENABLED = "extra_athan_enabled"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
        const val EXTRA_REMINDER_BODY = "extra_reminder_body"
        const val EXTRA_REMINDER_KIND = "extra_reminder_kind"
        const val EXTRA_REMINDER_AUDIO_URL = "extra_reminder_audio_url"
        const val EXTRA_REMINDER_EXPIRES_AT = "extra_reminder_expires_at"

        private const val CHANNEL_PRAYER_VISUAL = "prayer_visual_alerts_channel"
        private const val CHANNEL_OCCASIONS = "religious_occasions_channel"
        private const val CHANNEL_CLOUD_PUSH = "cloud_push_messages_channel"

        fun athanChannelId(voice: MuezzinVoice): String = "athan_alert_channel_${voice.id}"

        fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }

        fun ensureAllChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            // Visual heads-up prayer channel (used when AthanPlayer handles full playback in-app)
            val visualChannel = NotificationChannel(
                CHANNEL_PRAYER_VISUAL,
                "إشعارات مواقيت الصلاة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات دخول وقت الصلاة عند استخدام التطبيق"
                setSound(null, null)
                enableVibration(true)
            }
            manager.createNotificationChannel(visualChannel)

            // Per-voice Athan channels for background prayer notifications with real Athan sound
            val athanAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            MuezzinVoice.entries.forEach { voice ->
                val soundUri = Uri.parse(
                    "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${voice.rawResId}"
                )
                val channel = NotificationChannel(
                    athanChannelId(voice),
                    "أذان ${voice.arabicLabel}",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "تنبيه الأذان بصوت ${voice.arabicLabel}"
                    setSound(soundUri, athanAttrs)
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }

            // Religious occasions & Eid greetings channel
            val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val occasionAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val occasionChannel = NotificationChannel(
                CHANNEL_OCCASIONS,
                "المناسبات الدينية والأعياد",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات وتهاني المناسبات الدينية والأعياد"
                setSound(defaultSound, occasionAttrs)
                enableVibration(true)
            }
            manager.createNotificationChannel(occasionChannel)

            // Firebase Cloud Messaging channel
            val fcmChannel = NotificationChannel(
                CHANNEL_CLOUD_PUSH,
                "الإشعارات السحابية وتنبيهات الأذكار",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "رسائل وإشعارات Firebase الفورية وتنبيهات الأذكار"
                setSound(defaultSound, occasionAttrs)
                enableVibration(true)
            }
            manager.createNotificationChannel(fcmChannel)
        }

        fun showPrayerNotification(
            context: Context,
            prayerKey: String,
            prayerNameAr: String,
            cityName: String,
            formattedTime: String,
            voice: MuezzinVoice,
            withChannelSound: Boolean
        ) {
            ensureAllChannels(context)
            if (!hasNotificationPermission(context)) return

            val channelId = if (withChannelSound) {
                athanChannelId(voice)
            } else {
                CHANNEL_PRAYER_VISUAL
            }

            val title = if (prayerKey == PrayerType.FAJR.apiKey) {
                "حان الآن وقت صلاة الفجر"
            } else {
                "حان الآن وقت صلاة $prayerNameAr"
            }
            val details = buildString {
                if (cityName.isNotBlank()) append("المدينة: $cityName")
                if (formattedTime.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append("الموعد: $formattedTime")
                }
                if (isEmpty()) append("حيّ على الصلاة — بصوت ${voice.arabicLabel}")
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                prayerKey.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_muezzin)
                .setContentTitle(title)
                .setContentText(details)
                .setStyle(NotificationCompat.BigTextStyle().bigText(details))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()

            val notifId = 4100 + (prayerKey.hashCode() and 0xFF)
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }

        fun showOccasionNotification(
            context: Context,
            occasion: ReligiousOccasion
        ) {
            ensureAllChannels(context)
            if (!hasNotificationPermission(context)) return

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                occasion.id.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (occasion.isEid) occasion.activeBannerTitle else occasion.greetingTitleAr
            val body = occasion.greetingMessageAr

            val notification = NotificationCompat.Builder(context, CHANNEL_OCCASIONS)
                .setSmallIcon(R.drawable.ic_stat_muezzin)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()

            val notifId = 5100 + (occasion.id.hashCode() and 0xFF)
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }

        fun showCloudPushNotification(
            context: Context,
            title: String,
            body: String,
            reminderId: String? = null,
            audioUrl: String? = null
        ) {
            ensureAllChannels(context)
            if (!hasNotificationPermission(context)) return

            val uniqueKey = reminderId?.takeIf { it.isNotBlank() } ?: title
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                action = if (!reminderId.isNullOrBlank() || !audioUrl.isNullOrBlank()) {
                    ACTION_OPEN_REMOTE_REMINDER
                } else {
                    Intent.ACTION_MAIN
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (!reminderId.isNullOrBlank()) putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_REMINDER_TITLE, title)
                putExtra(EXTRA_REMINDER_BODY, body)
                if (!audioUrl.isNullOrBlank()) putExtra(EXTRA_REMINDER_AUDIO_URL, audioUrl)
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                6100 + (uniqueKey.hashCode() and 0x7FFF),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_CLOUD_PUSH)
                .setSmallIcon(R.drawable.ic_stat_muezzin)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()

            val notifId = 6100 + (uniqueKey.hashCode() and 0x7FFF)
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }

        fun showTestNotification(
            context: Context,
            voice: MuezzinVoice
        ) {
            ensureAllChannels(context)
            if (!hasNotificationPermission(context)) return

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                999,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_CLOUD_PUSH)
                .setSmallIcon(R.drawable.ic_stat_muezzin)
                .setContentTitle("تطبيق المؤذن — تنبيه تجريبي")
                .setContentText("الإشعارات الحقيقية وإشعارات السحابة مفعّلة بنجاح! المؤذن: ${voice.arabicLabel}")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "الإشعارات الحقيقية مفعّلة بنجاح! ستصلك تنبيهات الصلوات الخمس وأذكار الصباح والمساء والمناسبات الدينية تلقائياً."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()

            NotificationManagerCompat.from(context).notify(4999, notification)
        }
    }
}
