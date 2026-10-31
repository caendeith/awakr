package io.github.caendeith.awakr.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.time.NextOccurrence
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

/** An alarm paired with the moment it will ring next, or null when it will not ring. */
data class AlarmSchedule(val alarm: Alarm, val next: ZonedDateTime?)

fun scheduleOf(alarm: Alarm, now: ZonedDateTime): AlarmSchedule =
    AlarmSchedule(
        alarm = alarm,
        next = if (alarm.enabled) {
            NextOccurrence.next(now, alarm.hour, alarm.minute, alarm.daysMask)
        } else {
            null
        },
    )

/** Alarms sorted by the moment they ring; alarms that will not ring go last. */
fun schedulesOf(alarms: List<Alarm>, now: ZonedDateTime): List<AlarmSchedule> =
    alarms.map { scheduleOf(it, now) }.sortedWith(
        compareBy<AlarmSchedule> { it.next == null }
            .thenBy { it.next?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE }
            .thenBy { it.alarm.hour * 60 + it.alarm.minute },
    )

/** Wall clock that ticks so countdowns and today/tomorrow labels stay honest. */
@Composable
fun rememberNow(intervalMs: Long = 15_000L, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime {
    val state = produceState(initialValue = ZonedDateTime.now(zone), intervalMs, zone) {
        while (true) {
            delay(intervalMs)
            value = ZonedDateTime.now(zone)
        }
    }
    return state.value
}

/** "through 7 h 45 min" style countdown, localized and abbreviated so it never needs plurals. */
@Composable
fun countdownText(now: ZonedDateTime, target: ZonedDateTime?): String {
    if (target == null) return stringResource(R.string.home_no_next)
    val minutes = Duration.between(now, target).toMinutes()
    val days = minutes / (60 * 24)
    val hours = (minutes % (60 * 24)) / 60
    val rest = minutes % 60
    return when {
        minutes < 1L -> stringResource(R.string.home_less_than_minute)
        days > 0L -> stringResource(R.string.home_in_days_hours, days.toInt(), hours.toInt())
        hours > 0L -> stringResource(R.string.home_in_hours_minutes, hours.toInt(), rest.toInt())
        else -> stringResource(R.string.home_in_minutes, rest.toInt())
    }
}

/** "Today", "Tomorrow" or a short date for the day an alarm rings. */
@Composable
fun dateLabel(now: ZonedDateTime, target: ZonedDateTime): String {
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), target.toLocalDate())
    return when {
        days <= 0L -> stringResource(R.string.home_today)
        days == 1L -> stringResource(R.string.home_tomorrow)
        else -> target.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))
    }
}
