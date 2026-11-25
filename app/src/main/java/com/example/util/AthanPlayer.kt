package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RawRes
import com.example.data.model.AthanPlayMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TAG = "AthanPlayer"

class AthanPlayer(private val context: Context) {

    private val playerLock = Any()
    private var activePlayer: MediaPlayer? = null
    private var completionSignal: CompletableDeferred<Unit>? = null

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun vibrateClick() {
        runCatching {
            val vib = vibrator ?: return
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(30L)
            }
        }
    }

    fun vibratePattern() {
        runCatching {
            val vib = vibrator ?: return
            if (!vib.hasVibrator()) return
            val timings = longArrayOf(0, 180, 90, 180, 90, 220)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(timings, -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(timings, -1)
            }
        }
    }

    fun stop() {
        synchronized(playerLock) {
            completionSignal?.complete(Unit)
            completionSignal = null
            activePlayer?.let { player ->
                runCatching {
                    if (player.isPlaying) {
                        player.stop()
                    }
                }
                runCatching { player.release() }
            }
            activePlayer = null
        }
    }

    suspend fun playAthan(
        @RawRes rawResId: Int,
        playMode: AthanPlayMode,
        vibrateEnabled: Boolean,
        onPlayingChanged: (Boolean) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        stop()
        if (vibrateEnabled) {
            vibratePattern()
        }

        val doneSignal = CompletableDeferred<Unit>()
        var player: MediaPlayer? = null

        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                context.resources.openRawResourceFd(rawResId).use { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                setOnCompletionListener {
                    doneSignal.complete(Unit)
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error what=$what extra=$extra")
                    doneSignal.complete(Unit)
                    true
                }
                prepare()
            }

            synchronized(playerLock) {
                activePlayer = player
                completionSignal = doneSignal
            }

            withContext(Dispatchers.Main.immediate) {
                onPlayingChanged(true)
            }

            player.start()

            if (playMode == AthanPlayMode.PARTIAL) {
                val totalMs = player.duration.takeIf { it > 0 } ?: 20_000
                val partialTargetMs = minOf(20_000L, totalMs.toLong())
                val fadeDurationMs = minOf(2_000L, partialTargetMs / 4L)
                val steadyMs = (partialTargetMs - fadeDurationMs).coerceAtLeast(0L)

                var waited = 0L
                while (waited < steadyMs && !doneSignal.isCompleted) {
                    val step = minOf(200L, steadyMs - waited)
                    delay(step)
                    waited += step
                }

                if (!doneSignal.isCompleted && fadeDurationMs > 0L) {
                    val steps = 20
                    val stepDelay = (fadeDurationMs / steps).coerceAtLeast(30L)
                    for (s in steps - 1 downTo 0) {
                        if (doneSignal.isCompleted) break
                        val vol = s.toFloat() / steps.toFloat()
                        runCatching { player.setVolume(vol, vol) }
                        delay(stepDelay)
                    }
                }
                doneSignal.complete(Unit)
            } else {
                doneSignal.await()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play Athan audio res=$rawResId", e)
        } finally {
            synchronized(playerLock) {
                if (activePlayer === player) {
                    activePlayer = null
                }
                if (completionSignal === doneSignal) {
                    completionSignal = null
                }
            }
            player?.let { mp ->
                runCatching {
                    if (mp.isPlaying) {
                        mp.stop()
                    }
                }
                runCatching { mp.release() }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main.immediate) {
                onPlayingChanged(false)
            }
        }
    }
}
