package io.github.caendeith.awakr.alarm

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.util.Log
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.time.NextOccurrence
import java.time.ZonedDateTime

/**
 * Places alarms with the platform.
 *
 * The primary trigger uses [AlarmManager.setAlarmClock], which needs no special
 * permission, survives doze, and is shown to the user as an upcoming alarm in the
 * status bar. Snooze uses an exact allow-while-idle alarm instead, so a snoozed
 * alarm does not reappear in the system alarm indicator.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun schedule(alarm: Alarm) {
        val manager = alarmManager ?: return
        if (!alarm.enabled) {
            cancel(alarm.id)
            return
        }
        val now = ZonedDateTime.now()
        val next = NextOccurrence.next(
            now = now,
            hour = alarm.hour,
            minute = alarm.minute,
            daysMask = alarm.daysMask,
            skipNextAt = alarm.skipNextAt,
        )
        if (next == null) {
            Log.w(TAG, "no next occurrence for alarm ${alarm.id}")
            cancel(alarm.id)
            return
        }
        val triggerAt = next.toInstant().toEpochMilli()
        val operation = AlarmIntents.ringPendingIntent(context, alarm.id, triggerAt = triggerAt)
        val showIntent = AlarmIntents.ringActivityPendingIntent(context, alarm.id)
        try {
            if (canScheduleExact(manager)) {
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), operation)
                Log.i(TAG, "alarm ${alarm.id} scheduled at $next")
            } else {
                // Without the exact-alarm permission the platform may delay the trigger,
                // but the alarm still fires instead of being lost.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
                Log.w(TAG, "inexact fallback for alarm ${alarm.id} at $next")
            }
            PreAlert.schedule(context, alarm, triggerAt)
        } catch (e: SecurityException) {
            Log.e(TAG, "cannot schedule alarm ${alarm.id}", e)
        }
    }

    fun scheduleSnooze(alarm: Alarm, minutes: Int, snoozeCount: Int = 0) {
        val manager = alarmManager ?: return
        val triggerAt = System.currentTimeMillis() + minutes.coerceIn(1, 120) * 60_000L
        val operation = AlarmIntents.ringPendingIntent(
            context,
            alarm.id,
            snooze = true,
            triggerAt = triggerAt,
            snoozeCount = snoozeCount,
        )
        try {
            if (canScheduleExact(manager)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
            Log.i(TAG, "snooze for alarm ${alarm.id} in $minutes min")
        } catch (e: SecurityException) {
            Log.e(TAG, "cannot schedule snooze for alarm ${alarm.id}", e)
        }
    }

    fun cancel(alarmId: Long) {
        alarmManager?.cancel(AlarmIntents.ringPendingIntent(context, alarmId))
        alarmManager?.cancel(AlarmIntents.ringPendingIntent(context, alarmId, snooze = true))
        PreAlert.cancel(context, alarmId)
    }

    fun rescheduleAll(alarms: List<Alarm>) {
        alarms.forEach { if (it.enabled) schedule(it) else cancel(it.id) }
    }

    private fun canScheduleExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private companion object {
        const val TAG = "AlarmScheduler"
    }
}
