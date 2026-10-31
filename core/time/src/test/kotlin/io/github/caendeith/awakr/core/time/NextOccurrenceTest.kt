package io.github.caendeith.awakr.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextOccurrenceTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): ZonedDateTime =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

    @Test
    fun laterTodayStaysToday() {
        val next = NextOccurrence.next(at(2026, 3, 2, 6, 0), 7, 30, 0)!!
        assertEquals(LocalDate.of(2026, 3, 2), next.toLocalDate())
        assertEquals(7, next.hour)
        assertEquals(30, next.minute)
    }

    @Test
    fun passedTimeRollsToTomorrow() {
        val next = NextOccurrence.next(at(2026, 3, 2, 8, 0), 7, 30, 0)!!
        assertEquals(LocalDate.of(2026, 3, 3), next.toLocalDate())
    }

    @Test
    fun repeatMaskSkipsToNextEnabledDay() {
        // 2026-03-07 is a Saturday; workday mask must land on Monday the 9th.
        val saturday = at(2026, 3, 7, 10, 0)
        val workdays = 0b0011111
        val next = NextOccurrence.next(saturday, 7, 30, workdays)!!
        assertEquals(DayOfWeek.MONDAY, next.dayOfWeek)
        assertEquals(LocalDate.of(2026, 3, 9), next.toLocalDate())
    }

    @Test
    fun sameDayRepeatUsesToday() {
        val next = NextOccurrence.next(at(2026, 3, 2, 5, 0), 7, 30, 0b0000001)!!
        assertEquals(LocalDate.of(2026, 3, 2), next.toLocalDate())
    }

    @Test
    fun springForwardGapMovesForward() {
        // 2026-03-29 02:30 does not exist in Berlin: clocks jump 02:00 -> 03:00.
        val next = NextOccurrence.next(at(2026, 3, 29, 0, 0), 2, 30, 0)!!
        assertEquals(3, next.hour)
        assertEquals(30, next.minute)
        assertEquals(29, next.dayOfMonth)
    }

    @Test
    fun fallBackOverlapUsesEarlierOffset() {
        // 2026-10-25 02:30 happens twice in Berlin; the earlier instant is taken.
        val next = NextOccurrence.next(at(2026, 10, 25, 0, 0), 2, 30, 0)!!
        assertEquals(2, next.hour)
        assertEquals(30, next.minute)
        assertEquals(2, next.offset.totalSeconds / 3600)
    }

    @Test
    fun everyDayRingIsAlwaysWithinNextDay() {
        val now = at(2026, 6, 15, 12, 0)
        val next = NextOccurrence.next(now, 12, 0, 0b1111111)!!
        assertTrue(next.isAfter(now))
        assertTrue(next.isBefore(now.plusDays(1).plusMinutes(1)))
    }

    @Test
    fun invalidTimeIsRejected() {
        assertNull(NextOccurrence.next(at(2026, 3, 2, 6, 0), 25, 0, 0))
        assertNull(NextOccurrence.next(at(2026, 3, 2, 6, 0), 6, 61, 0))
    }

    @Test
    fun previousReturnsTheSameMorningBeforeTheTime() {
        val prev = NextOccurrence.previous(at(2026, 3, 2, 8, 0), 7, 30, 0)!!
        assertEquals(LocalDate.of(2026, 3, 2), prev.toLocalDate())
        assertEquals(7, prev.hour)
        assertEquals(30, prev.minute)
    }

    @Test
    fun previousRollsBackWhenTheTimeHasNotComeYet() {
        val prev = NextOccurrence.previous(at(2026, 3, 2, 6, 0), 7, 30, 0)!!
        assertEquals(LocalDate.of(2026, 3, 1), prev.toLocalDate())
    }

    @Test
    fun previousRespectsTheRepeatMask() {
        // 2026-03-07 is a Saturday; the workday mask must walk back to Friday.
        val prev = NextOccurrence.previous(at(2026, 3, 7, 12, 0), 7, 30, 0b0011111)!!
        assertEquals(DayOfWeek.FRIDAY, prev.dayOfWeek)
        assertEquals(LocalDate.of(2026, 3, 6), prev.toLocalDate())
    }

    @Test
    fun previousIsAlwaysBeforeNow() {
        val now = at(2026, 6, 15, 12, 0)
        val prev = NextOccurrence.previous(now, 12, 0, 0b1111111)!!
        assertTrue(prev.isBefore(now))
        assertTrue(prev.isAfter(now.minusDays(2)))
    }

    @Test
    fun previousRejectsInvalidTime() {
        assertNull(NextOccurrence.previous(at(2026, 3, 2, 6, 0), 24, 0, 0))
    }

    @Test
    fun repeatsOnUsesIsoWeekdayBits() {
        assertTrue(NextOccurrence.repeatsOn(0b1000000, DayOfWeek.SUNDAY))
        assertTrue(NextOccurrence.repeatsOn(0b0000001, DayOfWeek.MONDAY))
        assertTrue(!NextOccurrence.repeatsOn(0b0000001, DayOfWeek.TUESDAY))
    }
}
