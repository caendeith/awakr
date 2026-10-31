package io.github.caendeith.awakr.data.repository

import io.github.caendeith.awakr.core.alarm.AlarmLog
import io.github.caendeith.awakr.data.db.dao.AlarmLogDao
import io.github.caendeith.awakr.data.mapper.toDomain
import io.github.caendeith.awakr.data.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlarmLogRepository(private val dao: AlarmLogDao) {

    fun observeRecent(limit: Int = 50): Flow<List<AlarmLog>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    fun observeForAlarm(alarmId: Long): Flow<List<AlarmLog>> =
        dao.observeForAlarm(alarmId).map { rows -> rows.map { it.toDomain() } }

    fun observeMissed(limit: Int = 20): Flow<List<AlarmLog>> =
        dao.observeMissed(limit).map { rows -> rows.map { it.toDomain() } }

    /** Logs written for occurrences at or after [since]; used by the missed-alarm detector. */
    suspend fun scheduledSince(since: Long): List<AlarmLog> =
        dao.logsScheduledSince(since).map { it.toDomain() }

    suspend fun append(log: AlarmLog): Long = dao.insert(log.toEntity())

    suspend fun prune(before: Long) = dao.deleteOlderThan(before)
}
