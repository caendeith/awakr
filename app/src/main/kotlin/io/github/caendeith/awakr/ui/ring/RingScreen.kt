package io.github.caendeith.awakr.ui.ring

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.ui.component.AwakrCard
import io.github.caendeith.awakr.core.ui.component.PrimaryButton
import io.github.caendeith.awakr.core.ui.component.SecondaryButton
import io.github.caendeith.awakr.core.ui.component.TimeText
import io.github.caendeith.awakr.sdk.MissionPlugin
import io.github.caendeith.awakr.ui.background.RingBackgroundView
import io.github.caendeith.awakr.ui.background.rememberRingBackground
import io.github.caendeith.awakr.ui.format.formatTime
import io.github.caendeith.awakr.ui.format.parseMissionConfig
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/**
 * Full screen alarm UI. The mission owns the "dismiss" action: the alarm stops only
 * once the assigned mission reports success.
 *
 * While a mission is on screen the sound is muted and a countdown of [MissionTimer.DURATION_SECONDS]
 * seconds is shown ("like Alarmy"). When the countdown runs out the sound comes back, the mission is
 * replaced by a fresh task and the countdown starts again.
 *
 * @param alarm the ringing alarm, or null when it could not be loaded.
 * @param missions the mission chain that unlocks the dismiss action, played in order; an empty
 *   list means no plugin is available and the safety fallback takes over.
 * @param showSnooze whether the snooze button is offered for this occurrence.
 * @param onDismiss called when the mission was passed.
 * @param onSnooze called when the user snoozes.
 * @param onSilenceChanged mutes (true) or un-mutes (false) the ringing sound.
 */
@Composable
fun RingScreen(
    alarm: Alarm?,
    missions: List<RingMission>,
    showSnooze: Boolean,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onSilenceChanged: (Boolean) -> Unit,
) {
    // The background sits under the whole screen: the wake gate and the mission both stay readable
    // thanks to the scrim drawn by RingBackgroundView.
    val background = rememberRingBackground(alarm)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(Modifier.fillMaxSize()) {
            RingBackgroundView(
                id = background.id,
                dimPercent = background.dimPercent,
                modifier = Modifier.fillMaxSize(),
            )
            RingContent(alarm, missions, showSnooze, onDismiss, onSnooze, onSilenceChanged)
        }
    }
}

/** One mission of the ring screen, already resolved against the plugin registry. */
data class RingMission(
    val plugin: MissionPlugin,
    val config: JsonObject,
)

@Composable
private fun RingContent(
    alarm: Alarm?,
    missions: List<RingMission>,
    showSnooze: Boolean,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onSilenceChanged: (Boolean) -> Unit,
) {
    val time = alarm?.let { formatTime(it.hour, it.minute) } ?: "--:--"
    val label = alarm?.label.orEmpty()
    val silenceChange by rememberUpdatedState(onSilenceChanged)
    var stepIndex by remember(missions) { mutableIntStateOf(0) }
    val step = missions.getOrNull(stepIndex)
    val stepConfig = step?.config ?: parseMissionConfig("{}")
    val missionKind = step?.plugin?.kind
    // The "mission unavailable" fallback is the last resort to end a ring, so it keeps ringing.
    val missionOnScreen = missions.isNotEmpty()
    val resumed = rememberResumed()

    // The alarm rings until the user admits being awake ("I'm up"); the mission comes after that.
    // Silencing the alarm is the point of the gate, so the mission timer starts only once it is open.
    var awake by rememberSaveable { mutableStateOf(false) }
    var missionAttempt by remember(missionKind) { mutableIntStateOf(0) }
    var countdownRound by remember { mutableIntStateOf(0) }
    var remainingMillis by remember { mutableLongStateOf(MissionTimer.DURATION_MILLIS) }
    var timeIsUp by remember { mutableStateOf(false) }

    LaunchedEffect(countdownRound, missionOnScreen, resumed, awake) {
        if (!missionOnScreen) {
            // The fallback mission is the last resort to end a ring, so it must be audible.
            silenceChange(false)
            return@LaunchedEffect
        }
        if (!awake) {
            // The gate is what silences the alarm: until it is passed the sound stays on.
            silenceChange(false)
            return@LaunchedEffect
        }
        if (!resumed) {
            // Never mute a ring the user cannot see: while the ring screen is not in front (locked
            // device, another app) the alarm is audible again.
            silenceChange(false)
            return@LaunchedEffect
        }
        remainingMillis = MissionTimer.DURATION_MILLIS
        silenceChange(true)
        val deadline = MissionTimer.deadlineFrom(System.currentTimeMillis())
        while (true) {
            val left = MissionTimer.remainingMillis(deadline, System.currentTimeMillis())
            remainingMillis = left
            if (left <= 0L) break
            delay(COUNTDOWN_TICK_MILLIS)
        }
        // Out of time: the alarm rings again, the mission is replaced by a new task and the
        // countdown restarts once the user heard the penalty.
        silenceChange(false)
        timeIsUp = true
        delay(PENALTY_MILLIS)
        timeIsUp = false
        if (MissionTimer.isResettable(missionKind)) missionAttempt++
        countdownRound++
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TimeText(
            text = time,
            fontSize = MaterialTheme.typography.displayMedium.fontSize,
            color = MaterialTheme.colorScheme.primary,
        )
        if (label.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
        if (missionOnScreen && awake) {
            if (missions.size > 1) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.ring_mission_step,
                        (stepIndex + 1).coerceAtMost(missions.size),
                        missions.size,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            MissionCountdown(remainingMillis = remainingMillis, remainingIsGone = timeIsUp)
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (missionOnScreen && !awake) {
                WakeGate(onWakeUp = { awake = true })
            } else {
            // A new attempt really re-creates the mission state, so math and typing hand out a
            // different task instead of keeping the previous answer on screen.
                key(missionKind, missionAttempt, stepIndex) {
                    MissionHost(
                        mission = step?.plugin,
                        config = stepConfig,
                        onSolved = {
                            // Passing a step only advances the chain; the last one ends the ring.
                            if (stepIndex < missions.lastIndex) {
                                stepIndex++
                                countdownRound++
                            } else {
                                onDismiss()
                            }
                        },
                    )
                }
            }
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

/**
 * The gate between "the alarm rings" and "here is your mission".
 *
 * A ring that jumps straight into a task starts by silencing itself, which makes it easy to sleep
 * through. The ring screen therefore stays loud until the user admits to being awake, and only
 * then does the mission (with its own countdown) start.
 */
@Composable
private fun WakeGate(onWakeUp: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.ring_wake_gate_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.ring_wake_gate_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(
            text = stringResource(R.string.ring_wake_gate_button),
            onClick = onWakeUp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
/**
 * Countdown of the mission time budget.
 *
 * Digits use [TimeText] so they keep their width while the time runs down.
 */
@Composable
private fun MissionCountdown(remainingMillis: Long, remainingIsGone: Boolean) {
    AwakrCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ring_timer_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        if (remainingIsGone) R.string.ring_timer_expired else R.string.ring_timer_hint,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (remainingIsGone) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (!remainingIsGone) {
                TimeText(
                    text = MissionTimer.format(remainingMillis),
                    fontSize = 32.sp,
                    color = if (remainingMillis <= WARNING_MILLIS) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

/** True while the hosting activity is resumed; the countdown only runs in the foreground. */
@Composable
private fun rememberResumed(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return resumed
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

/** How often the countdown is refreshed; the label itself changes once a second. */
private const val COUNTDOWN_TICK_MILLIS = 200L

/** How long the alarm rings before a fresh mission and a fresh countdown start. */
private const val PENALTY_MILLIS = 4_000L

/** Below this the countdown turns red. */
private const val WARNING_MILLIS = 10_000L
