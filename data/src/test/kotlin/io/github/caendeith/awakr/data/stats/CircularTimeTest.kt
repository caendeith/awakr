package io.github.caendeith.awakr.data.stats

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CircularTimeTest {

    @Test
    fun `empty list has no mean`() {
        assertNull(CircularTime.meanMinuteOfDay(emptyList()))
    }

    @Test
    fun `a single time is its own mean`() {
        assertEquals(435.0, CircularTime.meanMinuteOfDay(listOf(435.0))!!, 1e-6)
    }

    @Test
    fun `times around midnight average to midnight`() {
        // 23:50 and 00:10 are ten minutes before and after midnight.
        val mean = CircularTime.meanMinuteOfDay(listOf(23 * 60 + 50.0, 10.0))!!
        assertEquals(0.0, mean, 1e-6)
    }

    @Test
    fun `the naive average of those times would be noon`() {
        val values = listOf(23 * 60 + 30.0, 30.0)
        assertEquals(0.0, CircularTime.meanMinuteOfDay(values)!!, 1e-6)
        assertEquals(720.0, values.average(), 1e-6)
    }

    @Test
    fun `afternoon times average to the middle of the day`() {
        assertEquals(720.0, CircularTime.meanMinuteOfDay(listOf(660.0, 780.0))!!, 1e-6)
    }

    @Test
    fun `values are folded into one day`() {
        assertEquals(0.0, CircularTime.meanMinuteOfDay(listOf(1440.0))!!, 1e-6)
        assertEquals(0.0, CircularTime.meanMinuteOfDay(listOf(-60.0, 60.0))!!, 1e-6)
    }

    @Test
    fun `exactly opposite times have no meaningful mean`() {
        assertNull(CircularTime.meanMinuteOfDay(listOf(0.0, 720.0)))
    }

    @Test
    fun `three evenly spaced times have no meaningful mean`() {
        assertNull(CircularTime.meanMinuteOfDay(listOf(0.0, 480.0, 960.0)))
    }

    @Test
    fun `seconds become fractional minutes`() {
        assertEquals(90.5, CircularTime.minutesOfDay(LocalTime.of(1, 30, 30)), 1e-6)
    }
}
