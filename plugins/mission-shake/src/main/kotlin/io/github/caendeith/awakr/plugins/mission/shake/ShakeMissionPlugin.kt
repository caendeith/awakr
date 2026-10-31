package io.github.caendeith.awakr.plugins.mission.shake

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.sqrt

/**
 * Shake the phone for a configurable number of seconds.
 *
 * Config: { "seconds": 10..100 }
 */
class ShakeMissionPlugin : MissionPlugin {

    override val kind: String = KIND

    override val manifest: PluginManifest = PluginManifest(
        id = "mission-shake",
        name = StringRes(R.string.plugin_shake_name),
        version = "1.0.0",
        capabilities = setOf(Capability.MISSION),
    )

    @Composable
    override fun Editor(config: JsonObject, onChange: (JsonObject) -> Unit) {
        val seconds = config.number("seconds", DEFAULT_SECONDS).coerceIn(MIN_SECONDS, MAX_SECONDS)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.shake_seconds, seconds),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = seconds.toFloat(),
                onValueChange = { onChange(config.with("seconds", it.toInt())) },
                valueRange = MIN_SECONDS.toFloat()..MAX_SECONDS.toFloat(),
                steps = (MAX_SECONDS - MIN_SECONDS) / STEP - 1,
            )
        }
    }

    @Composable
    override fun Mission(config: JsonObject, onSolved: () -> Unit) {
        val seconds = config.number("seconds", DEFAULT_SECONDS).coerceIn(MIN_SECONDS, MAX_SECONDS)
        val targetMillis = seconds * 1000L
        val context = LocalContext.current
        var shaken by remember { mutableLongStateOf(0L) }

        DisposableEffect(context, seconds) {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val tracker = ShakeTracker()
            val listener = object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

                override fun onSensorChanged(event: SensorEvent) {
                    val values = event.values
                    val total = tracker.onSample(
                        x = values[0],
                        y = values[1],
                        z = values[2],
                        nowMillis = SystemClock.elapsedRealtime(),
                    )
                    shaken = total.coerceAtMost(targetMillis)
                }
            }
            if (manager != null && sensor != null) {
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
            onDispose {
                manager?.unregisterListener(listener)
            }
        }

        LaunchedEffect(shaken) {
            if (shaken >= targetMillis) onSolved()
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.shake_hint),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { shaken.toFloat() / targetMillis },
                    modifier = Modifier.size(160.dp),
                    strokeWidth = 10.dp,
                )
                Text(
                    text = stringResource(R.string.shake_progress, (shaken * 100L / targetMillis).toInt()),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }
    }

    override fun validate(config: JsonObject): JsonObject = JsonObject(
        mapOf("seconds" to JsonPrimitive(config.number("seconds", DEFAULT_SECONDS).coerceIn(MIN_SECONDS, MAX_SECONDS))),
    )

    private companion object {
        const val KIND = "shake"
        const val DEFAULT_SECONDS = 20
        const val MIN_SECONDS = 10
        const val MAX_SECONDS = 100
        const val STEP = 5
    }
}

internal fun JsonObject.number(key: String, fallback: Int): Int =
    (this[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: fallback

internal fun JsonObject.with(key: String, value: Int): JsonObject =
    JsonObject(toMutableMap().apply { put(key, JsonPrimitive(value)) })
