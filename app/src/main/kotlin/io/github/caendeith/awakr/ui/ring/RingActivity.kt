package io.github.caendeith.awakr.ui.ring

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import io.github.caendeith.awakr.AwakrApplication
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.alarm.AlarmIntents
import io.github.caendeith.awakr.alarm.AlarmRingService
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.ui.theme.AwakrTheme
import io.github.caendeith.awakr.ui.format.formatTime

/**
 * Full screen alarm UI. It is launched by the full screen intent of the ringing
 * notification, on top of the lock screen, and it owns the dismiss/snooze actions.
 */
class RingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val alarmId = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, 0L)
        val app = application as AwakrApplication

        setContent {
            AwakrTheme {
                val alarm by produceState<Alarm?>(initialValue = null, alarmId) {
                    value = runCatching { app.data.alarms.get(alarmId) }.getOrNull()
                }
                val current = alarm
                RingScreen(
                    time = current?.let { formatTime(it.hour, it.minute) } ?: "--:--",
                    label = current?.label.orEmpty(),
                    missionText = stringResource(R.string.ring_mission_placeholder),
                    showSnooze = current?.snoozeEnabled ?: true,
                    onDismiss = { finishWithAction(AlarmIntents.ACTION_DISMISS) },
                    onSnooze = { finishWithAction(AlarmIntents.ACTION_SNOOZE) },
                )
            }
        }
    }

    @Deprecated("Back must not dismiss a ringing alarm")
    override fun onBackPressed() {
        // Deliberately ignored: an alarm cannot be dismissed with the back gesture.
    }

    private fun finishWithAction(action: String) {
        AlarmRingService.sendAction(this, action)
        finishAndRemoveTask()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguard = getSystemService(android.app.KeyguardManager::class.java)
            keyguard?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
    }
}
