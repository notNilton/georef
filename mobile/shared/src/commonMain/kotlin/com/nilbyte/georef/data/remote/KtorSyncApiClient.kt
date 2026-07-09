package com.nilbyte.georef.data.remote

import com.nilbyte.georef.domain.model.GisLayer
import com.nilbyte.georef.domain.model.SyncPullResponse
import com.nilbyte.georef.domain.model.SyncPushRequest
import com.nilbyte.georef.domain.model.SyncPushResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GisSyncPushRequest(
    val batchId: String,
    val clientId: String,
    val lastSyncServer: Long,
    val layers: List<GisLayer>
)

@Serializable
data class GisSyncPushResponse(
    val batchId: String,
    val processedCount: Int,
    val newLastSyncServer: Long
)

class KtorSyncApiClient(
    private val baseUrl: String = "http://10.0.2.2:8085"
) {
    val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    suspend fun pushSync(request: SyncPushRequest): Result<SyncPushResponse> {
        return runCatching {
            val response = client.post("$baseUrl/api/v1/sync/push") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            response.body<SyncPushResponse>()
        }
    }

    suspend fun pullSync(clientId: String, sinceServer: Long, limit: Int = 100): Result<SyncPullResponse> {
        return runCatching {
            val response = client.get("$baseUrl/api/v1/sync/pull") {
                parameter("client_id", clientId)
                parameter("since_server", sinceServer)
                parameter("limit", limit)
            }
            response.body<SyncPullResponse>()
        }
    }

    suspend fun pushGisSync(request: GisSyncPushRequest): Result<GisSyncPushResponse> {
        return runCatching {
            val response = client.post("$baseUrl/api/v1/gis/sync/push") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            response.body<GisSyncPushResponse>()
        }
    }

    suspend fun getGisLayers(): Result<List<GisLayer>> {
        return runCatching {
            val response = client.get("$baseUrl/api/v1/gis/layers")
            response.body<List<GisLayer>>()
        }
    }
}
