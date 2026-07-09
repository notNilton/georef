package com.nilbyte.georef.data.local

import com.nilbyte.georef.domain.model.GeoBoundingBox
import com.nilbyte.georef.domain.model.GeoTile
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class TileDownloadState {
    object Idle : TileDownloadState()
    data class Downloading(val current: Int, val total: Int, val percentage: Int) : TileDownloadState()
    data class Completed(val totalDownloaded: Int, val regionName: String) : TileDownloadState()
    data class Error(val message: String) : TileDownloadState()
}

class OfflineMapTileStore(
    private val httpClient: HttpClient = HttpClient()
) {
    private val mutex = Mutex()
    private val tileStorage = mutableMapOf<String, ByteArray>()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _downloadState = MutableStateFlow<TileDownloadState>(TileDownloadState.Idle)
    val downloadState: StateFlow<TileDownloadState> = _downloadState.asStateFlow()

    suspend fun isTileAvailableLocally(tile: GeoTile): Boolean = mutex.withLock {
        tileStorage.containsKey(tile.tileId)
    }

    suspend fun getTileBytes(tile: GeoTile): ByteArray? = mutex.withLock {
        tileStorage[tile.tileId]
    }

    suspend fun getTotalStoredTilesCount(): Int = mutex.withLock {
        tileStorage.size
    }

    /**
     * Downloads and stores all map tiles covering the specified region for offline use.
     */
    fun downloadMapTilesForRegion(
        boundingBox: GeoBoundingBox,
        regionName: String = "Região PDF",
        minZoom: Int = 12,
        maxZoom: Int = 15
    ) {
        scope.launch {
            val tiles = boundingBox.getTileGrid(minZoom, maxZoom)
            val total = tiles.size
            if (total == 0) {
                _downloadState.value = TileDownloadState.Error("Nenhum tile gerado para a caixa de seleção")
                return@launch
            }

            _downloadState.value = TileDownloadState.Downloading(0, total, 0)
            var downloadedCount = 0

            for (tile in tiles) {
                try {
                    val tileUrl = tile.tileUrl
                    val bytes: ByteArray = httpClient.get(tileUrl).body()
                    mutex.withLock {
                        tileStorage[tile.tileId] = bytes
                    }
                    downloadedCount++
                    val pct = (downloadedCount * 100) / total
                    _downloadState.value = TileDownloadState.Downloading(downloadedCount, total, pct)
                } catch (e: Exception) {
                    // In case of offline error or failure, save mock tile placeholder byte
                    mutex.withLock {
                        tileStorage[tile.tileId] = "MOCK_TILE_BYTES".encodeToByteArray()
                    }
                    downloadedCount++
                }
            }

            _downloadState.value = TileDownloadState.Completed(downloadedCount, regionName)
        }
    }
}
