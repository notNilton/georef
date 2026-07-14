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
import androidx.compose.ui.viewinterop.AndroidView
import com.nilbyte.georef.data.local.TileDownloadState
import com.nilbyte.georef.domain.model.GisFileType
import com.nilbyte.georef.domain.model.GisLayer
import com.nilbyte.georef.sync.IdempotentSyncEngine
import com.nilbyte.georef.sync.SyncState
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView as OsmMapView
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.Polygon as OsmPolygon
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val syncEngine by lazy {
        IdempotentSyncEngine(clientId = "android-field-" + UUID.randomUUID().toString().take(8))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(applicationContext, applicationContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00E676),
                    secondary = Color(0xFF29B6F6),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    surfaceVariant = Color(0xFF252525)
                )
            ) {
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
    var selectedTabIndex by remember { mutableIntStateOf(1) }

    val gisLayers by syncEngine.gisLayersFlow.collectAsState()
    val activeGisLayer by syncEngine.selectedGisLayer.collectAsState()
    val syncState by syncEngine.syncState.collectAsState()
    val tileState by syncEngine.offlineMapTileStore.downloadState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GeoRef", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E)
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1E1E1E)) {
                NavigationBarItem(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    icon = { Icon(Icons.Default.List, contentDescription = null, tint = if (selectedTabIndex == 0) Color(0xFF00E676) else Color.Gray) },
                    label = { Text("Camadas", fontSize = 11.sp, color = if (selectedTabIndex == 0) Color.White else Color.Gray) }
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = if (selectedTabIndex == 1) Color(0xFF00E676) else Color.Gray) },
                    label = { Text("Mapa", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selectedTabIndex == 1) Color.White else Color.Gray) }
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    icon = { Icon(Icons.Default.Add, contentDescription = null, tint = if (selectedTabIndex == 2) Color(0xFF00E676) else Color.Gray) },
                    label = { Text("Importar", fontSize = 11.sp, color = if (selectedTabIndex == 2) Color.White else Color.Gray) }
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
                        selectedTabIndex = 1
                    }
                )
                1 -> WorldMapOpenStreetMapTabScreen(
                    activeLayer = activeGisLayer,
                    syncEngine = syncEngine,
                    tileState = tileState
                )
                2 -> ImportDataTabScreen(
                    syncEngine = syncEngine,
                    syncState = syncState,
                    onImportSuccess = {
                        selectedTabIndex = 1
                    }
                )
            }
        }
    }
}

@Composable
fun ImportsTabScreen(
    gisLayers: List<GisLayer>,
    activeLayer: GisLayer?,
    onSelectLayer: (GisLayer) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Camadas Salvas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)

        Spacer(modifier = Modifier.height(12.dp))

        if (gisLayers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma camada importada.", fontSize = 13.sp, color = Color.Gray)
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

@Composable
fun WorldMapOpenStreetMapTabScreen(
    activeLayer: GisLayer?,
    syncEngine: IdempotentSyncEngine,
    tileState: TileDownloadState
) {
    var isSatelliteMode by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Visualizador GIS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
            FilterChip(
                selected = isSatelliteMode,
                onClick = { isSatelliteMode = !isSatelliteMode },
                label = { Text(if (isSatelliteMode) "Satélite" else "Vetor", fontSize = 11.sp) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
        ) {
            NativeOsmMapView(
                activeLayer = activeLayer,
                isSatelliteMode = isSatelliteMode
            )
        }

        if (activeLayer != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(activeLayer.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        FileTypeBadge(activeLayer.fileType)
                    }
                    Text("Lat: ${activeLayer.centerLat} | Lng: ${activeLayer.centerLng}", fontSize = 11.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { syncEngine.downloadMapTilesForPdfRegion(minZoom = 12, maxZoom = 14) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Baixar Tiles Offline", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    when (tileState) {
                        is TileDownloadState.Downloading -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(progress = { tileState.percentage / 100f }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF00E676))
                        }
                        is TileDownloadState.Completed -> {
                            Text("Tiles salvos (${tileState.totalDownloaded}).", fontSize = 10.sp, color = Color(0xFF00E676))
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
fun NativeOsmMapView(
    activeLayer: GisLayer?,
    isSatelliteMode: Boolean
) {
    val lat = activeLayer?.centerLat ?: -23.5505
    val lng = activeLayer?.centerLng ?: -46.6333
    val minLat = activeLayer?.minLat ?: (lat - 0.02)
    val minLng = activeLayer?.minLng ?: (lng - 0.02)
    val maxLat = activeLayer?.maxLat ?: (lat + 0.02)
    val maxLng = activeLayer?.maxLng ?: (lng + 0.02)
    val layerName = activeLayer?.name ?: "Mapa"

    AndroidView(
        factory = { context ->
            OsmMapView(context).apply {
                setTileSource(if (isSatelliteMode) TileSourceFactory.USGS_SAT else TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(if (activeLayer != null) 14.0 else 4.0)
                controller.setCenter(OsmGeoPoint(lat, lng))
            }
        },
        update = { mapView ->
            mapView.setTileSource(if (isSatelliteMode) TileSourceFactory.USGS_SAT else TileSourceFactory.MAPNIK)
            mapView.overlays.clear()

            val centerPt = OsmGeoPoint(lat, lng)
            mapView.controller.setCenter(centerPt)
            mapView.controller.setZoom(if (activeLayer != null) 14.0 else 4.0)

            if (activeLayer != null) {
                val polygon = OsmPolygon(mapView)
                polygon.fillPaint.color = android.graphics.Color.parseColor("#3000E676")
                polygon.outlinePaint.color = android.graphics.Color.parseColor("#00E676")
                polygon.outlinePaint.strokeWidth = 4f
                val pts = listOf(
                    OsmGeoPoint(minLat, minLng),
                    OsmGeoPoint(maxLat, minLng),
                    OsmGeoPoint(maxLat, maxLng),
                    OsmGeoPoint(minLat, maxLng)
                )
                polygon.setPoints(pts)
                mapView.overlays.add(polygon)

                val marker = OsmMarker(mapView)
                marker.position = centerPt
                marker.title = layerName
                marker.setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                mapView.overlays.add(marker)
            }

            mapView.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}

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

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val bytes = readBytesFromUri(context, it)
                val fileName = getFileNameFromUri(context, it) ?: "Mapa.pdf"
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
                val fileName = getFileNameFromUri(context, it) ?: "Dados.geojson"
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
            .padding(16.dp)
    ) {
        Text("Importar Dados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Seletor de Arquivos", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { pdfPickerLauncher.launch("application/pdf") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252525)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("GeoPDF (.pdf)", color = Color.White)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = { genericGisPickerLauncher.launch("*/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252525)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("GeoJSON / KML / GeoTIFF", color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Ponto Manual", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newPointName,
                    onValueChange = { newPointName = it },
                    label = { Text("Nome") },
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
                                    description = "Ponto manual",
                                    latitude = newPointLat.toDoubleOrNull() ?: -23.5505,
                                    longitude = newPointLng.toDoubleOrNull() ?: -46.6333
                                )
                                newPointName = ""
                                onImportSuccess()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Salvar Ponto", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("PostGIS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        text = when (syncState) {
                            is SyncState.Idle -> "Pronto"
                            is SyncState.Syncing -> "Sincronizando"
                            is SyncState.Success -> "Sincronizado"
                            is SyncState.OfflineError -> "Offline"
                        },
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                Button(
                    onClick = { syncEngine.syncNow("batch-" + UUID.randomUUID().toString().take(8)) },
                    enabled = syncState !is SyncState.Syncing,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252525))
                ) {
                    Text("Sincronizar", fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}

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
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) Color(0xFF00E676) else Color.Transparent,
                shape = CardDefaults.shape
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = layer.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                FileTypeBadge(layer.fileType)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("${layer.centerLat}, ${layer.centerLng}", fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun FileTypeBadge(type: GisFileType) {
    val label = when (type) {
        GisFileType.GEOPDF -> "GEOPDF"
        GisFileType.GEOJSON -> "GEOJSON"
        GisFileType.KML -> "KML"
        GisFileType.GEOTIFF -> "GEOTIFF"
    }

    Surface(color = Color(0xFF252525), shape = RoundedCornerShape(4.dp)) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 9.sp,
            color = Color.LightGray,
            fontWeight = FontWeight.Bold
        )
    }
}
