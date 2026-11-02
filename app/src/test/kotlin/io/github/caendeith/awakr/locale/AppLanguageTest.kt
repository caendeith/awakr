package io.github.caendeith.awakr.locale

import io.github.caendeith.awakr.data.settings.LANGUAGE_ENGLISH
import io.github.caendeith.awakr.data.settings.LANGUAGE_RUSSIAN
import io.github.caendeith.awakr.data.settings.LANGUAGE_SYSTEM
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun supportedKeepsThePickerOrder() {
        assertEquals(
            listOf(LANGUAGE_SYSTEM, LANGUAGE_ENGLISH, LANGUAGE_RUSSIAN),
            AppLanguage.supported,
        )
    }

    @Test
    fun knownPreferencesPassThrough() {
        assertEquals(LANGUAGE_SYSTEM, AppLanguage.normalize(LANGUAGE_SYSTEM))
        assertEquals(LANGUAGE_ENGLISH, AppLanguage.normalize(LANGUAGE_ENGLISH))
        assertEquals(LANGUAGE_RUSSIAN, AppLanguage.normalize(LANGUAGE_RUSSIAN))
    }

    @Test
    fun unknownAndMissingPreferencesFallBackToTheSystem() {
        assertEquals(LANGUAGE_SYSTEM, AppLanguage.normalize(null))
        assertEquals(LANGUAGE_SYSTEM, AppLanguage.normalize(""))
        assertEquals(LANGUAGE_SYSTEM, AppLanguage.normalize("de"))
        assertEquals(LANGUAGE_SYSTEM, AppLanguage.normalize("EN"))
    }

    @Test
    fun englishAndRussianMapToTheirTag() {
        assertEquals("en", AppLanguage.languageTags(LANGUAGE_ENGLISH))
        assertEquals("ru", AppLanguage.languageTags(LANGUAGE_RUSSIAN))
    }

    @Test
    fun systemDefaultIsAnEmptyTagList() {
        assertEquals("", AppLanguage.languageTags(LANGUAGE_SYSTEM))
        assertEquals("", AppLanguage.languageTags("de"))
        assertEquals("", AppLanguage.languageTags(null))
    }
}
