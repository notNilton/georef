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
    private val activeLayerIds = mutableSetOf<String>()

    private val _gisLayersFlow = MutableStateFlow<List<GisLayer>>(emptyList())
    val gisLayersFlow: StateFlow<List<GisLayer>> = _gisLayersFlow.asStateFlow()

    private val _activeGisLayers = MutableStateFlow<List<GisLayer>>(emptyList())
    val activeGisLayers: StateFlow<List<GisLayer>> = _activeGisLayers.asStateFlow()

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
        // Auto-enable newly imported layer on the map stack
        activeLayerIds.add(layer.id)
        publishUpdates()
    }

    suspend fun toggleLayerActive(id: String) = mutex.withLock {
        if (activeLayerIds.contains(id)) {
            activeLayerIds.remove(id)
        } else {
            activeLayerIds.add(id)
        }
        publishUpdates()
    }

    suspend fun removeLayer(id: String) = mutex.withLock {
        layersMap.remove(id)
        activeLayerIds.remove(id)
        outboxQueue.remove(id)
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

    suspend fun getPendingOutbox(): List<GisLayer> = mutex.withLock {
        outboxQueue.mapNotNull { id -> layersMap[id] }
    }

    suspend fun getAllLayers(): List<GisLayer> = mutex.withLock {
        layersMap.values.filter { !it.isDeleted }.toList()
    }

    private fun publishUpdates() {
        val all = layersMap.values.filter { !it.isDeleted }.sortedByDescending { it.clientUpdatedAt }
        _gisLayersFlow.value = all
        _activeGisLayers.value = all.filter { activeLayerIds.contains(it.id) }
    }
}
