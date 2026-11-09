package io.github.caendeith.awakr.ui.sleep

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepFormatTest {

    @Test
    fun `zero and sub-minute durations have no parts`() {
        assertEquals(DurationParts(0, 0), durationParts(0L))
        assertEquals(DurationParts(0, 0), durationParts(59_999L))
    }

    @Test
    fun `full hours drop the minutes`() {
        assertEquals(DurationParts(8, 0), durationParts(8 * 60 * 60 * 1000L))
    }

    @Test
    fun `minutes are the remainder`() {
        assertEquals(DurationParts(7, 32), durationParts((7 * 60 + 32) * 60_000L))
    }

    @Test
    fun `negative durations clamp to zero`() {
        assertEquals(DurationParts(0, 0), durationParts(-5_000L))
    }

    @Test
    fun `clock time keeps leading zeros`() {
        assertEquals("07:05", clockTime(7, 5))
        assertEquals("23:59", clockTime(23, 59))
        assertEquals("00:00", clockTime(0, 0))
    }
}
