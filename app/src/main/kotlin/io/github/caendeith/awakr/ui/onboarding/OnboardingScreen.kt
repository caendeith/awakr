package io.github.caendeith.awakr.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.perm.AlarmPermission
import io.github.caendeith.awakr.core.perm.AlarmPermissionChecks
import io.github.caendeith.awakr.core.perm.PermissionItem
import io.github.caendeith.awakr.core.perm.PermissionSnapshot
import io.github.caendeith.awakr.core.ui.component.AwakrCard
import io.github.caendeith.awakr.core.ui.component.PrimaryButton
import io.github.caendeith.awakr.core.ui.theme.PillShape

/**
 * First launch screen: explains, requests and verifies the system permissions an alarm clock
 * cannot work without. Every row is re-checked when the app comes back to the foreground, so
 * returning from a system settings page updates the state without a manual refresh.
 */
@Composable
fun OnboardingScreen(
    cameraMissionEnabled: Boolean,
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(AlarmPermissionChecks.snapshot(context, cameraMissionEnabled)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val refresh = { snapshot = AlarmPermissionChecks.snapshot(context, cameraMissionEnabled) }

    DisposableEffect(lifecycleOwner, cameraMissionEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh() }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh() }

    val onRequest: (AlarmPermission) -> Unit = { permission ->
        when (permission) {
            AlarmPermission.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    runCatching {
                        context.startActivity(AlarmPermissionChecks.settingsIntent(context, permission))
                    }
                }

            AlarmPermission.CAMERA -> {
                if (context.checkSelfPermission(Manifest.permission.CAMERA) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    runCatching {
                        context.startActivity(AlarmPermissionChecks.settingsIntent(context, permission))
                    }
                } else {
                    cameraLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            else -> runCatching {
                context.startActivity(AlarmPermissionChecks.settingsIntent(context, permission))
            }
        }
        refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(text = stringResource(R.string.action_back))
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        snapshot.items.forEach { item ->
            PermissionRow(
                item = item,
                granted = snapshot.isGranted(item.permission),
                onRequest = onRequest,
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(8.dp))
        // The hint points at the settings entry, which makes no sense when we came from there.
        if (onBack == null) {
            Text(
                text = stringResource(R.string.onboarding_later_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
        PrimaryButton(
            text = stringResource(
                if (onBack == null) R.string.onboarding_continue else R.string.action_done,
            ),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PermissionRow(
    item: PermissionItem,
    granted: Boolean,
    onRequest: (AlarmPermission) -> Unit,
) {
    val title = when (item.permission) {
        AlarmPermission.NOTIFICATIONS -> R.string.perm_notifications_title
        AlarmPermission.EXACT_ALARM -> R.string.perm_exact_title
        AlarmPermission.CAMERA -> R.string.perm_camera_title
        AlarmPermission.OVERLAY -> R.string.perm_overlay_title
    }
    val body = when (item.permission) {
        AlarmPermission.NOTIFICATIONS -> R.string.perm_notifications_body
        AlarmPermission.EXACT_ALARM -> R.string.perm_exact_body
        AlarmPermission.CAMERA -> R.string.perm_camera_body
        AlarmPermission.OVERLAY -> R.string.perm_overlay_body
    }
    val status = when {
        granted -> stringResource(R.string.perm_granted)
        !item.required -> stringResource(R.string.perm_optional)
        else -> stringResource(R.string.perm_missing)
    }
    val statusColor = when {
        granted -> MaterialTheme.colorScheme.primary
        !item.required -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }

    AwakrCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                modifier = Modifier
                    .clip(PillShape)
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!granted) {
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = { onRequest(item.permission) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                val label = if (item.permission == AlarmPermission.NOTIFICATIONS ||
                    item.permission == AlarmPermission.CAMERA
                ) {
                    R.string.perm_allow
                } else {
                    R.string.perm_open_settings
                }
                Text(text = stringResource(label), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}