package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.AthanPlayMode
import com.example.data.model.MuezzinVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AthkarAudioType(
    val id: String,
    val titleAr: String,
    val subtitleAr: String,
    val rawResId: Int
) {
    MORNING(
        id = "morning",
        titleAr = "أذكار الصباح",
        subtitleAr = "أذكار الصباح المباركة — من الفجر إلى طلوع الشمس",
        rawResId = R.raw.athkar_morning
    ),
    EVENING(
        id = "evening",
        titleAr = "أذكار المساء",
        subtitleAr = "أذكار المساء المباركة — قبيل الغروب وفي أول المساء",
        rawResId = R.raw.athkar_evening
    )
}

data class AthkarAudioPlaybackState(
    val activeType: AthkarAudioType? = null,
    val remoteTitle: String? = null,
    val remoteId: String? = null,
    val isPlaying: Boolean = false
) {
    val displayLabel: String
        get() = remoteTitle ?: activeType?.titleAr.orEmpty()
}

class MorningEveningAthkarService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopPlaybackAndSelf()
                START_NOT_STICKY
            }
            ACTION_START_ATHAN -> {
                val prayerNameAr = intent.getStringExtra(EXTRA_PRAYER_NAME_AR).orEmpty().ifBlank { "الصلاة" }
                val cityName = intent.getStringExtra(EXTRA_CITY_NAME).orEmpty()
                val voice = MuezzinVoice.fromId(intent.getStringExtra(EXTRA_VOICE_ID))
                val playMode = AthanPlayMode.fromId(intent.getStringExtra(EXTRA_ATHAN_PLAY_MODE_ID))
                startAthanPlayback(prayerNameAr, cityName, voice, playMode)
                START_NOT_STICKY
            }
            ACTION_START_REMOTE -> {
                val localPath = intent.getStringExtra(EXTRA_LOCAL_AUDIO_PATH).orEmpty()
                val url = intent.getStringExtra(EXTRA_REMOTE_URL).orEmpty()
                val title = intent.getStringExtra(EXTRA_REMOTE_TITLE).orEmpty().ifBlank { "تذكير صوتي" }
                val body = intent.getStringExtra(EXTRA_REMOTE_BODY).orEmpty().ifBlank { title }
                val remoteId = intent.getStringExtra(EXTRA_REMOTE_ID)
                val allowFallback = intent.getBooleanExtra(EXTRA_ALLOW_LOCAL_FALLBACK, true)
                when {
                    localPath.isNotBlank() && java.io.File(localPath).exists() -> {
                        startLocalFilePlayback(remoteId, title, body, localPath)
                    }
                    url.isNotBlank() -> {
                        startRemotePlayback(remoteId, title, body, url, allowFallback)
                    }
                    else -> {
                        stopPlaybackAndSelf()
                    }
                }
                START_NOT_STICKY
            }
            else -> {
                val typeId = intent?.getStringExtra(EXTRA_ATHKAR_TYPE)
                val type = AthkarAudioType.entries.find { it.id == typeId } ?: AthkarAudioType.MORNING
                startPlayback(type)
                START_NOT_STICKY
            }
        }
    }

    private fun startForegroundNotification(title: String, body: String) {
        ensureChannel()

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this,
            5501,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MorningEveningAthkarService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            5502,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_muezzin)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentPending)
            .addAction(0, "إيقاف", stopPending)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startAthanPlayback(
        prayerNameAr: String,
        cityName: String,
        voice: MuezzinVoice,
        playMode: AthanPlayMode
    ) {
        releasePlayer()
        val title = if (prayerNameAr == "الفجر") "أذان الفجر" else "أذان $prayerNameAr"
        val body = buildString {
            append("بصوت ${voice.arabicLabel}")
            if (cityName.isNotBlank()) append(" • $cityName")
        }
        startForegroundNotification(title, body)
        _playbackState.value = AthkarAudioPlaybackState(
            activeType = null,
            remoteTitle = title,
            remoteId = "athan_${voice.id}",
            isPlaying = true
        )

        runCatching {
            val player = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                resources.openRawResourceFd(voice.rawResId).use { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                setOnCompletionListener {
                    stopPlaybackAndSelf()
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Athan foreground MediaPlayer error what=$what extra=$extra")
                    stopPlaybackAndSelf()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player

            if (playMode == AthanPlayMode.PARTIAL) {
                serviceScope.launch {
                    val totalMs = runCatching { player.duration.takeIf { it > 0 } ?: 20_000 }.getOrDefault(20_000)
                    val partialTargetMs = minOf(20_000L, totalMs.toLong())
                    val fadeDurationMs = minOf(2_000L, partialTargetMs / 4L)
                    val steadyMs = (partialTargetMs - fadeDurationMs).coerceAtLeast(0L)
                    delay(steadyMs)
                    val steps = 15
                    val stepDelay = (fadeDurationMs / steps).coerceAtLeast(30L)
                    for (s in steps - 1 downTo 0) {
                        val current = mediaPlayer
                        if (current !== player) return@launch
                        val vol = s.toFloat() / steps.toFloat()
                        runCatching { player.setVolume(vol, vol) }
                        delay(stepDelay)
                    }
                    withContext(Dispatchers.Main) {
                        if (mediaPlayer === player) {
                            stopPlaybackAndSelf()
                        }
                    }
                }
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to start foreground Athan playback", e)
            stopPlaybackAndSelf()
        }
    }

    private fun startPlayback(type: AthkarAudioType) {
        releasePlayer()
        startForegroundNotification(type.titleAr, type.subtitleAr)

        runCatching {
            val player = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                resources.openRawResourceFd(type.rawResId).use { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                setOnCompletionListener {
                    stopPlaybackAndSelf()
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Athkar MediaPlayer error what=$what extra=$extra")
                    stopPlaybackAndSelf()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            _playbackState.value = AthkarAudioPlaybackState(activeType = type, isPlaying = true)
        }.onFailure { e ->
            Log.e(TAG, "Failed to start Athkar audio playback", e)
            stopPlaybackAndSelf()
        }
    }

    private fun startLocalFilePlayback(remoteId: String?, title: String, body: String, localPath: String) {
        releasePlayer()
        startForegroundNotification(title, body)
        _playbackState.value = AthkarAudioPlaybackState(
            activeType = null,
            remoteTitle = title,
            remoteId = remoteId,
            isPlaying = true
        )
        runCatching {
            val player = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(localPath)
                setOnPreparedListener { mp -> mp.start() }
                setOnCompletionListener { stopPlaybackAndSelf() }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Local portal audio MediaPlayer error what=$what extra=$extra")
                    stopPlaybackAndSelf()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        }.onFailure { e ->
            Log.e(TAG, "Failed to start local portal audio playback", e)
            stopPlaybackAndSelf()
        }
    }

    private fun startRemotePlayback(remoteId: String?, title: String, body: String, rawUrl: String, allowLocalFallback: Boolean = true) {
        releasePlayer()
        val resolved = AudioUrlResolver.resolve(rawUrl)
        if (!resolved.isValidHttps) {
            stopPlaybackAndSelf()
            return
        }
        startForegroundNotification(title, body)
        _playbackState.value = AthkarAudioPlaybackState(
            activeType = null,
            remoteTitle = title,
            remoteId = remoteId,
            isPlaying = true
        )

        // فحص فعلي للرابط: إذا كان صفحة HTML بلا صوت مستخرج نستخدم الاحتياطي، وإلا نجرب التشغيل المباشر بـ MediaPlayer مع User-Agent
        serviceScope.launch {
            // For a direct audio file (e.g. an uploaded MP3) play exactly what was
            // sent — no HTML pre-check, and never silently swap in the built-in athkar.
            if (resolved.likelyDirectAudio) {
                withContext(Dispatchers.Main) {
                    playRemoteMediaPlayer(resolved.directPlayUrl, allowLocalFallback)
                }
                return@launch
            }
            val inspection = RemoteAudioVerifier.inspect(resolved.directPlayUrl)
            val targetUrl = inspection.extractedAudioUrl
                ?.let { AudioUrlResolver.resolve(it).directPlayUrl }
                ?.takeIf { it.startsWith("https://", ignoreCase = true) }
                ?: inspection.finalUrl.takeIf { it.startsWith("https://", ignoreCase = true) }
                ?: resolved.directPlayUrl

            if (inspection.isHtmlContent && inspection.extractedAudioUrl.isNullOrBlank()) {
                Log.w(TAG, "Remote link returned HTML page with no audio: ${inspection.detail}")
                withContext(Dispatchers.Main) {
                    if (allowLocalFallback) startLocalFallback(R.raw.athkar_morning) else stopPlaybackAndSelf()
                }
                return@launch
            }
            if (inspection.ok) {
                Log.i(TAG, "Remote audio verified: ${inspection.kind.labelAr} (${inspection.detail})")
            } else {
                Log.w(TAG, "Remote pre-check inconclusive (${inspection.detail}); attempting direct MediaPlayer stream anyway")
            }
            withContext(Dispatchers.Main) {
                playRemoteMediaPlayer(targetUrl, allowLocalFallback)
            }
        }
    }


    private fun playRemoteMediaPlayer(url: String, allowLocalFallback: Boolean = true) {
        releasePlayer()
        runCatching {
            val headers = mapOf("User-Agent" to RemoteAudioVerifier.USER_AGENT)
            val player = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(this@MorningEveningAthkarService, Uri.parse(url), headers)
                setOnPreparedListener { mp ->
                    mp.start()
                }
                setOnCompletionListener {
                    stopPlaybackAndSelf()
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Remote audio MediaPlayer error what=$what extra=$extra")
                    if (allowLocalFallback) startLocalFallback(R.raw.athkar_morning) else stopPlaybackAndSelf()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        }.onFailure { e ->
            Log.e(TAG, "Failed to stream remote audio playback", e)
            if (allowLocalFallback) startLocalFallback(R.raw.athkar_morning) else stopPlaybackAndSelf()
        }
    }

    /** صوت احتياطي محلي يضمن سماع التذكير عند تعذّر الرابط البعيد */
    private fun startLocalFallback(rawResId: Int) {
        releasePlayer()
        runCatching {
            val player = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                resources.openRawResourceFd(rawResId).use { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                setOnCompletionListener {
                    stopPlaybackAndSelf()
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Fallback local audio error what=$what extra=$extra")
                    stopPlaybackAndSelf()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
        }.onFailure { e ->
            Log.e(TAG, "Fallback local audio failed", e)
            stopPlaybackAndSelf()
        }
    }

    private fun stopPlaybackAndSelf() {
        releasePlayer()
        _playbackState.value = AthkarAudioPlaybackState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun releasePlayer() {
        mediaPlayer?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
            }
            runCatching { mp.release() }
        }
        mediaPlayer = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "أذكار الصباح والمساء والتنبيهات الصوتية",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "تشغيل صوت أذكار الصباح والمساء والتنبيهات الصوتية تلقائياً"
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        releasePlayer()
        _playbackState.value = AthkarAudioPlaybackState()
    }

    companion object {
        private const val TAG = "MorningEveningAthkar"
        private const val CHANNEL_ID = "athkar_audio_playback_channel"
        private const val NOTIFICATION_ID = 5500
        const val ACTION_START = "com.example.ATHKAR_AUDIO_START"
        const val ACTION_START_ATHAN = "com.example.ATHKAR_AUDIO_START_ATHAN"
        const val ACTION_START_REMOTE = "com.example.ATHKAR_AUDIO_START_REMOTE"
        const val ACTION_STOP = "com.example.ATHKAR_AUDIO_STOP"
        const val EXTRA_ATHKAR_TYPE = "extra_athkar_type"
        const val EXTRA_PRAYER_NAME_AR = "extra_prayer_name_ar"
        const val EXTRA_CITY_NAME = "extra_city_name"
        const val EXTRA_VOICE_ID = "extra_voice_id"
        const val EXTRA_ATHAN_PLAY_MODE_ID = "extra_athan_play_mode_id"
        const val EXTRA_REMOTE_ID = "extra_remote_id"
        const val EXTRA_REMOTE_URL = "extra_remote_url"
        const val EXTRA_LOCAL_AUDIO_PATH = "extra_local_audio_path"
        const val EXTRA_ALLOW_LOCAL_FALLBACK = "extra_allow_local_fallback"
        const val EXTRA_REMOTE_TITLE = "extra_remote_title"
        const val EXTRA_REMOTE_BODY = "extra_remote_body"

        private val _playbackState = MutableStateFlow(AthkarAudioPlaybackState())
        val playbackState: StateFlow<AthkarAudioPlaybackState> = _playbackState.asStateFlow()

        fun startAthan(
            context: Context,
            prayerNameAr: String,
            cityName: String,
            voice: MuezzinVoice,
            playMode: AthanPlayMode = AthanPlayMode.FULL
        ): Boolean {
            return runCatching {
                val intent = Intent(context, MorningEveningAthkarService::class.java).apply {
                    action = ACTION_START_ATHAN
                    putExtra(EXTRA_PRAYER_NAME_AR, prayerNameAr)
                    putExtra(EXTRA_CITY_NAME, cityName)
                    putExtra(EXTRA_VOICE_ID, voice.id)
                    putExtra(EXTRA_ATHAN_PLAY_MODE_ID, playMode.id)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            }.getOrElse { e ->
                Log.w(TAG, "Unable to start foreground Athan service: ${e.message}")
                false
            }
        }

        fun start(context: Context, type: AthkarAudioType): Boolean {
            return runCatching {
                val intent = Intent(context, MorningEveningAthkarService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_ATHKAR_TYPE, type.id)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            }.getOrElse { e ->
                Log.w(TAG, "Unable to start foreground Athkar service: ${e.message}")
                false
            }
        }

        fun startRemote(
            context: Context,
            title: String,
            body: String,
            audioUrl: String = "",
            remoteId: String? = null,
            localAudioPath: String? = null,
            allowLocalFallback: Boolean = true
        ): Boolean {
            val hasLocalFile = !localAudioPath.isNullOrBlank() && java.io.File(localAudioPath).exists()
            val resolved = if (audioUrl.isNotBlank()) AudioUrlResolver.resolve(audioUrl) else null
            val hasValidUrl = resolved?.isValidHttps == true
            if (!hasLocalFile && !hasValidUrl) return false

            return runCatching {
                val intent = Intent(context, MorningEveningAthkarService::class.java).apply {
                    action = ACTION_START_REMOTE
                    putExtra(EXTRA_REMOTE_ID, remoteId)
                    putExtra(EXTRA_REMOTE_TITLE, title)
                    putExtra(EXTRA_REMOTE_BODY, body)
                    putExtra(EXTRA_ALLOW_LOCAL_FALLBACK, allowLocalFallback)
                    if (hasLocalFile) {
                        putExtra(EXTRA_LOCAL_AUDIO_PATH, localAudioPath)
                    }
                    if (hasValidUrl) {
                        putExtra(EXTRA_REMOTE_URL, resolved?.directPlayUrl)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            }.getOrElse { e ->
                Log.w(TAG, "Unable to start remote audio service: ${e.message}")
                false
            }
        }

        fun stop(context: Context) {
            runCatching {
                val intent = Intent(context, MorningEveningAthkarService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            }
        }
    }
}
