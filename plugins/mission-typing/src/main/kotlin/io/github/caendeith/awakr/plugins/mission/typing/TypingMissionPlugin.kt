package io.github.caendeith.awakr.plugins.mission.typing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.caendeith.awakr.sdk.Capability
import io.github.caendeith.awakr.sdk.MissionPlugin
import io.github.caendeith.awakr.sdk.PluginManifest
import io.github.caendeith.awakr.sdk.StringRes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Retype a phrase exactly to stop the alarm.
 *
 * Config: { "phrase": String } — an empty string falls back to the built-in phrase.
 */
class TypingMissionPlugin : MissionPlugin {

    override val kind: String = KIND

    override val manifest: PluginManifest = PluginManifest(
        id = "mission-typing",
        name = StringRes(R.string.plugin_typing_name),
        version = "1.0.0",
        capabilities = setOf(Capability.MISSION),
    )

    @Composable
    override fun Editor(config: JsonObject, onChange: (JsonObject) -> Unit) {
        val phrase = config.text("phrase", "")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.typing_phrase),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = phrase,
                onValueChange = { onChange(config.with("phrase", it)) },
                singleLine = false,
                minLines = 2,
                placeholder = { Text(stringResource(R.string.typing_default_phrase)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @Composable
    override fun Mission(config: JsonObject, onSolved: () -> Unit) {
        val defaultPhrase = stringResource(R.string.typing_default_phrase)
        val phrase = config.text("phrase", "").ifBlank { defaultPhrase }
        var input by remember { mutableStateOf("") }
        val matches = input.trim().equals(phrase.trim(), ignoreCase = true) && input.isNotBlank()

        LaunchedEffect(matches) {
            if (matches) onSolved()
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.typing_hint),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = phrase,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = false,
                minLines = 3,
                placeholder = { Text(stringResource(R.string.typing_placeholder)) },
                isError = input.isNotBlank() && !matches,
                supportingText = if (input.isNotBlank() && !matches) {
                    { Text(stringResource(R.string.typing_wrong)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    override fun validate(config: JsonObject): JsonObject = JsonObject(
        mapOf("phrase" to JsonPrimitive(config.text("phrase", ""))),
    )

    private companion object {
        const val KIND = "typing"
    }
}

internal fun JsonObject.text(key: String, fallback: String): String =
    (this[key] as? JsonPrimitive)?.content ?: fallback

internal fun JsonObject.with(key: String, value: String): JsonObject =
    JsonObject(toMutableMap().apply { put(key, JsonPrimitive(value)) })
