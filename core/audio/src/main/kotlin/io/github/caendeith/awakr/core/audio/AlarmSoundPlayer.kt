package io.github.caendeith.awakr.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plays the alarm sound with a gradual volume ramp.
 *
 * The stream is USAGE_ALARM so it follows the alarm volume slider and is not
 * muted by "do not disturb" the way media playback is.
 */
class AlarmSoundPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private var rampJob: Job? = null

    fun play(
        uri: Uri?,
        rampSeconds: Int,
        startVolumePercent: Int,
        scope: CoroutineScope,
    ) {
        stop()
        val target = uri ?: defaultSoundUri()
        val mediaPlayer = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, target)
                isLooping = true
                prepare()
            }
        } catch (e: Exception) {
            Log.e(TAG, "cannot play alarm sound ${target}", e)
            return
        }
        player = mediaPlayer
        val start = (startVolumePercent.coerceIn(1, 100) / 100f)
        mediaPlayer.setVolume(start, start)
        mediaPlayer.start()

        val steps = (rampSeconds.coerceIn(0, 600) * 2).coerceAtLeast(1)
        if (rampSeconds <= 0) {
            mediaPlayer.setVolume(1f, 1f)
            return
        }
        rampJob = scope.launch {
            for (step in 1..steps) {
                delay(500)
                val fraction = start + (1f - start) * (step.toFloat() / steps)
                player?.setVolume(fraction, fraction)
            }
        }
    }

    fun stop() {
        rampJob?.cancel()
        rampJob = null
        player?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "player already released", e)
            }
            it.release()
        }
        player = null
    }

    private fun defaultSoundUri(): Uri =
        Uri.parse("android.resource://${context.packageName}/${android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI}")

    private companion object {
        const val TAG = "AlarmSoundPlayer"
    }
}
