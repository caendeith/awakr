package io.github.caendeith.awakr.ui.ring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionTimerTest {

    @Test
    fun formatsMinutesAndSeconds() {
        assertEquals("1:30", MissionTimer.format(90_000L))
        assertEquals("1:29", MissionTimer.format(89_000L))
        assertEquals("0:07", MissionTimer.format(7_000L))
        assertEquals("0:00", MissionTimer.format(0L))
    }

    @Test
    fun partSecondsRoundUpSoTheCounterStartsFull() {
        assertEquals("1:30", MissionTimer.format(89_500L))
        assertEquals("0:01", MissionTimer.format(1L))
    }

    @Test
    fun remainingTimeNeverGoesNegative() {
        assertEquals("0:00", MissionTimer.format(-5_000L))
        assertEquals(0L, MissionTimer.remainingMillis(deadline = 1_000L, now = 4_000L))
    }

    @Test
    fun aFreshDeadlineIsExactlyTheMissionDuration() {
        val now = 1_700_000_000_000L
        val deadline = MissionTimer.deadlineFrom(now)
        assertEquals(MissionTimer.DURATION_MILLIS, MissionTimer.remainingMillis(deadline, now))
        assertEquals(90, MissionTimer.DURATION_SECONDS)
        assertEquals("1:30", MissionTimer.format(MissionTimer.remainingMillis(deadline, now)))
    }

    @Test
    fun everyMissionButTheQrScannerCanBeReset() {
        assertTrue(MissionTimer.isResettable("math"))
        assertTrue(MissionTimer.isResettable("typing"))
        assertFalse(MissionTimer.isResettable("code"))
        assertFalse(MissionTimer.isResettable(null))
    }
}
