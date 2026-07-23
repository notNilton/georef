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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.nilbyte.georef.data.repository.AuthRepository
import com.nilbyte.georef.domain.gis.GisAreaCalculator
import com.nilbyte.georef.domain.model.GeoPoint
import com.nilbyte.georef.domain.model.GeorefRecord
import com.nilbyte.georef.domain.model.GisFeature
import com.nilbyte.georef.domain.model.GisFileType
import com.nilbyte.georef.domain.model.GisLayer
import com.nilbyte.georef.domain.model.SyncStatus
import com.nilbyte.georef.domain.model.UserAccount
import com.nilbyte.georef.sync.IdempotentSyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView as OsmMapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.Polygon as OsmPolygon
import org.osmdroid.views.overlay.Polyline as OsmPolyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.UUID

// 100% FREE Satellite & Topographic Tile Sources (Zero API Keys required)
val ESRI_SATELLITE_10M_FREE = object : OnlineTileSourceBase(
    "EsriWorldImageryFree",
    0, 19, 256, ".jpg",
    arrayOf("https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl + "${MapTileIndex.getZoom(pMapTileIndex)}/${MapTileIndex.getY(pMapTileIndex)}/${MapTileIndex.getX(pMapTileIndex)}"
    }
}

val TOPO_MAP_10M_FREE = object : OnlineTileSourceBase(
    "OpenTopoMapFree",
    0, 17, 256, ".png",
    arrayOf("https://a.tile.opentopomap.org/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl + "${MapTileIndex.getZoom(pMapTileIndex)}/${MapTileIndex.getX(pMapTileIndex)}/${MapTileIndex.getY(pMapTileIndex)}.png"
    }
}

class MainActivity : ComponentActivity() {

    private val authRepository = AuthRepository()
    private val syncEngine by lazy {
        IdempotentSyncEngine(clientId = "android-field-" + UUID.randomUUID().toString().take(8))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(applicationContext, applicationContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().tileFileSystemCacheMaxBytes = 1024L * 1024L * 1024L // 1GB offline map cache

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
                    val user by authRepository.currentUser.collectAsState()

                    if (user == null) {
                        LoginRegisterScreen(
                            authRepository = authRepository,
                            onLoginSuccess = { }
                        )
                    } else {
                        GisMultiTabApp(
                            syncEngine = syncEngine,
                            currentUser = user!!,
                            onLogout = { authRepository.logout() }
                        )
                    }
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

@Composable
fun LoginRegisterScreen(
    authRepository: AuthRepository,
    onLoginSuccess: () -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("📍", fontSize = 36.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("GeoRef Field", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Mapeamento & Gestão Geográfica Offline",
                fontSize = 12.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(32.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = if (!isRegisterMode) MaterialTheme.colorScheme.surface else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isRegisterMode = false }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                            Text("Entrar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (isRegisterMode) MaterialTheme.colorScheme.surface else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isRegisterMode = true }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                            Text("Criar Conta", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (isRegisterMode) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it; errorMessage = null },
                    label = { Text("Nome Completo") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it; errorMessage = null },
                label = { Text("E-mail corporativo ou pessoal") },
                leadingIcon = { Icon(Icons.Default.Mail, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it; errorMessage = null },
                label = { Text("Senha de Acesso") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        val success = if (isRegisterMode) {
                            authRepository.register(nameInput, emailInput, passwordInput)
                        } else {
                            authRepository.login(emailInput, passwordInput)
                        }

                        if (success) {
                            onLoginSuccess()
                        } else {
                            errorMessage = "Preencha o e-mail e uma senha válida (mínimo 4 caracteres)."
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = if (isRegisterMode) "Cadastrar Conta" else "Acessar Conta",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = {
                    authRepository.loginAsGuest()
                    onLoginSuccess()
                }
            ) {
                Text("Continuar em Modo Offline (Sem Login)", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

enum class MapTileProviderMode {
    MAPNIK_VECTOR,
    ESRI_SATELLITE_FREE,
    TOPO_RELIEF_FREE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GisMultiTabApp(
    syncEngine: IdempotentSyncEngine,
    currentUser: UserAccount,
    onLogout: () -> Unit
) {
    var selectedScreen by remember { mutableIntStateOf(1) } // 0: Lista de Camadas, 1: Mapa Global
    var showRegisteredLayerPickerSheet by remember { mutableStateOf(false) }
    var showUserAccountDialog by remember { mutableStateOf(false) }
    var navTargetPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var navTargetName by remember { mutableStateOf<String?>(null) }

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
                    currentUser = currentUser,
                    onOpenUserAccount = { showUserAccountDialog = true },
                    onToggleActive = { id -> syncEngine.toggleLayerActive(id) },
                    onRemoveLayer = { id -> syncEngine.removeLayer(id) },
                    onImportNewLayerClick = {
                        genericGisPickerLauncher.launch("*/*")
                    },
                    onSelectLayer = { layer ->
                        navTargetPoint = GeoPoint(layer.centerLat, layer.centerLng)
                        navTargetName = layer.name
                        selectedScreen = 1
                    },
                    onShareLayer = { layer ->
                        shareLayerReport(context, layer)
                    }
                )
                1 -> WorldMapScreen(
                    activeLayers = activeGisLayers,
                    customPins = customPins,
                    syncEngine = syncEngine,
                    currentUser = currentUser,
                    navTargetPoint = navTargetPoint,
                    navTargetName = navTargetName,
                    onOpenUserAccount = { showUserAccountDialog = true },
                    onClearNavigation = {
                        navTargetPoint = null
                        navTargetName = null
                    },
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

            if (showUserAccountDialog) {
                AlertDialog(
                    onDismissRequest = { showUserAccountDialog = false },
                    title = { Text("Conta de Usuário", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Nome: ${currentUser.name}", fontWeight = FontWeight.SemiBold)
                            Text("E-mail: ${currentUser.email}", fontSize = 12.sp, color = Color.Gray)
                            Text("ID da Conta: ${currentUser.id}", fontSize = 11.sp, color = Color.Gray)
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showUserAccountDialog = false
                                onLogout()
                            }
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.Red)
                                Text("Sair da Conta", color = Color.Red)
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUserAccountDialog = false }) {
                            Text("Fechar")
                        }
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
                                                val areaHa = GisAreaCalculator.calculateAreaHectares(layer.polygonCoordinates)
                                                val perimKm = GisAreaCalculator.calculatePerimeterKm(layer.polygonCoordinates)
                                                Text(
                                                    text = if (areaHa > 0) String.format("%.2f ha • %.2f km", areaHa, perimKm) else "Lat: ${layer.centerLat}, Lng: ${layer.centerLng}",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
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
    currentUser: UserAccount,
    onOpenUserAccount: () -> Unit,
    onToggleActive: (String) -> Unit,
    onRemoveLayer: (String) -> Unit,
    onImportNewLayerClick: () -> Unit,
    onSelectLayer: (GisLayer) -> Unit,
    onShareLayer: (GisLayer) -> Unit
) {
    val totalAreaHectares = gisLayers.sumOf { GisAreaCalculator.calculateAreaHectares(it.polygonCoordinates) }
    val totalPerimeterKm = gisLayers.sumOf { GisAreaCalculator.calculatePerimeterKm(it.polygonCoordinates) }

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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { onOpenUserAccount() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = currentUser.name.take(1).uppercase(),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
                Column {
                    Text("Camadas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(currentUser.email, fontSize = 10.sp, color = Color.Gray)
                }
            }

            Button(
                onClick = onImportNewLayerClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Adicionar Camadas", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Total Farm Area Dashboard Banner
        if (gisLayers.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Área Total Mapeada", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(
                            text = String.format("%.2f ha", totalAreaHectares),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Talhões / Camadas", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${gisLayers.size} Áreas (${String.format("%.1f", totalPerimeterKm)} km)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

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
                        onClick = { onSelectLayer(layer) },
                        onShare = { onShareLayer(layer) }
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
    currentUser: UserAccount,
    navTargetPoint: GeoPoint?,
    navTargetName: String?,
    onOpenUserAccount: () -> Unit,
    onClearNavigation: () -> Unit,
    onOpenLayerToggleList: () -> Unit,
    onRequestLocationPermission: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var tileProviderMode by remember { mutableStateOf(MapTileProviderMode.ESRI_SATELLITE_FREE) }
    var triggerFocusLocationSignal by remember { mutableLongStateOf(0L) }

    var isMeasuringMode by remember { mutableStateOf(false) }
    var isDrawTalhaoMode by remember { mutableStateOf(false) }
    val mapPointsList = remember { mutableStateListOf<OsmGeoPoint>() }

    var editingPinRecord by remember { mutableStateOf<GeorefRecord?>(null) }
    var pinNameInput by remember { mutableStateOf("") }
    var pinLatInput by remember { mutableStateOf("") }
    var pinLngInput by remember { mutableStateOf("") }

    var showSaveTalhaoDialog by remember { mutableStateOf(false) }
    var newTalhaoNameInput by remember { mutableStateOf("") }

    var liveDistanceMeters by remember { mutableDoubleStateOf(0.0) }
    var liveAzimuthDegrees by remember { mutableDoubleStateOf(0.0) }

    Box(modifier = Modifier.fillMaxSize()) {
        NativeOsmMapView(
            activeLayers = activeLayers,
            customPins = customPins,
            tileProviderMode = tileProviderMode,
            triggerFocusLocationSignal = triggerFocusLocationSignal,
            isMeasuringMode = isMeasuringMode || isDrawTalhaoMode,
            measurementPoints = mapPointsList,
            navTargetPoint = navTargetPoint,
            onUpdateLiveNav = { dist, az ->
                liveDistanceMeters = dist
                liveAzimuthDegrees = az
            },
            onMapClickAddMeasurement = { pt ->
                mapPointsList.add(pt)
            },
            onLongPressCreatePin = { lat, lng ->
                if (!isMeasuringMode && !isDrawTalhaoMode) {
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
                }
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
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onOpenUserAccount() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = currentUser.name.take(1).uppercase(),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                    modifier = Modifier.clickable {
                        tileProviderMode = when (tileProviderMode) {
                            MapTileProviderMode.ESRI_SATELLITE_FREE -> MapTileProviderMode.TOPO_RELIEF_FREE
                            MapTileProviderMode.TOPO_RELIEF_FREE -> MapTileProviderMode.MAPNIK_VECTOR
                            MapTileProviderMode.MAPNIK_VECTOR -> MapTileProviderMode.ESRI_SATELLITE_FREE
                        }
                    }
                ) {
                    Text(
                        text = when (tileProviderMode) {
                            MapTileProviderMode.ESRI_SATELLITE_FREE -> "Satélite (Grátis)"
                            MapTileProviderMode.TOPO_RELIEF_FREE -> "Relevo (Grátis)"
                            MapTileProviderMode.MAPNIK_VECTOR -> "Vetor (OSM)"
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Ruler Button
                Surface(
                    shape = CircleShape,
                    color = if (isMeasuringMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {
                            isMeasuringMode = !isMeasuringMode
                            isDrawTalhaoMode = false
                            if (!isMeasuringMode) mapPointsList.clear()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("📏", fontSize = 18.sp)
                    }
                }

                // Draw Polygon Button (Desenhar Talhão)
                Surface(
                    shape = CircleShape,
                    color = if (isDrawTalhaoMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {
                            isDrawTalhaoMode = !isDrawTalhaoMode
                            isMeasuringMode = false
                            if (!isDrawTalhaoMode) mapPointsList.clear()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✍️", fontSize = 18.sp)
                    }
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

        // Active Draw Talhao Banner
        if (isDrawTalhaoMode) {
            val calcCoords = mapPointsList.map { GeoPoint(it.latitude, it.longitude) }
            val areaHa = GisAreaCalculator.calculateAreaHectares(calcCoords)

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("✍️ Desenhar Novo Talhão (${mapPointsList.size} vértices)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            text = "Área Calculada: ${String.format("%.2f", areaHa)} ha",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (mapPointsList.size >= 3) {
                            Button(
                                onClick = {
                                    newTalhaoNameInput = "Talhão Novo #${activeLayers.size + 1}"
                                    showSaveTalhaoDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                                Text("Salvar", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(onClick = {
                            isDrawTalhaoMode = false
                            mapPointsList.clear()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red)
                        }
                    }
                }
            }
        }

        if (showSaveTalhaoDialog) {
            AlertDialog(
                onDismissRequest = { showSaveTalhaoDialog = false },
                title = { Text("Salvar Novo Talhão", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = newTalhaoNameInput,
                            onValueChange = { newTalhaoNameInput = it },
                            label = { Text("Nome do Talhão") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val calcCoords = mapPointsList.map { GeoPoint(it.latitude, it.longitude) }
                        val areaHa = GisAreaCalculator.calculateAreaHectares(calcCoords)
                        Text("Área: ${String.format("%.2f", areaHa)} ha", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                val coords = mapPointsList.map { GeoPoint(it.latitude, it.longitude) }
                                val minLat = coords.minOf { it.latitude }
                                val maxLat = coords.maxOf { it.latitude }
                                val minLng = coords.minOf { it.longitude }
                                val maxLng = coords.maxOf { it.longitude }

                                val newLayer = GisLayer(
                                    id = "talhao-manual-" + UUID.randomUUID().toString().take(8),
                                    clientId = syncEngine.clientId,
                                    name = newTalhaoNameInput.ifBlank { "Talhão Desenhado" },
                                    fileType = GisFileType.GEOJSON,
                                    minLat = minLat,
                                    minLng = minLng,
                                    maxLat = maxLat,
                                    maxLng = maxLng,
                                    centerLat = (minLat + maxLat) / 2.0,
                                    centerLng = (minLng + maxLng) / 2.0,
                                    features = listOf(GisFeature("f1", newTalhaoNameInput, "POLYGON", GeoPoint((minLat + maxLat)/2.0, (minLng + maxLng)/2.0), coords)),
                                    polygonCoordinates = coords,
                                    polygonParts = listOf(coords),
                                    clientUpdatedAt = System.currentTimeMillis(),
                                    serverUpdatedAt = 0L,
                                    version = 1,
                                    isDeleted = false,
                                    syncStatus = SyncStatus.PENDING_CREATE
                                )

                                syncEngine.gisLocalDatabase.saveOrUpdateLayer(newLayer, isPendingSync = true)
                                isDrawTalhaoMode = false
                                mapPointsList.clear()
                                showSaveTalhaoDialog = false
                            }
                        }
                    ) {
                        Text("Salvar Camada")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveTalhaoDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Active Navigation Line Banner
        navTargetPoint?.let {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 70.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Navigation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(navTargetName ?: "Navegação GPS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            val distText = if (liveDistanceMeters > 1000) String.format("%.2f km", liveDistanceMeters / 1000) else "${liveDistanceMeters.toInt()} m"
                            Text("Distância: $distText | Azimute: ${liveAzimuthDegrees.toInt()}°", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = onClearNavigation) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            }
        }

        // Active Ruler Measurement Banner
        if (isMeasuringMode) {
            val calcCoords = mapPointsList.map { GeoPoint(it.latitude, it.longitude) }
            val perimKm = GisAreaCalculator.calculatePerimeterKm(calcCoords)
            val areaHa = GisAreaCalculator.calculateAreaHectares(calcCoords)

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📏 Régua de Medição (${mapPointsList.size} pontos)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        val distText = if (perimKm >= 1.0) String.format("%.2f km", perimKm) else "${(perimKm * 1000).toInt()} m"
                        Text(
                            text = if (areaHa > 0) "Distância: $distText | Área: ${String.format("%.2f", areaHa)} ha" else "Distância: $distText",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { mapPointsList.clear() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Gray)
                        }
                        IconButton(onClick = {
                            isMeasuringMode = false
                            mapPointsList.clear()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red)
                        }
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
    tileProviderMode: MapTileProviderMode,
    triggerFocusLocationSignal: Long,
    isMeasuringMode: Boolean,
    measurementPoints: List<OsmGeoPoint>,
    navTargetPoint: GeoPoint?,
    onUpdateLiveNav: (Double, Double) -> Unit,
    onMapClickAddMeasurement: (OsmGeoPoint) -> Unit,
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

    val tileSource = when (tileProviderMode) {
        MapTileProviderMode.ESRI_SATELLITE_FREE -> ESRI_SATELLITE_10M_FREE
        MapTileProviderMode.TOPO_RELIEF_FREE -> TOPO_MAP_10M_FREE
        MapTileProviderMode.MAPNIK_VECTOR -> TileSourceFactory.MAPNIK
    }

    AndroidView(
        factory = { ctx ->
            OsmMapView(ctx).apply {
                setTileSource(tileSource)
                maxZoomLevel = 19.0
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(if (activeLayers.isNotEmpty()) 15.0 else 4.0)
                controller.setCenter(OsmGeoPoint(targetLat, targetLng))

                val eventsReceiver = object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: OsmGeoPoint?): Boolean {
                        if (isMeasuringMode && p != null) {
                            onMapClickAddMeasurement(p)
                            return true
                        }
                        return false
                    }
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
            mapView.setTileSource(tileSource)
            mapView.maxZoomLevel = 19.0

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
                override fun singleTapConfirmedHelper(p: OsmGeoPoint?): Boolean {
                    if (isMeasuringMode && p != null) {
                        onMapClickAddMeasurement(p)
                        return true
                    }
                    return false
                }
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
            } else if (activeLayers.isNotEmpty() && navTargetPoint == null) {
                mapView.controller.setCenter(OsmGeoPoint(targetLat, targetLng))
            }

            // Draw GPS Navigation Line Overlay
            navTargetPoint?.let { dest ->
                val myLoc = locationOverlay.myLocation
                if (myLoc != null) {
                    val p1 = GeoPoint(myLoc.latitude, myLoc.longitude)
                    val p2 = dest
                    val dist = GisAreaCalculator.distanceMeters(p1, p2)
                    val az = GisAreaCalculator.calculateAzimuthDegrees(p1, p2)
                    onUpdateLiveNav(dist, az)

                    val navLine = OsmPolyline(mapView)
                    navLine.outlinePaint.color = android.graphics.Color.parseColor("#00E5FF")
                    navLine.outlinePaint.strokeWidth = 8f
                    navLine.setPoints(listOf(myLoc, OsmGeoPoint(dest.latitude, dest.longitude)))
                    mapView.overlays.add(navLine)
                }
            }

            // Draw Active Measurement / Polygon Creation Lines & Markers
            if (measurementPoints.isNotEmpty()) {
                val measurePolyline = OsmPolyline(mapView)
                measurePolyline.outlinePaint.color = android.graphics.Color.parseColor("#FFD600")
                measurePolyline.outlinePaint.strokeWidth = 6f
                measurePolyline.setPoints(measurementPoints)
                mapView.overlays.add(measurePolyline)

                measurementPoints.forEachIndexed { idx, pt ->
                    val mMarker = OsmMarker(mapView)
                    mMarker.position = pt
                    mMarker.title = "Vértice #${idx + 1}"
                    mMarker.setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                    mapView.overlays.add(mMarker)
                }
            }

            activeLayers.forEachIndexed { index, layer ->
                val colorIdx = index % strokeColors.size
                val sColor = strokeColors[colorIdx]
                val fColor = fillColors[colorIdx]

                val partsList = if (layer.polygonParts.isNotEmpty()) {
                    layer.polygonParts
                } else if (layer.polygonCoordinates.isNotEmpty()) {
                    listOf(layer.polygonCoordinates)
                } else {
                    listOf(
                        listOf(
                            com.nilbyte.georef.domain.model.GeoPoint(layer.minLat, layer.minLng),
                            com.nilbyte.georef.domain.model.GeoPoint(layer.maxLat, layer.minLng),
                            com.nilbyte.georef.domain.model.GeoPoint(layer.maxLat, layer.maxLng),
                            com.nilbyte.georef.domain.model.GeoPoint(layer.minLat, layer.maxLng)
                        )
                    )
                }

                partsList.forEach { partCoords ->
                    if (partCoords.size >= 3) {
                        val polygon = OsmPolygon(mapView)
                        polygon.fillPaint.color = android.graphics.Color.parseColor(fColor)
                        polygon.outlinePaint.color = android.graphics.Color.parseColor(sColor)
                        polygon.outlinePaint.strokeWidth = 5f

                        val pts = partCoords.map { OsmGeoPoint(it.latitude, it.longitude) }
                        polygon.setPoints(pts)
                        mapView.overlays.add(polygon)
                    }
                }

                val marker = OsmMarker(mapView)
                marker.position = OsmGeoPoint(layer.centerLat, layer.centerLng)
                val areaHa = GisAreaCalculator.calculateAreaHectares(layer.polygonCoordinates)
                marker.title = if (areaHa > 0) "${layer.name} (${String.format("%.2f", areaHa)} ha)" else layer.name
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

private fun shareLayerReport(context: Context, layer: GisLayer) {
    val geoJsonStr = GisAreaCalculator.exportLayerToGeoJson(layer)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Relatório GIS GeoRef - ${layer.name}")
        putExtra(Intent.EXTRA_TEXT, geoJsonStr)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Relatório GeoJSON"))
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
    onClick: () -> Unit,
    onShare: () -> Unit
) {
    val areaHa = GisAreaCalculator.calculateAreaHectares(layer.polygonCoordinates)
    val perimKm = GisAreaCalculator.calculatePerimeterKm(layer.polygonCoordinates)

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
                if (areaHa > 0) {
                    Text(
                        text = "Área: ${String.format("%.2f", areaHa)} ha | Perímetro: ${String.format("%.2f", perimKm)} km",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text("${layer.centerLat}, ${layer.centerLng}", fontSize = 11.sp, color = Color.Gray)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
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
