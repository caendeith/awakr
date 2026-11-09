package io.github.caendeith.awakr.ui.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundResolverTest {
    private val usableAll: (BackgroundRef) -> Boolean = { true }
    private val usableBuiltinsOnly: (BackgroundRef) -> Boolean = { it is BackgroundRef.Builtin }

    @Test
    fun alarmWinsOverSettings() {
        assertEquals(
            "builtin:starry",
            BackgroundResolver.resolve("builtin:starry", "builtin:ink", usableAll),
        )
    }

    @Test
    fun settingsUsedWhenAlarmHasNoBackground() {
        assertEquals("builtin:ink", BackgroundResolver.resolve("", "builtin:ink", usableAll))
        assertEquals("builtin:ink", BackgroundResolver.resolve(null, "builtin:ink", usableAll))
    }

    @Test
    fun brokenAlarmPhotoFallsBackToSettings() {
        // A deleted photo must not leave the ring screen blank.
        assertEquals(
            "builtin:ink",
            BackgroundResolver.resolve("file:/gone.jpg", "builtin:ink", usableBuiltinsOnly),
        )
    }

    @Test
    fun unusableAlarmWithoutSettingsGivesPlainScreen() {
        assertNull(BackgroundResolver.resolve("file:/gone.jpg", "", usableBuiltinsOnly))
    }

    @Test
    fun nothingConfiguredGivesPlainScreen() {
        assertNull(BackgroundResolver.resolve(null, null, usableAll))
        assertNull(BackgroundResolver.resolve("", "", usableAll))
    }

    @Test
    fun unknownPrefixIsIgnored() {
        assertNull(BackgroundResolver.resolve("http://x/y.jpg", "", usableAll))
    }

    @Test
    fun resultIsCanonical() {
        assertEquals("builtin:ink", BackgroundResolver.resolve(" builtin:ink ", "", usableAll))
    }
}
