package io.github.caendeith.awakr.sdk

import androidx.compose.runtime.Composable
import kotlinx.serialization.json.JsonObject

/**
 * Mission that has to be solved before an alarm can be dismissed.
 *
 * [config] is the per-alarm JSON blob stored in `alarms.mission_config`.
 * [Editor] renders mission settings inside the alarm editor, [Mission] renders the task shown
 * while the alarm is ringing. Both must be self-contained composables; the host owns layout,
 * theming and the ring screen chrome.
 */
@AwakrApi
interface MissionPlugin : AwakrPlugin {
    /** Stable identifier persisted in `alarms.mission_plugin_id`, e.g. "math". */
    val kind: String

    @Composable
    fun Editor(config: JsonObject, onChange: (JsonObject) -> Unit)

    @Composable
    fun Mission(config: JsonObject, onSolved: () -> Unit)

    /** Validates and migrates [config]; the returned object is what gets persisted. */
    fun validate(config: JsonObject): JsonObject = config
}
