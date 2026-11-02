package io.github.caendeith.awakr.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinSoundsTest {

    @Test
    fun everyShippedToneHasAPack() {
        assertEquals(7, BuiltinSounds.all.size)
        assertEquals(BuiltinSounds.all.size, BuiltinSounds.all.map { it.id }.toSet().size)
        SoundPack.entries.forEach { pack ->
            assertTrue("pack " + pack + " is empty", BuiltinSounds.inPack(pack).isNotEmpty())
        }
    }

    @Test
    fun gentleAndLoudKeepTheirOwnTones() {
        assertEquals(
            listOf("chime", "marimba", "sunrise", "bell"),
            BuiltinSounds.inPack(SoundPack.GENTLE).map { it.id },
        )
        assertEquals(
            listOf("digital", "alarm", "pulse"),
            BuiltinSounds.inPack(SoundPack.LOUD).map { it.id },
        )
    }

    @Test
    fun everyPack() {
        val seen = SoundPack.entries.flatMap { pack -> BuiltinSounds.inPack(pack) }
        assertEquals(BuiltinSounds.all, seen)
    }

    @Test
    fun packOfATone() {
        assertEquals(SoundPack.LOUD, BuiltinSounds.byId("pulse")?.pack)
        assertEquals(SoundPack.GENTLE, BuiltinSounds.byId("chime")?.pack)
        assertEquals(null, BuiltinSounds.byId("nope"))
    }

    @Test
    fun idRoundTrip() {
        val sound = BuiltinSounds.byId("bell")!!
        val id = BuiltinSounds.idOf(sound)
        assertEquals("builtin:bell", id)
        assertTrue(BuiltinSounds.isBuiltin(id))
        assertEquals("bell", BuiltinSounds.idWithoutPrefix(id))
        assertFalse(BuiltinSounds.isBuiltin("file:/tmp/x.m4a"))
        assertFalse(BuiltinSounds.isBuiltin(null))
    }

    @Test
    fun theDefaultToneIsShipped() {
        val fallback = BuiltinSounds.default()
        assertNotNull(BuiltinSounds.byId(fallback.id))
        assertTrue(BuiltinSounds.isBuiltin(BuiltinSounds.idOf(fallback)))
    }
}
