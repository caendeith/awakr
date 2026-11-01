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
 *
 * The player can be muted without stopping it ([setSilenced]): the mission timer uses that to keep
 * the ring session alive while the user solves the task. The ramp keeps running while the player is
 * silenced, so un-silencing continues from the level the ramp had already reached and never goes
 * above the configured ceiling.
 */
class AlarmSoundPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private var rampJob: Job? = null

    /** Position of the volume ramp, 0f..1f. The volume actually applied is 0 while silenced. */
    private var rampLevel = 0f

    private var silenced = false

    /** True while the sound is muted by [setSilenced] although the alarm is still ringing. */
    val isSilenced: Boolean
        get() = silenced

    fun play(
        uri: Uri?,
        rampSeconds: Int,
        startVolumePercent: Int,
        scope: CoroutineScope,
    ) {
        stop()
        // Never fall back to the system default URI here: the resolver already ends at a shipped
        // tone. A file the user picked can still be gone or corrupt when the alarm goes off, so
        // open the chosen sound first and then the shipped default: an alarm never rings in silence.
        val requested = uri ?: SoundResolver.uri(context, null, null)
        val mediaPlayer = createPlayer(requested)
            ?: createPlayer(SoundResolver.uri(context, null, null))
        if (mediaPlayer == null) {
            Log.e(TAG, "cannot play any alarm sound, requested $requested")
            return
        }
        player = mediaPlayer
        val start = (startVolumePercent.coerceIn(1, 100) / 100f)
        rampLevel = start
        applyVolume()
        mediaPlayer.start()

        if (rampSeconds <= 0) {
            rampLevel = MAX_LEVEL
            applyVolume()
            return
        }
        val steps = (rampSeconds.coerceIn(0, 600) * 2).coerceAtLeast(1)
        rampJob = scope.launch {
            for (step in 1..steps) {
                delay(RAMP_STEP_MS)
                rampLevel = start + (MAX_LEVEL - start) * (step.toFloat() / steps)
                applyVolume()
            }
        }
    }

    private fun createPlayer(uri: Uri): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setDataSource(context, uri)
            isLooping = true
            prepare()
        }
    } catch (e: Exception) {
        Log.e(TAG, "cannot play alarm sound $uri", e)
        null
    }

    /**
     * Mutes or un-mutes the ring without stopping it and without cancelling the volume ramp.
     *
     * Safe to call at any time, including while the ramp coroutine is still running, and when no
     * sound is playing (nothing happens then).
     */
    fun setSilenced(silenced: Boolean) {
        if (this.silenced == silenced) return
        this.silenced = silenced
        applyVolume()
    }

    fun stop() {
        rampJob?.cancel()
        rampJob = null
        silenced = false
        rampLevel = 0f
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

    private fun applyVolume() {
        val mediaPlayer = player ?: return
        val level = if (silenced) 0f else rampLevel.coerceIn(0f, MAX_LEVEL)
        runCatching { mediaPlayer.setVolume(level, level) }
            .onFailure { Log.w(TAG, "cannot set volume", it) }
    }

    private companion object {
        const val TAG = "AlarmSoundPlayer"
        const val RAMP_STEP_MS = 500L
        const val MAX_LEVEL = 1f
    }
}
