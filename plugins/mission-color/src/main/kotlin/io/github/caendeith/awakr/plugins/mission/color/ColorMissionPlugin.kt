package io.github.caendeith.awakr.plugins.mission.color

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
 * Clear every tile of the colour shown at the top of the grid.
 *
 * Config: { "targets": 2..6 } — how many tiles of the target colour the board holds.
 *
 * A wrong tap only counts a miss; progress is never taken away, so the mission cannot paint the
 * player into a corner no matter how sleepy they are.
 */
class ColorMissionPlugin : MissionPlugin {

    override val kind: String = KIND

    override val manifest: PluginManifest = PluginManifest(
        id = "mission-color",
        name = StringRes(R.string.plugin_color_name),
        version = "1.0.0",
        capabilities = setOf(Capability.MISSION),
    )

    @Composable
    override fun Editor(config: JsonObject, onChange: (JsonObject) -> Unit) {
        val targets = config.number("targets", DEFAULT_TARGETS).coerceIn(MIN_TARGETS, MAX_TARGETS)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.color_targets, targets),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = targets.toFloat(),
                onValueChange = { onChange(config.with("targets", it.roundToInt())) },
                valueRange = MIN_TARGETS.toFloat()..MAX_TARGETS.toFloat(),
                steps = MAX_TARGETS - MIN_TARGETS - 1,
            )
        }
    }

    @Composable
    override fun Mission(config: JsonObject, onSolved: () -> Unit) {
        val targets = config.number("targets", DEFAULT_TARGETS).coerceIn(MIN_TARGETS, MAX_TARGETS)
        val board = remember(targets) { ColorRules.generate(targets) }

        var cleared by remember(board) { mutableStateOf(emptySet<Int>()) }
        var misses by remember(board) { mutableIntStateOf(0) }
        var wrongTile by remember(board) { mutableIntStateOf(-1) }

        LaunchedEffect(cleared) {
            if (cleared.size >= board.total) onSolved()
        }

        LaunchedEffect(wrongTile) {
            if (wrongTile >= 0) {
                delay(WRONG_FLASH_MILLIS)
                wrongTile = -1
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Palette[board.target % Palette.size]),
                )
                Text(
                    text = stringResource(R.string.color_hint),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = stringResource(R.string.color_progress, cleared.size, board.total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (misses > 0) {
                Text(
                    text = stringResource(R.string.color_miss, misses),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(ColorRules.GRID) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(ColorRules.GRID) { column ->
                            val tile = row * ColorRules.GRID + column
                            ColorTile(
                                color = Palette[board.tiles[tile] % Palette.size],
                                cleared = tile in cleared,
                                wrong = wrongTile == tile,
                                onClick = {
                                    when {
                                        tile in cleared -> Unit
                                        board.isTarget(tile) -> cleared = cleared + tile
                                        else -> {
                                            misses += 1
                                            wrongTile = tile
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
        mapOf("targets" to JsonPrimitive(config.number("targets", DEFAULT_TARGETS).coerceIn(MIN_TARGETS, MAX_TARGETS))),
    )

    private companion object {
        const val KIND = "color"
        const val DEFAULT_TARGETS = 3
        const val MIN_TARGETS = 2
        const val MAX_TARGETS = 6
        const val WRONG_FLASH_MILLIS = 400L
    }
}

/** Saturated, mutually distinguishable tile colours; the grid carries no text. */
private val Palette = listOf(
    Color(0xFF1E88E5),
    Color(0xFFE53935),
    Color(0xFF43A047),
    Color(0xFFFDD835),
)

@Composable
private fun ColorTile(
    color: Color,
    cleared: Boolean,
    wrong: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = when {
        wrong -> MaterialTheme.colorScheme.error
        cleared -> MaterialTheme.colorScheme.surfaceVariant
        else -> color
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(shown)
            .clickable(onClick = onClick),
    )
}

internal fun JsonObject.number(key: String, fallback: Int): Int =
    (this[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: fallback

internal fun JsonObject.with(key: String, value: Int): JsonObject =
    JsonObject(toMutableMap().apply { put(key, JsonPrimitive(value)) })
