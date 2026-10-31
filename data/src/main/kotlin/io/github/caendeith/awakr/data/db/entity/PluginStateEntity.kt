package io.github.caendeith.awakr.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plugin_state")
data class PluginStateEntity(
    @PrimaryKey @ColumnInfo(name = "plugin_id") val pluginId: String,
    val enabled: Boolean = true,
    @ColumnInfo(name = "settings_json") val settingsJson: String = "{}",
    @ColumnInfo(name = "last_error") val lastError: String? = null,
)
