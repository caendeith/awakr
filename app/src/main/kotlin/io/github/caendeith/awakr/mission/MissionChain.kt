package io.github.caendeith.awakr.mission

import io.github.caendeith.awakr.core.alarm.Alarm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One mission inside a chain: a plugin kind plus that plugin's own JSON config. */
data class MissionStep(
    val kind: String,
    val config: String = "{}",
)

/**
 * The "several missions in a row" feature.
 *
 * The chain is stored in [Alarm.missionChain] as a JSON array of steps. An empty chain means the
 * alarm plays its single [Alarm.missionPluginId] mission, exactly like before the feature existed,
 * so old backups and old databases keep working without a rewrite.
 *
 * Kept free of Android so the rules can be unit tested.
 */
object MissionChain {

    /** How many missions one alarm may chain. Beyond this the alarm stops being a wake-up call. */
    const val MAX_STEPS: Int = 3

    /** The JSON of an alarm without a chain. */
    const val EMPTY: String = "[]"

    /** Parses [raw], dropping anything malformed instead of failing the whole alarm. */
    fun parse(raw: String): List<MissionStep> {
        if (raw.isBlank()) return emptyList()
        val array = runCatching { Json.parseToJsonElement(raw) as? JsonArray }.getOrNull()
            ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val kind = (obj["kind"] as? JsonPrimitive)?.content?.trim().orEmpty()
            if (kind.isEmpty()) return@mapNotNull null
            val config = (obj["config"] as? JsonObject)?.toString() ?: "{}"
            MissionStep(kind, config)
        }.take(MAX_STEPS)
    }

    /** Serialises [steps], clamping to [MAX_STEPS] and replacing broken configs with an object. */
    fun encode(steps: List<MissionStep>): String {
        val array = JsonArray(
            steps.take(MAX_STEPS).map { step ->
                buildJsonObject {
                    put("kind", step.kind)
                    put("config", configOf(step.config))
                }
            },
        )
        return array.toString()
    }

    /** The steps an alarm actually plays; a single-mission alarm yields exactly one step. */
    fun stepsOf(alarm: Alarm): List<MissionStep> {
        val chain = parse(alarm.missionChain)
        if (chain.isNotEmpty()) return chain
        return listOf(MissionStep(alarm.missionPluginId, alarm.missionConfig))
    }

    /** Appends [kind] when there is room left; returns the same list otherwise. */
    fun added(steps: List<MissionStep>, kind: String): List<MissionStep> =
        if (steps.size >= MAX_STEPS || kind.isBlank()) steps else steps + MissionStep(kind)

    fun removed(steps: List<MissionStep>, index: Int): List<MissionStep> =
        if (index !in steps.indices) steps else steps.filterIndexed { i, _ -> i != index }

    /** Moves the step at [index] by [delta] (-1 up, +1 down); out-of-range moves are ignored. */
    fun moved(steps: List<MissionStep>, index: Int, delta: Int): List<MissionStep> {
        val target = index + delta
        if (index !in steps.indices || target !in steps.indices) return steps
        val copy = steps.toMutableList()
        val step = copy.removeAt(index)
        copy.add(target, step)
        return copy
    }

    /** Replaces the config of the step at [index]. */
    fun withConfig(steps: List<MissionStep>, index: Int, config: String): List<MissionStep> =
        if (index !in steps.indices) {
            steps
        } else {
            steps.toMutableList().apply { set(index, steps[index].copy(config = config)) }
        }

    private fun configOf(config: String): JsonObject =
        runCatching { Json.parseToJsonElement(config) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
}
