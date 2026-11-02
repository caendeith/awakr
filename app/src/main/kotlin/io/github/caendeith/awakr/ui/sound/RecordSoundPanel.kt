package io.github.caendeith.awakr.ui.sound

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.audio.RecordedSound
import io.github.caendeith.awakr.core.audio.RecordingRules
import io.github.caendeith.awakr.core.perm.MicrophoneStatus

/** Where the recording card currently is. */
enum class RecordUiState { IDLE, RECORDING, PREVIEW }

/**
 * The "record your own sound" card: idle, recording with the 30 second countdown, and the preview
 * with use / again / delete actions. Stateless; [SoundPickerSheet] owns the recorder and the player.
 */
@Composable
fun RecordSoundPanel(
    status: MicrophoneStatus,
    state: RecordUiState,
    elapsedMs: Long,
    clip: RecordedSound?,
    error: String?,
    playing: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onUse: () -> Unit,
    onRecordAgain: () -> Unit,
    onDeleteClip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.sound_record_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.sound_record_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(12.dp))

            when (state) {
                RecordUiState.IDLE -> IdleActions(
                    status = status,
                    onRequestPermission = onRequestPermission,
                    onOpenSettings = onOpenSettings,
                    onStart = onStart,
                )

                RecordUiState.RECORDING -> RecordingActions(elapsedMs = elapsedMs, onStop = onStop)

                RecordUiState.PREVIEW -> PreviewActions(
                    clip = clip,
                    playing = playing,
                    onPreview = onPreview,
                    onStopPreview = onStopPreview,
                    onUse = onUse,
                    onRecordAgain = onRecordAgain,
                    onDeleteClip = onDeleteClip,
                )
            }

            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun IdleActions(
    status: MicrophoneStatus,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onStart: () -> Unit,
) {
    when (status) {
        MicrophoneStatus.UNSUPPORTED -> Text(
            text = stringResource(R.string.sound_record_unsupported),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        MicrophoneStatus.NEEDS_PERMISSION -> Column {
            Text(
                text = stringResource(R.string.sound_record_rationale),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onRequestPermission) {
                    Text(stringResource(R.string.sound_record_allow))
                }
                TextButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.sound_record_settings))
                }
            }
        }

        MicrophoneStatus.READY -> Button(onClick = onStart) {
            RecordDot(color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.sound_record_start))
        }
    }
}

@Composable
private fun RecordingActions(elapsedMs: Long, onStop: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RecordDot(color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(
                    R.string.sound_record_elapsed,
                    RecordingRules.durationLabel(elapsedMs),
                    RecordingRules.durationLabel(RecordingRules.remainingMs(elapsedMs).toLong()),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        LinearProgressIndicator(
            progress = { RecordingRules.progress(elapsedMs) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text(stringResource(R.string.sound_record_stop))
        }
    }
}

@Composable
private fun PreviewActions(
    clip: RecordedSound?,
    playing: Boolean,
    onPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onUse: () -> Unit,
    onRecordAgain: () -> Unit,
    onDeleteClip: () -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.sound_record_saved),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = clip?.timeLabel.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
            )
            if (clip != null) {
                IconButton(onClick = if (playing) onStopPreview else onPreview) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (playing) R.string.sound_picker_stop else R.string.sound_picker_preview,
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onUse) {
                Text(stringResource(R.string.sound_record_use))
            }
            TextButton(onClick = onRecordAgain) {
                Text(stringResource(R.string.sound_record_again))
            }
        }
        TextButton(onClick = onDeleteClip) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.sound_record_delete_action),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** The blinking red dot that marks an active recording. */
@Composable
private fun RecordDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "recording")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recordingDot",
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}
