package io.github.caendeith.awakr.ui.ring

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.ui.component.SecondaryButton
import io.github.caendeith.awakr.core.ui.component.TimeText
import io.github.caendeith.awakr.sdk.MissionPlugin
import io.github.caendeith.awakr.ui.format.formatTime
import io.github.caendeith.awakr.ui.format.parseMissionConfig
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/**
 * Full screen alarm UI. The mission owns the "dismiss" action: the alarm stops only
 * once the assigned mission reports success.
 */
@Composable
fun RingScreen(
    alarm: Alarm?,
    mission: MissionPlugin?,
    showSnooze: Boolean,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        RingContent(alarm, mission, showSnooze, onDismiss, onSnooze)
    }
}

@Composable
private fun RingContent(
    alarm: Alarm?,
    mission: MissionPlugin?,
    showSnooze: Boolean,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val time = alarm?.let { formatTime(it.hour, it.minute) } ?: "--:--"
        TimeText(
            text = time,
            fontSize = MaterialTheme.typography.displayMedium.fontSize,
            color = MaterialTheme.colorScheme.primary,
        )
        val label = alarm?.label.orEmpty()
        if (label.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
        val missionConfig = remember(alarm?.missionConfig) {
            parseMissionConfig(alarm?.missionConfig ?: "{}")
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            MissionHost(mission = mission, config = missionConfig, onSolved = onDismiss)
        }
        if (showSnooze) {
            SecondaryButton(
                text = stringResource(R.string.ring_snooze),
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MissionHost(mission: MissionPlugin?, config: JsonObject, onSolved: () -> Unit) {
    if (mission == null) {
        FallbackMission(onSolved = onSolved)
        return
    }
    mission.Mission(config, onSolved)
}

/** Host side safety net: if the configured mission is missing or disabled, holding the
 * circle for five seconds dismisses the alarm so the user is never trapped. */
@Composable
private fun FallbackMission(onSolved: () -> Unit) {
    var holding by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(holding) {
        if (!holding) {
            elapsed = 0L
            return@LaunchedEffect
        }
        while (holding && elapsed < HOLD_MILLIS) {
            delay(50L)
            elapsed += 50L
        }
        if (elapsed >= HOLD_MILLIS) onSolved()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.ring_fallback_hint),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            holding = true
                            tryAwaitRelease()
                            holding = false
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = { (elapsed.toFloat() / HOLD_MILLIS).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 8.dp,
            )
            Text(
                text = stringResource(R.string.ring_hold),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private const val HOLD_MILLIS = 5_000L
