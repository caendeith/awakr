package io.github.caendeith.awakr.data.sleep

/**
 * Compact, dependency-free encoding of a hypnogram for the database:
 * `LIGHT:1700000000000:1700003600000;DEEP:...`. Unknown or broken tokens are skipped,
 * so a corrupted row degrades to "no stages" instead of crashing the screen.
 */
object SleepStageCodec {

    fun encode(stages: List<SleepStage>): String =
        stages.joinToString(separator = ";") { "${it.type.name}:${it.start}:${it.end}" }

    fun decode(raw: String?): List<SleepStage> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';').mapNotNull { token ->
            val parts = token.split(':')
            if (parts.size != 3) return@mapNotNull null
            val type = SleepStageType.entries.firstOrNull { it.name == parts[0] } ?: return@mapNotNull null
            val start = parts[1].toLongOrNull() ?: return@mapNotNull null
            val end = parts[2].toLongOrNull() ?: return@mapNotNull null
            if (end <= start) return@mapNotNull null
            SleepStage(type = type, start = start, end = end)
        }
    }
}
