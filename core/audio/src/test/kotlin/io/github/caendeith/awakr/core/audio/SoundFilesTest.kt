package io.github.caendeith.awakr.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SoundFilesTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun theIdRoundTripsToTheSameFile() {
        val file = temp.newFile("clip.m4a")
        val id = SoundFiles.idOf(file)
        assertTrue(id.startsWith("file:"))
        assertEquals(file.absolutePath, SoundFiles.fileOf(id)?.absolutePath)
    }

    @Test
    fun otherFormatsHaveNoFile() {
        assertNull(SoundFiles.fileOf(null))
        assertNull(SoundFiles.fileOf(""))
        assertNull(SoundFiles.fileOf("builtin:chime"))
        assertNull(SoundFiles.fileOf("file:"))
        assertNull(SoundFiles.fileOf("file:   "))
    }

    @Test
    fun supportedNamesAreMatchedCaseInsensitively() {
        assertTrue(SoundFiles.isSupportedName("clip.m4a"))
        assertTrue(SoundFiles.isSupportedName("CLIP.M4A"))
        assertTrue(SoundFiles.isSupportedName("song.mp3"))
        assertTrue(SoundFiles.isSupportedName("voice.ogg"))
        assertFalse(SoundFiles.isSupportedName("notes.txt"))
        assertFalse(SoundFiles.isSupportedName("noextension"))
    }

    @Test
    fun listingIsNewestFirstAndSkipsJunk() {
        val dir = temp.newFolder("sounds")
        val older = File(dir, "older.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val newer = File(dir, "newer.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        File(dir, "notes.txt").writeText("not a sound")
        File(dir, "empty.wav").createNewFile()
        older.setLastModified(1_000_000L)
        newer.setLastModified(2_000_000L)

        val listed = SoundFiles.listIn(dir)
        assertEquals(listOf("newer.m4a", "older.mp3"), listed.map { it.file.name })
        assertEquals("newer", listed.first().displayName)
        assertNull(listed.first().timeLabel)
        assertFalse(listed.first().isRecording)
    }

    @Test
    fun missingFolderListsNothing() {
        assertEquals(emptyList<RecordedSound>(), SoundFiles.listIn(File(temp.root, "absent")))
    }

    @Test
    fun recordedNamesCarryTheTime() {
        val dir = temp.newFolder("sounds")
        val file = File(dir, "rec_20261102_104851_a1b2c3d4.m4a").apply { writeBytes(byteArrayOf(9)) }
        val sound = SoundFiles.listIn(dir).single()
        assertEquals("10:48", sound.timeLabel)
        assertTrue(sound.isRecording)
        assertEquals("rec_20261102_104851_a1b2c3d4", sound.displayName)
        assertTrue(sound.soundId.endsWith(file.name))
    }

    @Test
    fun onlyDirectChildrenCountAsInside() {
        val dir = temp.newFolder("sounds")
        val childDir = File(dir, "nested").apply { mkdirs() }
        val nested = File(childDir, "x.m4a").apply { writeBytes(byteArrayOf(1)) }
        val same = File(dir, "x.m4a").apply { writeBytes(byteArrayOf(1)) }
        val elsewhere = temp.newFile("outside.m4a")
        assertTrue(SoundFiles.isInside(dir, same))
        assertTrue(SoundFiles.isInside(dir, same.canonicalFile))
        assertFalse(SoundFiles.isInside(dir, nested))
        assertFalse(SoundFiles.isInside(dir, elsewhere))
    }
}
