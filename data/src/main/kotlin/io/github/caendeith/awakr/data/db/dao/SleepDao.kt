package io.github.caendeith.awakr.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.caendeith.awakr.data.db.entity.SleepSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {

    @Query("SELECT * FROM sleep_sessions ORDER BY start_time DESC")
    fun observeAll(): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_sessions ORDER BY start_time DESC LIMIT 1")
    suspend fun latest(): SleepSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SleepSessionEntity>)

    @Query("DELETE FROM sleep_sessions WHERE start_time < :cutoff")
    suspend fun deleteStartedBefore(cutoff: Long)
}
