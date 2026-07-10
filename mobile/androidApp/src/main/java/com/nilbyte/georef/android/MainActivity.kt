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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
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
                    GisMultiTabApp(syncEngine)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GisMultiTabApp(syncEngine: IdempotentSyncEngine) {
    var selectedTabIndex by remember { mutableIntStateOf(1) } // Default: Center Tab (Mapa Mundi)

    val gisLayers by syncEngine.gisLayersFlow.collectAsState()
    val activeGisLayer by syncEngine.selectedGisLayer.collectAsState()
    val syncState by syncEngine.syncState.collectAsState()
    val tileState by syncEngine.offlineMapTileStore.downloadState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GeoRef GIS - Sistema de Mapas", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                // Tab 0: Esquerda - Todos os Imports
                NavigationBarItem(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Importações") },
                    label = { Text("📁 Importações (${gisLayers.size})", fontSize = 11.sp) }
                )
                // Tab 1: Centro - Mapa Mundi Principal
                NavigationBarItem(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = "Mapa Mundi") },
                    label = { Text("🌍 Mapa Mundi", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                // Tab 2: Direita - Importar Dados
                NavigationBarItem(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Importar") },
                    label = { Text("📥 Importar Dados", fontSize = 11.sp) }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (selectedTabIndex) {
                0 -> ImportsTabScreen(
                    gisLayers = gisLayers,
                    activeLayer = activeGisLayer,
                    onSelectLayer = { layer ->
                        syncEngine.selectGisLayerForMapOverlay(layer)
                        selectedTabIndex = 1 // Auto switch to World Map tab on selection!
                    }
                )
                1 -> WorldMapTabScreen(
                    activeLayer = activeGisLayer,
                    syncEngine = syncEngine,
                    tileState = tileState
                )
                2 -> ImportDataTabScreen(
                    syncEngine = syncEngine,
                    syncState = syncState,
                    onImportSuccess = {
                        selectedTabIndex = 1 // Auto switch to World Map tab on new import!
                    }
                )
            }
        }
    }
}

// ==========================================
// ABA 1 (ESQUERDA): Todos os Mapas Importados
// ==========================================
@Composable
fun ImportsTabScreen(
    gisLayers: List<GisLayer>,
    activeLayer: GisLayer?,
    onSelectLayer: (GisLayer) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Text("📁 Meus Mapas e Camadas Importadas (${gisLayers.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Toque em qualquer mapa para centralizá-lo e exibi-lo sobreposto no Mapa Mundi.", fontSize = 12.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(12.dp))

        if (gisLayers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nenhum mapa importado ainda.", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                    Text("Vá para a aba 'Importar Dados' para carregar um GeoPDF, GeoJSON ou KML.", fontSize = 12.sp, color = Color.LightGray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(gisLayers) { layer ->
                    GisLayerCard(
                        layer = layer,
                        isSelected = activeLayer?.id == layer.id,
                        onClick = { onSelectLayer(layer) }
                    )
                }
            }
        }
    }
}

// ==========================================
// ABA 2 (CENTRO/PRINCIPAL): Mapa Mundi Interactive View
// ==========================================
@Composable
fun WorldMapTabScreen(
    activeLayer: GisLayer?,
    syncEngine: IdempotentSyncEngine,
    tileState: TileDownloadState
) {
    var isSatelliteMode by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Map Controls Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🌍 Mapa Mundi Global GIS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FilterChip(
                selected = isSatelliteMode,
                onClick = { isSatelliteMode = !isSatelliteMode },
                label = { Text(if (isSatelliteMode) "🛰️ Satélite" else "🗺️ Vetorial") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive World Map Widget
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = if (isSatelliteMode) Color(0xFF1C2833) else Color(0xFFE0F7FA)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, Color(0xFF00838F), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = "Pin Mapa",
                        tint = if (activeLayer != null) Color.Red else Color.Gray,
                        modifier = Modifier.size(40.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (activeLayer != null) {
                        Surface(
                            color = Color(0xFF004D40),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "SOBREPOSIÇÃO ATIVA: ${activeLayer.name}",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Formato: ${activeLayer.fileType} | PostGIS SRID 4326",
                                    fontSize = 10.sp,
                                    color = Color(0xFF80CBC4)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "📍 Coordenadas: ${activeLayer.centerLat}, ${activeLayer.centerLng}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSatelliteMode) Color.White else Color(0xFF006064)
                        )
                        Text(
                            text = "Formato DMS: ${activeLayer.centerPoint.toDmsString()}",
                            fontSize = 11.sp,
                            color = if (isSatelliteMode) Color.LightGray else Color.DarkGray
                        )
                        Text(
                            text = "Bounding Box: [${activeLayer.minLat}, ${activeLayer.minLng}] à [${activeLayer.maxLat}, ${activeLayer.maxLng}]",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    } else {
                        Text(
                            "Visualizando Mapa Mundi sem sobreposição.",
                            fontSize = 13.sp,
                            color = if (isSatelliteMode) Color.LightGray else Color.DarkGray
                        )
                        Text(
                            "Selecione um mapa na aba 'Importações' para visualizar sua área sobreposta.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Offline Tile Saver
        if (activeLayer != null) {
            Button(
                onClick = { syncEngine.downloadMapTilesForPdfRegion(minZoom = 12, maxZoom = 14) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("💾 Salvar Mapa desta Região Offline", fontSize = 12.sp)
            }

            when (tileState) {
                is TileDownloadState.Downloading -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { tileState.percentage / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Baixando tiles offline: ${tileState.current} / ${tileState.total} (${tileState.percentage}%)", fontSize = 10.sp)
                }
                is TileDownloadState.Completed -> {
                    Text("✅ Tiles de mapa da região salvos no dispositivo (${tileState.totalDownloaded} tiles).", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                }
                else -> {}
            }
        }
    }
}

// ==========================================
// ABA 3 (DIREITA): Importar Dados & PostGIS
// ==========================================
@Composable
fun ImportDataTabScreen(
    syncEngine: IdempotentSyncEngine,
    syncState: SyncState,
    onImportSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var newPointName by remember { mutableStateOf("") }
    var newPointLat by remember { mutableStateOf("-23.5505") }
    var newPointLng by remember { mutableStateOf("-46.6333") }

    // Native File Pickers
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val bytes = readBytesFromUri(context, it)
                val fileName = getFileNameFromUri(context, it) ?: "Mapa_GeoPDF.pdf"
                if (bytes != null && bytes.isNotEmpty()) {
                    syncEngine.processGeoPdfFile(bytes, fileName)
                    onImportSuccess()
                }
            }
        }
    }

    val genericGisPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val bytes = readBytesFromUri(context, it)
                val fileName = getFileNameFromUri(context, it) ?: "Export_GIS.geojson"
                if (bytes != null && bytes.isNotEmpty()) {
                    syncEngine.importGisDocument(bytes, fileName)
                    onImportSuccess()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Text("📥 Importar Dados e Arquivos GIS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(10.dp))

        // File Pickers Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("📄 Importar Arquivo do Dispositivo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Selecione arquivos GeoPDF, GeoJSON, KML ou GeoTIFF salvos no celular.", fontSize = 11.sp)

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { pdfPickerLauncher.launch("application/pdf") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📄 Selecionar GeoPDF (.pdf)")
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = { genericGisPickerLauncher.launch("*/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🗺️ Selecionar GeoJSON / KML / GeoTIFF")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Manual Point Creator
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("📍 Criar Ponto de Campo Manualmente", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = newPointName,
                    onValueChange = { newPointName = it },
                    label = { Text("Nome do Ponto") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newPointLat,
                        onValueChange = { newPointLat = it },
                        label = { Text("Latitude") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = newPointLng,
                        onValueChange = { newPointLng = it },
                        label = { Text("Longitude") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (newPointName.isNotBlank()) {
                            scope.launch {
                                syncEngine.createFieldRecord(
                                    id = UUID.randomUUID().toString(),
                                    name = newPointName,
                                    description = "Ponto criado manualmente",
                                    latitude = newPointLat.toDoubleOrNull() ?: -23.5505,
                                    longitude = newPointLng.toDoubleOrNull() ?: -46.6333
                                )
                                newPointName = ""
                                onImportSuccess()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Salvar Ponto Localmente")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // PostGIS Server Connection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Servidor PostGIS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = when (syncState) {
                            is SyncState.Idle -> "Conectado ao Go Backend (:8085)"
                            is SyncState.Syncing -> "Sincronizando..."
                            is SyncState.Success -> "Sincronizado com sucesso!"
                            is SyncState.OfflineError -> "Offline"
                        },
                        fontSize = 11.sp
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
    }
}

// Helpers
private fun readBytesFromUri(context: Context, uri: Uri): ByteArray? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        null
    }
}

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
