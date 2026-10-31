package io.github.caendeith.awakr.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val hour: Int,
    val minute: Int,
    @ColumnInfo(name = "days_mask") val daysMask: Int = 0,
    val enabled: Boolean = true,
    val label: String = "",
    @ColumnInfo(name = "sound_uri") val soundUri: String? = null,
    @ColumnInfo(name = "sound_pack_id") val soundPackId: String? = null,
    @ColumnInfo(name = "sound_id") val soundId: String = "",
    @ColumnInfo(name = "ramp_seconds") val rampSeconds: Int = 30,
    @ColumnInfo(name = "volume_start") val volumeStart: Int = 15,
    val vibrate: Boolean = true,
    @ColumnInfo(name = "snooze_enabled") val snoozeEnabled: Boolean = true,
    @ColumnInfo(name = "snooze_minutes") val snoozeMinutes: Int = 10,
    @ColumnInfo(name = "max_snoozes") val maxSnoozes: Int = 3,
    @ColumnInfo(name = "mission_plugin_id") val missionPluginId: String = "math",
    @ColumnInfo(name = "mission_config") val missionConfig: String = "{}",
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "skip_next_at") val skipNextAt: Long? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
)
