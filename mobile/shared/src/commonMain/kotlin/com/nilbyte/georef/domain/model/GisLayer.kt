package com.nilbyte.georef.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class GisFileType {
    GEOPDF,
    GEOJSON,
    KML,
    GEOTIFF
}

@Serializable
data class GisFeature(
    val id: String,
    val name: String,
    val geometryType: String, // POINT, POLYGON, LINESTRING
    val center: GeoPoint,
    val properties: Map<String, String> = emptyMap()
)

@Serializable
data class GisLayer(
    val id: String,
    @SerialName("client_id")
    val clientId: String,
    val name: String,
    @SerialName("file_type")
    val fileType: GisFileType,
    @SerialName("min_lat")
    val minLat: Double,
    @SerialName("min_lng")
    val minLng: Double,
    @SerialName("max_lat")
    val maxLat: Double,
    @SerialName("max_lng")
    val maxLng: Double,
    @SerialName("center_lat")
    val centerLat: Double,
    @SerialName("center_lng")
    val centerLng: Double,
    val features: List<GisFeature> = emptyList(),
    @SerialName("client_updated_at")
    val clientUpdatedAt: Long,
    @SerialName("server_updated_at")
    val serverUpdatedAt: Long = 0L,
    val version: Int = 1,
    @SerialName("is_deleted")
    val isDeleted: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
) {
    val boundingBox: GeoBoundingBox
        get() = GeoBoundingBox(minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng)

    val centerPoint: GeoPoint
        get() = GeoPoint(latitude = centerLat, longitude = centerLng)
}
