package io.github.caendeith.awakr.data.stats

import java.time.LocalDate

/** Window covered by the statistics screen, in days, ending today. */
enum class StatsRange(val days: Int) {
    WEEK(7),
    MONTH(30),
}

/** One column of the per-day wake-up chart. */
data class StatsDay(
    /** Day the alarm was scheduled for, in the device time zone. */
    val date: LocalDate,
    /**
     * Circular mean of the actual wake-up times on that day, in minutes after local
     * midnight. Null when nothing was dismissed that day.
     */
    val wakeUpMinuteOfDay: Double?,
    val successfulWakeUps: Int,
    val missed: Int,
)

/** Everything the statistics screen shows for one [range]. */
data class StatsSummary(
    val range: StatsRange,
    /** Oldest first; always [StatsRange.days] entries so the chart keeps a stable scale. */
    val days: List<StatsDay>,
    val successfulWakeUps: Int,
    val missed: Int,
    /** Number of snooze presses (one per SNOOZED row). */
    val snoozes: Int,
    /** DISMISSED / (DISMISSED + MISSED); null while neither has happened. */
    val successRate: Float?,
    /** Circular mean of the wake-up times of day; null without successful wake-ups. */
    val averageWakeUpMinuteOfDay: Double?,
    /** Consecutive days with at least one DISMISSED, counting back from today. */
    val streakDays: Int,
    /** Number of logged occurrences in the window, whatever their outcome. */
    val totalEntries: Int,
) {
    /** True when the selected window holds no history at all. */
    val isEmpty: Boolean get() = totalEntries == 0
}
