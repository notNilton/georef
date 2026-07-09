package com.nilbyte.georef.data.local

import com.nilbyte.georef.domain.model.GisLayer
import com.nilbyte.georef.domain.model.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GisLocalDatabase {
    private val mutex = Mutex()
    private val layersMap = mutableMapOf<String, GisLayer>()
    private val outboxQueue = mutableSetOf<String>()

    private val _gisLayersFlow = MutableStateFlow<List<GisLayer>>(emptyList())
    val gisLayersFlow: StateFlow<List<GisLayer>> = _gisLayersFlow.asStateFlow()

    private val _selectedGisLayer = MutableStateFlow<GisLayer?>(null)
    val selectedGisLayer: StateFlow<GisLayer?> = _selectedGisLayer.asStateFlow()

    suspend fun saveOrUpdateLayer(layer: GisLayer, isPendingSync: Boolean = true) = mutex.withLock {
        val updatedLayer = if (isPendingSync && layer.syncStatus == SyncStatus.SYNCED) {
            layer.copy(syncStatus = SyncStatus.PENDING_UPDATE)
        } else {
            layer
        }

        layersMap[layer.id] = updatedLayer
        if (updatedLayer.syncStatus != SyncStatus.SYNCED) {
            outboxQueue.add(layer.id)
        } else {
            outboxQueue.remove(layer.id)
        }
        publishUpdates()
    }

    suspend fun markSynced(id: String, serverVersion: Int, serverUpdatedAt: Long) = mutex.withLock {
        layersMap[id]?.let { existing ->
            layersMap[id] = existing.copy(
                syncStatus = SyncStatus.SYNCED,
                version = serverVersion,
                serverUpdatedAt = serverUpdatedAt
            )
        }
        outboxQueue.remove(id)
        publishUpdates()
    }

    suspend fun selectLayer(layer: GisLayer?) = mutex.withLock {
        _selectedGisLayer.value = layer
    }

    suspend fun getPendingOutbox(): List<GisLayer> = mutex.withLock {
        outboxQueue.mapNotNull { id -> layersMap[id] }
    }

    suspend fun getAllLayers(): List<GisLayer> = mutex.withLock {
        layersMap.values.filter { !it.isDeleted }.toList()
    }

    private fun publishUpdates() {
        _gisLayersFlow.value = layersMap.values.filter { !it.isDeleted }.sortedByDescending { it.clientUpdatedAt }
    }
}
