package io.github.caendeith.awakr.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.caendeith.awakr.data.db.entity.AlarmLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmLogDao {
    @Insert
    suspend fun insert(log: AlarmLogEntity): Long

    @Query("SELECT * FROM alarm_logs ORDER BY scheduled_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<AlarmLogEntity>>

    @Query("SELECT * FROM alarm_logs WHERE alarm_id = :alarmId ORDER BY scheduled_at DESC LIMIT :limit")
    fun observeForAlarm(alarmId: Long, limit: Int = 20): Flow<List<AlarmLogEntity>>

    @Query("SELECT * FROM alarm_logs WHERE scheduled_at >= :since")
    suspend fun logsScheduledSince(since: Long): List<AlarmLogEntity>

    @Query("SELECT * FROM alarm_logs WHERE outcome = 'MISSED' ORDER BY scheduled_at DESC LIMIT :limit")
    fun observeMissed(limit: Int = 20): Flow<List<AlarmLogEntity>>

    @Query("DELETE FROM alarm_logs WHERE scheduled_at < :before")
    suspend fun deleteOlderThan(before: Long)
}
