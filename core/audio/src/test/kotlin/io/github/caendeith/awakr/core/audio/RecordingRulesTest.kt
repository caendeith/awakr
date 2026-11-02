package io.github.caendeith.awakr.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class RecordingRulesTest {

    private val utc = ZoneId.of("UTC")
    private val instant = LocalDateTime.of(2026, 11, 2, 10, 48, 51)
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()

    @Test
    fun fileNameIsSortableAndUnique() {
        assertEquals(
            "rec_20261102_104851_a1b2c3d4.m4a",
            RecordingRules.fileName(instant, "a1b2c3d4-1111-2222", utc),
        )
    }

    @Test
    fun fileNameStripsUnsafeCharacters() {
        assertEquals(
            "rec_20261102_104851_abcdef12.m4a",
            RecordingRules.fileName(instant, "../../ab/cd:ef 12", utc),
        )
        assertTrue(RecordingRules.fileName(instant, "!!!", utc).endsWith("_sound.m4a"))
    }

    @Test
    fun theLabelRoundTrips() {
        val name = RecordingRules.fileName(instant, "x", utc)
        assertEquals("10:48", RecordingRules.timeLabel(name))
        assertEquals(instant, RecordingRules.timestamp(name)!!.toInstant(ZoneOffset.UTC).toEpochMilli())
    }

    @Test
    fun importedFilesHaveNoLabel() {
        assertNull(RecordingRules.timeLabel("1f2e3d4c-aaaa.mp3"))
        assertNull(RecordingRules.timeLabel("rec_2026.m4a"))
        assertNull(RecordingRules.timeLabel("rec_XXXXXXXX_XXXXXX_a.m4a"))
        assertNull(RecordingRules.timestamp("random.m4a"))
    }

    @Test
    fun durationIsClampedToTheCeiling() {
        assertEquals(0, RecordingRules.clampDurationMs(-5))
        assertEquals(12_345, RecordingRules.clampDurationMs(12_345))
        assertEquals(30_000, RecordingRules.clampDurationMs(99_999))
        assertEquals(0, RecordingRules.remainingMs(40_000))
        assertEquals(500, RecordingRules.remainingMs(29_500))
        assertEquals(0.5f, RecordingRules.progress(15_000))
        assertEquals(1f, RecordingRules.progress(50_000))
    }

    @Test
    fun durationLabelUsesMinutesAndSeconds() {
        assertEquals("0:00", RecordingRules.durationLabel(0))
        assertEquals("0:07", RecordingRules.durationLabel(7_000))
        assertEquals("0:30", RecordingRules.durationLabel(30_000))
        // Anything past the ceiling is clamped, so the label never shows 1:05.
        assertEquals("0:30", RecordingRules.durationLabel(65_000))
    }

    @Test
    fun clipsMustBeLongEnoughButNotTooLong() {
        assertFalse(RecordingRules.isUsable(0))
        assertFalse(RecordingRules.isUsable(RecordingRules.MIN_DURATION_MS - 1L))
        assertTrue(RecordingRules.isUsable(RecordingRules.MIN_DURATION_MS.toLong()))
        assertTrue(RecordingRules.isUsable(RecordingRules.MAX_DURATION_MS.toLong()))
        assertTrue(RecordingRules.isUsable(RecordingRules.MAX_DURATION_MS + 3_000L))
        assertFalse(RecordingRules.isUsable(RecordingRules.MAX_DURATION_MS + 3_001L))
    }
}
