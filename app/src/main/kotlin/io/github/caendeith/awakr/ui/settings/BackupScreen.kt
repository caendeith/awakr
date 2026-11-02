package io.github.caendeith.awakr.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.caendeith.awakr.AwakrApplication
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.alarm.AlarmScheduler
import io.github.caendeith.awakr.core.ui.component.AwakrCard
import io.github.caendeith.awakr.core.ui.component.PrimaryButton
import io.github.caendeith.awakr.core.ui.component.SecondaryButton
import io.github.caendeith.awakr.data.AwakrData
import io.github.caendeith.awakr.data.backup.BackupDecodeResult
import io.github.caendeith.awakr.data.backup.BackupError
import io.github.caendeith.awakr.data.backup.BackupPayload
import io.github.caendeith.awakr.data.backup.BackupRepository
import io.github.caendeith.awakr.data.backup.ImportMode
import io.github.caendeith.awakr.data.backup.ImportSummary
import io.github.caendeith.awakr.data.backup.backupRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Export and restore of alarms and preferences as a JSON file.
 *
 * The screen only presents the repository; navigation is wired by the host.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    data: AwakrData? = null,
    scheduler: AlarmScheduler? = null,
) {
    val context = LocalContext.current
    val application = context.applicationContext as? AwakrApplication
    val appData = data ?: application?.data
    val appScheduler = scheduler ?: application?.scheduler

    val repository = remember(appData) { appData?.backupRepository() }

    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<BackupMessage?>(null) }
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val repo = repository
        if (uri == null || repo == null) return@rememberLauncherForActivityResult
        busy = true
        message = null
        scope.launch {
            message = try {
                val stream = context.contentResolver.openOutputStream(uri)
                if (stream == null) {
                    BackupMessage.Failed(R.string.backup_error_write)
                } else {
                    BackupMessage.Exported(repo.exportTo(stream).alarmCount)
                }
            } catch (e: IOException) {
                BackupMessage.Failed(R.string.backup_error_write)
            } catch (e: SecurityException) {
                BackupMessage.Failed(R.string.backup_error_write)
            }
            busy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val repo = repository
        if (uri == null || repo == null) return@rememberLauncherForActivityResult
        busy = true
        message = null
        scope.launch {
            val decoded = try {
                context.contentResolver.openInputStream(uri)?.let { repo.readFrom(it) }
            } catch (e: IOException) {
                null
            } catch (e: SecurityException) {
                null
            }
            busy = false
            message = when (decoded) {
                null -> BackupMessage.Failed(R.string.backup_error_read)
                is BackupDecodeResult.Failure -> BackupMessage.DecodeFailed(decoded.error)
                is BackupDecodeResult.Success -> {
                    // Nothing to choose between modes when the file only carries preferences.
                    if (decoded.payload.alarms.isEmpty()) {
                        applyImport(repo, decoded.payload, ImportMode.MERGE, decoded.skippedAlarms, appData, appScheduler)
                        BackupMessage.Imported(0, decoded.skippedAlarms)
                    } else {
                        pendingImport = PendingImport(decoded.payload, decoded.skippedAlarms)
                        null
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (repository == null) {
                AwakrCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.backup_error_read),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                return@Column
            }

            SettingsSection(stringResource(R.string.backup_section_export))
            AwakrCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.backup_export_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.backup_export_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                PrimaryButton(
                    text = stringResource(R.string.backup_export_action),
                    onClick = { exportLauncher.launch(defaultFileName()) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                )
            }

            SettingsSection(stringResource(R.string.backup_section_import))
            AwakrCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.backup_import_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.backup_import_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                SecondaryButton(
                    text = stringResource(R.string.backup_import_action),
                    onClick = {
                        // JSON first; the other types cover files re-shared with a generic MIME.
                        importLauncher.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream"),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                )
            }

            if (busy) {
                Text(
                    text = stringResource(R.string.backup_working),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            message?.let { current ->
                Text(
                    text = messageText(current),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (current.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            Text(
                text = stringResource(R.string.backup_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp, bottom = 24.dp),
            )
        }
    }

    pendingImport?.let { pending ->
        ImportModeDialog(
            onDismiss = { pendingImport = null },
            onModeChosen = { mode ->
                pendingImport = null
                val repo = repository ?: return@ImportModeDialog
                busy = true
                message = null
                scope.launch {
                    val summary = applyImport(
                        repo = repo,
                        payload = pending.payload,
                        mode = mode,
                        skippedAlarms = pending.skippedAlarms,
                        data = appData,
                        scheduler = appScheduler,
                    )
                    message = BackupMessage.Imported(summary.imported, summary.skipped)
                    busy = false
                }
            },
        )
    }
}

/** Import result of one file, waiting for the user to pick [ImportMode]. */
private class PendingImport(
    val payload: BackupPayload,
    val skippedAlarms: Int,
)

private suspend fun applyImport(
    repo: BackupRepository,
    payload: BackupPayload,
    mode: ImportMode,
    skippedAlarms: Int,
    data: AwakrData?,
    scheduler: AlarmScheduler?,
): ImportSummary {
    val summary = repo.importInto(payload, mode, skippedAlarms)
    // AlarmManager holds stale entries for the rows that were just replaced.
    val alarms = data?.alarms?.observeAll()?.first().orEmpty()
    scheduler?.rescheduleAll(alarms)
    return summary
}

@Composable
private fun ImportModeDialog(
    onDismiss: () -> Unit,
    onModeChosen: (ImportMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_mode_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeOption(
                    title = stringResource(R.string.backup_mode_merge),
                    description = stringResource(R.string.backup_mode_merge_desc),
                    onClick = { onModeChosen(ImportMode.MERGE) },
                )
                ModeOption(
                    title = stringResource(R.string.backup_mode_replace),
                    description = stringResource(R.string.backup_mode_replace_desc),
                    onClick = { onModeChosen(ImportMode.REPLACE) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.backup_mode_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeOption(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
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

/** One line of feedback shown under the cards. */
private sealed interface BackupMessage {
    data class Exported(val alarmCount: Int) : BackupMessage
    data class Imported(val alarmCount: Int, val skipped: Int) : BackupMessage
    data class DecodeFailed(val error: BackupError) : BackupMessage
    data class Failed(val messageRes: Int) : BackupMessage

    val isError: Boolean
        get() = this is DecodeFailed || this is Failed
}

@Composable
private fun messageText(message: BackupMessage): String = when (message) {
    is BackupMessage.Exported -> stringResource(R.string.backup_exported, message.alarmCount)
    is BackupMessage.Imported -> if (message.skipped > 0) {
        stringResource(R.string.backup_imported_skipped, message.alarmCount, message.skipped)
    } else {
        stringResource(R.string.backup_imported, message.alarmCount)
    }
    is BackupMessage.DecodeFailed -> when (val error = message.error) {
        is BackupError.NotAwakrBackup -> stringResource(R.string.backup_error_not_awakr)
        is BackupError.UnsupportedSchema -> stringResource(R.string.backup_error_schema)
        is BackupError.Malformed -> stringResource(R.string.backup_error_malformed)
    }
    is BackupMessage.Failed -> stringResource(message.messageRes)
}

/** `awakr-backup-20240601-0730.json`, so consecutive exports do not overwrite each other. */
private fun defaultFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    return "awakr-backup-$stamp.json"
}
