package io.github.caendeith.awakr.ui.ring

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import io.github.caendeith.awakr.AwakrApplication
import io.github.caendeith.awakr.alarm.AlarmIntents
import io.github.caendeith.awakr.alarm.AlarmRingService
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.ui.theme.AwakrTheme

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

        // Back must never dismiss a ringing alarm: the dispatcher swallows both the button and
        // the predictive-back gesture (an onBackPressed override no longer receives the gesture
        // on API 36+).
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Deliberately ignored: the mission is the only way out.
                }
            },
        )

        val alarmId = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, 0L)
        val snoozeCount = intent.getIntExtra(AlarmIntents.EXTRA_SNOOZE_COUNT, 0)
        val app = application as AwakrApplication

        setContent {
            AwakrTheme {
                val state by produceState<RingState>(initialValue = RingState.Loading, alarmId) {
                    val alarm = runCatching { app.data.alarms.get(alarmId) }.getOrNull()
                    value = if (alarm == null) RingState.Missing else RingState.Ready(alarm)
                }
                val current = (state as? RingState.Ready)?.alarm
                val plugin = remember(current) {
                    current?.let { app.plugins.mission(it.missionPluginId) }
                }
                val enabled by produceState(initialValue = true, plugin) {
                    value = plugin?.let { mission ->
                        runCatching {
                            app.data.pluginStates.get(mission.manifest.id)?.enabled
                        }.getOrNull() ?: true
                    } ?: false
                }
                // Loading is a distinct state: rendering the ready screen with a null alarm would
                // flash the fallback mission ("mission unavailable") for a frame or two.
                if (state == RingState.Loading) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                } else {
                    RingScreen(
                        alarm = current,
                        mission = plugin.takeIf { enabled },
                        // The snooze allowance is per occurrence: after maxSnoozes the only way
                        // out is passing the mission.
                        showSnooze = current != null &&
                            current.snoozeEnabled &&
                            snoozeCount < current.maxSnoozes,
                        onDismiss = { finishWithAction(AlarmIntents.ACTION_DISMISS, missionPassed = true) },
                        onSnooze = { finishWithAction(AlarmIntents.ACTION_SNOOZE) },
                    )
                }
            }
        }
    }

    private sealed interface RingState {
        data object Loading : RingState
        data object Missing : RingState
        data class Ready(val alarm: Alarm) : RingState
    }

    private fun finishWithAction(action: String, missionPassed: Boolean = false) {
        AlarmRingService.sendAction(this, action, missionPassed)
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
