package io.github.caendeith.awakr.core.alarm

/**
 * A single alarm.
 *
 * [daysMask] bit 0 is Monday ... bit 6 is Sunday; 0 means a one-shot alarm.
 * [missionConfig] is an opaque JSON blob owned by the mission plugin.
 * [soundId] is either `builtin:<id>` or `file:<path>`; empty means "the default sound".
 * [sortOrder] is the user's manual order on Home (lower comes first).
 * [skipNextAt] suppresses exactly one occurrence: the epoch millis of the moment to skip.
 * [deletedAt] marks a soft deletion so the undo snackbar can bring the alarm back.
 */
data class Alarm(
    val id: Long = 0L,
    val hour: Int,
    val minute: Int,
    val daysMask: Int = 0,
    val enabled: Boolean = true,
    val label: String = "",
    val soundUri: String? = null,
    val soundPackId: String? = null,
    val soundId: String = "",
    val rampSeconds: Int = 30,
    val volumeStart: Int = 15,
    val vibrate: Boolean = true,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 10,
    val maxSnoozes: Int = 3,
    val missionPluginId: String = "math",
    val missionConfig: String = "{}",
    /** Several missions in a row as a JSON array of steps: [{kind, config}]. Empty means one mission. */
    val missionChain: String = "[]",
    val sortOrder: Int = 0,
    val skipNextAt: Long? = null,
    val deletedAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
