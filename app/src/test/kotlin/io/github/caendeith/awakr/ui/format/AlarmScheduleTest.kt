package io.github.caendeith.awakr.ui.format

import io.github.caendeith.awakr.core.alarm.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmScheduleTest {

    private val zone: ZoneId = ZoneId.of("Europe/Samara")
    private val monday: ZonedDateTime = ZonedDateTime.of(2026, 11, 2, 6, 0, 0, 0, zone)
    private val everyDay: Int = (1 shl 7) - 1

    private fun alarm(skipNextAt: Long? = null): Alarm =
        Alarm(id = 1L, hour = 7, minute = 30, daysMask = everyDay, skipNextAt = skipNextAt)

    @Test
    fun nextIsTheFirstOccurrenceWithoutASkip() {
        val schedule = scheduleOf(alarm(), monday)
        assertEquals(ZonedDateTime.of(2026, 11, 2, 7, 30, 0, 0, zone), schedule.next)
    }

    @Test
    fun aDisabledAlarmHasNoNextOccurrence() {
        assertNull(scheduleOf(alarm().copy(enabled = false), monday).next)
    }

    @Test
    fun aSkippedOccurrenceMovesTheCardAndTheOrder() {
        // The scheduler honours skipNextAt, so the card has to as well, or the hero would show a
        // moment that never rings.
        val skipped = ZonedDateTime.of(2026, 11, 2, 7, 30, 0, 0, zone)
        val schedule = scheduleOf(alarm(skipNextAt = skipped.toInstant().toEpochMilli()), monday)
        assertEquals(ZonedDateTime.of(2026, 11, 3, 7, 30, 0, 0, zone), schedule.next)
    }

    @Test
    fun skippingTheOnlyOccurrenceOfAOneShotAlarmLeavesItWithoutANext() {
        val at = ZonedDateTime.of(2026, 11, 2, 7, 30, 0, 0, zone)
        val oneShot = Alarm(id = 2L, hour = 7, minute = 30, daysMask = 0, skipNextAt = at.toInstant().toEpochMilli())
        assertNull(scheduleOf(oneShot, monday).next)
    }

    @Test
    fun schedulesAreOrderedByTheMomentTheyRing() {
        // 05:00 is before both alarms, so the 06:00 one rings first and has to come first.
        val now = ZonedDateTime.of(2026, 11, 2, 5, 0, 0, 0, zone)
        val later = Alarm(id = 3L, hour = 6, minute = 0, daysMask = everyDay)
        val sooner = Alarm(id = 4L, hour = 7, minute = 0, daysMask = everyDay)
        val ordered = schedulesOf(listOf(sooner, later), now)
        assertEquals(listOf(3L, 4L), ordered.map { it.alarm.id })
    }
}