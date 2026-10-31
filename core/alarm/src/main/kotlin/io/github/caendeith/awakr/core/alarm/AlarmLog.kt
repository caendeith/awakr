package io.github.caendeith.awakr.core.alarm

/** Outcome of one alarm occurrence. */
enum class AlarmOutcome {
    DISMISSED,
    SNOOZED,
    MISSED,
    ERROR,
}

/** One fired (or missed) occurrence, written to `alarm_logs`. */
data class AlarmLog(
    val id: Long = 0L,
    val alarmId: Long,
    val scheduledAt: Long,
    val firedAt: Long? = null,
    val dismissedAt: Long? = null,
    val missionPluginId: String? = null,
    val missionPassed: Boolean = false,
    val snoozeCount: Int = 0,
    val outcome: AlarmOutcome,
)
