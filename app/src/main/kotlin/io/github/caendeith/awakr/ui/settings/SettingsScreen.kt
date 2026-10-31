package io.github.caendeith.awakr.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.audio.BuiltinSounds
import io.github.caendeith.awakr.data.settings.AppSettings
import io.github.caendeith.awakr.data.settings.SwipeAction
import io.github.caendeith.awakr.data.settings.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    defaultSoundName: String,
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit = {},
    onOpenPlugins: () -> Unit,
    onOpenAbout: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit = {},
    onSwipeChange: (SwipeAction) -> Unit = {},
    onDefaultSoundChange: (String) -> Unit = {},
    onSnoozeChange: (Int) -> Unit = {},
) {
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SettingsSection(stringResource(R.string.settings_appearance))
            SettingsRow(
                title = stringResource(R.string.settings_theme),
                value = when (settings.themeMode) {
                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                },
                onClick = { dialog = SettingsDialog.Theme },
            )

            SettingsSection(stringResource(R.string.settings_behavior))
            SettingsRow(
                title = stringResource(R.string.settings_swipe),
                value = when (settings.swipeAction) {
                    SwipeAction.DELETE -> stringResource(R.string.settings_swipe_delete)
                    SwipeAction.TOGGLE -> stringResource(R.string.settings_swipe_toggle)
                    SwipeAction.NONE -> stringResource(R.string.settings_swipe_none)
                },
                onClick = { dialog = SettingsDialog.Swipe },
            )
            SettingsRow(
                title = stringResource(R.string.settings_snooze_default),
                value = stringResource(R.string.settings_minutes, settings.defaultSnoozeMinutes),
                onClick = { dialog = SettingsDialog.Snooze },
            )

            SettingsSection(stringResource(R.string.settings_sound))
            SettingsRow(
                title = stringResource(R.string.settings_default_sound),
                value = defaultSoundName,
                onClick = { dialog = SettingsDialog.Sound },
            )

            SettingsSection(stringResource(R.string.settings_app))
            SettingsRow(
                title = stringResource(R.string.settings_permissions),
                value = "",
                onClick = onOpenPermissions,
            )
            SettingsRow(
                title = stringResource(R.string.settings_plugins),
                value = "",
                onClick = onOpenPlugins,
            )
            SettingsRow(
                title = stringResource(R.string.settings_about),
                value = "",
                onClick = onOpenAbout,
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    when (val current = dialog) {
        null -> Unit
        SettingsDialog.Theme -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = listOf(
                Triple(ThemeMode.SYSTEM, stringResource(R.string.settings_theme_system), settings.themeMode == ThemeMode.SYSTEM),
                Triple(ThemeMode.DARK, stringResource(R.string.settings_theme_dark), settings.themeMode == ThemeMode.DARK),
                Triple(ThemeMode.LIGHT, stringResource(R.string.settings_theme_light), settings.themeMode == ThemeMode.LIGHT),
            ),
            onDismiss = { dialog = null },
            onSelect = { selected -> onThemeChange(selected); dialog = null },
        )
        SettingsDialog.Swipe -> ChoiceDialog(
            title = stringResource(R.string.settings_swipe),
            options = listOf(
                Triple(SwipeAction.DELETE, stringResource(R.string.settings_swipe_delete), settings.swipeAction == SwipeAction.DELETE),
                Triple(SwipeAction.TOGGLE, stringResource(R.string.settings_swipe_toggle), settings.swipeAction == SwipeAction.TOGGLE),
                Triple(SwipeAction.NONE, stringResource(R.string.settings_swipe_none), settings.swipeAction == SwipeAction.NONE),
            ),
            onDismiss = { dialog = null },
            onSelect = { selected -> onSwipeChange(selected); dialog = null },
        )
        SettingsDialog.Snooze -> ChoiceDialog(
            title = stringResource(R.string.settings_snooze_default),
            options = listOf(5, 10, 15, 20, 30).map { minutes ->
                Triple(minutes, stringResource(R.string.settings_minutes, minutes), settings.defaultSnoozeMinutes == minutes)
            },
            onDismiss = { dialog = null },
            onSelect = { selected -> onSnoozeChange(selected); dialog = null },
        )
        SettingsDialog.Sound -> ChoiceDialog(
            title = stringResource(R.string.settings_default_sound),
            options = BuiltinSounds.all.map { sound ->
                Triple(
                    BuiltinSounds.idOf(sound),
                    stringResource(sound.nameRes),
                    settings.defaultSoundId == BuiltinSounds.idOf(sound),
                )
            },
            onDismiss = { dialog = null },
            onSelect = { selected -> onDefaultSoundChange(selected); dialog = null },
        )
    }
}

private sealed interface SettingsDialog {
    data object Theme : SettingsDialog
    data object Swipe : SettingsDialog
    data object Snooze : SettingsDialog
    data object Sound : SettingsDialog
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Triple<T, String, Boolean>>,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (value, label, selected) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = { onSelect(value) })
                        Text(text = label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        if (value.isNotBlank()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
