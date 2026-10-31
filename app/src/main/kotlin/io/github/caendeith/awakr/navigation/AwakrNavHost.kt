package io.github.caendeith.awakr.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.caendeith.awakr.ui.about.AboutScreen
import io.github.caendeith.awakr.ui.editor.AlarmEditorScreen
import io.github.caendeith.awakr.ui.home.HomeScreen
import io.github.caendeith.awakr.ui.plugins.PluginsScreen
import io.github.caendeith.awakr.ui.ring.RingScreen
import io.github.caendeith.awakr.ui.settings.SettingsScreen

@Composable
fun AwakrNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onAddAlarm = { navController.navigate(Routes.editor()) },
                onEditAlarm = { id -> navController.navigate(Routes.editor(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument(Routes.EDITOR_ARG_ID) { type = NavType.LongType }),
        ) { entry ->
            AlarmEditorScreen(
                alarmId = entry.arguments?.getLong(Routes.EDITOR_ARG_ID) ?: 0L,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.RING,
            arguments = listOf(navArgument(Routes.RING_ARG_ID) { type = NavType.LongType }),
        ) { entry ->
            RingScreen(
                alarmId = entry.arguments?.getLong(Routes.RING_ARG_ID) ?: 0L,
                onFinished = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPlugins = { navController.navigate(Routes.PLUGINS) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.PLUGINS) {
            PluginsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
