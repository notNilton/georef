# georef

Georeferencing application and backend service for spatial data collection and mapping.

## Architecture

```
backend/              Go API server with spatial geometry processing
mobile/               React Native mobile application for field collection
docs/                 Technical documentation, architecture sync, and TODO roadmap
docker-compose.yml     Docker orchestration
Makefile              Task automation
```

### Components

- `backend`: Go REST API serving spatial points, geometry processing, and database persistence.
- `mobile`: Mobile client for field data capture, GPS recording, and map visualization.

## Documentation

- [📋 Roadmap & TODOs](docs/TODO.md) - Planned features and project roadmap
- [📐 Architecture & Sync Protocol](docs/ARCHITECTURE_SYNC.md) - Offline-first architecture and GeoPDF processing details
- [🚀 Publishing Guide](PUBLISHING.md) - Step-by-step guide for App Store & Google Play release

## Development

### Prerequisites

- Go 1.22+
- Node.js 20+
- Docker / Podman
- JDK and Android SDK (for mobile)

### Running Services

Start database and backend dependencies:

```bash
docker compose up -d
```

Run backend service:

```bash
cd backend
go run ./cmd/api
```

Run mobile application:

```bash
cd mobile
npm install
npm run android
```

### Service Endpoints

| Service | Type | Port | Endpoint |
|---------|------|------|----------|
| HTTP API | Backend | `8080` | http://localhost:8080 |
| PostgreSQL / PostGIS | Database | `5432` | localhost:5432/georef |
