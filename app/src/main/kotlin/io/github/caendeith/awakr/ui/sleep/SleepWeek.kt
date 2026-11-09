package io.github.caendeith.awakr.ui.sleep

import io.github.caendeith.awakr.data.sleep.SleepSession
import io.github.caendeith.awakr.data.sleep.SleepStage
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.roundToLong

/** One imported night, grouped by the local date the wearer woke up. */
data class SleepNight(
    val date: LocalDate,
    val bedtime: LocalTime,
    val wakeUp: LocalTime,
    val asleepMillis: Long,
    val stages: List<SleepStage> = emptyList(),
    val hrMin: Int? = null,
    val hrAvg: Int? = null,
    val hrMax: Int? = null,
    val spo2Avg: Double? = null,
)

/** One slot of the week chart; [night] is null when nothing was imported for that date. */
data class SleepDay(val date: LocalDate, val night: SleepNight?)

data class SleepWeek(
    /** Oldest first, exactly "days" slots ending today. */
    val days: List<SleepDay>,
    val averageAsleepMillis: Long?,
    /** Consecutive nights counted back from today (or yesterday, before tonight is synced). */
    val streakNights: Int,
) {
    val nights: List<SleepNight> get() = days.mapNotNull { it.night }

    /** Most recent night that actually has data, even when today's sync has not happened yet. */
    val lastNight: SleepNight? get() = nights.lastOrNull()
}

/**
 * Pure aggregation for the Sleep screen: no Android types, so it is covered by JVM tests.
 *
 * Nights are keyed by the local wake-up date because that is what a person means by "last night":
 * a session from 23:10 to 07:05 belongs to the date of 07:05. When two sessions end on the same
 * date (a nap plus a night) the longer one wins.
 */
object SleepWeekCalculator {

    const val DEFAULT_DAYS = 7

    fun compute(sessions: List<SleepSession>, now: ZonedDateTime, days: Int = DEFAULT_DAYS): SleepWeek {
        require(days > 0) { "days must be positive" }

        val zone = now.zone
        val today = now.toLocalDate()
        val firstDate = today.minusDays((days - 1).toLong())

        val nights = LinkedHashMap<LocalDate, SleepNight>()
        for (session in sessions) {
            if (session.endTime <= session.startTime) continue
            val wake = Instant.ofEpochMilli(session.endTime).atZone(zone)
            val date = wake.toLocalDate()
            if (date.isBefore(firstDate) || date.isAfter(today)) continue

            val night = SleepNight(
                date = date,
                bedtime = Instant.ofEpochMilli(session.startTime).atZone(zone).toLocalTime(),
                wakeUp = wake.toLocalTime(),
                asleepMillis = session.asleepMillis,
                stages = session.stages,
                hrMin = session.hrMin,
                hrAvg = session.hrAvg,
                hrMax = session.hrMax,
                spo2Avg = session.spo2Avg,
            )
            val existing = nights[date]
            if (existing == null || night.asleepMillis > existing.asleepMillis) {
                nights[date] = night
            }
        }

        val orderedDays = (days - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            SleepDay(date = date, night = nights[date])
        }

        val average = if (nights.isEmpty()) {
            null
        } else {
            nights.values.map { it.asleepMillis }.average().roundToLong()
        }

        var cursor = if (nights.containsKey(today)) today else today.minusDays(1)
        var streak = 0
        while (!cursor.isBefore(firstDate) && nights.containsKey(cursor)) {
            streak += 1
            cursor = cursor.minusDays(1)
        }

        return SleepWeek(days = orderedDays, averageAsleepMillis = average, streakNights = streak)
    }
}
