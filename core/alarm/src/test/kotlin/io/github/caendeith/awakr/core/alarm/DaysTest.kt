package io.github.caendeith.awakr.core.alarm

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DaysTest {

    @Test
    fun everyDayHasAllBits() {
        assertEquals(0b1111_111, Days.EVERY_DAY)
        DayOfWeek.entries.forEach { assertTrue(Days.bit(Days.EVERY_DAY, it)) }
    }

    @Test
    fun mondayIsBitZero() {
        assertEquals(0b0000_001, Days.maskOf(DayOfWeek.MONDAY))
        assertEquals(0b1000_000, Days.maskOf(DayOfWeek.SUNDAY))
    }

    @Test
    fun maskRoundTrip() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val mask = Days.toMask(days)
        assertEquals(days, Days.toDaySet(mask))
        assertTrue(Days.hasRepeats(mask))
        assertFalse(Days.hasRepeats(0))
    }

    @Test
    fun singleDayMask() {
        assertEquals(0b0001_010, Days.toMask(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)))
    }
}
