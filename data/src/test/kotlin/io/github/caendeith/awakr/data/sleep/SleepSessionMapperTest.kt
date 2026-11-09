package io.github.caendeith.awakr.data.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepSessionMapperTest {

    private val start = 1_700_000_000_000L
    private val minute = 60_000L

    private fun night(hours: Double, from: Long = start): RawSleepSession {
        val duration = (hours * 60 * minute).toLong()
        return RawSleepSession(start = from, end = from + duration)
    }

    @Test
    fun `rejects naps shorter than ninety minutes`() {
        assertNull(SleepSessionMapper.map(night(1.49), emptyList(), emptyList()))
    }

    @Test
    fun `accepts exactly ninety minutes`() {
        assertNotNull(SleepSessionMapper.map(night(1.5), emptyList(), emptyList()))
    }

    @Test
    fun `accepts sixteen hours and rejects anything longer`() {
        assertNotNull(SleepSessionMapper.map(night(16.0), emptyList(), emptyList()))
        assertNull(SleepSessionMapper.map(night(16.01), emptyList(), emptyList()))
    }

    @Test
    fun `uses the session start as id and tags the source`() {
        val session = SleepSessionMapper.map(night(7.0), emptyList(), emptyList())!!

        assertEquals(start.toString(), session.id)
        assertEquals(start, session.startTime)
        assertEquals(SleepSessionMapper.SOURCE_HEALTH_CONNECT, session.source)
    }

    @Test
    fun `normalises the stages the source returned`() {
        val raw = night(8.0).copy(
            stages = listOf(
                SleepStage(SleepStageType.LIGHT, start - 10 * minute, start + 60 * minute),
                SleepStage(SleepStageType.LIGHT, start + 60 * minute, start + 120 * minute),
                SleepStage(SleepStageType.DEEP, start + 100 * minute, start + 180 * minute),
            ),
        )

        val session = SleepSessionMapper.map(raw, emptyList(), emptyList())!!

        assertEquals(
            listOf(
                SleepStage(SleepStageType.LIGHT, start, start + 120 * minute),
                SleepStage(SleepStageType.DEEP, start + 120 * minute, start + 180 * minute),
            ),
            session.stages,
        )
    }

    @Test
    fun `keeps a session without stages`() {
        val session = SleepSessionMapper.map(night(6.0), emptyList(), emptyList())!!

        assertTrue(session.stages.isEmpty())
        assertEquals(session.durationMillis, session.asleepMillis)
    }

    @Test
    fun `aggregates heart rate only inside the session`() {
        val heartRates = listOf(
            HeartRateSample(start - minute, 200),
            HeartRateSample(start, 50),
            HeartRateSample(start + 60 * minute, 60),
            HeartRateSample(start + 120 * minute, 0),
            HeartRateSample(start + 6 * 60 * minute + 59 * minute, 99),
        )

        val session = SleepSessionMapper.map(night(7.0), heartRates, emptyList())!!

        assertEquals(50, session.hrMin)
        assertEquals(99, session.hrMax)
        assertEquals(70, session.hrAvg)
    }

    @Test
    fun `leaves heart rate empty when there are no samples`() {
        val session = SleepSessionMapper.map(night(7.0), emptyList(), emptyList())!!

        assertNull(session.hrMin)
        assertNull(session.hrAvg)
        assertNull(session.hrMax)
    }

    @Test
    fun `averages spo2 to one decimal`() {
        val spo2 = listOf(
            Spo2Sample(start, 96.0),
            Spo2Sample(start + 10 * minute, 97.0),
            Spo2Sample(start + 20 * minute, 97.5),
            Spo2Sample(start - minute, 50.0),
            Spo2Sample(start + 30 * minute, 0.0),
        )

        val session = SleepSessionMapper.map(night(7.0), emptyList(), spo2)!!

        assertEquals(96.8, session.spo2Avg!!, 1e-9)
    }

    @Test
    fun `leaves spo2 empty without samples`() {
        assertNull(SleepSessionMapper.map(night(7.0), emptyList(), emptyList())!!.spo2Avg)
    }

    @Test
    fun `mapAll sorts by start and drops duplicates and short sessions`() {
        val late = night(7.0, from = start + 24 * 60 * minute)
        val short = night(1.0, from = start + 48 * 60 * minute)
        val duplicate = night(7.0, from = start + 24 * 60 * minute)

        val sessions = SleepSessionMapper.mapAll(
            raws = listOf(late, short, duplicate, night(8.0, from = start)),
            heartRates = emptyList(),
            spo2 = emptyList(),
        )

        assertEquals(2, sessions.size)
        assertEquals(start, sessions[0].startTime)
        assertEquals(late.start, sessions[1].startTime)
    }
}
