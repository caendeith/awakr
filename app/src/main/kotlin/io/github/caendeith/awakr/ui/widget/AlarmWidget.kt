package io.github.caendeith.awakr.ui.widget

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.caendeith.awakr.AwakrApplication
import io.github.caendeith.awakr.MainActivity
import io.github.caendeith.awakr.R
import java.time.ZonedDateTime

/**
 * Home screen widget: the next alarm that will ring, or an invitation to add one.
 *
 * The widget refreshes only when the app tells it to (updatePeriodMillis is 0), so nothing is
 * polled from the home screen process.
 */
class AlarmWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = AlarmWidgetLogic.state(
            alarms = enabledAlarms(context),
            now = ZonedDateTime.now(),
            locale = context.resources.configuration.locales[0],
            words = AlarmWidgetLogic.Words(
                once = context.getString(R.string.repeat_once),
                everyDay = context.getString(R.string.repeat_every_day),
            ),
        )
        val texts = AlarmWidgetTexts(
            skip = context.getString(R.string.widget_skip_next),
            noAlarms = context.getString(R.string.widget_no_alarms),
            noAlarmsHint = context.getString(R.string.widget_no_alarms_hint),
        )

        provideContent {
            AlarmWidgetTheme {
                WidgetFrame(state, texts)
            }
        }
    }

    /** Enabled, not deleted alarms; an uninitialised application yields an empty widget, not a crash. */
    private suspend fun enabledAlarms(context: Context): List<io.github.caendeith.awakr.core.alarm.Alarm> {
        val app = context.applicationContext as? AwakrApplication ?: return emptyList()
        return runCatching { app.data.alarms.enabledAlarms() }.getOrNull().orEmpty()
    }
}

/** Strings resolved once per render, so the composable never needs a Context. */
private data class AlarmWidgetTexts(val skip: String, val noAlarms: String, val noAlarmsHint: String)

/** The app is dark in both system themes, so light and dark share one scheme. */
private val WidgetColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB020),
    onPrimary = Color(0xFF241800),
    background = Color(0xFF0B0C0E),
    onBackground = Color(0xFFE9EAEC),
    surface = Color(0xFF14161A),
    onSurface = Color(0xFFE9EAEC),
    surfaceVariant = Color(0xFF1B1E24),
    onSurfaceVariant = Color(0xFF9AA0A8),
    outline = Color(0xFF2E333B),
)

@Composable
private fun AlarmWidgetTheme(content: @Composable () -> Unit) {
    GlanceTheme(colors = ColorProviders(light = WidgetColorScheme, dark = WidgetColorScheme), content = content)
}

@Composable
private fun WidgetFrame(state: AlarmWidgetState, texts: AlarmWidgetTexts) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(20.dp),
    ) {
        if (state.hasAlarm) {
            NextAlarmRow(state, texts)
        } else {
            NoAlarmBody(texts)
        }
    }
}

@Composable
private fun NextAlarmRow(state: AlarmWidgetState, texts: AlarmWidgetTexts) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        // Amber dot marking a live alarm.
        Box(
            modifier = GlanceModifier
                .size(10.dp)
                .cornerRadius(6.dp)
                .background(GlanceTheme.colors.primary),
        ) {}
        Spacer(GlanceModifier.width(12.dp))
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity(MainActivity::class.java)),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = state.timeText,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 48.sp,
                    // glance-text 1.2.0 only exposes Normal/Medium/Bold; the 48sp monospace face
                    // already matches the app's light time style.
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                ),
            )
            Text(
                text = state.subtitle,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
        }
        if (state.canSkip) {
            Text(
                text = texts.skip,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = GlanceModifier
                    .padding(start = 8.dp)
                    .clickable(actionRunCallback<SkipNextAlarmAction>()),
            )
        }
    }
}

@Composable
private fun NoAlarmBody(texts: AlarmWidgetTexts) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(MainActivity::class.java)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            Text(
                text = texts.noAlarms,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = texts.noAlarmsHint,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
        }
    }
}
