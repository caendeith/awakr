package io.github.caendeith.awakr.plugins.mission.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Watch a short sequence of flashing tiles, then repeat it in the same order.
 *
 * Config: { "length": 3..7 } — how many tiles flash per round.
 *
 * A wrong tap restarts the same order instead of failing the mission, so the alarm can always be
 * dismissed even at 6 a.m.
 */
class MemoryMissionPlugin : MissionPlugin {

    override val kind: String = KIND

    override val manifest: PluginManifest = PluginManifest(
        id = "mission-memory",
        name = StringRes(R.string.plugin_memory_name),
        version = "1.0.0",
        capabilities = setOf(Capability.MISSION),
    )

    @Composable
    override fun Editor(config: JsonObject, onChange: (JsonObject) -> Unit) {
        val length = config.number("length", DEFAULT_LENGTH).coerceIn(MIN_LENGTH, MAX_LENGTH)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.memory_length, length),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = length.toFloat(),
                onValueChange = { onChange(config.with("length", it.roundToInt())) },
                valueRange = MIN_LENGTH.toFloat()..MAX_LENGTH.toFloat(),
                steps = MAX_LENGTH - MIN_LENGTH - 1,
            )
        }
    }

    @Composable
    override fun Mission(config: JsonObject, onSolved: () -> Unit) {
        val length = config.number("length", DEFAULT_LENGTH).coerceIn(MIN_LENGTH, MAX_LENGTH)
        val sequence = remember(length) { MemoryRules.generate(length) }

        var progress by remember(sequence) { mutableIntStateOf(0) }
        var failures by remember(sequence) { mutableIntStateOf(0) }
        var replay by remember(sequence) { mutableIntStateOf(0) }
        var lit by remember(sequence) { mutableIntStateOf(-1) }
        var accepting by remember(sequence) { mutableStateOf(false) }

        // Flashes the order once, then hands control to the player. Bumped by [replay] after a
        // mistake, which also cancels an in-flight flash loop.
        LaunchedEffect(sequence, replay) {
            accepting = false
            lit = -1
            delay(FLASH_LEAD_IN_MILLIS)
            for (tile in sequence) {
                lit = tile
                delay(FLASH_MILLIS)
                lit = -1
                delay(FLASH_GAP_MILLIS)
            }
            accepting = true
        }

        LaunchedEffect(progress) {
            if (progress >= sequence.size) onSolved()
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (accepting) R.string.memory_input else R.string.memory_watch,
                ),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.memory_progress, progress, sequence.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (failures > 0) {
                Text(
                    text = stringResource(R.string.memory_wrong),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(MemoryRules.GRID) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(MemoryRules.GRID) { column ->
                            val tile = row * MemoryRules.GRID + column
                            MemoryTile(
                                lit = lit == tile,
                                pressed = progress > 0 && sequence.take(progress).contains(tile),
                                onClick = {
                                    if (accepting && progress < sequence.size) {
                                        if (MemoryRules.matches(sequence, progress, tile)) {
                                            progress += 1
                                        } else {
                                            failures += 1
                                            progress = 0
                                            replay += 1
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun validate(config: JsonObject): JsonObject = JsonObject(
        mapOf("length" to JsonPrimitive(config.number("length", DEFAULT_LENGTH).coerceIn(MIN_LENGTH, MAX_LENGTH))),
    )

    private companion object {
        const val KIND = "memory"
        const val DEFAULT_LENGTH = 4
        const val MIN_LENGTH = 3
        const val MAX_LENGTH = 7
        const val FLASH_MILLIS = 400L
        const val FLASH_GAP_MILLIS = 220L
        const val FLASH_LEAD_IN_MILLIS = 350L
    }
}

@Composable
private fun MemoryTile(
    lit: Boolean,
    pressed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = when {
        lit -> MaterialTheme.colorScheme.primary
        pressed -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .clickable(onClick = onClick),
    )
}

internal fun JsonObject.number(key: String, fallback: Int): Int =
    (this[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: fallback

internal fun JsonObject.with(key: String, value: Int): JsonObject =
    JsonObject(toMutableMap().apply { put(key, JsonPrimitive(value)) })
