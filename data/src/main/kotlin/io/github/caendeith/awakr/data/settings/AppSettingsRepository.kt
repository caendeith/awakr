package io.github.caendeith.awakr.data.settings

import android.content.Context
import android.content.SharedPreferences
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

/**
 * Values of [AppSettings.language]: follow the system locale, or force one app language.
 * Anything else reads back as [LANGUAGE_SYSTEM].
 */
const val LANGUAGE_SYSTEM = "system"
const val LANGUAGE_ENGLISH = "en"
const val LANGUAGE_RUSSIAN = "ru"

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val swipeAction: SwipeAction = SwipeAction.DELETE,
    val onboarded: Boolean = false,
    val defaultSnoozeMinutes: Int = 10,
    val defaultMissionPluginId: String = "math",
    val defaultSoundId: String = "builtin:chime",
    val seeded: Boolean = false,
    val dynamicColor: Boolean = false,
    /** Accent colour name: AMBER, BLUE, GREEN or VIOLET; see AccentColor in core:ui. */
    val accentColor: String = "AMBER",
    /** Reads the alarm label aloud when the alarm starts ringing (offline text-to-speech). */
    val speakLabel: Boolean = false,
    val preAlertMinutes: Int = 30,
    /**
     * Minutes after a dismissal before the app asks "are you up?"; 0 switches the check off.
     * Only supported values are offered in Settings, see WakeUpCheckPolicy in the app module.
     */
    val wakeUpCheckMinutes: Int = 0,
    /** App language: [LANGUAGE_SYSTEM], [LANGUAGE_ENGLISH] or [LANGUAGE_RUSSIAN]. */
    val language: String = LANGUAGE_SYSTEM,
    /** Ring screen background: `builtin:<key>`, `file:<path>`, or empty for the plain look. */
    val ringBackgroundId: String = "",
    /** Extra darkening of the ring background so the clock stays readable, 0..60 percent. */
    val ringBackgroundDim: Int = 35,
)

class AppSettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsDataStore

    /**
     * Synchronous mirror of the language preference. The stored locale has to be known while
     * the first activity is created, which is before DataStore can answer.
     */
    private val languagePrefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(LANGUAGE_MIRROR_NAME, Context.MODE_PRIVATE)

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
            dynamicColor = prefs[KEY_DYNAMIC] ?: false,
            accentColor = prefs[KEY_ACCENT]?.uppercase() ?: "AMBER",
            speakLabel = prefs[KEY_SPEAK_LABEL] ?: false,
            preAlertMinutes = prefs[KEY_PRE_ALERT] ?: 30,
            wakeUpCheckMinutes = prefs[KEY_WAKE_UP_CHECK] ?: 0,
            language = normalizeLanguage(prefs[KEY_LANGUAGE]),
            ringBackgroundId = prefs[KEY_RING_BG] ?: "",
            ringBackgroundDim = (prefs[KEY_RING_BG_DIM] ?: 35).coerceIn(0, 60),
        )
    }

    suspend fun setSeeded(value: Boolean) = store.edit { it[KEY_SEEDED] = value }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[KEY_THEME] = mode.name }

    suspend fun setSwipeAction(action: SwipeAction) = store.edit { it[KEY_SWIPE] = action.name }

    suspend fun setOnboarded(value: Boolean) = store.edit { it[KEY_ONBOARDED] = value }

    suspend fun setDefaultSnoozeMinutes(minutes: Int) = store.edit { it[KEY_SNOOZE] = minutes }

    suspend fun setDefaultMission(pluginId: String) = store.edit { it[KEY_MISSION] = pluginId }

    suspend fun setDefaultSound(soundId: String) = store.edit { it[KEY_SOUND] = soundId }

    suspend fun setDynamicColor(value: Boolean) = store.edit { it[KEY_DYNAMIC] = value }

    suspend fun setAccentColor(value: String) = store.edit { it[KEY_ACCENT] = value.uppercase() }

    suspend fun setSpeakLabel(value: Boolean) = store.edit { it[KEY_SPEAK_LABEL] = value }

    suspend fun setPreAlertMinutes(minutes: Int) = store.edit { it[KEY_PRE_ALERT] = minutes }

    suspend fun setRingBackground(backgroundId: String) = store.edit { it[KEY_RING_BG] = backgroundId }

    suspend fun setRingBackgroundDim(percent: Int) = store.edit { it[KEY_RING_BG_DIM] = percent.coerceIn(0, 60) }

    suspend fun setWakeUpCheckMinutes(minutes: Int) = store.edit { it[KEY_WAKE_UP_CHECK] = minutes }

    /**
     * Blocking copy of [AppSettings.language], for the process start only.
     *
     * @return the stored language, or [LANGUAGE_SYSTEM] when nothing usable is stored yet.
     */
    fun languageNow(): String =
        normalizeLanguage(languagePrefs.getString(KEY_LANGUAGE_MIRROR, null))

    /** Stores [language], normalised to one of the supported tags. */
    suspend fun setLanguage(language: String) {
        val stored = normalizeLanguage(language)
        // Mirrored synchronously so that the next start reads the locale before DataStore loads.
        languagePrefs.edit().putString(KEY_LANGUAGE_MIRROR, stored).apply()
        store.edit { it[KEY_LANGUAGE] = stored }
    }

    private fun normalizeLanguage(value: String?): String =
        if (value == LANGUAGE_ENGLISH || value == LANGUAGE_RUSSIAN) value else LANGUAGE_SYSTEM

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_SWIPE = stringPreferencesKey("swipe_action")
        val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
        val KEY_SNOOZE = intPreferencesKey("default_snooze_minutes")
        val KEY_MISSION = stringPreferencesKey("default_mission_plugin_id")
        val KEY_SOUND = stringPreferencesKey("default_sound")
        val KEY_SEEDED = booleanPreferencesKey("sample_alarms_seeded")
        val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEY_ACCENT = stringPreferencesKey("accent_color")
        val KEY_SPEAK_LABEL = booleanPreferencesKey("speak_label")
        val KEY_PRE_ALERT = intPreferencesKey("pre_alert_minutes")
        val KEY_WAKE_UP_CHECK = intPreferencesKey("wake_up_check_minutes")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_RING_BG = stringPreferencesKey("ring_background")
        val KEY_RING_BG_DIM = intPreferencesKey("ring_background_dim")

        const val LANGUAGE_MIRROR_NAME = "awakr_language"
        const val KEY_LANGUAGE_MIRROR = "language"
    }
}
