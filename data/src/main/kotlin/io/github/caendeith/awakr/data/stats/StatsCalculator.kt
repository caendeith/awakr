package io.github.caendeith.awakr.data.stats

import io.github.caendeith.awakr.core.alarm.AlarmLog
import io.github.caendeith.awakr.core.alarm.AlarmOutcome
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Folds `alarm_logs` rows into the numbers shown on the statistics screen.
 *
 * Pure and time-zone explicit so it can be unit-tested without Android. Every row belongs
 * to the day its occurrence was *scheduled* for, while the wake-up time comes from the
 * moment the user actually dismissed the alarm: a 23:55 alarm dismissed at 00:02 stays on
 * its evening's day and plots just after midnight.
 */
object StatsCalculator {

    fun compute(
        logs: List<AlarmLog>,
        range: StatsRange,
        now: ZonedDateTime,
    ): StatsSummary {
        val zone = now.zone
        val endDate = now.toLocalDate()
        val startDate = endDate.minusDays(range.days - 1L)
        val inRange = logs.filter { log ->
            val date = occurrenceDate(log, zone)
            !date.isBefore(startDate) && !date.isAfter(endDate)
        }
        val byDay = inRange.groupBy { occurrenceDate(it, zone) }
        val days = (0 until range.days).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            val entries = byDay[date].orEmpty()
            val dismissed = entries.filter { it.outcome == AlarmOutcome.DISMISSED }
            StatsDay(
                date = date,
                wakeUpMinuteOfDay = CircularTime.meanMinuteOfDay(
                    dismissed.mapNotNull { wakeUpMinuteOfDay(it, zone) },
                ),
                successfulWakeUps = dismissed.size,
                missed = entries.count { it.outcome == AlarmOutcome.MISSED },
            )
        }
        val dismissed = inRange.filter { it.outcome == AlarmOutcome.DISMISSED }
        val successfulWakeUps = dismissed.size
        val missed = inRange.count { it.outcome == AlarmOutcome.MISSED }
        val attempts = successfulWakeUps + missed
        return StatsSummary(
            range = range,
            days = days,
            successfulWakeUps = successfulWakeUps,
            missed = missed,
            // snooze_count is a running counter of the whole chain (1, then 2, ...), so the
            // number of presses is the number of SNOOZED rows, not their sum.
            snoozes = inRange.count { it.outcome == AlarmOutcome.SNOOZED },
            // A SNOOZED row is a step of one occurrence, not a separate attempt: counting it
            // in the denominator would turn "snoozed once, then got up" into a failure.
            successRate = if (attempts == 0) null else successfulWakeUps.toFloat() / attempts,
            averageWakeUpMinuteOfDay = CircularTime.meanMinuteOfDay(
                dismissed.mapNotNull { wakeUpMinuteOfDay(it, zone) },
            ),
            streakDays = streak(byDay, endDate, startDate),
            totalEntries = inRange.size,
        )
    }

    private fun streak(
        byDay: Map<LocalDate, List<AlarmLog>>,
        endDate: LocalDate,
        startDate: LocalDate,
    ): Int {
        var day = endDate
        // Today's alarm may not have rung yet, so an empty (or still snoozed) today must not
        // reset a streak the user is in the middle of.
        if (byDay[day].orEmpty().none { it.outcome == AlarmOutcome.DISMISSED }) {
            day = day.minusDays(1)
        }
        var count = 0
        while (!day.isBefore(startDate) &&
            byDay[day].orEmpty().any { it.outcome == AlarmOutcome.DISMISSED }
        ) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    /** Local day the occurrence was scheduled for. */
    private fun occurrenceDate(log: AlarmLog, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(log.scheduledAt).atZone(zone).toLocalDate()

    /**
     * Actual time of getting up, in minutes after midnight. No writer stamps
     * [AlarmLog.dismissedAt] yet, so [AlarmLog.firedAt] (written when the ring finished) is
     * the effective source; the other hops keep this future-proof for older or imported rows.
     */
    private fun wakeUpMinuteOfDay(log: AlarmLog, zone: ZoneId): Double? {
        val timestamp = log.dismissedAt ?: log.firedAt ?: log.scheduledAt
        if (timestamp <= 0L) return null
        return CircularTime.minutesOfDay(
            Instant.ofEpochMilli(timestamp).atZone(zone).toLocalTime(),
        )
    }
}
