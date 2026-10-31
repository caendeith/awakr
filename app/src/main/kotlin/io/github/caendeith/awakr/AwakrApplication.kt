package io.github.caendeith.awakr

import android.app.Application
import io.github.caendeith.awakr.alarm.AlarmScheduler
import io.github.caendeith.awakr.alarm.MissedAlarmReporter
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.alarm.Days
import io.github.caendeith.awakr.core.notify.AwakrNotifications
import io.github.caendeith.awakr.data.AwakrData
import io.github.caendeith.awakr.plugin.runtime.PluginRegistry
import io.github.caendeith.awakr.plugins.mission.math.MathMissionPlugin
import io.github.caendeith.awakr.plugins.mission.code.CodeMissionPlugin
import io.github.caendeith.awakr.plugins.mission.shake.ShakeMissionPlugin
import io.github.caendeith.awakr.plugins.mission.typing.TypingMissionPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek

class AwakrApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var data: AwakrData
        private set

    val scheduler: AlarmScheduler by lazy { AlarmScheduler(this) }

    /**
     * Build-time plugin set. Mission/sound/theme modules are appended here as they land
     * (no reflection, no dynamic loading).
     */
    val plugins: PluginRegistry by lazy {
        PluginRegistry.build(
            plugins = listOf(
                MathMissionPlugin(),
                ShakeMissionPlugin(),
                TypingMissionPlugin(),
                CodeMissionPlugin(),
            ),
            declaredPermissions = declaredPermissions(),
        )
    }

    /**
     * Permissions this build actually declares. A plugin asking for anything else is reported in
     * Settings → Plugins instead of failing with a silent runtime permission denial.
     */
    @Suppress("DEPRECATION")
    private fun declaredPermissions(): Set<String> =
        packageManager
            .getPackageInfo(packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.toSet()
            .orEmpty()

    override fun onCreate() {
        super.onCreate()
        AwakrNotifications.ensureChannels(this)
        data = AwakrData.create(this)
        appScope.launch {
            seedSampleAlarms()
            // Soft-deleted alarms only live until the next start; the undo window is a few seconds.
            runCatching { data.alarms.purgeDeleted() }
            // Safety net: the platform can drop alarms (force stop, restore, clock change).
            runCatching { scheduler.rescheduleAll(data.alarms.enabledAlarms()) }
            // The device may have been off at the scheduled minute; only the log comparison notices.
            MissedAlarmReporter.sweep(this@AwakrApplication)
        }
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
