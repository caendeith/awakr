package io.github.caendeith.awakr.core.audio

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Pure rules of the "record your own alarm sound" flow: file naming, the 30 second ceiling and the
 * guards the UI needs.
 *
 * Kept free of Android so every rule can be unit tested on the JVM.
 */
object RecordingRules {

    /** Hard limit of one recording; the picker stops automatically when it is reached. */
    const val MAX_DURATION_MS: Int = 30_000

    /** Shorter clips are dropped as accidental taps. */
    const val MIN_DURATION_MS: Int = 700

    /** Container written by [AlarmRecorder]: AAC in MPEG-4. */
    const val EXTENSION: String = "m4a"

    const val MIME_TYPE: String = "audio/mp4"

    /** Prefix of every file this feature writes; also parsed back for the label. */
    const val FILE_PREFIX: String = "rec_"

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * `rec_20261102_104851_a1b2c3d4.m4a` – sortable, unique and safe on every file system.
     *
     * @param timestampMs wall clock time of the recording
     * @param unique any unique token (a UUID is fine); only letters and digits are kept
     */
    fun fileName(
        timestampMs: Long,
        unique: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val stamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMs), zone).format(STAMP)
        val suffix = unique.filter { it.isLetterOrDigit() }.lowercase().take(8).ifEmpty { "sound" }
        return FILE_PREFIX + stamp + "_" + suffix + "." + EXTENSION
    }

    /** "20:41" for a name produced by [fileName]; null for imported files and anything else. */
    fun timeLabel(fileName: String): String? = timestamp(fileName)?.format(CLOCK)

    /** Wall clock time encoded in a recorded file name, or null when the name is not ours. */
    fun timestamp(fileName: String): LocalDateTime? {
        if (!fileName.startsWith(FILE_PREFIX)) return null
        val base = fileName.substringAfter(FILE_PREFIX).substringBefore('.')
        if (base.length < 15) return null
        val stamp = base.substring(0, 15)
        return try {
            LocalDateTime.parse(stamp, STAMP)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /** Elapsed recording time clipped to the allowed window. */
    fun clampDurationMs(durationMs: Long): Int =
        durationMs.coerceIn(0L, MAX_DURATION_MS.toLong()).toInt()

    /** Milliseconds left before the automatic stop, never negative. */
    fun remainingMs(elapsedMs: Long): Int = (MAX_DURATION_MS - clampDurationMs(elapsedMs)).coerceAtLeast(0)

    /** Progress of the recording, 0f..1f. */
    fun progress(elapsedMs: Long): Float = clampDurationMs(elapsedMs).toFloat() / MAX_DURATION_MS

    /** `0:07` – clock style label without locale surprises. */
    fun durationLabel(ms: Long): String {
        val seconds = clampDurationMs(ms) / 1000
        return "" + (seconds / 60) + ":" + (seconds % 60).toString().padStart(2, '0')
    }

    /**
     * False when the clip is an accidental tap. The upper bound is lenient: stop() can overrun the
     * automatic stop by a few milliseconds on a slow device.
     */
    fun isUsable(durationMs: Long): Boolean =
        durationMs >= MIN_DURATION_MS && durationMs <= MAX_DURATION_MS + 3_000L
}
