package io.github.caendeith.awakr.data.repository

import io.github.caendeith.awakr.core.alarm.Alarm
import io.github.caendeith.awakr.data.db.dao.AlarmDao
import io.github.caendeith.awakr.data.mapper.toDomain
import io.github.caendeith.awakr.data.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlarmRepository(private val dao: AlarmDao) {

    fun observeAll(): Flow<List<Alarm>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: Long): Alarm? = dao.getById(id)?.toDomain()?.takeIf { it.deletedAt == null }

    suspend fun enabledAlarms(): List<Alarm> = dao.getEnabled().map { it.toDomain() }

    /** Inserts a new alarm (id == 0) or replaces an existing one, stamping timestamps. */
    suspend fun save(alarm: Alarm): Long {
        val now = System.currentTimeMillis()
        return if (alarm.id == 0L) {
            val order = if (alarm.sortOrder > 0) alarm.sortOrder else dao.nextSortOrder()
            dao.insert(alarm.copy(sortOrder = order, createdAt = now, updatedAt = now).toEntity())
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

    suspend fun duplicate(alarm: Alarm): Long = save(
        alarm.copy(
            id = 0L,
            sortOrder = 0,
            label = alarm.label,
            skipNextAt = null,
            deletedAt = null,
            createdAt = 0L,
            updatedAt = 0L,
        ),
    )

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled, System.currentTimeMillis())
    }

    /** One-tap "do not ring the next time" marker; cleared by the scheduler once it passes. */
    suspend fun setSkipNextAt(id: Long, skipAt: Long?) {
        dao.setSkipNextAt(id, skipAt, System.currentTimeMillis())
    }

    /** Soft deletion: the row stays hidden until [purgeDeleted] (or an undo) runs. */
    suspend fun delete(id: Long) = delete(listOf(id))

    suspend fun delete(ids: Collection<Long>) {
        val now = System.currentTimeMillis()
        ids.forEach { dao.softDelete(it, now) }
    }

    suspend fun restore(ids: Collection<Long>) {
        val now = System.currentTimeMillis()
        ids.forEach { dao.restore(it, now) }
    }

    suspend fun purgeDeleted() = dao.purgeDeleted()

    suspend fun reorder(orderedIds: List<Long>) {
        val now = System.currentTimeMillis()
        orderedIds.forEachIndexed { index, id -> dao.setSortOrder(id, index, now) }
    }
}
