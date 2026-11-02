package io.github.caendeith.awakr.ui.sound

import io.github.caendeith.awakr.core.audio.BuiltinSounds
import io.github.caendeith.awakr.core.audio.RecordedSound
import io.github.caendeith.awakr.core.audio.SoundPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SoundPickerModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun recorded(name: String, modifiedAt: Long): RecordedSound {
        val dir = temp.newFolder("sounds-" + modifiedAt)
        val file = File(dir, name).apply { writeBytes(byteArrayOf(1)) }
        file.setLastModified(modifiedAt)
        return RecordedSound(file)
    }

    @Test
    fun chipsListEveryPackAndTheUserSoundsLast() {
        assertEquals(
            listOf(
                SoundFilter.All,
                SoundFilter.Pack(SoundPack.GENTLE),
                SoundFilter.Pack(SoundPack.LOUD),
                SoundFilter.Recorded,
            ),
            SoundPickerModel.filters,
        )
    }

    @Test
    fun theDefaultFilterShowsEveryPackInOrder() {
        val sections = SoundPickerModel.sections(emptyList(), SoundFilter.All)
        assertEquals(2, sections.size)
        assertEquals(
            BuiltinSounds.all.map { BuiltinSounds.idOf(it) },
            SoundPickerModel.ids(sections),
        )
    }

    @Test
    fun anEmptyRecordedPackIsHiddenUntilSelected() {
        assertEquals(2, SoundPickerModel.sections(emptyList(), SoundFilter.All).size)
        val onlyRecorded = SoundPickerModel.sections(emptyList(), SoundFilter.Recorded)
        assertEquals(1, onlyRecorded.size)
        assertTrue((onlyRecorded.single() as SoundSection.Recorded).sounds.isEmpty())
    }

    @Test
    fun theRecordedPackComesAfterTheBuiltIns() {
        val clip = recorded("rec_20261102_104851_a1b2c3d4.m4a", 1_000L)
        val sections = SoundPickerModel.sections(listOf(clip), SoundFilter.All)
        assertEquals(3, sections.size)
        val last = sections.last()
        assertTrue(last is SoundSection.Recorded)
        assertEquals(listOf(clip.soundId), SoundPickerModel.ids(listOf(last)))
    }

    @Test
    fun aPackFilterShowsOnlyItsOwnTones() {
        val sections = SoundPickerModel.sections(emptyList(), SoundFilter.Pack(SoundPack.LOUD))
        assertEquals(
            listOf("digital", "alarm", "pulse").map { "builtin:" + it },
            SoundPickerModel.ids(sections),
        )
    }

    @Test
    fun recordingsAreListedNewestFirst() {
        val older = recorded("rec_20260101_080000_aaaaaaaa.m4a", 1_000L)
        val newer = recorded("rec_20260102_080000_bbbbbbbb.m4a", 2_000L)
        assertEquals(listOf(newer, older), SoundPickerModel.sortRecorded(listOf(older, newer)))
        val sections = SoundPickerModel.sections(listOf(older, newer), SoundFilter.Recorded)
        assertEquals(
            listOf(newer.soundId, older.soundId),
            SoundPickerModel.ids(sections),
        )
    }

    @Test
    fun blankIdMeansTheGlobalDefault() {
        assertTrue(SoundPickerModel.isDefaultSelected(null))
        assertTrue(SoundPickerModel.isDefaultSelected(""))
        assertTrue(SoundPickerModel.isDefaultSelected("   "))
        assertFalse(SoundPickerModel.isDefaultSelected("builtin:bell"))
    }

    @Test
    fun exactlyOneRowIsSelected() {
        val clip = recorded("rec_20261102_104851_a1b2c3d4.m4a", 1_000L)
        val sections = SoundPickerModel.sections(listOf(clip), SoundFilter.All)
        assertEquals(1, SoundPickerModel.selectionCount(sections, clip.soundId))
        assertEquals(1, SoundPickerModel.selectionCount(sections, "builtin:pulse"))
        assertEquals(0, SoundPickerModel.selectionCount(sections, "file:/gone.m4a"))
        assertTrue(SoundPickerModel.isSelected("builtin:pulse", "builtin:pulse"))
        assertFalse(SoundPickerModel.isSelected(null, "builtin:pulse"))
    }
}
