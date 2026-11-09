package io.github.caendeith.awakr.ui.sleep

import android.content.Context
import io.github.caendeith.awakr.AwakrApplication
import io.github.caendeith.awakr.data.sleep.HealthConnectSleepSource
import io.github.caendeith.awakr.data.sleep.SleepAvailability

/**
 * Pulls the last two weeks of nights from Health Connect into the local database.
 *
 * The import is idempotent: a night keeps its id (the session start), so syncing again only
 * refreshes the rows it already stored. Nothing leaves the phone.
 */
object SleepSync {

    /** Two weeks is far more than the week the screen draws and keeps latency low. */
    const val WINDOW_DAYS = 14

    private const val DAY_MILLIS = 86_400_000L

    /**
     * Reads the source and stores what it found. The result is deliberately explicit instead of a
     * thrown exception: the screen shows a different message for "not installed", "no permission"
     * and "the read failed", and none of those states may crash it.
     */
    suspend fun refresh(context: Context, now: Long = System.currentTimeMillis()): SleepSyncResult {
        val appContext = context.applicationContext
        val source = HealthConnectSleepSource(appContext)

        return when (runCatching { source.availability() }.getOrDefault(SleepAvailability.UNAVAILABLE)) {
            SleepAvailability.UNAVAILABLE -> SleepSyncResult.SourceUnavailable
            SleepAvailability.UPDATE_REQUIRED -> SleepSyncResult.UpdateRequired
            SleepAvailability.AVAILABLE -> {
                val granted = runCatching { source.grantedPermissions() }
                    .getOrElse { return SleepSyncResult.Failed(it) }
                if (!granted.containsAll(source.requiredPermissions())) {
                    return SleepSyncResult.PermissionMissing
                }

                runCatching {
                    val sessions = source.readSessions(now - WINDOW_DAYS * DAY_MILLIS, now)
                    (appContext as AwakrApplication).data.sleep.replaceWindow(sessions)
                    SleepSyncResult.Imported(sessions.size)
                }.getOrElse { SleepSyncResult.Failed(it) }
            }
        }
    }
}

sealed interface SleepSyncResult {

    /** [count] nights were read; the database may already have had some of them. */
    data class Imported(val count: Int) : SleepSyncResult

    /** Health Connect is not installed or not enabled on this device. */
    object SourceUnavailable : SleepSyncResult

    /** Health Connect is installed but wants an update before it can be read. */
    object UpdateRequired : SleepSyncResult

    /** The user has not granted (or revoked) the read permissions. */
    object PermissionMissing : SleepSyncResult

    data class Failed(val cause: Throwable) : SleepSyncResult
}
