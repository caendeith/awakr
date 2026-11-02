package io.github.caendeith.awakr.core.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.UUID

/** Outcome of [AlarmRecorder.stop]. */
sealed interface RecordingResult {
    /** A clip worth keeping, already stored in the app's sound folder. */
    data class Saved(val sound: RecordedSound) : RecordingResult

    /** The user let go too early; the file was removed. */
    data object TooShort : RecordingResult

    /** The platform refused to record (permission, call, broken audio service); nothing kept. */
    data object Failed : RecordingResult
}

/**
 * Records a short AAC clip into the app's private sound folder.
 *
 * Every platform call is guarded: a call arriving mid-recording, a microphone taken by another app
 * or a recorder that refuses to start must never crash the picker. The recorder is always released,
 * and the 30 second ceiling lives in [RecordingRules] so the UI can enforce it.
 */
class AlarmRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var target: File? = null
    private var startedAtMs = 0L

    val isRecording: Boolean get() = recorder != null

    /** Milliseconds recorded so far, 0 when idle. */
    fun elapsedMs(): Long =
        if (isRecording) (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L) else 0L

    /** Starts a new recording; returns the file being written, or null when recording is impossible. */
    fun start(): File? {
        if (isRecording) return target
        if (!canRecord()) {
            Log.w(TAG, "microphone unavailable or not permitted")
            return null
        }
        val file = File(
            SoundStorage.dir(context),
            RecordingRules.fileName(System.currentTimeMillis(), UUID.randomUUID().toString()),
        )
        val created = open(file) ?: return null
        recorder = created
        target = file
        startedAtMs = SystemClock.elapsedRealtime()
        Log.i(TAG, "recording into " + file.name)
        return file
    }

    /** Stops the recording and keeps it when it is long enough. */
    fun stop(): RecordingResult {
        val active = recorder
        val file = target
        recorder = null
        target = null
        if (active == null || file == null) return RecordingResult.Failed
        val elapsed = SystemClock.elapsedRealtime() - startedAtMs
        val stopped = runCatching { active.stop() }
        release(active)
        if (stopped.isFailure) {
            file.delete()
            // MediaRecorder rejects clips shorter than about a second by throwing on stop().
            return if (!RecordingRules.isUsable(elapsed)) {
                RecordingResult.TooShort
            } else {
                Log.w(TAG, "recorder stop failed", stopped.exceptionOrNull())
                RecordingResult.Failed
            }
        }
        if (!RecordingRules.isUsable(elapsed) || !file.isFile || file.length() <= 0L) {
            file.delete()
            return RecordingResult.TooShort
        }
        return RecordingResult.Saved(RecordedSound(file))
    }

    /** Stops without keeping the clip: the sheet was dismissed or the user starts over. */
    fun cancel() {
        val active = recorder
        val file = target
        recorder = null
        target = null
        if (active != null) {
            runCatching { active.stop() }
            release(active)
        }
        file?.delete()
    }

    private fun canRecord(): Boolean {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) return false
        return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Configures and starts a recorder; releases it again on any failure. */
    private fun open(file: File): MediaRecorder? {
        val fresh = newRecorder() ?: return null
        var started = false
        try {
            fresh.setAudioSource(MediaRecorder.AudioSource.MIC)
            fresh.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            fresh.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            fresh.setAudioEncodingBitRate(96_000)
            fresh.setAudioSamplingRate(44_100)
            fresh.setOutputFile(file)
            fresh.prepare()
            fresh.start()
            started = true
            return fresh
        } catch (e: Exception) {
            // Do not disturb, an ongoing call or a busy audio HAL all land here.
            Log.w(TAG, "cannot start recorder", e)
            return null
        } finally {
            if (!started) {
                release(fresh)
                file.delete()
            }
        }
    }

    private fun newRecorder(): MediaRecorder? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }.onFailure { Log.w(TAG, "cannot create recorder", it) }.getOrNull()

    private fun release(recorder: MediaRecorder) {
        runCatching { recorder.reset() }
        runCatching { recorder.release() }
    }

    private companion object {
        const val TAG = "AlarmRecorder"
    }
}
