package io.github.caendeith.awakr.core.alarm

import java.time.DayOfWeek

/**
 * Day-of-week bit mask used by `alarms.days_mask`.
 * Bit 0 = Monday ... bit 6 = Sunday (ISO-8601 order).
 */
object Days {
    const val EVERY_DAY: Int = 0b1111_111

    fun bit(daysMask: Int, day: DayOfWeek): Boolean =
        (daysMask shr (day.value - 1)) and 1 == 1

    fun maskOf(vararg days: DayOfWeek): Int =
        days.fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }

    fun toMask(days: Set<DayOfWeek>): Int =
        days.fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }

    fun toDaySet(daysMask: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filter { bit(daysMask, it) }.toSet()

    /** False for one-shot alarms (mask 0) and for masks with no valid bits. */
    fun hasRepeats(daysMask: Int): Boolean = (daysMask and EVERY_DAY) != 0
}
