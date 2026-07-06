package com.nilbyte.georef.data.local

import com.nilbyte.georef.domain.model.GeorefRecord
import com.nilbyte.georef.domain.model.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * LocalDatabase handles offline persistent storage on Android/iOS mobile device.
 * Maintains local entities and outbox sync queue for field operations without network.
 */
class LocalDatabase {
    private val mutex = Mutex()
    private val recordsMap = mutableMapOf<String, GeorefRecord>()
    private val outboxQueue = mutableSetOf<String>()

    private val _recordsFlow = MutableStateFlow<List<GeorefRecord>>(emptyList())
    val recordsFlow: StateFlow<List<GeorefRecord>> = _recordsFlow.asStateFlow()

    suspend fun saveOrUpdate(record: GeorefRecord, isPendingSync: Boolean = true) = mutex.withLock {
        val updatedRecord = if (isPendingSync && record.syncStatus == SyncStatus.SYNCED) {
            record.copy(syncStatus = SyncStatus.PENDING_UPDATE)
        } else {
            record
        }

        recordsMap[record.id] = updatedRecord
        if (updatedRecord.syncStatus != SyncStatus.SYNCED) {
            outboxQueue.add(record.id)
        } else {
            outboxQueue.remove(record.id)
        }
        publishUpdates()
    }

    suspend fun markSynced(id: String, serverVersion: Int, serverUpdatedAt: Long) = mutex.withLock {
        recordsMap[id]?.let { existing ->
            recordsMap[id] = existing.copy(
                syncStatus = SyncStatus.SYNCED,
                version = serverVersion,
                serverUpdatedAt = serverUpdatedAt
            )
        }
        outboxQueue.remove(id)
        publishUpdates()
    }

    suspend fun getPendingOutbox(): List<GeorefRecord> = mutex.withLock {
        outboxQueue.mapNotNull { id -> recordsMap[id] }
    }

    suspend fun getAllRecords(): List<GeorefRecord> = mutex.withLock {
        recordsMap.values.filter { !it.isDeleted }.toList()
    }

    suspend fun getRecordById(id: String): GeorefRecord? = mutex.withLock {
        recordsMap[id]
    }

    private fun publishUpdates() {
        _recordsFlow.value = recordsMap.values.filter { !it.isDeleted }.sortedByDescending { it.clientUpdatedAt }
    }
}
