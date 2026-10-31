package io.github.caendeith.awakr.alarm

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.notify.AwakrNotifications

/**
 * Last-resort alarm surface used when the ringing service cannot be started: a full-screen
 * alarm notification whose tap launches the ring screen (and the service behind it).
 */
object AlarmFallbackNotification {

    fun post(context: Context, alarmId: Long) {
        val manager = NotificationManagerCompat.from(context)
        val permissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted || !manager.areNotificationsEnabled()) {
            Log.w(TAG, "notifications are disabled, alarm $alarmId cannot be surfaced")
            return
        }
        val ringIntent = AlarmIntents.ringActivityIntent(context, alarmId)
        val pending = PendingIntent.getActivity(
            context,
            AlarmIntents.triggerRequestCode(alarmId),
            ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, AwakrNotifications.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(context.getString(R.string.ring_fallback_title))
            .setContentText(context.getString(R.string.ring_fallback_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .build()
        runCatching { manager.notify(AwakrNotifications.NOTIFICATION_RINGING, notification) }
            .onFailure { Log.e(TAG, "cannot post fallback notification", it) }
    }

    private const val TAG = "AlarmFallbackNotification"
}
