package com.nilbyte.georef.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class SyncStatus {
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
    SYNCED,
    FAILED
}

@Serializable
data class GeorefRecord(
    val id: String,
    @SerialName("client_id")
    val clientId: String,
    val name: String,
    val description: String = "",
    val latitude: Double,
    val longitude: Double,
    val elevation: Double = 0.0,
    val accuracy: Double = 0.0,
    @SerialName("metadata_json")
    val metadataJson: String = "{}",
    @SerialName("client_updated_at")
    val clientUpdatedAt: Long,
    @SerialName("server_updated_at")
    val serverUpdatedAt: Long = 0L,
    val version: Int = 1,
    @SerialName("is_deleted")
    val isDeleted: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)

@Serializable
data class SyncPushRequest(
    @SerialName("batch_id")
    val batchId: String,
    @SerialName("client_id")
    val clientId: String,
    @SerialName("last_sync_server")
    val lastSyncServer: Long,
    val records: List<GeorefRecord>
)

@Serializable
data class SyncItemStatus(
    val id: String,
    val status: String,
    @SerialName("server_version")
    val serverVersion: Int,
    @SerialName("server_updated_at")
    val serverUpdatedAt: Long,
    val message: String? = null
)

@Serializable
data class SyncPushResponse(
    @SerialName("batch_id")
    val batchId: String,
    @SerialName("processed_count")
    val processedCount: Int,
    val statuses: List<SyncItemStatus>,
    @SerialName("server_changes")
    val serverChanges: List<GeorefRecord> = emptyList(),
    @SerialName("new_last_sync_server")
    val newLastSyncServer: Long
)

@Serializable
data class SyncPullResponse(
    val records: List<GeorefRecord>,
    @SerialName("last_sync_server")
    val lastSyncServer: Long,
    @SerialName("has_more")
    val hasMore: Boolean
)
