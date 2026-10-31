package io.github.caendeith.awakr.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

/** Notification channels and ids used by the alarm engine. */
object AwakrNotifications {

    const val CHANNEL_ALARM = "alarm"
    const val CHANNEL_MISSED = "missed"
    const val CHANNEL_SERVICE = "alarm_service"

    /** Id of the ongoing notification that keeps the ringing service alive. */
    const val NOTIFICATION_RINGING = 1001
    const val NOTIFICATION_MISSED = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALARM,
                context.getString(R.string.channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_alarm_description)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MISSED,
                context.getString(R.string.channel_missed_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_missed_description)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                context.getString(R.string.channel_service_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setSound(null, null)
                setShowBadge(false)
            },
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun ringChannelId(): String = CHANNEL_ALARM
}
