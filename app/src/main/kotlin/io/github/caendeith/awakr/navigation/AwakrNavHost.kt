package io.github.caendeith.awakr.navigation

import androidx.compose.runtime.Composable
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
import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.data.AwakrData
import io.github.caendeith.awakr.plugin.runtime.PluginRegistry
import io.github.caendeith.awakr.ui.about.AboutScreen
import io.github.caendeith.awakr.ui.editor.AlarmEditorScreen
import io.github.caendeith.awakr.ui.home.HomeScreen
import io.github.caendeith.awakr.ui.plugins.PluginsScreen
import io.github.caendeith.awakr.ui.onboarding.OnboardingScreen
import io.github.caendeith.awakr.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AwakrNavHost(
    data: AwakrData,
    plugins: PluginRegistry,
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

    val qrEnabled = pluginStates.firstOrNull { it.pluginId == "mission-qr" }?.enabled ?: true
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
                cameraMissionEnabled = qrEnabled,
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
            HomeScreen(
                alarms = alarms,
                missionLabel = missionLabel,
                onToggle = { alarm, enabled ->
                    scope.launch {
                        data.alarms.setEnabled(alarm.id, enabled)
                        if (enabled) app.scheduler.schedule(alarm.copy(enabled = true))
                        else app.scheduler.cancel(alarm.id)
                    }
                },
                onAddAlarm = { navController.navigate(Routes.editor(0L)) },
                onEditAlarm = { id -> navController.navigate(Routes.editor(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                missedLabels = missedLabels,
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
                onBack = { navController.popBackStack() },
                onSave = { alarm ->
                    scope.launch {
                        val savedId = data.alarms.save(alarm)
                        app.scheduler.schedule(alarm.copy(id = if (alarm.id == 0L) savedId else alarm.id))
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
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPermissions = { navController.navigate(Routes.ONBOARDING) },
                onOpenPlugins = { navController.navigate(Routes.PLUGINS) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
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
        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() },
                missionCount = plugins.missions.size,
            )
        }
    }
}
