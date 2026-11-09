package io.github.caendeith.awakr.data.sleep

/**
 * Turns the stages reported by a source into a clean, non-overlapping timeline for one session.
 *
 * Wearables are not consistent: a stage can start before the session itself, stages can overlap each
 * other after a firmware update, and the same type often arrives as several adjacent slices. The UI
 * draws the timeline as a single bar, so [normalize] clamps everything to the session bounds, drops
 * empty stages, trims overlaps and merges adjacent stages of the same type.
 */
object SleepStageNormalizer {

    fun normalize(sessionStart: Long, sessionEnd: Long, stages: List<SleepStage>): List<SleepStage> {
        if (sessionEnd <= sessionStart) return emptyList()

        val clamped = stages.mapNotNull { stage ->
            val start = stage.start.coerceIn(sessionStart, sessionEnd)
            val end = stage.end.coerceIn(sessionStart, sessionEnd)
            if (end <= start) null else stage.copy(start = start, end = end)
        }.sortedWith(compareBy({ it.start }, { it.end }))

        val result = mutableListOf<SleepStage>()
        for (stage in clamped) {
            if (result.isEmpty()) {
                result += stage
                continue
            }
            val last = result.last()
            when {
                // Same type and touching or overlapping: extend the open run instead of splitting it.
                last.type == stage.type && stage.start <= last.end ->
                    result[result.lastIndex] = last.copy(end = maxOf(last.end, stage.end))

                // Different type but overlapping: keep only the part that comes after the previous stage.
                stage.start < last.end -> {
                    if (stage.end > last.end) result += stage.copy(start = last.end)
                }

                else -> result += stage
            }
        }
        return result
    }
}
