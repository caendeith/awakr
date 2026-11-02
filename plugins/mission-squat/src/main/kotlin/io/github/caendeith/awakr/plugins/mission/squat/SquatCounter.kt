package io.github.caendeith.awakr.plugins.mission.squat

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure squat detector: counts the upward acceleration peaks of a body squat.
 *
 * A squat is one dip below 1 g while the body drops and one peak above 1 g on the way up, so only
 * the positive deviation is counted. Two guards keep one squat from counting twice:
 *
 * - a refractory window, during which no peak can be counted at all;
 * - hysteresis — the signal has to fall back near gravity before the next peak counts, so a single
 *   long peak (shaking the phone) cannot tick the counter again and again.
 *
 * Kept free of Android APIs so it can be unit tested.
 */
internal class SquatCounter(
    private val threshold: Float = DEFAULT_THRESHOLD,
    private val refractoryMillis: Long = DEFAULT_REFRACTORY_MILLIS,
) {
    var count: Int = 0
        private set

    private var armed: Boolean = true
    private var lastPeakAt: Long = Long.MIN_VALUE / 4

    /** Feeds one accelerometer sample (m/s²) and returns the number of squats so far. */
    fun onSample(x: Float, y: Float, z: Float, nowMillis: Long): Int {
        val upward = sqrt(x * x + y * y + z * z) - GRAVITY
        if (upward >= threshold) {
            val sinceLastPeak = nowMillis - lastPeakAt
            if (armed && sinceLastPeak >= refractoryMillis) {
                count += 1
                lastPeakAt = nowMillis
                armed = false
            }
        } else if (upward < threshold * REARM_RATIO) {
            armed = true
        }
        return count
    }

    fun reset() {
        count = 0
        armed = true
        lastPeakAt = Long.MIN_VALUE / 4
    }

    companion object {
        /** Standard gravity in m/s², the resting magnitude of the accelerometer. */
        const val GRAVITY = 9.80665f

        /** Upward deviation that counts as a squat, in m/s². */
        const val DEFAULT_THRESHOLD = 2.5f

        /** Minimum time between two counted squats. */
        const val DEFAULT_REFRACTORY_MILLIS = 450L

        /** The signal has to drop below this fraction of the threshold to arm the next count. */
        const val REARM_RATIO = 0.4f
    }
}
