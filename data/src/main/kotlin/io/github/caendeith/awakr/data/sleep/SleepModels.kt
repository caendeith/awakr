package io.github.caendeith.awakr.data.sleep

/** A part of the night as the wearable classified it. */
enum class SleepStageType { AWAKE, LIGHT, DEEP, REM, UNKNOWN }

/** Half-open interval [start, end) of one sleep stage. */
data class SleepStage(
    val type: SleepStageType,
    val start: Long,
    val end: Long,
) {
    val durationMillis: Long get() = (end - start).coerceAtLeast(0L)
}

/**
 * One night of sleep.
 *
 * [stages] may be empty when the source only knows the interval; the UI then shows the total only.
 * [hrMin]/[hrAvg]/[hrMax] and [spo2Avg] are optional: not every watch records them.
 */
data class SleepSession(
    val id: String,
    val startTime: Long,
    val endTime: Long,
    val stages: List<SleepStage> = emptyList(),
    val hrMin: Int? = null,
    val hrAvg: Int? = null,
    val hrMax: Int? = null,
    val spo2Avg: Double? = null,
    val source: String = "",
) {
    val durationMillis: Long get() = (endTime - startTime).coerceAtLeast(0L)

    /** Time spent asleep, i.e. without the awake stages; falls back to [durationMillis]. */
    val asleepMillis: Long
        get() {
            if (stages.isEmpty()) return durationMillis
            return stages.filter { it.type != SleepStageType.AWAKE }.sumOf { it.durationMillis }
        }
}
