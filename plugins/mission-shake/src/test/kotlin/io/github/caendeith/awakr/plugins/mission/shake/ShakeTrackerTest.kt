package io.github.caendeith.awakr.plugins.mission.shake

import org.junit.Assert.assertEquals
import org.junit.Test

class ShakeTrackerTest {

    @Test
    fun ignoresSamplesBelowThreshold() {
        val tracker = ShakeTracker()
        var total = 0L
        repeat(10) { index -> total = tracker.onSample(0f, 0f, 9.8f, index * 20L) }
        assertEquals(0L, total)
    }

    @Test
    fun accumulatesElapsedTimeWhileShaking() {
        val tracker = ShakeTracker()
        tracker.onSample(0f, 0f, 9.8f, 0L)
        var total = 0L
        for (index in 1..10) {
            total = tracker.onSample(0f, 30f, 0f, index * 50L)
        }
        assertEquals(500L, total)
    }

    @Test
    fun clampsLongGapsBetweenSamples() {
        val tracker = ShakeTracker()
        tracker.onSample(20f, 0f, 0f, 0L)
        val total = tracker.onSample(20f, 0f, 0f, 5_000L)
        assertEquals(ShakeTracker.DEFAULT_MAX_FRAME_MILLIS, total)
    }

    @Test
    fun doesNotCountTimeBeforeFirstSample() {
        val tracker = ShakeTracker()
        val first = tracker.onSample(25f, 25f, 25f, 1_000L)
        assertEquals(0L, first)
    }

    @Test
    fun resetClearsProgress() {
        val tracker = ShakeTracker()
        tracker.onSample(0f, 0f, 9.8f, 0L)
        tracker.onSample(0f, 40f, 0f, 100L)
        tracker.reset()
        assertEquals(0L, tracker.elapsedMillis)
        assertEquals(0L, tracker.onSample(0f, 40f, 0f, 200L))
    }
}
