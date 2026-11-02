package io.github.caendeith.awakr.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.caendeith.awakr.AwakrApplication
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.alarm.AlarmLog
import io.github.caendeith.awakr.core.alarm.AlarmOutcome
import io.github.caendeith.awakr.core.audio.AlarmSoundPlayer
import io.github.caendeith.awakr.core.audio.SoundResolver
import io.github.caendeith.awakr.core.notify.AwakrNotifications
import io.github.caendeith.awakr.ui.format.formatTime
import io.github.caendeith.awakr.ui.widget.AlarmWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Keeps an alarm ringing in the foreground: plays the sound, vibrates and shows the
 * full screen ring UI. The service owns the lifecycle of a single ringing alarm.
 */
class AlarmRingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val soundPlayer by lazy { AlarmSoundPlayer(this) }

    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var currentAlarm: Alarm? = null
    private var timeoutJob: Job? = null
    private var panicJob: Job? = null
    private var speech: TextToSpeech? = null

    /** Safety net that un-mutes the sound if the ring screen disappears without doing it. */
    private var silenceWatchdog: Job? = null
    private var ringing = false
    private var triggerAtMillis = 0L

    /** How many times the current occurrence has already been snoozed. */
    private var snoozeCount = 0

    /**
     * True while the current ring repeats an occurrence the user already dismissed (wake-up
     * check). Such a ring is part of the earlier occurrence, not a new one.
     */
    private var reRingFromCheck = false

    /** Id of the alarm that is ringing right now; 0 when nothing rings. */
    private var ringingAlarmId = 0L

    /**
     * Alarms that fired while another one was already ringing. Two alarms can share a minute, and
     * a ring cannot be interrupted, so the second one waits here and starts as soon as the first is
     * dismissed or snoozed.
     */
    private val pendingRings = ArrayDeque<PendingRing>()

    private data class PendingRing(
        val alarmId: Long,
        val triggerAt: Long,
        val snoozeCount: Int,
        val wakeUpCheck: Boolean,
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AwakrNotifications.ensureChannels(this)
        val placeholder = placeholderNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The three-argument overload (and the mediaPlayback type) only exists from API 29.
            startForeground(
                AwakrNotifications.NOTIFICATION_RINGING,
                placeholder,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(AwakrNotifications.NOTIFICATION_RINGING, placeholder)
        }
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AlarmIntents.ACTION_DISMISS -> {
                // A late tap (the ring already timed out) must not write a second, contradictory
                // log row for an occurrence that has been finished once.
                if (!ringing) {
                    Log.i(TAG, "dismiss without a ring, ignoring")
                    if (pendingRings.isEmpty()) stopSelf()
                } else {
                    finish(
                        outcome = AlarmOutcome.DISMISSED,
                        missionPassed = intent.getBooleanExtra(AlarmIntents.EXTRA_MISSION_PASSED, false),
                    )
                }
            }
            AlarmIntents.ACTION_SNOOZE -> {
                if (!ringing) {
                    Log.i(TAG, "snooze without a ring, ignoring")
                    if (pendingRings.isEmpty()) stopSelf()
                } else {
                    snooze()
                }
            }
            AlarmIntents.ACTION_REPOST -> {
                // The user pushed the ring screen away: keep the notification (and its full-screen
                // intent) authoritative so the way back is always one tap.
                currentAlarm?.let { alarm -> scope.launch { notifyRinging(alarm) } }
            }
            AlarmIntents.ACTION_SET_SILENCED -> {
                setSilenced(intent.getBooleanExtra(AlarmIntents.EXTRA_SILENCED, false))
            }
            AlarmIntents.ACTION_RING, null -> {
                val alarmId = intent?.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, 0L) ?: 0L
                if (alarmId != 0L) {
                    startRinging(
                        alarmId = alarmId,
                        triggerAt = intent?.getLongExtra(AlarmIntents.EXTRA_TRIGGER_AT, 0L) ?: 0L,
                        snoozeCount = intent?.getIntExtra(AlarmIntents.EXTRA_SNOOZE_COUNT, 0) ?: 0,
                        wakeUpCheck = intent?.getBooleanExtra(AlarmIntents.EXTRA_WAKE_UP_CHECK, false)
                            ?: false,
                    )
                }
            }
        }
        return START_STICKY
    }

    private fun startRinging(
        alarmId: Long,
        triggerAt: Long = 0L,
        snoozeCount: Int = 0,
        wakeUpCheck: Boolean = false,
    ) {
        if (ringingAlarmId == alarmId || pendingRings.any { it.alarmId == alarmId }) {
            // Duplicate delivery for an alarm that is already ringing or queued (full-screen intent
            // retry, repeated broadcast): the user must not hear it twice, and a stray trigger must
            // not touch the state of the running ring (it would reset the snooze counter).
            Log.i(TAG, "already ringing or queued, ignoring trigger for alarm $alarmId")
            return
        }
        if (ringing) {
            // Another alarm is ringing right now. Do not interrupt it and do not drop this one:
            // queue it and start it as soon as the current ring is finished.
            pendingRings.addLast(PendingRing(alarmId, triggerAt, snoozeCount, wakeUpCheck))
            Log.i(TAG, "queued alarm $alarmId until the current ring is finished")
            return
        }
        ringing = true
        ringingAlarmId = alarmId
        this.triggerAtMillis = triggerAt
        this.snoozeCount = snoozeCount
        this.reRingFromCheck = wakeUpCheck
        val app = application as AwakrApplication
        scope.launch {
            val alarm = runCatching { app.data.alarms.get(alarmId) }.getOrNull()
            if (alarm == null) {
                Log.w(TAG, "alarm $alarmId no longer exists")
                ringing = false
                ringingAlarmId = 0L
                stopSelf()
                return@launch
            }
            currentAlarm = alarm
            if (triggerAtMillis <= 0L) triggerAtMillis = System.currentTimeMillis()
            // Read the display state before anything else wakes it: the answer decides which launch
            // path is used below.
            val displayAsleep = getSystemService(PowerManager::class.java)?.isInteractive != true
            startVibration(alarm)
            notifyRinging(alarm)
            launchRingActivity(alarm, displayAsleep)
            // Resolving the sound id touches settings storage, so the UI above comes first.
            startSound(alarm)
            speakLabelIfEnabled(alarm)
            panicJob?.cancel()
            panicJob = scope.launch {
                // Alarmy calls this the backup sound: if nothing happened for a while, stop being
                // polite and ring at full volume.
                delay(PANIC_AFTER_MS)
                if (ringing) {
                    Log.i(TAG, "panic stage: ringing at full volume")
                    soundPlayer.boost()
                }
            }
            timeoutJob?.cancel()
            timeoutJob = scope.launch {
                delay(RING_TIMEOUT_MS)
                Log.w(TAG, "ring timeout, stopping")
                finish(AlarmOutcome.MISSED, missionPassed = false)
            }
        }
    }

    /**
     * The full-screen intent is the sanctioned way to surface the ring screen, but some OEM
     * builds and Do-not-disturb policies drop it. An explicit start covers those cases as soon
     * as the app is allowed to launch activities from the background (see the onboarding step
     * that asks for "display over other apps").
     */
    private fun launchRingActivity(alarm: Alarm, displayAsleep: Boolean) {
        // With the screen off the notification's full screen intent is the only launch path the
        // system honours: it is the one that also turns the display on. A direct start from a
        // background service is refused there, and the refused attempt leaves the activity record
        // half created, so the full screen intent cannot open anything afterwards either and the
        // alarm rings on a black screen. The direct start therefore stays on the awake path only,
        // where it opens the ring screen a little sooner than the notification does.
        if (displayAsleep) {
            Log.i(TAG, "display is off, the full screen intent opens the ring screen")
            return
        }
        runCatching { startActivity(AlarmIntents.ringActivityIntent(this, alarm.id, snoozeCount)) }
            .onFailure { Log.w(TAG, "could not launch ring activity", it) }
    }

    /**
     * Starts the sound the user actually chose for this alarm.
     *
     * The choice lives in [Alarm.soundId] ("builtin:<id>", "file:<path>" or empty meaning "the
     * default sound from the settings"). [SoundResolver] validates it and falls back to the
     * settings default and finally to a shipped tone, so a deleted import can never end with a
     * silent alarm. The legacy soundUri/soundPackId columns are deliberately ignored here.
     */
    /**
     * Speaks the alarm label once the ring starts, when the user asked for it in Settings.
     *
     * Text-to-speech runs on the on-device engine, so this stays offline; when no engine is
     * installed the utterance is dropped and the ring continues as usual.
     */
    private fun speakLabelIfEnabled(alarm: Alarm) {
        if (alarm.label.isBlank()) return
        val app = application as AwakrApplication
        scope.launch {
            val settings = runCatching { app.data.settings.settings.first() }.getOrNull() ?: return@launch
            if (!settings.speakLabel) return@launch
            val label = alarm.label
            withContext(Dispatchers.Main) {
                runCatching { speech?.shutdown() }
                speech = TextToSpeech(this@AlarmRingService) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        runCatching {
                            speech?.language = Locale.getDefault()
                            speech?.speak(label, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_LABEL)
                        }.onFailure { Log.w(TAG, "cannot speak the label", it) }
                    } else {
                        Log.w(TAG, "text-to-speech is unavailable ($status)")
                    }
                }
            }
        }
    }

    private suspend fun startSound(alarm: Alarm) {
        val uri = SoundResolver.uri(this, alarm.soundId, defaultSoundId())
        // Logged so a support log shows which tone the alarm actually used: the user picks it in
        // the editor as builtin:<id> or file:<path>, and both are easy to mix up in a bug report.
        Log.i(TAG, "playing sound ${alarm.soundId.ifBlank { "<default>" }} -> ${uri}")
        soundPlayer.play(
            uri = uri,
            rampSeconds = alarm.rampSeconds,
            startVolumePercent = alarm.volumeStart,
            scope = scope,
        )
    }

    /** Default sound from the settings, or null when the settings cannot be read. */
    private suspend fun defaultSoundId(): String? =
        runCatching { (application as AwakrApplication).data.settings.settings.first().defaultSoundId }
            .getOrNull()

    /**
     * Mutes or un-mutes the ring sound while the ring session keeps running.
     *
     * Used by the ring screen while the mission timer counts down. A call that arrives when
     * nothing rings is ignored, and the service then stops so that no empty foreground
     * notification is left behind.
     */
    private fun setSilenced(silenced: Boolean) {
        if (!ringing) {
            Log.i(TAG, "setSilenced($silenced) without a ring, ignoring")
            if (pendingRings.isEmpty()) stopSelf()
            return
        }
        Log.i(TAG, "silenced=$silenced")
        soundPlayer.setSilenced(silenced)
        silenceWatchdog?.cancel()
        silenceWatchdog = if (silenced) {
            // Only the ring screen mutes the sound, and only while the mission timer runs. If that
            // screen goes away (crash, process death, task removed) the alarm would stay silent
            // forever, so the mute always expires on its own.
            scope.launch {
                delay(MAX_SILENCE_MS)
                if (ringing && soundPlayer.isSilenced) {
                    Log.w(TAG, "silence watchdog fired, ringing again")
                    soundPlayer.setSilenced(false)
                }
            }
        } else {
            null
        }
    }

    private fun startVibration(alarm: Alarm) {
        if (!alarm.vibrate) return
        val deviceVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VibratorManager::class.java))?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator = deviceVibrator
        runCatching {
            deviceVibrator?.vibrate(
                VibrationEffect.createWaveform(VIBRATION_PATTERN, 0),
            )
        }.onFailure { Log.w(TAG, "vibration unavailable", it) }
    }

    private fun snooze() {
        val alarm = currentAlarm
        val minutes = alarm?.snoozeMinutes ?: 10
        if (alarm != null) {
            val app = application as AwakrApplication
            app.scheduler.scheduleSnooze(alarm, minutes, snoozeCount + 1)
        }
        finish(AlarmOutcome.SNOOZED, missionPassed = true)
    }

    private fun finish(outcome: AlarmOutcome, missionPassed: Boolean) {
        Log.i(TAG, "ring finished: $outcome")
        panicJob?.cancel()
        panicJob = null
        val alarm = currentAlarm
        // Read the ring state before the fields below are cleared; the work below is asynchronous.
        val fromCheck = reRingFromCheck
        val ringSnoozeCount = snoozeCount
        if (alarm != null) {
            val app = application as AwakrApplication
            val firedAt = System.currentTimeMillis()
            val scheduledAt = if (triggerAtMillis > 0L) triggerAtMillis else firedAt
            val loggedSnoozeCount = if (outcome == AlarmOutcome.SNOOZED) snoozeCount + 1 else 0
            app.appScope.launch {
                // A wake-up-check re-ring repeats an occurrence that is already in the history: a
                // second row would make one alarm look like two fires in the log and in the
                // missed-alarm sweep.
                if (!fromCheck) {
                    runCatching {
                        app.data.alarmLogs.append(
                            AlarmLog(
                                alarmId = alarm.id,
                                scheduledAt = scheduledAt,
                                firedAt = firedAt,
                                missionPluginId = alarm.missionPluginId,
                                missionPassed = missionPassed,
                                snoozeCount = loggedSnoozeCount,
                                outcome = outcome,
                            ),
                        )
                    }
                }
                // Register the next occurrence: a repeating alarm keeps coming back on its own,
                // a one-shot alarm switches itself off once it has rung.
                runCatching {
                    val latest = app.data.alarms.get(alarm.id) ?: return@runCatching
                    if (!latest.enabled) return@runCatching
                    if (latest.daysMask == 0) {
                        app.data.alarms.setEnabled(latest.id, false)
                    } else {
                        if (latest.skipNextAt != null) {
                            app.data.alarms.setSkipNextAt(latest.id, null)
                        }
                        app.scheduler.schedule(latest.copy(skipNextAt = null))
                        Log.i(TAG, "alarm ${latest.id} rescheduled after ${outcome.name}")
                    }
                }.onFailure { Log.w(TAG, "could not reschedule alarm ${alarm.id}", it) }

                if (outcome == AlarmOutcome.DISMISSED && !fromCheck) {
                    // Dismissing is the "I am up" claim that the wake-up check verifies. Runs after
                    // the reschedule above, which must not cancel the check it just placed.
                    runCatching { WakeUpCheck.arm(app, alarm, ringSnoozeCount) }
                        .onFailure { Log.w(TAG, "could not arm the wake-up check for ${alarm.id}", it) }
                }
            }
        }
        triggerAtMillis = 0L
        snoozeCount = 0
        reRingFromCheck = false
        timeoutJob?.cancel()
        timeoutJob = null
        silenceWatchdog?.cancel()
        silenceWatchdog = null
        // stop() also clears the mute flag: finishing an alarm must never leave it silenced, and a
        // queued ring that starts right after must be audible again.
        soundPlayer.stop()
        runCatching { vibrator?.cancel() }
        vibrator = null
        ringing = false
        ringingAlarmId = 0L
        currentAlarm = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        // A finished ring disables a one-shot or re-arms a repeating alarm: repaint the widget.
        AlarmWidgetUpdater.requestUpdate(this)
        val next = pendingRings.removeFirstOrNull()
        if (next != null) {
            Log.i(TAG, "starting queued alarm ${next.alarmId}")
            startRinging(next.alarmId, next.triggerAt, next.snoozeCount, next.wakeUpCheck)
        } else {
            stopSelf()
        }
    }

    private fun notifyRinging(alarm: Alarm) {
        val notification = buildNotification(alarm)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            manager?.canUseFullScreenIntent() == false
        ) {
            // Android 14+ lets the user withhold the full screen intent permission: the ring screen
            // then does not open over the keyguard on its own and the direct activity start in
            // launchRingActivity is the only way in. Worth a log line when a device misbehaves.
            Log.w(TAG, "full screen intent permission is not granted")
        }
        manager?.notify(AwakrNotifications.NOTIFICATION_RINGING, notification)
    }

    private fun buildNotification(alarm: Alarm): Notification {
        val dismiss = PendingIntent.getService(
            this,
            AlarmIntents.triggerRequestCode(alarm.id),
            Intent(this, AlarmRingService::class.java).apply {
                action = AlarmIntents.ACTION_DISMISS
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getService(
            this,
            AlarmIntents.snoozeRequestCode(alarm.id),
            Intent(this, AlarmRingService::class.java).apply {
                action = AlarmIntents.ACTION_SNOOZE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val time = formatTime(alarm.hour, alarm.minute)
        return NotificationCompat.Builder(this, AwakrNotifications.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(time)
            .setContentText(alarm.label.ifBlank { getString(R.string.ring_default_label) })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            // Never mark this notification silent. The system drops the full screen intent of a
            // silent notification, and the ring screen then never opens over the keyguard: the
            // alarm rings with the display off and there is no way to reach it but the shade.
            // Quietness comes from the channel, which has no sound, and from the service, which
            // plays the alarm through the audio manager.
            .setContentIntent(AlarmIntents.ringActivityPendingIntent(this, alarm.id))
            .setFullScreenIntent(AlarmIntents.ringActivityPendingIntent(this, alarm.id), true)
            .apply {
                // Once the snooze allowance for this occurrence is spent, only passing the
                // mission dismisses the alarm.
                if (snoozeCount < alarm.maxSnoozes) addAction(0, getString(R.string.ring_snooze), snooze)
                addAction(0, getString(R.string.ring_dismiss), dismiss)
            }
            .build()
    }

    private fun placeholderNotification(): Notification =
        NotificationCompat.Builder(this, AwakrNotifications.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(getString(R.string.ring_default_label))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Awakr::ringing")?.apply {
            runCatching { acquire(RING_TIMEOUT_MS + 60_000L) }
        }
    }

    override fun onDestroy() {
        // The system can tear the service down before finish() runs: never leave the sound, the
        // vibration or the "already ringing" flag behind.
        timeoutJob?.cancel()
        timeoutJob = null
        panicJob?.cancel()
        panicJob = null
        silenceWatchdog?.cancel()
        silenceWatchdog = null
        runCatching { speech?.shutdown() }
        speech = null
        runCatching { soundPlayer.stop() }
        runCatching { vibrator?.cancel() }
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        vibrator = null
        wakeLock = null
        ringing = false
        currentAlarm = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmRingService"
        /**
         * How long a ring may stay unanswered before it is recorded as missed.
         *
         * The mission has its own 90 second timer, and a user can fail it many times in a row, so
         * the ceiling has to be generous: a ring that is still being worked on must not turn into
         * a "missed" entry while the screen is up.
         */
        private const val RING_TIMEOUT_MS = 30 * 60_000L

        /** How long a ring stays polite before it jumps to full volume ("backup sound"). */
        private const val PANIC_AFTER_MS = 40_000L

        /** Utterance id for the spoken label; reused so a re-ring replaces the previous line. */
        private const val UTTERANCE_LABEL = "awakr-label"

        /** Longest time the mission timer may keep the ring silent before the sound comes back. */
        private const val MAX_SILENCE_MS = 3 * 60_000L
        private val VIBRATION_PATTERN = longArrayOf(0, 800, 600, 800, 600, 800)

        fun start(
            context: Context,
            alarmId: Long,
            triggerAt: Long = 0L,
            snoozeCount: Int = 0,
            wakeUpCheck: Boolean = false,
        ) {
            val intent = Intent(context, AlarmRingService::class.java).apply {
                action = AlarmIntents.ACTION_RING
                putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
                if (triggerAt > 0L) putExtra(AlarmIntents.EXTRA_TRIGGER_AT, triggerAt)
                if (snoozeCount > 0) putExtra(AlarmIntents.EXTRA_SNOOZE_COUNT, snoozeCount)
                if (wakeUpCheck) putExtra(AlarmIntents.EXTRA_WAKE_UP_CHECK, true)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Mutes or un-mutes the alarm that is ringing right now.
         *
         * The ring screen calls it while the mission timer counts down; [context] only needs to be
         * an application context. Calls without a ringing alarm are harmless.
         */
        fun setSilenced(context: Context, silenced: Boolean) {
            val intent = AlarmIntents.silencedIntent(context, silenced)
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "could not set silenced=$silenced", it) }
        }

        /** Re-posts the ringing notification so its full-screen intent can bring the ring back. */
        fun repostNotification(context: Context) {
            val intent = Intent(context, AlarmRingService::class.java).apply {
                action = AlarmIntents.ACTION_REPOST
            }
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "could not repost the ringing notification", it) }
        }

        fun sendAction(context: Context, action: String, missionPassed: Boolean = false) {
            val intent = Intent(context, AlarmRingService::class.java).apply {
                this.action = action
                putExtra(AlarmIntents.EXTRA_MISSION_PASSED, missionPassed)
            }
            runCatching { context.startService(intent) }
        }
    }
}
