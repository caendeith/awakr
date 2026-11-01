package io.github.caendeith.awakr.ui.ring

import java.util.Locale

/**
 * Time budget of the mission while the alarm keeps quiet ("like Alarmy").
 *
 * Pure logic, so the ring screen owns the coroutine and this object only answers "how much time is
 * left" and "how is it shown". A fresh deadline is taken every time the mission restarts.
 */
object MissionTimer {

    /** How long the user has to solve the mission before the alarm rings again. */
    const val DURATION_SECONDS = 90

    const val DURATION_MILLIS = DURATION_SECONDS * 1_000L

    /** The QR scanner cannot hand out a different code, so it is never reset. */
    private const val QR_MISSION_KIND = "code"

    fun deadlineFrom(now: Long): Long = now + DURATION_MILLIS

    /** Time left until [deadline], clamped at zero so a late tick cannot go negative. */
    fun remainingMillis(deadline: Long, now: Long): Long = (deadline - now).coerceAtLeast(0L)

    /**
     * Formats the remaining time as "1:30" / "0:07".
     *
     * Part seconds round up so the counter starts at the full 1:30 and only shows 0:00 when the
     * time is really over.
     */
    fun format(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis.coerceAtLeast(0L) + 999L) / 1_000L
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    /**
     * True when this mission can show a different task after the countdown ran out.
     *
     * Math and typing generate their task on the fly; the QR scanner reads one specific code from
     * the alarm configuration, so there is nothing to replace.
     */
    fun isResettable(missionKind: String?): Boolean =
        !missionKind.isNullOrBlank() && missionKind != QR_MISSION_KIND
}
