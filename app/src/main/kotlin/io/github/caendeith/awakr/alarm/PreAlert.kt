package io.github.caendeith.awakr.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.caendeith.awakr.AwakrApplication
import io.github.caendeith.awakr.MainActivity
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.notify.AwakrNotifications
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Quiet "alarm in N minutes" reminder.
 *
 * A trigger is placed together with every scheduled alarm. Whether the user is
 * actually notified is decided when it fires, from the stored setting, so
 * changing "notify before" does not need to reschedule every alarm.
 */
internal object PreAlert {

    const val ACTION_PRE_ALERT = "io.github.caendeith.awakr.action.PRE_ALERT"

    private const val TAG = "PreAlert"
    private const val REQUEST_BASE = 900_000
    private const val WINDOW_MS = 5 * 60_000L
    private const val EXTRA_ALARM_ID = "alarmId"
    private const val EXTRA_TIME = "time"
    private const val EXTRA_LABEL = "label"
    private const val EXTRA_MINUTES = "minutes"

    fun schedule(context: Context, alarm: Alarm, triggerAt: Long) {
        val minutes = minutesBefore(context)
        if (minutes <= 0) {
            cancel(context, alarm.id)
            return
        }
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val at = triggerAt - minutes * 60_000L
        if (at <= System.currentTimeMillis()) {
            // The alarm is closer than the reminder window: nothing to warn about.
            cancel(context, alarm.id)
            return
        }
        val pending = pendingIntent(context, alarm.id) {
            Intent(context, PreAlertReceiver::class.java)
                .setAction(ACTION_PRE_ALERT)
                .setData(Uri.parse("awakr://pre-alert/${alarm.id}"))
                .putExtra(EXTRA_ALARM_ID, alarm.id)
                .putExtra(EXTRA_TIME, String.format("%02d:%02d", alarm.hour, alarm.minute))
                .putExtra(EXTRA_LABEL, alarm.label)
                .putExtra(EXTRA_MINUTES, minutes)
        }
        runCatching { manager.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, pending) }
            .onFailure { Log.w(TAG, "cannot schedule the reminder for alarm ${alarm.id}", it) }
    }

    fun cancel(context: Context, alarmId: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntent(context, alarmId) {
            Intent(context, PreAlertReceiver::class.java)
                .setAction(ACTION_PRE_ALERT)
                .setData(Uri.parse("awakr://pre-alert/$alarmId"))
        }
        runCatching { manager.cancel(pending) }
    }

    /** 0 disables the reminder. */
    fun minutesBefore(context: Context): Int = runCatching {
        val app = context.applicationContext as AwakrApplication
        runBlocking { app.data.settings.settings.first().preAlertMinutes }
    }.getOrDefault(0)

    fun notify(context: Context, time: String, label: String, minutes: Int, alarmId: Long) {
        AwakrNotifications.ensureChannels(context)
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) return

        val title = context.getString(R.string.pre_alert_title, minutes)
        val text = if (label.isBlank()) {
            context.getString(R.string.pre_alert_body, time)
        } else {
            context.getString(R.string.pre_alert_body_label, time, label)
        }
        val content = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, AwakrNotifications.CHANNEL_PRE_ALERT)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(AwakrNotifications.NOTIFICATION_PRE_ALERT + alarmId.toInt(), notification)
        }
    }

    private fun pendingIntent(context: Context, alarmId: Long, build: () -> Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + alarmId.toInt(),
            build(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** Fires a few minutes before an alarm; silent on purpose. */
class PreAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PreAlert.ACTION_PRE_ALERT) return
        if (PreAlert.minutesBefore(context) <= 0) return
        PreAlert.notify(
            context = context,
            time = intent.getStringExtra("time").orEmpty(),
            label = intent.getStringExtra("label").orEmpty(),
            minutes = intent.getIntExtra("minutes", 0),
            alarmId = intent.getLongExtra("alarmId", 0L),
        )
    }
}
