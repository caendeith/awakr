package io.github.caendeith.awakr.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import io.github.caendeith.awakr.data.settings.AppSettingsRepository
import io.github.caendeith.awakr.data.settings.LANGUAGE_ENGLISH
import io.github.caendeith.awakr.data.settings.LANGUAGE_RUSSIAN
import io.github.caendeith.awakr.data.settings.LANGUAGE_SYSTEM

/**
 * Turns the stored language preference into per-app locales.
 *
 * AppCompat owns the mechanism on every API level: it delegates to
 * [android.app.LocaleManager] on API 33+ and otherwise wraps each activity context and recreates
 * the running activities, so the UI switches without a process restart.
 */
object AppLanguage {

    /** Preferences this build can render, in picker order. */
    val supported: List<String> = listOf(LANGUAGE_SYSTEM, LANGUAGE_ENGLISH, LANGUAGE_RUSSIAN)

    /**
     * Coerces an arbitrary stored value to one of [supported].
     *
     * @param preference raw value from [AppSettingsRepository]; a null or unknown value means
     *   [LANGUAGE_SYSTEM].
     */
    fun normalize(preference: String?): String =
        if (preference != null && preference in supported) preference else LANGUAGE_SYSTEM

    /**
     * BCP-47 tag list for [preference]. An empty string means "follow the system locale", which is
     * how [LocaleListCompat] spells the default.
     */
    fun languageTags(preference: String?): String = when (normalize(preference)) {
        LANGUAGE_ENGLISH -> "en"
        LANGUAGE_RUSSIAN -> "ru"
        else -> ""
    }

    /** [languageTags] as an AppCompat locale list. */
    fun locales(preference: String?): LocaleListCompat =
        LocaleListCompat.forLanguageTags(languageTags(preference))

    /** Applies [preference] app-wide; a no-op when it is already the active locale list. */
    fun apply(preference: String?) {
        val locales = locales(preference)
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    /** Applies the stored language; called from [android.app.Application.onCreate]. */
    fun applyStored(settings: AppSettingsRepository) = apply(settings.languageNow())
}
