package io.github.caendeith.awakr.data.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepStageNormalizerTest {

    private val start = 1_000_000L
    private val end = start + 8 * 60 * 60 * 1000L

    private fun stage(type: SleepStageType, fromMin: Int, toMin: Int) = SleepStage(
        type = type,
        start = start + fromMin * 60_000L,
        end = start + toMin * 60_000L,
    )

    @Test
    fun `clamps stages to the session bounds`() {
        val result = SleepStageNormalizer.normalize(
            sessionStart = start,
            sessionEnd = end,
            stages = listOf(
                SleepStage(SleepStageType.LIGHT, start - 20 * 60_000L, start + 30 * 60_000L),
                SleepStage(SleepStageType.DEEP, end - 30 * 60_000L, end + 60 * 60_000L),
            ),
        )

        assertEquals(2, result.size)
        assertEquals(start, result[0].start)
        assertEquals(start + 30 * 60_000L, result[0].end)
        assertEquals(end - 30 * 60_000L, result[1].start)
        assertEquals(end, result[1].end)
    }

    @Test
    fun `merges touching stages of the same type`() {
        val result = SleepStageNormalizer.normalize(
            sessionStart = start,
            sessionEnd = end,
            stages = listOf(
                stage(SleepStageType.LIGHT, 0, 30),
                stage(SleepStageType.LIGHT, 30, 50),
                stage(SleepStageType.LIGHT, 50, 60),
            ),
        )

        assertEquals(1, result.size)
        assertEquals(stage(SleepStageType.LIGHT, 0, 60), result.single())
    }

    @Test
    fun `merges overlapping stages of the same type`() {
        val result = SleepStageNormalizer.normalize(
            sessionStart = start,
            sessionEnd = end,
            stages = listOf(
                stage(SleepStageType.DEEP, 0, 40),
                stage(SleepStageType.DEEP, 20, 60),
            ),
        )

        assertEquals(listOf(stage(SleepStageType.DEEP, 0, 60)), result)
    }

    @Test
    fun `trims an overlap of different types to the later stage`() {
        val result = SleepStageNormalizer.normalize(
            sessionStart = start,
            sessionEnd = end,
            stages = listOf(
                stage(SleepStageType.LIGHT, 0, 25),
                stage(SleepStageType.REM, 20, 45),
            ),
        )

        assertEquals(
            listOf(
                stage(SleepStageType.LIGHT, 0, 25),
                stage(SleepStageType.REM, 25, 45),
            ),
            result,
        )
    }

    @Test
    fun `drops a stage that is fully covered by the previous one`() {
        val result = SleepStageNormalizer.normalize(
            sessionStart = start,
            sessionEnd = end,
            stages = listOf(
                stage(SleepStageType.LIGHT, 0, 80),
                stage(SleepStageType.DEEP, 10, 20),
            ),
        )

        assertEquals(listOf(stage(SleepStageType.LIGHT, 0, 80)), result)
    }

    @Test
    fun `does not join the same type across another stage`() {
        val result = SleepStageNormalizer.normalize(
            sessionStart = start,
            sessionEnd = end,
            stages = listOf(
                stage(SleepStageType.LIGHT, 0, 20),
                stage(SleepStageType.DEEP, 20, 40),
                stage(SleepStageType.LIGHT, 40, 60),
            ),
        )

        assertEquals(3, result.size)
    }

    @Test
    fun `sorts unordered input`() {
        val result = SleepStageNormalizer.normalize(
            sessionStart = start,
            sessionEnd = end,
            stages = listOf(
                stage(SleepStageType.DEEP, 40, 60),
                stage(SleepStageType.LIGHT, 0, 40),
            ),
        )

        assertEquals(
            listOf(
                stage(SleepStageType.LIGHT, 0, 40),
                stage(SleepStageType.DEEP, 40, 60),
            ),
            result,
        )
    }

    @Test
    fun `drops empty and outside stages`() {
        val result = SleepStageNormalizer.normalize(
            sessionStart = start,
            sessionEnd = end,
            stages = listOf(
                SleepStage(SleepStageType.LIGHT, start - 10 * 60_000L, start),
                SleepStage(SleepStageType.DEEP, start + 60 * 60_000L, start + 60 * 60_000L),
                SleepStage(SleepStageType.REM, end, end + 30 * 60_000L),
            ),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns nothing for an invalid session`() {
        val result = SleepStageNormalizer.normalize(
            sessionStart = end,
            sessionEnd = start,
            stages = listOf(stage(SleepStageType.LIGHT, 0, 60)),
        )

        assertTrue(result.isEmpty())
    }
}
