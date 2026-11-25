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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
            ACTION_START_REMOTE -> {
                val url = intent.getStringExtra(EXTRA_REMOTE_URL).orEmpty()
                val title = intent.getStringExtra(EXTRA_REMOTE_TITLE).orEmpty().ifBlank { "تذكير صوتي" }
                val body = intent.getStringExtra(EXTRA_REMOTE_BODY).orEmpty().ifBlank { title }
                val remoteId = intent.getStringExtra(EXTRA_REMOTE_ID)
                if (url.isNotBlank()) {
                    startRemotePlayback(remoteId, title, body, url)
                } else {
                    stopPlaybackAndSelf()
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

    private fun startPlayback(type: AthkarAudioType) {
        releasePlayer()
        startForegroundNotification(type.titleAr, type.subtitleAr)

        runCatching {
            val player = MediaPlayer().apply {
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

    private fun startRemotePlayback(remoteId: String?, title: String, body: String, rawUrl: String) {
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
            val inspection = RemoteAudioVerifier.inspect(resolved.directPlayUrl)
            val targetUrl = inspection.extractedAudioUrl
                ?.let { AudioUrlResolver.resolve(it).directPlayUrl }
                ?.takeIf { it.startsWith("https://", ignoreCase = true) }
                ?: inspection.finalUrl.takeIf { it.startsWith("https://", ignoreCase = true) }
                ?: resolved.directPlayUrl

            if (inspection.isHtmlContent && inspection.extractedAudioUrl.isNullOrBlank()) {
                Log.w(TAG, "Remote link returned HTML page with no audio: ${inspection.detail}; using local fallback")
                withContext(Dispatchers.Main) {
                    startLocalFallback(R.raw.athkar_morning)
                }
                return@launch
            }
            if (inspection.ok) {
                Log.i(TAG, "Remote audio verified: ${inspection.kind.labelAr} (${inspection.detail})")
            } else {
                Log.w(TAG, "Remote pre-check inconclusive (${inspection.detail}); attempting direct MediaPlayer stream anyway")
            }
            withContext(Dispatchers.Main) {
                playRemoteMediaPlayer(targetUrl)
            }
        }
    }

    private fun playRemoteMediaPlayer(url: String) {
        releasePlayer()
        runCatching {
            val headers = mapOf("User-Agent" to RemoteAudioVerifier.USER_AGENT)
            val player = MediaPlayer().apply {
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
                    Log.w(TAG, "Remote audio MediaPlayer error what=$what extra=$extra; falling back to local audio")
                    startLocalFallback(R.raw.athkar_morning)
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        }.onFailure { e ->
            Log.e(TAG, "Failed to stream remote audio playback; using local fallback", e)
            startLocalFallback(R.raw.athkar_morning)
        }
    }

    /** صوت احتياطي محلي يضمن سماع التذكير عند تعذّر الرابط البعيد */
    private fun startLocalFallback(rawResId: Int) {
        releasePlayer()
        runCatching {
            val player = MediaPlayer().apply {
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
        const val ACTION_START_REMOTE = "com.example.ATHKAR_AUDIO_START_REMOTE"
        const val ACTION_STOP = "com.example.ATHKAR_AUDIO_STOP"
        const val EXTRA_ATHKAR_TYPE = "extra_athkar_type"
        const val EXTRA_REMOTE_ID = "extra_remote_id"
        const val EXTRA_REMOTE_URL = "extra_remote_url"
        const val EXTRA_REMOTE_TITLE = "extra_remote_title"
        const val EXTRA_REMOTE_BODY = "extra_remote_body"

        private val _playbackState = MutableStateFlow(AthkarAudioPlaybackState())
        val playbackState: StateFlow<AthkarAudioPlaybackState> = _playbackState.asStateFlow()

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
            audioUrl: String,
            remoteId: String? = null
        ): Boolean {
            val resolved = AudioUrlResolver.resolve(audioUrl)
            if (!resolved.isValidHttps) return false
            return runCatching {
                val intent = Intent(context, MorningEveningAthkarService::class.java).apply {
                    action = ACTION_START_REMOTE
                    putExtra(EXTRA_REMOTE_ID, remoteId)
                    putExtra(EXTRA_REMOTE_TITLE, title)
                    putExtra(EXTRA_REMOTE_BODY, body)
                    putExtra(EXTRA_REMOTE_URL, resolved.directPlayUrl)
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
