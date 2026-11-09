package io.github.caendeith.awakr.ui.sleep

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.ui.component.AwakrCard
import io.github.caendeith.awakr.core.ui.component.PrimaryButton
import io.github.caendeith.awakr.core.ui.component.TimeText
import io.github.caendeith.awakr.data.AwakrData
import io.github.caendeith.awakr.data.sleep.HealthConnectSleepSource
import io.github.caendeith.awakr.data.sleep.SleepAvailability
import io.github.caendeith.awakr.data.sleep.SleepStage
import io.github.caendeith.awakr.data.sleep.SleepStageType
import io.github.caendeith.awakr.ui.format.rememberNow
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as DayStyle
import java.util.Locale
import kotlinx.coroutines.launch

private val StageAwake = Color(0xFFFFB020)
private val StageRem = Color(0xFF6FD08C)
private val StageLight = Color(0xFF6FA8FF)
private val StageDeep = Color(0xFFB79CFF)
private val StageUnknown = Color(0xFF9AA0A8)

/**
 * Sleep imported from Health Connect, shown offline: last night's stages plus a seven-night bar
 * chart. The screen only reads the local database; the refresh button (and the periodic worker)
 * copy new nights in, and every unavailable state explains itself instead of failing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(data: AwakrData, onBack: () -> Unit) {
    val context = LocalContext.current
    val source = remember(context) { HealthConnectSleepSource(context.applicationContext) }
    var availability by remember(source) {
        mutableStateOf(runCatching { source.availability() }.getOrDefault(SleepAvailability.UNAVAILABLE))
    }
    var granted by remember(source) { mutableStateOf<Boolean?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var syncFailed by remember { mutableStateOf(false) }
    var autoSynced by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(contract = source.permissionContract()) { result ->
        granted = result.containsAll(source.requiredPermissions())
    }

    LaunchedEffect(source) {
        granted = runCatching {
            source.grantedPermissions().containsAll(source.requiredPermissions())
        }.getOrNull()
    }

    val sessions by remember(data) { data.sleep.observeAll() }.collectAsState(initial = emptyList())
    val now = rememberNow(intervalMs = 60_000L)
    val week = remember(sessions, now.toLocalDate()) { SleepWeekCalculator.compute(sessions, now) }
    val scope = rememberCoroutineScope()

    fun sync() {
        if (syncing) return
        syncing = true
        syncFailed = false
        scope.launch {
            when (val result = SleepSync.refresh(context)) {
                is SleepSyncResult.Imported -> granted = true
                SleepSyncResult.PermissionMissing -> granted = false
                SleepSyncResult.SourceUnavailable -> availability = SleepAvailability.UNAVAILABLE
                SleepSyncResult.UpdateRequired -> availability = SleepAvailability.UPDATE_REQUIRED
                is SleepSyncResult.Failed -> syncFailed = true
            }
            syncing = false
        }
    }

    LaunchedEffect(granted, sessions.isEmpty()) {
        if (!autoSynced && granted == true && sessions.isEmpty()) {
            autoSynced = true
            sync()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sleep_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { sync() },
                        enabled = !syncing && availability == SleepAvailability.AVAILABLE,
                    ) {
                        if (syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.sleep_sync),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (syncFailed) {
                item {
                    Text(
                        text = stringResource(R.string.sleep_sync_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            when {
                availability == SleepAvailability.UNAVAILABLE -> item {
                    MessageCard(
                        title = stringResource(R.string.sleep_unavailable_title),
                        body = stringResource(R.string.sleep_unavailable_body),
                        action = stringResource(R.string.sleep_retry),
                        onAction = {
                            availability = runCatching { source.availability() }
                                .getOrDefault(SleepAvailability.UNAVAILABLE)
                        },
                    )
                }

                availability == SleepAvailability.UPDATE_REQUIRED -> item {
                    MessageCard(
                        title = stringResource(R.string.sleep_update_title),
                        body = stringResource(R.string.sleep_update_body),
                        action = stringResource(R.string.sleep_retry),
                        onAction = {
                            availability = runCatching { source.availability() }
                                .getOrDefault(SleepAvailability.UNAVAILABLE)
                        },
                    )
                }

                granted == false -> item {
                    MessageCard(
                        title = stringResource(R.string.sleep_permission_title),
                        body = stringResource(R.string.sleep_permission_body),
                        action = stringResource(R.string.sleep_permission_action),
                        onAction = { permissionLauncher.launch(source.permissions()) },
                    )
                }

                week.nights.isEmpty() -> item {
                    EmptyStateCard(syncing = syncing, onSync = { sync() })
                }

                else -> {
                    week.lastNight?.let { night ->
                        item { LastNightCard(night) }
                    }
                    item { WeekCard(week) }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun MessageCard(title: String, body: String, action: String, onAction: () -> Unit) {
    AwakrCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        PrimaryButton(
            text = action,
            onClick = onAction,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptyStateCard(syncing: Boolean, onSync: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val moon = MaterialTheme.colorScheme.primary

    AwakrCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(modifier = Modifier.size(72.dp)) {
                val radius = size.minDimension / 2f
                drawCircle(color = moon, radius = radius, center = center)
                drawCircle(
                    color = surface,
                    radius = radius * 0.92f,
                    center = center + Offset(radius * 0.55f, -radius * 0.15f),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.sleep_empty_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.sleep_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            PrimaryButton(
                text = stringResource(R.string.sleep_sync_now),
                onClick = onSync,
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LastNightCard(night: SleepNight) {
    val locale = LocalConfiguration.current.locales[0]
    val dateText = remember(night.date, locale) {
        night.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }

    AwakrCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.sleep_last_night), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sleep_bedtime),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TimeText(
                    text = clockTime(night.bedtime.hour, night.bedtime.minute),
                    fontSize = 28.sp,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sleep_wake_up),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TimeText(
                    text = clockTime(night.wakeUp.hour, night.wakeUp.minute),
                    fontSize = 28.sp,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.sleep_duration_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val parts = durationParts(night.asleepMillis)
                Text(
                    text = stringResource(R.string.sleep_duration, parts.hours, parts.minutes),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        if (night.stages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp)),
            ) {
                night.stages.forEach { stage ->
                    Box(
                        modifier = Modifier
                            .weight(stage.durationMillis.coerceAtLeast(1L).toFloat())
                            .fillMaxHeight()
                            .background(stageColor(stage.type)),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            StageLegend(night.stages)
            Spacer(modifier = Modifier.height(12.dp))

            val chartDescription = stringResource(R.string.sleep_chart_description)
            Hypnogram(
                night = night,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .semantics { contentDescription = chartDescription },
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = clockTime(night.bedtime.hour, night.bedtime.minute),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = clockTime(night.wakeUp.hour, night.wakeUp.minute),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.sleep_no_stages),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (night.hrMin != null || night.hrAvg != null || night.hrMax != null || night.spo2Avg != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                night.hrMin?.let { Metric(stringResource(R.string.sleep_hr_min), it.toString()) }
                night.hrAvg?.let { Metric(stringResource(R.string.sleep_hr_avg), it.toString()) }
                night.hrMax?.let { Metric(stringResource(R.string.sleep_hr_max), it.toString()) }
                night.spo2Avg?.let {
                    Metric(
                        stringResource(R.string.sleep_spo2),
                        stringResource(
                            R.string.sleep_spo2_value,
                            String.format(Locale.getDefault(), "%.0f", it),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.Metric(label: String, value: String) {
    Column(modifier = Modifier.weight(1f)) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StageLegend(stages: List<SleepStage>) {
    val types = stages.map { it.type }.distinct().sortedBy { stageRank(it) }
    types.chunked(2).forEach { rowTypes ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            rowTypes.forEach { type ->
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(stageColor(type)),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stageLabel(type),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun stageLabel(type: SleepStageType): String = when (type) {
    SleepStageType.AWAKE -> stringResource(R.string.sleep_stage_awake)
    SleepStageType.REM -> stringResource(R.string.sleep_stage_rem)
    SleepStageType.LIGHT -> stringResource(R.string.sleep_stage_light)
    SleepStageType.DEEP -> stringResource(R.string.sleep_stage_deep)
    SleepStageType.UNKNOWN -> stringResource(R.string.sleep_none)
}

private fun stageColor(type: SleepStageType): Color = when (type) {
    SleepStageType.AWAKE -> StageAwake
    SleepStageType.REM -> StageRem
    SleepStageType.LIGHT -> StageLight
    SleepStageType.DEEP -> StageDeep
    SleepStageType.UNKNOWN -> StageUnknown
}

private fun stageRank(type: SleepStageType): Int = when (type) {
    SleepStageType.AWAKE -> 0
    SleepStageType.REM -> 1
    SleepStageType.LIGHT -> 2
    SleepStageType.DEEP -> 3
    SleepStageType.UNKNOWN -> 2
}

/** Step line of the night: awake on top, deep sleep at the bottom. */
@Composable
private fun Hypnogram(night: SleepNight, modifier: Modifier = Modifier) {
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Canvas(modifier = modifier) {
        val stages = night.stages.sortedBy { it.start }
        if (stages.isEmpty()) return@Canvas

        val from = stages.first().start
        val to = stages.last().end
        val span = (to - from).coerceAtLeast(1L)
        val top = 6.dp.toPx()
        val bottom = size.height - 6.dp.toPx()
        val step = (bottom - top) / 3f

        for (rank in 0..3) {
            val y = top + step * rank
            drawLine(
                color = grid,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        fun x(time: Long): Float = ((time - from).toFloat() / span.toFloat()) * size.width
        fun y(type: SleepStageType): Float = top + step * stageRank(type)

        var previousY: Float? = null
        for (stage in stages) {
            val color = stageColor(stage.type)
            val startX = x(stage.start)
            val endX = x(stage.end)
            val lineY = y(stage.type)
            drawLine(
                color = color,
                start = Offset(startX, lineY),
                end = Offset(endX, lineY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            previousY?.let { previous ->
                if (previous != lineY) {
                    drawLine(
                        color = color,
                        start = Offset(startX, previous),
                        end = Offset(startX, lineY),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
            previousY = lineY
        }
    }
}

@Composable
private fun WeekCard(week: SleepWeek) {
    val locale = LocalConfiguration.current.locales[0]

    AwakrCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.sleep_week_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.sleep_week_average),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val average = week.averageAsleepMillis
            Text(
                text = if (average == null) {
                    stringResource(R.string.sleep_none)
                } else {
                    val parts = durationParts(average)
                    stringResource(R.string.sleep_duration, parts.hours, parts.minutes)
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val chartDescription = stringResource(R.string.sleep_week_chart_description)
        WeekBars(
            week = week,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .semantics { contentDescription = chartDescription },
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            week.days.forEach { day ->
                val label = day.date.dayOfWeek.getDisplayName(DayStyle.SHORT, locale)
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.night == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (week.streakNights > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = pluralStringResource(R.plurals.sleep_streak, week.streakNights, week.streakNights),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekBars(week: SleepWeek, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val maxAsleep = week.days.mapNotNull { it.night?.asleepMillis }.maxOrNull()?.coerceAtLeast(1L) ?: 1L

    Canvas(modifier = modifier) {
        val slot = size.width / week.days.size
        val barWidth = slot * 0.5f
        val gap = slot - barWidth
        val topInset = 8.dp.toPx()
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

        week.days.forEachIndexed { index, day ->
            val left = index * slot + gap / 2f
            val night = day.night
            if (night == null) {
                drawCircle(
                    color = emptyColor,
                    radius = 2.dp.toPx(),
                    center = Offset(left + barWidth / 2f, size.height - 4.dp.toPx()),
                )
            } else {
                val height = (night.asleepMillis.toFloat() / maxAsleep.toFloat()) * (size.height - topInset)
                val isLatest = index == week.days.lastIndex
                drawRoundRect(
                    color = if (isLatest) barColor else barColor.copy(alpha = 0.55f),
                    topLeft = Offset(left, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = radius,
                )
            }
        }
    }
}
