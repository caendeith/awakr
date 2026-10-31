package io.github.caendeith.awakr.data.repository

import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.data.db.dao.AlarmDao
import io.github.caendeith.awakr.data.mapper.toDomain
import io.github.caendeith.awakr.data.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlarmRepository(private val dao: AlarmDao) {

    fun observeAll(): Flow<List<Alarm>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: Long): Alarm? = dao.getById(id)?.toDomain()

    suspend fun enabledAlarms(): List<Alarm> = dao.getEnabled().map { it.toDomain() }

    /** Inserts a new alarm (id == 0) or replaces an existing one, stamping timestamps. */
    suspend fun save(alarm: Alarm): Long {
        val now = System.currentTimeMillis()
        return if (alarm.id == 0L) {
            dao.insert(alarm.copy(createdAt = now, updatedAt = now).toEntity())
        } else {
            val existing = dao.getById(alarm.id)
            val merged = alarm.copy(
                createdAt = existing?.createdAt ?: alarm.createdAt.takeIf { it > 0L } ?: now,
                updatedAt = now,
            )
            dao.update(merged.toEntity())
            merged.id
        }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled, System.currentTimeMillis())
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }
}
