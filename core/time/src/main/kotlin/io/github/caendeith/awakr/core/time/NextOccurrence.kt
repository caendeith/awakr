package io.github.caendeith.awakr.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Computes the next moment an alarm should ring.
 *
 * The calendar is evaluated in the alarm's own time zone, so daylight saving
 * transitions are followed by wall clock time: an alarm set to 07:30 rings at
 * 07:30 local time before and after a transition. When the requested local time
 * does not exist (spring forward) the platform resolves it forward to the first
 * valid instant, and when it exists twice (fall back) the earlier instant wins.
 */
object NextOccurrence {

    /** Bit 0 is Monday, bit 6 is Sunday. Zero means "no repeat". */
    fun repeatsOn(daysMask: Int, day: DayOfWeek): Boolean =
        daysMask and (1 shl (day.value - 1)) != 0

    /**
     * @param daysMask repeat mask, or 0 for a one-shot alarm
     * @return the earliest instant strictly after [now], or null when it cannot be computed
     */
    fun next(now: ZonedDateTime, hour: Int, minute: Int, daysMask: Int): ZonedDateTime? {
        if (hour !in 0..23 || minute !in 0..59) return null

        // Eight days of candidates is enough for every mask: the same weekday next week
        // is always after any "now" within the current week.
        for (offset in 0..7) {
            val date: LocalDate = now.toLocalDate().plusDays(offset.toLong())
            if (daysMask != 0 && !repeatsOn(daysMask, date.dayOfWeek)) continue
            val candidate = resolve(date, hour, minute, now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }

    /** Local wall clock time resolved in [zone]; DST gaps move forward, overlaps take the earlier instant. */
    fun resolve(date: LocalDate, hour: Int, minute: Int, zone: java.time.ZoneId): ZonedDateTime =
        ZonedDateTime.of(date.atTime(hour, minute), zone)
}
