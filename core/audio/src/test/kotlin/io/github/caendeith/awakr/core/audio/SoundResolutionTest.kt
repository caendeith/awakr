package io.github.caendeith.awakr.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundResolutionTest {

    private val shipped = setOf("builtin:chime", "builtin:marimba", "builtin:bell")

    private fun resolve(requested: String?, default: String?): String =
        SoundChoice.resolve(requested, default) { it in shipped }

    @Test
    fun requestedSoundWins() {
        assertEquals("builtin:bell", resolve("builtin:bell", "builtin:marimba"))
    }

    @Test
    fun emptyRequestMeansTheDefaultSound() {
        assertEquals("builtin:marimba", resolve(null, "builtin:marimba"))
        assertEquals("builtin:marimba", resolve("", "builtin:marimba"))
    }

    @Test
    fun missingFileFallsBackToTheDefaultSound() {
        assertEquals("builtin:bell", resolve("file:/storage/gone.mp3", "builtin:bell"))
    }

    @Test
    fun unknownValuesFallBackToTheShippedTone() {
        assertEquals("builtin:chime", resolve("file:/gone.mp3", "file:/also-gone.mp3"))
        assertEquals("builtin:chime", resolve(null, null))
        assertEquals("builtin:chime", resolve("nonsense", "builtin:nope"))
    }

    @Test
    fun theDefaultIsNotTriedTwice() {
        assertEquals("builtin:chime", resolve("file:/gone.mp3", "file:/gone.mp3"))
    }

    @Test
    fun emptyAlarmValueIsReportedAsInherited() {
        assertTrue(SoundChoice.isInherited(null))
        assertTrue(SoundChoice.isInherited("  "))
        assertFalse(SoundChoice.isInherited("builtin:chime"))
    }
}
