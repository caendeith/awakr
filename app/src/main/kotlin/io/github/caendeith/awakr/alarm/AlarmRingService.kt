package io.github.caendeith.awakr.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.caendeith.awakr.AwakrApplication
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.alarm.AlarmLog
import io.github.caendeith.awakr.core.alarm.AlarmOutcome
import io.github.caendeith.awakr.core.audio.AlarmSoundPlayer
import io.github.caendeith.awakr.core.notify.AwakrNotifications
import io.github.caendeith.awakr.ui.format.formatTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    private var ringing = false
    private var triggerAtMillis = 0L

    /** How many times the current occurrence has already been snoozed. */
    private var snoozeCount = 0

    /** Id of the alarm that is ringing right now; 0 when nothing rings. */
    private var ringingAlarmId = 0L

    /**
     * Alarms that fired while another one was already ringing. Two alarms can share a minute, and
     * a ring cannot be interrupted, so the second one waits here and starts as soon as the first is
     * dismissed or snoozed.
     */
    private val pendingRings = ArrayDeque<PendingRing>()

    private data class PendingRing(val alarmId: Long, val triggerAt: Long, val snoozeCount: Int)

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
                finish(
                    outcome = AlarmOutcome.DISMISSED,
                    missionPassed = intent.getBooleanExtra(AlarmIntents.EXTRA_MISSION_PASSED, false),
                )
            }
            AlarmIntents.ACTION_SNOOZE -> {
                snooze()
            }
            AlarmIntents.ACTION_RING, null -> {
                val alarmId = intent?.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, 0L) ?: 0L
                if (alarmId != 0L) {
                    startRinging(
                        alarmId = alarmId,
                        triggerAt = intent?.getLongExtra(AlarmIntents.EXTRA_TRIGGER_AT, 0L) ?: 0L,
                        snoozeCount = intent?.getIntExtra(AlarmIntents.EXTRA_SNOOZE_COUNT, 0) ?: 0,
                    )
                }
            }
        }
        return START_STICKY
    }

    private fun startRinging(alarmId: Long, triggerAt: Long = 0L, snoozeCount: Int = 0) {
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
            pendingRings.addLast(PendingRing(alarmId, triggerAt, snoozeCount))
            Log.i(TAG, "queued alarm $alarmId until the current ring is finished")
            return
        }
        ringing = true
        ringingAlarmId = alarmId
        this.triggerAtMillis = triggerAt
        this.snoozeCount = snoozeCount
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
            startSound(alarm)
            startVibration(alarm)
            notifyRinging(alarm)
            launchRingActivity(alarm)
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
    private fun launchRingActivity(alarm: Alarm) {
        runCatching { startActivity(AlarmIntents.ringActivityIntent(this, alarm.id, snoozeCount)) }
            .onFailure { Log.w(TAG, "could not launch ring activity", it) }
    }

    private fun startSound(alarm: Alarm) {
        val uri = alarm.soundUri?.let(Uri::parse) ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        soundPlayer.play(
            uri = uri,
            rampSeconds = alarm.rampSeconds,
            startVolumePercent = alarm.volumeStart,
            scope = scope,
        )
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
        val alarm = currentAlarm
        if (alarm != null) {
            val app = application as AwakrApplication
            val firedAt = System.currentTimeMillis()
            val scheduledAt = if (triggerAtMillis > 0L) triggerAtMillis else firedAt
            // Read the counter before the fields below are cleared; the log write is asynchronous.
            val loggedSnoozeCount = if (outcome == AlarmOutcome.SNOOZED) snoozeCount + 1 else 0
            app.appScope.launch {
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
        }
        triggerAtMillis = 0L
        snoozeCount = 0
        timeoutJob?.cancel()
        timeoutJob = null
        soundPlayer.stop()
        runCatching { vibrator?.cancel() }
        vibrator = null
        ringing = false
        ringingAlarmId = 0L
        currentAlarm = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        val next = pendingRings.removeFirstOrNull()
        if (next != null) {
            Log.i(TAG, "starting queued alarm ${next.alarmId}")
            startRinging(next.alarmId, next.triggerAt, next.snoozeCount)
        } else {
            stopSelf()
        }
    }

    private fun notifyRinging(alarm: Alarm) {
        val notification = buildNotification(alarm)
        val manager = getSystemService(android.app.NotificationManager::class.java)
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
            .setSilent(true)
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
        private const val RING_TIMEOUT_MS = 10 * 60_000L
        private val VIBRATION_PATTERN = longArrayOf(0, 800, 600, 800, 600, 800)

        fun start(context: Context, alarmId: Long, triggerAt: Long = 0L, snoozeCount: Int = 0) {
            val intent = Intent(context, AlarmRingService::class.java).apply {
                action = AlarmIntents.ACTION_RING
                putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
                if (triggerAt > 0L) putExtra(AlarmIntents.EXTRA_TRIGGER_AT, triggerAt)
                if (snoozeCount > 0) putExtra(AlarmIntents.EXTRA_SNOOZE_COUNT, snoozeCount)
            }
            ContextCompat.startForegroundService(context, intent)
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
