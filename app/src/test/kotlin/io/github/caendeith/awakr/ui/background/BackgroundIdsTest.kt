package io.github.caendeith.awakr.ui.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundIdsTest {
    @Test
    fun parsesBuiltinId() {
        assertEquals(BackgroundRef.Builtin("ink"), BackgroundIds.parse("builtin:ink"))
        assertEquals("ink", BackgroundIds.builtinKey("builtin:ink"))
    }

    @Test
    fun parsesFileId() {
        val path = "/data/user/0/app/files/backgrounds/bg_1.jpg"
        assertEquals(BackgroundRef.Local(path), BackgroundIds.parse("file:" + path))
        assertEquals(path, BackgroundIds.filePath("file:" + path))
    }

    @Test
    fun rejectsBlankAndUnknownIds() {
        assertNull(BackgroundIds.parse(null))
        assertNull(BackgroundIds.parse(""))
        assertNull(BackgroundIds.parse("   "))
        assertNull(BackgroundIds.parse("builtin:"))
        assertNull(BackgroundIds.parse("file:"))
        assertNull(BackgroundIds.parse("http://example.com/a.jpg"))
        assertNull(BackgroundIds.parse("ink"))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals(BackgroundRef.Builtin("ink"), BackgroundIds.parse("  builtin:ink  "))
        assertEquals("builtin:ink", BackgroundIds.idOf(BackgroundRef.Builtin("ink")))
    }

    @Test
    fun idRoundTripsThroughParse() {
        val refs = listOf(
            BackgroundRef.Builtin("starry"),
            BackgroundRef.Local("/tmp/bg.jpg"),
        )
        for (ref in refs) {
            val id = BackgroundIds.idOf(ref)
            assertEquals(ref, BackgroundIds.parse(id))
            assertEquals(id, BackgroundIds.idOf(BackgroundIds.parse(id)!!))
        }
    }

    @Test
    fun keyHelpersAreNullForOtherPrefixes() {
        assertNull(BackgroundIds.builtinKey("file:/tmp/a.jpg"))
        assertNull(BackgroundIds.filePath("builtin:ink"))
        assertNull(BackgroundIds.builtinKey(null))
    }
}
