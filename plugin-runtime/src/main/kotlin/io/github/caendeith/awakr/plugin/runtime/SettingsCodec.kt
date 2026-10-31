package io.github.caendeith.awakr.plugin.runtime

import io.github.caendeith.awakr.sdk.SettingField
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Converts declarative [SettingField]s into stored JSON values and back. */
object SettingsCodec {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Default value for the field, as written to `plugin_state.settings_json`. */
    fun defaultValue(field: SettingField): JsonPrimitive = when (field) {
        is SettingField.Toggle -> JsonPrimitive(field.default)
        is SettingField.Slider -> JsonPrimitive(field.default)
        is SettingField.Select -> JsonPrimitive(field.default)
        is SettingField.TextInput -> JsonPrimitive(field.default)
        is SettingField.Info -> JsonPrimitive("")
    }

    fun defaults(fields: List<SettingField>): JsonObject =
        JsonObject(fields.associate { it.key to defaultValue(it) })

    /** Stored values merged over defaults, so new fields appear without migrations. */
    fun merge(fields: List<SettingField>, stored: JsonObject?): JsonObject {
        val base = defaults(fields).toMutableMap()
        stored?.forEach { (key, value) -> if (key in base) base[key] = value }
        return JsonObject(base)
    }

    fun parse(raw: String?): JsonObject = try {
        if (raw.isNullOrBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(raw) as JsonObject
    } catch (_: Exception) {
        JsonObject(emptyMap())
    }

    fun encode(values: JsonObject): String = values.toString()

    fun boolean(values: JsonObject, key: String, default: Boolean): Boolean =
        (values[key] as? JsonPrimitive)?.booleanOrNull ?: default

    fun float(values: JsonObject, key: String, default: Float): Float =
        (values[key] as? JsonPrimitive)?.floatOrNull ?: default

    fun string(values: JsonObject, key: String, default: String): String =
        (values[key] as? JsonPrimitive)?.jsonPrimitive?.content ?: default
}
