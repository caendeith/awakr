package io.github.caendeith.awakr.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.caendeith.awakr.AwakrApplication
import io.github.caendeith.awakr.alarm.WakeUpCheck
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.core.audio.SoundResolver
import io.github.caendeith.awakr.core.time.NextOccurrence
import io.github.caendeith.awakr.data.AwakrData
import io.github.caendeith.awakr.data.settings.AppSettings
import io.github.caendeith.awakr.locale.AppLanguage
import io.github.caendeith.awakr.plugin.runtime.PluginRegistry
import io.github.caendeith.awakr.ui.about.AboutScreen
import io.github.caendeith.awakr.ui.editor.AlarmEditorScreen
import io.github.caendeith.awakr.ui.home.HomeScreen
import io.github.caendeith.awakr.ui.onboarding.OnboardingScreen
import io.github.caendeith.awakr.ui.plugins.PluginsScreen
import io.github.caendeith.awakr.ui.settings.BackupScreen
import io.github.caendeith.awakr.ui.settings.SettingsScreen
import io.github.caendeith.awakr.ui.stats.StatsScreen
import io.github.caendeith.awakr.ui.widget.AlarmWidgetUpdater
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Enough history for the 30-day statistics screen without loading the whole table. */
private const val STATS_LOG_LIMIT = 2_000

@Composable
fun AwakrNavHost(
    data: AwakrData,
    plugins: PluginRegistry,
    settings: AppSettings,
    startDestination: String = Routes.HOME,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val app = LocalContext.current.applicationContext as AwakrApplication
    val resources = LocalResources.current

    val pluginStates by data.pluginStates.observeAll().collectAsState(initial = emptyList())
    val enabledPluginIds: Set<String> = plugins.missions
        .filter { plugin ->
            pluginStates.firstOrNull { it.pluginId == plugin.manifest.id }?.enabled ?: true
        }
        .map { it.manifest.id }
        .toSet()

    val missionLabel: (String) -> String = { pluginId ->
        val manifest = plugins.mission(pluginId)?.manifest
        when {
            manifest == null -> pluginId
            manifest.name.resId != 0 -> resources.getString(manifest.name.resId)
            else -> manifest.name.fallback.ifBlank { pluginId }
        }
    }

    val cameraMissionEnabled = pluginStates
        .firstOrNull { it.pluginId == "mission-code" || it.pluginId == "mission-qr" }
        ?.enabled ?: true

    val missed by data.alarmLogs.observeMissed(limit = 5).collectAsState(initial = emptyList())
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    // Only the last day matters for a "you missed it" nudge; older rows stay in history.
    val missedLabels = remember(missed) {
        val zone = ZoneId.systemDefault()
        val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        missed
            .filter { it.scheduledAt >= cutoff }
            .map { Instant.ofEpochMilli(it.scheduledAt).atZone(zone).format(timeFormatter) }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                cameraMissionEnabled = cameraMissionEnabled,
                onDone = {
                    scope.launch { data.settings.setOnboarded(true) }
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                },
                onBack = if (startDestination == Routes.ONBOARDING) {
                    null
                } else {
                    { navController.popBackStack() }
                },
            )
        }
        composable(Routes.HOME) {
            val alarms by data.alarms.observeAll().collectAsState(initial = emptyList<Alarm>())
            // The widget has no periodic update: repaint it whenever the alarm list changes.
            LaunchedEffect(alarms) { AlarmWidgetUpdater.update(app) }
            HomeScreen(
                alarms = alarms,
                missionLabel = missionLabel,
                swipeAction = settings.swipeAction,
                onToggle = { alarm, enabled ->
                    scope.launch {
                        data.alarms.setEnabled(alarm.id, enabled)
                        if (enabled) {
                            val fresh = data.alarms.get(alarm.id) ?: alarm
                            app.scheduler.schedule(fresh.copy(enabled = true))
                        } else {
                            app.scheduler.cancel(alarm.id)
                        }
                    }
                },
                onAddAlarm = { navController.navigate(Routes.editor(0L)) },
                onEditAlarm = { id -> navController.navigate(Routes.editor(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenStats = { navController.navigate(Routes.STATS) },
                missedLabels = missedLabels,
                onDelete = { ids ->
                    scope.launch {
                        ids.forEach { app.scheduler.cancel(it) }
                        data.alarms.delete(ids)
                    }
                },
                onRestore = { ids ->
                    scope.launch {
                        data.alarms.restore(ids)
                        ids.forEach { id ->
                            data.alarms.get(id)?.let { if (it.enabled) app.scheduler.schedule(it) }
                        }
                    }
                },
                onDuplicate = { alarm ->
                    scope.launch {
                        val newId = data.alarms.duplicate(alarm)
                        data.alarms.get(newId)?.let { app.scheduler.schedule(it) }
                    }
                },
                onSkipNext = { alarm ->
                    scope.launch {
                        val next = NextOccurrence.next(
                            ZonedDateTime.now(),
                            alarm.hour,
                            alarm.minute,
                            alarm.daysMask,
                        )
                        data.alarms.setSkipNextAt(alarm.id, next?.toInstant()?.toEpochMilli())
                        data.alarms.get(alarm.id)?.let { app.scheduler.schedule(it) }
                    }
                },
                onClearSkipNext = { alarm ->
                    scope.launch {
                        data.alarms.setSkipNextAt(alarm.id, null)
                        data.alarms.get(alarm.id)?.let { app.scheduler.schedule(it) }
                    }
                },
                onSetEnabledBulk = { ids, enabled ->
                    scope.launch {
                        ids.forEach { id ->
                            data.alarms.setEnabled(id, enabled)
                            if (enabled) {
                                data.alarms.get(id)?.let { app.scheduler.schedule(it) }
                            } else {
                                app.scheduler.cancel(id)
                            }
                        }
                    }
                },
                onReorder = { orderedIds -> scope.launch { data.alarms.reorder(orderedIds) } },
            )
        }
        composable(Routes.STATS) {
            val logFlow = remember { data.alarmLogs.observeRecent(limit = STATS_LOG_LIMIT) }
            val logEntries by logFlow.collectAsState(initial = emptyList())
            StatsScreen(
                logs = logEntries,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument(Routes.EDITOR_ARG_ID) {
                type = NavType.LongType
                defaultValue = 0L
            }),
        ) { entry ->
            val alarmId = entry.arguments?.getLong(Routes.EDITOR_ARG_ID) ?: 0L
            AlarmEditorScreen(
                alarmId = alarmId,
                alarms = data.alarms,
                plugins = plugins,
                enabledPluginIds = enabledPluginIds,
                missionLabel = missionLabel,
                defaultSoundId = settings.defaultSoundId,
                defaultSnoozeMinutes = settings.defaultSnoozeMinutes,
                defaultMissionPluginId = settings.defaultMissionPluginId,
                onBack = { navController.popBackStack() },
                onSave = { alarm ->
                    scope.launch {
                        val savedId = data.alarms.save(alarm)
                        val id = if (alarm.id == 0L) savedId else alarm.id
                        data.alarms.get(id)?.let { app.scheduler.schedule(it) }
                        // The alarm was re-scheduled: a pending "are you up?" check belongs to the
                        // old schedule and must not ring for the edited one.
                        WakeUpCheck.cancel(app, id)
                        navController.popBackStack()
                    }
                },
                onDelete = { id ->
                    scope.launch {
                        app.scheduler.cancel(id)
                        data.alarms.delete(id)
                        navController.popBackStack()
                    }
                },
                onDuplicate = { alarm ->
                    scope.launch {
                        val newId = data.alarms.duplicate(alarm)
                        data.alarms.get(newId)?.let { app.scheduler.schedule(it) }
                        navController.popBackStack()
                    }
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                defaultSoundName = SoundResolver.name(
                    LocalContext.current.applicationContext,
                    settings.defaultSoundId,
                ) ?: "",
                onBack = { navController.popBackStack() },
                onOpenPermissions = { navController.navigate(Routes.ONBOARDING) },
                onOpenPlugins = { navController.navigate(Routes.PLUGINS) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onOpenBackup = { navController.navigate(Routes.BACKUP) },
                onThemeChange = { mode -> scope.launch { data.settings.setThemeMode(mode) } },
                onSwipeChange = { action -> scope.launch { data.settings.setSwipeAction(action) } },
                onDefaultSoundChange = { soundId ->
                    scope.launch { data.settings.setDefaultSound(soundId) }
                },
                onSnoozeChange = { minutes -> scope.launch { data.settings.setDefaultSnoozeMinutes(minutes) } },
                onDynamicColorChange = { value -> scope.launch { data.settings.setDynamicColor(value) } },
                onPreAlertChange = { minutes ->
                    scope.launch {
                        data.settings.setPreAlertMinutes(minutes)
                        // Each alarm carries its own pre-alert trigger, so the new lead time only
                        // takes effect for alarms that are re-armed now.
                        app.scheduler.rescheduleAll(data.alarms.enabledAlarms())
                    }
                },
                onWakeUpCheckChange = { minutes ->
                    // Read at dismissal time, so no alarm has to be re-armed for this.
                    scope.launch { data.settings.setWakeUpCheckMinutes(minutes) }
                },
                onLanguageChange = { language ->
                    scope.launch {
                        // Store first: AppCompat recreates the screen for the new locale right away.
                        data.settings.setLanguage(language)
                        AppLanguage.apply(language)
                    }
                },
                onAccentChange = { accent ->
                    scope.launch { data.settings.setAccentColor(accent) }
                },
                onSpeakLabelChange = { speak ->
                    // Read at ring time, so no alarm has to be re-armed for this.
                    scope.launch { data.settings.setSpeakLabel(speak) }
                },
            )
        }
        composable(Routes.PLUGINS) {
            PluginsScreen(
                plugins = plugins,
                pluginStates = data.pluginStates,
                missionLabel = missionLabel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.BACKUP) {
            BackupScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() },
                missionCount = plugins.missions.size,
            )
        }
    }
}
