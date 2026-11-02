package io.github.caendeith.awakr.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class SkipNextTest {

    private val zone: ZoneId = ZoneId.of("Europe/Samara")
    private val monday: ZonedDateTime = ZonedDateTime.of(2026, 11, 2, 6, 0, 0, 0, zone)

    private val everyDay: Int = (1 shl 7) - 1
    private val mondayOnly: Int = 1 shl (DayOfWeek.MONDAY.value - 1)

    private fun millis(time: ZonedDateTime): Long = time.toInstant().toEpochMilli()

    private fun durationHours(from: ZonedDateTime, to: ZonedDateTime): Long =
        java.time.Duration.between(from.toInstant(), to.toInstant()).toHours()

    @Test
    fun withoutSkipTheNextOccurrenceIsUnchanged() {
        val next = NextOccurrence.next(monday, 7, 30, everyDay)
        assertEquals(ZonedDateTime.of(2026, 11, 2, 7, 30, 0, 0, zone), next)
    }

    @Test
    fun skippingADailyAlarmMovesItToTheDayAfter() {
        val first = NextOccurrence.next(monday, 7, 30, everyDay)!!
        val afterSkip = NextOccurrence.next(monday, 7, 30, everyDay, millis(first))
        assertEquals(ZonedDateTime.of(2026, 11, 3, 7, 30, 0, 0, zone), afterSkip)
    }

    @Test
    fun skippingAWeeklyAlarmMovesItToTheNextWeek() {
        val first = NextOccurrence.next(monday, 7, 30, mondayOnly)!!
        val afterSkip = NextOccurrence.next(monday, 7, 30, mondayOnly, millis(first))
        assertEquals(ZonedDateTime.of(2026, 11, 9, 7, 30, 0, 0, zone), afterSkip)
    }

    @Test
    fun skippingTwiceInARowMovesTheAlarmTwoOccurrencesAhead() {
        val first = NextOccurrence.next(monday, 7, 30, everyDay)!!
        val second = NextOccurrence.next(monday, 7, 30, everyDay, millis(first))!!
        // The home screen widget and the alarm list both store the moment being skipped, so a
        // second press has to look past the whole skipped range instead of restarting from "now".
        val third = NextOccurrence.next(monday, 7, 30, everyDay, millis(second))
        assertEquals(ZonedDateTime.of(2026, 11, 4, 7, 30, 0, 0, zone), third)
    }

    @Test
    fun skippingTheOnlyOccurrenceOfAOneShotAlarmLeavesNothing() {
        val first = NextOccurrence.next(monday, 7, 30, daysMask = 0)!!
        assertNull(NextOccurrence.next(monday, 7, 30, daysMask = 0, skipNextAt = millis(first)))
    }

    @Test
    fun aStaleSkipValueIsIgnored() {
        assertNull(null)
        val first = NextOccurrence.next(monday, 7, 30, everyDay)!!
        val yesterday = millis(first.minusDays(1))
        assertEquals(first, NextOccurrence.next(monday, 7, 30, everyDay, yesterday))
    }

    @Test
    fun skippingIsEvaluatedOnInstantsNotWallClock() {
        // Same wall clock time in a zone with a DST transition: the skip value is an instant, so
        // the comparison still matches the candidate the scheduler produced.
        val berlin = ZoneId.of("Europe/Berlin")
        val now = ZonedDateTime.of(2026, 3, 28, 6, 0, 0, 0, berlin)
        val first = NextOccurrence.next(now, 7, 30, everyDay)!!
        val skipped = NextOccurrence.next(now, 7, 30, everyDay, millis(first))!!
        // 28 March is still winter time (+01:00), 29 March already uses summer time (+02:00), so the
        // gap between the two occurrences is 23 hours even though the wall clock time is identical.
        assertEquals(ZonedDateTime.of(2026, 3, 28, 7, 30, 0, 0, berlin), first)
        assertEquals(ZonedDateTime.of(2026, 3, 29, 7, 30, 0, 0, berlin), skipped)
        assertEquals(23L, durationHours(first, skipped))
    }
}
