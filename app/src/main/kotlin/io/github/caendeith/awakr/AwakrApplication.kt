package io.github.caendeith.awakr

import android.app.Application
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.alarm.Days
import io.github.caendeith.awakr.data.AwakrData
import io.github.caendeith.awakr.plugin.runtime.PluginRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek

class AwakrApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var data: AwakrData
        private set

    /**
     * Build-time plugin set. Mission/sound/theme modules are appended here as they land
     * (no reflection, no dynamic loading).
     */
    val plugins: PluginRegistry by lazy { PluginRegistry.build(emptyList()) }

    override fun onCreate() {
        super.onCreate()
        data = AwakrData.create(this)
        appScope.launch { seedSampleAlarms() }
    }

    /** First launch only: a couple of sample alarms so the UI is not empty. */
    private suspend fun seedSampleAlarms() {
        val settings = data.settings.settings.first()
        if (settings.seeded) return
        if (data.alarms.enabledAlarms().isEmpty()) {
            val workdays = Days.toMask(
                setOf(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
                ),
            )
            val weekend = Days.toMask(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
            data.alarms.save(Alarm(hour = 7, minute = 30, daysMask = workdays, label = "", missionPluginId = "math"))
            data.alarms.save(Alarm(hour = 9, minute = 15, daysMask = weekend, label = "", missionPluginId = "shake"))
        }
        data.settings.setSeeded(true)
    }
}
