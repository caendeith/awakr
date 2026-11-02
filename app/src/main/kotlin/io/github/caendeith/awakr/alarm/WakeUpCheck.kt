package io.github.caendeith.awakr.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import io.github.caendeith.awakr.AwakrApplication
import io.github.caendeith.awakr.MainActivity
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.notify.AwakrNotifications
import io.github.caendeith.awakr.ui.format.formatTime
import kotlinx.coroutines.flow.first

/**
 * "Wake up check": a quiet "are you really up?" notification after a dismissal.
 *
 * The check is placed when the user dismisses an alarm. It gets a notification asking for a
 * confirmation; when that does not arrive before [WakeUpCheckPolicy.ESCALATION_DELAY_MS] the same
 * alarm rings again. The pending check is per alarm and is persisted, so a reboot can re-arm it.
 *
 * Only a dismissal starts a check. A snooze cancels it, and the re-ring itself never starts
 * another one: one dismissal, at most one re-ring.
 */
internal object WakeUpCheck {

    const val ACTION_CHECK = "io.github.caendeith.awakr.action.WAKE_UP_CHECK"
    const val ACTION_CONFIRM = "io.github.caendeith.awakr.action.WAKE_UP_CHECK_CONFIRM"
    const val ACTION_ESCALATE = "io.github.caendeith.awakr.action.WAKE_UP_CHECK_ESCALATE"

    const val EXTRA_ALARM_ID = "alarm_id"

    private const val TAG = "WakeUpCheck"

    /** Pending checks live here so a reboot can re-arm them; nothing else reads this file. */
    private const val PREFS_NAME = "awakr_wake_up_check"

    /** Separate request-code ranges so the three triggers can never overwrite each other. */
    private const val REQUEST_CHECK = 1_100_000
    private const val REQUEST_ESCALATE = 1_200_000
    private const val REQUEST_CONFIRM = 1_300_000

    /** Fallback window when the exact-alarm permission is missing (never granted to this app). */
    private const val CHECK_WINDOW_MS = 60_000L

    /**
     * Places a pending check after an alarm was dismissed.
     *
     * Reads the setting here rather than at scheduling time, so turning the feature on or off
     * never has to rewrite the platform alarms.
     */
    suspend fun arm(context: Context, alarm: Alarm, snoozeCount: Int) {
        val minutes = minutes(context)
        if (minutes <= 0) {
            cancel(context, alarm.id)
            return
        }
        val now = System.currentTimeMillis()
        val checkAt = now + minutes * 60_000L
        val pending = PendingWakeUpCheck(
            alarmId = alarm.id,
            checkAt = checkAt,
            escalateAt = WakeUpCheckPolicy.escalationAt(checkAt),
            snoozeCount = snoozeCount,
            time = formatTime(alarm.hour, alarm.minute),
            label = alarm.label,
        )
        store(context, pending)
        schedule(context, pending, WakeUpCheckPlan(checkAt = checkAt, escalateAt = pending.escalateAt))
        Log.i(TAG, "check for alarm ${alarm.id} in $minutes min, re-ring at ${pending.escalateAt}")
    }

    /**
     * Re-arms every stored check after a reboot or a package update.
     *
     * A check whose deadline already passed is dropped instead of fired: the moment for the
     * reminder is gone, and ringing for an alarm that ended before the reboot would be wrong.
     */
    fun rearm(context: Context) {
        val now = System.currentTimeMillis()
        pending(context).forEach { check ->
            val plan = WakeUpCheckPolicy.plan(check.checkAt, check.escalateAt, now)
            if (plan == null) {
                Log.i(TAG, "dropping stale check for alarm ${check.alarmId}")
                cancel(context, check.alarmId)
            } else {
                schedule(context, check, plan)
            }
        }
    }

    /** Drops the check of one alarm: platform triggers, stored state and notification. */
    fun cancel(context: Context, alarmId: Long) {
        if (alarmId <= 0L) return
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        runCatching {
            manager?.cancel(broadcast(context, alarmId, ACTION_CHECK))
            manager?.cancel(broadcast(context, alarmId, ACTION_ESCALATE))
        }
        forget(context, alarmId)
    }

    /** The user confirmed being awake: nothing is left to ring. */
    fun confirm(context: Context, alarmId: Long) {
        Log.i(TAG, "alarm $alarmId confirmed, check cancelled")
        cancel(context, alarmId)
    }

    /** Handles the check moment: show the question, or ring at once when it is already too late. */
    fun onCheck(context: Context, alarmId: Long) {
        val check = find(context, alarmId)
        if (check == null) {
            Log.i(TAG, "no pending check for alarm $alarmId, ignoring")
            return
        }
        if (WakeUpCheckPolicy.isExpired(check.checkAt, System.currentTimeMillis())) {
            // A doze window can push the check past its own deadline; asking now and ringing 100
            // seconds later would only delay the alarm the user already missed.
            Log.i(TAG, "check for alarm $alarmId is late, ringing instead")
            escalate(context, check)
            return
        }
        notify(context, check)
        // Re-arm the deadline: a duplicate delivery or a reboot between the two triggers must not
        // lose the re-ring.
        val plan = WakeUpCheckPlan(checkAt = null, escalateAt = check.escalateAt)
        schedule(context, check, plan)
    }

    /** Handles the deadline: the user did not confirm, so the alarm rings again. */
    fun onEscalate(context: Context, alarmId: Long) {
        val check = find(context, alarmId)
        if (check == null) {
            Log.i(TAG, "no pending check for alarm $alarmId, nothing to escalate")
            return
        }
        if (WakeUpCheckPolicy.isStale(check.escalateAt, System.currentTimeMillis())) {
            Log.i(TAG, "escalation for alarm $alarmId is stale, dropping")
            cancel(context, alarmId)
            return
        }
        escalate(context, check)
    }

    /** Pending checks, oldest first. Used by the boot path and by tests of the storage format. */
    fun pending(context: Context): List<PendingWakeUpCheck> =
        prefs(context).all.values
            .mapNotNull { WakeUpCheckPolicy.decode(it as? String) }
            .sortedBy { it.checkAt }

    private fun escalate(context: Context, check: PendingWakeUpCheck) {
        // The check is consumed before the service starts: a second delivery for this alarm must
        // not ring on top of the first one.
        forget(context, check.alarmId)
        runCatching {
            AlarmRingService.start(
                context = context,
                alarmId = check.alarmId,
                snoozeCount = check.snoozeCount,
                wakeUpCheck = true,
            )
        }.onFailure { error ->
            // Same fallback as a platform alarm: a notification that opens the ring screen, in
            // case the foreground service start is refused from the background.
            Log.e(TAG, "cannot ring alarm ${check.alarmId} again", error)
            AlarmFallbackNotification.post(context, check.alarmId)
        }
    }

    private fun schedule(context: Context, check: PendingWakeUpCheck, plan: WakeUpCheckPlan) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val exact = canScheduleExact(manager)
        val checkAt = plan.checkAt
        if (checkAt != null) {
            runCatching {
                val trigger = broadcast(context, check.alarmId, ACTION_CHECK)
                if (exact) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkAt, trigger)
                } else {
                    manager.setWindow(AlarmManager.RTC_WAKEUP, checkAt, CHECK_WINDOW_MS, trigger)
                }
            }.onFailure { Log.w(TAG, "cannot place the check for alarm ${check.alarmId}", it) }
        }
        runCatching {
            val trigger = broadcast(context, check.alarmId, ACTION_ESCALATE)
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.escalateAt, trigger)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.escalateAt, trigger)
            }
        }.onFailure { Log.w(TAG, "cannot place the re-ring for alarm ${check.alarmId}", it) }
    }

    private fun notify(context: Context, check: PendingWakeUpCheck) {
        AwakrNotifications.ensureChannels(context)
        val manager = NotificationManagerCompat.from(context)
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!permissionGranted || !manager.areNotificationsEnabled()) {
            // The re-ring is armed anyway: the alarm still has to ring even when the question
            // could not be asked.
            Log.w(TAG, "notifications are disabled, the check for alarm ${check.alarmId} is not shown")
            return
        }
        val body = if (check.label.isBlank()) {
            context.getString(R.string.wake_up_check_body, check.time)
        } else {
            context.getString(R.string.wake_up_check_body_label, check.time, check.label)
        }
        val content = PendingIntent.getActivity(
            context,
            REQUEST_CONFIRM + check.alarmId.toInt(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, AwakrNotifications.CHANNEL_WAKE_UP_CHECK)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(context.getString(R.string.wake_up_check_title))
            .setContentText(body)
            .setContentIntent(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // Only the "I am up!" action answers the question. Tapping the body opens the app and
            // must not make the question disappear as if it had been answered.
            .setAutoCancel(false)
            .addAction(0, context.getString(R.string.wake_up_check_action), confirmIntent(context, check.alarmId))
            .build()
        runCatching { manager.notify(notificationId(check.alarmId), notification) }
            .onFailure { Log.e(TAG, "cannot post the check for alarm ${check.alarmId}", it) }
    }

    private fun confirmIntent(context: Context, alarmId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CONFIRM + alarmId.toInt(),
            Intent(context, WakeUpCheckReceiver::class.java)
                .setAction(ACTION_CONFIRM)
                .setData("awakr://wake-up-check/confirm/$alarmId".toUri())
                .putExtra(EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun broadcast(context: Context, alarmId: Long, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(action) + alarmId.toInt(),
            Intent(context, receiverFor(action))
                .setAction(action)
                .setData("awakr://wake-up-check/$action/$alarmId".toUri())
                .putExtra(EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun requestCode(action: String): Int = when (action) {
        ACTION_ESCALATE -> REQUEST_ESCALATE
        else -> REQUEST_CHECK
    }

    private fun receiverFor(action: String): Class<out BroadcastReceiver> =
        if (action == ACTION_ESCALATE) WakeUpEscalationReceiver::class.java else WakeUpCheckReceiver::class.java

    private fun notificationId(alarmId: Long): Int =
        AwakrNotifications.NOTIFICATION_WAKE_UP_CHECK + alarmId.toInt()

    private fun find(context: Context, alarmId: Long): PendingWakeUpCheck? =
        WakeUpCheckPolicy.decode(prefs(context).getString(key(alarmId), null))

    private fun store(context: Context, check: PendingWakeUpCheck) {
        prefs(context).edit { putString(key(check.alarmId), WakeUpCheckPolicy.encode(check)) }
    }

    private fun forget(context: Context, alarmId: Long) {
        prefs(context).edit { remove(key(alarmId)) }
        runCatching {
            NotificationManagerCompat.from(context).cancel(notificationId(alarmId))
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(alarmId: Long): String = "alarm_$alarmId"

    private suspend fun minutes(context: Context): Int = runCatching {
        val app = context.applicationContext as AwakrApplication
        WakeUpCheckPolicy.normalizeMinutes(app.data.settings.settings.first().wakeUpCheckMinutes)
    }.getOrDefault(0)

    private fun canScheduleExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
}

/** Shows the "are you up?" question and handles the confirmation tap. */
class WakeUpCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(WakeUpCheck.EXTRA_ALARM_ID, 0L)
        if (alarmId == 0L) return
        when (intent.action) {
            WakeUpCheck.ACTION_CHECK -> WakeUpCheck.onCheck(context, alarmId)
            WakeUpCheck.ACTION_CONFIRM -> WakeUpCheck.confirm(context, alarmId)
        }
    }
}

/** The deadline passed without a confirmation: ring the alarm again. */
class WakeUpEscalationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WakeUpCheck.ACTION_ESCALATE) return
        val alarmId = intent.getLongExtra(WakeUpCheck.EXTRA_ALARM_ID, 0L)
        if (alarmId == 0L) return
        WakeUpCheck.onEscalate(context, alarmId)
    }
}
