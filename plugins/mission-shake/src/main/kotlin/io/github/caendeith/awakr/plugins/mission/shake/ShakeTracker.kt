package io.github.caendeith.awakr.plugins.mission.shake

import kotlin.math.sqrt

/**
 * Pure accumulator for the shake mission: adds up the time the device spends above the
 * acceleration threshold. Kept free of Android APIs so it can be unit tested.
 */
internal class ShakeTracker(
    private val threshold: Float = DEFAULT_THRESHOLD,
    private val maxFrameMillis: Long = DEFAULT_MAX_FRAME_MILLIS,
) {
    var elapsedMillis: Long = 0L
        private set

    private var lastSampleAt: Long? = null

    /** Feeds one accelerometer sample and returns the accumulated shake time. */
    fun onSample(x: Float, y: Float, z: Float, nowMillis: Long): Long {
        val delta = lastSampleAt?.let { (nowMillis - it).coerceIn(0L, maxFrameMillis) } ?: 0L
        lastSampleAt = nowMillis
        val magnitude = sqrt(x * x + y * y + z * z)
        if (magnitude > threshold) elapsedMillis += delta
        return elapsedMillis
    }

    fun reset() {
        elapsedMillis = 0L
        lastSampleAt = null
    }

    companion object {
        const val DEFAULT_THRESHOLD = 12.5f
        const val DEFAULT_MAX_FRAME_MILLIS = 100L
    }
}
