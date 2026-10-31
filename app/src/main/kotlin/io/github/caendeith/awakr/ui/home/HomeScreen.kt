package io.github.caendeith.awakr.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.ui.component.AwakrCard
import io.github.caendeith.awakr.core.ui.component.TimeText
import io.github.caendeith.awakr.core.ui.theme.PillShape
import io.github.caendeith.awakr.ui.format.AlarmSchedule
import io.github.caendeith.awakr.ui.format.countdownText
import io.github.caendeith.awakr.ui.format.dateLabel
import io.github.caendeith.awakr.ui.format.formatTime
import io.github.caendeith.awakr.ui.format.rememberNow
import io.github.caendeith.awakr.ui.format.repeatSummary
import io.github.caendeith.awakr.ui.format.schedulesOf
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    alarms: List<Alarm>,
    missionLabel: (String) -> String,
    onToggle: (Alarm, Boolean) -> Unit,
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val now = rememberNow()
    val schedules = remember(alarms, now) { schedulesOf(alarms, now) }
    val next = schedules.firstOrNull { it.next != null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddAlarm,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_add_alarm)) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionLabel(stringResource(R.string.home_next_alarm)) }
            item {
                if (next == null) {
                    EmptyNextCard()
                } else {
                    HeroCard(
                        schedule = next,
                        now = now,
                        mission = missionLabel(next.alarm.missionPluginId),
                        onClick = { onEditAlarm(next.alarm.id) },
                    )
                }
            }
            if (schedules.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionLabel(stringResource(R.string.home_all_alarms))
                }
                items(schedules, key = { it.alarm.id }) { schedule ->
                    AlarmRow(
                        schedule = schedule,
                        now = now,
                        mission = missionLabel(schedule.alarm.missionPluginId),
                        onToggle = { onToggle(schedule.alarm, it) },
                        onClick = { onEditAlarm(schedule.alarm.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun CountdownPill(text: String) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun HeroCard(
    schedule: AlarmSchedule,
    now: ZonedDateTime,
    mission: String,
    onClick: () -> Unit,
) {
    val alarm = schedule.alarm
    AwakrCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimeText(
                text = formatTime(alarm.hour, alarm.minute),
                fontSize = 56.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            CountdownPill(countdownText(now, schedule.next))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = alarm.label.ifBlank { mission },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (alarm.label.isBlank()) {
                repeatSummary(alarm.daysMask)
            } else {
                "${repeatSummary(alarm.daysMask)} · $mission"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyNextCard() {
    AwakrCard {
        Text(
            text = stringResource(R.string.home_active_none_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.home_active_none_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AlarmRow(
    schedule: AlarmSchedule,
    now: ZonedDateTime,
    mission: String,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val alarm = schedule.alarm
    val enabled = alarm.enabled
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatTime(alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${alarm.label.ifBlank { mission }} · ${repeatSummary(alarm.daysMask)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val detail = schedule.next
                Text(
                    text = if (detail == null) {
                        stringResource(R.string.home_disabled)
                    } else {
                        "${dateLabel(now, detail)} · ${countdownText(now, detail)}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}
