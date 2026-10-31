package io.github.caendeith.awakr.data

import android.content.Context
import io.github.caendeith.awakr.data.db.AwakrDatabase
import io.github.caendeith.awakr.data.repository.AlarmLogRepository
import io.github.caendeith.awakr.data.repository.AlarmRepository
import io.github.caendeith.awakr.data.repository.PluginStateRepository
import io.github.caendeith.awakr.data.settings.AppSettingsRepository

/**
 * Manual dependency container for the data layer.
 * Created once by the application class; modules do not reach for global singletons.
 */
class AwakrData private constructor(
    val database: AwakrDatabase,
    val alarms: AlarmRepository,
    val alarmLogs: AlarmLogRepository,
    val pluginStates: PluginStateRepository,
    val settings: AppSettingsRepository,
) {
    companion object {
        fun create(context: Context): AwakrData {
            val db = AwakrDatabase.build(context)
            return AwakrData(
                database = db,
                alarms = AlarmRepository(db.alarmDao()),
                alarmLogs = AlarmLogRepository(db.alarmLogDao()),
                pluginStates = PluginStateRepository(db.pluginStateDao()),
                settings = AppSettingsRepository(context),
            )
        }
    }
}
