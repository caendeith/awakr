package io.github.caendeith.awakr.data.backup

import io.github.caendeith.awakr.data.AwakrData

/**
 * Builds the backup repository from the app's dependency container.
 *
 * Lives here because Room types are an implementation detail of this module; the app module only
 * sees this factory.
 */
fun AwakrData.backupRepository(): BackupRepository = BackupRepository(
    dao = database.alarmDao(),
    settings = AppSettingsBackupStore(settings),
)
