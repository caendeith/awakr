package io.github.caendeith.awakr.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.alarm.Days
import io.github.caendeith.awakr.core.ui.component.AwakrCard
import io.github.caendeith.awakr.core.ui.component.PrimaryButton
import io.github.caendeith.awakr.core.ui.component.SecondaryButton
import io.github.caendeith.awakr.core.ui.component.TimeText
import io.github.caendeith.awakr.data.repository.AlarmRepository
import io.github.caendeith.awakr.plugin.runtime.PluginRegistry
import io.github.caendeith.awakr.ui.format.formatTime
import java.time.DayOfWeek
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    alarmId: Long,
    alarms: AlarmRepository,
    plugins: PluginRegistry,
    missionLabel: (String) -> String,
    onBack: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var draft by remember { mutableStateOf<Alarm?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(alarmId) {
        draft = if (alarmId == 0L) {
            Alarm(hour = 7, minute = 0, missionPluginId = "math")
        } else {
            alarms.get(alarmId)
        }
    }

    val current = draft ?: return

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (alarmId == 0L) stringResource(R.string.editor_new_alarm)
                        else stringResource(R.string.editor_edit_alarm)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AwakrCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTimePicker = true },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    TimeText(
                        text = formatTime(current.hour, current.minute),
                        fontSize = MaterialTheme.typography.displayMedium.fontSize,
                    )
                    Text(
                        text = stringResource(R.string.editor_tap_to_change_time),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionTitle(stringResource(R.string.editor_repeat))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DayToggles(daysMask = current.daysMask) { day, checked ->
                    val set = Days.toDaySet(current.daysMask).toMutableSet()
                    if (checked) set.add(day) else set.remove(day)
                    draft = current.copy(daysMask = Days.toMask(set))
                }
            }

            OutlinedTextField(
                value = current.label,
                onValueChange = { draft = current.copy(label = it) },
                label = { Text(stringResource(R.string.editor_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle(stringResource(R.string.editor_mission))
            val missions = plugins.missions
            if (missions.isEmpty()) {
                Text(
                    text = stringResource(R.string.editor_no_plugins),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    missions.forEach { mission ->
                        FilterChip(
                            selected = current.missionPluginId == mission.kind,
                            onClick = { draft = current.copy(missionPluginId = mission.kind) },
                            label = { Text(missionLabel(mission.kind)) },
                        )
                    }
                }
            }

            AwakrCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.editor_snooze),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = current.snoozeEnabled,
                        onCheckedChange = { draft = current.copy(snoozeEnabled = it) },
                    )
                }
                if (current.snoozeEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.editor_snooze_length, current.snoozeMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = current.snoozeMinutes.toFloat(),
                        onValueChange = { draft = current.copy(snoozeMinutes = it.toInt()) },
                        valueRange = 5f..30f,
                        steps = 4,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = { onSave(current) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (alarmId != 0L) {
                SecondaryButton(
                    text = stringResource(R.string.action_delete),
                    onClick = { onDelete(alarmId) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    draft = current.copy(hour = state.hour, minute = state.minute)
                    showTimePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RowScope.DayToggles(daysMask: Int, onToggle: (DayOfWeek, Boolean) -> Unit) {
    val locale = LocalContext.current.resources.configuration.locales[0]
    DayOfWeek.entries.forEach { day ->
        val selected = Days.bit(daysMask, day)
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainer,
                )
                .clickable { onToggle(day, !selected) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.getDisplayName(TextStyle.SHORT, locale).take(2)
                    .replaceFirstChar { it.titlecase(locale) },
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
