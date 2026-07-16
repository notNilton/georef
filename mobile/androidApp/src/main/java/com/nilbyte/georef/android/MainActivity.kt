package com.nilbyte.georef.android

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
            val context = LocalContext.current
            val isDark = isSystemInDarkTheme()

            val colorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                isDark -> darkColorScheme(
                    primary = Color(0xFF00E676),
                    surface = Color(0xFF1E1E1E),
                    background = Color(0xFF121212)
                )
                else -> lightColorScheme(
                    primary = Color(0xFF00897B),
                    surface = Color(0xFFF5F5F5),
                    background = Color(0xFFFFFFFF)
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
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
    var showFabMenuSheet by remember { mutableStateOf(false) }

    val gisLayers by syncEngine.gisLayersFlow.collectAsState()
    val activeGisLayers by syncEngine.activeGisLayers.collectAsState()
    val syncState by syncEngine.syncState.collectAsState()
    val tileState by syncEngine.offlineMapTileStore.downloadState.collectAsState()

    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.clip(CircleShape)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedTabIndex = 0 }) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = null,
                                tint = if (selectedTabIndex == 0) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }

                        IconButton(onClick = { selectedTabIndex = 1 }) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (selectedTabIndex == 1) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }

                        IconButton(onClick = { selectedTabIndex = 2 }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = if (selectedTabIndex == 2) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    }
                }
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
                    activeLayers = activeGisLayers,
                    onToggleActive = { id -> syncEngine.toggleLayerActive(id) },
                    onRemoveLayer = { id -> syncEngine.removeLayer(id) },
                    onSelectLayer = { _ ->
                        selectedTabIndex = 1
                    }
                )
                1 -> WorldMapOpenStreetMapTabScreen(
                    activeLayers = activeGisLayers,
                    syncEngine = syncEngine,
                    tileState = tileState,
                    onOpenFabMenu = { showFabMenuSheet = true }
                )
                2 -> ImportDataTabScreen(
                    syncEngine = syncEngine,
                    syncState = syncState,
                    onImportSuccess = {
                        selectedTabIndex = 1
                    }
                )
            }

            // Layer Management Bottom Sheet opened by FAB (+)
            if (showFabMenuSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showFabMenuSheet = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Gerenciar Camadas Sobrepostas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { showFabMenuSheet = false }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (gisLayers.isEmpty()) {
                            Text("Nenhum mapa cadastrado.", fontSize = 13.sp, color = Color.Gray)
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 300.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(gisLayers) { layer ->
                                    val isOverlayOn = activeGisLayers.any { it.id == layer.id }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(layer.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                Text("${layer.fileType} | Lat: ${layer.centerLat}", fontSize = 10.sp, color = Color.Gray)
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Switch(
                                                    checked = isOverlayOn,
                                                    onCheckedChange = { syncEngine.toggleLayerActive(layer.id) }
                                                )
                                                IconButton(onClick = { syncEngine.removeLayer(layer.id) }) {
                                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImportsTabScreen(
    gisLayers: List<GisLayer>,
    activeLayers: List<GisLayer>,
    onToggleActive: (String) -> Unit,
    onRemoveLayer: (String) -> Unit,
    onSelectLayer: (GisLayer) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
        Text("Camadas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))

        if (gisLayers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma camada salva", fontSize = 14.sp, color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(gisLayers) { layer ->
                    val isOverlayOn = activeLayers.any { it.id == layer.id }
                    GisLayerItemCard(
                        layer = layer,
                        isOverlayOn = isOverlayOn,
                        onToggle = { onToggleActive(layer.id) },
                        onRemove = { onRemoveLayer(layer.id) },
                        onClick = { onSelectLayer(layer) }
                    )
                }
            }
        }
    }
}

@Composable
fun WorldMapOpenStreetMapTabScreen(
    activeLayers: List<GisLayer>,
    syncEngine: IdempotentSyncEngine,
    tileState: TileDownloadState,
    onOpenFabMenu: () -> Unit
) {
    var isSatelliteMode by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fullscreen Native Multi-Layer Map Engine
        NativeOsmMapView(
            activeLayers = activeLayers,
            isSatelliteMode = isSatelliteMode
        )

        // Floating Top Header Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = if (activeLayers.isNotEmpty()) "${activeLayers.size} Camadas Sobrepostas" else "GeoRef",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shadowElevation = 4.dp,
                modifier = Modifier.clickable { isSatelliteMode = !isSatelliteMode }
            ) {
                Text(
                    text = if (isSatelliteMode) "Satélite" else "Vetor",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Floating Action Button (FAB +) to manage stacked overlays
        FloatingActionButton(
            onClick = onOpenFabMenu,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.Black,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 90.dp, end = 20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
        }
    }
}

/**
 * 100% Native OpenStreetMap Engine supporting MULTIPLE STACKED VECTOR LAYERS.
 * Iterates through all enabled active layers and draws every polygon bounding box & pin!
 */
@Composable
fun NativeOsmMapView(
    activeLayers: List<GisLayer>,
    isSatelliteMode: Boolean
) {
    val defaultLat = -23.5505
    val defaultLng = -46.6333

    val targetLat = activeLayers.firstOrNull()?.centerLat ?: defaultLat
    val targetLng = activeLayers.firstOrNull()?.centerLng ?: defaultLng

    val strokeColors = listOf("#00E676", "#29B6F6", "#FF9100", "#E040FB", "#FFD600")
    val fillColors = listOf("#3000E676", "#3029B6F6", "#30FF9100", "#30E040FB", "#30FFD600")

    AndroidView(
        factory = { context ->
            OsmMapView(context).apply {
                setTileSource(if (isSatelliteMode) TileSourceFactory.USGS_SAT else TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(if (activeLayers.isNotEmpty()) 14.0 else 4.0)
                controller.setCenter(OsmGeoPoint(targetLat, targetLng))
            }
        },
        update = { mapView ->
            mapView.setTileSource(if (isSatelliteMode) TileSourceFactory.USGS_SAT else TileSourceFactory.MAPNIK)
            mapView.overlays.clear()

            if (activeLayers.isNotEmpty()) {
                mapView.controller.setCenter(OsmGeoPoint(targetLat, targetLng))

                // Render ALL active stacked layers simultaneously!
                activeLayers.forEachIndexed { index, layer ->
                    val colorIdx = index % strokeColors.size
                    val sColor = strokeColors[colorIdx]
                    val fColor = fillColors[colorIdx]

                    val polygon = OsmPolygon(mapView)
                    polygon.fillPaint.color = android.graphics.Color.parseColor(fColor)
                    polygon.outlinePaint.color = android.graphics.Color.parseColor(sColor)
                    polygon.outlinePaint.strokeWidth = 5f

                    val pts = listOf(
                        OsmGeoPoint(layer.minLat, layer.minLng),
                        OsmGeoPoint(layer.maxLat, layer.minLng),
                        OsmGeoPoint(layer.maxLat, layer.maxLng),
                        OsmGeoPoint(layer.minLat, layer.maxLng)
                    )
                    polygon.setPoints(pts)
                    mapView.overlays.add(polygon)

                    val marker = OsmMarker(mapView)
                    marker.position = OsmGeoPoint(layer.centerLat, layer.centerLng)
                    marker.title = layer.name
                    marker.setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                    mapView.overlays.add(marker)
                }
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
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        Text("Importar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Arquivos", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { pdfPickerLauncher.launch("application/pdf") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("GeoPDF (.pdf)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { genericGisPickerLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("GeoJSON / KML / SHP / GeoTIFF")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Ponto Manual", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)

                Spacer(modifier = Modifier.height(10.dp))

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

                Spacer(modifier = Modifier.height(10.dp))

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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Salvar Ponto")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("PostGIS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

                TextButton(
                    onClick = { syncEngine.syncNow("batch-" + UUID.randomUUID().toString().take(8)) },
                    enabled = syncState !is SyncState.Syncing
                ) {
                    Text("Sincronizar", fontSize = 11.sp)
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
fun GisLayerItemCard(
    layer: GisLayer,
    isOverlayOn: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isOverlayOn) 6.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = if (isOverlayOn) 1.5.dp else 0.dp,
                color = if (isOverlayOn) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = layer.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FileTypeBadge(layer.fileType)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("${layer.centerLat}, ${layer.centerLng}", fontSize = 11.sp, color = Color.Gray)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = isOverlayOn,
                    onCheckedChange = { onToggle() }
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(20.dp))
                }
            }
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
        GisFileType.SHAPEFILE -> "SHAPEFILE"
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
    }
}
