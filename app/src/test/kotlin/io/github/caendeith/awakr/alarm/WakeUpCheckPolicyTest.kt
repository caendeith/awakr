package io.github.caendeith.awakr.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeUpCheckPolicyTest {

    private val checkAt = 1_700_000_000_000L

    private fun check(
        alarmId: Long = 7L,
        checkAt: Long = this.checkAt,
        snoozeCount: Int = 0,
        time: String = "07:30",
        label: String = "",
    ) = PendingWakeUpCheck(
        alarmId = alarmId,
        checkAt = checkAt,
        escalateAt = WakeUpCheckPolicy.escalationAt(checkAt),
        snoozeCount = snoozeCount,
        time = time,
        label = label,
    )

    @Test
    fun offAndNegativeValuesSwitchTheCheckOff() {
        assertEquals(0, WakeUpCheckPolicy.normalizeMinutes(0))
        assertEquals(0, WakeUpCheckPolicy.normalizeMinutes(-5))
        assertEquals(0, WakeUpCheckPolicy.DEFAULT_MINUTES)
    }

    @Test
    fun supportedValuesStayUntouched() {
        WakeUpCheckPolicy.OPTIONS_MINUTES.forEach { minutes ->
            assertEquals(minutes, WakeUpCheckPolicy.normalizeMinutes(minutes))
        }
    }

    @Test
    fun delayRoundsUpToTheNextOption() {
        // Rounding up keeps a stray value from firing the check earlier than the user asked.
        assertEquals(3, WakeUpCheckPolicy.normalizeMinutes(2))
        assertEquals(5, WakeUpCheckPolicy.normalizeMinutes(4))
        assertEquals(20, WakeUpCheckPolicy.normalizeMinutes(19))
    }

    @Test
    fun delayAboveTheLargestOptionIsCapped() {
        assertEquals(30, WakeUpCheckPolicy.normalizeMinutes(31))
        assertEquals(30, WakeUpCheckPolicy.normalizeMinutes(1_000))
    }

    @Test
    fun escalationIsHundredSecondsAfterTheCheck() {
        assertEquals(checkAt + 100_000L, WakeUpCheckPolicy.escalationAt(checkAt))
    }

    @Test
    fun checkExpiresExactlyAtTheDeadline() {
        val deadline = WakeUpCheckPolicy.escalationAt(checkAt)
        assertFalse(WakeUpCheckPolicy.isExpired(checkAt, deadline - 1L))
        assertTrue(WakeUpCheckPolicy.isExpired(checkAt, deadline))
        assertTrue(WakeUpCheckPolicy.isExpired(checkAt, deadline + 60_000L))
    }

    @Test
    fun planBeforeTheCheckMomentArmsBothTriggers() {
        val plan = WakeUpCheckPolicy.plan(checkAt, WakeUpCheckPolicy.escalationAt(checkAt), checkAt - 5_000L)
        assertNotNull(plan)
        assertEquals(checkAt, plan?.checkAt)
        assertEquals(WakeUpCheckPolicy.escalationAt(checkAt), plan?.escalateAt)
    }

    @Test
    fun planAfterTheCheckMomentKeepsOnlyTheReRing() {
        // The notification moment passed while the device was off: the re-ring is all that is left.
        val now = checkAt + 30_000L
        val plan = WakeUpCheckPolicy.plan(checkAt, WakeUpCheckPolicy.escalationAt(checkAt), now)
        assertNotNull(plan)
        assertNull(plan?.checkAt)
        assertEquals(WakeUpCheckPolicy.escalationAt(checkAt), plan?.escalateAt)
    }

    @Test
    fun planAfterTheDeadlineIsDropped() {
        val deadline = WakeUpCheckPolicy.escalationAt(checkAt)
        assertNull(WakeUpCheckPolicy.plan(checkAt, deadline, deadline))
        assertNull(WakeUpCheckPolicy.plan(checkAt, deadline, deadline + 1L))
    }

    @Test
    fun lateEscalationIsStaleOnlyAfterTheGrace() {
        val deadline = WakeUpCheckPolicy.escalationAt(checkAt)
        assertFalse(WakeUpCheckPolicy.isStale(deadline, deadline))
        assertFalse(WakeUpCheckPolicy.isStale(deadline, deadline + WakeUpCheckPolicy.STALE_GRACE_MS))
        assertTrue(WakeUpCheckPolicy.isStale(deadline, deadline + WakeUpCheckPolicy.STALE_GRACE_MS + 1L))
    }

    @Test
    fun encodedCheckRoundTrips() {
        val original = check(snoozeCount = 2, label = "Gym | Monday · зал")
        assertEquals(original, WakeUpCheckPolicy.decode(WakeUpCheckPolicy.encode(original)))
    }

    @Test
    fun encodedCheckSurvivesAnEmptyLabel() {
        val original = check(label = "")
        assertEquals(original, WakeUpCheckPolicy.decode(WakeUpCheckPolicy.encode(original)))
    }

    @Test
    fun decodingRejectsGarbage() {
        assertNull(WakeUpCheckPolicy.decode(null))
        assertNull(WakeUpCheckPolicy.decode(""))
        assertNull(WakeUpCheckPolicy.decode("nonsense"))
        assertNull(WakeUpCheckPolicy.decode("1|2|3|4|07:30"))
        assertNull(WakeUpCheckPolicy.decode("0|1700000000000|1700000100000|0|07:30|"))
        assertNull(WakeUpCheckPolicy.decode("1|x|1700000100000|0|07:30|"))
    }
}
