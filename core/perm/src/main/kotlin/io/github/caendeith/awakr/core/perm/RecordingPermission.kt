package io.github.caendeith.awakr.core.perm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/** Whether the microphone can be used right now. */
enum class MicrophoneStatus {
    /** Permission granted and the device has a microphone. */
    READY,

    /** The user must grant the permission; ask it only when they actually record. */
    NEEDS_PERMISSION,

    /** No microphone at all (rare, e.g. some TV boxes); recording is hidden. */
    UNSUPPORTED,
}

/**
 * The optional RECORD_AUDIO permission.
 *
 * It is deliberately kept outside [AlarmPermission]: recording a custom sound is a niche feature,
 * so the permission is requested from the sound picker on first tap and is never part of the
 * onboarding requirements.
 */
object RecordingPermission {

    /** The manifest declaration; declared by the app module. */
    const val PERMISSION: String = Manifest.permission.RECORD_AUDIO

    /** Single entry for [androidx.activity.compose.rememberLauncherForActivityResult]. */
    val request: Array<String> = arrayOf(PERMISSION)

    fun isGranted(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun hasMicrophone(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

    fun status(context: Context): MicrophoneStatus = when {
        !hasMicrophone(context) -> MicrophoneStatus.UNSUPPORTED
        isGranted(context) -> MicrophoneStatus.READY
        else -> MicrophoneStatus.NEEDS_PERMISSION
    }

    /** True when the user previously refused; the next ask should come with a short explanation. */
    fun shouldExplain(requested: Boolean, granted: Boolean, canAskAgain: Boolean): Boolean =
        requested && !granted && canAskAgain

    /** Fallback when the permission is switched off for good: deep link into the app settings. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
