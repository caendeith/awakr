package io.github.caendeith.awakr.ui.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset

class BackgroundFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun acceptsOnlyImageNames() {
        assertTrue(BackgroundFiles.isSupportedName("bg_1.jpg"))
        assertTrue(BackgroundFiles.isSupportedName("PHOTO.JPG"))
        assertTrue(BackgroundFiles.isSupportedName("a.png"))
        assertTrue(BackgroundFiles.isSupportedName("a.webp"))
        assertFalse(BackgroundFiles.isSupportedName("notes.txt"))
        assertFalse(BackgroundFiles.isSupportedName("bg_1"))
        assertFalse(BackgroundFiles.isSupportedName(".jpg"))
        assertFalse(BackgroundFiles.isSupportedName("a."))
    }

    @Test
    fun namesFilesByTimestamp() {
        val dir = folder.newFolder("backgrounds")
        val at = LocalDateTime.of(2026, 1, 1, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val file = BackgroundFiles.newFile(dir, at, "abcd1234", ZoneOffset.UTC)
        assertEquals("bg_20260101_120000_abcd1234.jpg", file.name)
        assertEquals(dir, file.parentFile)
    }

    @Test
    fun listsNewestFirstAndSkipsJunk() {
        val dir = folder.newFolder("backgrounds")
        val older = write(dir, "bg_old.jpg", at(2026, 1, 1, 10, 0))
        val newer = write(dir, "bg_new.jpg", at(2026, 1, 1, 11, 0))
        // Empty file: an interrupted import must not show as a broken tile.
        File(dir, "bg_empty.jpg").createNewFile()
        File(dir, "notes.txt").writeText("not an image")
        dir.mkdirs()

        val listed = BackgroundFiles.list(dir)
        assertEquals(listOf(newer.absolutePath, older.absolutePath), listed.map { it.absolutePath })
    }

    @Test
    fun listIsEmptyForMissingDirectory() {
        assertTrue(BackgroundFiles.list(File(folder.root, "nope")).isEmpty())
    }

    @Test
    fun idRoundTripsToFile() {
        val dir = folder.newFolder("backgrounds")
        val file = write(dir, "bg_1.jpg", at(2026, 1, 1, 12, 0))
        val id = BackgroundFiles.idOf(file)
        assertEquals("file:" + file.absolutePath, id)
        assertEquals(file.absolutePath, BackgroundFiles.fileOf(id)?.absolutePath)
        assertNull(BackgroundFiles.fileOf("builtin:ink"))
    }

    @Test
    fun timeLabelUsesFileTimestamp() {
        val dir = folder.newFolder("backgrounds")
        val file = write(dir, "bg_1.jpg", at(2026, 1, 1, 7, 4))
        assertEquals("07:04", BackgroundFiles.timeLabel(file, ZoneOffset.UTC))
    }

    @Test
    fun ownershipCheckKeepsDeletesInsideTheFolder() {
        val dir = folder.newFolder("backgrounds")
        val inside = write(dir, "bg_1.jpg", at(2026, 1, 1, 12, 0))
        val outside = folder.newFile("outside.jpg")
        assertTrue(BackgroundFiles.isInside(dir, inside))
        assertFalse(BackgroundFiles.isInside(dir, outside))
    }

    private fun write(dir: File, name: String, lastModified: Long): File {
        val file = File(dir, name)
        file.writeBytes(ByteArray(8))
        file.setLastModified(lastModified)
        return file
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()
}
