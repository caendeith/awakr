package io.github.caendeith.awakr.data.stats

import io.github.caendeith.awakr.core.alarm.AlarmLog
import io.github.caendeith.awakr.core.alarm.AlarmOutcome
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    /** Monday evening: "today", the six previous days and the month boundary are stable. */
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 2, 18, 0, 0, 0, zone)

    private fun timestamp(date: String, hour: Int, minute: Int): Long =
        LocalDate.parse(date).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun log(
        scheduledAt: Long,
        outcome: AlarmOutcome,
        firedAt: Long? = null,
        dismissedAt: Long? = null,
        snoozeCount: Int = 0,
        alarmId: Long = 1L,
    ): AlarmLog = AlarmLog(
        alarmId = alarmId,
        scheduledAt = scheduledAt,
        firedAt = firedAt,
        dismissedAt = dismissedAt,
        snoozeCount = snoozeCount,
        outcome = outcome,
    )

    private fun compute(logs: List<AlarmLog>, range: StatsRange = StatsRange.WEEK): StatsSummary =
        StatsCalculator.compute(logs, range, now)

    @Test
    fun `empty history has a full window and no numbers`() {
        val summary = compute(emptyList())
        assertEquals(7, summary.days.size)
        assertEquals(LocalDate.parse("2026-10-27"), summary.days.first().date)
        assertEquals(LocalDate.parse("2026-11-02"), summary.days.last().date)
        assertTrue(summary.isEmpty)
        assertEquals(0, summary.totalEntries)
        assertNull(summary.successRate)
        assertNull(summary.averageWakeUpMinuteOfDay)
        assertEquals(0, summary.streakDays)
    }

    @Test
    fun `month range always has thirty day slots`() {
        val summary = compute(emptyList(), StatsRange.MONTH)
        assertEquals(30, summary.days.size)
        assertEquals(LocalDate.parse("2026-10-04"), summary.days.first().date)
        assertEquals(LocalDate.parse("2026-11-02"), summary.days.last().date)
    }

    @Test
    fun `counts only rows inside the selected window`() {
        val logs = listOf(
            log(timestamp("2026-11-01", 7, 0), AlarmOutcome.DISMISSED),
            log(timestamp("2026-10-20", 7, 0), AlarmOutcome.DISMISSED),
            log(timestamp("2026-10-31", 7, 0), AlarmOutcome.MISSED),
            log(timestamp("2026-10-30", 7, 0), AlarmOutcome.SNOOZED, snoozeCount = 2),
            log(timestamp("2026-09-01", 7, 0), AlarmOutcome.DISMISSED),
        )

        val week = compute(logs)
        assertEquals(1, week.successfulWakeUps)
        assertEquals(1, week.missed)
        // One SNOOZED row, even though its stored counter says 2: counters run per chain.
        assertEquals(1, week.snoozes)
        assertEquals(3, week.totalEntries)

        val month = compute(logs, StatsRange.MONTH)
        assertEquals(2, month.successfulWakeUps)
        assertEquals(1, month.missed)
        assertEquals(1, month.snoozes)
        assertEquals(4, month.totalEntries)
    }

    @Test
    fun `snoozes count presses, not the running counter`() {
        // One chain: the second SNOOZED row stores the running total 2, but it was only the
        // second press. Summing the field would report three snoozes for two presses.
        val logs = listOf(
            log(timestamp("2026-11-01", 7, 0), AlarmOutcome.SNOOZED, snoozeCount = 1),
            log(timestamp("2026-11-01", 7, 10), AlarmOutcome.SNOOZED, snoozeCount = 2),
            log(timestamp("2026-11-01", 7, 20), AlarmOutcome.DISMISSED),
        )
        assertEquals(2, compute(logs).snoozes)
    }

    @Test
    fun `success rate divides dismissals by finished attempts`() {
        val logs = listOf(
            log(timestamp("2026-11-01", 7, 0), AlarmOutcome.DISMISSED),
            log(timestamp("2026-10-31", 7, 0), AlarmOutcome.DISMISSED),
            log(timestamp("2026-10-30", 7, 0), AlarmOutcome.MISSED),
            log(timestamp("2026-10-29", 7, 0), AlarmOutcome.MISSED),
            log(timestamp("2026-10-28", 7, 0), AlarmOutcome.SNOOZED, snoozeCount = 3),
        )
        assertEquals(0.5f, compute(logs).successRate!!, 0.0001f)
    }

    @Test
    fun `success rate is unknown while nothing has finished`() {
        val logs = listOf(
            log(timestamp("2026-11-01", 7, 0), AlarmOutcome.SNOOZED, snoozeCount = 1),
            log(timestamp("2026-10-31", 7, 0), AlarmOutcome.ERROR),
        )
        val summary = compute(logs)
        assertEquals(2, summary.totalEntries)
        assertNull(summary.successRate)
    }

    @Test
    fun `average wake-up time wraps around midnight`() {
        val logs = listOf(
            log(
                timestamp("2026-11-01", 23, 50),
                AlarmOutcome.DISMISSED,
                firedAt = timestamp("2026-11-01", 23, 50),
                dismissedAt = timestamp("2026-11-01", 23, 50),
            ),
            log(
                timestamp("2026-11-02", 0, 10),
                AlarmOutcome.DISMISSED,
                firedAt = timestamp("2026-11-02", 0, 10),
                dismissedAt = timestamp("2026-11-02", 0, 10),
            ),
        )
        assertEquals(0.0, compute(logs).averageWakeUpMinuteOfDay!!, 1e-6)
    }

    @Test
    fun `average wake-up time uses the dismissal moment`() {
        val logs = listOf(
            log(
                timestamp("2026-11-01", 7, 0),
                AlarmOutcome.DISMISSED,
                firedAt = timestamp("2026-11-01", 7, 0),
                dismissedAt = timestamp("2026-11-01", 7, 42),
            ),
        )
        assertEquals(7 * 60 + 42.0, compute(logs).averageWakeUpMinuteOfDay!!, 1e-6)
    }

    @Test
    fun `average wake-up time falls back to when the alarm rang`() {
        val logs = listOf(
            log(
                timestamp("2026-11-01", 7, 15),
                AlarmOutcome.DISMISSED,
                firedAt = timestamp("2026-11-01", 7, 15),
            ),
        )
        assertEquals(7 * 60 + 15.0, compute(logs).averageWakeUpMinuteOfDay!!, 1e-6)
    }

    @Test
    fun `per-day chart has one slot per day and uses the dismissal time`() {
        val logs = listOf(
            log(
                timestamp("2026-10-28", 6, 30),
                AlarmOutcome.DISMISSED,
                dismissedAt = timestamp("2026-10-28", 6, 30),
            ),
            log(
                timestamp("2026-10-31", 23, 55),
                AlarmOutcome.DISMISSED,
                dismissedAt = timestamp("2026-11-01", 0, 5),
            ),
        )
        val days = compute(logs).days

        assertEquals(7, days.size)
        assertNull(days.first { it.date == LocalDate.parse("2026-10-27") }.wakeUpMinuteOfDay)
        assertEquals(6 * 60 + 30.0, days.first { it.date == LocalDate.parse("2026-10-28") }.wakeUpMinuteOfDay!!, 1e-6)
        assertNull(days.first { it.date == LocalDate.parse("2026-10-30") }.wakeUpMinuteOfDay)

        // The dismissal after midnight still belongs to the evening it woke the user up from.
        val evening = days.first { it.date == LocalDate.parse("2026-10-31") }
        assertEquals(5.0, evening.wakeUpMinuteOfDay!!, 1e-6)
        assertEquals(1, evening.successfulWakeUps)
        assertEquals(0, days.first { it.date == LocalDate.parse("2026-11-01") }.successfulWakeUps)
    }

    @Test
    fun `streak counts consecutive days ending today`() {
        val logs = listOf(
            log(timestamp("2026-11-02", 7, 0), AlarmOutcome.DISMISSED),
            log(timestamp("2026-11-01", 7, 0), AlarmOutcome.DISMISSED),
            log(timestamp("2026-10-31", 7, 0), AlarmOutcome.DISMISSED),
            log(timestamp("2026-10-29", 7, 0), AlarmOutcome.DISMISSED),
        )
        assertEquals(3, compute(logs).streakDays)
    }

    @Test
    fun `streak survives a day whose alarm has not rung yet`() {
        val logs = listOf(
            log(timestamp("2026-11-01", 7, 0), AlarmOutcome.DISMISSED),
            log(timestamp("2026-10-31", 7, 0), AlarmOutcome.DISMISSED),
        )
        assertEquals(2, compute(logs).streakDays)
    }

    @Test
    fun `streak stops at the first missed day`() {
        val logs = listOf(
            log(timestamp("2026-11-02", 7, 0), AlarmOutcome.DISMISSED),
            log(timestamp("2026-11-01", 7, 0), AlarmOutcome.MISSED),
            log(timestamp("2026-10-31", 7, 0), AlarmOutcome.DISMISSED),
        )
        assertEquals(1, compute(logs).streakDays)
    }

    @Test
    fun `streak never reaches past the selected window`() {
        // Eight consecutive dismissed days, but the week window can only see seven of them.
        val logs = (0 until 8).map { offset ->
            log(
                timestamp("2026-11-02", 7, 0) - offset * 24L * 60L * 60L * 1000L,
                AlarmOutcome.DISMISSED,
            )
        }
        assertEquals(7, compute(logs).streakDays)
    }
}
