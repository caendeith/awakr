package io.github.caendeith.awakr.data.sleep

import io.github.caendeith.awakr.data.db.dao.SleepDao
import io.github.caendeith.awakr.data.db.entity.SleepSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Nights imported from Health Connect: the repository never talks to the sensor layer itself,
 * it only stores what a [SleepSource] produced, so the UI stays source-agnostic.
 */
class SleepRepository(private val dao: SleepDao) {

    fun observeAll(): Flow<List<SleepSession>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun latest(): SleepSession? = dao.latest()?.toDomain()

    /**
     * Replaces the nights the import just saw and drops anything older than [RETENTION_DAYS].
     * Imported rows keep their id (the session start), so re-importing is idempotent.
     */
    suspend fun replaceWindow(sessions: List<SleepSession>) {
        if (sessions.isNotEmpty()) dao.upsertAll(sessions.map { it.toEntity() })
        dao.deleteStartedBefore(System.currentTimeMillis() - RETENTION_DAYS * DAY_MILLIS)
    }

    companion object {
        const val RETENTION_DAYS = 90
        private const val DAY_MILLIS = 86_400_000L
    }
}

internal fun SleepSessionEntity.toDomain(): SleepSession = SleepSession(
    id = id,
    startTime = startTime,
    endTime = endTime,
    stages = SleepStageCodec.decode(stages),
    hrMin = hrMin,
    hrAvg = hrAvg,
    hrMax = hrMax,
    spo2Avg = spo2Avg,
    source = source,
)

internal fun SleepSession.toEntity(): SleepSessionEntity = SleepSessionEntity(
    id = id,
    startTime = startTime,
    endTime = endTime,
    stages = SleepStageCodec.encode(stages),
    hrMin = hrMin,
    hrAvg = hrAvg,
    hrMax = hrMax,
    spo2Avg = spo2Avg,
    source = source,
    importedAt = System.currentTimeMillis(),
)
