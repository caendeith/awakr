package io.github.caendeith.awakr.plugins.mission.step

import org.junit.Assert.assertEquals
import org.junit.Test

class StepCounterTest {

    /** Simulates one step: a positive peak followed by the heel-strike trough 120 ms later. */
    private fun step(counter: StepCounter, startMillis: Long): Int {
        counter.onSample(0f, 0f, StepCounter.GRAVITY, startMillis)
        counter.onSample(0f, 0f, StepCounter.GRAVITY + 4f, startMillis + 20L)
        counter.onSample(0f, 0f, StepCounter.GRAVITY - 4f, startMillis + 140L)
        counter.onSample(0f, 0f, StepCounter.GRAVITY, startMillis + 260L)
        return counter.count
    }

    @Test
    fun standingStillCountsNothing() {
        val counter = StepCounter()
        var count = 0
        repeat(100) { index ->
            count = counter.onSample(0f, 0f, StepCounter.GRAVITY + 0.5f, index * 20L)
        }
        assertEquals(0, count)
    }

    @Test
    fun oneStepCountsOnce() {
        val counter = StepCounter()
        assertEquals(1, step(counter, 0L))
    }

    @Test
    fun heelStrikeTroughDoesNotCount() {
        val counter = StepCounter()
        step(counter, 0L)
        // The trough of the same step arrives inside the refractory window.
        assertEquals(1, counter.onSample(0f, 0f, StepCounter.GRAVITY - 6f, 200L))
    }

    @Test
    fun countsAWalk() {
        val counter = StepCounter()
        var count = 0
        repeat(12) { index ->
            count = step(counter, index * 550L)
        }
        assertEquals(12, count)
    }

    @Test
    fun refractoryBlocksImpossiblyFastSteps() {
        val counter = StepCounter(refractoryMillis = 280L)
        assertEquals(1, counter.onSample(0f, 0f, StepCounter.GRAVITY + 5f, 0L))
        counter.onSample(0f, 0f, StepCounter.GRAVITY, 40L)
        // 150 ms later: faster than any human step.
        assertEquals(1, counter.onSample(0f, 0f, StepCounter.GRAVITY + 5f, 150L))
        counter.onSample(0f, 0f, StepCounter.GRAVITY, 200L)
        assertEquals(2, counter.onSample(0f, 0f, StepCounter.GRAVITY + 5f, 350L))
    }

    @Test
    fun noiseBelowTheThresholdIsIgnored() {
        val counter = StepCounter()
        repeat(20) { index ->
            counter.onSample(0f, 0f, StepCounter.GRAVITY + 1.5f, index * 100L)
        }
        assertEquals(0, counter.count)
    }

    @Test
    fun resetClearsProgress() {
        val counter = StepCounter()
        assertEquals(1, step(counter, 0L))
        counter.reset()
        assertEquals(0, counter.count)
        // After a reset the counter behaves like a fresh one: the next step counts again.
        assertEquals(1, step(counter, 2_000L))
    }
}
