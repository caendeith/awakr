package io.github.caendeith.awakr.plugins.mission.step

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure step detector built on the raw accelerometer.
 *
 * Deliberately not [android.hardware.Sensor.TYPE_STEP_COUNTER]: that sensor is a "significant
 * motion" one, on many ROMs it needs the activity-recognition permission, and its resolution is
 * one sensor epoch. Walking produces a clear vertical oscillation around 1 g, so the steps are
 * counted here instead — no permission, no dependency.
 *
 * A step is a rising edge over [threshold] of the deviation from gravity, with two guards:
 *
 * - a refractory window, because the heel strike and the push-off are ~120 ms apart and only the
 *   first peak of a step may count;
 * - hysteresis — the signal has to fall back near gravity before the next step counts.
 *
 * Kept free of Android APIs so it can be unit tested.
 */
internal class StepCounter(
    private val threshold: Float = DEFAULT_THRESHOLD,
    private val refractoryMillis: Long = DEFAULT_REFRACTORY_MILLIS,
) {
    var count: Int = 0
        private set

    private var armed: Boolean = true
    private var lastStepAt: Long = Long.MIN_VALUE / 4

    /** Feeds one accelerometer sample (m/s²) and returns the number of steps so far. */
    fun onSample(x: Float, y: Float, z: Float, nowMillis: Long): Int {
        val deviation = abs(sqrt(x * x + y * y + z * z) - GRAVITY)
        if (deviation >= threshold) {
            val sinceLastStep = nowMillis - lastStepAt
            if (armed && sinceLastStep >= refractoryMillis) {
                count += 1
                lastStepAt = nowMillis
                armed = false
            }
        } else if (deviation < threshold * REARM_RATIO) {
            armed = true
        }
        return count
    }

    fun reset() {
        count = 0
        armed = true
        lastStepAt = Long.MIN_VALUE / 4
    }

    companion object {
        /** Standard gravity in m/s², the resting magnitude of the accelerometer. */
        const val GRAVITY = 9.80665f

        /** Deviation from gravity that counts as a step, in m/s². */
        const val DEFAULT_THRESHOLD = 2.0f

        /** Minimum time between two counted steps (~4 steps/s). */
        const val DEFAULT_REFRACTORY_MILLIS = 280L

        /** The signal has to drop below this fraction of the threshold to arm the next step. */
        const val REARM_RATIO = 0.4f
    }
}
