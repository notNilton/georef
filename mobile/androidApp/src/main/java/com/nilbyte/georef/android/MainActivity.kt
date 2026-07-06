package com.nilbyte.georef.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nilbyte.georef.domain.model.GeorefRecord
import com.nilbyte.georef.domain.model.SyncStatus
import com.nilbyte.georef.sync.IdempotentSyncEngine
import com.nilbyte.georef.sync.SyncState
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val syncEngine by lazy {
        IdempotentSyncEngine(clientId = "android-device-" + UUID.randomUUID().toString().take(8))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GeoRefApp(syncEngine)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoRefApp(syncEngine: IdempotentSyncEngine) {
    val records by syncEngine.recordsFlow.collectAsState()
    val syncState by syncEngine.syncState.collectAsState()
    val scope = rememberCoroutineScope()

    var nameText by remember { mutableStateOf("") }
    var descText by remember { mutableStateOf("") }
    var latText by remember { mutableStateOf("-23.5505") }
    var lngText by remember { mutableStateOf("-46.6333") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GeoRef - Mobile Field Collect") },
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
                .padding(16.dp)
        ) {
            // Sync status header card
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
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Status de Sincronização",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = when (val state = syncState) {
                                is SyncState.Idle -> "Aguardando operações em campo"
                                is SyncState.Syncing -> "Sincronizando com backend Go..."
                                is SyncState.Success -> state.message
                                is SyncState.OfflineError -> "Modo Offline: ${state.reason}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray
                        )
                    }

                    Button(
                        onClick = {
                            val batchId = "batch-" + UUID.randomUUID().toString()
                            syncEngine.syncNow(batchId)
                        },
                        enabled = syncState !is SyncState.Syncing
                    ) {
                        Text("Sincronizar")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Form to create offline georeferenced point
            Text("Novo Ponto de Campo (Offline First)", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = { Text("Nome da Amostra/Ponto") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = descText,
                onValueChange = { descText = it },
                label = { Text("Descrição / Observação") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("Latitude") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = lngText,
                    onValueChange = { lngText = it },
                    label = { Text("Longitude") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (nameText.isNotBlank()) {
                        scope.launch {
                            val recordId = UUID.randomUUID().toString()
                            syncEngine.createFieldRecord(
                                id = recordId,
                                name = nameText,
                                description = descText,
                                latitude = latText.toDoubleOrNull() ?: -23.5505,
                                longitude = lngText.toDoubleOrNull() ?: -46.6333
                            )
                            nameText = ""
                            descText = ""
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salvar Ponto Localmente")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Pontos Guardados no Dispositivo (${records.size})", style = MaterialTheme.typography.titleMedium)

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
                text = "Coordenadas: ${record.latitude}, ${record.longitude}",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Text(
                text = "Versão: ${record.version} | ID: ${record.id.take(8)}...",
                fontSize = 11.sp,
                color = Color.LightGray
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
