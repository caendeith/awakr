package io.github.caendeith.awakr.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.caendeith.awakr.AwakrApplication
import kotlinx.coroutines.launch

/**
 * Restores alarms after events that drop them from the platform:
 * reboot, app update, manual clock or time zone change, and exact-alarm permission changes.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return
        Log.i(TAG, "rescheduling alarms after $action")
        val app = context.applicationContext as? AwakrApplication ?: return
        app.appScope.launch {
            runCatching {
                val alarms = app.data.alarms.enabledAlarms()
                app.scheduler.rescheduleAll(alarms)
            }.onFailure { Log.e(TAG, "reschedule failed", it) }

            // A reboot is exactly when an alarm can have been missed while the device was off.
            if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
                MissedAlarmReporter.sweep(app)
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
