package io.github.caendeith.awakr.alarm

import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.alarm.AlarmLog
import io.github.caendeith.awakr.core.alarm.AlarmOutcome
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissedAlarmsTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun alarm(
        id: Long = 1L,
        hour: Int = 7,
        minute: Int = 30,
        daysMask: Int = 0,
        enabled: Boolean = true,
        createdAt: Long = 0L,
        updatedAt: Long = 0L,
    ) = Alarm(
        id = id,
        hour = hour,
        minute = minute,
        daysMask = daysMask,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun log(alarmId: Long, scheduledAt: Long) = AlarmLog(
        alarmId = alarmId,
        scheduledAt = scheduledAt,
        outcome = AlarmOutcome.DISMISSED,
    )

    @Test
    fun occurrenceWithoutLogIsMissed() {
        val now = at(2026, 3, 2, 9, 0)
        val missed = MissedAlarms.find(listOf(alarm()), emptyList(), now, zone)
        assertEquals(1, missed.size)
        assertEquals(at(2026, 3, 2, 7, 30), missed.first().scheduledAt)
    }

    @Test
    fun loggedOccurrenceIsNotMissed() {
        val now = at(2026, 3, 2, 9, 0)
        val logs = listOf(log(1L, at(2026, 3, 2, 7, 30)))
        assertTrue(MissedAlarms.find(listOf(alarm()), logs, now, zone).isEmpty())
    }

    @Test
    fun logWrittenALittleLateStillCounts() {
        val now = at(2026, 3, 2, 9, 0)
        // The ring service logs the occurrence a minute after the scheduled minute.
        val logs = listOf(log(1L, at(2026, 3, 2, 7, 30) + 60_000L))
        assertTrue(MissedAlarms.find(listOf(alarm()), logs, now, zone).isEmpty())
    }

    @Test
    fun yesterdayIsTooOldToReport() {
        val now = at(2026, 3, 2, 9, 0)
        val missed = MissedAlarms.find(
            alarms = listOf(alarm(hour = 6, minute = 0)),
            logs = emptyList(),
            nowMillis = now,
            zone = zone,
            maxAgeMillis = 2L * 60L * 60L * 1000L,
        )
        assertTrue(missed.isEmpty())
    }

    @Test
    fun disabledAlarmsAreIgnored() {
        val now = at(2026, 3, 2, 9, 0)
        val missed = MissedAlarms.find(listOf(alarm(enabled = false)), emptyList(), now, zone)
        assertTrue(missed.isEmpty())
    }

    @Test
    fun repeatMaskDecidesWhichDayCounts() {
        // 2026-03-02 is a Monday; a Sunday-only alarm last rang on 2026-03-01.
        val now = at(2026, 3, 2, 9, 0)
        val sundayOnly = alarm(daysMask = 0b1000000)
        // Sunday 07:30 was 25.5 hours before "now", so the age window must be widened here.
        val window = 48L * 60L * 60L * 1000L
        val missed = MissedAlarms.find(
            alarms = listOf(sundayOnly),
            logs = listOf(log(1L, at(2026, 3, 1, 7, 30))),
            nowMillis = now,
            zone = zone,
            maxAgeMillis = window,
        )
        assertTrue(missed.isEmpty())

        val withoutLog = MissedAlarms.find(
            alarms = listOf(sundayOnly),
            logs = emptyList(),
            nowMillis = now,
            zone = zone,
            maxAgeMillis = window,
        )
        assertEquals(at(2026, 3, 1, 7, 30), withoutLog.single().scheduledAt)
    }

    @Test
    fun occurrenceOlderThanTheAlarmItselfIsNotMissed() {
        val now = at(2026, 3, 2, 9, 0)
        val created = alarm(createdAt = at(2026, 3, 2, 8, 30))
        assertTrue(MissedAlarms.find(listOf(created), emptyList(), now, zone).isEmpty())

        val saved = alarm(updatedAt = at(2026, 3, 2, 8, 45))
        assertTrue(MissedAlarms.find(listOf(saved), emptyList(), now, zone).isEmpty())
    }

    @Test
    fun resultsAreOrderedByScheduledTime() {
        val now = at(2026, 3, 2, 12, 0)
        val missed = MissedAlarms.find(
            alarms = listOf(alarm(id = 2L, hour = 11, minute = 0), alarm(id = 1L, hour = 8, minute = 0)),
            logs = emptyList(),
            nowMillis = now,
            zone = zone,
        )
        assertEquals(listOf(1L, 2L), missed.map { it.alarm.id })
    }
}