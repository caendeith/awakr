package io.github.caendeith.awakr.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.caendeith.awakr.MainActivity
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.notify.AwakrNotifications
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Posts one summary notification for occurrences the device slept through. */
internal object MissedAlarmNotification {

    fun post(context: Context, missed: List<MissedOccurrence>) {
        if (missed.isEmpty()) return
        AwakrNotifications.ensureChannels(context)

        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val zone = ZoneId.systemDefault()

        val text = if (missed.size == 1) {
            val time = Instant.ofEpochMilli(missed.first().scheduledAt).atZone(zone).format(formatter)
            context.getString(R.string.missed_single, time)
        } else {
            context.getString(R.string.missed_many, missed.size)
        }

        val content = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, AwakrNotifications.CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(context.getString(R.string.missed_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(AwakrNotifications.NOTIFICATION_MISSED, notification)
        }
    }
}