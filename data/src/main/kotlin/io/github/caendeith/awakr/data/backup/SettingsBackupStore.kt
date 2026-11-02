package io.github.caendeith.awakr.data.backup

import io.github.caendeith.awakr.data.settings.AppSettings
import io.github.caendeith.awakr.data.settings.AppSettingsRepository
import io.github.caendeith.awakr.data.settings.LANGUAGE_ENGLISH
import io.github.caendeith.awakr.data.settings.LANGUAGE_RUSSIAN
import io.github.caendeith.awakr.data.settings.LANGUAGE_SYSTEM
import io.github.caendeith.awakr.data.settings.SwipeAction
import io.github.caendeith.awakr.data.settings.ThemeMode
import kotlinx.coroutines.flow.first

/**
 * Snapshot and restore of the preferences a backup carries.
 *
 * Import/export logic talks to this interface rather than to DataStore, so it can be unit-tested
 * without an Android Context.
 */
interface SettingsBackupStore {
    suspend fun snapshot(): BackupSettings
    suspend fun restore(settings: BackupSettings)
}

/** Maps a language tag from a file onto the one of the supported values. */
internal fun normalizeBackupLanguage(value: String): String =
    if (value == LANGUAGE_ENGLISH || value == LANGUAGE_RUSSIAN) value else LANGUAGE_SYSTEM

/**
 * Folds a file's settings into the current ones.
 *
 * A field the file does not carry (null) keeps the current value, so importing a backup written
 * before a setting existed never resets it.
 */
internal fun AppSettings.mergedWith(backup: BackupSettings): AppSettings = copy(
    themeMode = ThemeMode.entries.firstOrNull { it.name == backup.themeMode } ?: themeMode,
    swipeAction = SwipeAction.entries.firstOrNull { it.name == backup.swipeAction } ?: swipeAction,
    defaultSnoozeMinutes = backup.defaultSnoozeMinutes.coerceIn(1, 60),
    defaultMissionPluginId = backup.defaultMissionPluginId,
    defaultSoundId = backup.defaultSoundId,
    dynamicColor = backup.dynamicColor ?: dynamicColor,
    preAlertMinutes = backup.preAlertMinutes?.coerceIn(0, 180) ?: preAlertMinutes,
    language = backup.language?.let(::normalizeBackupLanguage) ?: language,
)

/** DataStore-backed implementation used by the app. */
class AppSettingsBackupStore(
    private val repository: AppSettingsRepository,
) : SettingsBackupStore {

    override suspend fun snapshot(): BackupSettings {
        val settings = repository.settings.first()
        return BackupSettings(
            themeMode = settings.themeMode.name,
            swipeAction = settings.swipeAction.name,
            defaultSnoozeMinutes = settings.defaultSnoozeMinutes,
            defaultMissionPluginId = settings.defaultMissionPluginId,
            defaultSoundId = settings.defaultSoundId,
            dynamicColor = settings.dynamicColor,
            preAlertMinutes = settings.preAlertMinutes,
            language = settings.language,
        )
    }

    override suspend fun restore(settings: BackupSettings) {
        val merged = repository.settings.first().mergedWith(settings)
        repository.setThemeMode(merged.themeMode)
        repository.setSwipeAction(merged.swipeAction)
        repository.setDefaultSnoozeMinutes(merged.defaultSnoozeMinutes)
        repository.setDefaultMission(merged.defaultMissionPluginId)
        repository.setDefaultSound(merged.defaultSoundId)
        repository.setDynamicColor(merged.dynamicColor)
        repository.setPreAlertMinutes(merged.preAlertMinutes)
        repository.setLanguage(merged.language)
        // Real alarms now exist, so the first-run sample alarms must not be seeded on next start.
        repository.setSeeded(true)
    }
}
