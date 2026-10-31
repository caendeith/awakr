package io.github.caendeith.awakr.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "awakr_settings")

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** What a horizontal swipe on an alarm card does. */
enum class SwipeAction { DELETE, TOGGLE, NONE }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val swipeAction: SwipeAction = SwipeAction.DELETE,
    val onboarded: Boolean = false,
    val defaultSnoozeMinutes: Int = 10,
    val defaultMissionPluginId: String = "math",
    val defaultSoundId: String = "builtin:chime",
    val seeded: Boolean = false,
)

class AppSettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            themeMode = prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            swipeAction = prefs[KEY_SWIPE]?.let { runCatching { SwipeAction.valueOf(it) }.getOrNull() }
                ?: SwipeAction.DELETE,
            onboarded = prefs[KEY_ONBOARDED] ?: false,
            defaultSnoozeMinutes = prefs[KEY_SNOOZE] ?: 10,
            defaultMissionPluginId = prefs[KEY_MISSION] ?: "math",
            defaultSoundId = prefs[KEY_SOUND] ?: "builtin:chime",
            seeded = prefs[KEY_SEEDED] ?: false,
        )
    }

    suspend fun setSeeded(value: Boolean) = store.edit { it[KEY_SEEDED] = value }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[KEY_THEME] = mode.name }

    suspend fun setSwipeAction(action: SwipeAction) = store.edit { it[KEY_SWIPE] = action.name }

    suspend fun setOnboarded(value: Boolean) = store.edit { it[KEY_ONBOARDED] = value }

    suspend fun setDefaultSnoozeMinutes(minutes: Int) = store.edit { it[KEY_SNOOZE] = minutes }

    suspend fun setDefaultMission(pluginId: String) = store.edit { it[KEY_MISSION] = pluginId }

    suspend fun setDefaultSound(soundId: String) = store.edit { it[KEY_SOUND] = soundId }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_SWIPE = stringPreferencesKey("swipe_action")
        val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
        val KEY_SNOOZE = intPreferencesKey("default_snooze_minutes")
        val KEY_MISSION = stringPreferencesKey("default_mission_plugin_id")
        val KEY_SOUND = stringPreferencesKey("default_sound")
        val KEY_SEEDED = booleanPreferencesKey("sample_alarms_seeded")
    }
}
