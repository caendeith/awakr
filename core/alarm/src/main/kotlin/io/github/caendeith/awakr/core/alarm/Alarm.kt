package io.github.caendeith.awakr.core.alarm

/**
 * A single alarm.
 *
 * [daysMask] bit 0 is Monday ... bit 6 is Sunday; 0 means a one-shot alarm.
 * [missionConfig] is an opaque JSON blob owned by the mission plugin.
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
    val rampSeconds: Int = 30,
    val volumeStart: Int = 15,
    val vibrate: Boolean = true,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 10,
    val maxSnoozes: Int = 3,
    val missionPluginId: String = "math",
    val missionConfig: String = "{}",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
