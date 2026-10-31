package io.github.caendeith.awakr.plugin.runtime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.caendeith.awakr.core.ui.component.AwakrCard
import io.github.caendeith.awakr.sdk.SettingField
import io.github.caendeith.awakr.sdk.resolve
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Renders a plugin's declarative settings schema. The host owns the look and the storage;
 * the plugin only declares fields.
 */
@Composable
fun SettingsForm(
    fields: List<SettingField>,
    values: JsonObject,
    onChange: (JsonObject) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        fields.forEach { field ->
            when (field) {
                is SettingField.Info -> AwakrCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = field.title.resolve(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = field.body.resolve(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is SettingField.Toggle -> AwakrCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = field.title.resolve(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = SettingsCodec.boolean(values, field.key, field.default),
                            onCheckedChange = { onChange(values.with(field.key, JsonPrimitive(it))) },
                        )
                    }
                }

                is SettingField.Slider -> AwakrCard {
                    val current = SettingsCodec.float(values, field.key, field.default)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = field.title.resolve(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = current.toInt().toString() + (field.unit ?: ""),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Slider(
                            value = current.coerceIn(field.min, field.max),
                            onValueChange = { onChange(values.with(field.key, JsonPrimitive(it))) },
                            valueRange = field.min..field.max,
                            steps = stepsFor(field),
                        )
                    }
                }

                is SettingField.Select, is SettingField.TextInput -> AwakrCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = field.title.resolve(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun stepsFor(field: SettingField.Slider): Int {
    if (field.step <= 0f) return 0
    val range = field.max - field.min
    val count = (range / field.step).toInt() - 1
    return count.coerceIn(0, 100)
}

private fun JsonObject.with(key: String, value: JsonPrimitive): JsonObject =
    JsonObject(toMutableMap().apply { put(key, value) })
