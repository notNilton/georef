# Monorepo GeoRef: Architecture & GeoPDF Processing Protocol

## 1. Visão Geral das Funcionalidades Adicionadas ao Kotlin Multiplatform

Além da arquitetura **Offline-First com Sincronismo Idempotente**, o módulo **Kotlin Multiplatform (`mobile/shared/`)** agora possui suporte completo a:

1. **Processamento e Extração de GeoPDF (`GeoPdfExtractor.kt`)**:
   - Extrai metadados estruturados de arquivos PDF no formato **OGC / ISO 32000-1 GeoPDF** (dicionários `/LGIDict`, `/Viewport`, `/BBox` e vetores de pontos globais `/GPTS`).
   - Identifica tags de texto com geocoordenadas embutidas (graus decimais ou formato sexagesimal DMS).
   - Extrai automaticamente o **Bounding Box** da região do mapa contida no PDF.

2. **Manipulação de Geocoordenadas (`GeoCoordinates.kt`)**:
   - **`GeoPoint`**: Representa latitude, longitude e elevação.
   - **Conversor DMS**: Converte graus decimais para Graus, Minutos e Segundos (`23° 33' 1.80" S, 46° 37' 59.88" W`).
   - **Fórmula de Haversine**: Cálculo de distâncias entre pontos em metros.
   - **`GeoBoundingBox`**: Cálculo de centro de mapas, limites geográficos e intersecção de áreas.

3. **Gerenciador de Mapas Offline da Região (`OfflineMapTileStore.kt`)**:
   - Converte a **Bounding Box** do GeoPDF extraído em uma grade de coordenadas de tiles `(Z, X, Y)`.
   - Baixa e armazena os tiles de mapa para uma região inteira (ex: Zoom 12 ao 15) no armazenamento do dispositivo móvel.
   - Serve os tiles de mapa armazenados localmente no dispositivo para renderização offline do visualizador de mapa em campo.

---

## 2. Componentes Kotlin Criados

```
mobile/shared/src/commonMain/kotlin/com/nilbyte/georef/
├── domain/
│   ├── model/
│   │   ├── GeoCoordinates.kt      # GeoPoint, GeoBoundingBox, GeoTile, Conversão DMS
│   │   └── GeorefRecord.kt        # Entidades e DTOs de sincronização
│   └── pdf/
│       └── GeoPdfExtractor.kt     # Leitor e Parser de metadados /GPTS e /BBox do GeoPDF
├── data/
│   ├── local/
│   │   ├── LocalDatabase.kt       # Armazenamento thread-safe com Mutex
│   │   └── OfflineMapTileStore.kt # Download e cache de tiles de mapas por região
│   └── remote/
│       └── KtorSyncApiClient.kt   # Cliente HTTP REST Ktor
└── sync/
    └── IdempotentSyncEngine.kt    # Integração: GeoPDF + Tiles Offline + Sync Backend
```

---

## 3. Teste de Funcionamento no Android & iOS

- **Android (`MainActivity.kt`)**: Interface Jetpack Compose com visualização de metadados GeoPDF, coordenadas em formato DMS, visualizador de mapa e botão **"Salvar Mapas da Região Offline"** com barra de progresso de download de tiles.
- **iOS (`ContentView.swift`)**: Interface SwiftUI com importador GeoPDF, exibição de geocoordenadas e salvamento de mapas regionais offline.
