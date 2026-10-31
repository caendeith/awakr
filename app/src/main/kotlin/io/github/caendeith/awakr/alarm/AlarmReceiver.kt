package io.github.caendeith.awakr.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Receives the platform alarm trigger and hands it to the ringing service. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmIntents.ACTION_RING) return
        val alarmId = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, 0L)
        if (alarmId == 0L) return
        Log.i(TAG, "alarm $alarmId fired")
        val triggerAt = intent.getLongExtra(AlarmIntents.EXTRA_TRIGGER_AT, 0L)
        val snoozeCount = intent.getIntExtra(AlarmIntents.EXTRA_SNOOZE_COUNT, 0)
        runCatching { AlarmRingService.start(context, alarmId, triggerAt, snoozeCount) }
            .onFailure { error ->
                // Background foreground-service starts can be refused when the alarm did not
                // come from the platform alarm manager (manual trigger, restored state).
                // The notification still surfaces the alarm, and tapping it starts the service.
                Log.e(TAG, "cannot start ring service for alarm $alarmId", error)
                AlarmFallbackNotification.post(context, alarmId)
            }
    }

    private companion object {
        const val TAG = "AlarmReceiver"
    }
}
