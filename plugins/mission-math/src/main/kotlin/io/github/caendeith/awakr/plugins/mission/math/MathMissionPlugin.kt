package io.github.caendeith.awakr.plugins.mission.math

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.caendeith.awakr.sdk.Capability
import io.github.caendeith.awakr.sdk.MissionPlugin
import io.github.caendeith.awakr.sdk.PluginManifest
import io.github.caendeith.awakr.sdk.StringRes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Solve a few arithmetic problems before the alarm can be dismissed.
 *
 * Config: { "difficulty": "easy" | "medium" | "hard", "count": 1..5 }
 */
class MathMissionPlugin : MissionPlugin {

    override val kind: String = KIND

    override val manifest: PluginManifest = PluginManifest(
        id = "mission-math",
        name = StringRes(R.string.plugin_math_name),
        version = "1.0.0",
        capabilities = setOf(Capability.MISSION),
    )

    @Composable
    override fun Editor(config: JsonObject, onChange: (JsonObject) -> Unit) {
        val difficulty = config.text("difficulty", DIFFICULTIES.first())
        val count = config.number("count", DEFAULT_COUNT).coerceIn(1, MAX_COUNT)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.math_level),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DIFFICULTIES.forEach { level ->
                    FilterChip(
                        selected = difficulty == level,
                        onClick = { onChange(config.with("difficulty", level)) },
                        label = { Text(stringResource(levelTitle(level))) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.math_count, count),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = count.toFloat(),
                onValueChange = { onChange(config.with("count", it.toInt())) },
                valueRange = 1f..MAX_COUNT.toFloat(),
                steps = MAX_COUNT - 2,
            )
        }
    }

    @Composable
    override fun Mission(config: JsonObject, onSolved: () -> Unit) {
        val difficulty = config.text("difficulty", DIFFICULTIES.first())
        val count = config.number("count", DEFAULT_COUNT).coerceIn(1, MAX_COUNT)
        val problems = remember(difficulty, count) { MathPuzzles.generate(difficulty, count) }

        var index by remember { mutableIntStateOf(0) }
        var input by remember { mutableStateOf("") }
        var wrong by remember { mutableStateOf(false) }

        LaunchedEffect(index) {
            if (index >= problems.size) onSolved()
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.math_progress, minOf(index + 1, problems.size), problems.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { index.toFloat() / problems.size },
                modifier = Modifier.fillMaxWidth(),
            )
            val problem = problems.getOrNull(index)
            if (problem != null) {
                Text(
                    text = problem.text + " = ?",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value.filter { it.isDigit() || it == '-' }.take(6)
                        wrong = false
                    },
                    singleLine = true,
                    isError = wrong,
                    supportingText = if (wrong) {
                        { Text(stringResource(R.string.math_wrong)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (input.trim().toIntOrNull() == problem.answer) {
                            input = ""
                            index += 1
                        } else {
                            input = ""
                            wrong = true
                        }
                    },
                    enabled = input.isNotBlank(),
                ) {
                    Text(stringResource(R.string.math_check))
                }
            }
        }
    }

    override fun validate(config: JsonObject): JsonObject = JsonObject(
        mapOf(
            "difficulty" to JsonPrimitive(config.text("difficulty", DIFFICULTIES.first())),
            "count" to JsonPrimitive(config.number("count", DEFAULT_COUNT).coerceIn(1, MAX_COUNT)),
        ),
    )

    private companion object {
        const val KIND = "math"
        const val DEFAULT_COUNT = 2
        const val MAX_COUNT = 5
        val DIFFICULTIES = listOf("easy", "medium", "hard")

        fun levelTitle(level: String): Int = when (level) {
            "medium" -> R.string.math_level_medium
            "hard" -> R.string.math_level_hard
            else -> R.string.math_level_easy
        }
    }
}

internal fun JsonObject.text(key: String, fallback: String): String =
    (this[key] as? JsonPrimitive)?.content ?: fallback

internal fun JsonObject.number(key: String, fallback: Int): Int =
    (this[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: fallback

internal fun JsonObject.with(key: String, value: String): JsonObject =
    JsonObject(toMutableMap().apply { put(key, JsonPrimitive(value)) })

internal fun JsonObject.with(key: String, value: Int): JsonObject =
    JsonObject(toMutableMap().apply { put(key, JsonPrimitive(value)) })
