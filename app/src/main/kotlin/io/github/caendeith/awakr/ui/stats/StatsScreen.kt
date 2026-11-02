package io.github.caendeith.awakr.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.alarm.AlarmLog
import io.github.caendeith.awakr.core.ui.component.AwakrCard
import io.github.caendeith.awakr.core.ui.component.TimeText
import io.github.caendeith.awakr.core.ui.theme.PillShape
import io.github.caendeith.awakr.data.stats.CircularTime
import io.github.caendeith.awakr.data.stats.StatsCalculator
import io.github.caendeith.awakr.data.stats.StatsDay
import io.github.caendeith.awakr.data.stats.StatsRange
import io.github.caendeith.awakr.data.stats.StatsSummary
import io.github.caendeith.awakr.ui.format.rememberNow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Offline wake-up statistics built from `alarm_logs`: four KPI cards, the circular-mean
 * wake-up time and a per-day scatter chart drawn with Canvas, so no chart dependency is
 * needed. Everything is computed by [StatsCalculator] from the rows passed in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    logs: List<AlarmLog>,
    onBack: () -> Unit,
) {
    var range by rememberSaveable { mutableStateOf(StatsRange.WEEK) }
    // The wall clock only has to advance far enough to roll the window over at midnight.
    val now = rememberNow(intervalMs = 60_000L)
    val today = now.toLocalDate()
    val summary = remember(logs, range, today) { StatsCalculator.compute(logs, range, now) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            RangeSwitch(range = range, onSelect = { range = it })
            Spacer(Modifier.height(4.dp))
            if (summary.isEmpty) {
                EmptyStats(modifier = Modifier.fillMaxWidth().weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "average") { AverageCard(summary) }
                    item(key = "kpi") { KpiGrid(summary) }
                    item(key = "chart") { ChartCard(summary) }
                    item(key = "streak") { StreakCard(summary) }
                }
            }
        }
    }
}

/** Two-option pill switch mirroring the pill language used on the Home screen. */
@Composable
private fun RangeSwitch(range: StatsRange, onSelect: (StatsRange) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StatsRange.entries.forEach { option ->
                val selected = option == range
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = PillShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                ) {
                    Text(
                        text = stringResource(option.labelRes()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            )
                            .padding(vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** The circular-mean wake-up time, presented as the hero of the screen. */
@Composable
private fun AverageCard(summary: StatsSummary) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val average = summary.averageWakeUpMinuteOfDay

    AwakrCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.stats_average_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (average == null) {
            Text(
                text = stringResource(R.string.stats_value_none),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.stats_average_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TimeText(text = formatMinuteOfDay(average, formatter), fontSize = 48.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.stats_average_basis, summary.successfulWakeUps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KpiGrid(summary: StatsSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard(
                label = stringResource(R.string.stats_kpi_successful),
                value = summary.successfulWakeUps.toString(),
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(R.string.stats_kpi_missed),
                value = summary.missed.toString(),
                valueColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard(
                label = stringResource(R.string.stats_kpi_snoozes),
                value = summary.snoozes.toString(),
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(R.string.stats_kpi_rate),
                value = successRateText(summary.successRate),
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    AwakrCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = valueColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun successRateText(rate: Float?): String =
    if (rate == null) {
        stringResource(R.string.stats_value_none)
    } else {
        stringResource(R.string.stats_value_percent, (rate * 100f).roundToInt())
    }

@Composable
private fun ChartCard(summary: StatsSummary) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val dayLabels = remember(summary.days, summary.range, locale) {
        buildDayLabels(summary.days, summary.range, locale)
    }
    val hasPoints = summary.days.any { it.wakeUpMinuteOfDay != null }

    AwakrCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.stats_chart_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.stats_chart_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            WakeUpChart(
                summary = summary,
                dayLabels = dayLabels,
                formatter = formatter,
                modifier = Modifier.matchParentSize(),
            )
            if (!hasPoints) {
                Text(
                    text = stringResource(R.string.stats_chart_empty),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Scatter plot of the time of day the user got up on each day of the window.
 *
 * Time runs from midnight at the top to midnight at the bottom, so a point near the top is
 * an early rise and a point near the bottom is a late one. Missing days simply leave gaps
 * in the line; the axis and day labels come from the same [TextMeasurer], which keeps them
 * aligned with the points at any font scale.
 */
@Composable
private fun WakeUpChart(
    summary: StatsSummary,
    dayLabels: List<String?>,
    formatter: DateTimeFormatter,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val pointColor = MaterialTheme.colorScheme.primary
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = axisColor)
    val description = stringResource(R.string.stats_chart_description, summary.range.days)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = description },
    ) {
        val axisLabels = listOf(0, 6, 12, 18).map { hour ->
            hour to textMeasurer.measure(formatter.format(LocalTime.of(hour, 0)), labelStyle)
        }
        val gutter = axisLabels.maxOf { (_, layout) -> layout.size.width } + 10.dp.toPx()
        val plotTop = 4.dp.toPx()
        val labelArea = 22.dp.toPx()
        val plotBottom = size.height - labelArea
        val plotLeft = gutter
        val plotRight = size.width - 2.dp.toPx()
        val plotHeight = plotBottom - plotTop
        if (plotHeight <= 0f || plotRight <= plotLeft) return@Canvas

        for ((hour, layout) in axisLabels) {
            val y = plotTop + plotHeight * hour / 24f
            drawLine(
                color = gridColor,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.dp.toPx(),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = (gutter - 6.dp.toPx() - layout.size.width).coerceAtLeast(0f),
                    y = (y - layout.size.height / 2f)
                        .coerceIn(0f, size.height - layout.size.height),
                ),
            )
        }
        drawLine(
            color = gridColor,
            start = Offset(plotLeft, plotBottom),
            end = Offset(plotRight, plotBottom),
            strokeWidth = 1.dp.toPx(),
        )

        val step = (plotRight - plotLeft) / summary.days.size
        val points = mutableListOf<Offset>()
        val path = Path()
        var connected = false
        summary.days.forEachIndexed { index, day ->
            val minute = day.wakeUpMinuteOfDay
            if (minute == null) {
                connected = false
                return@forEachIndexed
            }
            val x = plotLeft + step * (index + 0.5f)
            val y = (plotTop + plotHeight * (minute / CircularTime.MINUTES_PER_DAY).toFloat())
                .coerceIn(plotTop, plotBottom)
            points += Offset(x, y)
            if (connected) path.lineTo(x, y) else path.moveTo(x, y)
            connected = true
        }
        drawPath(
            path = path,
            color = pointColor.copy(alpha = 0.4f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
        )
        val dotRadius = if (summary.range == StatsRange.WEEK) 3.5.dp.toPx() else 2.dp.toPx()
        points.forEach { point ->
            drawCircle(color = pointColor.copy(alpha = 0.18f), radius = dotRadius * 2.2f, center = point)
            drawCircle(color = pointColor, radius = dotRadius, center = point)
        }

        val labelTop = plotBottom + 5.dp.toPx()
        dayLabels.forEachIndexed { index, label ->
            if (label == null) return@forEachIndexed
            val layout = textMeasurer.measure(label, labelStyle)
            val x = plotLeft + step * (index + 0.5f) - layout.size.width / 2f
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = x.coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f)),
                    y = labelTop,
                ),
            )
        }
    }
}

@Composable
private fun StreakCard(summary: StatsSummary) {
    AwakrCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.stats_streak_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.stats_streak_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.stats_streak_value, summary.streakDays),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Centred empty state with a muted bar glyph drawn in the same accent as the chart. */
@Composable
private fun EmptyStats(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val artColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
        Canvas(modifier = Modifier.size(width = 140.dp, height = 96.dp)) {
            val barWidth = 18.dp.toPx()
            val gap = 14.dp.toPx()
            val fractions = listOf(0.45f, 0.75f, 0.6f, 0.9f)
            fractions.forEachIndexed { index, fraction ->
                val barHeight = size.height * fraction
                drawRoundRect(
                    color = artColor,
                    topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.stats_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.stats_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun StatsRange.labelRes(): Int = when (this) {
    StatsRange.WEEK -> R.string.stats_range_week
    StatsRange.MONTH -> R.string.stats_range_month
}

private fun formatMinuteOfDay(minuteOfDay: Double, formatter: DateTimeFormatter): String {
    val seconds = (minuteOfDay * 60.0).roundToLong()
        .coerceIn(0L, CircularTime.MINUTES_PER_DAY * 60L - 1L)
    return formatter.format(LocalTime.ofSecondOfDay(seconds))
}

private fun buildDayLabels(
    days: List<StatsDay>,
    range: StatsRange,
    locale: Locale,
): List<String?> {
    val formatter = DateTimeFormatter.ofPattern(
        if (range == StatsRange.WEEK) "EE" else "d",
        locale,
    )
    return when (range) {
        StatsRange.WEEK -> days.map { formatter.format(it.date) }
        // Thirty columns are too narrow for a label each, so date the fifth day and today.
        StatsRange.MONTH -> days.mapIndexed { index, day ->
            if (index % 5 == 4 || index == days.lastIndex) {
                formatter.format(day.date)
            } else {
                null
            }
        }
    }
}
