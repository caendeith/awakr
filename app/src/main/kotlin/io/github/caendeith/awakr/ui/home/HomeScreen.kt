package io.github.caendeith.awakr.ui.home

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.ui.component.AwakrCard
import io.github.caendeith.awakr.core.ui.component.TimeText
import io.github.caendeith.awakr.ui.format.formatTime
import io.github.caendeith.awakr.ui.format.repeatSummary

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
    val enabled = alarms.filter { it.enabled }
    val next = enabled.minByOrNull { it.hour * 60 + it.minute } ?: enabled.firstOrNull()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.home_next_alarm),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                NextAlarmCard(
                    time = next?.let { formatTime(it.hour, it.minute) } ?: "--:--",
                    caption = next?.let {
                        val repeat = repeatSummary(it.daysMask)
                        val mission = missionLabel(it.missionPluginId)
                        "$repeat · $mission"
                    } ?: stringResource(R.string.home_no_alarms),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.home_alarms),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (alarms.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.home_no_alarms),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(alarms, key = { it.id }) { alarm ->
                AlarmRow(
                    alarm = alarm,
                    mission = missionLabel(alarm.missionPluginId),
                    onToggle = { onToggle(alarm, it) },
                    onClick = { onEditAlarm(alarm.id) },
                )
            }
        }
    }
}

@Composable
private fun NextAlarmCard(time: String, caption: String) {
    AwakrCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            TimeText(
                text = time,
                fontSize = MaterialTheme.typography.displayLarge.fontSize,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    mission: String,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    AwakrCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatTime(alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
                    ),
                    color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = listOf(repeatSummary(alarm.daysMask), mission)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            }
        }
    }
}
