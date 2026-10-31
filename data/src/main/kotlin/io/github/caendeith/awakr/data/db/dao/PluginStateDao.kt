package io.github.caendeith.awakr.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.caendeith.awakr.data.db.entity.PluginStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginStateDao {
    @Query("SELECT * FROM plugin_state")
    fun observeAll(): Flow<List<PluginStateEntity>>

    @Query("SELECT * FROM plugin_state WHERE plugin_id = :pluginId")
    suspend fun get(pluginId: String): PluginStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: PluginStateEntity)

    @Query("UPDATE plugin_state SET enabled = :enabled WHERE plugin_id = :pluginId")
    suspend fun setEnabled(pluginId: String, enabled: Boolean)

    @Query("UPDATE plugin_state SET last_error = :error WHERE plugin_id = :pluginId")
    suspend fun setLastError(pluginId: String, error: String?)
}
