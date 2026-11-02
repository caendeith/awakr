package io.github.caendeith.awakr.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.audio.AlarmSoundPlayer
import io.github.caendeith.awakr.core.audio.BuiltinSounds
import io.github.caendeith.awakr.core.audio.SoundResolver
import io.github.caendeith.awakr.core.time.NextOccurrence
import io.github.caendeith.awakr.core.ui.component.AwakrCard
import io.github.caendeith.awakr.core.ui.component.PrimaryButton
import io.github.caendeith.awakr.core.ui.component.SecondaryButton
import io.github.caendeith.awakr.core.ui.component.TimeText
import io.github.caendeith.awakr.data.repository.AlarmRepository
import io.github.caendeith.awakr.mission.MissionChain
import io.github.caendeith.awakr.mission.MissionStep
import io.github.caendeith.awakr.plugin.runtime.PluginRegistry
import io.github.caendeith.awakr.ui.format.countdownText
import io.github.caendeith.awakr.ui.format.dateLabel
import io.github.caendeith.awakr.ui.format.formatTime
import io.github.caendeith.awakr.ui.format.parseMissionConfig
import io.github.caendeith.awakr.ui.format.rememberNow
import kotlinx.coroutines.Dispatchers
import io.github.caendeith.awakr.ui.sound.SoundPickerSheet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/**
 * Creates or edits one alarm.
 *
 * Everything that can be changed lives on this screen: time (big picker plus quick nudges),
 * repeat days, label, mission, sound, snooze behaviour and the "skip the next one" marker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    alarmId: Long,
    alarms: AlarmRepository,
    plugins: PluginRegistry,
    enabledPluginIds: Set<String>,
    missionLabel: (String) -> String,
    defaultSoundId: String = "",
    defaultSnoozeMinutes: Int = 10,
    defaultMissionPluginId: String = "math",
    onBack: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: (Long) -> Unit,
    onDuplicate: (Alarm) -> Unit = {},
) {
    var draft by remember { mutableStateOf<Alarm?>(null) }
    var skipNext by remember { mutableStateOf(false) }
    var showTimeSheet by remember { mutableStateOf(false) }
    var showSoundSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(alarmId) {
        val loaded = if (alarmId == 0L) null else alarms.get(alarmId)
        draft = loaded ?: Alarm(
            hour = 7,
            minute = 0,
            snoozeMinutes = defaultSnoozeMinutes,
            missionPluginId = defaultMissionPluginId,
            soundId = "",
        )
        skipNext = loaded?.skipNextAt != null
    }

    val current = draft
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (alarmId == 0L) R.string.editor_new_alarm else R.string.editor_edit_alarm,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (alarmId != 0L && current != null) {
                        TextButton(onClick = { onDuplicate(current) }) {
                            Text(stringResource(R.string.editor_duplicate))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        val now = rememberNow(intervalMs = 60_000L)
        val nextRing = NextOccurrence.next(now, current.hour, current.minute, current.daysMask)
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val player = remember { AlarmSoundPlayer(context) }
        DisposableEffect(Unit) { onDispose { player.stop() } }

        val importSound = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    val saved = copySoundIntoApp(context, uri)
                    if (saved != null) draft = draft?.copy(soundId = saved)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            AwakrCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTimeSheet = true },
            ) {
                TimeText(text = formatTime(current.hour, current.minute), fontSize = 72.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.editor_tap_to_change_time),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = listOfNotNull(
                        nextRing?.let { dateLabel(now, it) },
                        countdownText(now, nextRing),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle(stringResource(R.string.editor_repeat))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayToggles(
                    daysMask = current.daysMask,
                    locale = locale,
                    onToggle = { bit ->
                        draft = current.copy(daysMask = current.daysMask xor bit)
                    },
                )
            }

            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = current.label,
                onValueChange = { draft = current.copy(label = it) },
                label = { Text(stringResource(R.string.editor_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            SectionTitle(stringResource(R.string.editor_mission))
            val missions = plugins.missions.filter { enabledPluginIds.contains(it.manifest.id) }
            if (missions.isEmpty()) {
                Text(
                    text = stringResource(R.string.editor_no_plugins),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val steps = MissionChain.parse(current.missionChain)
                    .ifEmpty { MissionChain.stepsOf(current) }

                // The first step stays mirrored into missionPluginId/missionConfig so that old
                // readers (backups, widget, logs) keep seeing the mission the alarm starts with.
                fun applyChain(next: List<MissionStep>) {
                    val first = next.firstOrNull()
                    draft = current.copy(
                        missionPluginId = first?.kind ?: current.missionPluginId,
                        missionConfig = first?.config ?: "{}",
                        missionChain = MissionChain.encode(next),
                    )
                }

                steps.forEachIndexed { index, step ->
                    Spacer(Modifier.height(8.dp))
                    AwakrCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = missionLabel(step.kind),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (steps.size > 1) {
                                    Text(
                                        text = stringResource(
                                            R.string.editor_mission_step,
                                            index + 1,
                                            steps.size,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (index > 0) {
                                IconButton(onClick = { applyChain(MissionChain.moved(steps, index, -1)) }) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = stringResource(R.string.editor_mission_up),
                                    )
                                }
                            }
                            if (index < steps.lastIndex) {
                                IconButton(onClick = { applyChain(MissionChain.moved(steps, index, 1)) }) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.editor_mission_down),
                                    )
                                }
                            }
                            if (steps.size > 1) {
                                IconButton(onClick = { applyChain(MissionChain.removed(steps, index)) }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.editor_mission_remove),
                                    )
                                }
                            }
                        }
                        val plugin = missions.firstOrNull { it.kind == step.kind }
                        if (plugin != null) {
                            Spacer(Modifier.height(12.dp))
                            plugin.Editor(
                                parseMissionConfig(step.config),
                            ) { next ->
                                applyChain(MissionChain.withConfig(steps, index, next.toString()))
                            }
                        }
                    }
                }

                if (steps.size < MissionChain.MAX_STEPS) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        missions
                            .filter { plugin -> steps.none { it.kind == plugin.kind } }
                            .forEach { plugin ->
                                FilterChip(
                                    selected = false,
                                    onClick = { applyChain(MissionChain.added(steps, plugin.kind)) },
                                    label = {
                                        Text(
                                            stringResource(
                                                R.string.editor_mission_add,
                                                missionLabel(plugin.kind),
                                            ),
                                        )
                                    },
                                )
                            }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle(stringResource(R.string.editor_sound))
            AwakrCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = SoundResolver.name(context, current.soundId)
                                ?: SoundResolver.name(context, defaultSoundId)
                                ?: stringResource(R.string.editor_sound_default),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    TextButton(onClick = {
                        player.play(
                            uri = SoundResolver.uri(context, current.soundId, defaultSoundId),
                            rampSeconds = 0,
                            startVolumePercent = 100,
                            scope = scope,
                        )
                    }) {
                        Text(stringResource(R.string.editor_sound_preview))
                    }
                    TextButton(onClick = { showSoundSheet = true }) {
                        Text(stringResource(R.string.editor_sound_choose))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            AwakrCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.editor_snooze),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.editor_snooze_length, current.snoozeMinutes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = current.snoozeEnabled,
                        onCheckedChange = { draft = current.copy(snoozeEnabled = it) },
                    )
                }
                Slider(
                    value = current.snoozeMinutes.toFloat(),
                    onValueChange = { draft = current.copy(snoozeMinutes = it.toInt()) },
                    valueRange = 5f..30f,
                    steps = 4,
                    enabled = current.snoozeEnabled,
                )
            }

            Spacer(Modifier.height(20.dp))
            AwakrCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.editor_skip_next),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.editor_skip_next_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = skipNext, onCheckedChange = { skipNext = it })
                }
            }

            Spacer(Modifier.height(28.dp))
            PrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = {
                    val skipAt = if (skipNext) {
                        NextOccurrence.next(
                            ZonedDateTime.now(),
                            current.hour,
                            current.minute,
                            current.daysMask,
                        )?.toInstant()?.toEpochMilli()
                    } else {
                        null
                    }
                    onSave(current.copy(skipNextAt = skipAt))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (alarmId != 0L) {
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = stringResource(R.string.action_delete),
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(32.dp))
        }

        if (showTimeSheet) {
            val pickerState = rememberTimePickerState(
                initialHour = current.hour,
                initialMinute = current.minute,
                is24Hour = true,
            )
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showTimeSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TimePicker(state = pickerState)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(5, 15, 30).forEach { delta ->
                            TextButton(
                                onClick = {
                                    val total = (pickerState.hour * 60 + pickerState.minute + delta) % (24 * 60)
                                    pickerState.hour = total / 60
                                    pickerState.minute = total % 60
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.editor_quick_plus, delta))
                            }
                        }
                    }
                    Button(
                        onClick = {
                            draft = current.copy(hour = pickerState.hour, minute = pickerState.minute)
                            showTimeSheet = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Text(stringResource(R.string.action_done))
                    }
                }
            }
        }

        if (showSoundSheet) {
            SoundPickerSheet(
                currentSoundId = current?.soundId ?: "",
                defaultSoundId = defaultSoundId,
                onPick = { draft = draft?.copy(soundId = it) },
                onDismiss = { showSoundSheet = false },
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(R.string.action_delete)) },
                text = { Text(stringResource(R.string.editor_delete_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        onDelete(current.id)
                    }) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SoundRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = title, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun RowScope.DayToggles(daysMask: Int, locale: Locale, onToggle: (Int) -> Unit) {
    DayOfWeek.entries.forEach { day ->
        val bit = 1 shl (day.value - 1)
        val selected = daysMask and bit != 0
        val label = day.getDisplayName(TextStyle.SHORT, locale)
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                .clickable { onToggle(bit) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.take(2),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Copies a picked document into the app's private storage and returns `file:<path>`. */
private suspend fun copySoundIntoApp(context: android.content.Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val extension = context.contentResolver.getType(uri)
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() && it.length <= 5 }
                ?: "bin"
            val dir = File(context.filesDir, "sounds").apply { mkdirs() }
            val file = File(dir, UUID.randomUUID().toString() + "." + extension)
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            if (file.isFile && file.length() > 0L) "file:" + file.absolutePath else null
        }.getOrNull()
    }
