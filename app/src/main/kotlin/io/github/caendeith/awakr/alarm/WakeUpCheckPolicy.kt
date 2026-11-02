package io.github.caendeith.awakr.alarm

/**
 * A "wake up check" that is still pending for one alarm.
 *
 * [checkAt] is when the quiet clarification notification is due, [escalateAt] when the alarm
 * rings again if the user has not confirmed being awake in between. [time] and [label] are
 * snapshots of the alarm at dismissal time so the notification does not need the database.
 */
internal data class PendingWakeUpCheck(
    val alarmId: Long,
    val checkAt: Long,
    val escalateAt: Long,
    val snoozeCount: Int,
    val time: String,
    val label: String,
)

/** How the pending check has to be (re)armed at [now]: the check itself is skipped when its moment passed. */
internal data class WakeUpCheckPlan(
    val checkAt: Long?,
    val escalateAt: Long,
)

/**
 * Timing rules of the wake-up check, kept free of Android so they can be unit tested.
 *
 * The user picks a delay in [OPTIONS_MINUTES]; the escalation deadline is 100 seconds after the
 * check. Both are derived, never stored on their own, so a clock change cannot drift them apart.
 */
internal object WakeUpCheckPolicy {

    /** How long the user has to answer the check before the alarm rings again. */
    const val ESCALATION_DELAY_MS: Long = 100_000L

    /**
     * How late an escalation may still fire and ring.
     *
     * The platform may deliver an exact alarm long after the fact when the device slept through
     * it; ringing half an hour later would be a second, unrelated alarm rather than a reminder.
     */
    const val STALE_GRACE_MS: Long = 10 * 60_000L

    /** Choosable delays in minutes; 0 switches the check off. */
    val OPTIONS_MINUTES: List<Int> = listOf(0, 1, 3, 5, 10, 20, 30)

    /** Off unless the user asks for it. */
    const val DEFAULT_MINUTES: Int = 0

    /**
     * Maps a stored or selected value to a supported delay.
     *
     * Values are rounded *up* to the next option, never down: a stray 2 must not turn into a
     * check earlier than the user asked for. Anything above the largest option is capped.
     */
    fun normalizeMinutes(value: Int): Int {
        if (value <= 0) return 0
        return OPTIONS_MINUTES.firstOrNull { it >= value } ?: OPTIONS_MINUTES.last()
    }

    /** Deadline of the re-ring that follows a check placed at [checkAt]. */
    fun escalationAt(checkAt: Long): Long = checkAt + ESCALATION_DELAY_MS

    /** True once the check is too late to be shown and the alarm has to ring instead. */
    fun isExpired(checkAt: Long, now: Long): Boolean = now >= escalationAt(checkAt)

    /** True when an escalation that fires at [now] is too late to ring at all. */
    fun isStale(escalateAt: Long, now: Long): Boolean = now > escalateAt + STALE_GRACE_MS

    /**
     * What has to be armed for a pending check at [now].
     *
     * @return null when the whole check is stale and must be dropped (the deadline passed while
     * the device was off, so ringing now would surprise the user instead of reminding them);
     * otherwise the plan, whose [WakeUpCheckPlan.checkAt] is null when only the escalation is left.
     */
    fun plan(checkAt: Long, escalateAt: Long, now: Long): WakeUpCheckPlan? {
        if (now >= escalateAt) return null
        return if (now < checkAt) {
            WakeUpCheckPlan(checkAt = checkAt, escalateAt = escalateAt)
        } else {
            // The notification moment passed while nothing could show it; only the re-ring is left.
            WakeUpCheckPlan(checkAt = null, escalateAt = escalateAt)
        }
    }

    /** Serialises a pending check for SharedPreferences. The label stays last and may contain [SEPARATOR]. */
    fun encode(check: PendingWakeUpCheck): String = listOf(
        check.alarmId,
        check.checkAt,
        check.escalateAt,
        check.snoozeCount,
        check.time,
        check.label,
    ).joinToString(SEPARATOR)

    /** Reads back [encode]; null for a missing, truncated or foreign entry. */
    fun decode(value: String?): PendingWakeUpCheck? {
        val parts = value?.split(SEPARATOR, limit = FIELD_COUNT) ?: return null
        if (parts.size != FIELD_COUNT) return null
        val alarmId = parts[0].toLongOrNull() ?: return null
        val checkAt = parts[1].toLongOrNull() ?: return null
        val escalateAt = parts[2].toLongOrNull() ?: return null
        val snoozeCount = parts[3].toIntOrNull() ?: return null
        if (alarmId <= 0L || checkAt <= 0L || escalateAt <= checkAt) return null
        return PendingWakeUpCheck(alarmId, checkAt, escalateAt, snoozeCount, parts[4], parts[5])
    }

    private const val SEPARATOR = "|"
    private const val FIELD_COUNT = 6
}
