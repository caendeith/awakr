package io.github.caendeith.awakr.alarm

import android.util.Log
import io.github.caendeith.awakr.AwakrApplication
import io.github.caendeith.awakr.core.alarm.AlarmLog
import io.github.caendeith.awakr.core.alarm.AlarmOutcome
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Writes MISSED rows for occurrences the device slept through and posts a single notification.
 *
 * Runs on boot and on app start: the device can be powered off at the scheduled minute, in which
 * case no alarm receiver ever fires and only the log comparison notices.
 *
 * A boot delivers both LOCKED_BOOT_COMPLETED and BOOT_COMPLETED, so two sweeps can run while the
 * application start runs a third one. The mutex keeps them apart: the next sweep re-reads the log
 * table inside the lock and finds nothing left to record.
 */
internal object MissedAlarmReporter {

    private const val TAG = "MissedAlarmReporter"

    private val mutex = Mutex()

    suspend fun sweep(app: AwakrApplication) {
        mutex.withLock {
            runCatching { sweepLocked(app) }
                .onFailure { Log.e(TAG, "missed alarm sweep failed", it) }
        }
    }

    private suspend fun sweepLocked(app: AwakrApplication) {
        val now = System.currentTimeMillis()
        val alarms = app.data.alarms.enabledAlarms()
        if (alarms.isEmpty()) return
        val logs = app.data.alarmLogs.scheduledSince(now - MissedAlarms.MAX_AGE_MS)
        val missed = MissedAlarms.find(alarms, logs, now)
        if (missed.isEmpty()) return

        missed.forEach { occurrence ->
            app.data.alarmLogs.append(
                AlarmLog(
                    alarmId = occurrence.alarm.id,
                    scheduledAt = occurrence.scheduledAt,
                    outcome = AlarmOutcome.MISSED,
                    missionPluginId = occurrence.alarm.missionPluginId,
                ),
            )
        }
        Log.i(TAG, "recorded " + missed.size + " missed occurrence(s)")
        MissedAlarmNotification.post(app, missed)
    }
}