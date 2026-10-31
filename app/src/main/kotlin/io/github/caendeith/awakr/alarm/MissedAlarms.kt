package io.github.caendeith.awakr.alarm

import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.alarm.AlarmLog
import io.github.caendeith.awakr.core.time.NextOccurrence
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** An occurrence that should have rung but was never logged. */
internal data class MissedOccurrence(
    val alarm: Alarm,
    val scheduledAt: Long,
)

/**
 * Finds alarm occurrences the device slept through.
 *
 * An occurrence counts as missed when the most recent scheduled moment of an enabled alarm is
 * inside MAX_AGE_MS of "now" and alarm_logs holds no entry for it. The window keeps the report
 * honest: an alarm from three days ago is history, not something to nag about.
 *
 * Occurrences older than the moment the alarm was last saved are ignored: an alarm created at
 * 10:00 for 07:30 did not miss anything today, it simply did not exist yet.
 */
internal object MissedAlarms {

    const val MAX_AGE_MS: Long = 24L * 60L * 60L * 1000L

    /** A fired alarm may be logged a few seconds after its scheduled minute. */
    const val TOLERANCE_MS: Long = 2L * 60L * 1000L

    fun find(
        alarms: List<Alarm>,
        logs: List<AlarmLog>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        maxAgeMillis: Long = MAX_AGE_MS,
    ): List<MissedOccurrence> {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        return alarms
            .asSequence()
            .filter { it.enabled }
            .mapNotNull { alarm ->
                val previous = NextOccurrence.previous(now, alarm.hour, alarm.minute, alarm.daysMask)
                    ?: return@mapNotNull null
                val scheduledAt = previous.toInstant().toEpochMilli()
                val age = nowMillis - scheduledAt
                if (age <= 0L || age > maxAgeMillis) return@mapNotNull null
                val existedSince = maxOf(alarm.createdAt, alarm.updatedAt)
                if (existedSince > scheduledAt) return@mapNotNull null
                val logged = logs.any { abs(it.scheduledAt - scheduledAt) <= TOLERANCE_MS }
                if (logged) null else MissedOccurrence(alarm, scheduledAt)
            }
            .sortedBy { it.scheduledAt }
            .toList()
    }
}