package com.nilbyte.georef.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import com.nilbyte.georef.domain.model.GisFileType
import com.nilbyte.georef.domain.model.GisLayer
import com.nilbyte.georef.sync.IdempotentSyncEngine
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView as OsmMapView
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.Polygon as OsmPolygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
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
    var selectedScreen by remember { mutableIntStateOf(1) } // 0: Lista de Camadas, 1: Mapa Global
    var showRegisteredLayerPickerSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val gisLayers by syncEngine.gisLayersFlow.collectAsState()
    val activeGisLayers by syncEngine.activeGisLayers.collectAsState()

    // Request Location Permissions launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Launcher for importing NEW layer files into catalog
    val genericGisPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val bytes = readBytesFromUri(context, it)
                val fileName = getFileNameFromUri(context, it) ?: "Camada.geojson"
                if (bytes != null && bytes.isNotEmpty()) {
                    syncEngine.importGisDocument(bytes, fileName)
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            // Floating 3-Button Navigation Bar with Safe Window Insets
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
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
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Botão da Esquerda: Lista de Camadas
                        IconButton(onClick = { selectedScreen = 0 }) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = null,
                                tint = if (selectedScreen == 0) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }

                        // 2. Botão Central (+): Adicionar Camadas ao Mapa (Das Camadas Já Cadastradas)
                        IconButton(onClick = {
                            showRegisteredLayerPickerSheet = true
                        }) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color.Black
                                    )
                                }
                            }
                        }

                        // 3. Botão da Direita: Mapa Global
                        IconButton(onClick = { selectedScreen = 1 }) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (selectedScreen == 1) MaterialTheme.colorScheme.primary else Color.Gray
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
            when (selectedScreen) {
                0 -> RegisteredLayersListScreen(
                    gisLayers = gisLayers,
                    activeLayers = activeGisLayers,
                    onToggleActive = { id -> syncEngine.toggleLayerActive(id) },
                    onRemoveLayer = { id -> syncEngine.removeLayer(id) },
                    onImportNewLayerClick = {
                        genericGisPickerLauncher.launch("*/*")
                    },
                    onSelectLayer = { _ ->
                        selectedScreen = 1
                    }
                )
                1 -> WorldMapScreen(
                    activeLayers = activeGisLayers,
                    onOpenLayerToggleList = {
                        showRegisteredLayerPickerSheet = true
                    },
                    onRequestLocationPermission = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                )
            }

            // BottomSheet with Toggle list of Registered Layers
            if (showRegisteredLayerPickerSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showRegisteredLayerPickerSheet = false },
                    windowInsets = WindowInsets.navigationBars
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Camadas Cadastradas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { showRegisteredLayerPickerSheet = false }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }

                        Text("Selecione quais camadas deseja sobrepor no mapa:", fontSize = 12.sp, color = Color.Gray)

                        Spacer(modifier = Modifier.height(14.dp))

                        if (gisLayers.isEmpty()) {
                            Text("Nenhuma camada cadastrada. Vá até a lista para importar.", fontSize = 13.sp, color = Color.Gray)
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 320.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(gisLayers) { layer ->
                                    val isOverlayOn = activeGisLayers.any { it.id == layer.id }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                syncEngine.toggleLayerActive(layer.id)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Text(layer.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    FileTypeBadge(layer.fileType)
                                                }
                                                Text("Lat: ${layer.centerLat}, Lng: ${layer.centerLng}", fontSize = 10.sp, color = Color.Gray)
                                            }

                                            Switch(
                                                checked = isOverlayOn,
                                                onCheckedChange = { syncEngine.toggleLayerActive(layer.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = {
                                showRegisteredLayerPickerSheet = false
                                genericGisPickerLauncher.launch("*/*")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Importar Novo Arquivo (+)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RegisteredLayersListScreen(
    gisLayers: List<GisLayer>,
    activeLayers: List<GisLayer>,
    onToggleActive: (String) -> Unit,
    onRemoveLayer: (String) -> Unit,
    onImportNewLayerClick: () -> Unit,
    onSelectLayer: (GisLayer) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Camadas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            Button(
                onClick = onImportNewLayerClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Adicionar Camadas", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (gisLayers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma camada cadastrada", fontSize = 14.sp, color = Color.Gray)
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
fun WorldMapScreen(
    activeLayers: List<GisLayer>,
    onOpenLayerToggleList: () -> Unit,
    onRequestLocationPermission: () -> Unit
) {
    var isSatelliteMode by remember { mutableStateOf(false) }
    var triggerFocusLocationSignal by remember { mutableLongStateOf(0L) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fullscreen Native Multi-Layer Map Engine with Real-Time GPS User Location Overlay
        NativeOsmMapView(
            activeLayers = activeLayers,
            isSatelliteMode = isSatelliteMode,
            triggerFocusLocationSignal = triggerFocusLocationSignal
        )

        // Floating Top Left Header: Active Layers Chip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left: Interactive Layer Count Chip
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 4.dp,
                modifier = Modifier.clickable { onOpenLayerToggleList() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (activeLayers.isNotEmpty()) "${activeLayers.size} Camadas" else "GeoRef (0 Camadas)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("▼", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Right Column: 1. Vetor/Satélite Toggle + 2. Focar na Minha Localização (Directly below 'Vetor')
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button 1: Vetor / Satélite
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
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

                // Button 2: Focar na Minha Localização (Located directly below 'Vetor')
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                    modifier = Modifier.clickable {
                        onRequestLocationPermission()
                        triggerFocusLocationSignal = System.currentTimeMillis()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Focar Minha Localização",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NativeOsmMapView(
    activeLayers: List<GisLayer>,
    isSatelliteMode: Boolean,
    triggerFocusLocationSignal: Long
) {
    val context = LocalContext.current

    val defaultLat = -23.5505
    val defaultLng = -46.6333

    val targetLat = activeLayers.firstOrNull()?.centerLat ?: defaultLat
    val targetLng = activeLayers.firstOrNull()?.centerLng ?: defaultLng

    val strokeColors = listOf("#00E676", "#29B6F6", "#FF9100", "#E040FB", "#FFD600")
    val fillColors = listOf("#3000E676", "#3029B6F6", "#30FF9100", "#30E040FB", "#30FFD600")

    val locationOverlayState = remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    AndroidView(
        factory = { ctx ->
            OsmMapView(ctx).apply {
                setTileSource(if (isSatelliteMode) TileSourceFactory.USGS_SAT else TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(if (activeLayers.isNotEmpty()) 14.0 else 4.0)
                controller.setCenter(OsmGeoPoint(targetLat, targetLng))

                // Real-time GPS Location Overlay
                val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                locationOverlay.enableMyLocation()
                overlays.add(locationOverlay)
                locationOverlayState.value = locationOverlay
            }
        },
        update = { mapView ->
            mapView.setTileSource(if (isSatelliteMode) TileSourceFactory.USGS_SAT else TileSourceFactory.MAPNIK)

            // Preserve location overlay while updating vector polygons
            val locationOverlay = locationOverlayState.value ?: MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).also {
                it.enableMyLocation()
                locationOverlayState.value = it
            }

            mapView.overlays.clear()
            mapView.overlays.add(locationOverlay)

            // Handle "Focar na minha localização" signal
            if (triggerFocusLocationSignal > 0L) {
                locationOverlay.myLocation?.let { myPt ->
                    mapView.controller.animateTo(myPt)
                    mapView.controller.setZoom(17.0)
                } ?: run {
                    locationOverlay.enableMyLocation()
                    locationOverlay.runOnFirstFix {
                        locationOverlay.myLocation?.let { fixPt ->
                            mapView.post {
                                mapView.controller.animateTo(fixPt)
                                mapView.controller.setZoom(17.0)
                            }
                        }
                    }
                }
            } else if (activeLayers.isNotEmpty()) {
                mapView.controller.setCenter(OsmGeoPoint(targetLat, targetLng))
            }

            // Render active stacked layers
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

            mapView.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
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
