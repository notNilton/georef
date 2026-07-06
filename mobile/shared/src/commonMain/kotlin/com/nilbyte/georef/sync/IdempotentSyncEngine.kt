package com.nilbyte.georef.sync

import com.nilbyte.georef.data.local.LocalDatabase
import com.nilbyte.georef.data.local.OfflineMapTileStore
import com.nilbyte.georef.data.remote.KtorSyncApiClient
import com.nilbyte.georef.domain.model.GeorefRecord
import com.nilbyte.georef.domain.model.SyncPushRequest
import com.nilbyte.georef.domain.model.SyncStatus
import com.nilbyte.georef.domain.pdf.GeoPdfExtractor
import com.nilbyte.georef.domain.pdf.GeoPdfMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val itemsSyncedCount: Int, val message: String) : SyncState()
    data class OfflineError(val reason: String) : SyncState()
}

class IdempotentSyncEngine(
    val clientId: String,
    val localDatabase: LocalDatabase = LocalDatabase(),
    val apiClient: KtorSyncApiClient = KtorSyncApiClient(),
    val offlineMapTileStore: OfflineMapTileStore = OfflineMapTileStore()
) {
    private val syncScope = CoroutineScope(Dispatchers.IO)
    private val syncMutex = Mutex()
    private val pdfExtractor = GeoPdfExtractor()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _selectedGeoPdf = MutableStateFlow<GeoPdfMetadata?>(null)
    val selectedGeoPdf: StateFlow<GeoPdfMetadata?> = _selectedGeoPdf.asStateFlow()

    private var lastSyncServerTimestamp: Long = 0L

    val recordsFlow: StateFlow<List<GeorefRecord>> = localDatabase.recordsFlow

    /**
     * Process an incoming PDF file, extracting embedded GeoPDF metadata (/GPTS, /BBox)
     * and coordinates, creating a local offline record for field use.
     */
    suspend fun processGeoPdfFile(pdfBytes: ByteArray, fileName: String): GeoPdfMetadata {
        val metadata = pdfExtractor.extractGeoMetadata(pdfBytes, fileName)
        _selectedGeoPdf.value = metadata

        // Save center point of GeoPDF as a local georeferenced field record
        val recordId = "pdf-geo-" + metadata.centerPoint.latitude.toString().take(6) + "-" + metadata.centerPoint.longitude.toString().take(6)
        createFieldRecord(
            id = recordId,
            name = "PDF Geo: $fileName",
            description = "Extraído de PDF | BBox: [${metadata.boundingBox.minLat}, ${metadata.boundingBox.minLng}] a [${metadata.boundingBox.maxLat}, ${metadata.boundingBox.maxLng}]",
            latitude = metadata.centerPoint.latitude,
            longitude = metadata.centerPoint.longitude,
            elevation = metadata.centerPoint.elevation,
            accuracy = 1.5
        )

        return metadata
    }

    /**
     * Triggers offline tile download for the entire regional bounding box of the active PDF.
     */
    fun downloadMapTilesForPdfRegion(minZoom: Int = 12, maxZoom: Int = 15) {
        val pdfMeta = _selectedGeoPdf.value ?: return
        offlineMapTileStore.downloadMapTilesForRegion(
            boundingBox = pdfMeta.boundingBox,
            regionName = pdfMeta.fileName,
            minZoom = minZoom,
            maxZoom = maxZoom
        )
    }

    /**
     * Field operation: Create record locally when offline.
     */
    suspend fun createFieldRecord(
        id: String,
        name: String,
        description: String,
        latitude: Double,
        longitude: Double,
        elevation: Double = 0.0,
        accuracy: Double = 0.0
    ): GeorefRecord {
        val now = Clock.System.now().toEpochMilliseconds()
        val record = GeorefRecord(
            id = id,
            clientId = clientId,
            name = name,
            description = description,
            latitude = latitude,
            longitude = longitude,
            elevation = elevation,
            accuracy = accuracy,
            clientUpdatedAt = now,
            serverUpdatedAt = 0L,
            version = 1,
            isDeleted = false,
            syncStatus = SyncStatus.PENDING_CREATE
        )
        localDatabase.saveOrUpdate(record, isPendingSync = true)
        return record
    }

    /**
     * Field operation: Update existing record locally when offline.
     */
    suspend fun updateFieldRecord(
        id: String,
        name: String,
        description: String,
        latitude: Double,
        longitude: Double
    ) {
        val existing = localDatabase.getRecordById(id) ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        val updated = existing.copy(
            name = name,
            description = description,
            latitude = latitude,
            longitude = longitude,
            clientUpdatedAt = now,
            version = existing.version + 1,
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        localDatabase.saveOrUpdate(updated, isPendingSync = true)
    }

    /**
     * Field operation: Soft delete record locally when offline.
     */
    suspend fun deleteFieldRecord(id: String) {
        val existing = localDatabase.getRecordById(id) ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        val deleted = existing.copy(
            isDeleted = true,
            clientUpdatedAt = now,
            syncStatus = SyncStatus.PENDING_DELETE
        )
        localDatabase.saveOrUpdate(deleted, isPendingSync = true)
    }

    /**
     * Asynchronously triggers idempotent network synchronization cycle.
     */
    fun syncNow(batchId: String) {
        syncScope.launch {
            performSync(batchId)
        }
    }

    suspend fun performSync(batchId: String): SyncState = syncMutex.withLock {
        _syncState.value = SyncState.Syncing

        val pendingRecords = localDatabase.getPendingOutbox()
        if (pendingRecords.isEmpty()) {
            val pullResult = apiClient.pullSync(clientId, lastSyncServerTimestamp)
            pullResult.onSuccess { pullResp ->
                for (record in pullResp.records) {
                    localDatabase.saveOrUpdate(record.copy(syncStatus = SyncStatus.SYNCED), isPendingSync = false)
                }
                if (pullResp.lastSyncServer > lastSyncServerTimestamp) {
                    lastSyncServerTimestamp = pullResp.lastSyncServer
                }
            }

            val state = SyncState.Success(0, "Local store up to date")
            _syncState.value = state
            return state
        }

        val request = SyncPushRequest(
            batchId = batchId,
            clientId = clientId,
            lastSyncServer = lastSyncServerTimestamp,
            records = pendingRecords
        )

        val result = apiClient.pushSync(request)
        result.fold(
            onSuccess = { response ->
                for (itemStatus in response.statuses) {
                    if (itemStatus.status == "ACCEPTED" || itemStatus.status == "IGNORED_STALE") {
                        localDatabase.markSynced(
                            id = itemStatus.id,
                            serverVersion = itemStatus.serverVersion,
                            serverUpdatedAt = itemStatus.serverUpdatedAt
                        )
                    }
                }

                for (serverRec in response.serverChanges) {
                    localDatabase.saveOrUpdate(
                        serverRec.copy(syncStatus = SyncStatus.SYNCED),
                        isPendingSync = false
                    )
                }

                if (response.newLastSyncServer > lastSyncServerTimestamp) {
                    lastSyncServerTimestamp = response.newLastSyncServer
                }

                val successState = SyncState.Success(
                    itemsSyncedCount = response.processedCount,
                    message = "Successfully synced ${response.processedCount} records"
                )
                _syncState.value = successState
                successState
            },
            onFailure = { error ->
                val errorState = SyncState.OfflineError(
                    reason = error.message ?: "Network unreachable. Saved in local outbox."
                )
                _syncState.value = errorState
                errorState
            }
        )
    }
}
