package io.github.caendeith.awakr.ui.widget

import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.alarm.Days
import io.github.caendeith.awakr.core.time.NextOccurrence
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Everything the widget draws, computed from domain data only.
 *
 * The type is deliberately free of Android and Glance so the selection and formatting rules can be
 * unit tested on the JVM. The Glance layer only paints [state].
 */
data class AlarmWidgetState(
    /** Id of the alarm that rings next, or null when nothing will ring. */
    val alarmId: Long? = null,
    /** Wall clock of the next alarm, e.g. `07:30`; empty when there is no alarm. */
    val timeText: String = "",
    /** `label · repeat days`, already localised; empty when there is no alarm. */
    val subtitle: String = "",
    /** Exact moment the alarm rings, kept for tests and for the skip action. */
    val ringsAt: ZonedDateTime? = null,
    /** True when the next occurrence can be cancelled from the widget. */
    val canSkip: Boolean = false,
) {
    val hasAlarm: Boolean get() = alarmId != null
}

/**
 * Pure selection and formatting rules behind the "next alarm" widget.
 *
 * The repeat-day summary is produced here rather than by the Compose helper in `ui/format` because
 * that helper needs `LocalConfiguration`, which Glance does not provide.
 */
object AlarmWidgetLogic {

    /** Localised words the widget needs; passed in so no Android resource is touched here. */
    data class Words(val once: String, val everyDay: String)

    /** `07:30`; the same 24-hour wall clock the rest of the app prints. */
    fun formatTime(hour: Int, minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

    /** "Once", "Every day" or a comma separated day list such as "Mon, Wed, Fri". */
    fun daysSummary(daysMask: Int, locale: Locale, words: Words): String {
        if (!Days.hasRepeats(daysMask)) return words.once
        if (daysMask and Days.EVERY_DAY == Days.EVERY_DAY) return words.everyDay
        return Days.toDaySet(daysMask)
            .sortedBy { it.value }
            .joinToString(", ") { day: DayOfWeek -> day.getDisplayName(TextStyle.SHORT, locale) }
    }

    /** Moment [alarm] rings next, honouring a pending "skip next" marker; null when it never rings. */
    fun nextOccurrence(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? =
        if (!alarm.enabled || alarm.deletedAt != null) {
            null
        } else {
            NextOccurrence.next(now, alarm.hour, alarm.minute, alarm.daysMask, alarm.skipNextAt)
        }

    /** The enabled alarm that rings first, or null when none will ring. */
    fun nextAlarm(alarms: List<Alarm>, now: ZonedDateTime): Alarm? =
        alarms.asSequence()
            .mapNotNull { alarm -> nextOccurrence(alarm, now)?.let { at -> alarm to at } }
            .minByOrNull { (_, at) -> at.toInstant().toEpochMilli() }
            ?.first

    /** Builds the state the widget draws, or the no-alarm variant when nothing will ring. */
    fun state(alarms: List<Alarm>, now: ZonedDateTime, locale: Locale, words: Words): AlarmWidgetState {
        val next = alarms.asSequence()
            .mapNotNull { alarm -> nextOccurrence(alarm, now)?.let { at -> alarm to at } }
            .minByOrNull { (_, at) -> at.toInstant().toEpochMilli() }
            ?: return AlarmWidgetState()

        val (alarm, at) = next
        val days = daysSummary(alarm.daysMask, locale, words)
        val label = alarm.label.trim()
        return AlarmWidgetState(
            alarmId = alarm.id,
            timeText = formatTime(alarm.hour, alarm.minute),
            subtitle = if (label.isEmpty()) days else "$label · $days",
            ringsAt = at,
            canSkip = true,
        )
    }
}
