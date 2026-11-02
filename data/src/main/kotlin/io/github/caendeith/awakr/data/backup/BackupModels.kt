package io.github.caendeith.awakr.data.backup

import io.github.caendeith.awakr.data.db.entity.AlarmEntity

/** Version of the backup file layout this build reads and writes. */
const val BACKUP_SCHEMA = 1

/** Expected value of the "app" field; anything else is not an Awakr backup. */
const val BACKUP_APP = "awakr"

/**
 * Preferences carried by a backup.
 *
 * Onboarding and other device-local flags are intentionally absent: restoring a file must never
 * push the user through onboarding again.
 */
data class BackupSettings(
    val themeMode: String = "SYSTEM",
    val swipeAction: String = "DELETE",
    val defaultSnoozeMinutes: Int = 10,
    val defaultMissionPluginId: String = "math",
    val defaultSoundId: String = "builtin:chime",
    /**
     * Null means "the file does not carry this setting" (a backup written before it existed);
     * restoring then keeps whatever the device already has.
     */
    val dynamicColor: Boolean? = null,
    val preAlertMinutes: Int? = null,
    val language: String? = null,
)

/** A decoded backup file. */
data class BackupPayload(
    val exportedAt: Long,
    val alarms: List<AlarmEntity>,
    val settings: BackupSettings?,
)

/** Why a file was rejected. The UI turns each case into a readable message. */
sealed interface BackupError {
    data class NotAwakrBackup(val app: String?) : BackupError
    data class UnsupportedSchema(val schema: Long) : BackupError
    data class Malformed(val detail: String) : BackupError
}

sealed interface BackupDecodeResult {
    data class Success(val payload: BackupPayload, val skippedAlarms: Int) : BackupDecodeResult
    data class Failure(val error: BackupError) : BackupDecodeResult
}

/** Result of writing a file. */
data class ExportSummary(val alarmCount: Int, val settingsIncluded: Boolean)

/** How imported alarms meet the alarms already on the device. */
enum class ImportMode {
    /** Delete every alarm first, then insert the backup. */
    REPLACE,

    /** Append the backup to the existing list, keeping both. */
    MERGE,
}

data class ImportSummary(
    val mode: ImportMode,
    val imported: Int,
    val skipped: Int,
    val settingsApplied: Boolean,
)
