package com.nilbyte.georef.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Edit
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
import com.nilbyte.georef.domain.model.GeorefRecord
import com.nilbyte.georef.domain.model.GisFileType
import com.nilbyte.georef.domain.model.GisLayer
import com.nilbyte.georef.sync.IdempotentSyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView as OsmMapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.Polygon as OsmPolygon
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
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

        handleIncomingFileIntent(intent)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingFileIntent(intent)
    }

    private fun handleIncomingFileIntent(intent: Intent?) {
        val uri: Uri? = intent?.data ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        if (uri != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val bytes = readBytesFromUri(applicationContext, uri)
                val fileName = getFileNameFromUri(applicationContext, uri) ?: "Camada_Importada.shp"
                if (bytes != null && bytes.isNotEmpty()) {
                    syncEngine.importGisDocument(bytes, fileName)
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Camada $fileName salva com sucesso!", Toast.LENGTH_LONG).show()
                    }
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
    val customPins by syncEngine.recordsFlow.collectAsState()

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
                        IconButton(onClick = { selectedScreen = 0 }) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = null,
                                tint = if (selectedScreen == 0) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }

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
                    customPins = customPins,
                    syncEngine = syncEngine,
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
    customPins: List<GeorefRecord>,
    syncEngine: IdempotentSyncEngine,
    onOpenLayerToggleList: () -> Unit,
    onRequestLocationPermission: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSatelliteMode by remember { mutableStateOf(false) }
    var triggerFocusLocationSignal by remember { mutableLongStateOf(0L) }

    var editingPinRecord by remember { mutableStateOf<GeorefRecord?>(null) }
    var pinNameInput by remember { mutableStateOf("") }
    var pinLatInput by remember { mutableStateOf("") }
    var pinLngInput by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        NativeOsmMapView(
            activeLayers = activeLayers,
            customPins = customPins,
            isSatelliteMode = isSatelliteMode,
            triggerFocusLocationSignal = triggerFocusLocationSignal,
            onLongPressCreatePin = { lat, lng ->
                val newId = UUID.randomUUID().toString()
                val newRecord = GeorefRecord(
                    id = newId,
                    clientId = syncEngine.clientId,
                    name = "Ponto #${customPins.size + 1}",
                    description = "Criado via toque no mapa",
                    latitude = lat,
                    longitude = lng,
                    elevation = 0.0,
                    accuracy = 0.0,
                    clientUpdatedAt = System.currentTimeMillis(),
                    serverUpdatedAt = 0L,
                    version = 1,
                    isDeleted = false
                )
                editingPinRecord = newRecord
                pinNameInput = newRecord.name
                pinLatInput = String.format("%.6f", lat).replace(",", ".")
                pinLngInput = String.format("%.6f", lng).replace(",", ".")
            },
            onSelectPinRecord = { record ->
                editingPinRecord = record
                pinNameInput = record.name
                pinLatInput = String.format("%.6f", record.latitude).replace(",", ".")
                pinLngInput = String.format("%.6f", record.longitude).replace(",", ".")
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
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

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {
                            onRequestLocationPermission()
                            triggerFocusLocationSignal = System.currentTimeMillis()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        editingPinRecord?.let { pin ->
            AlertDialog(
                onDismissRequest = { editingPinRecord = null },
                title = { Text("Editar Ponto (Pin)", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = pinNameInput,
                            onValueChange = { pinNameInput = it },
                            label = { Text("Nome do Ponto") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = pinLatInput,
                            onValueChange = { pinLatInput = it },
                            label = { Text("Latitude") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = pinLngInput,
                            onValueChange = { pinLngInput = it },
                            label = { Text("Longitude") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val newLat = pinLatInput.toDoubleOrNull() ?: pin.latitude
                            val newLng = pinLngInput.toDoubleOrNull() ?: pin.longitude
                            scope.launch {
                                syncEngine.createFieldRecord(
                                    id = pin.id,
                                    name = pinNameInput.ifBlank { "Ponto" },
                                    description = "Ponto no mapa",
                                    latitude = newLat,
                                    longitude = newLng
                                )
                                editingPinRecord = null
                            }
                        }
                    ) {
                        Text("Salvar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingPinRecord = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun NativeOsmMapView(
    activeLayers: List<GisLayer>,
    customPins: List<GeorefRecord>,
    isSatelliteMode: Boolean,
    triggerFocusLocationSignal: Long,
    onLongPressCreatePin: (Double, Double) -> Unit,
    onSelectPinRecord: (GeorefRecord) -> Unit
) {
    val context = LocalContext.current

    val defaultLat = -23.5505
    val defaultLng = -46.6333

    val targetLat = activeLayers.firstOrNull()?.centerLat ?: defaultLat
    val targetLng = activeLayers.firstOrNull()?.centerLng ?: defaultLng

    val strokeColors = listOf("#00E676", "#29B6F6", "#FF9100", "#E040FB", "#FFD600")
    val fillColors = listOf("#3000E676", "#3029B6F6", "#30FF9100", "#30E040FB", "#30FFD600")

    val rotationOverlayState = remember { mutableStateOf<RotationGestureOverlay?>(null) }
    val locationOverlayState = remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    AndroidView(
        factory = { ctx ->
            OsmMapView(ctx).apply {
                setTileSource(if (isSatelliteMode) TileSourceFactory.USGS_SAT else TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(if (activeLayers.isNotEmpty()) 14.0 else 4.0)
                controller.setCenter(OsmGeoPoint(targetLat, targetLng))

                val eventsReceiver = object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: OsmGeoPoint?): Boolean = false
                    override fun longPressHelper(p: OsmGeoPoint?): Boolean {
                        p?.let {
                            onLongPressCreatePin(it.latitude, it.longitude)
                        }
                        return true
                    }
                }
                overlays.add(MapEventsOverlay(eventsReceiver))

                val rotationGestureOverlay = RotationGestureOverlay(this).apply {
                    isEnabled = true
                }
                overlays.add(rotationGestureOverlay)
                rotationOverlayState.value = rotationGestureOverlay

                val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                locationOverlay.enableMyLocation()
                overlays.add(locationOverlay)
                locationOverlayState.value = locationOverlay
            }
        },
        update = { mapView ->
            mapView.setTileSource(if (isSatelliteMode) TileSourceFactory.USGS_SAT else TileSourceFactory.MAPNIK)

            val rotationOverlay = rotationOverlayState.value ?: RotationGestureOverlay(mapView).also {
                it.isEnabled = true
                rotationOverlayState.value = it
            }

            val locationOverlay = locationOverlayState.value ?: MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).also {
                it.enableMyLocation()
                locationOverlayState.value = it
            }

            mapView.overlays.clear()
            val eventsReceiver = object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: OsmGeoPoint?): Boolean = false
                override fun longPressHelper(p: OsmGeoPoint?): Boolean {
                    p?.let {
                        onLongPressCreatePin(it.latitude, it.longitude)
                    }
                    return true
                }
            }
            mapView.overlays.add(MapEventsOverlay(eventsReceiver))
            mapView.overlays.add(rotationOverlay)
            mapView.overlays.add(locationOverlay)

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

            // Render active stacked vector layers with EXACT complex polygon vertices
            activeLayers.forEachIndexed { index, layer ->
                val colorIdx = index % strokeColors.size
                val sColor = strokeColors[colorIdx]
                val fColor = fillColors[colorIdx]

                val coords = if (layer.polygonCoordinates.isNotEmpty()) {
                    layer.polygonCoordinates
                } else {
                    layer.features.firstOrNull { it.coordinates.isNotEmpty() }?.coordinates
                        ?: listOf(
                            com.nilbyte.georef.domain.model.GeoPoint(layer.minLat, layer.minLng),
                            com.nilbyte.georef.domain.model.GeoPoint(layer.maxLat, layer.minLng),
                            com.nilbyte.georef.domain.model.GeoPoint(layer.maxLat, layer.maxLng),
                            com.nilbyte.georef.domain.model.GeoPoint(layer.minLat, layer.maxLng)
                        )
                }

                val polygon = OsmPolygon(mapView)
                polygon.fillPaint.color = android.graphics.Color.parseColor(fColor)
                polygon.outlinePaint.color = android.graphics.Color.parseColor(sColor)
                polygon.outlinePaint.strokeWidth = 5f

                val pts = coords.map { OsmGeoPoint(it.latitude, it.longitude) }
                polygon.setPoints(pts)
                mapView.overlays.add(polygon)

                val marker = OsmMarker(mapView)
                marker.position = OsmGeoPoint(layer.centerLat, layer.centerLng)
                marker.title = layer.name
                marker.setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                mapView.overlays.add(marker)
            }

            customPins.forEach { record ->
                val pinMarker = OsmMarker(mapView)
                pinMarker.position = OsmGeoPoint(record.latitude, record.longitude)
                pinMarker.title = record.name
                pinMarker.snippet = "Lat: ${record.latitude}, Lng: ${record.longitude}"
                pinMarker.setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                pinMarker.setOnMarkerClickListener { m, _ ->
                    m.showInfoWindow()
                    onSelectPinRecord(record)
                    true
                }
                mapView.overlays.add(pinMarker)
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
