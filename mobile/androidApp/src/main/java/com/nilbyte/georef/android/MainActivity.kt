package com.nilbyte.georef.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nilbyte.georef.data.local.TileDownloadState
import com.nilbyte.georef.domain.model.GeoPoint
import com.nilbyte.georef.domain.model.GeorefRecord
import com.nilbyte.georef.domain.model.SyncStatus
import com.nilbyte.georef.domain.pdf.GeoPdfMetadata
import com.nilbyte.georef.sync.IdempotentSyncEngine
import com.nilbyte.georef.sync.SyncState
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val syncEngine by lazy {
        IdempotentSyncEngine(clientId = "android-field-" + UUID.randomUUID().toString().take(8))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GeoPdfApp(syncEngine)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoPdfApp(syncEngine: IdempotentSyncEngine) {
    val records by syncEngine.recordsFlow.collectAsState()
    val syncState by syncEngine.syncState.collectAsState()
    val selectedPdf by syncEngine.selectedGeoPdf.collectAsState()
    val tileState by syncEngine.offlineMapTileStore.downloadState.collectAsState()

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GeoRef - Leitor de GeoPDF & Mapas Offline", fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(14.dp)
        ) {
            // Header: Status Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (syncState) {
                        is SyncState.Syncing -> Color(0xFFFFF9C4)
                        is SyncState.Success -> Color(0xFFE8F5E9)
                        is SyncState.OfflineError -> Color(0xFFFFEBEE)
                        else -> Color(0xFFF5F5F5)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Status da Conexão / Sincronismo", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = when (val state = syncState) {
                                is SyncState.Idle -> "Operando em Campo (Offline-First)"
                                is SyncState.Syncing -> "Sincronizando com backend Go..."
                                is SyncState.Success -> state.message
                                is SyncState.OfflineError -> "Modo Offline: ${state.reason}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray
                        )
                    }

                    Button(
                        onClick = { syncEngine.syncNow("batch-" + UUID.randomUUID().toString().take(8)) },
                        enabled = syncState !is SyncState.Syncing
                    ) {
                        Text("Sincronizar", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section 1: GeoPDF Input & Extraction Action
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("📄 Processar Documento GeoPDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Extrai geocoordenadas embutidas (/GPTS, /BBox) e registra o ponto no app.", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val mockGeoPdf = """
                                        %PDF-1.7
                                        1 0 obj
                                        << /Type /Page /LGIDict << /VP [ << /BBox [-46.6400 -23.5600 -46.6200 -23.5400] /GPTS [-23.5505 -46.6333 -23.5450 -46.6250] >> ] >> >>
                                        endobj
                                    """.trimIndent()
                                    syncEngine.processGeoPdfFile(mockGeoPdf.encodeToByteArray(), "Mapa_Geologico_Campo_2026.pdf")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Importar GeoPDF", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    val mockPdf2 = """
                                        %PDF-1.4
                                        /BBox [-43.2000 -22.9000 -43.1700 -22.8800]
                                        /GPTS [-22.8950 -43.1820]
                                    """.trimIndent()
                                    syncEngine.processGeoPdfFile(mockPdf2.encodeToByteArray(), "Relatorio_Agro_Regiao_Leste.pdf")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("GeoPDF Região Leste", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section 2: Display Extracted Geo-Coordinates & Bounding Box
            selectedPdf?.let { pdfMeta ->
                GeoPdfMetadataCard(pdfMeta, syncEngine, tileState)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Section 3: Georeferenced List
            Text("📍 Pontos Georreferenciados Salvos (${records.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(records) { record ->
                    GeorefRecordCard(record)
                }
            }
        }
    }
}

@Composable
fun GeoPdfMetadataCard(
    pdfMeta: GeoPdfMetadata,
    syncEngine: IdempotentSyncEngine,
    tileState: TileDownloadState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("📍 Geocoordenadas Extraídas do PDF", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
            Text("Arquivo: ${pdfMeta.fileName}", fontSize = 13.sp, fontWeight = FontWeight.Medium)

            Spacer(modifier = Modifier.height(6.dp))

            // Coordinates in Decimal & DMS Format
            Text("• Centro Decimal: ${pdfMeta.centerPoint.latitude}, ${pdfMeta.centerPoint.longitude}", fontSize = 12.sp)
            Text("• Formato DMS: ${pdfMeta.centerPoint.toDmsString()}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("• Projeção: ${pdfMeta.projectionName}", fontSize = 11.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(6.dp))

            // Bounding box area details
            Text("🗺️ Caixa Delimitadora (Bounding Box da Região):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Lat: [${pdfMeta.boundingBox.minLat} à ${pdfMeta.boundingBox.maxLat}]", fontSize = 11.sp)
            Text("Lng: [${pdfMeta.boundingBox.minLng} à ${pdfMeta.boundingBox.maxLng}]", fontSize = 11.sp)

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Map Widget Mock representation
            MapViewWidget(pdfMeta.centerPoint, pdfMeta.fileName)

            Spacer(modifier = Modifier.height(10.dp))

            // Offline Map Saver Button & Download Bar
            Button(
                onClick = { syncEngine.downloadMapTilesForPdfRegion(minZoom = 12, maxZoom = 14) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("💾 Salvar Mapas da Região Offline")
            }

            // Tile Download Status
            when (tileState) {
                is TileDownloadState.Downloading -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { tileState.percentage / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Baixando tiles de mapa da região: ${tileState.current} / ${tileState.total} (${tileState.percentage}%)",
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )
                }
                is TileDownloadState.Completed -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("✅ Região baixada com sucesso! ${tileState.totalDownloaded} tiles armazenados offline.", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                }
                is TileDownloadState.Error -> {
                    Text("❌ Erro no download: ${tileState.message}", fontSize = 11.sp, color = Color.Red)
                }
                else -> {}
            }
        }
    }
}

@Composable
fun MapViewWidget(center: GeoPoint, label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF81D4FA))
            .border(1.dp, Color(0xFF0288D1), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🗺️ Visualizador de Mapa de Campo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF01579B))
            Text("Região: $label", fontSize = 11.sp, color = Color(0xFF0277BD))
            Surface(
                color = Color.Red,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    "📍 Pin: ${center.latitude.toString().take(7)}, ${center.longitude.toString().take(7)}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun GeorefRecordCard(record: GeorefRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = record.name, style = MaterialTheme.typography.titleMedium)
                StatusBadge(record.syncStatus)
            }

            if (record.description.isNotBlank()) {
                Text(text = record.description, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Coordenadas: ${record.latitude}, ${record.longitude} (DMS: ${GeoPoint(record.latitude, record.longitude).toDmsString()})",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun StatusBadge(status: SyncStatus) {
    val (label, bgColor) = when (status) {
        SyncStatus.PENDING_CREATE -> "PENDENTE (Criação)" to Color(0xFFFF9800)
        SyncStatus.PENDING_UPDATE -> "PENDENTE (Atualização)" to Color(0xFF2196F3)
        SyncStatus.PENDING_DELETE -> "PENDENTE (Remoção)" to Color(0xFFF44336)
        SyncStatus.SYNCED -> "SINCRONIZADO" to Color(0xFF4CAF50)
        SyncStatus.FAILED -> "FALHA" to Color(0xFF9E9E9E)
    }

    Surface(
        color = bgColor,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp,
            color = Color.White
        )
    }
}
