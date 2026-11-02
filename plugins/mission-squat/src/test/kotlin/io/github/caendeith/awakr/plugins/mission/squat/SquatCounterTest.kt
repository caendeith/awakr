package io.github.caendeith.awakr.plugins.mission.squat

import org.junit.Assert.assertEquals
import org.junit.Test

class SquatCounterTest {

    @Test
    fun standingStillCountsNothing() {
        val counter = SquatCounter()
        var count = 0
        repeat(50) { index -> count = counter.onSample(0f, 0f, SquatCounter.GRAVITY, index * 20L) }
        assertEquals(0, count)
    }

    @Test
    fun oneUpwardPeakCountsOneSquat() {
        val counter = SquatCounter()
        counter.onSample(0f, 0f, SquatCounter.GRAVITY, 0L)
        // Body drops: below gravity.
        counter.onSample(0f, 0f, SquatCounter.GRAVITY - 4f, 200L)
        // Way up: above gravity.
        assertEquals(1, counter.onSample(0f, 0f, SquatCounter.GRAVITY + 4f, 400L))
    }

    @Test
    fun aSustainedPeakCountsOnce() {
        val counter = SquatCounter()
        var count = 0
        for (index in 1..20) {
            count = counter.onSample(0f, 0f, SquatCounter.GRAVITY + 6f, index * 100L)
        }
        assertEquals(1, count)
    }

    @Test
    fun refractoryBlocksRapidPeaks() {
        val counter = SquatCounter(refractoryMillis = 450L)
        counter.onSample(0f, 0f, SquatCounter.GRAVITY, 0L)
        assertEquals(1, counter.onSample(0f, 0f, SquatCounter.GRAVITY + 5f, 100L))
        counter.onSample(0f, 0f, SquatCounter.GRAVITY, 150L)
        // 200 ms after the first peak: still inside the refractory window.
        assertEquals(1, counter.onSample(0f, 0f, SquatCounter.GRAVITY + 5f, 300L))
        counter.onSample(0f, 0f, SquatCounter.GRAVITY, 350L)
        assertEquals(2, counter.onSample(0f, 0f, SquatCounter.GRAVITY + 5f, 600L))
    }

    @Test
    fun ignoresTheDownwardHalfOfASquat() {
        val counter = SquatCounter()
        var count = 0
        for (index in 1..10) {
            count = counter.onSample(0f, 0f, SquatCounter.GRAVITY - 5f, index * 100L)
        }
        assertEquals(0, count)
    }

    @Test
    fun countsATrainOfSquats() {
        val counter = SquatCounter()
        var count = 0
        var now = 0L
        repeat(5) {
            counter.onSample(0f, 0f, SquatCounter.GRAVITY - 3f, now)
            now += 300L
            count = counter.onSample(0f, 0f, SquatCounter.GRAVITY + 3f, now)
            now += 700L
        }
        assertEquals(5, count)
    }

    @Test
    fun resetClearsProgress() {
        val counter = SquatCounter()
        assertEquals(1, counter.onSample(0f, 0f, SquatCounter.GRAVITY + 5f, 100L))
        counter.reset()
        assertEquals(0, counter.count)
        // After a reset the counter behaves like a fresh one: the next peak counts again.
        counter.onSample(0f, 0f, SquatCounter.GRAVITY, 900L)
        assertEquals(1, counter.onSample(0f, 0f, SquatCounter.GRAVITY + 5f, 1_000L))
    }
}
