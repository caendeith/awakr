package io.github.caendeith.awakr.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One night recorded by a wearable and imported from Health Connect.
 *
 * [stages] is a compact encoding of the hypnogram (see SleepStageCodec): `LIGHT:start:end;...`.
 * The row id is the session start in epoch millis, so re-reading the same night replaces it.
 */
@Entity(tableName = "sleep_sessions", indices = [Index(value = ["start_time"])])
data class SleepSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    val stages: String = "",
    @ColumnInfo(name = "hr_min") val hrMin: Int? = null,
    @ColumnInfo(name = "hr_avg") val hrAvg: Int? = null,
    @ColumnInfo(name = "hr_max") val hrMax: Int? = null,
    @ColumnInfo(name = "spo2_avg") val spo2Avg: Double? = null,
    val source: String = "",
    @ColumnInfo(name = "imported_at") val importedAt: Long = 0L,
)
