package io.github.caendeith.awakr.ui.ring

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.caendeith.awakr.AwakrApplication
import io.github.caendeith.awakr.alarm.AlarmIntents
import io.github.caendeith.awakr.alarm.AlarmRingService
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.ui.theme.AwakrTheme

/**
 * Full screen alarm UI. It is launched by the full screen intent of the ringing
 * notification, on top of the lock screen, and it owns the dismiss/snooze actions.
 */
class RingActivity : AppCompatActivity() {

    /**
     * Alarm shown by this screen. It is state, not a plain intent read, so that a second alarm
     * that waited in the service queue updates the live screen through [onNewIntent] instead of
     * forcing a recreate (the ring session state lives in the service).
     */
    private val ringRequest = mutableStateOf(RingRequest(alarmId = 0L, snoozeCount = 0))

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

        ringRequest.value = RingRequest.from(intent)
        val app = application as AwakrApplication

        setContent {
            AwakrTheme {
                val request = ringRequest.value
                val state by produceState<RingState>(initialValue = RingState.Loading, request) {
                    if (request.alarmId == 0L) {
                        value = RingState.Missing
                        return@produceState
                    }
                    val alarm = runCatching { app.data.alarms.get(request.alarmId) }.getOrNull()
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
                            request.snoozeCount < current.maxSnoozes,
                        onDismiss = { finishWithAction(AlarmIntents.ACTION_DISMISS, missionPassed = true) },
                        onSnooze = { finishWithAction(AlarmIntents.ACTION_SNOOZE) },
                        onSilenceChanged = { silenced ->
                            AlarmRingService.setSilenced(this@RingActivity, silenced)
                        },
                    )
                }
            }
        }
    }

    /**
     * A queued alarm starts while this screen is alive: reuse it and swap the alarm instead of
     * recreating the activity in the middle of a ring.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val request = RingRequest.from(intent)
        if (request.alarmId != 0L) ringRequest.value = request
    }

    override fun onStop() {
        // Safety net for the mission timer: whenever the ring screen leaves the foreground (the
        // device was locked again, another app was opened, the system moved us away) the alarm
        // must be audible again rather than staying muted forever.
        if (!isChangingConfigurations) {
            AlarmRingService.setSilenced(this, silenced = false)
        }
        super.onStop()
    }

    private data class RingRequest(val alarmId: Long, val snoozeCount: Int) {
        companion object {
            fun from(intent: Intent): RingRequest = RingRequest(
                alarmId = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, 0L),
                snoozeCount = intent.getIntExtra(AlarmIntents.EXTRA_SNOOZE_COUNT, 0),
            )
        }
    }

    private sealed interface RingState {
        data object Loading : RingState
        data object Missing : RingState
        data class Ready(val alarm: Alarm) : RingState
    }

    private fun finishWithAction(action: String, missionPassed: Boolean = false) {
        // Never leave the service muted after the ring was handled from here.
        AlarmRingService.setSilenced(this, silenced = false)
        AlarmRingService.sendAction(this, action, missionPassed)
        // The ring shares the app task, so removing the whole task would also close the user's
        // alarm list. Only a ring that owns its task should take the task down with it.
        if (isTaskRoot) finishAndRemoveTask() else finish()
    }

    /**
     * Draws the ring above the lock screen without asking for the device PIN or pattern.
     *
     * "Like Alarmy": the ring screen must be usable on a secure lock screen, so the keyguard is
     * deliberately not dismissed (a dismissal request turns the ring into an authentication
     * prompt). [setShowWhenLocked] keeps the activity visible above the keyguard; unlocking just
     * reveals the same activity instead of recreating it.
     */
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
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
