package io.github.caendeith.awakr.ui.sleep

import java.util.Locale

/** Whole hours and minutes of a duration, ready for a localised "%1$d h %2$d min" string. */
data class DurationParts(val hours: Int, val minutes: Int)

fun durationParts(millis: Long): DurationParts {
    val totalMinutes = millis.coerceAtLeast(0L) / 60_000L
    return DurationParts(
        hours = (totalMinutes / 60).toInt(),
        minutes = (totalMinutes % 60).toInt(),
    )
}

/** "07:05" for a local time; keeps the leading zero in both locales. */
fun clockTime(hour: Int, minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
