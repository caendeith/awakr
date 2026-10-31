package io.github.caendeith.awakr.core.perm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** System permissions an alarm clock cannot work without, plus the optional ones. */
enum class AlarmPermission {
    /** Needed on API 33+ so the fallback notification is visible at all. */
    NOTIFICATIONS,

    /** Needed on API 31+ for `setAlarmClock` / `setExactAndAllowWhileIdle`. */
    EXACT_ALARM,

    /** Only required while a camera based mission is enabled. */
    CAMERA,

    /** Optional: makes the ring screen reliable on ROMs that ignore background activity starts. */
    OVERLAY,
}

/**
 * One permission row of the onboarding screen.
 *
 * @param required false means "nice to have": the app works without it, the screen only recommends it.
 */
data class PermissionItem(
    val permission: AlarmPermission,
    val required: Boolean,
)

/** The requirement list together with what is currently granted. */
data class PermissionSnapshot(
    val items: List<PermissionItem>,
    val granted: Set<AlarmPermission>,
) {
    val missingRequired: List<AlarmPermission> =
        items.filter { it.required && it.permission !in granted }.map { it.permission }

    val ready: Boolean = missingRequired.isEmpty()

    fun isGranted(permission: AlarmPermission): Boolean = permission in granted
}

/**
 * Pure policy: which permissions matter on a given API level and app configuration.
 *
 * Kept free of Android calls so the rules are unit tested; [AlarmPermissionChecks] does the
 * platform queries.
 */
object AlarmPermissionPolicy {

    fun requirements(sdkInt: Int, cameraMissionEnabled: Boolean): List<PermissionItem> = listOf(
        PermissionItem(AlarmPermission.NOTIFICATIONS, required = sdkInt >= Build.VERSION_CODES.TIRAMISU),
        PermissionItem(AlarmPermission.EXACT_ALARM, required = sdkInt >= Build.VERSION_CODES.S),
        PermissionItem(AlarmPermission.CAMERA, required = cameraMissionEnabled),
        PermissionItem(AlarmPermission.OVERLAY, required = false),
    )

    fun snapshot(
        sdkInt: Int,
        cameraMissionEnabled: Boolean,
        granted: Set<AlarmPermission>,
    ): PermissionSnapshot = PermissionSnapshot(requirements(sdkInt, cameraMissionEnabled), granted)
}

/** Platform side of [AlarmPermissionPolicy]: reads the current state and opens the right settings. */
object AlarmPermissionChecks {

    fun isGranted(context: Context, permission: AlarmPermission): Boolean = when (permission) {
        AlarmPermission.NOTIFICATIONS -> notificationsEnabled(context)
        AlarmPermission.EXACT_ALARM -> exactAlarmsAllowed(context)
        AlarmPermission.CAMERA ->
            context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        AlarmPermission.OVERLAY ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun snapshot(context: Context, cameraMissionEnabled: Boolean): PermissionSnapshot {
        val requirements = AlarmPermissionPolicy.requirements(
            sdkInt = Build.VERSION.SDK_INT,
            cameraMissionEnabled = cameraMissionEnabled,
        )
        val granted = requirements
            .map { it.permission }
            .filter { runCatching { isGranted(context, it) }.getOrDefault(false) }
            .toSet()
        return PermissionSnapshot(requirements, granted)
    }

    /** Notifications can be disabled as a whole on API 26+, independently of the runtime grant. */
    fun notificationsEnabled(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * USE_EXACT_ALARM (declared in the manifest) is granted at install time, so this normally
     * returns true; SCHEDULE_EXACT_ALARM devices may report false and need the settings screen.
     */
    fun exactAlarmsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
    }

    /** Where to send the user when a permission is missing and cannot be requested in-page. */
    fun settingsIntent(context: Context, permission: AlarmPermission): Intent {
        val packageUri = Uri.fromParts("package", context.packageName, null)
        return when (permission) {
            AlarmPermission.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            AlarmPermission.EXACT_ALARM -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
            AlarmPermission.CAMERA -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            AlarmPermission.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
