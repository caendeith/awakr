package io.github.caendeith.awakr.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarm_logs")
data class AlarmLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "alarm_id") val alarmId: Long,
    @ColumnInfo(name = "scheduled_at") val scheduledAt: Long,
    @ColumnInfo(name = "fired_at") val firedAt: Long? = null,
    @ColumnInfo(name = "dismissed_at") val dismissedAt: Long? = null,
    @ColumnInfo(name = "mission_plugin_id") val missionPluginId: String? = null,
    @ColumnInfo(name = "mission_passed") val missionPassed: Boolean = false,
    @ColumnInfo(name = "snooze_count") val snoozeCount: Int = 0,
    val outcome: String,
)
