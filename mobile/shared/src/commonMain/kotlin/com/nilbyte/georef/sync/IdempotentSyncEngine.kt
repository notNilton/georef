package com.nilbyte.georef.sync

import com.nilbyte.georef.data.local.GisLocalDatabase
import com.nilbyte.georef.data.local.LocalDatabase
import com.nilbyte.georef.data.local.OfflineMapTileStore
import com.nilbyte.georef.data.remote.GisSyncPushRequest
import com.nilbyte.georef.data.remote.KtorSyncApiClient
import com.nilbyte.georef.domain.gis.GisFormatImporter
import com.nilbyte.georef.domain.model.GeorefRecord
import com.nilbyte.georef.domain.model.GisLayer
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
    val gisLocalDatabase: GisLocalDatabase = GisLocalDatabase(),
    val apiClient: KtorSyncApiClient = KtorSyncApiClient(),
    val offlineMapTileStore: OfflineMapTileStore = OfflineMapTileStore()
) {
    private val syncScope = CoroutineScope(Dispatchers.IO)
    private val syncMutex = Mutex()
    private val pdfExtractor = GeoPdfExtractor()
    private val gisImporter = GisFormatImporter()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _selectedGeoPdf = MutableStateFlow<GeoPdfMetadata?>(null)
    val selectedGeoPdf: StateFlow<GeoPdfMetadata?> = _selectedGeoPdf.asStateFlow()

    private var lastSyncServerTimestamp: Long = 0L

    val recordsFlow: StateFlow<List<GeorefRecord>> = localDatabase.recordsFlow
    val gisLayersFlow: StateFlow<List<GisLayer>> = gisLocalDatabase.gisLayersFlow
    val selectedGisLayer: StateFlow<GisLayer?> = gisLocalDatabase.selectedGisLayer

    /**
     * Import GIS file (GeoPDF, GeoJSON, KML, GeoTIFF), saving it locally offline
     * and setting it as the active overlay layer on the global GIS map.
     */
    suspend fun importGisDocument(fileBytes: ByteArray, fileName: String): GisLayer {
        val gisLayer = gisImporter.importGisDocument(fileBytes, fileName, clientId)
        gisLocalDatabase.saveOrUpdateLayer(gisLayer, isPendingSync = true)
        gisLocalDatabase.selectLayer(gisLayer)

        // Also create a local field record marker at the center of the imported map
        createFieldRecord(
            id = "rec-" + gisLayer.id,
            name = "Camada GIS: $fileName",
            description = "Formato: ${gisLayer.fileType} | BBox: [${gisLayer.minLat}, ${gisLayer.minLng}] a [${gisLayer.maxLat}, ${gisLayer.maxLng}]",
            latitude = gisLayer.centerLat,
            longitude = gisLayer.centerLng,
            elevation = 0.0,
            accuracy = 1.0
        )

        return gisLayer
    }

    /**
     * Set a saved GIS layer as active overlay on the global interactive map.
     */
    fun selectGisLayerForMapOverlay(layer: GisLayer?) {
        syncScope.launch {
            gisLocalDatabase.selectLayer(layer)
        }
    }

    /**
     * Process an incoming PDF file, extracting embedded GeoPDF metadata (/GPTS, /BBox).
     */
    suspend fun processGeoPdfFile(pdfBytes: ByteArray, fileName: String): GeoPdfMetadata {
        val metadata = pdfExtractor.extractGeoMetadata(pdfBytes, fileName)
        _selectedGeoPdf.value = metadata
        importGisDocument(pdfBytes, fileName)
        return metadata
    }

    /**
     * Triggers offline tile download for the entire regional bounding box of the active PDF/GIS layer.
     */
    fun downloadMapTilesForPdfRegion(minZoom: Int = 12, maxZoom: Int = 15) {
        val activeGis = selectedGisLayer.value
        val box = activeGis?.boundingBox ?: _selectedGeoPdf.value?.boundingBox ?: return
        val name = activeGis?.name ?: _selectedGeoPdf.value?.fileName ?: "Região GIS"

        offlineMapTileStore.downloadMapTilesForRegion(
            boundingBox = box,
            regionName = name,
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
     * Asynchronously triggers idempotent network synchronization cycle for records and PostGIS layers.
     */
    fun syncNow(batchId: String) {
        syncScope.launch {
            performSync(batchId)
        }
    }

    suspend fun performSync(batchId: String): SyncState = syncMutex.withLock {
        _syncState.value = SyncState.Syncing

        val pendingRecords = localDatabase.getPendingOutbox()
        val pendingGisLayers = gisLocalDatabase.getPendingOutbox()

        if (pendingRecords.isEmpty() && pendingGisLayers.isEmpty()) {
            val pullResult = apiClient.pullSync(clientId, lastSyncServerTimestamp)
            pullResult.onSuccess { pullResp ->
                for (record in pullResp.records) {
                    localDatabase.saveOrUpdate(record.copy(syncStatus = SyncStatus.SYNCED), isPendingSync = false)
                }
                if (pullResp.lastSyncServer > lastSyncServerTimestamp) {
                    lastSyncServerTimestamp = pullResp.lastSyncServer
                }
            }

            val state = SyncState.Success(0, "Local store & PostGIS up to date")
            _syncState.value = state
            return state
        }

        // Push pending GIS layers to PostGIS
        if (pendingGisLayers.isNotEmpty()) {
            val gisReq = GisSyncPushRequest(
                batchId = "gis-$batchId",
                clientId = clientId,
                lastSyncServer = lastSyncServerTimestamp,
                layers = pendingGisLayers
            )
            val gisRes = apiClient.pushGisSync(gisReq)
            gisRes.onSuccess { _ ->
                for (l in pendingGisLayers) {
                    gisLocalDatabase.markSynced(l.id, l.version, Clock.System.now().toEpochMilliseconds())
                }
            }
        }

        if (pendingRecords.isEmpty()) {
            val successState = SyncState.Success(pendingGisLayers.size, "Synced ${pendingGisLayers.size} GIS layers to PostGIS")
            _syncState.value = successState
            return successState
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
                    message = "Successfully synced ${response.processedCount} records & PostGIS GIS layers"
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
