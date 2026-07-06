# GeoRef - Monorepo Kotlin Multiplatform (Android/iOS) + Go Backend + PostgreSQL

Sistema georreferenciado monorepo projetado especificamente para operação **Offline-First em campo** (sem internet por longos períodos) com **sincronização assíncrona idempotente**.

---

## 🛠️ Tecnologias Utilizadas

- **Backend**: Go (GoLang 1.26) com HTTP router nativo e pool de conexões com `pgx/v5`.
- **Database**: PostgreSQL 16 com extensão PostGIS para dados espaciais.
- **Mobile Multiplatform**: Kotlin Multiplatform (KMP) compartilhando o código de domínio, banco de dados local, lógica de fila offline (outbox) e mecanismo de sincronização assíncrono idempotente.
- **Android UI**: Android Nativo com **Jetpack Compose** e Material Design 3.
- **iOS UI**: iOS Nativo com **SwiftUI**.

---

## 📁 Estrutura do Monorepo

```
georef/
├── backend/                      # Backend em Go (API REST + PostgreSQL)
│   ├── cmd/main.go               # Ponto de entrada do backend
│   ├── db/migrations/            # Scripts de migração SQL
│   ├── internal/api/             # Handlers HTTP REST
│   ├── internal/db/              # Pool pgx e inicializador de schema
│   ├── internal/models/          # Entidades e DTOs de sincronização
│   ├── internal/repository/      # Métodos PostgreSQL idempotentes (UPSERT + Version Check)
│   └── internal/sync/            # Serviço de sincronização em lote e deltas
├── mobile/                       # Monorepo Kotlin Multiplatform (Android & iOS)
│   ├── shared/                   # Módulo KMP compartilhado (commonMain, androidMain, iosMain)
│   │   ├── domain/model/         # GeorefRecord, SyncPushRequest, SyncPushResponse
│   │   ├── data/local/           # LocalDatabase thread-safe com Mutex
│   │   ├── data/remote/          # Cliente HTTP Ktor Multiplataforma
│   │   └── sync/                 # IdempotentSyncEngine assíncrono em Coroutines
│   ├── androidApp/               # Aplicativo Android em Jetpack Compose
│   └── iosApp/                   # Aplicativo iOS em SwiftUI
├── docs/                         # Documentação detalhada
│   └── ARCHITECTURE_SYNC.md      # Protocolo de Sincronização Idempotente & Diagramas
├── docker-compose.yml            # Orquestração do Postgres DB + Go Backend
└── README.md
```

---

## ⚡ Como Executar

### 1. Iniciar Banco de Dados PostgreSQL e Go Backend via Docker:
```bash
docker-compose up --build -d
```

### 2. Verificar Status do Servidor Go:
```bash
curl http://localhost:8080/health
```

### 3. Rodar o Backend Go Localmente (opcional):
```bash
cd backend
go run main.go
```

### 4. Executar os Aplicativos Móveis (Android / iOS):
Abra a pasta `mobile/` no Android Studio ou Xcode e execute os módulos `:androidApp` ou `iosApp`.

---

## 📄 Documentação Técnica
Para entender detalhes sobre os identificadores `UUID v4`, resolução de conflitos *Last-Write-Wins*, vetores de versão e logs de idempotência no Postgres, veja:
👉 [ARCHITECTURE_SYNC.md](file:///home/notNilton/Workspace/nilbyte/georef/docs/ARCHITECTURE_SYNC.md)
