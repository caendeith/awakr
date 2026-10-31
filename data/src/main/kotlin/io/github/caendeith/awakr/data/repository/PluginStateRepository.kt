package io.github.caendeith.awakr.data.repository

import io.github.caendeith.awakr.data.db.dao.PluginStateDao
import io.github.caendeith.awakr.data.db.entity.PluginStateEntity
import kotlinx.coroutines.flow.Flow

class PluginStateRepository(private val dao: PluginStateDao) {

    fun observeAll(): Flow<List<PluginStateEntity>> = dao.observeAll()

    suspend fun get(pluginId: String): PluginStateEntity? = dao.get(pluginId)

    /** Ensures a row exists for [pluginId] without overwriting stored values. */
    suspend fun ensure(pluginId: String) {
        if (dao.get(pluginId) == null) dao.upsert(PluginStateEntity(pluginId = pluginId))
    }

    suspend fun setEnabled(pluginId: String, enabled: Boolean) = dao.setEnabled(pluginId, enabled)

    suspend fun setSettingsJson(pluginId: String, json: String) {
        val current = dao.get(pluginId) ?: PluginStateEntity(pluginId = pluginId)
        dao.upsert(current.copy(settingsJson = json))
    }

    suspend fun setLastError(pluginId: String, error: String?) = dao.setLastError(pluginId, error)
}
