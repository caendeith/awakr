package io.github.caendeith.awakr.data.sleep

import kotlin.math.roundToInt

/** One raw night as a source reported it, before the domain filters and aggregates are applied. */
data class RawSleepSession(
    val start: Long,
    val end: Long,
    val stages: List<SleepStage> = emptyList(),
)

/** One heart-rate measurement, epoch millis. */
data class HeartRateSample(val time: Long, val beatsPerMinute: Int)

/** One blood-oxygen measurement, epoch millis and percent (0..100). */
data class Spo2Sample(val time: Long, val percent: Double)

/**
 * Maps source records to [SleepSession] without touching Android classes, so the whole pipeline can
 * be unit-tested on the JVM.
 *
 * A night is kept only when it lasts between [MIN_DURATION_MILLIS] and [MAX_DURATION_MILLIS]:
 * shorter intervals are naps or sensor noise, longer ones are usually several nights stitched
 * together after a watch was not taken off. Heart rate and SpO2 are averaged inside the session only.
 */
object SleepSessionMapper {

    const val SOURCE_HEALTH_CONNECT = "health_connect"

    const val MIN_DURATION_MILLIS: Long = 90L * 60L * 1_000L
    const val MAX_DURATION_MILLIS: Long = 16L * 60L * 60L * 1_000L

    private const val MAX_PLAUSIBLE_BPM = 300

    fun map(
        raw: RawSleepSession,
        heartRates: List<HeartRateSample>,
        spo2: List<Spo2Sample>,
    ): SleepSession? {
        val duration = raw.end - raw.start
        if (duration < MIN_DURATION_MILLIS || duration > MAX_DURATION_MILLIS) return null

        val beats = heartRates.asSequence()
            .filter { it.time in raw.start until raw.end && it.beatsPerMinute in 1..MAX_PLAUSIBLE_BPM }
            .map { it.beatsPerMinute }
            .toList()

        return SleepSession(
            id = raw.start.toString(),
            startTime = raw.start,
            endTime = raw.end,
            stages = SleepStageNormalizer.normalize(raw.start, raw.end, raw.stages),
            hrMin = beats.minOrNull(),
            hrAvg = if (beats.isEmpty()) null else beats.average().roundToInt(),
            hrMax = beats.maxOrNull(),
            spo2Avg = averageSpo2(spo2, raw.start, raw.end),
            source = SOURCE_HEALTH_CONNECT,
        )
    }

    /** Maps every raw night, dropping filtered ones and duplicates with the same start. */
    fun mapAll(
        raws: List<RawSleepSession>,
        heartRates: List<HeartRateSample>,
        spo2: List<Spo2Sample>,
    ): List<SleepSession> = raws
        .sortedBy { it.start }
        .mapNotNull { map(it, heartRates, spo2) }
        .distinctBy { it.id }

    private fun averageSpo2(samples: List<Spo2Sample>, start: Long, end: Long): Double? {
        val values = samples.asSequence()
            .filter { it.time in start until end && it.percent > 0.0 && it.percent <= 100.0 }
            .map { it.percent }
            .toList()
        if (values.isEmpty()) return null
        val average = values.average()
        return (average * 10.0).roundToInt() / 10.0
    }
}
