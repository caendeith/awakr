package io.github.caendeith.awakr.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import io.github.caendeith.awakr.AwakrApplication
import io.github.caendeith.awakr.core.time.NextOccurrence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** Hosts [AlarmWidget]; the manifest entry the app still needs is described in the task report. */
class AlarmWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AlarmWidget()
}

/**
 * "Skip next" action: pushes the ringing of the next alarm past one occurrence.
 *
 * Mirrors the in-app skip in `AwakrNavHost`: remember the moment being skipped in
 * `skipNextAt`, re-arm the system alarm and let the widget redraw.
 */
class SkipNextAlarmAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as? AwakrApplication ?: return
        val now = ZonedDateTime.now()
        val next = AlarmWidgetLogic.nextAlarm(app.data.alarms.enabledAlarms(), now) ?: return

        // Same rule as the alarm list: skip the occurrence that would fire next.
        // Passing the existing skip makes a second tap advance one more occurrence instead of
        // re-skipping the same instant.
        val skipped = NextOccurrence.next(now, next.hour, next.minute, next.daysMask, next.skipNextAt)
        app.data.alarms.setSkipNextAt(next.id, skipped?.toInstant()?.toEpochMilli())
        app.data.alarms.get(next.id)?.let { alarm -> app.scheduler.schedule(alarm) }

        AlarmWidgetUpdater.update(context.applicationContext)
    }
}

/** Redraw trigger for call sites that change alarm data. */
object AlarmWidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Suspending refresh, for WorkManager-free call sites that already run on a coroutine. */
    suspend fun update(context: Context) {
        AlarmWidget().updateAll(context.applicationContext)
    }

    /** Fire-and-forget refresh for click handlers and other non-suspending code. */
    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        scope.launch { runCatching { update(appContext) } }
    }
}
