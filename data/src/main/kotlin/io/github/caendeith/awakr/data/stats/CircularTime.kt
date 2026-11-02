package io.github.caendeith.awakr.data.stats

import java.time.LocalTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Mean of times of day that respects the 24-hour circle.
 *
 * A plain arithmetic average puts 23:50 and 00:10 at noon. Here every time becomes a unit
 * vector on a clock face, the vectors are summed, and the direction of the sum is the
 * average, which puts those two times at midnight instead.
 *
 * Values are minutes after local midnight. Folding them into a single day makes the result
 * independent of whole-day offsets, so 1500 and -30 behave like 60 and 1410.
 */
object CircularTime {

    const val MINUTES_PER_DAY: Int = 24 * 60

    /** Below this resultant length the times cancel each other out and have no average. */
    private const val DEGENERATE_EPSILON = 1e-9

    private const val FULL_TURN_DEGREES = 360.0

    /** Circular mean in minutes after midnight, in `[0, 1440)`, or null for an empty list. */
    fun meanMinuteOfDay(minutes: List<Double>): Double? {
        if (minutes.isEmpty()) return null
        var sinSum = 0.0
        var cosSum = 0.0
        minutes.forEach { value ->
            val angle = Math.toRadians(normalize(value) / MINUTES_PER_DAY * FULL_TURN_DEGREES)
            sinSum += sin(angle)
            cosSum += cos(angle)
        }
        // Times spread evenly around the clock (or exactly opposite each other) cancel out.
        // There is no honest average then, so report "unknown" rather than an arbitrary 00:00.
        if (hypot(sinSum, cosSum) < DEGENERATE_EPSILON) return null
        val degrees = Math.toDegrees(atan2(sinSum, cosSum))
        // Adding a whole day before taking the remainder folds a tiny negative angle (just
        // below midnight, e.g. -1e-15) to 0 instead of letting it round up to 1440.
        return (degrees / FULL_TURN_DEGREES * MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY
    }

    /** Whole minutes plus the second fraction of [time], as minutes after midnight. */
    fun minutesOfDay(time: LocalTime): Double = time.toSecondOfDay() / 60.0

    /** [minute] folded into a single day, including negative values. */
    private fun normalize(minute: Double): Double {
        val wrapped = minute % MINUTES_PER_DAY
        return if (wrapped < 0.0) wrapped + MINUTES_PER_DAY else wrapped
    }
}
