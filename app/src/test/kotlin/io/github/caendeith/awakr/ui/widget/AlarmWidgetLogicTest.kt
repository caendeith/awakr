package io.github.caendeith.awakr.ui.widget

import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.alarm.Days
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class AlarmWidgetLogicTest {

    private val zone = ZoneId.of("Europe/Berlin")

    /** Monday, 2024-01-01, 10:00 local. */
    private val now = ZonedDateTime.of(2024, 1, 1, 10, 0, 0, 0, zone)

    private val words = AlarmWidgetLogic.Words(once = "Once", everyDay = "Every day")

    private fun state(alarms: List<Alarm>, at: ZonedDateTime = now) =
        AlarmWidgetLogic.state(alarms, at, Locale.US, words)

    @Test
    fun timeIsPaddedToTwoDigits() {
        assertEquals("07:05", AlarmWidgetLogic.formatTime(7, 5))
        assertEquals("00:00", AlarmWidgetLogic.formatTime(0, 0))
        assertEquals("23:59", AlarmWidgetLogic.formatTime(23, 59))
    }

    @Test
    fun oneShotAlarmSaysOnce() {
        assertEquals("Once", AlarmWidgetLogic.daysSummary(0, Locale.US, words))
    }

    @Test
    fun fullWeekSaysEveryDay() {
        assertEquals("Every day", AlarmWidgetLogic.daysSummary(Days.EVERY_DAY, Locale.US, words))
    }

    @Test
    fun partialWeekListsDaysInCalendarOrder() {
        val mask = Days.maskOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)
        assertEquals("Mon, Wed", AlarmWidgetLogic.daysSummary(mask, Locale.US, words))
    }

    @Test
    fun emptyListDrawsTheNoAlarmState() {
        val state = state(emptyList())
        assertFalse(state.hasAlarm)
        assertNull(state.alarmId)
        assertNull(state.ringsAt)
        assertFalse(state.canSkip)
        assertTrue(state.timeText.isEmpty())
        assertTrue(state.subtitle.isEmpty())
    }

    @Test
    fun theSoonestAlarmWins() {
        val tonight = Alarm(id = 1L, hour = 23, minute = 0, daysMask = 0)
        val tomorrow = Alarm(id = 2L, hour = 7, minute = 0, daysMask = Days.EVERY_DAY)

        val state = state(listOf(tomorrow, tonight))

        assertEquals(1L, state.alarmId)
        assertEquals("23:00", state.timeText)
        assertEquals("Once", state.subtitle)
        assertTrue(state.canSkip)
    }

    @Test
    fun labelPrefixedTheRepeatSummary() {
        val alarm = Alarm(id = 3L, hour = 7, minute = 30, daysMask = Days.EVERY_DAY, label = "Wake up")
        assertEquals("Wake up · Every day", state(listOf(alarm)).subtitle)
    }

    @Test
    fun blankLabelFallsBackToTheRepeatSummary() {
        val alarm = Alarm(id = 4L, hour = 7, minute = 30, daysMask = Days.EVERY_DAY, label = "   ")
        assertEquals("Every day", state(listOf(alarm)).subtitle)
    }

    @Test
    fun disabledAndDeletedAlarmsAreIgnored() {
        val off = Alarm(id = 5L, hour = 7, minute = 0, daysMask = Days.EVERY_DAY, enabled = false)
        val gone = Alarm(id = 6L, hour = 8, minute = 0, daysMask = Days.EVERY_DAY, deletedAt = 1L)

        assertFalse(state(listOf(off, gone)).hasAlarm)
        assertNull(AlarmWidgetLogic.nextOccurrence(off, now))
        assertNull(AlarmWidgetLogic.nextOccurrence(gone, now))
        assertNull(AlarmWidgetLogic.nextAlarm(listOf(off, gone), now))
    }

    @Test
    fun skippingAOneShotAlarmLeavesNothingToRing() {
        val at = ZonedDateTime.of(2024, 1, 1, 11, 0, 0, 0, zone)
        val alarm = Alarm(id = 7L, hour = 11, minute = 0, daysMask = 0, skipNextAt = at.toInstant().toEpochMilli())

        val state = state(listOf(alarm), ZonedDateTime.of(2024, 1, 1, 10, 0, 0, 0, zone))

        assertFalse(state.hasAlarm)
    }

    @Test
    fun skippingARepeatingAlarmMovesItByOneOccurrence() {
        val skipped = ZonedDateTime.of(2024, 1, 1, 7, 0, 0, 0, zone)
        val alarm = Alarm(
            id = 8L,
            hour = 7,
            minute = 0,
            daysMask = Days.EVERY_DAY,
            skipNextAt = skipped.toInstant().toEpochMilli(),
        )

        val state = state(listOf(alarm), ZonedDateTime.of(2024, 1, 1, 6, 0, 0, 0, zone))

        assertEquals("07:00", state.timeText)
        assertEquals(skipped.plusDays(1), state.ringsAt)
    }

    @Test
    fun modifiersDoNotChangeTheRingTime() {
        val alarm = Alarm(id = 9L, hour = 7, minute = 0, daysMask = Days.EVERY_DAY)
        val expected = now.toLocalDate().plusDays(1).atTime(7, 0).atZone(zone)

        assertEquals(expected, AlarmWidgetLogic.nextOccurrence(alarm, now))
        assertEquals(expected, state(listOf(alarm)).ringsAt)
    }
}
