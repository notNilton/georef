package com.nilbyte.georef.android

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nilbyte.georef.data.local.TileDownloadState
import com.nilbyte.georef.domain.model.GeoPoint
import com.nilbyte.georef.domain.model.GisFileType
import com.nilbyte.georef.domain.model.GisLayer
import com.nilbyte.georef.domain.model.SyncStatus
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
                    GisInteractiveApp(syncEngine)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GisInteractiveApp(syncEngine: IdempotentSyncEngine) {
    val context = LocalContext.current
    val gisLayers by syncEngine.gisLayersFlow.collectAsState()
    val activeGisLayer by syncEngine.selectedGisLayer.collectAsState()
    val syncState by syncEngine.syncState.collectAsState()
    val tileState by syncEngine.offlineMapTileStore.downloadState.collectAsState()

    val scope = rememberCoroutineScope()

    // Native Android PDF File Picker Launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val bytes = readBytesFromUri(context, it)
                val fileName = getFileNameFromUri(context, it) ?: "Mapa_GeoPDF.pdf"
                if (bytes != null && bytes.isNotEmpty()) {
                    syncEngine.processGeoPdfFile(bytes, fileName)
                }
            }
        }
    }

    // Native Android Generic File Picker Launcher (.pdf, .geojson, .json, .kml)
    val genericGisPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val bytes = readBytesFromUri(context, it)
                val fileName = getFileNameFromUri(context, it) ?: "Export_GIS.geojson"
                if (bytes != null && bytes.isNotEmpty()) {
                    syncEngine.importGisDocument(bytes, fileName)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GeoRef - Leitor PDF & Interação GIS", fontSize = 17.sp) },
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
                .padding(12.dp)
        ) {
            // Connection & PostGIS Status Header
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
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PostGIS & Sincronização em Campo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = when (val state = syncState) {
                                is SyncState.Idle -> "Pronto (Camadas preparadas para PostGIS)"
                                is SyncState.Syncing -> "Enviando geometrias ao PostgreSQL PostGIS..."
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
                        Text("Sincronizar", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Section 1: Interactive Global GIS Map View
            Text("🌐 Mapa Global GIS (Posição Sobreposta)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            GlobalGisMapViewWidget(activeGisLayer, syncEngine, tileState)

            Spacer(modifier = Modifier.height(10.dp))

            // Section 2: Native Android System File Picker for PDF / GIS Files
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("📂 Abrir e Importar Arquivo PDF / GIS do Aparelho", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Selecione um arquivo PDF ou mapa exportado salvo no seu celular.", fontSize = 11.sp)

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { pdfPickerLauncher.launch("application/pdf") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📄 Selecionar PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { genericGisPickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🗺️ Selecionar GIS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Demo / Test Simulation Row
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val mockPdf = "%PDF-1.7 /BBox [-46.6400 -23.5600 -46.6200 -23.5400] /GPTS [-23.5505 -46.6333]"
                                    syncEngine.processGeoPdfFile(mockPdf.encodeToByteArray(), "Demo_GeoPDF.pdf")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Demo PDF", fontSize = 10.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val mockGeoJson = """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[-43.1820,-22.8950]}}]}"""
                                    syncEngine.importGisDocument(mockGeoJson.encodeToByteArray(), "Demo_GeoJSON.geojson")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Demo GeoJSON", fontSize = 10.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Section 3: List of Imported Map Layers (Click to overlay)
            Text("🗺️ Mapas de Região Salvos (${gisLayers.size}) - Toque para sobrepor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(gisLayers) { layer ->
                    GisLayerCard(
                        layer = layer,
                        isSelected = activeGisLayer?.id == layer.id,
                        onClick = { syncEngine.selectGisLayerForMapOverlay(layer) }
                    )
                }
            }
        }
    }
}

// Helper function to read byte array from Android ContentResolver URI
private fun readBytesFromUri(context: Context, uri: Uri): ByteArray? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.readBytes()
        }
    } catch (e: Exception) {
        null
    }
}

// Helper function to get original filename from ContentResolver URI
private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var fileName: String? = null
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                fileName = it.getString(nameIndex)
            }
        }
    }
    return fileName
}

@Composable
fun GlobalGisMapViewWidget(
    activeLayer: GisLayer?,
    syncEngine: IdempotentSyncEngine,
    tileState: TileDownloadState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F7FA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF80DEEA))
                    .border(1.dp, Color(0xFF00838F), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                    Text("🌍 VISUALIZADOR GLOBAL GIS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF006064))

                    if (activeLayer != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = Color(0xFF004D40),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "SOBREPOSIÇÃO ATIVA: ${activeLayer.name} (${activeLayer.fileType})",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "📍 Centro: ${activeLayer.centerLat}, ${activeLayer.centerLng} (DMS: ${activeLayer.centerPoint.toDmsString()})",
                            fontSize = 10.sp,
                            color = Color(0xFF004D40)
                        )
                        Text(
                            text = "Bounding Box: [${activeLayer.minLat}, ${activeLayer.minLng}] à [${activeLayer.maxLat}, ${activeLayer.maxLng}]",
                            fontSize = 9.sp,
                            color = Color.DarkGray
                        )
                    } else {
                        Text("Nenhum mapa selecionado. Escolha um PDF ou mapa abaixo.", fontSize = 11.sp, color = Color.DarkGray)
                    }
                }
            }

            if (activeLayer != null) {
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { syncEngine.downloadMapTilesForPdfRegion(minZoom = 12, maxZoom = 14) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("💾 Salvar Tiles de Mapa da Região Offline", fontSize = 11.sp)
                }

                when (tileState) {
                    is TileDownloadState.Downloading -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(progress = { tileState.percentage / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("Baixando tiles offline: ${tileState.current} / ${tileState.total} (${tileState.percentage}%)", fontSize = 10.sp)
                    }
                    is TileDownloadState.Completed -> {
                        Text("✅ Tiles de mapa salvos localmente no dispositivo (${tileState.totalDownloaded} tiles).", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun GisLayerCard(
    layer: GisLayer,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Color(0xFF0288D1) else Color.Transparent,
                shape = CardDefaults.shape
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE1F5FE) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FileTypeBadge(layer.fileType)
                    Text(text = layer.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                if (isSelected) {
                    Text("📍 EXIBINDO SOBREPOSTO", fontSize = 10.sp, color = Color(0xFF0288D1), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Centro: ${layer.centerLat}, ${layer.centerLng}", fontSize = 11.sp, color = Color.Gray)
            Text("BBox: [${layer.minLat}, ${layer.minLng}] à [${layer.maxLat}, ${layer.maxLng}]", fontSize = 10.sp, color = Color.LightGray)
        }
    }
}

@Composable
fun FileTypeBadge(type: GisFileType) {
    val (label, bgColor) = when (type) {
        GisFileType.GEOPDF -> "GeoPDF" to Color(0xFFD32F2F)
        GisFileType.GEOJSON -> "GeoJSON" to Color(0xFF00796B)
        GisFileType.KML -> "KML" to Color(0xFF512DA8)
        GisFileType.GEOTIFF -> "GeoTIFF" to Color(0xFFE65100)
    }

    Surface(color = bgColor, shape = RoundedCornerShape(4.dp)) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 9.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}
