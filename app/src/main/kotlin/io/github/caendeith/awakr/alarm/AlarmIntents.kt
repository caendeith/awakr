package io.github.caendeith.awakr.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.caendeith.awakr.MainActivity
import io.github.caendeith.awakr.ui.ring.RingActivity

/** Intent contract between the scheduler, the receiver, the service and the ring screen. */
object AlarmIntents {

    const val ACTION_RING = "io.github.caendeith.awakr.action.RING"
    const val ACTION_DISMISS = "io.github.caendeith.awakr.action.DISMISS"
    const val ACTION_SNOOZE = "io.github.caendeith.awakr.action.SNOOZE"
    const val ACTION_STOP_SERVICE = "io.github.caendeith.awakr.action.STOP_SERVICE"

    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_TRIGGER_AT = "trigger_at"
    const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
    const val EXTRA_LABEL = "label"
    const val EXTRA_TIME = "time"

    /** Request code of the alarm trigger PendingIntent; snooze uses the next range. */
    fun triggerRequestCode(alarmId: Long): Int = (alarmId xor (alarmId ushr 32)).toInt()

    fun snoozeRequestCode(alarmId: Long): Int = triggerRequestCode(alarmId) + 1_000_000

    fun ringIntent(context: Context, alarmId: Long, triggerAt: Long = 0L): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_RING
            putExtra(EXTRA_ALARM_ID, alarmId)
            if (triggerAt > 0L) putExtra(EXTRA_TRIGGER_AT, triggerAt)
        }

    fun ringPendingIntent(
        context: Context,
        alarmId: Long,
        snooze: Boolean = false,
        triggerAt: Long = 0L,
    ): PendingIntent {
        val requestCode = if (snooze) snoozeRequestCode(alarmId) else triggerRequestCode(alarmId)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            ringIntent(context, alarmId, triggerAt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun ringActivityIntent(context: Context, alarmId: Long): Intent =
        Intent(context, RingActivity::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    fun ringActivityPendingIntent(context: Context, alarmId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            triggerRequestCode(alarmId),
            ringActivityIntent(context, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
