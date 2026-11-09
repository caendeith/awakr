package io.github.caendeith.awakr.data.sleep

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

/** How the phone can reach Health Connect right now. */
enum class SleepAvailability {
    AVAILABLE,
    /** Health Connect is installed but too old to expose the sleep APIs. */
    UPDATE_REQUIRED,
    UNAVAILABLE,
}

/**
 * A read-only provider of sleep sessions, such as Health Connect.
 *
 * Everything the app needs to show the permission UI lives here, so the screen does not have to
 * depend on the Health Connect classes directly.
 */
interface SleepSource {

    fun availability(): SleepAvailability

    /** Permissions without which [readSessions] cannot return any session. */
    fun requiredPermissions(): Set<String>

    /** Every permission the screen may ask the user for. */
    fun permissions(): Set<String>

    /** System dialog that asks for [permissions]. */
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>>

    suspend fun grantedPermissions(): Set<String>

    /** Opens the Health Connect screens; null when the provider is not reachable. */
    fun manageDataIntent(): Intent?

    /** Sessions that finished in [start, end] (epoch millis), already normalised for display. */
    suspend fun readSessions(start: Long, end: Long): List<SleepSession>
}
