package io.github.caendeith.awakr.data.backup

import io.github.caendeith.awakr.data.db.entity.AlarmEntity

/**
 * Encodes and decodes the JSON backup file.
 *
 * Format (schema 1):
 * ```
 * {
 *   "schema": 1,
 *   "app": "awakr",
 *   "exportedAt": 1762000000000,
 *   "alarms": [ { "id": 1, "hour": 7, "minute": 30, ... } ],
 *   "settings": { "themeMode": "SYSTEM", "defaultSoundId": "builtin:chime", ... }
 * }
 * ```
 *
 * Reading is deliberately forgiving: unknown fields are ignored (a newer build may add some), a
 * missing alarm field falls back to the [AlarmEntity] default, and soft-deleted alarms are dropped.
 * A file from another app or from a newer schema is rejected with a typed error.
 */
object BackupCodec {

    /** Days mask bit 0..6 = Monday..Sunday; higher bits are not meaningful. */
    private const val DAYS_MASK = 0b111_1111

    private val VALID_THEMES = setOf("SYSTEM", "DARK", "LIGHT")
    private val VALID_SWIPE_ACTIONS = setOf("DELETE", "TOGGLE", "NONE")

    /** Pre-alert notification lead time, in minutes. */
    private const val MAX_PRE_ALERT_MINUTES = 180

    fun encode(payload: BackupPayload): String =
        JsonWriter.write(
            jsonObject(
                "schema" to longValue(BACKUP_SCHEMA.toLong()),
                "app" to JsonValue.Str(BACKUP_APP),
                "exportedAt" to longValue(payload.exportedAt),
                "alarms" to jsonArray(payload.alarms.map(::encodeAlarm)),
                "settings" to (payload.settings?.let(::encodeSettings) ?: JsonValue.JsonNull),
            ),
        ) + "\n"

    fun decode(text: String): BackupDecodeResult {
        val root = try {
            JsonParser.parse(text)
        } catch (e: JsonParseException) {
            return failure(BackupError.Malformed(e.message ?: "not valid JSON"))
        }
        if (root !is JsonValue.Obj) {
            return failure(BackupError.Malformed("the file is not a JSON object"))
        }
        val app = root.string("app")
        if (app != BACKUP_APP) return failure(BackupError.NotAwakrBackup(app))

        val schema = root.long("schema")
            ?: return failure(BackupError.Malformed("missing field \"schema\""))
        if (schema != BACKUP_SCHEMA.toLong()) {
            return failure(BackupError.UnsupportedSchema(schema))
        }

        val alarms = mutableListOf<AlarmEntity>()
        var skipped = 0
        when (val node = root.fields["alarms"]) {
            null, JsonValue.JsonNull -> Unit
            is JsonValue.Arr -> node.items.forEach { item ->
                val alarm = (item as? JsonValue.Obj)?.let(::decodeAlarm)
                // Soft-deleted alarms are not restored: a backup keeps what the user sees.
                if (alarm == null || alarm.deletedAt != null) skipped++ else alarms += alarm
            }
            else -> return failure(BackupError.Malformed("field \"alarms\" is not an array"))
        }

        return BackupDecodeResult.Success(
            payload = BackupPayload(
                exportedAt = root.long("exportedAt") ?: 0L,
                alarms = alarms,
                settings = root.obj("settings")?.let(::decodeSettings),
            ),
            skippedAlarms = skipped,
        )
    }

    private fun failure(error: BackupError): BackupDecodeResult.Failure =
        BackupDecodeResult.Failure(error)

    private fun encodeAlarm(alarm: AlarmEntity): JsonValue = jsonObject(
        "id" to longValue(alarm.id),
        "hour" to intValue(alarm.hour),
        "minute" to intValue(alarm.minute),
        "daysMask" to intValue(alarm.daysMask),
        "enabled" to boolValue(alarm.enabled),
        "label" to JsonValue.Str(alarm.label),
        "soundUri" to stringOrNull(alarm.soundUri),
        "soundPackId" to stringOrNull(alarm.soundPackId),
        "soundId" to JsonValue.Str(alarm.soundId),
        "rampSeconds" to intValue(alarm.rampSeconds),
        "volumeStart" to intValue(alarm.volumeStart),
        "vibrate" to boolValue(alarm.vibrate),
        "snoozeEnabled" to boolValue(alarm.snoozeEnabled),
        "snoozeMinutes" to intValue(alarm.snoozeMinutes),
        "maxSnoozes" to intValue(alarm.maxSnoozes),
        "missionPluginId" to JsonValue.Str(alarm.missionPluginId),
        "missionConfig" to JsonValue.Str(alarm.missionConfig),
        "missionChain" to JsonValue.Str(alarm.missionChain),
        "sortOrder" to intValue(alarm.sortOrder),
        "skipNextAt" to longOrNull(alarm.skipNextAt),
        "deletedAt" to longOrNull(alarm.deletedAt),
        "createdAt" to longValue(alarm.createdAt),
        "updatedAt" to longValue(alarm.updatedAt),
    )

    /** Returns null when the entry is unusable, so the caller can count it as skipped. */
    private fun decodeAlarm(node: JsonValue.Obj): AlarmEntity? {
        val hour = node.int("hour") ?: return null
        val minute = node.int("minute") ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        val defaults = AlarmEntity(hour = hour, minute = minute)
        return defaults.copy(
            id = node.long("id")?.coerceAtLeast(0L) ?: defaults.id,
            daysMask = (node.int("daysMask") ?: defaults.daysMask) and DAYS_MASK,
            enabled = node.bool("enabled") ?: defaults.enabled,
            label = node.string("label") ?: defaults.label,
            soundUri = node.string("soundUri") ?: defaults.soundUri,
            soundPackId = node.string("soundPackId") ?: defaults.soundPackId,
            soundId = node.string("soundId") ?: defaults.soundId,
            rampSeconds = (node.int("rampSeconds") ?: defaults.rampSeconds).coerceIn(0, 3600),
            volumeStart = (node.int("volumeStart") ?: defaults.volumeStart).coerceIn(0, 100),
            vibrate = node.bool("vibrate") ?: defaults.vibrate,
            snoozeEnabled = node.bool("snoozeEnabled") ?: defaults.snoozeEnabled,
            snoozeMinutes = (node.int("snoozeMinutes") ?: defaults.snoozeMinutes).coerceIn(1, 60),
            maxSnoozes = (node.int("maxSnoozes") ?: defaults.maxSnoozes).coerceIn(0, 10),
            missionPluginId = node.string("missionPluginId") ?: defaults.missionPluginId,
            missionConfig = node.string("missionConfig") ?: defaults.missionConfig,
            missionChain = node.string("missionChain") ?: defaults.missionChain,
            sortOrder = node.int("sortOrder") ?: defaults.sortOrder,
            skipNextAt = node.long("skipNextAt"),
            deletedAt = node.long("deletedAt"),
            createdAt = node.long("createdAt") ?: defaults.createdAt,
            updatedAt = node.long("updatedAt") ?: defaults.updatedAt,
        )
    }

    private fun encodeSettings(settings: BackupSettings): JsonValue {
        val fields = linkedMapOf<String, JsonValue>(
            "themeMode" to JsonValue.Str(settings.themeMode),
            "swipeAction" to JsonValue.Str(settings.swipeAction),
            "defaultSnoozeMinutes" to intValue(settings.defaultSnoozeMinutes),
            "defaultMissionPluginId" to JsonValue.Str(settings.defaultMissionPluginId),
            "defaultSoundId" to JsonValue.Str(settings.defaultSoundId),
        )
        // Optional: written when known, omitted when the caller has no value to report.
        settings.dynamicColor?.let { fields["dynamicColor"] = boolValue(it) }
        settings.preAlertMinutes?.let { fields["preAlertMinutes"] = intValue(it) }
        settings.language?.let { fields["language"] = JsonValue.Str(it) }
        return JsonValue.Obj(fields)
    }

    private fun decodeSettings(node: JsonValue.Obj): BackupSettings {
        val defaults = BackupSettings()
        val theme = node.string("themeMode")?.uppercase()?.takeIf { it in VALID_THEMES }
            ?: defaults.themeMode
        val swipe = node.string("swipeAction")?.uppercase()?.takeIf { it in VALID_SWIPE_ACTIONS }
            ?: defaults.swipeAction
        return BackupSettings(
            themeMode = theme,
            swipeAction = swipe,
            defaultSnoozeMinutes = (node.int("defaultSnoozeMinutes") ?: defaults.defaultSnoozeMinutes)
                .coerceIn(1, 60),
            defaultMissionPluginId = node.string("defaultMissionPluginId")
                ?: defaults.defaultMissionPluginId,
            defaultSoundId = node.string("defaultSoundId") ?: defaults.defaultSoundId,
            // Absent in older files: null keeps the current device value on restore.
            dynamicColor = node.bool("dynamicColor"),
            preAlertMinutes = node.int("preAlertMinutes")?.coerceIn(0, MAX_PRE_ALERT_MINUTES),
            language = node.string("language")?.let { normalizeBackupLanguage(it.lowercase()) },
        )
    }
}
