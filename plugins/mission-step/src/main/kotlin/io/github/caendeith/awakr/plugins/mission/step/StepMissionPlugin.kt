package io.github.caendeith.awakr.plugins.mission.step

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.caendeith.awakr.sdk.Capability
import io.github.caendeith.awakr.sdk.MissionPlugin
import io.github.caendeith.awakr.sdk.PluginManifest
import io.github.caendeith.awakr.sdk.StringRes
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

/**
 * Take a configurable number of steps, counted from accelerometer peaks.
 *
 * Config: { "goal": 10..100 }
 *
 * Raw [Sensor.TYPE_ACCELEROMETER] only: no [Sensor.TYPE_STEP_COUNTER], no ACTIVITY_RECOGNITION, no
 * permission at all. If the device has no working accelerometer the mission falls back to counting
 * taps, so the alarm can always be dismissed.
 */
class StepMissionPlugin : MissionPlugin {

    override val kind: String = KIND

    override val manifest: PluginManifest = PluginManifest(
        id = "mission-step",
        name = StringRes(R.string.plugin_step_name),
        version = "1.0.0",
        capabilities = setOf(Capability.MISSION),
    )

    @Composable
    override fun Editor(config: JsonObject, onChange: (JsonObject) -> Unit) {
        val goal = config.number("goal", DEFAULT_GOAL).coerceIn(MIN_GOAL, MAX_GOAL)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.step_goal, goal),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = goal.toFloat(),
                onValueChange = { onChange(config.with("goal", it.roundToInt())) },
                valueRange = MIN_GOAL.toFloat()..MAX_GOAL.toFloat(),
                steps = (MAX_GOAL - MIN_GOAL) / GOAL_STEP - 1,
            )
        }
    }

    @Composable
    override fun Mission(config: JsonObject, onSolved: () -> Unit) {
        val goal = config.number("goal", DEFAULT_GOAL).coerceIn(MIN_GOAL, MAX_GOAL)
        val context = LocalContext.current
        var done by remember { mutableIntStateOf(0) }
        var sawSample by remember { mutableStateOf(false) }
        var manual by remember { mutableStateOf(false) }

        DisposableEffect(context, goal) {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val counter = StepCounter()
            val listener = object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

                override fun onSensorChanged(event: SensorEvent) {
                    val values = event.values
                    sawSample = true
                    done = counter
                        .onSample(values[0], values[1], values[2], SystemClock.elapsedRealtime())
                        .coerceAtMost(goal)
                }
            }
            if (manager != null && sensor != null) {
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            } else {
                // No accelerometer at all: offer the tap fallback right away.
                manual = true
            }
            onDispose {
                manager?.unregisterListener(listener)
            }
        }

        // A sensor that never reports is as good as a missing one; do not let the mission hang.
        LaunchedEffect(Unit) {
            delay(SENSOR_GRACE_MILLIS)
            if (!sawSample) manual = true
        }

        LaunchedEffect(done) {
            if (done >= goal) onSolved()
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.step_hint),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { done.toFloat() / goal },
                    modifier = Modifier.size(160.dp),
                    strokeWidth = 10.dp,
                )
                Text(
                    text = stringResource(R.string.step_progress, done, goal),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            if (manual) {
                Text(
                    text = stringResource(R.string.step_no_sensor),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { done = (done + 1).coerceAtMost(goal) }) {
                    Text(stringResource(R.string.step_tap))
                }
            }
        }
    }

    override fun validate(config: JsonObject): JsonObject = JsonObject(
        mapOf("goal" to JsonPrimitive(config.number("goal", DEFAULT_GOAL).coerceIn(MIN_GOAL, MAX_GOAL))),
    )

    private companion object {
        const val KIND = "step"
        const val DEFAULT_GOAL = 30
        const val MIN_GOAL = 10
        const val MAX_GOAL = 100
        const val GOAL_STEP = 10
        const val SENSOR_GRACE_MILLIS = 8_000L
    }
}

internal fun JsonObject.number(key: String, fallback: Int): Int =
    (this[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: fallback

internal fun JsonObject.with(key: String, value: Int): JsonObject =
    JsonObject(toMutableMap().apply { put(key, JsonPrimitive(value)) })
