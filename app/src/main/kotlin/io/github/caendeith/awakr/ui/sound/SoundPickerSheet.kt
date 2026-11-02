package io.github.caendeith.awakr.ui.sound

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.audio.AlarmRecorder
import io.github.caendeith.awakr.core.audio.AlarmSoundPlayer
import io.github.caendeith.awakr.core.audio.BuiltinSounds
import io.github.caendeith.awakr.core.audio.RecordedSound
import io.github.caendeith.awakr.core.audio.RecordingResult
import io.github.caendeith.awakr.core.audio.RecordingRules
import io.github.caendeith.awakr.core.audio.SoundResolver
import io.github.caendeith.awakr.core.audio.SoundStorage
import io.github.caendeith.awakr.core.perm.MicrophoneStatus
import io.github.caendeith.awakr.core.perm.RecordingPermission
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bottom sheet that picks the alarm sound.
 *
 * Built-in tones are grouped into packs, the user's own files and recordings form the "Recorded"
 * pack, and the card at the bottom records a new clip. The picker only ever reports a single
 * sound id through [onPick]; the alarm keeps storing one id, so nothing changes in the data model.
 *
 * @param currentSoundId id stored on the alarm being edited; blank means "use the default sound"
 * @param defaultSoundId the global default, used for previewing inherited sounds
 * @param onPick called with the chosen id; blank means the default sound
 * @param onDismiss called when the sheet is closed without a choice
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundPickerSheet(
    currentSoundId: String?,
    defaultSoundId: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val player = remember { AlarmSoundPlayer(context) }
    val recorder = remember { AlarmRecorder(context) }

    var filter by remember { mutableStateOf<SoundFilter>(SoundFilter.All) }
    var recorded by remember { mutableStateOf(SoundStorage.recorded(context)) }
    var recordState by remember { mutableStateOf(RecordUiState.IDLE) }
    var clip by remember { mutableStateOf<RecordedSound?>(null) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var previewingId by remember { mutableStateOf<String?>(null) }
    var granted by remember { mutableStateOf(RecordingPermission.isGranted(context)) }
    var pendingDelete by remember { mutableStateOf<RecordedSound?>(null) }
    val hasMicrophone = remember { RecordingPermission.hasMicrophone(context) }

    // Resolved here, at composition time: the callbacks below are plain lambdas, and lint forbids
    // pulling resources from the LocalContext inside them (LocalContextGetResourceValueCall).
    val recordTooShortError = stringResource(R.string.sound_record_too_short)
    val recordFailedError = stringResource(R.string.sound_record_failed)
    val recordDeniedError = stringResource(R.string.sound_record_denied)
    val importFailedError = stringResource(R.string.sound_picker_import_failed)
    val deleteFailedError = stringResource(R.string.sound_picker_delete_failed)

    val status = when {
        !hasMicrophone -> MicrophoneStatus.UNSUPPORTED
        granted -> MicrophoneStatus.READY
        else -> MicrophoneStatus.NEEDS_PERMISSION
    }

    // Leave the sheet with a choice; the caller applies it to the alarm.
    val choose: (String) -> Unit = { id ->
        player.stop()
        previewingId = null
        scope.launch {
            runCatching { sheetState.hide() }
            onPick(id)
            onDismiss()
        }
    }

    val finishRecording: () -> Unit = {
        player.stop()
        previewingId = null
        when (val result = recorder.stop()) {
            is RecordingResult.Saved -> {
                clip = result.sound
                recorded = SoundStorage.recorded(context)
                error = null
                recordState = RecordUiState.PREVIEW
            }

            RecordingResult.TooShort -> {
                clip = null
                error = recordTooShortError
                recordState = RecordUiState.IDLE
            }

            RecordingResult.Failed -> {
                clip = null
                error = recordFailedError
                recordState = RecordUiState.IDLE
            }
        }
        elapsedMs = 0L
    }

    val startRecording: () -> Unit = {
        player.stop()
        previewingId = null
        error = null
        if (recorder.start() == null) {
            error = recordFailedError
            recordState = RecordUiState.IDLE
        } else {
            elapsedMs = 0L
            recordState = RecordUiState.RECORDING
        }
    }

    val previewSound: (String) -> Unit = { id ->
        player.stop()
        player.play(
            uri = SoundResolver.uri(context, id, defaultSoundId),
            rampSeconds = 0,
            startVolumePercent = 100,
            scope = scope,
        )
        previewingId = id
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { accepted ->
        granted = accepted
        if (accepted) {
            startRecording()
        } else {
            error = recordDeniedError
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val id = SoundStorage.import(context, uri)
                if (id == null) {
                    error = importFailedError
                } else {
                    recorded = SoundStorage.recorded(context)
                    choose(id)
                }
            }
        }
    }

    val deleteClip: (RecordedSound) -> Unit = { sound ->
        player.stop()
        previewingId = null
        if (SoundStorage.delete(context, sound.soundId)) {
            recorded = SoundStorage.recorded(context)
            // Never leave the edited alarm pointing at a file that no longer exists.
            if (SoundPickerModel.isSelected(currentSoundId, sound.soundId)) onPick("")
            if (SoundPickerModel.isSelected(clip?.soundId, sound.soundId)) {
                clip = null
                recordState = RecordUiState.IDLE
            }
        } else {
            error = deleteFailedError
        }
        pendingDelete = null
    }

    // Ticks the elapsed label and enforces the 30 second ceiling.
    LaunchedEffect(recordState) {
        if (recordState != RecordUiState.RECORDING) return@LaunchedEffect
        while (true) {
            val now = recorder.elapsedMs()
            elapsedMs = now
            if (now >= RecordingRules.MAX_DURATION_MS) {
                finishRecording()
                break
            }
            delay(100)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.stop()
            recorder.cancel()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.editor_sound_choose),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SoundPickerModel.filters.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(packLabel(option)) },
                    )
                }
            }

            val sections = remember(recorded, filter) {
                SoundPickerModel.sections(recorded, filter)
            }

            if (filter == SoundFilter.All) {
                Spacer(Modifier.height(4.dp))
                SoundRow(
                    title = stringResource(R.string.editor_sound_default),
                    selected = SoundPickerModel.isDefaultSelected(currentSoundId),
                    onClick = { choose("") },
                )
            }

            sections.forEach { section ->
                when (section) {
                    is SoundSection.Builtins -> {
                        SectionHeader(packLabel(SoundFilter.Pack(section.pack)))
                        section.sounds.forEach { sound ->
                            val id = BuiltinSounds.idOf(sound)
                            SoundRow(
                                title = stringResource(sound.nameRes),
                                selected = SoundPickerModel.isSelected(currentSoundId, id),
                                onClick = { choose(id) },
                                onPreview = { previewSound(id) },
                            )
                        }
                    }

                    is SoundSection.Recorded -> {
                        SectionHeader(stringResource(R.string.sound_picker_pack_recorded))
                        if (section.sounds.isEmpty()) {
                            Text(
                                text = stringResource(R.string.sound_picker_recorded_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        section.sounds.forEach { sound ->
                            RecordedRow(
                                sound = sound,
                                selected = SoundPickerModel.isSelected(currentSoundId, sound.soundId),
                                onClick = { choose(sound.soundId) },
                                onPreview = { previewSound(sound.soundId) },
                                onDelete = { pendingDelete = sound },
                            )
                        }
                    }
                }
            }

            SoundRow(
                title = stringResource(R.string.sound_picker_import),
                selected = false,
                onClick = { importLauncher.launch(arrayOf("audio/*")) },
                leading = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            RecordSoundPanel(
                status = status,
                state = recordState,
                elapsedMs = elapsedMs,
                clip = clip,
                error = error,
                playing = clip != null && previewingId == clip?.soundId,
                onRequestPermission = { permissionLauncher.launch(RecordingPermission.PERMISSION) },
                onOpenSettings = {
                    runCatching { context.startActivity(RecordingPermission.settingsIntent(context)) }
                },
                onStart = startRecording,
                onStop = finishRecording,
                onPreview = { clip?.let { previewSound(it.soundId) } },
                onStopPreview = {
                    player.stop()
                    previewingId = null
                },
                onUse = { clip?.let { choose(it.soundId) } },
                onRecordAgain = startRecording,
                onDeleteClip = { clip?.let { pendingDelete = it } },
            )
        }
    }

    pendingDelete?.let { sound ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.sound_record_delete_title)) },
            text = { Text(stringResource(R.string.sound_record_delete_message)) },
            confirmButton = {
                TextButton(onClick = { deleteClip(sound) }) {
                    Text(stringResource(R.string.sound_record_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun packLabel(filter: SoundFilter): String = when (filter) {
    SoundFilter.All -> stringResource(R.string.sound_picker_pack_all)
    is SoundFilter.Pack -> when (filter.pack) {
        io.github.caendeith.awakr.core.audio.SoundPack.GENTLE ->
            stringResource(R.string.sound_picker_pack_gentle)

        io.github.caendeith.awakr.core.audio.SoundPack.LOUD ->
            stringResource(R.string.sound_picker_pack_loud)
    }

    SoundFilter.Recorded -> stringResource(R.string.sound_picker_pack_recorded)
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SoundRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    onPreview: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        } else {
            RadioButton(selected = selected, onClick = onClick)
        }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (leading != null) 0.dp else 8.dp),
        )
        if (onPreview != null) {
            IconButton(onClick = onPreview) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.sound_picker_preview),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecordedRow(
    sound: RecordedSound,
    selected: Boolean,
    onClick: () -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            Text(
                text = sound.timeLabel ?: sound.displayName,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (sound.timeLabel != null) {
                Text(
                    text = sound.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onPreview) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.sound_picker_preview),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.sound_record_delete_action),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
