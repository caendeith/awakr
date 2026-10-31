package io.github.caendeith.awakr.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.alarm.Days
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

fun formatTime(hour: Int, minute: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", hour, minute)

/** Localised summary of the repeat days, e.g. "Mon, Tue, Wed" or "Every day". */
@Composable
fun repeatSummary(daysMask: Int): String {
    if (!Days.hasRepeats(daysMask)) {
        return androidx.compose.ui.res.stringResource(R.string.repeat_once)
    }
    if (daysMask and Days.EVERY_DAY == Days.EVERY_DAY) {
        return androidx.compose.ui.res.stringResource(R.string.repeat_every_day)
    }
    val locale = LocalContext.current.resources.configuration.locales[0]
    val days = Days.toDaySet(daysMask)
        .sortedBy { it.value }
        .joinToString(", ") { day: DayOfWeek -> day.getDisplayName(TextStyle.SHORT, locale) }
    return days
}
