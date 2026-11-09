package io.github.caendeith.awakr.ui.sleep

import io.github.caendeith.awakr.data.sleep.SleepSession
import io.github.caendeith.awakr.data.sleep.SleepStage
import io.github.caendeith.awakr.data.sleep.SleepStageType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepWeekTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 9, 12, 0, 0, 0, zone)

    private fun night(date: LocalDate, hours: Double = 8.0): SleepSession {
        val wake = date.atTime(7, 0).atZone(zone)
        val start = wake.minusMinutes((hours * 60).toLong())
        return SleepSession(
            id = start.toInstant().toEpochMilli().toString(),
            startTime = start.toInstant().toEpochMilli(),
            endTime = wake.toInstant().toEpochMilli(),
            source = "test",
        )
    }

    @Test
    fun `empty history yields empty days but a fixed grid`() {
        val week = SleepWeekCalculator.compute(emptyList(), now)

        assertEquals(7, week.days.size)
        assertTrue(week.days.all { it.night == null })
        assertNull(week.averageAsleepMillis)
        assertEquals(0, week.streakNights)
        assertNull(week.lastNight)
    }

    @Test
    fun `days run oldest first and end today`() {
        val week = SleepWeekCalculator.compute(emptyList(), now)

        assertEquals(LocalDate.of(2026, 11, 3), week.days.first().date)
        assertEquals(LocalDate.of(2026, 11, 9), week.days.last().date)
    }

    @Test
    fun `a night is grouped by the local wake-up date`() {
        val start = ZonedDateTime.of(2026, 11, 8, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(2026, 11, 9, 7, 0, 0, 0, zone).toInstant().toEpochMilli()
        val week = SleepWeekCalculator.compute(
            listOf(SleepSession(id = start.toString(), startTime = start, endTime = end, source = "test")),
            now,
        )

        val day = week.days.single { it.date == LocalDate.of(2026, 11, 9) }
        assertEquals(LocalTime.of(7, 0), day.night?.wakeUp)
        assertEquals(LocalTime.of(23, 30), day.night?.bedtime)
    }

    @Test
    fun `sessions outside the window are ignored`() {
        val week = SleepWeekCalculator.compute(
            listOf(
                night(LocalDate.of(2026, 10, 31)),
                night(LocalDate.of(2026, 11, 8)),
            ),
            now,
        )

        assertEquals(1, week.nights.size)
        assertEquals(LocalDate.of(2026, 11, 8), week.nights.single().date)
    }

    @Test
    fun `future sessions are ignored`() {
        val week = SleepWeekCalculator.compute(listOf(night(LocalDate.of(2026, 11, 10))), now)

        assertTrue(week.nights.isEmpty())
    }

    @Test
    fun `the longer session wins when two end on the same date`() {
        val day = LocalDate.of(2026, 11, 9)
        val wake = day.atTime(7, 0).atZone(zone)
        val napStart = day.atTime(15, 0).atZone(zone)
        val napEnd = day.atTime(16, 0).atZone(zone)
        val properStart = wake.minusHours(8)

        val week = SleepWeekCalculator.compute(
            listOf(
                SleepSession(
                    id = "nap",
                    startTime = napStart.toInstant().toEpochMilli(),
                    endTime = napEnd.toInstant().toEpochMilli(),
                ),
                SleepSession(
                    id = "proper",
                    startTime = properStart.toInstant().toEpochMilli(),
                    endTime = wake.toInstant().toEpochMilli(),
                ),
            ),
            now,
        )

        assertEquals(1, week.nights.size)
        assertEquals(8 * 60 * 60 * 1000L, week.nights.single().asleepMillis)
    }

    @Test
    fun `average uses the nights that were imported`() {
        val week = SleepWeekCalculator.compute(
            listOf(
                night(LocalDate.of(2026, 11, 8), hours = 8.0),
                night(LocalDate.of(2026, 11, 9), hours = 7.5),
            ),
            now,
        )

        assertEquals(7 * 60 + 45, java.time.Duration.ofMillis(week.averageAsleepMillis!!).toMinutes())
    }

    @Test
    fun `a streak counts consecutive nights back from today`() {
        val week = SleepWeekCalculator.compute(
            listOf(
                night(LocalDate.of(2026, 11, 7)),
                night(LocalDate.of(2026, 11, 8)),
                night(LocalDate.of(2026, 11, 9)),
            ),
            now,
        )

        assertEquals(3, week.streakNights)
    }

    @Test
    fun `the streak starts yesterday when today has no night yet`() {
        val week = SleepWeekCalculator.compute(
            listOf(
                night(LocalDate.of(2026, 11, 7)),
                night(LocalDate.of(2026, 11, 8)),
            ),
            now,
        )

        assertEquals(2, week.streakNights)
    }

    @Test
    fun `a gap breaks the streak`() {
        val week = SleepWeekCalculator.compute(
            listOf(
                night(LocalDate.of(2026, 11, 4)),
                night(LocalDate.of(2026, 11, 8)),
                night(LocalDate.of(2026, 11, 9)),
            ),
            now,
        )

        assertEquals(2, week.streakNights)
    }

    @Test
    fun `the streak is capped by the window`() {
        val history = (3..9).map { day -> night(LocalDate.of(2026, 11, day)) }

        val week = SleepWeekCalculator.compute(history, now)

        assertEquals(7, week.streakNights)
    }

    @Test
    fun `a session without stages falls back to its duration`() {
        val week = SleepWeekCalculator.compute(listOf(night(LocalDate.of(2026, 11, 9), hours = 6.0)), now)

        assertEquals(6 * 60 * 60 * 1000L, week.lastNight?.asleepMillis)
    }

    @Test
    fun `stage asleep time excludes awake periods`() {
        val start = ZonedDateTime.of(2026, 11, 8, 23, 0, 0, 0, zone).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(2026, 11, 9, 7, 0, 0, 0, zone).toInstant().toEpochMilli()
        val session = SleepSession(
            id = start.toString(),
            startTime = start,
            endTime = end,
            stages = listOf(
                SleepStage(SleepStageType.AWAKE, start, start + 60 * 60_000L),
                SleepStage(SleepStageType.LIGHT, start + 60 * 60_000L, end),
            ),
            source = "test",
        )

        val week = SleepWeekCalculator.compute(listOf(session), now)

        assertEquals(7 * 60 * 60 * 1000L, week.lastNight?.asleepMillis)
    }

    @Test
    fun `invalid sessions are ignored`() {
        val week = SleepWeekCalculator.compute(
            listOf(SleepSession(id = "bad", startTime = 1000, endTime = 1000)),
            now,
        )

        assertTrue(week.nights.isEmpty())
    }
}
