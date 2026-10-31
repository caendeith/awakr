package io.github.caendeith.awakr.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.caendeith.awakr.data.db.entity.AlarmEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms WHERE deleted_at IS NULL ORDER BY sort_order, hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE deleted_at IS NULL ORDER BY sort_order, hour, minute")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE enabled = 1 AND deleted_at IS NULL")
    suspend fun getEnabled(): List<AlarmEntity>

    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM alarms")
    suspend fun nextSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Query("UPDATE alarms SET enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE alarms SET sort_order = :order, updated_at = :updatedAt WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int, updatedAt: Long)

    @Query("UPDATE alarms SET skip_next_at = :skipAt, updated_at = :updatedAt WHERE id = :id")
    suspend fun setSkipNextAt(id: Long, skipAt: Long?, updatedAt: Long)

    @Query("UPDATE alarms SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    @Query("UPDATE alarms SET deleted_at = NULL, updated_at = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long)

    @Query("DELETE FROM alarms WHERE deleted_at IS NOT NULL")
    suspend fun purgeDeleted()

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteById(id: Long)
}
