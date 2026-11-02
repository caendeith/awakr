package io.github.caendeith.awakr.data.backup

import io.github.caendeith.awakr.data.settings.AppSettings
import io.github.caendeith.awakr.data.settings.SwipeAction
import io.github.caendeith.awakr.data.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupSettingsMergeTest {

    private val current = AppSettings(
        themeMode = ThemeMode.DARK,
        swipeAction = SwipeAction.TOGGLE,
        defaultSnoozeMinutes = 5,
        defaultMissionPluginId = "shake",
        defaultSoundId = "builtin:bell",
        dynamicColor = true,
        preAlertMinutes = 10,
        language = "ru",
    )

    @Test
    fun `older backup keeps every newer preference`() {
        val file = BackupSettings(
            themeMode = "LIGHT",
            swipeAction = "NONE",
            defaultSnoozeMinutes = 20,
            defaultMissionPluginId = "math",
            defaultSoundId = "builtin:chime",
        )

        val merged = current.mergedWith(file)

        assertEquals(ThemeMode.LIGHT, merged.themeMode)
        assertEquals(SwipeAction.NONE, merged.swipeAction)
        assertEquals(20, merged.defaultSnoozeMinutes)
        assertEquals("math", merged.defaultMissionPluginId)
        assertEquals("builtin:chime", merged.defaultSoundId)
        // Fields the file does not carry stay as they are on the device.
        assertEquals(true, merged.dynamicColor)
        assertEquals(10, merged.preAlertMinutes)
        assertEquals("ru", merged.language)
    }

    @Test
    fun `mentioned preferences are applied`() {
        val file = BackupSettings(dynamicColor = false, preAlertMinutes = 60, language = "en")

        val merged = current.mergedWith(file)

        assertEquals(false, merged.dynamicColor)
        assertEquals(60, merged.preAlertMinutes)
        assertEquals("en", merged.language)
    }

    @Test
    fun `invalid values are normalised instead of stored`() {
        val file = BackupSettings(
            themeMode = "PURPLE",
            swipeAction = "explode",
            preAlertMinutes = 5_000,
            language = "de",
        )

        val merged = current.mergedWith(file)

        assertEquals(ThemeMode.DARK, merged.themeMode)
        assertEquals(SwipeAction.TOGGLE, merged.swipeAction)
        assertEquals(180, merged.preAlertMinutes)
        assertEquals("system", merged.language)
    }
}
